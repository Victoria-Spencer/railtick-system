package org.rail.commonapi.client;

import org.rail.commonapi.dto.AvailableSeatDTO;
import org.rail.commonapi.dto.RandomSeatQueryDTO;
import org.rail.commonapi.fallback.TicketFeignFallback;
import org.rail.commonservice.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
}
