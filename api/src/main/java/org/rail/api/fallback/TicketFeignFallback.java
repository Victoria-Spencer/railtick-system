package org.rail.api.fallback;

import org.rail.api.client.TicketFeignClient;
import org.rail.api.dto.AvailableSeatRemoteDTO;
import org.rail.api.dto.BatchSeatIntervalInsertDTO;
import org.rail.api.dto.RandomSeatQueryDTO;
import org.rail.common.core.model.result.Result;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * // 降级类，实现 Feign 接口
 */
@Component
public class TicketFeignFallback implements TicketFeignClient {

    /**
     * 查询可用座位，请求失败处理
     * @param randomSeatQueryDTO 请求参数
     * @return 错误提示
     */
    @Override
    public Result<List<AvailableSeatRemoteDTO>> getAvailableSeats(@RequestBody RandomSeatQueryDTO randomSeatQueryDTO) {
        return Result.error("远程调用失败/触发降级");
    }

    /**
     * 修改占用区间，并同步新的座位状态，请求失败处理
     * @param batchDTO 批量座位区间插入DTO，包含订单ID、订单类型、列车ID、出发站编码、到达站编码、席别类型列表、占用区间列表
     * @return 错误提示
     */
    @Override
    public Result<Void> updateSeatStatus(@RequestBody BatchSeatIntervalInsertDTO batchDTO) {
        return Result.error("远程调用失败/触发降级");
    }
}
