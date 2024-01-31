package com.liyu.config;

import com.liyu.utils.loginIntercepetor;
import com.liyu.utils.refreshTokenIntercepetor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ClassName:MvcConfig
 * PackageName:com.liyu.config
 * 题目：
 * Author:misaki
 * Create 2024/1/31 16:20
 * Version 1.0
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {


    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new refreshTokenIntercepetor(redisTemplate));
        registry.addInterceptor(new loginIntercepetor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**"
                );
    }


}
