package com.v2board.api.controller.admin;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import com.v2board.api.service.MailService;
import com.v2board.api.service.TelegramService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端系统配置，对齐 PHP ConfigController。
 */
@RestController
@RequestMapping("/api/v1/admin/config")
public class AdminConfigController {

    @Autowired
    private ConfigService configService;

    @Autowired
    private MailService mailService;

    @Autowired
    private TelegramService telegramService;

    /**
     * GET /api/v1/admin/config/fetch?key=site
     * 不传 key 返回完整配置；传 key 返回 { key: { ... } }。
     */
    @GetMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch(@RequestParam(required = false) String key) {
        try {
            Map<String, Object> data = configService.fetch(key);
            return ApiResponse.success(data);
        } catch (Exception e) {
            throw new BusinessException(500, "获取配置失败");
        }
    }

    /**
     * POST /api/v1/admin/config/save
     * Body 与 fetch 返回结构一致（可只传要更新的分组，会与现有配置合并）。
     */
    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody Map<String, Object> body) {
        try {
            configService.save(body);
            return ApiResponse.success(true);
        } catch (Exception e) {
            throw new BusinessException(500, "保存配置失败");
        }
    }

    /**
     * POST /api/v1/admin/config/testSendMail — 测试邮件发送
     */
    @PostMapping("/testSendMail")
    public ApiResponse<Boolean> testSendMail(HttpServletRequest request) {
        Object attr = request.getAttribute("user");
        if (!(attr instanceof User user)) {
            throw new BusinessException(401, "Unauthenticated");
        }
        try {
            String appName = "V2Board";
            Map<String, Object> config = configService.getFullConfig();
            Object siteObj = config.get("site");
            if (siteObj instanceof Map<?, ?> siteMap) {
                Object nameObj = siteMap.get("app_name");
                if (nameObj != null) appName = String.valueOf(nameObj);
            }
            mailService.sendEmail(
                    user.getEmail(),
                    "This is v2board test email",
                    "notify",
                    Map.of(
                            "name", appName,
                            "content", "This is v2board test email",
                            "url", siteObj instanceof Map<?, ?> sm && sm.get("app_url") != null
                                    ? String.valueOf(sm.get("app_url")) : ""
                    )
            );
            return ApiResponse.success(true);
        } catch (Exception e) {
            throw new BusinessException(500, "发送测试邮件失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/v1/admin/config/setTelegramWebhook — 设置 Telegram Bot Webhook
     */
    @PostMapping("/setTelegramWebhook")
    public ApiResponse<Boolean> setTelegramWebhook(HttpServletRequest request) {
        try {
            Map<String, Object> config = configService.getFullConfig();
            Object telegramObj = config.get("telegram");
            String botToken = null;
            if (telegramObj instanceof Map<?, ?> tgMap) {
                Object tokenObj = tgMap.get("telegram_bot_token");
                if (tokenObj != null) botToken = String.valueOf(tokenObj);
            }
            // 允许从请求参数覆盖
            String paramToken = request.getParameter("telegram_bot_token");
            if (paramToken != null && !paramToken.isEmpty()) {
                botToken = paramToken;
            }
            if (botToken == null || botToken.isEmpty()) {
                throw new BusinessException(500, "Telegram Bot Token 未配置");
            }

            Object siteObj = config.get("site");
            String appUrl = "";
            if (siteObj instanceof Map<?, ?> siteMap) {
                Object urlObj = siteMap.get("app_url");
                if (urlObj != null) appUrl = String.valueOf(urlObj);
            }

            String hookUrl = appUrl + "/api/v1/guest/telegram/webhook?access_token="
                    + md5(botToken);
            telegramService.setWebhook(botToken, hookUrl);
            return ApiResponse.success(true);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "设置 Webhook 失败: " + e.getMessage());
        }
    }

    private String md5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
