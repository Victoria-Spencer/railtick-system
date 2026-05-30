package org.rail.common.feign.config;

import feign.Response;
import feign.codec.Decoder;
import org.rail.common.core.config.SensitiveProperties;
import org.rail.common.core.constant.SensitiveConstants;
import org.rail.common.core.exception.SensitiveDataException;
import org.rail.common.core.util.LogUtils;
import org.rail.common.core.util.security.AESCryptUtils;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 调用方 Feign响应解密器（接收被调用方的密文 → 自动解密）
 */
public class FeignResponseDecryptDecoder implements Decoder {

    private static final Map<Class<?>, Field[]> FIELD_CACHE = new WeakHashMap<>();
    private final SpringDecoder springDecoder;
    private final SensitiveProperties properties;

    public FeignResponseDecryptDecoder(HttpMessageConverters converters, SensitiveProperties properties) {
        this.springDecoder = new SpringDecoder(() -> converters);
        this.properties = properties;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
        if (!properties.getEncryptEnabled()) {
            return springDecoder.decode(response, type);
        }

        Object body = springDecoder.decode(response, type);
        if (body == null) {
            return null;
        }

        try {
            decryptFields(body);
            return body;
        } catch (Exception e) {
            throw new SensitiveDataException("Feign响应体解密失败", e);
        }
    }

    /**
     * 递归解密
     */
    private void decryptFields(Object obj) {
        if (obj == null || isSimpleType(obj.getClass())) {
            return;
        }

        // 集合处理
        if (obj instanceof Iterable<?> iterable) {
            iterable.forEach(this::decryptFields);
            return;
        }

        // Map处理
        if (obj instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> typedMap = (Map<Object, Object>) map;

            for (Map.Entry<Object, Object> entry : typedMap.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();

                // 判断key是否为传输敏感字段 → 解密value
                if (key instanceof String keyStr && properties.getTransportSensitiveFields().contains(keyStr)) {
                    if (value instanceof String cipherText && !cipherText.isBlank()) {
                        if (cipherText.startsWith(SensitiveConstants.CIPHER_PREFIX)) {
                            String realCipher = cipherText.substring(SensitiveConstants.CIPHER_PREFIX.length());
                            entry.setValue(AESCryptUtils.decrypt(realCipher));
                        }
                    }
                }

                // 递归处理嵌套结构
                decryptFields(value);
            }
            return;
        }

        // 数组处理
        if (obj.getClass().isArray()) {
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                decryptFields(Array.get(obj, i));
            }
            return;
        }

        Field[] fields = getAllFields(obj.getClass());

        for (Field field : fields) {
            try {
                // 跳过静态、常量字段
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }

                Object fieldValue = field.get(obj);

                // 白名单字段 + 字符串 + 带密文前缀 → 解密
                if (properties.getTransportSensitiveFields().contains(field.getName())) {
                    if (fieldValue instanceof String cipherText && !cipherText.isBlank()) {
                        if (cipherText.startsWith(SensitiveConstants.CIPHER_PREFIX)) {
                            String realCipher = cipherText.substring(SensitiveConstants.CIPHER_PREFIX.length());
                            String decryptStr = AESCryptUtils.decrypt(realCipher);
                            field.set(obj, decryptStr);
                        }
                    }
                }

                // 递归子对象
                decryptFields(fieldValue);
            } catch (Exception e) {
                LogUtils.error("Feign调用方字段[{}]解密失败", field.getName(), e);
                throw new SensitiveDataException("Feign字段解密失败：" + field.getName(), e);
            }
        }
    }

    /**
     * 统一：获取当前类 + 所有父类字段
     */
    private Field[] getAllFields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, key -> {
            List<Field> fieldList = new ArrayList<>();
            Class<?> currentClass = key;
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

    // 基础类型判断
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
}