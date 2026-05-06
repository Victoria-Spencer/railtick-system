package org.rail.common.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.redisson.codec.JsonJacksonCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Redisson 独立 JSON 序列化配置类
 * 职责单一：仅配置全局序列化器，不耦合连接信息
 */
@Configuration
public class RedissonJsonCodecConfig {

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

        // 注册模块
        objectMapper.registerModule(javaTimeModule);

        return new JsonJacksonCodec(objectMapper);
    }
}