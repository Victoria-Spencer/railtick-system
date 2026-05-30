package org.rail.api.client;

import org.rail.api.dto.PassengerRemoteDTO;
import org.rail.api.dto.UserIdCardDTO;
import org.rail.api.fallback.UserFeignFallback;
import org.rail.common.core.model.result.Result;
import org.rail.common.feign.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "user-service",
        fallback = UserFeignFallback.class,
        configuration = FeignConfig.class
)
public interface UserFeignClient {

    /**
     * 查询用户的证件类型和证件号码
     * @return 用户的证件类型和证件号码
     */
    @GetMapping("/api/user-service/user/id-card-info")
    Result<UserIdCardDTO> getIdCardInfo();

    /**
     * 批量根据乘客ID查询真实信息
     */
    @PostMapping("/api/user-service/passenger/batch")
    Result<List<PassengerRemoteDTO>> batchListPassenger(@RequestBody List<Long> passengerIds);
}


