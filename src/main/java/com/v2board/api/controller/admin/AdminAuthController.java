package com.v2board.api.controller.admin;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.model.User;
import com.v2board.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAuthController {

    @Autowired
    private UserService userService;

    /**
     * 管理端登录占位实现，实际应结合 is_admin 字段与权限系统。
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(String email, String password) {
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
        return ApiResponse.success(Map.of(
                "id", user.getId(),
                "email", user.getEmail()
        ));
    }
}

