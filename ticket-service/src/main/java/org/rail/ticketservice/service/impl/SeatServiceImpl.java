package org.rail.ticketservice.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.rail.api.constant.OrderTypeConstants;
import org.rail.common.core.exception.BizException;
import org.rail.common.redis.exception.CacheInitException;
import org.rail.ticketservice.model.dto.SequenceDTO;
import org.rail.ticketservice.model.entity.Station;
import org.rail.ticketservice.model.vo.SeatBusinessVO;
import org.rail.ticketservice.model.vo.SeatDetailVO;
import org.rail.common.redis.constant.RedisConstants;
import org.rail.common.redis.util.CacheClient;
import org.rail.ticketservice.mapper.SeatIntervalOccupyMapper;
import org.rail.ticketservice.mapper.SeatMapper;
import org.rail.ticketservice.model.entity.SeatIntervalOccupy;
import org.rail.ticketservice.service.SeatService;
import org.rail.ticketservice.task.StationLocalCacheTask;
import org.rail.ticketservice.task.TrainStopStationLocalCacheTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
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
    @Autowired
    private StationLocalCacheTask stationCacheTask;
    @Autowired
    private TrainStopStationLocalCacheTask trainStopCacheTask;

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
     * 将座位对象拆分为 Redis Hash 字段格式：
     * 1. 正向：SeatId:{seatId}:{字段名} -> 字段值
     * 2. 反向：SeatNo:{seatNo} -> seatId（用于seatNo→seatId映射）
     */
    private Map<String, Object> convertSeatToFieldMap(SeatDetailVO seat) {
        Map<String, Object> fieldMap = new HashMap<>();

        // ============================= 正向映射 =============================
        String fieldId = buildSeatFieldKey(seat.getId(), "id");
        fieldMap.put(fieldId, seat.getId());

        String fieldTrainId = buildSeatFieldKey(seat.getId(), "trainId");
        fieldMap.put(fieldTrainId, seat.getTrainId());

        String fieldClassId = buildSeatFieldKey(seat.getId(), "trainSeatClassId");
        fieldMap.put(fieldClassId, seat.getTrainSeatClassId());

        String fieldSeatType = buildSeatFieldKey(seat.getId(), "seatType");
        fieldMap.put(fieldSeatType, seat.getSeatType());

        String fieldCarriage = buildSeatFieldKey(seat.getId(), "carriageNumber");
        fieldMap.put(fieldCarriage, seat.getCarriageNumber());

        String fieldSeatNo = buildSeatFieldKey(seat.getId(), "seatNo");
        fieldMap.put(fieldSeatNo, seat.getSeatNo());

        String fieldStatus = buildSeatFieldKey(seat.getId(), "status");
        fieldMap.put(fieldStatus, seat.getStatus());

        Integer rowNum = extractRowNum(seat.getSeatNo());
        Integer seatSeq = extractFieldSeatSeq(seat.getSeatNo());

        String fieldRowNum = buildSeatFieldKey(seat.getId(), "rowNum");
        fieldMap.put(fieldRowNum, rowNum);

        String fieldSeatSeq = buildSeatFieldKey(seat.getId(), "seatSeq");
        fieldMap.put(fieldSeatSeq, seatSeq);


        // ============================= 反向映射 =============================
        String reverseField = buildSeatReverseFieldKey(seat.getCarriageNumber(), seat.getSeatNo());
        fieldMap.put(reverseField, seat.getId());

        return fieldMap;
    }

    /**
     * 从 seatNo 提取排号：1A → 1
     */
    private Integer extractRowNum(String seatNo) {
        return Integer.parseInt(seatNo.replaceAll("[^0-9]", ""));
    }

    /**
     * 从 seatNo 提取座位序号：A→0, B→1, C→2, D→3, F→4
     */
    private Integer extractFieldSeatSeq(String seatNo) {
        char c = seatNo.replaceAll("[0-9]", "").toUpperCase().charAt(0);
        return switch (c) {
            case 'A' -> 0;
            case 'B' -> 1;
            case 'C' -> 2;
            case 'D' -> 3;
            case 'F' -> 4;
            default -> -1; // 异常座位
        };
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
     * 构建座位反向映射的全局唯一键
     */
    private String buildSeatReverseFieldKey(String carriageNumber, String seatNo) {
        String safeCarriage = StrUtil.trimToEmpty(carriageNumber);
        String safeSeatNo = StrUtil.trimToEmpty(seatNo);
        return String.format("SeatNoReverse:%s:%s", safeCarriage, safeSeatNo);
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
     * 根据车次ID、出发站、到达站，获取Redis中空闲座位ID列表
     */
    @Override
    public List<Long> getFreeSeatIdsByBitmap(Long trainId, String departureCode, String arrivalCode) {
        if(trainId == null || departureCode == null || arrivalCode == null) {
            throw new BizException("车次ID、出发站、到达站不能为空");
        }

        SequenceDTO seq = getStationSequence(trainId, departureCode, arrivalCode);
        int startSeq = seq.getStartSequence();
        int endSeq = seq.getEndSequence();

        if (startSeq < 0 || endSeq < 0 || startSeq >= endSeq) {
            throw new BizException("车次不存在或站点信息无效");
        }

        List<Long> allSeatIds = getAllSeatIdsByTrainId(trainId);
        if (allSeatIds.isEmpty()) {
            return null;
        }

        List<Long> freeSeatIdList = new ArrayList<>();
        for (Long seatId : allSeatIds) {
            boolean isFree = isSeatFreeInRange(trainId, seatId, startSeq, endSeq);
            if (isFree) {
                freeSeatIdList.add(seatId);
            }
        }

        return freeSeatIdList;
    }

    /**
     * 根据车次ID和座位ID获取座位基础信息（Hash结构字段查询）
     */
    @Override
    public SeatBusinessVO getSeatBaseInfo(Long trainId, Long seatId) {
        if (trainId == null || seatId == null) {
            return null;
        }

        String hashKey = buildSeatHashKey(trainId);

        try {
            Object idObj = cacheClient.hGet(hashKey, buildSeatFieldKey(seatId, "id"));
            Object trainIdObj = cacheClient.hGet(hashKey, buildSeatFieldKey(seatId, "trainId"));
            Object classIdObj = cacheClient.hGet(hashKey, buildSeatFieldKey(seatId, "trainSeatClassId"));
            Object seatTypeObj = cacheClient.hGet(hashKey, buildSeatFieldKey(seatId, "seatType"));
            Object carriageObj = cacheClient.hGet(hashKey, buildSeatFieldKey(seatId, "carriageNumber"));
            Object seatNoObj = cacheClient.hGet(hashKey, buildSeatFieldKey(seatId, "seatNo"));
            Object statusObj = cacheClient.hGet(hashKey, buildSeatFieldKey(seatId, "status"));
            Object rowNumObj = cacheClient.hGet(hashKey, buildSeatFieldKey(seatId, "rowNum"));
            Object seatSeqObj = cacheClient.hGet(hashKey, buildSeatFieldKey(seatId, "seatSeq"));

            return SeatBusinessVO.builder()
                    .id(Long.parseLong(idObj.toString()))
                    .trainId(Long.parseLong(trainIdObj.toString()))
                    .trainSeatClassId(Long.parseLong(classIdObj.toString()))
                    .seatType(Integer.parseInt(seatTypeObj.toString()))
                    .carriageNumber(carriageObj != null ? carriageObj.toString() : "")
                    .seatNo(seatNoObj != null ? seatNoObj.toString() : "")
                    .status(statusObj != null ? Integer.parseInt(statusObj.toString()) : 0)
                    .rowNum(rowNumObj != null ? Integer.parseInt(rowNumObj.toString()) : null)
                    .seatSeq(seatSeqObj != null ? Integer.parseInt(seatSeqObj.toString()) : null)
                    .build();

        } catch (Exception e) {
            String errorMsg = String.format("从Redis获取座位基础信息发生异常，trainId=%s, seatId=%s", trainId, seatId);
            throw new BizException(errorMsg, e);
        }
    }

    /**
     * 判断座位在指定区间内是否空闲
     */
    private boolean isSeatFreeInRange(Long trainId, Long seatId, int startSeq, int endSeq) {
        String tempBitmapKey = buildSeatBitmapKey(OrderTypeConstants.PREORDER, trainId, seatId);
        String formalBitmapKey = buildSeatBitmapKey(OrderTypeConstants.ORDER, trainId, seatId);

        return cacheClient.isRangeAllZero(tempBitmapKey, startSeq, endSeq)
                && cacheClient.isRangeAllZero(formalBitmapKey, startSeq, endSeq);
    }

    /**
     * 根据车次ID获取Redis中所有座位ID（通过Hash字段前缀过滤）
     */
    private List<Long> getAllSeatIdsByTrainId(Long trainId) {
        if (trainId == null) {
            throw new BizException("车次ID不能为空");
        }

        String redisHashKey = buildSeatHashKey(trainId);

        Map<String, Object> seatFieldMap = cacheClient.hEntries(redisHashKey);
        if (CollectionUtils.isEmpty(seatFieldMap)) {
            return List.of();
        }

        return seatFieldMap.keySet().stream()
                .filter(field -> field.startsWith("SeatId:") && field.split(":").length >= 3)
                // 提取seatId（拆分字符串：SeatId:1001:id → [SeatId,1001,id] → 取1001）
                .map(field -> {
                    try {
                        return Long.parseLong(field.split(":")[1]);
                    } catch (NumberFormatException e) {
                        log.error("解析座位ID失败，field:{}", field, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取站点序列信息
     */
    private SequenceDTO getStationSequence(Long trainId, String departureCode, String arrivalCode) {
        List<Station> allStations = stationCacheTask.getAllStations();

        Long fromStationId = getStationIdByCode(allStations, departureCode, "出发站");
        Long toStationId = getStationIdByCode(allStations, arrivalCode, "到达站");

        Map<Long, Integer> stationId2SeqMap = trainStopCacheTask.getCacheByTrainId(trainId)
                .getStationId2SeqMap();

        validateStationSequence(stationId2SeqMap, fromStationId, toStationId, trainId, departureCode, arrivalCode);

        SequenceDTO sequenceDTO = new SequenceDTO();
        sequenceDTO.setStartSequence(stationId2SeqMap.get(fromStationId));
        sequenceDTO.setEndSequence(stationId2SeqMap.get(toStationId));
        return sequenceDTO;
    }

    /**
     * 根据站点编码获取ID
     */
    private Long getStationIdByCode(List<Station> allStations, String stationCode, String stationType) {
        return allStations.stream()
                .filter(station -> stationCode.equals(station.getCode()))
                .findFirst()
                .orElseThrow(() -> new BizException(stationType + "编码不存在：" + stationCode))
                .getId();
    }

    /**
     * 业务校验
     */
    private void validateStationSequence(Map<Long, Integer> stationId2SeqMap,
                                         Long fromStationId,
                                         Long toStationId,
                                         Long trainId,
                                         String departureCode,
                                         String arrivalCode) {
        if (!stationId2SeqMap.containsKey(fromStationId)) {
            throw new BizException(trainId + "车次不包含出发站：" + departureCode);
        }
        if (!stationId2SeqMap.containsKey(toStationId)) {
            throw new BizException(trainId + "车次不包含到达站：" + arrivalCode);
        }
        Integer startSeq = stationId2SeqMap.get(fromStationId);
        Integer endSeq = stationId2SeqMap.get(toStationId);
        if (startSeq >= endSeq) {
            throw new BizException("站点顺序异常：出发站序列不能大于等于到达站序列");
        }
    }

    /**
     * 单条座位占用记录初始化Bitmap
     */
    private void initSeatOccupancyFromDb(SeatIntervalOccupy occupancy) {
        validateSeatOccupancy(occupancy);

        String key = buildSeatBitmapKey(occupancy.getOrderType(), occupancy.getTrainId(), occupancy.getSeatId());
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
    private String buildSeatBitmapKey(Integer orderType, Long trainId, Long seatId) {
        String prefix = OrderTypeConstants.PREORDER.equals(orderType)
                ? RedisConstants.RAIL_BITMAP_SEAT_TEMP_LOCK_PREFIX
                : RedisConstants.RAIL_BITMAP_SEAT_FORMAL_PREFIX;

        return String.format("%strainId:%d:seatId:%d",
                prefix,
                trainId,
                seatId);
    }
}