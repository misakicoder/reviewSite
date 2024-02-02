package com.liyu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liyu.dto.Result;
import com.liyu.entity.Shop;
import com.liyu.mapper.ShopMapper;
import com.liyu.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liyu.utils.CacheClient;
import com.liyu.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.crypto.Data;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.liyu.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ShopMapper shopMapper;

    private static  final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Autowired
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {

        //解决缓存穿透工具类
        /*cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,CACHE_SHOP_TTL,
                TimeUnit.MINUTES,Shop.class,shopMapper::selectById);
        */

        //从redis中查询缓存
        String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //判断是否存在
        //存在直接返回
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //解决缓存穿透
        //判断是否命中空值
        if(shopJson != null){
            //返回错误信息
            return Result.fail("店铺不存在");
        }
        //解决缓存击穿
        //获取互斥锁,每个热点key设置不同的锁
        Shop shop = null;
        try {
            if (!tryLock(LOCK_SHOP_KEY+id)){
                Thread.sleep(50);
                return queryById(id);
            }
            //解决缓存击穿
            //检测缓存是否存在，如果存在无需重建，因为有其它线程之前获取锁已经进行了修改。
            shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if(StrUtil.isNotBlank(shopJson)){
                shop = JSONUtil.toBean(shopJson, Shop.class);
                return Result.ok(shop);
            }
            //获取不到休眠一段时间，继续获取
            //获取到了执行更新操作
            shop = shopMapper.selectById(id);
            //不存在查询数据库
            //id不存在返回错误,并在redis中存入空值
            if(shop == null){
                redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return Result.fail("店铺不存在");
            }
            //id存在，存入redis，返回信息
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(LOCK_SHOP_KEY+id);
        }
        //释放互斥锁
        return Result.ok(shop);
    }

    public Result queryByIdWithLogicExpire(Long id){
        //判断是否命中
        String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //不命中，因为是热点key，所以认为肯定在redis中，不在redis中就直接返回不存在
        if(StrUtil.isBlank(shopJson)){
            return Result.fail("店铺不存在");
        }
        //命中,反序列化
        RedisData<Shop> redisData = JSONUtil.toBean(shopJson,
                new TypeReference<RedisData<Shop>>() {},
                false);
        Shop shop = redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        //未过期，直接返回
        if(expireTime.isAfter(LocalDateTime.now())){
            return Result.ok(shop);
        }
        //已过期，判断是否获取锁
        //获取锁成功，开启独立线程，执行更新流程
        String lock = LOCK_SHOP_KEY+id;
        if(tryLock(lock)){
            shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if(StrUtil.isNotBlank(shopJson)){
                redisData = JSONUtil.toBean(shopJson,
                        new TypeReference<RedisData<Shop>>() {},
                        false);
                shop = redisData.getData();
                return Result.ok(shop);
            }
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lock);
                }
            });

        }
        //获取锁失败,返回
        return Result.ok(shop);
    }


    private boolean tryLock(String lockName){
        Boolean getOrNot = redisTemplate.opsForValue().setIfAbsent(lockName, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(getOrNot);
    }

    private void unLock(String lockName){
        redisTemplate.delete(lockName);
    }

    public void saveShop2Redis(Long id,Long expireSeconds){
        //查出店铺数据
        Shop shop = shopMapper.selectById(id);
        //封装逻辑过期时间
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }


    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        //1、更新数据库
        shopMapper.updateById(shop);
        //2、删除缓存
        if(shop.getId() == null){
            return Result.fail("id不存在");
        }
        redisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        //3、返回结果
        return Result.ok();
    }
}
