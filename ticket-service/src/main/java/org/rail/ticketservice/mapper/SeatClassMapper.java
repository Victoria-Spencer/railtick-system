package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.rail.ticketservice.pojo.dto.SeatQueryDTO;
import org.rail.ticketservice.pojo.vo.SeatClassVO;

import java.util.List;

@Mapper
public interface SeatClassMapper {

    /**
     * 批量查询席别信息
     * @param seatQueryDTOList
     * @return
     */
    List<SeatClassVO> batchQuerySeatInfoByDTOList(List<SeatQueryDTO> seatQueryDTOList);
}
