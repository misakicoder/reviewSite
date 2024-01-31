package com.liyu.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.liyu.dto.UserDTO;
import com.liyu.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.Spliterator;
import java.util.concurrent.TimeUnit;

import static com.liyu.utils.RedisConstants.LOGIN_USER_KEY;
import static com.liyu.utils.RedisConstants.LOGIN_USER_TTL;
import static com.liyu.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * ClassName:loginIntercepetor
 * PackageName:com.liyu.utils
 * 题目：
 * Author:misaki
 * Create 2024/1/31 16:10
 * Version 1.0
 */
public class loginIntercepetor implements HandlerInterceptor {



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if(UserHolder.getUser() == null){
            response.setStatus(401);
            return false;
        }
         return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
