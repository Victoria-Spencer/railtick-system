package org.rail.common.autoConfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.rail.common.db") // 扫描common-db所有Bean
public class DbAutoConfiguration {
}
