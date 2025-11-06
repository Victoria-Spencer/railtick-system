package org.rail.commonservice.autoConfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.rail.commonservice") // 扫描common-service所有Bean
public class CommonServiceAutoConfiguration {
}