package com.v2board.api.controller.passport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.mapper.InviteCodeMapper;
import com.v2board.api.model.InviteCode;
import com.v2board.api.service.ConfigService;
import com.v2board.api.service.PassportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对齐 PHP V1\Passport\CommController。
 * 公开配置 {@link #config()} 由 {@code PublicConfigRouteRegistrar} 挂到固定路径 {@code /config}；
 * 整包响应由 {@code PanelSm4Filter} 外层 SM4 加密（data 内为明文公开字段，避免双重 SM4）。
 */
@RestController
@RequestMapping("/api/v1/passport/comm")
public class CommController {

    @Autowired
    private PassportService passportService;

    @Autowired
    private InviteCodeMapper inviteCodeMapper;

    @Autowired
    private ConfigService configService;

    /**
     * 公开站点配置（仅非敏感字段）。返回普通 ApiResponse；外层信封由 PanelSm4Filter 负责。
     */
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> plain = new LinkedHashMap<>();
        plain.put("app_name", configService.getAppName());
        plain.put("stop_register", configService.getStopRegister());
        plain.put("invite_force", configService.getInviteForce());
        plain.put("email_verify", configService.getEmailVerify());
        plain.put("safe_mode_enable", configService.getSafeModeEnable());
        plain.put("secure_path", configService.getSecurePath());
        plain.put("recaptcha_enable", configService.getRecaptchaEnable());
        plain.put("recaptcha_site_key", configService.getRecaptchaSiteKey());
        plain.put("frontend_theme_sidebar", configService.getFrontendThemeSidebar());
        plain.put("frontend_theme_header", configService.getFrontendThemeHeader());
        plain.put("frontend_theme_color", configService.getFrontendThemeColor());
        plain.put("frontend_background_url", configService.getFrontendBackgroundUrl());
        plain.put("telegram_discuss_link", configService.getTelegramDiscussLink());
        try {
            Map<String, String> paths = configService.ensureClientApiPaths();
            plain.put("passport_api_prefix", paths.get("passport_api_prefix"));
            plain.put("user_api_prefix", paths.get("user_api_prefix"));
            plain.put("admin_api_prefix", paths.get("admin_api_prefix"));
            plain.put("public_config_path", ConfigService.FIXED_PUBLIC_CONFIG_PATH);
        } catch (Exception e) {
            plain.put("passport_api_prefix", configService.getPassportApiPrefix());
            plain.put("user_api_prefix", configService.getUserApiPrefix());
            plain.put("admin_api_prefix", configService.getAdminApiPrefix());
            plain.put("public_config_path", ConfigService.FIXED_PUBLIC_CONFIG_PATH);
        }
        return ApiResponse.success(plain);
    }

    @PostMapping("/sendEmailVerify")
    public ApiResponse<Boolean> sendEmailVerify(@RequestBody Map<String, Object> body,
                                                HttpServletRequest request) throws Exception {
        return ApiResponse.success(passportService.sendEmailVerify(body, request.getRemoteAddr()));
    }

    @PostMapping("/pv")
    public ApiResponse<Boolean> pv(@RequestBody Map<String, Object> body) {
        Object codeObj = body.get("invite_code");
        if (codeObj != null) {
            InviteCode invite = inviteCodeMapper.selectOne(
                    new LambdaQueryWrapper<InviteCode>().eq(InviteCode::getCode, String.valueOf(codeObj)));
            if (invite != null) {
                invite.setPv((invite.getPv() != null ? invite.getPv() : 0) + 1);
                inviteCodeMapper.updateById(invite);
            }
        }
        return ApiResponse.success(true);
    }
}
