package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.rail.api.dto.AvailableSeatDTO;
import org.rail.api.dto.SeatTypeQueryDTO;
import org.rail.ticketservice.pojo.dto.SeatQueryDTO;
import org.rail.ticketservice.pojo.vo.SeatClassVO;

import java.util.List;

@Mapper
public interface SeatClassMapper {

    /**
     * 批量查询席别信息
     * @param seatQueryDTOList 席别查询条件列表
     * @return 席别信息列表
     */
    List<SeatClassVO> batchQuerySeatInfoByDTOList(List<SeatQueryDTO> seatQueryDTOList);

    /**
     * 查询可用座位
     * @param seatTypeQueryDTO 可用座位查询条件
     * @return 可用座位列表
     */
    List<AvailableSeatDTO> getAvailableSeatsBySeatTypeQueryDTO(SeatTypeQueryDTO seatTypeQueryDTO);
}
