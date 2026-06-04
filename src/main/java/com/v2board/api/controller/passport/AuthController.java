package com.v2board.api.controller.passport;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.model.User;
import com.v2board.api.service.AuthService;
import com.v2board.api.service.PassportService;
import com.v2board.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 对齐 PHP V1\Passport\AuthController
 */
@RestController
@RequestMapping("/api/v1/passport/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private PassportService passportService;

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

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) throws Exception {
        return ApiResponse.success(passportService.register(body, request));
    }

    @PostMapping("/forget")
    public ApiResponse<Boolean> forget(@RequestBody Map<String, Object> body) throws Exception {
        return ApiResponse.success(passportService.forget(body));
    }

    @PostMapping("/getQuickLoginUrl")
    public ApiResponse<String> getQuickLoginUrl(@RequestBody(required = false) Map<String, Object> body,
                                                HttpServletRequest request) throws Exception {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            auth = auth.substring(7);
        }
        if (!StringUtils.hasText(auth) && body != null) {
            auth = String.valueOf(body.get("auth_data"));
        }
        String redirect = body != null ? String.valueOf(body.getOrDefault("redirect", "dashboard")) : "dashboard";
        return ApiResponse.success(passportService.getQuickLoginUrl(auth, redirect));
    }

    /**
     * 仅保留 verify 快捷登录（不暴露 CVE-2026-39912 相关的 mail link token 登录）
     */
    @GetMapping("/token2Login")
    public ApiResponse<Map<String, Object>> token2Login(@RequestParam(value = "verify", required = false) String verify,
                                                       HttpServletRequest request) {
        if (!StringUtils.hasText(verify)) {
            throw new BusinessException(400, "Not supported");
        }
        return ApiResponse.success(passportService.verifyLogin(verify, request));
    }
}
