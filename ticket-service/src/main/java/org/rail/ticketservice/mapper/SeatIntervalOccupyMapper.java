package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.rail.commonapi.dto.UpdateSeatStatusDTO;
import org.rail.ticketservice.pojo.dto.IntervalOccupyDTO;

import java.util.List;

@Mapper
public interface SeatIntervalOccupyMapper {

    /**
     * 查询列车下指定的席别类型的指定座位的占用区间
     * @param updateSeatStatusDTOList
     * @return
     */
    List<IntervalOccupyDTO> getIntervalOccupy(List<UpdateSeatStatusDTO> updateSeatStatusDTOList);
}
