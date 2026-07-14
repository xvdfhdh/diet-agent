package com.diet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 饮食推荐 Agent 应用启动入口。
 * <p>
 * 与 {@code com.agent.AgentApplication} 相互独立：本类只扫描 {@code com.diet} 包下的组件，
 * 运行时使用 {@code com.diet.DietApplication} 作为主类即可启动饮食推荐服务。
 * </p>
 */
@MapperScan("com.diet.mapper")
@SpringBootApplication
public class DietApplication {

    public static void main(String[] args) {
        SpringApplication.run(DietApplication.class, args);
    }
}
