package org.rail.common.autoConfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.rail.common.mq") // 扫描common-mq所有Bean
public class MqAutoConfiguration {
}
