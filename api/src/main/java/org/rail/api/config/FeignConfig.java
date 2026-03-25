package org.rail.api.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.rail.common.core.exception.OpenFeignException;
import org.rail.common.core.util.ThreadLocalUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    /**
     * Feign请求拦截器：添加认证信息到请求头
     */
    @Bean
    public RequestInterceptor authRequestInterceptor() {
        return new RequestInterceptor() {

            public void apply(RequestTemplate requestTemplate) {
                // 从当前上下文（如ThreadLocal）中获取用户ID
                // 用户登录后，user-id存储在ThreadLocal中
                String userId = ThreadLocalUtils.get();
                if (userId != null) {
                    // 向请求头添加user-id
                    requestTemplate.header("user-id", userId);
                } else {
                    // 若未获取到用户ID，可抛出异常或处理未登录场景
                    throw new OpenFeignException("当前用户未登录，无法发起远程调用");
                }
            }
        };
    }
}
