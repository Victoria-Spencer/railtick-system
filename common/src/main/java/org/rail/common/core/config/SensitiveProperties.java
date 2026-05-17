package org.rail.common.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "sensitive")
public class SensitiveProperties {
    // 数据库敏感字段
    private Set<String> dbSensitiveFields;
    // 内网传输敏感字段
    private Set<String> transportSensitiveFields;
    // 前端脱敏敏感字段
    private Set<String> maskSensitiveFields;

    // 加密总开关
    private Boolean encryptEnabled = true;
}