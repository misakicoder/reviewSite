package com.liyu.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.json.JSONUtil;
import com.liyu.dto.Result;
import com.liyu.entity.ShopType;
import com.liyu.mapper.ShopTypeMapper;
import com.liyu.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ShopTypeMapper shopTypeMapper;

    @Override
    public Result queryTypeList() {
        //从redis中查询店铺类型列表
        Set<String> shopTypeSet = redisTemplate.opsForZSet().range("cache:shop-type", 0, -1);
        if(shopTypeSet != null && !shopTypeSet.isEmpty()){
            ArrayList<ShopType> shopTypes = new ArrayList<>();
            for(String s :shopTypeSet){
                log.debug("s:" + s);
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);
                shopTypes.add(shopType);
            }
            return Result.ok(shopTypes);
        }
        //如果查不到就查数据库
        List<ShopType> shopTypes = shopTypeMapper.selectList(null);
        if(shopTypes == null){
            return  Result.fail("查不到数据");
        }
        for(ShopType s : shopTypes){
            String shopTypeJson = JSONUtil.toJsonStr(s);
            redisTemplate.opsForZSet().add("cache:shop-type",shopTypeJson,s.getSort());
        }
        return Result.ok(shopTypes);
    }
}
