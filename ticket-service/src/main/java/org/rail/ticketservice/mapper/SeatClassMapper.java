package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.rail.ticketservice.pojo.dto.SeatClassDTO;

@Mapper
public interface SeatClassMapper {
    /**
     * 根据席别id，查询席别类型，名称以及价格
     * @param seatClassId
     * @return
     */
    @Select("select type, name, price from seat_class where id = #{seatClassId}")
    SeatClassDTO getBySeatClassId(Long seatClassId);
}
