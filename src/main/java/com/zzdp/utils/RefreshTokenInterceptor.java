package com.zzdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.zzdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.zzdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.zzdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @author 千叶零
 * @version 1.0
 * create 2023-05-25  10:15:31
 * 定义拦截器
 */
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1. 获取请求头中的Token
        String token = request.getHeader("authorization");
        if (token == null) {
            return true;
        }

        //2. 获取Token中的用户信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        //3.判断用户是否存在
        if (userMap.isEmpty()){
            return true;
        }

        //4. 将map转为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //5 存在，保存用户信息到TheadLocal
        UserHolder.saveUser(userDTO);

        //6.刷新token有效期, 30分钟
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MILLISECONDS);

        //7.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
