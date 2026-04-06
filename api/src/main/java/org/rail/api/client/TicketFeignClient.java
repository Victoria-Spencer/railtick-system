package org.rail.api.client;

import org.apache.ibatis.jdbc.Null;
import org.rail.api.dto.AvailableSeatDTO;
import org.rail.api.dto.RandomSeatQueryDTO;
import org.rail.api.dto.operateSeatIntervalOccupy;
import org.rail.api.fallback.TicketFeignFallback;
import org.rail.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
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
    Result<Null> updateSeatStatus(@RequestBody operateSeatIntervalOccupy sioDTO);
}
