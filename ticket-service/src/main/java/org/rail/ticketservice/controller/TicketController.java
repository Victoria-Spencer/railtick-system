package org.rail.ticketservice.controller;

import org.rail.api.dto.AvailableSeatRemoteDTO;
import org.rail.api.dto.BatchSeatIntervalInsertDTO;
import org.rail.api.dto.RandomSeatQueryDTO;
import org.rail.common.core.annotation.OperationLog;
import org.rail.ticketservice.model.dto.PlannedTicketQueryDTO;
import org.rail.ticketservice.model.dto.TicketQueryDTO;
import org.rail.ticketservice.model.vo.TicketQueryVO;
import org.rail.ticketservice.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/ticket-service/ticket")
@Validated
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @PostMapping("/query")
    public List<TicketQueryVO> queryTicket(@RequestBody @Validated TicketQueryDTO ticketQueryDTO) {
        return ticketService.queryTicket(ticketQueryDTO);
    }

    @PostMapping("/planned-tickets/query")
    public TicketQueryVO queryPlannedTicket(@RequestBody @Validated PlannedTicketQueryDTO plannedTicketQueryDTO) {
        return ticketService.queryPlannedTicket(plannedTicketQueryDTO);
    }

    @PostMapping("/seats/available")
    public List<AvailableSeatRemoteDTO> getAvailableSeats(@RequestBody @Validated RandomSeatQueryDTO randomSeatQueryDTO) {
        return ticketService.getAvailableSeats(randomSeatQueryDTO);
    }

    @OperationLog(value = "更新座位状态", saveParam = true)
    @PutMapping("/seat-status/update")
    public void updateSeatStatus(@RequestBody @Validated BatchSeatIntervalInsertDTO batchDTO) {
        ticketService.updateSeatStatus(batchDTO);
    }
}
