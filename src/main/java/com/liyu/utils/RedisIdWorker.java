package com.liyu.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * ClassName:RedisIdWorker
 * PackageName:com.liyu.utils
 * 题目：
 * Author:misaki
 * Create 2024/2/2 10:52
 * Version 1.0
 */
@Component
public class RedisIdWorker {

    private static final long BEGIN_STAMP;
    private static final long COUNT_BITS = 32;

    @Autowired
    private StringRedisTemplate redisTemplate;

    static {
        LocalDateTime time = LocalDateTime.of(2022,1,1,0,0,0);
        BEGIN_STAMP = time.toEpochSecond(ZoneOffset.UTC);
    }

    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime nowTime = LocalDateTime.now();
        long nowTimeSecond = nowTime.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowTimeSecond - BEGIN_STAMP;
        //生成序列号
        String nowDay = nowTime.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + nowDay);
        //拼接返回
        return timeStamp << COUNT_BITS | count;
    }

}
