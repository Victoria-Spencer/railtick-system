package org.rail.ticketservice.service.impl;

import org.rail.commonservice.utils.BeanUtils;
import org.rail.ticketservice.mapper.*;
import org.rail.ticketservice.pojo.dto.PlannedTicketQueryDTO;
import org.rail.ticketservice.pojo.dto.SeatClassDTO;
import org.rail.ticketservice.pojo.dto.SeatClassTotalDTO;
import org.rail.ticketservice.pojo.dto.TicketQueryDTO;
import org.rail.ticketservice.pojo.entity.Train;
import org.rail.ticketservice.pojo.vo.SeatClassVO;
import org.rail.ticketservice.pojo.vo.TicketQueryVO;
import org.rail.ticketservice.pojo.vo.TrainDetailVO;
import org.rail.ticketservice.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TicketServiceImpl implements TicketService {

    @Autowired
    private TicketMapper ticketMapper;
    @Autowired
    private StationMapper stationMapper;
    @Autowired
    private TrainStopStationMapper trainStopStationMapper;
    @Autowired
    private TrainSeatClassMapper trainSeatClassMapper;
    @Autowired
    private SeatClassMapper seatClassMapper;
    @Autowired
    private TrainSeatMapper trainSeatMapper;

    /**
     * 查询购票列表
     * @param ticketQueryDTO
     * @return
     */
    public List<TicketQueryVO> queryTicket(TicketQueryDTO ticketQueryDTO) {
        return getTicketQueryVOS(ticketQueryDTO);
    }

    /**
     * 查询拟购票信息
     * @param plannedTicketQueryDTO
     * @return
     */
    public TicketQueryVO queryPlannedTicket(PlannedTicketQueryDTO plannedTicketQueryDTO) {
        TicketQueryDTO ticketQueryDTO = BeanUtils.copyProperties(plannedTicketQueryDTO, TicketQueryDTO.class);
        // 调用getTicketQueryVOS方法查询
        List<TicketQueryVO> ticketQueryVOS = getTicketQueryVOS(ticketQueryDTO);
        if(ticketQueryVOS.isEmpty()){
            return null;
        }
        return ticketQueryVOS.get(0);
    }


    /**
     * 检索满足筛选条件的车票信息
     * @param ticketQueryDTO
     * @return
     */
    private List<TicketQueryVO> getTicketQueryVOS(TicketQueryDTO ticketQueryDTO) {
        // 查询列车ids
        List<TrainDetailVO> trainDetailVOList = queryTrains(ticketQueryDTO);

        /**       循环        **/
        List<TicketQueryVO> resultList = new ArrayList<>();

        for (TrainDetailVO trainDetailVO : trainDetailVOList) {
            // 取出列车id
            Long trainId = trainDetailVO.getTrainId();

            TicketQueryVO ticketQueryVO = new TicketQueryVO();

            // 拷贝出发时间和结束时间
            BeanUtils.copyProperties(trainDetailVO, ticketQueryVO);

            // 根据列车id，查询列车数据
            Train train = ticketMapper.getTrainById(trainId);
            // 拷贝列车属性
            ticketQueryVO.setTrain(train);

            // 根据列车id，查询席别数据（类型等）--- List

            // 查询席别信息
            Integer startSequence = trainDetailVO.getStartSequence();
            Integer endSequence = trainDetailVO.getEndSequence();
            List<SeatClassVO> seatClassList = querySeatClassData(trainId, startSequence, endSequence);
            ticketQueryVO.setSeatClassList(seatClassList);


            /**    拷贝列车其它属性   **/
            boolean departureFlag = checkDepartureStation(trainId, trainDetailVO.getDepartureStationId());
            ticketQueryVO.setDepartureFlag(departureFlag);
            boolean arrivalFlag = checkTerminalStation(trainId, trainDetailVO.getArrivalStationId());
            ticketQueryVO.setArrivalFlag(arrivalFlag);
            // 设置历时
            ticketQueryVO.setDuration(
                    calculateDurationInMinutes(
                            trainDetailVO.getArrivalTime(),
                            trainDetailVO.getDepartureTime()
                    )
            );
            // 设置出发站点，到达站点
            ticketQueryVO.setDeparture(ticketQueryDTO.getDeparture());
            ticketQueryVO.setArrival(ticketQueryDTO.getArrival());

            resultList.add(ticketQueryVO);
        }

        // 封装返回
        return resultList;
    }


    /**
     * 查询席别信息
     * @param trainId
     * @return
     */
    private List<SeatClassVO> querySeatClassData(Long trainId, Integer startSequence, Integer endSequence) {
        // 根据列车id，获取所有关联id，席别id和总座位数
        List<SeatClassTotalDTO> seatClassTotalDTOList = trainSeatClassMapper.getByTrainId(trainId);

        List<SeatClassVO> seatClassVOList = new ArrayList<>();

        for (SeatClassTotalDTO seatClassTotalDTO : seatClassTotalDTOList) {
            // 根据席别id，查询席别类型，名称以及价格
            Long seatClassId = seatClassTotalDTO.getSeatClassId();
            SeatClassDTO seatClassDTO = seatClassMapper.getBySeatClassId(seatClassId);

            // 根据trainSeatClassId，统计可用座位数
            Long trainSeatClassId = seatClassTotalDTO.getId();
            Integer availableSeatNum = trainSeatMapper.countAvailSeatsByClassId(trainSeatClassId, startSequence, endSequence);

            // 拷贝属性
            SeatClassVO seatClassVO = new SeatClassVO();
            seatClassVO.setTotalSeatNum(seatClassTotalDTO.getTotalSeats()); // 总座位数
            BeanUtils.copyProperties(seatClassDTO, seatClassVO);
            seatClassVO.setAvailableSeatNum(availableSeatNum); // 可用座位
            seatClassVO.setCandidate(availableSeatNum == 0);  // 席别候补标识

            seatClassVOList.add(seatClassVO);
        }

        return seatClassVOList;
    }


    /**
     * 查询 trainId, departureTime, arrivalTime, departureStationId, arrivalStationId 5个属性
     * @param ticketQueryDTO
     * @return
     */
    private List<TrainDetailVO> queryTrains(TicketQueryDTO ticketQueryDTO) {
        /**
         * 根据出发日和【（出发地和到达地）或（出发车站和到达车站）】，查询列车ids,列车出发和到达时间
         * 通过车站名称匹配车站 IDS
         * 再通过列车经停顺序筛选有效列车，并查询出发时间和到达时间
         */

        // trainId, departureTime, arrivalTime, departureStationId, arrivalStationId

        // 1. 查询出发站和到达站的IDS
        /**
         *
         *
         *
         *
         *
         List<Integer> startStationIds = stationMapper.getStartStationIdByDTO(ticketQueryDTO);
        List<Integer> endStationIds = stationMapper.getEndStationIdByDTO(ticketQueryDTO);

        List<TrainDetailVO> resultList = new ArrayList<>();

        for (Integer startStationId : startStationIds) {
            for (Integer endStationId : endStationIds) {
                // 2. 查询符合条件的列车
                List<TrainTimeDTO> trainTimeDTOList = trainStopStationMapper.getTrainsByStations(startStationId, endStationId);

                // 3. 封装列车详情数据,并返回
                TrainDetailVO trainDetailVO = new TrainDetailVO();
                trainDetailVO.setTrainTimeDTOList(trainTimeDTOList);
                trainDetailVO.setDepartureStationId(startStationId);
                trainDetailVO.setArrivalStationId(endStationId);

                resultList.add(trainDetailVO);
            }
        }


        return resultList; **/


        // 逻辑下沉到 SQL，用批量查询替代循环查询，直接通过一次数据库查询获取所有结果，MyBatis自动封装为List<TrainDetailVO>
        return stationMapper.getTrainDetailsByDTO(ticketQueryDTO);

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
}
