package org.rail.api.fallback;

import org.rail.api.client.UserFeignClient;
import org.rail.api.dto.UserIdCardDTO;
import org.rail.common.core.result.Result;
import org.rail.common.core.util.LogUtils;
import org.springframework.stereotype.Component;

/**
 * // 降级类，实现 Feign 接口
 */
@Component
public class UserFeignFallback implements UserFeignClient {

    /**
     * 查询用户的证件类型和证件号码，请求失败处理
     * @param id 用户 ID
     * @return 错误提示信息
     */
    public Result<UserIdCardDTO> getIdCardInfo(Long id) {
        return Result.error("远程调用失败/触发降级");
    }
}
