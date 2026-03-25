package org.rail.gatewayservice.filters;

import org.rail.gatewayservice.config.GatewayAuthProperties;
import org.rail.gatewayservice.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    // 注入路径排除属性类
    @Autowired
    private GatewayAuthProperties gatewayAuthProperties;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1.判断是否需要拦截
        String path = exchange.getRequest().getPath().toString();
        if(isExclude(path)) {
            return chain.filter(exchange);
        }

        // 2.从请求头中获取token
        String token = exchange.getRequest().getHeaders().getFirst("token");

        // 3.需要先去掉 Bearer 前缀才能进行解析
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7); // 截取第7位之后的内容，得到真实Token
        }

        // 4.校验token
        String userId = JwtTokenUtil.parseToken(token).toString();
        if (userId == null) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        // 5.将userId存入请求头中
        ServerWebExchange newExchange = exchange.mutate()
                .request(builder -> builder.header("user-id", userId))
                .build();

        // 6.放行
        return chain.filter(newExchange);
    }

    /**
     * 判断当前路径是否在排除列表中（支持模糊匹配）
     */
    private boolean isExclude(String path) {
        List<String> excludePaths = gatewayAuthProperties.getExcludePaths();

        // 排除列表为空
        if(excludePaths == null || excludePaths.isEmpty()) {
            return false;
        }

        // 排除在外
        for (String excludePath : excludePaths) {
            if(pathMatcher.match(excludePath, path)) {
                return true;
            }
        }

        return false;
    }

    public int getOrder() {
        return 0;
    }
}
