package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class LoginInterceptor implements HandlerInterceptor {

    public  boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //只负责拦截需要验证登录状态的请求
        UserDTO user = UserHolder.getUser();
        System.out.println("UserHolder.getUser()：" + user);
        if ( user == null) {
            System.out.println("UserHolder.getUser()为空");
            response.setStatus(401);
            return false;
        }

        return true;
    }


    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
