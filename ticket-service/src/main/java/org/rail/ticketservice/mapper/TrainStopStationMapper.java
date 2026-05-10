package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.rail.ticketservice.model.dto.PlannedTicketQueryDTO;
import org.rail.ticketservice.model.dto.SequenceDTO;
import org.rail.ticketservice.model.dto.SequenceQueryDTO;
import org.rail.ticketservice.model.dto.StopInfoDTO;
import org.rail.ticketservice.model.entity.TrainStopStation;

import java.util.List;

@Mapper
public interface TrainStopStationMapper {

    /**
     * 判断是否为始发站
     * @param trainId 列车ID
     * @param stationId 站点ID
     * @return 是否为始发站
     */
    @Select("SELECT COUNT(1) > 0 " +
            "FROM train_stop_station " +
            "WHERE train_id = #{trainId} " +
            "  AND station_id = #{stationId} " +
            "  AND sequence = 1 " +
            "  AND arrival_time IS NULL")
    boolean isDepartureStation(Long trainId, Integer stationId);

    /**
     * 判断是否为终点站
     * @param trainId 列车ID
     * @param stationId 站点ID
     * @return 是否为终点站
     */
    @Select("SELECT COUNT(1) > 0 " +
            "FROM train_stop_station a " +
            "WHERE a.train_id = #{trainId} " +
            "  AND a.station_id = #{stationId} " +
            "  AND a.sequence = (SELECT MAX(sequence) FROM train_stop_station WHERE train_id = #{trainId}) " +
            "  AND a.departure_time IS NULL")
    boolean isTerminalStation(Long trainId, Integer stationId);

    /**
     * 查询站点信息
     * @param plannedTicketQueryDTO 查询条件
     * @return 站点信息
     */
    StopInfoDTO getStopInfoByQueryDTO(PlannedTicketQueryDTO plannedTicketQueryDTO);

    /**
     * 查询终点站站序
     * @param trainId 列车ID
     * @return 终点站站序
     */
    @Select("select max(sequence) from train_stop_station where train_id = #{trainId}")
    Integer getTerminalSequence(Long trainId);

    /**
     * 查询出发站站序和到达站站序
     * @param sequenceQueryDTO 查询条件
     * @return 出发站站序和到达站站序
     */
    SequenceDTO getSequenceInfo(SequenceQueryDTO sequenceQueryDTO);

    /**
     * 查询所有的站点信息
     * @return 所有的站点信息
     */
    List<TrainStopStation> selectAll();

    /**
     * 根据列车ID查询站点信息
     * @param trainId 列车ID
     * @return 站点信息列表
     */
    List<TrainStopStation> selectByTrainId(Long trainId);
}
