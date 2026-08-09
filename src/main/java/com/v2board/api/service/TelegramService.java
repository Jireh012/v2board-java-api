package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.User;
import com.v2board.api.queue.JobDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Telegram Bot 消息发送服务 — 对齐 PHP TelegramService
 */
@Service
public class TelegramService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramService.class);
    private static final String API_BASE = "https://api.telegram.org/bot";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ConfigService configService;

    @Autowired
    private JobDispatcher jobDispatcher;

    @Autowired
    private UserMapper userMapper;

    /** telegram.telegram_bot_enable 识别 Number / Boolean / String 的 0/1。 */
    public boolean isBotEnabled() {
        try {
            Map<String, Object> config = configService.getFullConfig();
            Object tgObj = config.get("telegram");
            if (tgObj instanceof Map<?, ?> tgMap) {
                return parseEnableFlag(tgMap.get("telegram_bot_enable"));
            }
        } catch (Exception e) {
            logger.error("Failed to load telegram_bot_enable", e);
        }
        return false;
    }

    /** Package-visible / test helper for enable flag parsing. */
    static boolean parseEnableFlag(Object enableObj) {
        if (enableObj == null) {
            return false;
        }
        if (enableObj instanceof Boolean b) {
            return b;
        }
        if (enableObj instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = String.valueOf(enableObj).trim();
        if (s.isEmpty() || "0".equals(s) || "false".equalsIgnoreCase(s)) {
            return false;
        }
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) {
            return true;
        }
        try {
            return Integer.parseInt(s) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public String getBotToken() {
        String token = configService.getStringFromGroup("telegram", "telegram_bot_token");
        return StringUtils.hasText(token) ? token : null;
    }

    public String getDiscussLink() {
        return configService.getTelegramDiscussLink();
    }

    /**
     * 向管理员发送 Telegram 通知 — 对齐 PHP sendMessageWithAdmin($message, $isStaff = false)
     */
    public void sendMessageWithAdmin(String text) {
        sendMessageWithAdmin(text, false);
    }

    /**
     * @param includeStaff true 时同时通知 is_staff=1 且已绑定 telegram_id 的用户
     */
    public void sendMessageWithAdmin(String text, boolean includeStaff) {
        if (!isBotEnabled()) {
            return;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (includeStaff) {
            wrapper.and(q -> q.eq(User::getIsAdmin, 1).or().eq(User::getIsStaff, 1));
        } else {
            wrapper.eq(User::getIsAdmin, 1);
        }
        wrapper.isNotNull(User::getTelegramId);
        List<User> admins = userMapper.selectList(wrapper);
        for (User admin : admins) {
            if (admin.getTelegramId() != null) {
                sendMessageAsync(admin.getTelegramId(), text);
            }
        }
    }

    /**
     * 同步发送 Telegram 消息（markdown；对齐 PHP 转义下划线）。
     */
    public void sendMessage(Long chatId, String text) {
        if (chatId == null || text == null) {
            return;
        }
        String token = getBotToken();
        if (!StringUtils.hasText(token)) {
            logger.warn("Telegram bot token not configured");
            return;
        }
        String bodyText = text.replace("_", "\\_");
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", bodyText);
            body.put("parse_mode", "markdown");
            postApi(token, "sendMessage", body);
        } catch (Exception e) {
            logger.error("Failed to send Telegram message to chatId={}", chatId, e);
        }
    }

    public void sendMessageAsync(Long chatId, String text) {
        if (chatId == null) {
            return;
        }
        jobDispatcher.dispatchSendTelegram(chatId, text);
    }

    /**
     * 设置 Telegram Bot Webhook — 对齐 PHP setWebhook()；检查 Telegram ok 字段。
     */
    public void setWebhook(String botToken, String hookUrl) {
        if (!StringUtils.hasText(botToken)) {
            throw new BusinessException(500, "Telegram Bot Token 未配置");
        }
        if (!StringUtils.hasText(hookUrl)) {
            throw new BusinessException(500, "Webhook URL 无效");
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("url", hookUrl);
            JsonNode resp = postApi(botToken, "setWebhook", body);
            if (resp == null || !resp.has("ok")) {
                throw new BusinessException(500, "请求失败");
            }
            if (!resp.get("ok").asBoolean()) {
                String desc = resp.has("description") ? resp.get("description").asText() : "unknown";
                throw new BusinessException(500, "来自TG的错误：" + desc);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "设置 Webhook 失败: " + e.getMessage());
        }
    }

    public String getMeUsername() {
        String token = getBotToken();
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            JsonNode resp = getApi(token, "getMe");
            if (resp != null && resp.path("ok").asBoolean(false)) {
                return resp.path("result").path("username").asText(null);
            }
        } catch (Exception e) {
            logger.warn("Telegram getMe failed: {}", e.getMessage());
        }
        return null;
    }

    public void approveChatJoinRequest(long chatId, long userId) {
        String token = getBotToken();
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("user_id", userId);
            postApi(token, "approveChatJoinRequest", body);
        } catch (Exception e) {
            logger.error("approveChatJoinRequest failed chatId={} userId={}", chatId, userId, e);
        }
    }

    public void declineChatJoinRequest(long chatId, long userId) {
        String token = getBotToken();
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("user_id", userId);
            postApi(token, "declineChatJoinRequest", body);
        } catch (Exception e) {
            logger.error("declineChatJoinRequest failed chatId={} userId={}", chatId, userId, e);
        }
    }

    public static String md5Hex(String input) {
        if (input == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private JsonNode postApi(String botToken, String method, Map<String, Object> body) throws Exception {
        String url = API_BASE + botToken + "/" + method;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = objectMapper.writeValueAsString(body);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        String resp = restTemplate.postForObject(url, entity, String.class);
        if (resp == null || resp.isBlank()) {
            return null;
        }
        return objectMapper.readTree(resp);
    }

    private JsonNode getApi(String botToken, String method) throws Exception {
        String url = API_BASE + botToken + "/" + method;
        String resp = restTemplate.getForObject(url, String.class);
        if (resp == null || resp.isBlank()) {
            return null;
        }
        return objectMapper.readTree(resp);
    }
}
