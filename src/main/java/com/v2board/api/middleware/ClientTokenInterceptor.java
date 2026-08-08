package com.v2board.api.middleware;

import com.v2board.api.config.SubscribeRouteRegistrar;
import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import com.v2board.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Token gate for the current subscribe path from {@link ConfigService}.
 * Registered broadly; non-subscribe requests pass through immediately.
 */
@Component
public class ClientTokenInterceptor implements HandlerInterceptor {

    @Autowired
    private UserService userService;

    @Autowired
    private ConfigService configService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!isCurrentSubscribePath(request)) {
            return true;
        }

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

        request.setAttribute("user", user);
        return true;
    }

    private boolean isCurrentSubscribePath(HttpServletRequest request) {
        String expected = SubscribeRouteRegistrar.normalizePath(configService.getSubscribePath());
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
            if (uri.isEmpty()) {
                uri = "/";
            }
        }
        // strip ;jsessionid=...
        int semi = uri.indexOf(';');
        if (semi >= 0) {
            uri = uri.substring(0, semi);
        }
        return expected.equals(uri);
    }
}

