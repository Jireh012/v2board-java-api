package com.v2board.api.controller.passport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.InviteCodeMapper;
import com.v2board.api.model.InviteCode;
import com.v2board.api.service.ConfigService;
import com.v2board.api.service.PassportService;
import com.v2board.api.util.Sm4Util;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对齐 PHP V1\Passport\CommController
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

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${v2board.sm4-key:}")
    private String sm4Key;

    /**
     * 公开站点配置（仅非敏感字段），data 为 SM4-CBC 信封 {@code {iv,payload}}。
     */
    @GetMapping("/config")
    public ApiResponse<Map<String, String>> config() {
        if (!StringUtils.hasText(sm4Key)) {
            throw new BusinessException(500, "SM4 key not configured");
        }
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
            String json = objectMapper.writeValueAsString(plain);
            byte[] key = Sm4Util.parseKey(sm4Key);
            return ApiResponse.success(Sm4Util.encryptToEnvelope(json, key));
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(500, "SM4 key invalid: " + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(500, "加密公开配置失败");
        }
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
