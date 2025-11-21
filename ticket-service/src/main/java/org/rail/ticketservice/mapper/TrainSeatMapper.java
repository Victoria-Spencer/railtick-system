package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.rail.ticketservice.pojo.dto.SeatInfoQueryDTO;
import org.rail.ticketservice.pojo.dto.SeatIntervalBaseDTO;
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
     * 批量查询座位占用的基本信息
     * @param seatInfoQueryDTOList
     * @return
     */
    List<SeatIntervalBaseDTO> batchQuerySIOBaseInfo(List<SeatInfoQueryDTO> seatInfoQueryDTOList);
}
