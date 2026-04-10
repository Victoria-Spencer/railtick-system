package org.rail.ticketservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.rail.api.constant.OrderTypeConstants;
import org.rail.common.core.exception.CacheInitException;
import org.rail.common.core.pojo.vo.SeatDetailVO;
import org.rail.common.redis.constant.RedisConstants;
import org.rail.common.redis.util.CacheClient;
import org.rail.ticketservice.mapper.SeatIntervalOccupyMapper;
import org.rail.ticketservice.mapper.SeatMapper;
import org.rail.ticketservice.pojo.entity.SeatIntervalOccupy;
import org.rail.ticketservice.service.SeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SeatServiceImpl implements SeatService {

    @Autowired
    private SeatIntervalOccupyMapper seatIntervalOccupyMapper;
    @Autowired
    private SeatMapper seatMapper;
    @Autowired
    private CacheClient cacheClient;

    /**
     * 最大允许初始化的座位区间长度，防止Redis内存溢出
     */
    private static final int MAX_INTERVAL = 1000;

    @Override
    public void initAllTrainSeatCache() {
        try {
            List<SeatDetailVO> seatDetailList = seatMapper.selectAllSeatDetails();
            if (CollectionUtils.isEmpty(seatDetailList)) {
                return;
            }

            Map<Long, List<SeatDetailVO>> seatGroupByTrainId = groupSeatsByTrainId(seatDetailList);

            batchSaveSeatToRedis(seatGroupByTrainId);

            log.info("列车座位基础信息Hash缓存初始化完成，总数据量：{}，涉及车次：{}个",
                    seatDetailList.size(), seatGroupByTrainId.size());
        } catch (Exception e) {
            throw new CacheInitException("列车座位基础信息缓存初始化失败", e);
        }
    }

    /**
     * 批量保存座位到Redis（结构化字段存储，支持单字段修改）
     */
    private void batchSaveSeatToRedis(Map<Long, List<SeatDetailVO>> seatGroupByTrainId) {
        for (Map.Entry<Long, List<SeatDetailVO>> entry : seatGroupByTrainId.entrySet()) {
            Long trainId = entry.getKey();
            List<SeatDetailVO> seats = entry.getValue();

            // 存放所有拆分后的字段键值对
            Map<String, Object> allFieldMap = new HashMap<>();
            // 遍历座位，拆分每个字段
            for (SeatDetailVO seat : seats) {
                Map<String, Object> fieldMap = convertSeatToFieldMap(seat);
                allFieldMap.putAll(fieldMap);
            }

            String redisKey = buildSeatHashKey(trainId);
            cacheClient.hPutAll(redisKey, allFieldMap);
        }
    }

    /**
     * 将座位对象拆分为 Redis Hash 字段格式："SeatId" : seatId : 字段名 -> 字段值
     */
    private Map<String, Object> convertSeatToFieldMap(SeatDetailVO seat) {
        Map<String, Object> fieldMap = new HashMap<>();
        // 座位ID
        String fieldId = buildSeatFieldKey(seat.getId(), "id");
        fieldMap.put(fieldId, seat.getId());
        // 车次ID
        String fieldTrainId = buildSeatFieldKey(seat.getId(), "trainId");
        fieldMap.put(fieldTrainId, seat.getTrainId());
        // 席别关联ID
        String fieldClassId = buildSeatFieldKey(seat.getId(), "trainSeatClassId");
        fieldMap.put(fieldClassId, seat.getTrainSeatClassId());
        // 座位号
        String fieldSeatNo = buildSeatFieldKey(seat.getId(), "seatNo");
        fieldMap.put(fieldSeatNo, seat.getSeatNo());
        // 座位状态
        String fieldStatus = buildSeatFieldKey(seat.getId(), "status");
        fieldMap.put(fieldStatus, seat.getStatus());

        return fieldMap;
    }

    /**
     * 构建座位Hash的Redis Key
     */
    private String buildSeatHashKey(Long trainId) {
        return String.format("%strainId:%d", RedisConstants.RAIL_HASH_SEAT_INFO_PREFIX, trainId);
    }

    /**
     * 构建单字段的Field名称：seatId:字段名
     */
    private String buildSeatFieldKey(Long seatId, String fieldName) {
        return String.format("SeatId:%d:%s", seatId, fieldName);
    }

    /**
     * 按车次分组
     */
    private Map<Long, List<SeatDetailVO>> groupSeatsByTrainId(List<SeatDetailVO> seatDetailList) {
        return seatDetailList.stream()
                .collect(Collectors.groupingBy(SeatDetailVO::getTrainId));
    }

    /**
     * 从数据库加载所有座位占用区间，初始化Redis Bitmap
     */
    @Override
    public void initAllSeatOccupancyBitmap() {
        List<SeatIntervalOccupy> occupancyList = seatIntervalOccupyMapper.selectValidAll();

        for (SeatIntervalOccupy occupancy : occupancyList) {
            initSeatOccupancyFromDb(occupancy);
        }

        log.info("列车座位占用Bitmap缓存初始化完成，总记录数：{}", occupancyList.size());
    }

    /**
     * 单条座位占用记录初始化Bitmap
     */
    private void initSeatOccupancyFromDb(SeatIntervalOccupy occupancy) {
        validateSeatOccupancy(occupancy);

        String key = buildSeatBitmapKey(occupancy);
        try {
            cacheClient.setRangeBits(key, occupancy.getStartSequence(), occupancy.getEndSequence(), true);
        } catch (Exception e) {
            throw new CacheInitException("初始化座位占用Bitmap失败，key：" + key, e);
        }
    }

    /**
     * 座位占用记录参数+合法性校验
     */
    private void validateSeatOccupancy(SeatIntervalOccupy occupancy) {
        // 空对象校验
        if (occupancy == null) {
            throw new CacheInitException("座位占用记录不能为空");
        }

        Integer startSequence = occupancy.getStartSequence();
        Integer endSequence = occupancy.getEndSequence();
        Long trainId = occupancy.getTrainId();

        // 核心参数非空校验
        if (startSequence == null || endSequence == null || trainId == null) {
            throw new CacheInitException(
                    String.format("车次[%s]座位缓存参数缺失", trainId)
            );
        }

        // 序列非负校验
        if (startSequence < 0 || endSequence < 0) {
            throw new CacheInitException(
                    String.format("车次[%s]座位序列不能为负数，区间[%s,%s]", trainId, startSequence, endSequence)
            );
        }

        // 区间合法性校验
        if (startSequence > endSequence) {
            throw new CacheInitException(
                    String.format("车次[%s]座位区间非法，起始大于结束", trainId)
            );
        }

        // 区间长度限制
        if (endSequence - startSequence > MAX_INTERVAL) {
            throw new CacheInitException(
                    String.format("车次[%s]座位区间超出最大限制长度", trainId)
            );
        }
    }

    /**
     * 构建Bitmap缓存Key
     */
    private String buildSeatBitmapKey(SeatIntervalOccupy occupancy) {
        String prefix = OrderTypeConstants.PREORDER.equals(occupancy.getOrderType())
                ? RedisConstants.RAIL_BITMAP_SEAT_TEMP_LOCK_PREFIX
                : RedisConstants.RAIL_BITMAP_SEAT_FORMAL_PREFIX;

        return String.format("%strainId:%d:seatId:%d",
                prefix,
                occupancy.getTrainId(),
                occupancy.getSeatId());
    }
}