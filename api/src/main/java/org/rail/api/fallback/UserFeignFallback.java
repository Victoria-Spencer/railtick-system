package org.rail.api.fallback;

import org.rail.api.client.UserFeignClient;
import org.rail.api.dto.PassengerRemoteDTO;
import org.rail.api.dto.UserIdCardDTO;
import org.rail.common.core.model.result.Result;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * // 降级类，实现 Feign 接口
 */
@Component
public class UserFeignFallback implements UserFeignClient {

    /**
     * 查询用户的证件类型和证件号码，请求失败处理
     * @return 错误提示信息
     */
    @Override
    public Result<UserIdCardDTO> getIdCardInfo() {
        return Result.error("远程调用失败/触发降级");
    }

    /**
     * 批量查询乘客信息，请求失败处理
     * @param passengerIds 乘客ID列表
     * @return 错误提示信息
     */
    @Override
    public Result<List<PassengerRemoteDTO>> batchListPassenger(List<Long> passengerIds) {
        return Result.error("远程调用失败/触发降级");
    }
}
