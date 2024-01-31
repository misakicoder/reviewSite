package com.liyu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.liyu.mapper")
@SpringBootApplication
public class reviewSiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(reviewSiteApplication.class, args);
    }

}
