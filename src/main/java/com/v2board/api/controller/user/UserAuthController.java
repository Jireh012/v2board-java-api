package com.v2board.api.controller.user;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.model.User;
import com.v2board.api.service.AuthService;
import com.v2board.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
public class UserAuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    /**
     * 对齐 PHP 版 /api/v1/user/checkLogin
     * 这里通过 AuthService 解密前端携带的 JWT（与 PHP 端生成方式兼容）
     */
    @PostMapping("/checkLogin")
    public ApiResponse<Map<String, Object>> checkLogin(HttpServletRequest request) {
        String jwt = resolveJwt(request);
        if (!StringUtils.hasText(jwt)) {
            return ApiResponse.success(Map.of("is_login", false));
        }
        Map<String, Object> authData = authService.decryptAuthData(jwt);
        if (authData == null || authData.get("id") == null) {
            return ApiResponse.success(Map.of("is_login", false));
        }
        boolean isAdmin = Boolean.TRUE.equals(authData.get("is_admin"));
        return ApiResponse.success(Map.of(
                "is_login", true,
                "is_admin", isAdmin
        ));
    }

    /**
     * 简单实现一个基于邮箱+密码的登录接口，对齐 PHP Passport 行为的大致形态。
     * 注意：密码校验逻辑需与实际数据库字段和加密方式对齐，这里仅做占位。
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(String email, String password) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new BusinessException(422, "邮箱和密码不能为空");
        }
        User user = userService.findByEmail(email);
        if (user == null) {
            throw new BusinessException(500, "The user does not exist");
        }
        if (!userService.verifyPassword(user, password)) {
            throw new BusinessException(500, "The password is wrong");
        }
        // 这里为了兼容前端，可以复用 PHP 端的 JWT 生成逻辑；暂时只返回基础信息，JWT 生成由前端或后续补充。
        Map<String, Object> data = Map.of(
                "id", user.getId(),
                "email", user.getEmail()
        );
        return ApiResponse.success(data);
    }

    private String resolveJwt(HttpServletRequest request) {
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
}

