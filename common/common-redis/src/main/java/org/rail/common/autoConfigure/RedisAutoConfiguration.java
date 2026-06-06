package org.rail.common.autoConfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.rail.common.redis") // 扫描common-redis所有Bean
public class RedisAutoConfiguration {
}
