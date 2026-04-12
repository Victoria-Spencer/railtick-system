package org.rail.ticketservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import org.apache.commons.lang.StringUtils;
import org.rail.api.constant.OrderTypeConstants;
import org.rail.api.constant.SeatIntervalStatusConstants;
import org.rail.common.core.util.BeanConvertUtil;
import org.rail.common.core.util.SnowflakeIdGenerator;
import org.rail.common.redis.api.ICacheClient;
import org.rail.common.redis.constant.RedisConstants;
import org.rail.common.core.exception.BusinessException;
import org.rail.common.redis.result.AggBatchResult;
import org.rail.common.redis.result.AggCacheResult;
import org.rail.common.core.util.BeanUtils;
import org.rail.api.dto.*;
import org.rail.ticketservice.constant.SeatStatusConstants;
import org.rail.ticketservice.mapper.*;
import org.rail.ticketservice.pojo.dto.*;
import org.rail.ticketservice.pojo.entity.SeatIntervalOccupy;
import org.rail.ticketservice.pojo.entity.Station;
import org.rail.ticketservice.pojo.entity.Train;
import org.rail.ticketservice.pojo.vo.*;
import org.rail.ticketservice.service.TicketService;
import org.rail.ticketservice.task.StationLocalCacheTask;
import org.rail.ticketservice.task.TrainStopStationLocalCacheTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.rail.common.redis.constant.RedisConstants.RAIL_SEAT_OCCUPY_FORMAL_EXPIRE_MINUTES;
import static org.rail.common.redis.constant.RedisConstants.RAIL_SEAT_OCCUPY_LOCK_EXPIRE_MINUTES;

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
    private ICacheClient cacheClient;
    @Autowired
    private StationLocalCacheTask stationCacheTask;
    @Autowired
    private TrainStopStationLocalCacheTask trainStopCacheTask;

    // 从配置文件注入预订单有效期（分钟）
    @Value("${order.pre.expire-minutes : 15}") // 默认15分钟
    private Integer preOrderExpireMinutes;

    /**
     * 查询购票列表（新增分层次缓存）
     * @param ticketQueryDTO 购票查询参数DTO
     * @return 购票列表VO
     */
    @Override
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
     * @param trainDetailVOList 车次详情列表（包含trainId等核心查询维度）
     * @return 席别信息列表
     */
    private List<SeatClassVO> querySeatClassData(List<TrainDetailVO> trainDetailVOList) {
        // 1. 转换入参：TrainDetailVO -> SeatQueryDTO
        List<SeatQueryDTO> seatQueryDTOList = BeanUtil.copyToList(trainDetailVOList, SeatQueryDTO.class);

        // 2. 核心：构建「trainId → SeatQueryDTO列表」的映射（仅一次遍历，预处理）
        // 目的：后续通过VO的trainId快速拿到对应DTO
        Map<Long, List<SeatQueryDTO>> trainId2DtosMap = seatQueryDTOList.stream()
                .collect(Collectors.groupingBy(SeatQueryDTO::getTrainId)); // 按trainId分组

        // 3. 查缓存
        return batchQuerySeatClassCache(seatQueryDTOList, trainId2DtosMap);
    }

    /**
     * 席别批量聚合缓存调用
     */
    private List<SeatClassVO> batchQuerySeatClassCache(List<SeatQueryDTO> seatQueryDTOList, Map<Long, List<SeatQueryDTO>> trainId2DtosMap) {
        Function<SeatQueryDTO, String> keyGenerator = dto ->
                String.format("%s%d:%d:%d",
                        RedisConstants.RAIL_AGG_SEAT_CLASS,
                        dto.getTrainId(),
                        dto.getStartSequence(),
                        dto.getEndSequence());

        TypeReference<SeatClassVO> typeRef = new TypeReference<>() {};

        return cacheClient.batchQueryAggCache(
                keyGenerator,
                seatQueryDTOList,
                typeRef,
                missDtos -> querySeatClassDb(missDtos, keyGenerator, trainId2DtosMap),
                RedisConstants.RAIL_AGG_SEAT_CLASS_CACHE_TTL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * 缓存未命中时：批量查询席别数据库 + 构建聚合缓存结果
     * @param missDtos 未命中缓存的DTO列表
     * @param keyGenerator 缓存Key生成器
     * @param trainId2DtosMap trainId->DTO映射
     * @return 聚合缓存批量结果
     */
    private AggBatchResult<SeatClassVO> querySeatClassDb(
            List<SeatQueryDTO> missDtos,
            Function<SeatQueryDTO, String> keyGenerator,
            Map<Long, List<SeatQueryDTO>> trainId2DtosMap
    ) {
        // 用【缓存未命中的DTO列表】查询数据库（而非全量 seatQueryDTOList）
        List<SeatClassVO> seatClassVOList = seatClassMapper.batchQuerySeatInfoByDTOList(missDtos);
        // 构建 aggKey -> SeatClassVO 的映射
        Map<String, SeatClassVO> dataMap = getSeatClassVOMap(keyGenerator, trainId2DtosMap, seatClassVOList);
        // 组装所有依赖的单表Key
        List<String> dependSingleKeys = buildSeatClassDependencyKeys(seatClassVOList);
        return AggBatchResult.of(dataMap, dependSingleKeys);
    }

    /**
     * 构建席别相关的聚合缓存依赖单表Key
     */
    private List<String> buildSeatClassDependencyKeys(List<SeatClassVO> seatClassVOList) {
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
        return dependSingleKeys;
    }

    private Map<String, SeatClassVO> getSeatClassVOMap(Function<SeatQueryDTO, String> keyGenerator, Map<Long, List<SeatQueryDTO>> trainId2DtosMap, List<SeatClassVO> seatClassVOList) {
        Map<String, SeatClassVO> dataMap = new HashMap<>();
        for (SeatClassVO vo : seatClassVOList) {
            // 从预处理的映射中，根据VO的trainId取对应的DTO列表
            List<SeatQueryDTO> dtos = trainId2DtosMap.get(vo.getTrainId());
            if (dtos != null && !dtos.isEmpty()) {
                SeatQueryDTO matchDto = dtos.getFirst();
                String aggKey = keyGenerator.apply(matchDto);
                dataMap.put(aggKey, vo);
            }
        }
        return dataMap;
    }

    /**
     * 构建购票列表返回条件
     * @param trainDetailVOList 车次详情列表
     * @param seatClassVOList 席别信息列表
     * @return 购票列表VO
     */
    private List<TicketQueryVO> buildTicketQueryVO(List<TrainDetailVO> trainDetailVOList,
                                                   List<SeatClassVO> seatClassVOList,
                                                   TicketQueryDTO ticketQueryDTO) {
        if (CollectionUtils.isEmpty(trainDetailVOList)) {
            return Collections.emptyList();
        }

        // 按trainId分组,映射到Map里面
        Map<Long, List<SeatClassVO>> seatGroupByTrainId = seatClassVOList.stream()
                .collect(Collectors.groupingBy(SeatClassVO::getTrainId));
        // 提前提取席别筛选条件，避免多次调用
        List<Integer> targetSeatTypes = ticketQueryDTO.getSeatTypes();
        boolean needFilterSeat = targetSeatTypes != null && !targetSeatTypes.isEmpty();

        List<TicketQueryVO> resultList = new ArrayList<>();
        for (TrainDetailVO detailVO : trainDetailVOList) {
            // 1. 筛选列车（席别条件）
            if (!isTrainMatchSeatType(seatGroupByTrainId, detailVO.getTrainId(), targetSeatTypes, needFilterSeat)) {
                continue;
            }
            // 2. 构建单个VO
            TicketQueryVO vo = buildSingleTicketQueryVO(detailVO, seatGroupByTrainId);
            resultList.add(vo);
        }

        return resultList;
    }

    /**
     * 列车席别匹配校验
     */
    private boolean isTrainMatchSeatType(
            Map<Long, List<SeatClassVO>> seatGroupMap,
            Long trainId,
            List<Integer> targetSeatTypes,
            boolean needFilter
    ) {
        if (!needFilter) {
            return true;
        }
        List<SeatClassVO> seatVOs = seatGroupMap.getOrDefault(trainId, Collections.emptyList());
        return seatVOs.stream()
                .anyMatch(seat -> Objects.nonNull(seat.getSeatType()) && targetSeatTypes.contains(seat.getSeatType()));
    }

    /**
     * 构建单个TicketQueryVO
     */
    private TicketQueryVO buildSingleTicketQueryVO(TrainDetailVO detailVO, Map<Long, List<SeatClassVO>> seatGroupMap) {
        TicketQueryVO result = new TicketQueryVO();
        Long trainId = detailVO.getTrainId();

        // 1. 拷贝列车基础信息
        result.setTrain(copyTrainFromDetailVO(detailVO));
        // 2. 拷贝席别信息
        List<SeatClassVO> seatVOs = seatGroupMap.getOrDefault(trainId, Collections.emptyList());
        result.setSeatClassFrontVOList(copySeatClassVO(seatVOs));
        // 3. 拷贝列车类型
        result.setTrainTypeVOList(detailVO.getTrainTypeVOList());
        // 4. 拷贝公共属性
        BeanUtil.copyProperties(detailVO, result);
        // 5. 计算历经时间
        Integer duration = calculateDurationInMinutes(result.getDepartureTime(), result.getArrivalTime());
        result.setDuration(duration);
        // 6. 始发站和终点站判断
        Integer departureStationId = detailVO.getDepartureStationId();
        result.setDepartureFlag(checkDepartureStation(trainId, departureStationId));
        Integer arrivalStationId = detailVO.getArrivalStationId();
        result.setArrivalFlag(checkTerminalStation(trainId, arrivalStationId));
        return result;
    }

    private List<SeatClassFrontVO> copySeatClassVO(List<SeatClassVO> seatVOs) {
        return BeanUtil.copyToList(seatVOs, SeatClassFrontVO.class);
    }

    private Train copyTrainFromDetailVO(TrainDetailVO detailVO) {
        // 拷贝列车属性
        Train train = new Train();
        CopyOptions copyOptions = CopyOptions.create().setFieldMapping(Collections.singletonMap("trainId", "id"));
        BeanUtil.copyProperties(detailVO, train, copyOptions);
        return train;
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
                List<TrainDetailVO> detailVOS = queryTrainDetailCache(ticketQueryDTO, depCode, arrCode, departureDate);
                if (detailVOS == null) continue; // 避免空Key导致缓存操作失败
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

    /**
     * 车次路线聚合缓存调用
     */
    private List<TrainDetailVO> queryTrainDetailCache(TicketQueryDTO ticketQueryDTO, String depCode, String arrCode, LocalDate departureDate) {
        // 构建基础车次缓存Key
        String aggKey = buildTrainBaseKey(departureDate, depCode, arrCode);
        if (StringUtils.isEmpty(aggKey)) {
            return null;
        }
        // 缓存订单分页查询信息
        TypeReference<List<TrainDetailVO>> typeRef = new TypeReference<>() {};
        return cacheClient.queryAggCacheWithNullCache(
                aggKey,
                typeRef,
                // 缓存未命中时，查库
                dto -> queryTrainDetailDb(dto, departureDate, depCode, arrCode),
                ticketQueryDTO,
                RedisConstants.RAIL_TRAIN_BASE_CACHE_TTL_HOURS,
                TimeUnit.HOURS
        );
    }

    /**
     * 缓存未命中时：查询车次详情数据库 + 构建聚合缓存依赖Key
     * @param dto 查询参数DTO
     * @param departureDate 出发日期
     * @param depCode 出发站编码
     * @param arrCode 到达站编码
     * @return 聚合缓存结果（数据+依赖单表Key）
     */
    private AggCacheResult<List<TrainDetailVO>> queryTrainDetailDb(
            TicketQueryDTO dto,
            LocalDate departureDate,
            String depCode,
            String arrCode
    ) {
        List<TrainDetailVO> trainDetailVOS = stationMapper.getTrainDetailsByRouteAndDate(departureDate, depCode, arrCode);

        // 组装所有依赖的单表Key
        List<String> dependSingleKeys = buildTrainDetailDependencyKeys(trainDetailVOS);

        return AggCacheResult.of(trainDetailVOS, dependSingleKeys);
    }


    /**
     * 构建车次详情相关的聚合缓存依赖单表Key
     */
    private List<String> buildTrainDetailDependencyKeys(List<TrainDetailVO> trainDetailVOS) {
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
        return dependSingleKeys;
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
     * @param plannedTicketQueryDTO 拟购票查询参数DTO
     * @return 拟购票信息VO
     */
    @Override
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
     * @param randomSeatQueryDTO 随机选座查询参数DTO
     * @return 可用座位列表DTO
     */
    @Override
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
     */
    @Override
    public void updateSeatStatus(BatchSeatIntervalInsertDTO batchDTO) {
        // 1.变动座位区间占用记录
        operateSeatIntervalOccupy(batchDTO);

        // 2.批量更新座位状态
        batchUpdateSeatStatus(batchDTO);
    }

    /**
     * 批量更新座位状态（根据最新的占用区间记录，重新计算每个座位的状态，并更新到Redis缓存）
     */
    private void batchUpdateSeatStatus(BatchSeatIntervalInsertDTO batchDTO) {
        List<UpdateSeatStatusDTO> updateSeatStatusDTOList = new ArrayList<>();
        if(batchDTO != null && !CollectionUtils.isEmpty(batchDTO.getSeatList())) {
            updateSeatStatusDTOList = BeanConvertUtil.copyWithCommonField(
                    batchDTO,
                    batchDTO.getSeatList(),
                    UpdateSeatStatusDTO.class
            );
        }

        if(batchDTO == null || CollectionUtils.isEmpty(updateSeatStatusDTOList)) {
            return;
        }
        Long trainId = batchDTO.getTrainId();
        List<SeatBaseDTO> seatList = batchDTO.getSeatList();

        List<Long> seatIdList = getSeatIdListBySeatNos(seatList, trainId);
        Map<Long, Integer> seatStatusMap = calculateSeatStatusMap(trainId, seatIdList);

        Map<String, Object> statusUpdateMap = buildStatusUpdateMap(seatStatusMap);
        if (!CollectionUtils.isEmpty(statusUpdateMap)) {
            String hashKey = buildSeatHashKey(trainId);
            cacheClient.hPutAll(hashKey, statusUpdateMap);
        }
    }

    /**
     * 构建Redis Hash结构的座位状态批量更新数据
     * @param seatStatusMap 座位ID-状态映射
     * @return Redis Hash批量更新的键值对
     */
    private Map<String, Object> buildStatusUpdateMap(Map<Long, Integer> seatStatusMap) {
        if (MapUtil.isEmpty(seatStatusMap)) {
            return new HashMap<>();
        }

        Map<String, Object> statusUpdateMap = new HashMap<>(seatStatusMap.size());
        for (Map.Entry<Long, Integer> entry : seatStatusMap.entrySet()) {
            Long seatId = entry.getKey();
            Integer status = entry.getValue();

            String fieldStatus = buildSeatFieldKey(seatId, "status");
            statusUpdateMap.put(fieldStatus, status);
        }

        return statusUpdateMap;
    }

    /**
     * 构建单字段的Field名称：seatId:字段名
     */
    private String buildSeatFieldKey(Long seatId, String fieldName) {
        return String.format("SeatId:%d:%s", seatId, fieldName);
    }

    /**
     * 批量计算座位的最新状态
     */
    private Map<Long, Integer> calculateSeatStatusMap(Long trainId, List<Long> seatIdList) {
        Map<Long, Integer> statusMap = new HashMap<>(seatIdList.size());
        for (Long seatId : seatIdList) {
            // 从Redis查询该座位的所有占用区间，计算状态（复用你原有的getSeatStatus逻辑）
            Integer status = calculateSeatStatusFromRedis(trainId, seatId);
            statusMap.put(seatId, status);
        }
        return statusMap;
    }

    /**
     * 从Redis计算单个座位的状态
     */
    private Integer calculateSeatStatusFromRedis(Long trainId, Long seatId) {
        Integer terminalSeq = getTrainTerminalSeqFromCache(trainId);
        int minOffset = 1;
        int maxOffset = terminalSeq - 1;

        // 读取该座位的正式订单Bitmap（永久占用）
        String formalKey = RedisConstants.RAIL_BITMAP_SEAT_FORMAL_PREFIX + "trainId:" + trainId + ":seatId:" + seatId;
        // 读取该座位的预订单Bitmap（临时占用）
        String tempKey = RedisConstants.RAIL_BITMAP_SEAT_TEMP_LOCK_PREFIX + "trainId:" + trainId + ":seatId:" + seatId;

        byte[] formalBytes = cacheClient.get(formalKey, byte[].class);
        byte[] tempBytes = cacheClient.get(tempKey, byte[].class);

        byte[] mergedBytes = mergeBytesOr(formalBytes, tempBytes);

        long occupiedCount = countBitsInRange(mergedBytes, minOffset, maxOffset);

        if (occupiedCount == 0) {
            return SeatStatusConstants.AVAILABLE;
        } else if (occupiedCount == terminalSeq - 1) {
            return SeatStatusConstants.FULLY_OCCUPIED;
        } else {
            return SeatStatusConstants.PARTIALLY_OCCUPIED;
        }
    }

    /**
     * 从缓存获取列车终点站序列
     */
    private Integer getTrainTerminalSeqFromCache(Long trainId) {
        return trainStopCacheTask.getTrainTerminalSeq(trainId);
    }

    /**
     * 统计 Bitmap 字节数组中 [startOffset, endOffset] 范围内 bit=1 的数量
     * 兼容Redis大端存储
     */
    private long countBitsInRange(byte[] bytes, int startOffset, int endOffset) {
        if (bytes == null || bytes.length == 0 || startOffset > endOffset) {
            return 0;
        }

        long count = 0;
        for (int offset = startOffset; offset <= endOffset; offset++) {
            if (getBit(bytes, offset)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取指定offset的位值
     */
    private boolean getBit(byte[] bytes, int offset) {
        if (offset < 0) return false;
        int byteIdx = offset / 8;
        int bitPos = 7 - (offset % 8); // 大端存储：最高位优先

        if (byteIdx >= bytes.length) return false;
        return (bytes[byteIdx] & (1 << bitPos)) != 0;
    }

    /**
     * 两个字节数组 按位或 合并
     * null 视为全0字节数组
     */
    private byte[] mergeBytesOr(byte[] a, byte[] b) {
        if (a == null) return b == null ? new byte[0] : b;
        if (b == null) return a;

        int maxLen = Math.max(a.length, b.length);
        byte[] result = new byte[maxLen];

        for (int i = 0; i < maxLen; i++) {
            byte b1 = i < a.length ? a[i] : 0;
            byte b2 = i < b.length ? b[i] : 0;
            result[i] = (byte) (b1 | b2);
        }
        return result;
    }

    /**
     * 操作座位区间占用记录（新增/更新），并同步到Redis Bitmap和占用记录缓存
     */
    private void operateSeatIntervalOccupy(BatchSeatIntervalInsertDTO batchDTO) {
        if (batchDTO == null) {
            return;
        }
        Long trainId = batchDTO.getTrainId();
        List<SeatBaseDTO> seatList = batchDTO.getSeatList();

        // 获取站点序列 + 座位ID列表
        SequenceDTO seqs = getStationSequence(batchDTO);
        List<Long> seatIdList = getSeatIdListBySeatNos(seatList, trainId);

        List<SeatIntervalOccupy> occupyList = BeanConvertUtil.copyWithCommonField(
                batchDTO,
                batchDTO.getSeatList(),
                SeatIntervalOccupy.class
        );

        batchCacheSeatOccupy(occupyList, seatIdList, seqs, trainId);
    }

    /**
     * 批量缓存：Bitmap区间标记 + String占用记录
     */
    private void batchCacheSeatOccupy(List<SeatIntervalOccupy> occupyList, List<Long> seatIdList, SequenceDTO seqs, Long trainId) {
        if(CollectionUtils.isEmpty(occupyList)) return;

        String recordKey = buildSeatOccupyHashKey(trainId);
        Map<String, Object> batchHashMap = new HashMap<>();

        boolean isLockedBatch = SeatIntervalStatusConstants.LOCKED.equals(occupyList.getFirst().getStatus());

        for (int i = 0; i < occupyList.size(); i++) {
            SeatIntervalOccupy occupy = occupyList.get(i);
            Long seatId = seatIdList.get(i);
            String lockId = String.valueOf(SnowflakeIdGenerator.nextId());

            // 设置其它属性
            fillSeatOccupyCommonFields(seqs, occupy, seatId, lockId);

            // 1. 更新Bitmap占用标记
            String bitmapKey = buildSeatBitmapKey(occupy);
            cacheClient.setRangeBits(bitmapKey, occupy.getStartSequence(), occupy.getEndSequence(), true);

            // 2. 存储占用元数据
            String field = buildSeatLockFieldKey(seatId, lockId);
            batchHashMap.put(field, occupy);

            // 3. 仅锁定中：单条发送延迟释放消息
            if (isLockedBatch) {
                sendDelayReleaseMsg(trainId, seatId, lockId);
            }
        }

        if (isLockedBatch) {
            cacheClient.hPutAll(recordKey, batchHashMap, RAIL_SEAT_OCCUPY_LOCK_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } else {
            cacheClient.hPutAll(recordKey, batchHashMap, RAIL_SEAT_OCCUPY_FORMAL_EXPIRE_MINUTES, TimeUnit.MINUTES);
            // 非锁定状态：发送1次批量落库消息
            sendBatchSyncDbMsg(occupyList);
        }
    }

    /**
     * 填充座位占用记录的公共属性
     */
    private void fillSeatOccupyCommonFields(SequenceDTO seqs, SeatIntervalOccupy occupy, Long seatId, String lockId) {
        occupy.setSeatId(seatId);
        occupy.setStartSequence(seqs.getStartSequence());
        occupy.setEndSequence(seqs.getEndSequence());
        occupy.setCreateTime(LocalDateTime.now());
        occupy.setLockId(lockId);
    }

    /**
     * TODO 普通队列消息：批量落库 + 批量删除Hash
     *  触发时机：正式订单占用（立即发送）+预订单占用（延迟发送）
     */
    private void sendBatchSyncDbMsg(List<SeatIntervalOccupy> occupyList) {
        // TODO 实现：发送批量MQ消息
        //  消费者：批量入库 → 批量删除Redis Hash字段
    }

    /**
     *   TODO 延迟消息队列：发送延迟消息到队列，消息内容包含 trainId + seatId + lockId
     *    整的逻辑：发送 (key, lockId) 到队列 → 批量取出 → 进入普通队列 →
     *    消息处理器根据 key 从 Redis 获取占用记录 → 如果记录存在且 lockId 匹配，则说明锁过期，进行解锁处理（删除占用记录 + 更新Bitmap）
     */
    private void sendDelayReleaseMsg(Long trainId, Long seatId, String lockId) {
        // TODO 实现：发送延迟MQ消息(trainId+seatId+lockId)
        //  消费者：校验lockId → 存在则释放座位 → 发送普通队列落库
    }

    /**
     * 根据座位号列表批量查询座位ID
     */
    private List<Long> getSeatIdListBySeatNos(List<SeatBaseDTO> seatList, Long trainId) {
        if (CollectionUtils.isEmpty(seatList)) {
            return Collections.emptyList();
        }
        return batchGetSeatIdBySeatNo(trainId, seatList);
    }

    /**
     * 获取站点序列信息
     */
    private SequenceDTO getStationSequence(BatchSeatIntervalInsertDTO batchDTO) {
        Long trainId = batchDTO.getTrainId();
        String departureCode = batchDTO.getDepartureCode();
        String arrivalCode = batchDTO.getArrivalCode();
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
                .orElseThrow(() -> new BusinessException(stationType + "编码不存在：" + stationCode))
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
            throw new BusinessException(trainId + "车次不包含出发站：" + departureCode);
        }
        if (!stationId2SeqMap.containsKey(toStationId)) {
            throw new BusinessException(trainId + "车次不包含到达站：" + arrivalCode);
        }
        Integer startSeq = stationId2SeqMap.get(fromStationId);
        Integer endSeq = stationId2SeqMap.get(toStationId);
        if (startSeq >= endSeq) {
            throw new BusinessException("站点顺序异常：出发站序列不能大于等于到达站序列");
        }
    }

    /**
     * 构建座位占用记录的Hash Key
     */
    private String buildSeatOccupyHashKey(Long trainId) {
        return String.format("%strainId:%d",
                RedisConstants.RAIL_SEAT_OCCUPY_RECORD_PREFIX,
                trainId);
    }

    /**
     * 构建座位锁唯一Field
     */
    private String buildSeatLockFieldKey(Long seatId, String lockId) {
        return String.format("SeatId:%d:LockId:%s", seatId, lockId);
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

    /**
     * 批量根据【车次+座位号】查询座位ID
     * @param trainId    车次ID
     * @param seatList 座位号列表
     * @return 座位ID列表
     */
    private List<Long> batchGetSeatIdBySeatNo(Long trainId, List<SeatBaseDTO> seatList) {
        String hashKey = buildSeatHashKey(trainId);
        if (CollectionUtils.isEmpty(seatList)) {
            return Collections.emptyList();
        }

        // 批量构建反向映射Field ：SeatNoReverse:车厢号:座位号
        List<String> reverseFields = seatList.stream()
                .filter(seat -> StrUtil.isNotBlank(seat.getCarriageNumber()) && StrUtil.isNotBlank(seat.getSeatNo()))
                .map(seat -> buildSeatReverseFieldKey(seat.getCarriageNumber(), seat.getSeatNo()))
                .collect(Collectors.toList());

        List<String> seatUniqueDescList = seatList.stream()
                .map(seat -> seat.getCarriageNumber() + "车厢-" + seat.getSeatNo())
                .toList();

        // 批量获取座位ID
        List<Object> seatIdObjList = cacheClient.hMultiGet(hashKey, reverseFields);
        return convertAndCheckSeatIds(trainId, seatUniqueDescList, seatIdObjList);
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
     * 进行结果校验, 并将Object类型的座位ID转换为Long类型，最终返回座位ID列表
     */
    private List<Long> convertAndCheckSeatIds(Long trainId, List<String> seatUniqueDescList, List<Object> seatIdObjList) {
        List<Long> seatIdList = new ArrayList<>(seatUniqueDescList.size());

        for (int i = 0; i < seatUniqueDescList.size(); i++) {
            String seatUniqueDesc = seatUniqueDescList.get(i);
            Object seatIdObj = seatIdObjList.get(i);

            if (seatIdObj == null) {
                throw new BusinessException(String.format("%d车次-%s不存在座位", trainId, seatUniqueDesc));
            }
            if (!(seatIdObj instanceof Long)) {
                throw new BusinessException(String.format("%d车次-%s数据异常", trainId, seatUniqueDesc));
            }

            seatIdList.add((Long) seatIdObj);
        }
        return seatIdList;
    }


    /**
     * 构建座位Hash的Redis Key
     */
    private String buildSeatHashKey(Long trainId) {
        return String.format("%strainId:%d", RedisConstants.RAIL_HASH_SEAT_INFO_PREFIX, trainId);
    }

    /**
     * 判断座位状态
     * @param intervalList 座位占用区间列表（包含startSequence、endSequence等属性）
     * @param trainId 列车ID（用于查询终点站站序，判断是否完全占用）
     * @return 座位状态（0-无占用，1-部分占用，2-完全占用）
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
        if(!beginSeq.equals(intervalList.getFirst().getStartSequence()) || !terminalSeq.equals(intervalList.getLast().getEndSequence())) {
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
     * @param seatTypes 席别类型列表（可能包含重复的seatType，表示需要的该类型座位数量）
     * @return Map<seatType, count>，key为seatType，value为该类型出现的次数（即需要的座位数量）
     */
    private Map<Integer, Integer> countSeatTypes(List<Integer> seatTypes) {
        // 处理null列表（返回空Map），非空则统计
        return seatTypes == null ? new HashMap<>() :
                seatTypes.stream()
                        .filter(Objects::nonNull) // 过滤null（可选）
                        .collect(Collectors.groupingBy(
                                Function.identity(), // key：seatType本身
                                Collectors.summingInt(e -> 1) // value：出现次数（每次+1）
                        ));
    }


    /**
     * 判断站点是否为列车的始发站
     */
    private boolean checkDepartureStation(Long trainId, Integer stationId) {
        return trainStopStationMapper.isDepartureStation(trainId, stationId);
    }

    /**
     * 判断站点是否为列车的终点站
     */
    private boolean checkTerminalStation(Long trainId, Integer stationId) {
        return trainStopStationMapper.isTerminalStation(trainId, stationId);
    }

    /**
     * 计算历经时长
     */
    private Integer calculateDurationInMinutes (LocalDateTime departureTime, LocalDateTime arrivalTime) {
        // 计算两个时间的差值（分钟）
        long minutesLong = Duration.between(departureTime, arrivalTime).toMinutes();

        // 转为整形返回
        int minutesInteger;
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
}
