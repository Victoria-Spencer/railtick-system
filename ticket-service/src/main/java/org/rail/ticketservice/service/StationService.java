package org.rail.ticketservice.service;


import org.rail.common.core.model.result.PageResult;
import org.rail.ticketservice.model.dto.StationPageQueryDTO;
import org.rail.ticketservice.model.vo.StationPageQueryVO;
import org.rail.ticketservice.model.vo.TrainStopStationVO;

import java.util.List;

public interface StationService {

    /**
     * 根据查询类型或名称分页查询站点信息
     * @param stationPageQueryDTO 查询条件
     * @return 分页查询结果
     */
    PageResult<StationPageQueryVO> pageQueryStations(StationPageQueryDTO stationPageQueryDTO);

    /**
     * 根据列车id查询列车经停站信息
     * @return 列车经停站信息列表
     */
    List<TrainStopStationVO> getStopsByTrainId(Long trainId);
}
