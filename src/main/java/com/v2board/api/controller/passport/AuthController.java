package com.v2board.api.controller.passport;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 对齐 PHP V1\\Passport\\AuthController 中的登录行为：
 * POST /api/v1/passport/auth/login
 */
@RestController
@RequestMapping("/api/v1/passport/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(HttpServletRequest request,
                                                  @RequestParam("email") String email,
                                                  @RequestParam("password") String password) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new BusinessException(422, "邮箱和密码不能为空");
        }
        User user = userService.findByEmail(email);
        if (user == null) {
            throw new BusinessException(500, "Incorrect email or password");
        }
        if (!userService.verifyPassword(user, password)) {
            throw new BusinessException(500, "Incorrect email or password");
        }
        if (user.getBanned() != null && user.getBanned() == 1) {
            throw new BusinessException(500, "Your account has been suspended");
        }
        Map<String, Object> data = authService.generateAuthData(user, request);
        if (data == null) {
            throw new BusinessException(500, "Login failed");
        }
        return ApiResponse.success(data);
    }
}

