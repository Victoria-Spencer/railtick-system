package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.rail.ticketservice.model.dto.StationPageQueryDTO;
import org.rail.ticketservice.model.entity.Station;
import org.rail.ticketservice.model.vo.StationPageQueryVO;
import org.rail.ticketservice.model.vo.TrainDetailVO;
import org.rail.ticketservice.model.vo.TrainStopStationVO;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StationMapper {

    /**
     * 根据查询类型或名称分页查询站点列表
     * @param stationPageQueryDTO 站点分页查询参数（包含queryType、keyword、pageNumber、pageSize）
     * @return 分页结果（包含总记录数和当前页数据列表）
     */
    List<StationPageQueryVO> pageQuery(StationPageQueryDTO stationPageQueryDTO);

    /**
     * 根据列车id查询列车经停站信息
     * @param trainId 列车id
     * @return 列车经停站信息列表
     */
    List<TrainStopStationVO> batchQueryByTrainId(Long trainId);

    /**
     * 查询列车详情
     * @param ticketQueryDTO
     * @return
     */
//    List<TrainDetailVO> getTrainDetailsByDTO(TicketQueryDTO ticketQueryDTO);

    /**
     * 根据起始站和日期查询列车详情
     * @param departureDate 出发日期
     * @param depCode 出发站代码
     * @param arrCode 到达站代码
     */
    List<TrainDetailVO> getTrainDetailsByRouteAndDate(
            @Param("departureDate") LocalDate departureDate,
            @Param("depCode") String depCode,
            @Param("arrCode") String arrCode
    );

    /**
     * 查询所有站点
     * @return 站点列表
     */
    @Select("select * from station")
    List<Station> selectAllStations();

}
