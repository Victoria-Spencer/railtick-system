package org.rail.api.client;

import org.rail.api.dto.AvailableSeatRemoteDTO;
import org.rail.api.dto.BatchSeatIntervalInsertDTO;
import org.rail.api.dto.RandomSeatQueryDTO;
import org.rail.api.fallback.TicketFeignFallback;
import org.rail.common.core.model.result.Result;
import org.rail.common.feign.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "ticket-service",
        fallback = TicketFeignFallback.class,
        configuration = FeignConfig.class
)
public interface TicketFeignClient {

    @PostMapping("/api/ticket-service/ticket/seats/available")
    Result<List<AvailableSeatRemoteDTO>> getAvailableSeats(@RequestBody RandomSeatQueryDTO randomSeatQueryDTO);

    @PutMapping("/api/ticket-service/ticket/seat-status/update")
    Result<Void> updateSeatStatus(@RequestBody BatchSeatIntervalInsertDTO batchDTO);
}
