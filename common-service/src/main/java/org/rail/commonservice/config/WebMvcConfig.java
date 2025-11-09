package org.rail.commonservice.config;

import org.rail.commonservice.interceptor.CommonRequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 标记为 Spring MVC 配置类
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private CommonRequestInterceptor commonRequestInterceptor;

    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(commonRequestInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/user-service/v1/login",
                        "/api/user-service/register",
                        "/error" // 排除错误页面请求
                );
    }
}
