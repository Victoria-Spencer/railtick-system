package org.rail.ticketservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.TypeReference;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.github.pagehelper.PageHelper;
import org.apache.commons.lang.StringUtils;
import org.rail.commonapi.constant.OrderTypeConstants;
import org.rail.commonapi.dto.*;
import org.rail.commonservice.constant.RedisConstants;
import org.rail.commonservice.exception.BusinessException;
import org.rail.commonservice.result.AggBatchResult;
import org.rail.commonservice.result.AggCacheResult;
import org.rail.commonservice.result.PageResult;
import org.rail.commonservice.utils.BeanUtils;
import org.rail.commonservice.utils.CacheClient;
import org.rail.ticketservice.constant.SeatStatusConstants;
import org.rail.ticketservice.mapper.*;
import org.rail.ticketservice.pojo.dto.*;
import org.rail.ticketservice.pojo.entity.SeatIntervalOccupy;
import org.rail.ticketservice.pojo.entity.Train;
import org.rail.ticketservice.pojo.vo.*;
import org.rail.ticketservice.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TicketServiceImpl implements TicketService {

    @Autowired
    private StationMapper stationMapper;
    @Autowired
    private TrainStopStationMapper trainStopStationMapper;
    @Autowired
    private SeatClassMapper seatClassMapper;
    @Autowired
    private TrainMapper trainMapper;
    @Autowired
    private TrainTypeDictMapper  trainTypeDictMapper;
    @Autowired
    private TrainSeatMapper trainSeatMapper;
    @Autowired SeatIntervalOccupyMapper seatIntervalOccupyMapper;
    @Autowired
    private CacheClient cacheClient;

    // 从配置文件注入预订单有效期（分钟）
    @Value("${order.pre.expire-minutes : 15}") // 默认15分钟
    private Integer preOrderExpireMinutes;

    /**
     * 查询购票列表（old）
     * @param ticketQueryDTO
     * @return
     */
    /*public List<TicketQueryVO> queryTicket(TicketQueryDTO ticketQueryDTO) {

        // 1.逻辑下沉到 SQL，用批量查询替代循环查询，直接通过一次数据库查询获取所有结果，MyBatis自动封装为List<TrainDetailVO>
        List<TrainDetailVO> trainDetailVOList = stationMapper.getTrainDetailsByDTO(ticketQueryDTO);

        List<TicketQueryVO> resultList = new ArrayList<>();

        // 2.根据列车id，查询席别数据（类型等）--- List
        List<SeatQueryDTO> seatQueryDTOList = BeanUtil.copyToList(trainDetailVOList, SeatQueryDTO.class);
        List<SeatClassVO> seatClassVOList = querySeatClassData(seatQueryDTOList);
        // 按trainId分组,映射到Map里面
        Map<Long, List<SeatClassVO>> seatGroupByTrainId = seatClassVOList.stream()
                .collect(Collectors.groupingBy(SeatClassVO::getTrainId));


        // 3.拷贝属性
        for (TrainDetailVO trainDetailVO : trainDetailVOList) {
            TicketQueryVO ticketQueryVO = new TicketQueryVO();

            // 3.1.拷贝列车属性
            Train train = new Train();
            BeanUtil.copyProperties(
                    trainDetailVO,
                    train,
                    CopyOptions.create()
                            .setFieldMapping(new HashMap<String, String>(){{
                                put("trainId", "id");
                            }})
            );
            ticketQueryVO.setTrain(train);

            // 取出列车id
            Long trainId = trainDetailVO.getTrainId();

            // 3.2.拷贝席别信息
            // 从Map中取该列车的席别列表
            List<SeatClassVO> seatVOs = seatGroupByTrainId.getOrDefault(trainId, new ArrayList<>());
            List<SeatClassFrontVO> frontVOs = BeanUtil.copyToList(seatVOs, SeatClassFrontVO.class);
            ticketQueryVO.setSeatClassFrontVOList(frontVOs);

            // 3.3.拷贝列车类型信息
            List<TrainTypeVO> trainTypeVOList = trainDetailVO.getTrainTypeVOList();
            ticketQueryVO.setTrainTypeVOList(trainTypeVOList);

            // 3.3.拷贝其它属性
            BeanUtil.copyProperties(trainDetailVO, ticketQueryVO);

            // 3.4.计算历经时间
            Integer duration = calculateDurationInMinutes(ticketQueryVO.getDepartureTime(), ticketQueryVO.getArrivalTime());
            ticketQueryVO.setDuration(duration);

            // 3.5.始发站和终点站判断
            Integer departureStationId = trainDetailVO.getDepartureStationId();
            boolean isDeparture = checkDepartureStation(trainId, departureStationId);
            Integer arrivalStationId = trainDetailVO.getArrivalStationId();
            boolean isArrival = checkTerminalStation(trainId, arrivalStationId);
            ticketQueryVO.setDepartureFlag(isDeparture);
            ticketQueryVO.setArrivalFlag(isArrival);

            resultList.add(ticketQueryVO);
        }

        // 封装返回
        return resultList;
    }*/

    /**
     * 查询购票列表（新增分层次缓存）
     * @param ticketQueryDTO
     * @return
     */
    public List<TicketQueryVO> queryTicket(TicketQueryDTO ticketQueryDTO) {
        // 查询车次基础信息
        List<TrainDetailVO> trainDetailVOList = getTrainDetailVOS(ticketQueryDTO);

        // 查询余票数量
        List<SeatClassVO> seatClassVOList = querySeatClassData(trainDetailVOList);

        // 构建最终返回的vo
        return buildTicketQueryVO(trainDetailVOList, seatClassVOList, ticketQueryDTO);
    }

    /**
     * 查询席别信息
     * @param trainDetailVOList
     * @return
     */
    private List<SeatClassVO> querySeatClassData(List<TrainDetailVO> trainDetailVOList) {
        // 1. 转换入参：TrainDetailVO -> SeatQueryDTO
        List<SeatQueryDTO> seatQueryDTOList = BeanUtil.copyToList(trainDetailVOList, SeatQueryDTO.class);

        // 2. 定义【聚合缓存Key生成器】：为每个SeatQueryDTO生成唯一缓存Key
        // Key规则：rail:agg:seat_class:列车ID:出发站序:到达站序（确保唯一性）
        Function<SeatQueryDTO, String> keyGenerator = dto ->
                String.format("%s%d:%d:%d",
                        RedisConstants.RAIL_AGG_SEAT_CLASS,
                        dto.getTrainId(),
                        dto.getStartSequence(),
                        dto.getEndSequence());

        // 3. 核心：构建「trainId → SeatQueryDTO列表」的映射（仅一次遍历，预处理）
        // 目的：后续通过VO的trainId快速拿到对应DTO
        Map<Long, List<SeatQueryDTO>> trainId2DtosMap = seatQueryDTOList.stream()
                .collect(Collectors.groupingBy(SeatQueryDTO::getTrainId)); // 按trainId分组

        // 4. 定义返回类型（单个SeatClassVO）
        TypeReference<SeatClassVO> typeRef = new TypeReference<SeatClassVO>() {};

        // 5. 调用缓存工具类
        return cacheClient.batchQueryAggCache(
                keyGenerator,
                seatQueryDTOList,
                typeRef,
                missDtos -> {
                    // 用【缓存未命中的DTO列表】查询数据库（而非全量 seatQueryDTOList）
                    List<SeatClassVO> seatClassVOList = seatClassMapper.batchQuerySeatInfoByDTOList(missDtos);

                    // 构建 aggKey -> SeatClassVO 的映射
                    Map<String, SeatClassVO> dataMap = new HashMap<>();
                    for (SeatClassVO vo : seatClassVOList) {
                        // 从预处理的映射中，根据VO的trainId取对应的DTO列表
                        List<SeatQueryDTO> dtos = trainId2DtosMap.get(vo.getTrainId());
                        if (dtos != null && !dtos.isEmpty()) {
                            // 取第一个匹配的DTO
                            SeatQueryDTO matchDto = dtos.getFirst();
                            String aggKey = keyGenerator.apply(matchDto);
                            dataMap.put(aggKey, vo);
                        }
                    }

                    // 组装所有依赖的单表Key
                    List<String> dependSingleKeys = new ArrayList<>();
                    for (SeatClassVO seatClassVO : seatClassVOList) {
                        // trainSeatClassKey（列车席别关联Key）
                        String trainSeatClassKey = RedisConstants.RAIL_TRAIN_SEAT_CLASS_PREFIX
                                + seatClassVO.getTrainId()
                                + ":"
                                + seatClassVO.getSeatClassId();
                        dependSingleKeys.add(trainSeatClassKey);

                        // seatClassKey（席别key)
                        String seatClassKey = RedisConstants.RAIL_SEAT_CLASS_PREFIX +  seatClassVO.getSeatClassId();
                        dependSingleKeys.add(seatClassKey);
                    }

                    return AggBatchResult.of(dataMap, dependSingleKeys);
                },
                RedisConstants.RAIL_AGG_SEAT_CLASS_CACHE_TTL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * 构建购票列表返回条件
     * @param trainDetailVOList
     * @param seatClassVOList
     * @return
     */
    private List<TicketQueryVO> buildTicketQueryVO(List<TrainDetailVO> trainDetailVOList,
                                                   List<SeatClassVO> seatClassVOList,
                                                   TicketQueryDTO ticketQueryDTO) {
        List<TicketQueryVO> resultList = new ArrayList<>();

        // 按trainId分组,映射到Map里面
        Map<Long, List<SeatClassVO>> seatGroupByTrainId = seatClassVOList.stream()
                .collect(Collectors.groupingBy(SeatClassVO::getTrainId));

        // 提前提取席别筛选条件，避免多次调用
        List<Integer> targetSeatTypes = ticketQueryDTO.getSeatTypes();
        boolean needFilterSeat = targetSeatTypes != null && !targetSeatTypes.isEmpty();

        for (TrainDetailVO trainDetailVO : trainDetailVOList) {
            TicketQueryVO result = new TicketQueryVO();

            // 拷贝列车属性
            Train train = new Train();
            BeanUtil.copyProperties(
                    trainDetailVO,
                    train,
                    CopyOptions.create()
                            .setFieldMapping(new HashMap<String, String>(){{
                                put("trainId", "id");
                            }})
            );
            result.setTrain(train);

            // 取出列车id
            Long trainId = trainDetailVO.getTrainId();

            // 拷贝席别信息
            // 从Map中取该列车的席别列表（未过滤）
            List<SeatClassVO> seatVOs = seatGroupByTrainId.getOrDefault(trainId, new ArrayList<>());

            // ===================== 核心修改：按席别类型筛选 =====================
            boolean isTrainMatch = true; // 默认匹配（无席别筛选条件时）
            if (needFilterSeat) {
                // 判定：该列车是否有至少一个席别匹配seatTypes
                isTrainMatch = seatVOs.stream()
                        .anyMatch(seatVO -> {
                            // 防护：seatTypeId为空时不匹配
                            return seatVO.getSeatType() != null
                                    && targetSeatTypes.contains(seatVO.getSeatType());
                        });
            }

            // 如果列车不匹配（无席别符合条件），直接跳过该列车
            if (!isTrainMatch) {
                continue;
            }
            // ==================================================================
            List<SeatClassFrontVO> frontVOs = BeanUtil.copyToList(seatVOs, SeatClassFrontVO.class);
            result.setSeatClassFrontVOList(frontVOs);

            // 拷贝列车类型信息
            List<TrainTypeVO> trainTypeVOList = trainDetailVO.getTrainTypeVOList();
            result.setTrainTypeVOList(trainTypeVOList);

            // 3.3.拷贝其它属性
            BeanUtil.copyProperties(trainDetailVO, result);

            // 3.4.计算历经时间
            Integer duration = calculateDurationInMinutes(result.getDepartureTime(), result.getArrivalTime());
            result.setDuration(duration);

            // 3.5.始发站和终点站判断
            Integer departureStationId = trainDetailVO.getDepartureStationId();
            boolean isDeparture = checkDepartureStation(trainId, departureStationId);
            Integer arrivalStationId = trainDetailVO.getArrivalStationId();
            boolean isArrival = checkTerminalStation(trainId, arrivalStationId);
            result.setDepartureFlag(isDeparture);
            result.setArrivalFlag(isArrival);

            resultList.add(result);
        }

        return resultList;
    }

    private List<TrainDetailVO> getTrainDetailVOS(TicketQueryDTO ticketQueryDTO) {
        // ========== 构造缓存Key的核心维度 ==========
        LocalDate departureDate = ticketQueryDTO.getDepartureDate(); // 日期
        List<String> departureCodes = ticketQueryDTO.getDepartureCodes(); // 出发站编码
        List<String> arrivalCodes = ticketQueryDTO.getArrivalCodes(); // 到达站编码

        // ========== 先查缓存，缓存命中则内存筛选 ==========
        List<TrainDetailVO> trainDetailVOList = new ArrayList<>();
        // 遍历所有出发/到达站组合（适配多站点查询）
        for (String depCode : departureCodes) {
            for (String arrCode : arrivalCodes) {
                // 构建基础车次缓存Key
                String aggKey = buildTrainBaseKey(departureDate, depCode, arrCode);
                if (StringUtils.isEmpty(aggKey)) {
                    continue; // 避免空Key导致缓存操作失败
                }
                // 缓存订单分页查询信息
                TypeReference<List<TrainDetailVO>> typeRef = new TypeReference<List<TrainDetailVO>>() {};
                List<TrainDetailVO> detailVOS = cacheClient.queryAggCache(
                        aggKey,
                        typeRef,
                        // 缓存未命中时，查库
                        dto -> {
                            List<TrainDetailVO> trainDetailVOS = stationMapper.getTrainDetailsByRouteAndDate(departureDate, depCode, arrCode);

                            // 组装所有依赖的单表Key
                            List<String> dependSingleKeys = new ArrayList<>();
                            for (TrainDetailVO trainDetailVO : trainDetailVOS) {
                                // trainKey
                                String trainKey = RedisConstants.RAIL_TRAIN_PREFIX + trainDetailVO.getTrainId();
                                dependSingleKeys.add(trainKey);

                                // stationKey
                                String depKey = RedisConstants.RAIL_STATION_PREFIX + trainDetailVO.getDepartureCode();
                                String arrKey = RedisConstants.RAIL_STATION_PREFIX + trainDetailVO.getArrivalCode();
                                dependSingleKeys.add(depKey);
                                dependSingleKeys.add(arrKey);

                                // trainStopStationKey
                                String stopStationKey = RedisConstants.RAIL_TRAIN_STOP_STATION_PREFIX + trainDetailVO.getTrainId();
                                dependSingleKeys.add(stopStationKey);


                                // trainTypeKeys
                                List<TrainTypeVO> trainTypeVOList = trainDetailVO.getTrainTypeVOList();
                                List<String> trainTypeKeys = trainTypeVOList.stream()
                                        .filter(typeVO -> typeVO.getTypeId() != null) // 防护：typeId为空跳过
                                        .map(trainTypeVO -> RedisConstants.RAIL_TRAIN_TRAIN_TYPE_PREFIX +
                                                trainDetailVO.getTrainId() +
                                                ":" +
                                                trainTypeVO.getTypeId())
                                        .toList();
                                dependSingleKeys.addAll(trainTypeKeys);
                            }

                            return AggCacheResult.of(trainDetailVOS, dependSingleKeys);
                        },
                        ticketQueryDTO,
                        RedisConstants.RAIL_TRAIN_BASE_CACHE_TTL,
                        TimeUnit.MINUTES
                );

                trainDetailVOList.addAll(detailVOS);
            }
        }

        // ========== 内存筛选列车类型 ==========
        // 筛选列车类型（trainTypeIds）
        if (ticketQueryDTO.getTrainTypeIds() != null && !ticketQueryDTO.getTrainTypeIds().isEmpty()) {
            trainDetailVOList = trainDetailVOList.stream()
                    .filter(vo -> {
                        List<TrainTypeVO> typeVOList = vo.getTrainTypeVOList();
                        if (typeVOList == null || typeVOList.isEmpty()) {
                            return false;
                        }
                        return typeVOList.stream()
                                .anyMatch(typeVO -> ticketQueryDTO.getTrainTypeIds().contains(typeVO.getTypeId()));
                    })
                    .collect(Collectors.toList());
        }
        return trainDetailVOList;
    }

    private String buildTrainBaseKey(LocalDate departureDate, String depCode, String arrCode) {
        // 防护：日期为空直接返回null
        if (departureDate == null) {
            return null;
        }
        // 空值替换为占位符（_），避免连续分隔符
        String safeDepCode = depCode == null ? "_" : depCode;
        String safeArrCode = arrCode == null ? "_" : arrCode;
        // 构建key：格式统一为「前缀:日期:出发站:到达站」
        return String.format("%s%s:%s:%s",
                RedisConstants.RAIL_AGG_TRAIN_BASE_INFO_PREFIX,
                departureDate,
                safeDepCode,
                safeArrCode);
    }

    /**
     * 查询拟购票信息
     * @param plannedTicketQueryDTO
     * @return
     */
    public TicketQueryVO queryPlannedTicket(PlannedTicketQueryDTO plannedTicketQueryDTO) {
        TicketQueryVO ticketQueryVO = new TicketQueryVO();

        // 1.查询列车表属性
        Long trainId = plannedTicketQueryDTO.getTrainId();
        Train train = trainMapper.getById(trainId);
        ticketQueryVO.setTrain(train);

        // 2.查询经停站相关信息
        StopInfoDTO stopInfoDTO = trainStopStationMapper.getStopInfoByQueryDTO(plannedTicketQueryDTO);
        BeanUtils.copyProperties(stopInfoDTO, ticketQueryVO);

        // 3.查询席别类型
        List<SeatQueryDTO> seatQueryDTOList = new ArrayList<>();
        SeatQueryDTO seatQueryDTO = new SeatQueryDTO();
        seatQueryDTO.setTrainId(trainId);
        seatQueryDTO.setStartSequence(stopInfoDTO.getDepartureSequence());
        seatQueryDTO.setEndSequence(stopInfoDTO.getArrivalSequence());
        seatQueryDTOList.add(seatQueryDTO);
        List<SeatClassVO> seatClassVOList = listSeatClassByTrainInterval(seatQueryDTOList);
        List<SeatClassFrontVO> frontVOs = BeanUtil.copyToList(seatClassVOList, SeatClassFrontVO.class);
        ticketQueryVO.setSeatClassFrontVOList(frontVOs);

        // 4.查询列车类型信息
        List<TrainTypeVO> trainTypeVOList = trainTypeDictMapper.getTrainTypeDictByTrainId(trainId);
        ticketQueryVO.setTrainTypeVOList(trainTypeVOList);

        // 5.计算历经时间
        Integer duration = calculateDurationInMinutes(stopInfoDTO.getDepartureTime(), stopInfoDTO.getArrivalTime());
        ticketQueryVO.setDuration(duration);

        // 6.始发站和终点站判断
        Integer departureStationId = stopInfoDTO.getDepartureStationId();
        boolean isDeparture = checkDepartureStation(trainId, departureStationId);
        Integer arrivalStationId = stopInfoDTO.getArrivalStationId();
        boolean isArrival = checkTerminalStation(trainId, arrivalStationId);
        ticketQueryVO.setDepartureFlag(isDeparture);
        ticketQueryVO.setArrivalFlag(isArrival);

        return ticketQueryVO;
    }

    private List<SeatClassVO> listSeatClassByTrainInterval(List<SeatQueryDTO> seatQueryDTOList) {
        return seatClassMapper.batchQuerySeatInfoByDTOList(seatQueryDTOList);
    }

    /**
     * 查询可用座位
     * @param randomSeatQueryDTO
     * @return
     */
    public List<AvailableSeatDTO> getAvailableSeats(RandomSeatQueryDTO randomSeatQueryDTO) {
        List<AvailableSeatDTO> availableSeatDTOList = new ArrayList<>();

        // 按seatType对seatTypes进行分类
        List<Integer> seatTypes = randomSeatQueryDTO.getSeatTypes();
        Map<Integer, Integer> countSeatTypes = countSeatTypes(seatTypes);

        // 遍历查询可用的座位
        for (Map.Entry<Integer, Integer> entry : countSeatTypes.entrySet()) {
            Integer seatType = entry.getKey(); // 获取seatType（键）
            Integer requiredCount = entry.getValue();  // 获取出现次数（值）

            // 构建查询条件
            SeatTypeQueryDTO seatTypeQueryDTO = BeanUtil.copyProperties(randomSeatQueryDTO, SeatTypeQueryDTO.class);
            seatTypeQueryDTO.setSeatType(seatType);
            seatTypeQueryDTO.setRequiredCount(requiredCount);
            List<AvailableSeatDTO> availableSeatDTOs = seatClassMapper.getAvailableSeatsBySeatTypeQueryDTO(seatTypeQueryDTO);

            // 判断取到的座位数量是否满足要求
            if(availableSeatDTOs == null || availableSeatDTOs.size() < requiredCount) {
                throw new BusinessException("空座位数量不足");
            }

            // 将availableSeatDTOs批量添加到availableSeatDTOList中
            availableSeatDTOList.addAll(availableSeatDTOs);
        }

        return availableSeatDTOList;
    }

    /**
     * 更新座位占用区间，并同步新的座位状态
     * @param sioDTO
     */
    public void updateSeatStatus(SeatIntervalOccupyDTO sioDTO) {
        List<SeatIntervalOccupyInsertDTO> insertDTOList = sioDTO.getInsertDTOList();
        List<SeatIntervalOccupyUpdateDTO> updateDTOList = sioDTO.getUpdateDTOList();

        // 1.变动座区间占用记录
        operateSeatIntervalOccupy(insertDTOList, updateDTOList);


        // 2.批量更新座位状态
        // 构建更新条件
        List<UpdateSeatStatusDTO> updateSeatStatusDTOList = new ArrayList<>();
        if(insertDTOList != null && !insertDTOList.isEmpty()) {
            List<UpdateSeatStatusDTO> insertConverted = BeanUtils.copyToList(insertDTOList, UpdateSeatStatusDTO.class);
            updateSeatStatusDTOList.addAll(insertConverted);
        }
        if(updateDTOList != null && !updateDTOList.isEmpty()) {
            List<UpdateSeatStatusDTO> updateConverted = BeanUtils.copyToList(updateDTOList, UpdateSeatStatusDTO.class);
            updateSeatStatusDTOList.addAll(updateConverted);
        }

        // 获取座位状态集合
        List<SeatStatusUpdateConditionDTO> seatStatusUpdateDTOList = checkSeatIntervalOccupationStatus(updateSeatStatusDTOList);
        if (seatStatusUpdateDTOList == null || seatStatusUpdateDTOList.isEmpty()) {
            throw new BusinessException("待更新的座位状态列表为空");
        }
        // 批量更新座位状态
        trainSeatMapper.batchUpdateSeatStatus(seatStatusUpdateDTOList);
    }

    private void operateSeatIntervalOccupy(List<SeatIntervalOccupyInsertDTO> insertDTOList, List<SeatIntervalOccupyUpdateDTO> updateDTOList) {
        // 1 先执行更新操作，避免一同修改新增的数据
        if (updateDTOList != null && !updateDTOList.isEmpty()) {
            List<SeatIntervalOccupyModifyDTO> occupyModifyDTOS = BeanUtils.copyToList(updateDTOList, SeatIntervalOccupyModifyDTO.class);
            if (CollectionUtils.isNotEmpty(occupyModifyDTOS)) {
                seatIntervalOccupyMapper.batchUpdateSIOOccupyRecodes(occupyModifyDTOS);
            }
        }

        // 2.新增操作
        if (insertDTOList == null || insertDTOList.isEmpty()) {
            return;
        }
        // 查询开始站序，结束站序
        SequenceQueryDTO sequenceQueryDTO = BeanUtil.copyProperties(insertDTOList.getFirst(), SequenceQueryDTO.class);
        SequenceDTO seqs = trainStopStationMapper.getSequenceInfo(sequenceQueryDTO);
        // 查询座位ID
        List<SeatInfoQueryDTO> seatInfoQueryDTOList = BeanUtils.copyToList(insertDTOList, SeatInfoQueryDTO.class);
        List<Long> seatIdList = trainSeatMapper.getSeatIdByQueryDTO(seatInfoQueryDTOList);

        // 新增条件构建
        List<SeatIntervalOccupy> seatIntervalOccupyList = BeanUtils.copyToList(insertDTOList, SeatIntervalOccupy.class);
        for (int i = 0; i < seatIntervalOccupyList.size(); i++) {
            SeatIntervalOccupy seatIntervalOccupy = seatIntervalOccupyList.get(i);

            // 设置其它属性
            Long seatId = seatIdList.get(i);
            seatIntervalOccupy.setSeatId(seatId);
            seatIntervalOccupy.setStartSequence(seqs.getStartSequence());
            seatIntervalOccupy.setEndSequence(seqs.getEndSequence());
            seatIntervalOccupy.setCreateTime(LocalDateTime.now());
            if (seatIntervalOccupy.getOrderType() == OrderTypeConstants.PREORDER) {
                seatIntervalOccupy.setExpireTime(calculateExpireTime());
            }
        }
        if (CollectionUtils.isNotEmpty(seatIntervalOccupyList)) {
            seatIntervalOccupyMapper.batchInsertSIOOccupyRecords(seatIntervalOccupyList);
        }
    }

    /**
     * 查看区间状态
     * @param updateSeatStatusDTOList
     * @return
     */
    private List<SeatStatusUpdateConditionDTO> checkSeatIntervalOccupationStatus(List<UpdateSeatStatusDTO> updateSeatStatusDTOList) {
        // 查询列车下指定的席别类型下的指定座位的占用区间
        List<IntervalOccupyDTO> intervalOccupyDTOList = seatIntervalOccupyMapper.getIntervalOccupy(updateSeatStatusDTOList);
        // 合并区间，修改座位状态
        if(intervalOccupyDTOList == null || intervalOccupyDTOList.isEmpty()) {
            throw new BusinessException("占用区间列表为空");
        }

        // 返回的状态集合
        Long trainId = intervalOccupyDTOList.get(0).getTrainId();
        List<SeatStatusUpdateConditionDTO> seatStatusUpdateConditionDTOList = BeanUtil
                        .copyToList(intervalOccupyDTOList, SeatStatusUpdateConditionDTO.class);

        for (int i = 0; i < intervalOccupyDTOList.size(); i++) {
            IntervalOccupyDTO intervalOccupyDTO = intervalOccupyDTOList.get(i);
            List<SequenceDTO> intervalList = intervalOccupyDTO.getIntervalList();

            Integer seatStatus = getSeatStatus(intervalList, trainId);

            seatStatusUpdateConditionDTOList.get(i).setStatus(seatStatus);
        }
        return seatStatusUpdateConditionDTOList;
    }

    /**
     * 判断座位状态
     * @param intervalList
     * @param trainId
     * @return
     */
    private Integer getSeatStatus(List<SequenceDTO> intervalList, Long trainId) {
        // 无占用
        if(intervalList == null || intervalList.isEmpty()) {
            // 无占用
            return SeatStatusConstants.AVAILABLE;
        }

        // 获取终点站站序
        Integer beginSeq = 1;
        Integer terminalSeq = trainStopStationMapper.getTerminalSequence(trainId);
        // 开始站序不是起点站序或最后站序不是终点站序，则部分占用
        if(intervalList.getFirst().getStartSequence() != beginSeq || intervalList.getLast().getEndSequence() != terminalSeq) {
            return SeatStatusConstants.PARTIALLY_OCCUPIED;
        }

        // 其余为部分或全部占用
        Integer preArrSeq = 1;
        for (SequenceDTO interval : intervalList) {
            Integer depSeq = interval.getStartSequence();
            Integer arrSeq = interval.getEndSequence();

            if(arrSeq > preArrSeq) {
                return SeatStatusConstants.PARTIALLY_OCCUPIED;
            }
        }
        return SeatStatusConstants.FULLY_OCCUPIED;
    }

    /**
     * 按seatType对seatTypes进行分类
     * @param seatTypes
     * @return
     */
    private Map<Integer, Integer> countSeatTypes(List<Integer> seatTypes) {
        // 处理null列表（返回空Map），非空则统计
        return seatTypes == null ? new HashMap<>() :
                seatTypes.stream()
                        .filter(seatType -> seatType != null) // 过滤null（可选）
                        .collect(Collectors.groupingBy(
                                Function.identity(), // key：seatType本身
                                Collectors.summingInt(e -> 1) // value：出现次数（每次+1）
                        ));
    }


    /**
     * 判断站点是否为列车的始发站
     */
    public boolean checkDepartureStation(Long trainId, Integer stationId) {
        return trainStopStationMapper.isDepartureStation(trainId, stationId);
    }

    /**
     * 判断站点是否为列车的终点站
     */
    public boolean checkTerminalStation(Long trainId, Integer stationId) {
        return trainStopStationMapper.isTerminalStation(trainId, stationId);
    }

    /**
     * 计算历经时长
     */
    public Integer calculateDurationInMinutes (LocalDateTime departureTime, LocalDateTime arrivalTime) {
        // 计算两个时间的差值（分钟）
        long minutesLong = Duration.between(departureTime, arrivalTime).toMinutes();

        // 转为整形返回
        Integer minutesInteger;
        if (minutesLong > Integer.MAX_VALUE) {
            // 超出最大值，按业务需求处理（如取最大值）
            minutesInteger = Integer.MAX_VALUE;
        } else if (minutesLong < Integer.MIN_VALUE) {
            // 超出最小值（时间差为负时可能触发，需先确保 arrivalTime 晚于 departureTime）
            minutesInteger = Integer.MIN_VALUE;
        } else {
            // 在范围内，安全转换
            minutesInteger = (int) minutesLong;
        }
        return minutesInteger;
    }

    /**
     * 计算过期时间
     * @return
     */
    public LocalDateTime calculateExpireTime() {
        // 1. 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 2. 处理null情况（避免空指针，设置默认值，例如15分钟）
        int minutes = (preOrderExpireMinutes != null) ? preOrderExpireMinutes : 15;

        // 3. 计算过期时间：当前时间 + 过期分钟数
        LocalDateTime expireTime = now.plusMinutes(minutes);

        return expireTime;
    }
}
