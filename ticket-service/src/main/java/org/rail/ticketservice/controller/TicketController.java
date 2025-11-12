package org.rail.ticketservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.rail.commonservice.result.Result;
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
     * 检索满足筛选条件的车票信息
     * @param ticketQueryDTO
     * @return
     */
    @GetMapping("/query")
    public Result<List<TicketQueryVO>> queryTicket(@RequestBody TicketQueryDTO ticketQueryDTO) {
        List<TicketQueryVO> ticketQueryVOList = ticketService.queryTicket(ticketQueryDTO);
        return Result.success(ticketQueryVOList);
    }
}
