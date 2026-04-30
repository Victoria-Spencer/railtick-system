package org.rail.api.client;

import org.rail.api.dto.AvailableSeatDTO;
import org.rail.api.dto.BatchSeatIntervalInsertDTO;
import org.rail.api.dto.RandomSeatQueryDTO;
import org.rail.api.fallback.TicketFeignFallback;
import org.rail.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "ticket-service", fallback = TicketFeignFallback.class)
public interface TicketFeignClient {

    @PostMapping("/api/ticket-service/ticket/seats/available")
    Result<List<AvailableSeatDTO>> getAvailableSeats(@RequestBody RandomSeatQueryDTO randomSeatQueryDTO);

    @PutMapping("/api/ticket-service/ticket/seat-status/update")
    Result<Void> updateSeatStatus(@RequestBody BatchSeatIntervalInsertDTO batchDTO);
}
