package org.rail.gatewayservice.filters;

import org.rail.common.core.constant.RequestHeaderConstants;
import org.rail.common.core.exception.UnauthorizedException;
import org.rail.common.core.model.UserAuthInfo;
import org.rail.common.core.util.security.JwtTokenUtil;
import org.rail.gatewayservice.config.GatewayAuthProperties;
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

    @Autowired
    private GatewayAuthProperties gatewayAuthProperties;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String clientIp = getClientRealIp(request);
        String repeatToken = request.getHeaders().getFirst(RequestHeaderConstants.REPEAT_TOKEN_HEADER);

        // 构建请求头：统一透传 IP + 防重Token
        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .header(RequestHeaderConstants.X_REAL_IP_HEADER, clientIp)
                .header(RequestHeaderConstants.X_FORWARDED_FOR_HEADER, clientIp);

        if (repeatToken != null && !repeatToken.isBlank()) {
            requestBuilder.header(RequestHeaderConstants.REPEAT_TOKEN_HEADER, repeatToken);
        }

        // 白名单直接放行
        String path = request.getPath().toString();
        if(isExcludePath(path)) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        // 从请求头中获取token
        String token = exchange.getRequest().getHeaders().getFirst(RequestHeaderConstants.TOKEN_HEADER);

        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        UserAuthInfo authInfo;
        try {
            authInfo = JwtTokenUtil.parseToken(token);
        } catch (UnauthorizedException e) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        requestBuilder.header(RequestHeaderConstants.USER_ID_HEADER, authInfo.getUserId().toString());
        requestBuilder.header(RequestHeaderConstants.USER_NAME_HEADER, authInfo.getUsername());

        ServerWebExchange newExchange = exchange.mutate()
                .request(requestBuilder.build())
                .build();

        return chain.filter(newExchange);
    }

    /**
     * 获取客户端真实IP地址（支持多级代理）
     */
    private String getClientRealIp(ServerHttpRequest request) {
        List<String> xForwardedFor = request.getHeaders().get(RequestHeaderConstants.X_FORWARDED_FOR_HEADER);
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String ip = xForwardedFor.getFirst();
            if (ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            if (!"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
        }

        String realIp = request.getHeaders().getFirst(RequestHeaderConstants.X_REAL_IP_HEADER);
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
