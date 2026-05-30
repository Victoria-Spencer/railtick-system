package org.rail.common.db.interceptor;

import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.rail.common.core.config.SensitiveProperties;
import org.rail.common.core.constant.SensitiveConstants;
import org.rail.common.core.exception.SensitiveDataException;
import org.rail.common.core.util.LogUtils;
import org.rail.common.core.util.security.AESCryptUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 数据库查询自动解密拦截器。针对MyBatis查询结果中的敏感字段，进行自动AES解密处理。
 * 敏感数据禁止用「裸 String」作为数据库结果
 */
@Component
@Intercepts(@Signature(
        type = ResultSetHandler.class,
        method = "handleResultSets",
        args = Statement.class
))
public class DbDecryptInterceptor implements Interceptor {

    // 全局缓存字段，避免重复反射获取
    private static final Map<Class<?>, Field[]> FIELD_CACHE = new WeakHashMap<>();
    private final SensitiveProperties properties;

    public DbDecryptInterceptor(SensitiveProperties properties) {
        this.properties = properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.getEncryptEnabled()) {
            return invocation.proceed();
        }

        Object result = invocation.proceed();

        if (result == null || isSimpleType(result.getClass())) {
            return result;
        }

        try {
            decryptSensitiveFields(result);
            return result;
        } catch (Exception e) {
            throw new SensitiveDataException("数据库结果集解密失败", e);
        }
    }

    /**
     * 递归处理：仅解密白名单字段
     */
    private void decryptSensitiveFields(Object obj) {
        if (obj == null || isSimpleType(obj.getClass())) {
            return;
        }

        // 集合处理（List/Set）
        if (obj instanceof Collection<?> collection) {
            collection.forEach(this::decryptSensitiveFields);
            return;
        }

        // Map处理
        if (obj instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> typedMap = (Map<Object, Object>) map;

            for (Map.Entry<Object, Object> entry : typedMap.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();

                // 判断key是否为数据库敏感字段 → 解密value
                if (key instanceof String keyStr && properties.getDbSensitiveFields().contains(keyStr)) {
                    if (value instanceof String cipherText && !cipherText.isBlank()) {
                        // 仅解密带前缀的密文
                        if (cipherText.startsWith(SensitiveConstants.CIPHER_PREFIX)) {
                            String realCipher = cipherText.substring(SensitiveConstants.CIPHER_PREFIX.length());
                            entry.setValue(AESCryptUtils.decrypt(realCipher));
                        }
                    }
                }
                decryptSensitiveFields(value);
            }
            return;
        }


        // 数组处理
        if (obj.getClass().isArray()) {
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                decryptSensitiveFields(Array.get(obj, i));
            }
            return;
        }

        Field[] fields = getAllFields(obj.getClass());

        for (Field field : fields) {
            try {
                // 跳过静态、常量字段
                if (Modifier.isStatic(field.getModifiers()) ||
                        Modifier.isFinal(field.getModifiers())) {
                    continue;
                }

                Object fieldValue = field.get(obj);

                if (properties.getDbSensitiveFields().contains(field.getName())) {
                    if (fieldValue instanceof String cipherText && !cipherText.isBlank()) {
                        // 仅解密带前缀的密文，防止明文解密报错
                        if (cipherText.startsWith(SensitiveConstants.CIPHER_PREFIX)) {
                            String realCipher = cipherText.substring(SensitiveConstants.CIPHER_PREFIX.length());
                            field.set(obj, AESCryptUtils.decrypt(realCipher));
                        }
                    }
                }

                decryptSensitiveFields(fieldValue);
            } catch (Exception e) {
                LogUtils.error("字段[{}]解密失败", field.getName(), e);
                throw new SensitiveDataException("字段解密失败：" + field.getName(), e);
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
                || LocalTime.class.isAssignableFrom(clazz)
                || clazz.isEnum();
    }
}