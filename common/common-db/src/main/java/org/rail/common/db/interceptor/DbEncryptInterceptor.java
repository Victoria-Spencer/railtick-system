package org.rail.common.db.interceptor;

import org.apache.ibatis.executor.parameter.ParameterHandler;
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
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 数据库入库自动加密拦截器。针对MyBatis参数中的敏感字段，进行自动AES加密处理。
 * 敏感数据禁止用「裸 String」作为数据库参数
 */
@Component
@Intercepts(@Signature(
        type = ParameterHandler.class,
        method = "setParameters",
        args = PreparedStatement.class
))
public class DbEncryptInterceptor implements Interceptor {

    // 全局缓存字段，避免重复反射获取
    private static final Map<Class<?>, Field[]> FIELD_CACHE = new WeakHashMap<>();
    private final SensitiveProperties properties;

    public DbEncryptInterceptor(SensitiveProperties properties) {
        this.properties = properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.getEncryptEnabled()) {
            return invocation.proceed();
        }

        ParameterHandler handler = (ParameterHandler) invocation.getTarget();
        Object paramObj = handler.getParameterObject();

        // 空参数/基础类型
        if (paramObj == null || isSimpleType(paramObj.getClass())) {
            return invocation.proceed();
        }

        // MyBatis 临时创建的一次性对象，不污染业务逻辑
        encryptSensitiveFields(paramObj);
        return invocation.proceed();
    }

    /**
     * 递归处理：仅加密白名单字段
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

        // 处理 Map
        if (obj instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> typedMap = (Map<Object, Object>) map;

            for (Map.Entry<Object, Object> entry : typedMap.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();

                // 判断key是否为数据库敏感字段 → 加密value
                if (key instanceof String keyStr && properties.getDbSensitiveFields().contains(keyStr)) {
                    if (value instanceof String original && !original.isBlank()) {
                        // 已加密则跳过
                        if (original.startsWith(SensitiveConstants.CIPHER_PREFIX)) {
                            continue;
                        }
                        String encryptStr = AESCryptUtils.encrypt(original);
                        entry.setValue(SensitiveConstants.CIPHER_PREFIX + encryptStr);
                    }
                }
                encryptSensitiveFields(value);
            }
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

        for (Field field : fields) {
            try {
                // 跳过静态、常量字段
                if (Modifier.isStatic(field.getModifiers()) ||
                        Modifier.isFinal(field.getModifiers())) {
                    continue;
                }

                Object fieldValue = field.get(obj);

                if (properties.getDbSensitiveFields().contains(field.getName())) {
                    if (fieldValue instanceof String original && !original.isBlank()) {
                        if (original.startsWith(SensitiveConstants.CIPHER_PREFIX)) {
                            continue;
                        }
                        String encryptStr = AESCryptUtils.encrypt(original);
                        field.set(obj, SensitiveConstants.CIPHER_PREFIX + encryptStr);
                    }
                }

                encryptSensitiveFields(fieldValue);
            } catch (Exception e) {
                LogUtils.error("字段[{}]加密失败", field.getName(), e);
                throw new SensitiveDataException("字段加密失败：" + field.getName(), e);
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