package org.rail.common.autoConfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.rail.common.feign") // 扫描common-feign所有Bean
public class FeignAutoConfiguration {
}
