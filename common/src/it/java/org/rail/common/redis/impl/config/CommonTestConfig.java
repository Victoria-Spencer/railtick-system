package org.rail.common.redis.impl.config;

import org.rail.common.autoConfigure.CommonAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * common模块所有测试类共用的上下文配置
 * 解决：1. 无主启动类问题 2. 数据库自动配置报错问题
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@Import(CommonAutoConfiguration.class)
public class CommonTestConfig {
    // 空配置类，仅用于给测试提供上下文配置
}