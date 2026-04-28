package org.rail.common.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "request.interceptor")
public class RequestInterceptorProperties {
    private List<String> excludePaths;
}