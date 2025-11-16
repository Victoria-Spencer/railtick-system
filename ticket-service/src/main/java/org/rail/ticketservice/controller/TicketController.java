package org.rail.ticketservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.rail.commonapi.dto.AvailableSeatDTO;
import org.rail.commonapi.dto.SeatQueryDTO;
import org.rail.commonservice.result.Result;
import org.rail.ticketservice.pojo.dto.PlannedTicketQueryDTO;
import org.rail.ticketservice.pojo.dto.TicketQueryDTO;
import org.rail.ticketservice.pojo.vo.TicketQueryVO;
import org.rail.ticketservice.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/ticket-service/ticket")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    /**
     * 查询购票列表
     * @param ticketQueryDTO
     * @return
     */
    @GetMapping("/query")
    public Result<List<TicketQueryVO>> queryTicket(@RequestBody TicketQueryDTO ticketQueryDTO) {
        List<TicketQueryVO> ticketQueryVOList = ticketService.queryTicket(ticketQueryDTO);
        return Result.success(ticketQueryVOList);
    }

    @GetMapping("/planned-tickets/query")
    public Result<TicketQueryVO>  queryPlannedTicket(@RequestBody PlannedTicketQueryDTO plannedTicketQueryDTO) {
        TicketQueryVO ticketQueryVO = ticketService.queryPlannedTicket(plannedTicketQueryDTO);
        return Result.success(ticketQueryVO);
    }

    @GetMapping("/seats/available")
    public Result<List<AvailableSeatDTO>> getAvailableSeats(@RequestBody SeatQueryDTO seatQueryDTO) {
        List<AvailableSeatDTO> availableSeatDTOList = ticketService.getAvailableSeats(seatQueryDTO);
        return Result.success(availableSeatDTOList);
    }
}
