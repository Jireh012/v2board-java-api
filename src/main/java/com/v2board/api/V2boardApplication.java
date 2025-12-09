package com.v2board.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.v2board.api.mapper")
public class V2boardApplication {
    public static void main(String[] args) {
        SpringApplication.run(V2boardApplication.class, args);
    }
}

