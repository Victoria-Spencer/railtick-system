package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.rail.ticketservice.pojo.dto.SeatClassTotalDTO;

import java.util.List;

@Mapper
public interface TrainSeatClassMapper {

    /**
     * 根据列车id，获取所有关联id，席别id和总座位数
     * @param trainId
     * @return
     */

    @Select("select id, seat_class_id, total_seats from train_seat_class where train_id = #{trainId}")
    List<SeatClassTotalDTO> getByTrainId(Long trainId);
}
