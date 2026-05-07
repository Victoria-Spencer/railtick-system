package org.rail.gatewayservice.filters;

import org.rail.gatewayservice.config.GatewayAuthProperties;
import org.rail.gatewayservice.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "user-id";
    private static final String TOKEN_HEADER = "token";
    private static final String X_REAL_IP_HEADER = "X-Real-IP";
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    @Autowired
    private GatewayAuthProperties gatewayAuthProperties;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // 所有请求都获取并传递IP
        String clientIp = getClientRealIp(request);
        ServerWebExchange newExchange = exchange.mutate()
                .request(builder -> builder
                        .header(X_REAL_IP_HEADER, clientIp)
                        .header(X_FORWARDED_FOR_HEADER, clientIp)
                )
                .build();

        // 判断是否需要拦截
        String path = request.getPath().toString();
        if(isExcludePath(path)) {
            return chain.filter(newExchange);
        }

        // 从请求头中获取token
        String token = exchange.getRequest().getHeaders().getFirst(TOKEN_HEADER);

        // 需要先去掉 Bearer 前缀才能进行解析
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7); // 截取第7位之后的内容，得到真实Token
        }

        // 校验token
        String userId = JwtTokenUtil.parseToken(token).toString();
        if (userId.isEmpty()) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        // 将userId存入请求头中
        newExchange = newExchange.mutate()
                .request(builder -> builder.header(USER_ID_HEADER, userId))
                .build();

        return chain.filter(newExchange);
    }

    /**
     * 获取客户端真实IP地址（支持多级代理）
     */
    private String getClientRealIp(ServerHttpRequest request) {
        List<String> xForwardedFor = request.getHeaders().get(X_FORWARDED_FOR_HEADER);
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String ip = xForwardedFor.getFirst();
            if (ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            if (!"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
        }

        String realIp = request.getHeaders().getFirst(X_REAL_IP_HEADER);
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    /**
     * 判断当前路径是否在排除列表中（支持模糊匹配）
     */
    private boolean isExcludePath(String path) {
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

    @Override
    public int getOrder() {
        return 0;
    }
}
