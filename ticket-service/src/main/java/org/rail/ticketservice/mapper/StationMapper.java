package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.rail.ticketservice.pojo.dto.StationPageQueryDTO;
import org.rail.ticketservice.pojo.dto.TicketQueryDTO;
import org.rail.ticketservice.pojo.vo.StationPageQueryVO;
import org.rail.ticketservice.pojo.vo.TrainDetailVO;
import org.rail.ticketservice.pojo.vo.TrainStopStationVO;

import java.util.List;

@Mapper
public interface StationMapper {

    /**
     * 根据查询类型或名称分页查询站点列表
     * @param stationPageQueryDTO
     * @return
     */
    List<StationPageQueryVO> pageQuery(StationPageQueryDTO stationPageQueryDTO);

    /**
     * 根据列车id查询列车经停站信息
     * @param trainId
     * @return
     */
    List<TrainStopStationVO> batchQueryByTrainId(Long trainId);

//    /**
//     * 根据出发日和（出发地或出发车站）查询车站ID
//     * @param  ticketQueryDTO 出发日和名称（地点或车站名称，如“北京、北京南”）
//     * @return 车站IDs（List<station_id>）
//     */
//    List<Integer> getStartStationIdByDTO(TicketQueryDTO ticketQueryDTO);
//
//    /**
//     * 根据（目的地或到达站）查询车站ID
//     * @param  ticketQueryDTO 出发日和名称（地点或车站名称，如“北京、北京南”）
//     * @return 车站IDs（List<station_id>）
//     */
//    List<Integer> getEndStationIdByDTO(TicketQueryDTO ticketQueryDTO);

    /**
     * 查询 trainId, departureTime, arrivalTime, departureStationId, arrivalStationId ,
     * startSequence, endSequence 7个属性
     * @param ticketQueryDTO
     * @return
     */
    List<TrainDetailVO> getTrainDetailsByDTO(TicketQueryDTO ticketQueryDTO);
}
