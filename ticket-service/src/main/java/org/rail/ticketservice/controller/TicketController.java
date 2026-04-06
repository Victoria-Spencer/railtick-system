package org.rail.ticketservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.jdbc.Null;
import org.rail.api.dto.AvailableSeatDTO;
import org.rail.api.dto.RandomSeatQueryDTO;
import org.rail.api.dto.operateSeatIntervalOccupy;
import org.rail.common.core.result.Result;
import org.rail.ticketservice.pojo.dto.PlannedTicketQueryDTO;
import org.rail.ticketservice.pojo.dto.TicketQueryDTO;
import org.rail.ticketservice.pojo.vo.TicketQueryVO;
import org.rail.ticketservice.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/ticket-service/ticket")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    /**
     * 查询购票列表
     * @param ticketQueryDTO 购票查询条件
     * @return 购票列表
     */
    @GetMapping("/query")
    public Result<List<TicketQueryVO>> queryTicket(@RequestBody TicketQueryDTO ticketQueryDTO) {
        List<TicketQueryVO> ticketQueryVOList = ticketService.queryTicket(ticketQueryDTO);
        return Result.success(ticketQueryVOList);
    }

    /**
     * 查询拟购票信息
     * @param plannedTicketQueryDTO 拟购票查询条件
     * @return 拟购票信息
     */
    @GetMapping("/planned-tickets/query")
    public Result<TicketQueryVO>  queryPlannedTicket(@RequestBody PlannedTicketQueryDTO plannedTicketQueryDTO) {
        TicketQueryVO ticketQueryVO = ticketService.queryPlannedTicket(plannedTicketQueryDTO);
        return Result.success(ticketQueryVO);
    }

    @PostMapping("/seats/available")
    public Result<List<AvailableSeatDTO>> getAvailableSeats(@RequestBody RandomSeatQueryDTO randomSeatQueryDTO) {
        List<AvailableSeatDTO> availableSeatDTOList = ticketService.getAvailableSeats(randomSeatQueryDTO);
        return Result.success(availableSeatDTOList);
    }

    @PutMapping("/seat-status/update")
    public Result<Null> updateSeatStatus(@RequestBody operateSeatIntervalOccupy sioDTO) {
        ticketService.updateSeatStatus(sioDTO);
        return Result.success();
    }
}
