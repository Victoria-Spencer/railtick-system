package org.rail.ticketservice.controller;

import org.rail.api.dto.AvailableSeatDTO;
import org.rail.api.dto.BatchSeatIntervalInsertDTO;
import org.rail.api.dto.RandomSeatQueryDTO;
import org.rail.common.core.annotation.OperationLog;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.core.result.Result;
import org.rail.common.core.util.LogUtils;
import org.rail.ticketservice.pojo.dto.PlannedTicketQueryDTO;
import org.rail.ticketservice.pojo.dto.TicketQueryDTO;
import org.rail.ticketservice.pojo.vo.TicketQueryVO;
import org.rail.ticketservice.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.rail.common.core.util.LogUtils.FAIL;
import static org.rail.common.core.util.LogUtils.SUCCESS;

@RestController
@RequestMapping("/api/ticket-service/ticket")
@Validated
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @PostMapping("/query")
    public Result<List<TicketQueryVO>> queryTicket(@RequestBody @Validated TicketQueryDTO ticketQueryDTO) {
        List<TicketQueryVO> ticketQueryVOList = ticketService.queryTicket(ticketQueryDTO);
        return Result.success(ticketQueryVOList);
    }

    @PostMapping("/planned-tickets/query")
    public Result<TicketQueryVO>  queryPlannedTicket(@RequestBody @Validated PlannedTicketQueryDTO plannedTicketQueryDTO) {
        TicketQueryVO ticketQueryVO = ticketService.queryPlannedTicket(plannedTicketQueryDTO);
        return Result.success(ticketQueryVO);
    }

    @PostMapping("/seats/available")
    public Result<List<AvailableSeatDTO>> getAvailableSeats(@RequestBody @Validated RandomSeatQueryDTO randomSeatQueryDTO) {
        RequestContext context = RequestContextHolder.getRequestContext();

        try {
            List<AvailableSeatDTO> availableSeatDTOList = ticketService.getAvailableSeats(randomSeatQueryDTO);
            Result<List<AvailableSeatDTO>> result = Result.success(availableSeatDTOList);

            LogUtils.monitor(context, "TicketController", "getAvailableSeats",context.getStartTime(),
                    SUCCESS, randomSeatQueryDTO, result);

            return result;
        } catch (Exception e) {
            LogUtils.monitor(context, "TicketController", "getAvailableSeats",context.getStartTime(),
                    FAIL, randomSeatQueryDTO, e.getMessage(), e);
            throw e;
        }
    }

    @OperationLog(value = "更新座位状态", saveParam = true)
    @PutMapping("/seat-status/update")
    public Result<Void> updateSeatStatus(@RequestBody @Validated BatchSeatIntervalInsertDTO batchDTO) {
        ticketService.updateSeatStatus(batchDTO);
        return Result.success();
    }
}
