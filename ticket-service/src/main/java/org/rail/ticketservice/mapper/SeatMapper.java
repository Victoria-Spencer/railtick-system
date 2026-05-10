package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.rail.ticketservice.model.vo.SeatDetailVO;
import org.rail.ticketservice.model.dto.SeatInfoQueryDTO;
import org.rail.ticketservice.model.dto.SeatStatusUpdateConditionDTO;

import java.util.List;

@Mapper
public interface SeatMapper {

    /**
     * 批量更新座位状态
     * @param conditionDTOList 座位状态更新条件列表
     */
    void batchUpdateSeatStatus(List<SeatStatusUpdateConditionDTO> conditionDTOList);

    /**
     * 查询座位ID
     * @param seatInfoQueryDTOList 座位信息查询条件列表
     * @return 座位ID列表
     */
    List<Long> getSeatIdByQueryDTO(List<SeatInfoQueryDTO> seatInfoQueryDTOList);

    /**
     * 全量查询所有座位详情列表
     * @return 座位详情列表
     */
    List<SeatDetailVO> selectAllSeatDetails();
}
