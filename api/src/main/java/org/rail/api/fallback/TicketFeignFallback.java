package org.rail.api.fallback;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.jdbc.Null;
import org.rail.api.client.TicketFeignClient;
import org.rail.api.dto.AvailableSeatDTO;
import org.rail.api.dto.BatchSeatIntervalInsertDTO;
import org.rail.api.dto.RandomSeatQueryDTO;
import org.rail.common.core.result.Result;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * // 降级类，实现 Feign 接口
 */
@Component
@Slf4j
public class TicketFeignFallback implements TicketFeignClient {

    /**
     * 查询可用座位，请求失败处理
     * @param randomSeatQueryDTO 请求参数
     * @return 错误提示
     */
    public Result<List<AvailableSeatDTO>> getAvailableSeats(@RequestBody RandomSeatQueryDTO randomSeatQueryDTO) {
        log.error("服务临时不可用，请稍后重试");
        return Result.error("服务临时不可用，请稍后重试");
    }

    /**
     * 修改占用区间，并同步新的座位状态，请求失败处理
     * @param batchDTO 批量座位区间插入DTO，包含订单ID、订单类型、列车ID、出发站编码、到达站编码、席别类型列表、占用区间列表
     * @return
     */
    @Override
    public Result<Null> updateSeatStatus(@RequestBody BatchSeatIntervalInsertDTO batchDTO) {
        log.error("服务临时不可用，请稍后重试");
        return Result.error("服务临时不可用，请稍后重试");
    }
}
