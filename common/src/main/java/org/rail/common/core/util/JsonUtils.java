package org.rail.common.core.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * JSON操作工具类。提供JSON序列化和反序列化方法。
 */
public class JsonUtils {

	/**
	 * 配置了通用设置的Jackson ObjectMapper实例 -- GETTER -- 返回配置好的ObjectMapper实例
	 *
	 */
	@Getter
	private static final ObjectMapper objectMapper = new ObjectMapper();

	static {
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		// objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
		objectMapper.findAndRegisterModules();
	}

	/**
	 * 将对象转换为JSON字符串
	 */
	public static String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
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
			return objectMapper.readValue(json, clazz);
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
			return objectMapper.readTree(json);
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
			return objectMapper.readTree(json);
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
			JavaType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
			return objectMapper.readValue(json, javaType);
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
			return objectMapper.readValue(json, new TypeReference<>() {
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
			return objectMapper.readValue(filePath, clazz);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 将Map转换为指定类的对象
	 */
	public static <T> T fromMap(Map<String, Object> map, Class<T> clazz) {
		return objectMapper.convertValue(map, clazz);
	}

	/**
	 * 将对象转换为Map
	 */
	public static Map<String, Object> fromObjectToMap(Object obj) {
		return objectMapper.convertValue(obj, new TypeReference<>() {
		});
	}

	/**
	 * 校验字符串是否为合法JSON
	 */
	public static boolean isValidJson(String json) {
		try {
			ObjectMapper mapper = new ObjectMapper();
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
		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode node = mapper.readTree(json);
			return node.isArray();
		}
		catch (JsonProcessingException e) {
			return false;
		}
	}

}