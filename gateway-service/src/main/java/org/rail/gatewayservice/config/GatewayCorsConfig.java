package org.rail.gatewayservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Spring Cloud Gateway 跨域配置（响应式框架专属）
 */
@Configuration
public class GatewayCorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        // 1. 构建跨域配置规则
        CorsConfiguration corsConfig = new CorsConfiguration();

        // 允许前端的域名（精确匹配你的前端运行地址）
        corsConfig.addAllowedOriginPattern("http://localhost:5173");
        corsConfig.addAllowedOriginPattern("http://127.0.0.1:5173");

        // 允许携带 Cookie/Token（登录鉴权必须开启）
        corsConfig.setAllowCredentials(true);

        // 允许所有 HTTP 请求方法（GET/POST/PUT/DELETE/OPTIONS）
        corsConfig.addAllowedMethod("*");

        // 允许所有请求头（如 Token、Content-Type 等）
        corsConfig.addAllowedHeader("*");

        // 预检请求（OPTIONS）缓存时间，避免频繁发送预检请求
        corsConfig.setMaxAge(3600L);

        // 2. 配置跨域规则生效的路径（所有接口）
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        source.registerCorsConfiguration("/**", corsConfig);

        // 3. 返回跨域过滤器（Gateway 专属）
        return new CorsWebFilter(source);
    }
}