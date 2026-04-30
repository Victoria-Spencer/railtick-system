package org.rail.api.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.core.exception.OpenFeignException;
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
                //  统一从上下文获取 RequestContext
                RequestContext context = RequestContextHolder.getRequestContext();
                if (context == null) {
                    throw new OpenFeignException("当前请求无上下文，无法发起远程调用");
                }

                String userId = context.getAccountId();
                if (userId == null || userId.isBlank()) {
                    throw new OpenFeignException("当前用户未登录，无法发起远程调用");
                }

                requestTemplate.header("user-id", userId);
                requestTemplate.header("request-id", context.getRequestId());
            }
        };
    }
}
