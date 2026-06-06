package org.rail.common.autoConfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.rail.common.web") // 扫描common-web所有Bean
public class WebAutoConfiguration {
}