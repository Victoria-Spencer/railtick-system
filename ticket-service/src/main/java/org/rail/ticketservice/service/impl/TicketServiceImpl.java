package org.rail.ticketservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import org.rail.commonapi.dto.AvailableSeatDTO;
import org.rail.commonapi.dto.RandomSeatQueryDTO;
import org.rail.commonapi.dto.SeatTypeQueryDTO;
import org.rail.commonservice.exception.BusinessException;
import org.rail.commonservice.utils.BeanUtils;
import org.rail.ticketservice.mapper.*;
import org.rail.ticketservice.pojo.dto.*;
import org.rail.ticketservice.pojo.entity.Train;
import org.rail.ticketservice.pojo.vo.*;
import org.rail.ticketservice.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 查询购票列表
     * @param ticketQueryDTO
     * @return
     */
    public List<TicketQueryVO> queryTicket(TicketQueryDTO ticketQueryDTO) {
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
        List<SeatClassVO> seatClassVOList = querySeatClassData(seatQueryDTOList);
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
     * 查询席别信息
     * @param seatQueryDTOList
     * @return
     */
    private List<SeatClassVO> querySeatClassData(List<SeatQueryDTO> seatQueryDTOList) {
        // 逻辑下沉到 SQL，根据列车id，出发站站序和到达站站序，查询席别数据（类型等）--- List
        List<SeatClassVO> seatClassVOList = seatClassMapper.batchQuerySeatInfoByDTOList(seatQueryDTOList);
        return seatClassVOList;
    }


    /**
     * 查询 trainId, departureTime, arrivalTime, departureStationId, arrivalStationId 5个属性
     * @param ticketQueryDTO
     * @return
     */

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
}
