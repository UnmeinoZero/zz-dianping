package com.zzdp.config;

import com.zzdp.utils.LoginInterceptor;
import com.zzdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author 千叶零
 * @version 1.0
 * create 2023-05-25  10:34:06
 */
@Configuration
public class MVCConfig implements WebMvcConfigurer {

    //注入定义的拦截器
    @Autowired
    LoginInterceptor loginInterceptor;

    @Autowired
    RefreshTokenInterceptor refreshTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(refreshTokenInterceptor);  //拦截所有， 前面的拦截器先执行

        registry.addInterceptor(loginInterceptor)  //配置拦截器
                .excludePathPatterns( //排除拦截路径
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/bolg/hot",
                        "/user/code",
                        "/user/login"
                );
    }
}
