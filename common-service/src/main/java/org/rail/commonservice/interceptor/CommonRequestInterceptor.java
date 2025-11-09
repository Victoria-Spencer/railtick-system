package org.rail.commonservice.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rail.commonservice.exception.BusinessException;
import org.rail.commonservice.utils.ThreadLocalUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Enumeration;

/**
 * 通用请求拦截器
 */
@Component
public class CommonRequestInterceptor implements HandlerInterceptor {

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从请求头中提取user-id
        String userId = request.getHeader("user-id");
        if (userId == null || userId.trim().isEmpty()) {
            // 返回 401 状态码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("未获取到用户信息（user-id为空）");
            return false; // 返回false，终止请求继续处理
        }

        // 将user-id存入到线程中
        ThreadLocalUtils.set(userId);

//        System.out.println("threadLocal：" + userId);
        return true;
    }

    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理 ThreadLocal，避免内存泄漏
        ThreadLocalUtils.remove();
    }
}
