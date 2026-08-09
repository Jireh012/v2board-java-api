package com.v2board.api.middleware;

import com.v2board.api.model.User;
import com.v2board.api.service.AuthService;
import com.v2board.api.service.UserService;
import com.v2board.api.util.PanelSm4Support;
import com.v2board.api.util.Sm4Util;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class ClientAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Value("${v2board.sm4-key:}")
    private String sm4Key;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (PanelSm4Support.rejectClassicAuth(request)) {
            if (hasClassicAuth(request)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
        }
        String jwt = resolveJwt(request);
        if (!StringUtils.hasText(jwt)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        Map<String, Object> authData = authService.decryptAuthData(jwt);
        if (authData == null || authData.get("id") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        Long userId;
        try {
            Object idObj = authData.get("id");
            if (idObj instanceof Integer) {
                userId = ((Integer) idObj).longValue();
            } else if (idObj instanceof Long) {
                userId = (Long) idObj;
            } else {
                userId = Long.valueOf(idObj.toString());
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        User user = userService.findById(userId);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        request.setAttribute("user", user);
        request.setAttribute("authData", authData);
        return true;
    }

    private String resolveJwt(HttpServletRequest request) {
        if (PanelSm4Support.rejectClassicAuth(request) || PanelSm4Support.isPanelSm4(request)) {
            String xa = request.getHeader(PanelSm4Support.HEADER_X_A);
            if (StringUtils.hasText(xa) && StringUtils.hasText(sm4Key)) {
                try {
                    return Sm4Util.decryptFromCompact(xa.trim(), Sm4Util.parseKey(sm4Key));
                } catch (Exception ignored) {
                    return null;
                }
            }
            if (PanelSm4Support.rejectClassicAuth(request)) {
                return null;
            }
        }
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        String token = request.getParameter("auth_data");
        if (StringUtils.hasText(token)) {
            return token;
        }
        return null;
    }

    private static boolean hasClassicAuth(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader)) {
            return true;
        }
        return StringUtils.hasText(request.getParameter("auth_data"));
    }
}
