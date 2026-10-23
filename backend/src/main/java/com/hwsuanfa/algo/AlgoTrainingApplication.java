package com.hwsuanfa.algo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.TimeZone;

/**
 * 校队算法训练平台 —— 后端启动类
 */
@SpringBootApplication
@MapperScan("com.hwsuanfa.algo.mapper")
@EnableScheduling
public class AlgoTrainingApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(AlgoTrainingApplication.class, args);
    }
}
