package org.rail.common.feign.handler;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.rail.common.core.config.SensitiveProperties;
import org.rail.common.core.constant.SensitiveConstants;
import org.rail.common.core.exception.SensitiveDataException;
import org.rail.common.core.util.LogUtils;
import org.rail.common.core.util.security.AESCryptUtils;
import org.rail.common.feign.constant.FeignConstant;
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
 * Feign客户端（被调用方）加密处理器
 * 给Feign调用方返回数据前，对敏感字段自动加密
 * 敏感数据禁止用「裸 String」作为请求体
 */
@ControllerAdvice
public class FeignEncryptAdvice implements ResponseBodyAdvice<Object> {

    // 全局缓存字段
    private static final Map<Class<?>, Field[]> FIELD_CACHE = new WeakHashMap<>();
    private final SensitiveProperties properties;

    public FeignEncryptAdvice(SensitiveProperties properties) {
        this.properties = properties;
    }

    // 开启所有接口支持
    @Override
    public boolean supports(
            @Nullable MethodParameter returnType,
            @Nullable Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    // 响应体写入前加密
    @Override
    public Object beforeBodyWrite(
            @NotNull Object body,
            @Nullable MethodParameter returnType,
            @Nullable MediaType selectedContentType,
            @Nullable Class<? extends HttpMessageConverter<?>> selectedConverterType,
            @Nullable ServerHttpRequest request,
            @Nullable ServerHttpResponse response) {

        if (!properties.getEncryptEnabled()) {
            return body;
        }

        boolean isFeignCall = request != null
                && request.getHeaders().containsKey(FeignConstant.FEIGN_REQUEST_HEADER);

        if (!isFeignCall) {
            return body;
        }

        if (body == null || isSimpleType(body.getClass())) {
            return body;
        }

        encryptSensitiveFields(body);
        return body;
    }

    /**
     * 递归加密：和解密方法逻辑完全对称
     */
    private void encryptSensitiveFields(Object obj) {
        if (obj == null || isSimpleType(obj.getClass())) {
            return;
        }

        // 集合处理
        if (obj instanceof Collection<?> collection) {
            collection.forEach(this::encryptSensitiveFields);
            return;
        }

        // Map处理
        if (obj instanceof Map<?, ?> map) {
            map.values().forEach(this::encryptSensitiveFields);
            return;
        }

        // 数组处理
        if (obj.getClass().isArray()) {
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                encryptSensitiveFields(Array.get(obj, i));
            }
            return;
        }

        Field[] fields = getAllFields(obj.getClass());

        // 遍历字段加密
        for (Field field : fields) {
            try {
                // 跳过静态/常量
                if (Modifier.isStatic(field.getModifiers()) ||
                        Modifier.isFinal(field.getModifiers())) {
                    continue;
                }

                Object fieldValue = field.get(obj);

                // 白名单字段 → 加密
                if (properties.getTransportSensitiveFields().contains(field.getName())) {
                    if (fieldValue instanceof String plainText) {
                        if (plainText.startsWith(SensitiveConstants.CIPHER_PREFIX)) {
                            continue;
                        }
                        String encryptStr = AESCryptUtils.encrypt(plainText);
                        field.set(obj, SensitiveConstants.CIPHER_PREFIX + encryptStr);
                    }
                }

                // 递归处理子对象
                encryptSensitiveFields(fieldValue);
            } catch (Exception e) {
                LogUtils.error("Feign字段[{}]加密失败", field.getName(), e);
                throw new SensitiveDataException("Feign字段加密失败：" + field.getName(), e);
            }
        }
    }

    /**
     * 获取当前类 + 所有父类的字段（支持继承实体）
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
     * 基础类型判断
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
                || LocalTime.class.isAssignableFrom(clazz);
    }
}