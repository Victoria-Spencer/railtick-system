package org.rail.ticketservice.service;


import org.rail.common.core.result.PageResult;
import org.rail.ticketservice.pojo.dto.StationPageQueryDTO;
import org.rail.ticketservice.pojo.vo.StationPageQueryVO;
import org.rail.ticketservice.pojo.vo.TrainStopStationVO;

import java.util.List;

public interface StationService {

    /**
     * 根据查询类型或名称分页查询站点信息
     * @param stationPageQueryDTO
     * @return
     */
    PageResult<StationPageQueryVO> pageQueryStations(StationPageQueryDTO stationPageQueryDTO);

    /**
     * 根据列车id查询列车经停站信息
     * @return
     */
    List<TrainStopStationVO> getStopsByTrainId(Long trainId);
}
