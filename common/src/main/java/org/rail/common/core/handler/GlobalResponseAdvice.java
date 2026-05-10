package org.rail.common.core.handler;

import org.rail.common.core.model.result.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局统一响应结果包装器
 * 自动将 Controller 返回值包装为 Result.success()
 */
@RestControllerAdvice(basePackages = "org.rail")
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 开启支持：如果返回值已经是 Result 类型，不重复包装
     */
    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return !(returnType.getParameterType().equals(Result.class));
    }

    /**
     * 统一包装返回结果
     */
    @Override
    public Object beforeBodyWrite(@Nullable Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {

        if (body == null) {
            return Result.success();
        }

        return Result.success(body);
    }
}