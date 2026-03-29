package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.从session里获取用户信息
        HttpSession session = request.getSession();
        Object user = session.getAttribute("user");
        // 2.判断用户是否存在
        if (user==null){
            // 3.如果不存在，拦截 返回401状态码
            response.setStatus(401);
            return false;
        }
        // 4.存在，保存用户信息到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        // 5.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
