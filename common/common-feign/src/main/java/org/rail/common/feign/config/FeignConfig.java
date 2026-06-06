package org.rail.common.feign.config;

import feign.RequestInterceptor;
import feign.codec.Decoder;
import lombok.RequiredArgsConstructor;
import org.rail.common.core.config.SensitiveProperties;
import org.rail.common.core.constant.RequestHeaderConstants;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.core.exception.OpenFeignException;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

            String userId = context.getUserId();
            if (userId == null || userId.isBlank()) {
                throw new OpenFeignException("当前用户未登录，无法发起远程调用");
            }

            String username = context.getUsername();
            if (username == null || username.isBlank()) {
                throw new OpenFeignException("当前用户未登录，无法发起远程调用");
            }

            String usernameEncoded = Base64.getEncoder()
                    .encodeToString(username.getBytes(StandardCharsets.UTF_8));

            requestTemplate.header(RequestHeaderConstants.USER_ID_HEADER, userId);
            requestTemplate.header(RequestHeaderConstants.USER_NAME_HEADER, usernameEncoded);
            requestTemplate.header(RequestHeaderConstants.REQUEST_ID_HEADER, context.getRequestId());
            requestTemplate.header(RequestHeaderConstants.START_TIME_HEADER, String.valueOf(context.getStartTime()));
            requestTemplate.header(RequestHeaderConstants.X_REAL_IP_HEADER, context.getCallerIp());
            requestTemplate.header(RequestHeaderConstants.SOURCE_HEADER, context.getSource());
        };
    }

    /**
     * Feign调用标记拦截器
     */
    @Bean
    public RequestInterceptor feignCallHeaderInterceptor() {
        return requestTemplate ->
                requestTemplate.header(RequestHeaderConstants.FEIGN_REQUEST_HEADER, "true");
    }

    /**
     * 响应解密解码器
     */
    @Bean
    public Decoder feignResponseDecryptDecoder() {
        return new FeignResponseDecryptDecoder(messageConverters, sensitiveProperties);
    }
}
