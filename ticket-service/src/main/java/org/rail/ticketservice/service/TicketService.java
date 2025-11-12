package org.rail.ticketservice.service;

import org.rail.ticketservice.pojo.dto.TicketQueryDTO;
import org.rail.ticketservice.pojo.vo.TicketQueryVO;

import java.util.List;

public interface TicketService {

    /**
     * 检索满足筛选条件的车票信息
     * @param ticketQueryDTO
     * @return
     */
    List<TicketQueryVO> queryTicket(TicketQueryDTO ticketQueryDTO);
}
