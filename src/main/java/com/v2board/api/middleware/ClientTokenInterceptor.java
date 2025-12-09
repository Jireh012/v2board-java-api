package com.v2board.api.middleware;

import com.v2board.api.model.User;
import com.v2board.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ClientTokenInterceptor implements HandlerInterceptor {
    
    @Autowired
    private UserService userService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) throws Exception {
        String token = request.getParameter("token");
        if (token == null || token.isEmpty()) {
            response.setStatus(403);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("token is null");
            return false;
        }
        
        User user = userService.findByToken(token);
        if (user == null) {
            response.setStatus(403);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("token is error");
            return false;
        }
        
        // 将用户信息存储到 request 属性中
        request.setAttribute("user", user);
        return true;
    }
}

