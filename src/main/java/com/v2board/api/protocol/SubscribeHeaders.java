package com.v2board.api.protocol;

import com.v2board.api.model.User;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 订阅响应头（对齐 PHP 各 Protocol handler 的 header 输出）
 */
public final class SubscribeHeaders {

    private SubscribeHeaders() {
    }

    public static void applyUserInfo(HttpServletResponse response, User user) {
        if (response == null || user == null) {
            return;
        }
        long upload = user.getU() != null ? user.getU() : 0;
        long download = user.getD() != null ? user.getD() : 0;
        long total = user.getTransferEnable() != null ? user.getTransferEnable() : 0;
        long expire = user.getExpiredAt() != null ? user.getExpiredAt() : 0;
        response.setHeader("subscription-userinfo",
                String.format("upload=%d; download=%d; total=%d; expire=%d", upload, download, total, expire));
    }

    public static void applyClashMeta(HttpServletResponse response, User user, String appName) {
        applyUserInfo(response, user);
        response.setHeader("profile-update-interval", "24");
        if (StringUtils.hasText(appName)) {
            response.setHeader("content-disposition",
                    "attachment;filename*=UTF-8''" + java.net.URLEncoder.encode(appName, StandardCharsets.UTF_8));
        }
    }

    public static void applySingbox(HttpServletResponse response, User user, String appName) {
        applyUserInfo(response, user);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("profile-update-interval", "24");
        if (StringUtils.hasText(appName)) {
            response.setHeader("Profile-Title", "base64:" + Base64.getEncoder().encodeToString(appName.getBytes(StandardCharsets.UTF_8)));
            response.setHeader("Content-Disposition", "attachment; filename=\"" + appName + "\"");
        }
    }

    public static void applyV2rayTun(HttpServletResponse response, User user, String appName) {
        applyUserInfo(response, user);
        if (response == null) {
            return;
        }
        response.setHeader("profile-update-interval", "24");
        if (StringUtils.hasText(appName)) {
            response.setHeader("profile-title", "base64:" + Base64.getEncoder().encodeToString(appName.getBytes(StandardCharsets.UTF_8)));
            response.setHeader("Content-Disposition", "attachment; filename=\"" + appName + "\"");
        }
    }

    public static void applyLoon(HttpServletResponse response, User user) {
        applyUserInfo(response, user);
        // PHP 使用 Subscription-Userinfo（大写 S），同时设置标准小写以兼容
        long upload = user.getU() != null ? user.getU() : 0;
        long download = user.getD() != null ? user.getD() : 0;
        long total = user.getTransferEnable() != null ? user.getTransferEnable() : 0;
        long expire = user.getExpiredAt() != null ? user.getExpiredAt() : 0;
        String info = String.format("upload=%d; download=%d; total=%d; expire=%d", upload, download, total, expire);
        response.setHeader("Subscription-Userinfo", info);
    }
}
