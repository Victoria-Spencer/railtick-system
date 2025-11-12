package org.rail.ticketservice.service;

import org.rail.ticketservice.pojo.dto.PlannedTicketQueryDTO;
import org.rail.ticketservice.pojo.dto.TicketQueryDTO;
import org.rail.ticketservice.pojo.vo.TicketQueryVO;

import java.util.List;

public interface TicketService {

    /**
     * 查询购票列表
     * @param ticketQueryDTO
     * @return
     */
    List<TicketQueryVO> queryTicket(TicketQueryDTO ticketQueryDTO);

    /**
     * 查询拟购票信息
     * @param plannedTicketQueryDTO
     * @return
     */
    TicketQueryVO queryPlannedTicket(PlannedTicketQueryDTO plannedTicketQueryDTO);
}
