package org.rail.commonapi.client;


import org.rail.commonapi.dto.UserIdCardDTO;
import org.rail.commonapi.fallback.UserFeignFallback;
import org.rail.commonservice.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", fallback = UserFeignFallback.class)
public interface UserFeignClient {

    /**
     * 查询用户的证件类型和证件号码
     * @param id
     * @return
     */
    @GetMapping("/api/user-service/user/{id}")
    Result<UserIdCardDTO> getIdCardInfo(@PathVariable Long id);
}
