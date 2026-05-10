package org.rail.ticketservice.service;

import org.rail.api.dto.AvailableSeatDTO;
import org.rail.api.dto.BatchSeatIntervalInsertDTO;
import org.rail.api.dto.RandomSeatQueryDTO;
import org.rail.ticketservice.model.dto.PlannedTicketQueryDTO;
import org.rail.ticketservice.model.dto.TicketQueryDTO;
import org.rail.ticketservice.model.vo.TicketQueryVO;

import java.util.List;

public interface TicketService {

    /**
     * 查询购票列表
     * @param ticketQueryDTO 购票查询条件
     * @return 购票信息列表
     */
    List<TicketQueryVO> queryTicket(TicketQueryDTO ticketQueryDTO);

    /**
     * 查询拟购票信息
     * @param plannedTicketQueryDTO 拟购票查询条件
     * @return 拟购票信息
     */
    TicketQueryVO queryPlannedTicket(PlannedTicketQueryDTO plannedTicketQueryDTO);

    /**
     * 查询可用座位
     * @param randomSeatQueryDTO 查询条件
     * @return 可用座位列表
     */
    List<AvailableSeatDTO> getAvailableSeats(RandomSeatQueryDTO randomSeatQueryDTO);

    /**
     * 更新座位占用区间，并同步新的座位状态
     * @param batchDTO 批量座位区间插入DTO，包含订单ID、订单类型、列车ID、出发站编码、到达站编码、席别类型列表、占用区间列表
     */
    void updateSeatStatus(BatchSeatIntervalInsertDTO batchDTO);
}
