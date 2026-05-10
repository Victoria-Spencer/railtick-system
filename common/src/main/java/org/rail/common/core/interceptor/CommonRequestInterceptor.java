package org.rail.common.core.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rail.common.core.config.RequestInterceptorProperties;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.core.util.thread.ThreadLocalUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.UUID;

/**
 * 通用请求拦截器
 */
@Component
public class CommonRequestInterceptor implements HandlerInterceptor {

    private static final String USER_ID_HEADER = "user-id";
    private static final String REQUEST_ID_HEADER = "request-id";
    private static final String X_REAL_IP_HEADER = "X-Real-IP";
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    @Autowired
    private RequestInterceptorProperties interceptorProperties;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();

        if (isExcludePath(requestURI)) {
            return true;
        }

        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null || userId.trim().isEmpty()) {
            // 返回 401 状态码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("未获取到用户信息（user-id为空）");
            return false;
        }

        RequestContext context = new RequestContext();
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        // 上游未传递 → 自己生成
        if (requestId == null || requestId.isBlank()) {
            requestId = getTraceId();
        }
        context.setRequestId(requestId);

        context.setStartTime(System.currentTimeMillis());
        context.setAccountId(userId);
        context.setCallerIp(getClientIp(request));

        RequestContextHolder.setRequestContext(context);

        return true;
    }

    /**
     * 支持Ant风格模糊匹配
     */
    private boolean isExcludePath(String path) {
        List<String> excludePaths = interceptorProperties.getExcludePaths();
        if (excludePaths == null || excludePaths.isEmpty()) {
            return false;
        }
        for (String excludePath : excludePaths) {
            if (pathMatcher.match(excludePath, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成traceId
     */
    private static String getTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 获取客户端真实IP（兼容代理/负载均衡场景）
     */
    private String getClientIp(HttpServletRequest request) {
        // 优先从代理请求头获取真实IP
        String xForwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader(X_REAL_IP_HEADER);
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp.trim();
        }

        return "unknown";
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        ThreadLocalUtils.removeAll();
        RequestContextHolder.clearRequestContext();
    }
}
