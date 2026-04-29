package org.rail.common.core.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rail.common.core.config.RequestInterceptorProperties;
import org.rail.common.core.util.ThreadLocalUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 通用请求拦截器
 */
@Component
public class CommonRequestInterceptor implements HandlerInterceptor {

    private static final String USER_ID_HEADER = "user-id";

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
            return false; // 返回false，终止请求继续处理
        }

        // 将user-id存入到线程中
        ThreadLocalUtils.set("userId", userId);

//        System.out.println("threadLocal：" + userId);
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

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        ThreadLocalUtils.removeAll();
    }
}
