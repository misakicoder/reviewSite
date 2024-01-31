package com.liyu.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.liyu.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.liyu.utils.RedisConstants.LOGIN_USER_KEY;
import static com.liyu.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * ClassName:refreshTokenIntercepetor
 * PackageName:com.liyu.utils
 * 题目：
 * Author:misaki
 * Create 2024/1/31 19:40
 * Version 1.0
 */
public class refreshTokenIntercepetor implements HandlerInterceptor {
    private StringRedisTemplate redisTemplate;

    public refreshTokenIntercepetor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //        HttpSession session = request.getSession();
        //获取token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;
        }

        //在redis中获取token中的用户
//         UserDTO user = (UserDTO) session.getAttribute("user");
        String key = LOGIN_USER_KEY+token;
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(key);
        if(userMap.isEmpty()){
            return true;
        }
        //将查询到的hash转化为对象
        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //刷新token
        if(user == null){
            return true;
        }
        UserHolder.saveUser(user);
        redisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
