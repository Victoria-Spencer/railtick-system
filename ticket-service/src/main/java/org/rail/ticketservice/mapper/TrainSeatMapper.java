package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.rail.ticketservice.pojo.dto.SeatInfoQueryDTO;
import org.rail.ticketservice.pojo.dto.SeatStatusUpdateConditionDTO;

import java.util.List;

@Mapper
public interface TrainSeatMapper {

    /**
     * 批量更新座位状态
     * @param conditionDTOList
     */
    void batchUpdateSeatStatus(List<SeatStatusUpdateConditionDTO> conditionDTOList);

    /**
     * 查询座位ID
     * @param seatInfoQueryDTOList
     * @return
     */
    List<Long> getSeatIdByQueryDTO(List<SeatInfoQueryDTO> seatInfoQueryDTOList);
}
