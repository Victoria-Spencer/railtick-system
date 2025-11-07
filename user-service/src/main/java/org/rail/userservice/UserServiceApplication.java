package org.rail.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
//@ComponentScan(basePackages = {
//		"org.rail.userservice", // 自身包
//		"org.rail.commonservice" // 扫描 common-service 中的 Bean（包括全局异常处理器）
//})
//@EnableDiscoveryClient // 开启服务注册发现
public class UserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
