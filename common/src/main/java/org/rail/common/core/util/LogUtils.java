/*
 * 12306 项目专用日志工具类
 */
package org.rail.common.core.util;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.rail.common.core.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * 日志操作工具类
 */
public class LogUtils {

	// 通用应用日志
	private final static Logger logger = LoggerFactory.getLogger("application");

	// 监控日志
	private final static Logger monitorLogger = LoggerFactory.getLogger("monitor-log");

	// 链路追踪日志
	private final static Logger traceLogger = LoggerFactory.getLogger("trace-log");

	// 日志分隔符
	public static final String SIMPLE_LOG_SPLIT = "@@@";

	// 状态常量
	public static final String SUCCESS = "success";
	public static final String FAIL = "fail";

	/**
	 * debug级别日志
	 */
	public static void debug(Object... objects) {
		LogContent build = build(objects);
		if (build != null) {
			logger.debug(build.logData);
		}
	}

	/**
	 * info级别日志（带traceId）
	 */
	public static void info(Object... objects) {
		LogContent build = build(objects);
		if (build != null) {
			logger.info("{}@@@{}", getTraceId(), build.logData);
		}
	}

	/**
	 * warn级别日志（带traceId）
	 */
	public static void warn(Object... objects) {
		LogContent build = build(objects);
		if (build != null) {
			logger.warn("{}@@@{}", getTraceId(), build.logData);
		}
	}

	/**
	 * error级别日志（带traceId）
	 */
	public static void error(Object... objects) {
		LogContent build = build(objects);
		if (build != null) {
			logger.error("{}@@@{}", getTraceId(), build.logData, build.getThrowable());
		}
	}

	/**
	 * 接口监控日志
	 */
	public static void monitor(String service, String action, Long start, String errorCode, Object input, Object output,
							   Object... objects) {
		monitor(null, service, action, start, errorCode, input, output, objects);
	}

	/**
	 * 带请求上下文的监控日志
	 */
	public static void monitor(RequestContext context, String service, String action, Long startTime, String errorCode,
							   Object input, Object output, Object... objects) {
		String status = StrUtil.isNotBlank(errorCode) && !errorCode.equalsIgnoreCase(SUCCESS) ? FAIL : SUCCESS;

		Long costTime = startTime == null || startTime == 0 ? 0 : System.currentTimeMillis() - startTime;
		StringBuilder builder = new StringBuilder();
		builder.append(getTraceId());

		builder.append(SIMPLE_LOG_SPLIT).append(service);
		builder.append(SIMPLE_LOG_SPLIT).append(action);
		builder.append(SIMPLE_LOG_SPLIT).append(status);
		builder.append(SIMPLE_LOG_SPLIT).append(costTime);
		builder.append(SIMPLE_LOG_SPLIT).append(errorCode);

		if (context != null) {
			builder.append(SIMPLE_LOG_SPLIT).append(context.getRequestId());
			builder.append(SIMPLE_LOG_SPLIT).append(context.getAccountId());
		} else {
			builder.append(SIMPLE_LOG_SPLIT).append("");
			builder.append(SIMPLE_LOG_SPLIT).append("");
		}

		Throwable throwable = null;
		if (Objects.nonNull(objects)) {
			for (Object obj : objects) {
				if (obj instanceof Throwable) {
					throwable = (Throwable) obj;
					continue;
				}

				String value;
				if (obj instanceof String) {
					value = String.valueOf(obj);
				}
				else {
					value = JsonUtils.toJson(obj);
				}

				builder.append(SIMPLE_LOG_SPLIT).append(value);
			}
		}

		String logStr = builder.toString();
		monitorLogger.info(logStr);
		if (Objects.nonNull(throwable)) {
			logger.error(logStr, throwable);
		}

		if (context != null) {
			trace(context, action, errorCode, startTime, input, output, objects);
		}
	}

	/**
	 * 请求链路追踪日志
	 */
	public static void trace(RequestContext context, String action, String resultCode, Long startTime, Object input,
							 Object output, Object... objects) {
		Long costTime = startTime == null || startTime == 0 ? 0 : System.currentTimeMillis() - startTime;

		StringBuilder trace = new StringBuilder();
		trace.append("trace").append(SIMPLE_LOG_SPLIT);
		trace.append(context.getRequestId()).append(SIMPLE_LOG_SPLIT);
		trace.append(context.getAccountId()).append(SIMPLE_LOG_SPLIT);

		trace.append(action).append(SIMPLE_LOG_SPLIT);
		trace.append(resultCode).append(SIMPLE_LOG_SPLIT);
		trace.append(costTime).append(SIMPLE_LOG_SPLIT);
		trace.append(JsonUtils.toJson(input)).append(SIMPLE_LOG_SPLIT);
		trace.append(JsonUtils.toJson(output)).append(SIMPLE_LOG_SPLIT);
		trace.append(context.getSource()).append(SIMPLE_LOG_SPLIT);

		for (Object obj : objects) {
			String value;
			if (obj instanceof String) {
				value = String.valueOf(obj);
			} else if (obj instanceof Throwable err) {
				value = err.getMessage();
			} else {
				value = JsonUtils.toJson(obj);
			}
			trace.append(value).append(SIMPLE_LOG_SPLIT);
		}

		traceLogger.info(trace.toString());
	}

	/**
	 * 构建日志内容
	 */
	private static LogContent build(Object... objects) {
		if (objects == null || objects.length == 0) {
			return null;
		}

		try {
			StringBuilder builder = new StringBuilder();
			Throwable throwable = null;

			for (Object obj : objects) {
				if (obj instanceof Throwable) {
					throwable = (Throwable) obj;
					continue;
				}

				String value;
				if (obj instanceof String) {
					value = String.valueOf(obj);
				}
				else {
					value = JsonUtils.toJson(obj);
				}

				builder.append(SIMPLE_LOG_SPLIT).append(value);
			}

			LogContent logContent = new LogContent();
			logContent.setLogData(builder.toString());
			logContent.setThrowable(throwable);
			return logContent;
		} catch (Exception exception) {
			logger.error("build log error", exception);
			return null;
		}
	}

	/**
	 * 日志内容对象
	 */
	@Data
	static class LogContent {
		String logData;
		Throwable throwable;
	}

	/**
	 * 生成traceId
	 */
	private static String getTraceId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

}