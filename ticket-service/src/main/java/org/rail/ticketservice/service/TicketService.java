package org.rail.ticketservice.service;

import org.rail.commonapi.dto.AvailableSeatDTO;
import org.rail.commonapi.dto.RandomSeatQueryDTO;
import org.rail.commonapi.dto.UpdateSeatStatusDTO;
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

    /**
     * 查询可用座位
     * @param randomSeatQueryDTO
     * @return
     */
    List<AvailableSeatDTO> getAvailableSeats(RandomSeatQueryDTO randomSeatQueryDTO);

    /**
     * 更新座位状态
     * @param updateSeatStatusDTOList
     */
    void updateSeatStatus(List<UpdateSeatStatusDTO> updateSeatStatusDTOList);
}
