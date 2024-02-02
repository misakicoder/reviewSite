package com.liyu.utils;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.liyu.dto.Result;
import com.liyu.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.liyu.utils.RedisConstants.*;

/**
 * ClassName:CacheClient
 * PackageName:com.liyu.utils
 * 题目：
 * Author:misaki
 * Create 2024/2/1 17:23
 * Version 1.0
 */
@Slf4j
@Component
public class CacheClient {

    @Autowired
    public StringRedisTemplate redisTemplate;

    public void  set(String key, Object value, Long time, TimeUnit unit){
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData<Object> redisData = new RedisData<>();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //查询防止缓存穿透工具类
    public <R,T> R queryWithPassThrough(String keyPrefix, T id,Long time, TimeUnit unit,
                                        Class<R> returnType, Function<T,R> dbFallback){
        String key = keyPrefix + id;
        String json = redisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json,returnType);
        }
        if(json != null){
            return null;
        }
        R r =  dbFallback.apply(id);
        if(r == null){
            this.set(key,"",time,unit);
            return null;
        }
        this.set(key,r,time,unit);
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //查询逻辑删除工具类
    public <R,T> R queryWithLogicalExpire(String keyPrefix,T id,Class<R> type
            ,Function<T,R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        String json = redisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(json)){
            return null;
        }
        RedisData<R> redisData = JSONUtil.toBean(json,
                new TypeReference<RedisData<R>>() {},
                false
        );
        R data = redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return data;
        }
        String lock = LOCK_SHOP_KEY+id;
        if(tryLock(lock)){
            json = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if(StrUtil.isNotBlank(json)){
                redisData = JSONUtil.toBean(json,
                        new TypeReference<RedisData<R>>() {},
                        false
                );
                data = redisData.getData();
                return data;
            }
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lock);
                }
            });
        }
        return data;
    }
    private boolean tryLock(String lockName){
        Boolean getOrNot = redisTemplate.opsForValue().setIfAbsent(lockName, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(getOrNot);
    }

    private void unLock(String lockName){
        redisTemplate.delete(lockName);
    }
}
