package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.rail.ticketservice.pojo.dto.StationPageQueryDTO;
import org.rail.ticketservice.pojo.vo.StationPageQueryVO;

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
    @Select("select train_stop_station_info from train where id = #{trainId}")
    String getStopsByTrainId(Integer trainId);
}
