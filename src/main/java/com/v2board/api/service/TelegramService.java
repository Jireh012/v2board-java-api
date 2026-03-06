package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Telegram Bot 消息发送服务 — 对齐 PHP TelegramService
 */
@Service
public class TelegramService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private ConfigService configService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 向管理员发送 Telegram 通知 — 对齐 PHP sendMessageWithAdmin()
     * 查询所有 is_admin>=1 且有 telegram_id 的用户，逐个异步发送
     */
    public void sendMessageWithAdmin(String text) {
        Map<String, Object> config;
        try {
            config = configService.getFullConfig();
        } catch (Exception e) {
            logger.error("Failed to load config for Telegram", e);
            return;
        }
        Object tgObj = config.get("telegram");
        if (tgObj instanceof Map<?, ?> tgMap) {
            Object enableObj = tgMap.get("telegram_bot_enable");
            if (enableObj == null || "0".equals(String.valueOf(enableObj))) {
                return;
            }
        } else {
            return;
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(User::getIsAdmin, 1);
        wrapper.isNotNull(User::getTelegramId);
        List<User> admins = userMapper.selectList(wrapper);

        for (User admin : admins) {
            sendMessageAsync(admin.getTelegramId(), text);
        }
    }

    /**
     * 异步发送 Telegram 消息
     */
    @Async("telegramExecutor")
    public void sendMessageAsync(Long chatId, String text) {
        try {
            String token = getBotToken();
            if (token == null || token.isEmpty()) {
                logger.warn("Telegram bot token not configured");
                return;
            }
            String url = "https://api.telegram.org/bot" + token + "/sendMessage"
                    + "?chat_id=" + chatId
                    + "&text=" + java.net.URLEncoder.encode(text, "UTF-8")
                    + "&parse_mode=markdown";
            restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            logger.error("Failed to send Telegram message to chatId={}", chatId, e);
        }
    }

    private String getBotToken() {
        try {
            Map<String, Object> config = configService.getFullConfig();
            Object tgObj = config.get("telegram");
            if (tgObj instanceof Map<?, ?> tgMap) {
                Object tokenObj = tgMap.get("telegram_bot_token");
                return tokenObj != null ? String.valueOf(tokenObj) : null;
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to load config for Telegram bot token", e);
            return null;
        }
    }

    /**
     * 设置 Telegram Bot Webhook — 对齐 PHP setWebhook()
     */
    public void setWebhook(String botToken, String hookUrl) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/setWebhook"
                    + "?url=" + java.net.URLEncoder.encode(hookUrl, "UTF-8");
            restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set Telegram webhook: " + e.getMessage(), e);
        }
    }
}
