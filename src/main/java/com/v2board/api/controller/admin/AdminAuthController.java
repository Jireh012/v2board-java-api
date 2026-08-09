package com.v2board.api.controller.admin;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.model.User;
import com.v2board.api.service.AuthService;
import com.v2board.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    /**
     * 管理端登录。JSON body（加密区经 PanelSm4Filter 解密后到达此处）。
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(HttpServletRequest request,
                                                  @RequestBody Map<String, Object> body) {
        String email = body != null && body.get("email") != null ? String.valueOf(body.get("email")).trim() : "";
        String password = body != null && body.get("password") != null ? String.valueOf(body.get("password")) : "";
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new BusinessException(422, "邮箱和密码不能为空");
        }
        User user = userService.findByEmail(email);
        if (user == null) {
            throw new BusinessException(500, "用户不存在");
        }
        if (!userService.verifyPassword(user, password)) {
            throw new BusinessException(500, "密码错误");
        }
        Map<String, Object> data = authService.generateAuthData(user, request);
        if (data == null) {
            throw new BusinessException(500, "登录失败");
        }
        return ApiResponse.success(data);
    }
}
