package org.rail.api.autoConfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.rail.api") // 扫描common-service所有Bean
public class apiAutoConfiguration {
}