package org.rail.common.core.context;

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
		return requestContextThreadLocal.get();
	}

	/** 清空当前线程的请求上下文 */
	public static void clearRequestContext() {
		requestContextThreadLocal.remove();
	}

}