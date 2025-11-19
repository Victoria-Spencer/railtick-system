package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.rail.ticketservice.pojo.dto.PlannedTicketQueryDTO;
import org.rail.ticketservice.pojo.dto.StopInfoDTO;

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
     * @param plannedTicketQueryDTO
     * @return
     */
    StopInfoDTO getStopInfoByQueryDTO(PlannedTicketQueryDTO plannedTicketQueryDTO);

    /**
     * 查询终点站站序
     * @param trainId
     * @return
     */
    @Select("select max(sequence) from train_stop_station where train_id = #{trainId}")
    Integer getTerminalSequence(Long trainId);
}
