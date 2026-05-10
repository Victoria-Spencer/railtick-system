package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.rail.ticketservice.model.entity.Train;

@Mapper
public interface TrainMapper {

    @Select("select id, train_number, days_arrived, sale_time, sale_status " +
            "from train " +
            "where id = #{trainId}")
    Train getById(Long trainId);
}
