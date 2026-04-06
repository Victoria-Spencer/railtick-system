package org.rail.api.fallback;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.jdbc.Null;
import org.rail.api.client.TicketFeignClient;
import org.rail.api.dto.AvailableSeatDTO;
import org.rail.api.dto.RandomSeatQueryDTO;
import org.rail.api.dto.operateSeatIntervalOccupy;
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
     * @param randomSeatQueryDTO
     * @return
     */
    public Result<List<AvailableSeatDTO>> getAvailableSeats(@RequestBody RandomSeatQueryDTO randomSeatQueryDTO) {
        log.error("服务临时不可用，请稍后重试");
        return Result.error("服务临时不可用，请稍后重试");
    }

    @Override
    public Result<Null> updateSeatStatus(@RequestBody operateSeatIntervalOccupy sioDTO) {
        log.error("服务临时不可用，请稍后重试");
        return Result.error("服务临时不可用，请稍后重试");
    }
}
