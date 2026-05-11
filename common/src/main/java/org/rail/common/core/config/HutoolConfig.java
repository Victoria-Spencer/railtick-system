package org.rail.common.core.config;

import cn.hutool.json.JSONConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hutool 全局配置
 * 与 Jackson 配置保持统一
 */
@Configuration
public class HutoolConfig {

    /**
     * 全局JSON配置：与 Jackson 对齐
     * JsonInclude.NON_NULL → 不忽略 null 字段
     */
    @Bean
    public JSONConfig hutoolJsonConfig() {
        return JSONConfig.create()
                .setIgnoreNullValue(false)
                .setDateFormat("yyyy-MM-dd HH:mm:ss");
    }
}