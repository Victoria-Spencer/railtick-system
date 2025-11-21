package org.rail.commonapi.client;

import org.rail.commonapi.dto.AvailableSeatDTO;
import org.rail.commonapi.dto.RandomSeatQueryDTO;
import org.rail.commonapi.dto.SeatIntervalOccupyDTO;
import org.rail.commonapi.dto.UpdateSeatStatusDTO;
import org.rail.commonapi.fallback.TicketFeignFallback;
import org.rail.commonservice.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "ticket-service", fallback = TicketFeignFallback.class)
public interface TicketFeignClient {

    /**
     * 查询可用座位
     * @param randomSeatQueryDTO
     * @return
     */
    @PostMapping("/api/ticket-service/ticket/seats/available")
    Result<List<AvailableSeatDTO>> getAvailableSeats(@RequestBody RandomSeatQueryDTO randomSeatQueryDTO);

    /**
     * 修改占用区间，并同步新的座位状态
     * @param sioDTO
     */
    @PutMapping("/api/ticket-service/ticket/seat-status/update")
    public Result updateSeatStatus(@RequestBody SeatIntervalOccupyDTO sioDTO);
}
