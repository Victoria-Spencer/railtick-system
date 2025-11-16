package org.rail.commonapi.autoConfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.rail.commonapi") // 扫描common-service所有Bean
public class CommonApiAutoConfiguration {
}