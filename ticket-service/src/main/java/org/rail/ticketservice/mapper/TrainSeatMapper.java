package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.rail.ticketservice.pojo.dto.SeatStatusUpdateConditionDTO;

import java.util.List;

@Mapper
public interface TrainSeatMapper {

    /**
     * 批量更新座位状态
     * @param conditionDTOList
     */
    void batchUpdateSeatStatus(List<SeatStatusUpdateConditionDTO> conditionDTOList);
}
