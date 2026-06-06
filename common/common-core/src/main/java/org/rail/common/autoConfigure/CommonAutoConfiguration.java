package org.rail.common.autoConfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.rail.common.core") // 扫描common-core所有Bean
public class CommonAutoConfiguration {
}