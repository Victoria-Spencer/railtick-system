package org.rail.ticketservice.service;

import org.rail.api.dto.AvailableSeatDTO;
import org.rail.api.dto.RandomSeatQueryDTO;
import org.rail.api.dto.SeatIntervalOccupyDTO;
import org.rail.ticketservice.pojo.dto.PlannedTicketQueryDTO;
import org.rail.ticketservice.pojo.dto.TicketQueryDTO;
import org.rail.ticketservice.pojo.vo.TicketQueryVO;

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
     * @param sioDTO 座位占用区间信息
     */
    void updateSeatStatus(SeatIntervalOccupyDTO sioDTO);
}
