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

    /**
     * 查询列车详情
     * @param ticketQueryDTO
     * @return
     */
    List<TrainDetailVO> getTrainDetailsByDTO(TicketQueryDTO ticketQueryDTO);
}
