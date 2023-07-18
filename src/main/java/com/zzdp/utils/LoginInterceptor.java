package com.zzdp.utils;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author 千叶零
 * @version 1.0
 * create 2023-05-25  10:15:31
 * 定义拦截器
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
       //1. 判断是否需要拦截 （ThreadLocal中是否用用户）
        if (UserHolder.getUser() == null){
            //不存在，拦截，设置状态码
            response.setStatus(401);
            //拦截
            return false;
        }

        //存在用户，放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
