package org.rail.api.client;

import org.apache.ibatis.jdbc.Null;
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

    /**
     * 查询可用座位
     * @param randomSeatQueryDTO 查询条件：列车ID、席别类型列表、出发站编码、到达站编码
     * @return 可用座位列表
     */
    @PostMapping("/api/ticket-service/ticket/seats/available")
    Result<List<AvailableSeatDTO>> getAvailableSeats(@RequestBody RandomSeatQueryDTO randomSeatQueryDTO);

    /**
     * 修改占用区间，并同步新的座位状态
     * @param batchDTO 批量座位区间插入DTO，包含订单ID、订单类型、列车ID、出发站编码、到达站编码、席别类型列表、占用区间列表
     */
    @PutMapping("/api/ticket-service/ticket/seat-status/update")
    Result<Null> updateSeatStatus(@RequestBody BatchSeatIntervalInsertDTO batchDTO);
}
