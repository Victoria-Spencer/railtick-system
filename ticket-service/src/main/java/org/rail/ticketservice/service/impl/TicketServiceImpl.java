package org.rail.ticketservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.TypeReference;
import com.alibaba.nacos.common.utils.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.rail.api.constant.OrderTypeConstants;
import org.rail.common.core.util.BeanConvertUtil;
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
import org.rail.ticketservice.pojo.entity.Train;
import org.rail.ticketservice.pojo.vo.*;
import org.rail.ticketservice.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
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
    @Autowired
    private SeatIntervalOccupyMapper seatIntervalOccupyMapper;
    @Autowired
    private ICacheClient cacheClient;

    // 从配置文件注入预订单有效期（分钟）
    @Value("${order.pre.expire-minutes : 15}") // 默认15分钟
    private Integer preOrderExpireMinutes;

    /**
     * 查询购票列表（新增分层次缓存）
     * @param ticketQueryDTO 购票查询参数DTO
     * @return 购票列表VO
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
        List<TrainDetailVO> detailVOS = cacheClient.queryAggCacheWithNullCache(
                aggKey,
                typeRef,
                // 缓存未命中时，查库
                dto -> queryTrainDetailDb(dto, departureDate, depCode, arrCode),
                ticketQueryDTO,
                RedisConstants.RAIL_TRAIN_BASE_CACHE_TTL_HOURS,
                TimeUnit.HOURS
        );
        return detailVOS;
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
    public void updateSeatStatus(BatchSeatIntervalInsertDTO batchDTO) {
        // 1.变动座位区间占用记录
        operateSeatIntervalOccupy(batchDTO);

        // 2.批量更新座位状态
        batchUpdateSeatStatus(batchDTO);
    }

    private void batchUpdateSeatStatus(BatchSeatIntervalInsertDTO batchDTO) {
        // 构建更新条件
        List<UpdateSeatStatusDTO> updateSeatStatusDTOList = new ArrayList<>();
        if(batchDTO != null && CollectionUtils.isNotEmpty(batchDTO.getSeatList())) {
            updateSeatStatusDTOList = BeanConvertUtil.copyWithCommonField(
                    batchDTO,
                    batchDTO.getSeatList(),
                    UpdateSeatStatusDTO.class
            );
        }

        // 获取座位状态集合
        List<SeatStatusUpdateConditionDTO> seatStatusUpdateDTOList = checkSeatIntervalOccupationStatus(updateSeatStatusDTOList);
        if (seatStatusUpdateDTOList == null || seatStatusUpdateDTOList.isEmpty()) {
            throw new BusinessException("待更新的座位状态列表为空");
        }
        // 批量更新座位状态
        trainSeatMapper.batchUpdateSeatStatus(seatStatusUpdateDTOList);
    }

    private void operateSeatIntervalOccupy(BatchSeatIntervalInsertDTO batchDTO) {
        if (batchDTO == null) {
            return;
        }
        // 查询开始站序，结束站序
        SequenceQueryDTO sequenceQueryDTO = BeanUtil.copyProperties(batchDTO, SequenceQueryDTO.class);
        SequenceDTO seqs = trainStopStationMapper.getSequenceInfo(sequenceQueryDTO);

        // 查询座位ID
        List<SeatInfoQueryDTO> seatInfoQueryDTOList = BeanConvertUtil.copyWithCommonField(
                batchDTO,
                batchDTO.getSeatList(),
                SeatInfoQueryDTO.class
        );
        List<Long> seatIdList = trainSeatMapper.getSeatIdByQueryDTO(seatInfoQueryDTOList);

        // 条件构建
        List<SeatIntervalOccupy> seatIntervalOccupyList = BeanConvertUtil.copyWithCommonField(
                batchDTO,
                batchDTO.getSeatList(),
                SeatIntervalOccupy.class
        );
        for (int i = 0; i < seatIntervalOccupyList.size(); i++) {
            SeatIntervalOccupy seatIntervalOccupy = seatIntervalOccupyList.get(i);

            // 设置其它属性
            Long seatId = seatIdList.get(i);
            seatIntervalOccupy.setSeatId(seatId);
            seatIntervalOccupy.setStartSequence(seqs.getStartSequence());
            seatIntervalOccupy.setEndSequence(seqs.getEndSequence());
            seatIntervalOccupy.setCreateTime(LocalDateTime.now());
        }
        if (CollectionUtils.isNotEmpty(seatIntervalOccupyList)) {
            seatIntervalOccupyMapper.batchInsertSIOOccupyRecords(seatIntervalOccupyList);
        }
    }

    /**
     * 查看区间状态
     * @param updateSeatStatusDTOList 待更新座位状态的DTO列表（包含trainId、seatId、startSequence、endSequence等核心查询维度）
     * @return 座位状态更新条件DTO列表（包含trainId、seatId、startSequence、endSequence、status等属性）
     */
    private List<SeatStatusUpdateConditionDTO> checkSeatIntervalOccupationStatus(List<UpdateSeatStatusDTO> updateSeatStatusDTOList) {
        // 查询列车下指定的席别类型下的指定座位的占用区间
        List<IntervalOccupyDTO> intervalOccupyDTOList = seatIntervalOccupyMapper.getIntervalOccupy(updateSeatStatusDTOList);
        // 合并区间，修改座位状态
        if(intervalOccupyDTOList == null || intervalOccupyDTOList.isEmpty()) {
            throw new BusinessException("占用区间列表为空");
        }

        // 返回的状态集合
        Long trainId = intervalOccupyDTOList.getFirst().getTrainId();
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
