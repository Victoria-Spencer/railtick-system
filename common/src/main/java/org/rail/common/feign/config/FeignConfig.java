package org.rail.common.feign.config;

import feign.RequestInterceptor;
import feign.codec.Decoder;
import lombok.RequiredArgsConstructor;
import org.rail.common.core.config.SensitiveProperties;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.core.exception.OpenFeignException;
import org.rail.common.feign.constant.FeignConstant;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class FeignConfig {

    private final HttpMessageConverters messageConverters;
    private final SensitiveProperties sensitiveProperties;

    /**
     * Feign认证拦截器：添加认证信息到请求头
     */
    @Bean
    public RequestInterceptor authRequestInterceptor() {
        return requestTemplate -> {
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
        };
    }

    /**
     * Feign调用标记拦截器
     */
    @Bean
    public RequestInterceptor feignCallHeaderInterceptor() {
        return requestTemplate ->
                requestTemplate.header(FeignConstant.FEIGN_REQUEST_HEADER, "true");
    }

    /**
     * 响应解密解码器
     */
    @Bean
    public Decoder feignResponseDecryptDecoder() {
        return new FeignResponseDecryptDecoder(messageConverters, sensitiveProperties);
    }
}
