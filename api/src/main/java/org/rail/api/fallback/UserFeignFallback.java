package org.rail.api.fallback;

import lombok.extern.slf4j.Slf4j;
import org.rail.api.client.UserFeignClient;
import org.rail.api.dto.UserIdCardDTO;
import org.rail.common.core.result.Result;
import org.springframework.stereotype.Component;

/**
 * // 降级类，实现 Feign 接口
 */
@Component
@Slf4j
public class UserFeignFallback implements UserFeignClient {

    /**
     * 查询用户的证件类型和证件号码，请求失败处理
     * @param id
     * @return
     */
    public Result<UserIdCardDTO> getIdCardInfo(Long id) {
        log.error("服务临时不可用，请稍后重试");
        return Result.error("服务临时不可用，请稍后重试");
    }
}
