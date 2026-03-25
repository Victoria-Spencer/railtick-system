package org.rail.common.core.autoConfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.rail.common") // 扫描common-service所有Bean
public class CommonAutoConfiguration {
}