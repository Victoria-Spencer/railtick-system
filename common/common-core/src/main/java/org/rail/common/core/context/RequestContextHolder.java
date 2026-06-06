package org.rail.common.core.context;

import java.util.UUID;

/**
 * 请求上下文持有者
 * 以线程本地（ThreadLocal）方式管理请求上下文，提供当前线程上下文的设置、获取、清空方法
 */
public class RequestContextHolder {

	/** 线程本地变量：存储每个线程独立的请求上下文 */
	private static final ThreadLocal<RequestContext> requestContextThreadLocal = new ThreadLocal<>();

	/** 为当前线程设置请求上下文 */
	public static void setRequestContext(RequestContext requestContext) {
		requestContextThreadLocal.set(requestContext);
	}

	/**  获取当前线程的请求上下文 */
	public static RequestContext getRequestContext() {
		RequestContext context = requestContextThreadLocal.get();
		// 空上下文 → 直接返回系统默认值
		if (context == null) {
			return createDefaultSystemContext();
		}
		return context;
	}

	/** 创建系统默认上下文（定时任务/MQ/项目初始化/内部调用 自动使用） */
	private static RequestContext createDefaultSystemContext() {
		return RequestContext.builder()
				.startTime(System.currentTimeMillis())
				.requestId(getTraceId())
				.userId("-1")       // 系统用户ID
				.username("system") // 系统用户
				.callerIp("127.0.0.1")
				.source("system")
				.build();
	}

	/** 生成traceId */
	private static String getTraceId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	/** 清空当前线程的请求上下文 */
	public static void clearRequestContext() {
		requestContextThreadLocal.remove();
	}

}