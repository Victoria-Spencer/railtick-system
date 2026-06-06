package org.rail.common.web.handler;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import org.rail.common.core.config.SensitiveProperties;
import org.rail.common.core.constant.RequestHeaderConstants;
import org.rail.common.core.exception.SensitiveDataException;
import org.rail.common.core.util.security.CryptoUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 响应体敏感字段脱敏处理器。针对返回给前端的响应体，自动对配置的敏感字段进行打码处理
 * 敏感数据禁止用「裸 String」作为响应体
 */
@ControllerAdvice
public class MaskAdvice implements ResponseBodyAdvice<Object> {

    // 全局缓存字段，避免重复反射获取
    private static final Map<Class<?>, Field[]> FIELD_CACHE = new WeakHashMap<>();
    private final SensitiveProperties properties;
    private final ObjectMapper objectMapper;

    // 构造器注入，消除字段注入警告
    public MaskAdvice(SensitiveProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 控制哪些响应体需要脱敏处理
     * 这里默认所有响应体都处理，可根据业务扩展过滤规则
     */
    @Override
    public boolean supports(
            @Nullable MethodParameter returnType,
            @Nullable Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  @Nullable MethodParameter returnType,
                                  @Nullable MediaType selectedContentType,
                                  @Nullable Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @Nullable ServerHttpRequest request,
                                  @Nullable ServerHttpResponse response) {
        if (!properties.getEncryptEnabled()) {
            return body;
        }

        // Feign 远程调用 → 直接跳过脱敏
        if (request != null && request.getHeaders().containsKey(RequestHeaderConstants.FEIGN_REQUEST_HEADER)) {
            return body;
        }

        if (body == null || isSimpleType(body.getClass())) {
            return body;
        }

        try {
            Object copyBody = deepCopy(body);
            // 脱敏副本
            maskField(copyBody);
            return copyBody;
        } catch (SensitiveDataException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("响应体处理失败", e);
        }
    }

    /**
     * 递归处理敏感字段脱敏
     */
    private void maskField(Object obj) {
        if (obj == null || isSimpleType(obj.getClass())) {
            return;
        }

        // 处理集合（List/Set等）
        if (obj instanceof Collection<?> collection) {
            collection.forEach(this::maskField);
            return;
        }

        if (obj instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> typedMap = (Map<Object, Object>) map;

            for (Map.Entry<Object, Object> entry : typedMap.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();

                // 判断key是否符合敏感字段配置，符合则对value脱敏
                if (key instanceof String keyStr && properties.getMaskSensitiveFields().contains(keyStr)) {
                    if (value instanceof String original && !original.isBlank()) {
                        entry.setValue(CryptoUtils.mask(original));
                    }
                }
                // 递归处理嵌套结构
                maskField(value);
            }
            return;
        }

        // 处理数组
        if (obj.getClass().isArray()) {
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                maskField(Array.get(obj, i));
            }
            return;
        }

        Field[] fields = getAllFields(obj.getClass());

        for (Field field : fields) {
            try {
                // 跳过静态/常量字段
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }

                Object fieldValue = field.get(obj);

                if (properties.getMaskSensitiveFields().contains(field.getName())) {
                    if (fieldValue instanceof String original) {
                        String masked = CryptoUtils.mask(original);
                        field.set(obj, masked);
                    }
                }

                maskField(fieldValue);
            } catch (Exception e) {
                throw new SensitiveDataException("字段脱敏失败：" + field.getName(), e);
            }
        }
    }

    /**
     * 获取当前类 + 所有父类的字段（支持继承实体，统一规范）
     */
    private Field[] getAllFields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, key -> {
            List<Field> fieldList = new ArrayList<>();
            Class<?> currentClass = key;
            // 遍历所有父类直至Object
            while (currentClass != null && currentClass != Object.class) {
                Field[] declaredFields = currentClass.getDeclaredFields();
                for (Field field : declaredFields) {
                    field.setAccessible(true);
                    fieldList.add(field);
                }
                currentClass = currentClass.getSuperclass();
            }
            return fieldList.toArray(new Field[0]);
        });
    }

    /**
     * 判断是否为基础类型/包装类型/字符串
     */
    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || CharSequence.class.isAssignableFrom(clazz)
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class == clazz
                || Character.class == clazz
                || Class.class == clazz
                || LocalDateTime.class.isAssignableFrom(clazz)
                || LocalDate.class.isAssignableFrom(clazz)
                || LocalTime.class.isAssignableFrom(clazz)
                || clazz.isEnum();
    }

    /**
     * Jackson 深拷贝
     */
    private <T> T deepCopy(T obj) throws Exception {
        if (obj == null) {
            return null;
        }
        JavaType javaType = objectMapper.getTypeFactory().constructType(obj.getClass());
        String json = objectMapper.writeValueAsString(obj);
        return objectMapper.readValue(json, javaType);
    }
}