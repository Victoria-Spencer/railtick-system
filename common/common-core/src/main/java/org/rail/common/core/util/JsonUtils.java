package org.rail.common.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * JSON操作工具类，提供JSON序列化和反序列化方法
 * 复用Spring全局唯一的ObjectMapper，保证全链路格式统一
 */
@Component
public class JsonUtils implements ApplicationContextAware {

	/**
	 * 配置了通用设置的Jackson ObjectMapper实例
	 */
	private static ObjectMapper mapper;

	/**
	 * Spring启动时自动注入上下文，赋值给静态字段
	 */
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		JsonUtils.mapper = applicationContext.getBean(ObjectMapper.class);
	}

	/**
	 * 将对象转换为JSON字符串
	 */
	public static String toJson(Object obj) {
		try {
			return mapper.writeValueAsString(obj);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 将JSON字符串转换为指定类的对象
	 */
	public static <T> T fromJson(String json, Class<T> clazz) {
		try {
			return mapper.readValue(json, clazz);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 将JSON字符串转换为JsonNode
	 */
	public static JsonNode fromJson(String json) {
		try {
			return mapper.readTree(json);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 将JSON输入流转换为JsonNode
	 */
	public static JsonNode fromJson(InputStream json) {
		try {
			return mapper.readTree(json);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 将JSON字符串转换为指定类型的对象列表
	 */
	public static <T> List<T> fromJsonToList(String json, Class<T> clazz) {
		try {
			JavaType javaType = mapper.getTypeFactory().constructCollectionType(List.class, clazz);
			return mapper.readValue(json, javaType);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 将JSON字符串转换为Map
	 */
	public static <K, V> Map<K, V> fromJsonToMap(String json) {
		try {
			return mapper.readValue(json, new TypeReference<>() {
			});
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 从文件读取JSON并转换为指定类的对象
	 */
	public static <T> T fromJsonFile(String filePath, Class<T> clazz) {
		try {
			return mapper.readValue(filePath, clazz);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 将Map转换为指定类的对象
	 */
	public static <T> T fromMap(Map<String, Object> map, Class<T> clazz) {
		return mapper.convertValue(map, clazz);
	}

	/**
	 * 将对象转换为Map
	 */
	public static Map<String, Object> fromObjectToMap(Object obj) {
		return mapper.convertValue(obj, new TypeReference<>() {
		});
	}

	/**
	 * 校验字符串是否为合法JSON
	 */
	public static boolean isValidJson(String json) {
		if (json == null || json.trim().isEmpty()) {
			return false;
		}

		try {
			JsonNode node = mapper.readTree(json);
			return node != null;
		}
		catch (JsonProcessingException e) {
			return false;
		}
	}

	/**
	 * 检查字符串是否为JSON数组
	 */
	public static boolean isJsonArray(String json) {
		if (json == null || json.trim().isEmpty()) {
			return false;
		}

		try {
			JsonNode node = mapper.readTree(json);
			return node.isArray();
		}
		catch (JsonProcessingException e) {
			return false;
		}
	}

}