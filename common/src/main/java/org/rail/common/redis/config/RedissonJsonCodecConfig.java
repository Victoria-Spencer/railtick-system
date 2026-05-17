package org.rail.common.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.rail.common.core.config.SensitiveProperties;
import org.rail.common.core.constant.SensitiveConstants;
import org.rail.common.core.exception.SensitiveDataException;
import org.rail.common.core.util.LogUtils;
import org.rail.common.core.util.security.AESCryptUtils;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.redisson.codec.JsonJacksonCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Redisson 独立 JSON 序列化配置类
 * 职责单一：仅配置全局序列化器，不耦合连接信息
 * 集成功能：Java8时间格式化 + 敏感字段自动加密/解密
 */
@Configuration
public class RedissonJsonCodecConfig {

    private final SensitiveProperties properties;

    public RedissonJsonCodecConfig(SensitiveProperties properties) {
        this.properties = properties;
    }

    /**
     * 创建配置好时间格式的JSON序列化器
     * 单例、线程安全，全项目共用
     */
    @Bean
    public JsonJacksonCodec redissonJsonCodec() {
        ObjectMapper objectMapper = new ObjectMapper();

        // 配置 Java8 时间序列化
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));

        objectMapper.registerModule(javaTimeModule);

        return new JsonJacksonCodec(objectMapper) {
            private static final Map<Class<?>, Field[]> FIELD_CACHE = new WeakHashMap<>();

            /**
             * 重写编码器：先加密 → 再原生编码
             */
            @Override
            public Encoder getValueEncoder() {
                // 获取原生编码器
                Encoder originalEncoder = super.getValueEncoder();
                return in -> {
                    // 加密敏感字段
                    if (in != null) {
                        encryptSensitiveFields(in);
                    }
                    // 原生序列化
                    return originalEncoder.encode(in);
                };
            }

            /**
             * 重写解码器：先原生解码 → 再解密
             */
            @Override
            public Decoder<Object> getValueDecoder() {
                // 获取原生解码器
                Decoder<Object> originalDecoder = super.getValueDecoder();
                return (buf, state) -> {
                    // 原生反序列化
                    Object obj = originalDecoder.decode(buf, state);
                    // 解密敏感字段
                    if (obj != null) {
                        decryptSensitiveFields(obj);
                    }
                    return obj;
                };
            }

            /**
             * 递归加密对象中的敏感字段
             * 支持：普通对象、集合、Map、数组
             *
             * @param obj 待加密的对象
             */
            private void encryptSensitiveFields(Object obj) {
                if (!properties.getEncryptEnabled()) {
                    return;
                }

                if (obj == null || isSimpleType(obj.getClass())) {
                    return;
                }

                if (obj instanceof Collection<?> collection) {
                    collection.forEach(this::encryptSensitiveFields);
                    return;
                }

                if (obj instanceof Map<?, ?> map) {
                    map.values().forEach(this::encryptSensitiveFields);
                    return;
                }

                if (obj.getClass().isArray()) {
                    int len = Array.getLength(obj);
                    for (int i=0;i<len;i++) encryptSensitiveFields(Array.get(obj,i));
                    return;
                }

                Field[] fields = getAllFields(obj.getClass());
                for (Field f : fields) {
                    try {
                        if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
                        Object v = f.get(obj);
                        if (properties.getDbSensitiveFields().contains(f.getName())) {
                            if (v instanceof String original && !original.isBlank()) {
                                // 已加密则跳过
                                if (original.startsWith(SensitiveConstants.CIPHER_PREFIX)) {
                                    continue;
                                }
                                String encryptStr = AESCryptUtils.encrypt(original);
                                f.set(obj, SensitiveConstants.CIPHER_PREFIX + encryptStr);
                            }
                        }
                        encryptSensitiveFields(v);
                    } catch (Exception e) {
                        LogUtils.error("Redis加密字段[{}]失败", f.getName(), e);
                        throw new SensitiveDataException("Redis加密字段失败：" + f.getName(), e);
                    }
                }
            }

            /**
             * 递归解密对象中的敏感字段
             * 支持：普通对象、集合、Map、数组
             *
             * @param obj 待解密的对象
             */
            private void decryptSensitiveFields(Object obj) {
                if (!properties.getEncryptEnabled()) {
                    return;
                }

                if (obj == null || isSimpleType(obj.getClass())){
                    return;
                }

                if (obj instanceof Collection<?> collection) {
                    collection.forEach(this::decryptSensitiveFields);
                    return;
                }

                if (obj instanceof Map<?, ?> map) {
                    map.values().forEach(this::decryptSensitiveFields);
                    return;
                }

                if (obj.getClass().isArray()) {
                    int len = Array.getLength(obj);
                    for (int i=0;i<len;i++) decryptSensitiveFields(Array.get(obj,i));
                    return;
                }

                Field[] fields = getAllFields(obj.getClass());
                for (Field f : fields) {
                    try {
                        if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
                        Object v = f.get(obj);
                        if (properties.getDbSensitiveFields().contains(f.getName())) {
                            if (v instanceof String cipherText && !cipherText .isBlank()) {
                                if (cipherText.startsWith(SensitiveConstants.CIPHER_PREFIX)) {
                                    String realCipher = cipherText.substring(SensitiveConstants.CIPHER_PREFIX.length());
                                    f.set(obj, AESCryptUtils.decrypt(realCipher));
                                }
                            }
                        }
                        decryptSensitiveFields(v);
                    } catch (Exception e) {
                        LogUtils.error("Redis解密字段[{}]失败", f.getName(), e);
                        throw new SensitiveDataException("Redis解密字段失败：" + f.getName(), e);
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

            /**
             * 判断是否为基础数据类型
             * 基础类型无需进行加解密处理
             *
             * @param clazz 待判断的类
             * @return true-基础类型 false-复杂类型
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
        };
    }
}