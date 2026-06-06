package org.rail.common.web.config;

import org.rail.common.core.config.AuthProperties;
import org.rail.common.web.interceptor.CommonRequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private CommonRequestInterceptor commonRequestInterceptor;

    // ========== 拦截器配置 ==========
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(commonRequestInterceptor)
                .addPathPatterns("/**");  // 拦截所有路径，由拦截器内部的excludePaths控制免拦截
    }
}
