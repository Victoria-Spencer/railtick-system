package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.rail.ticketservice.pojo.entity.Train;

@Mapper
public interface TicketMapper {
    /**
     *  根据列车id查询列车信息
     * @param id
     *//*
    @Select("select id, train_number, train_type, " +
            "  train_attributes, days_arrived," +
            "  sale_time, sale_status, train_tags " +
            "from train " +
            "where id = #{id}")
    Train getTrainById(Long id);*/
}
