package com.v2board.api.controller.guest;

import com.v2board.api.service.TelegramBotHandler;
import com.v2board.api.service.TelegramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Guest Telegram webhook — 对齐 PHP Guest\\TelegramController。
 * access_token 必须等于 MD5(bot token)。
 */
@RestController
@RequestMapping("/api/v1/guest/telegram")
public class GuestTelegramController {

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private TelegramBotHandler telegramBotHandler;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestParam(value = "access_token", required = false) String accessToken,
            @RequestBody(required = false) Map<String, Object> update) {
        String botToken = telegramService.getBotToken();
        if (!StringUtils.hasText(botToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String expected = TelegramService.md5Hex(botToken);
        if (!StringUtils.hasText(accessToken) || !expected.equalsIgnoreCase(accessToken.trim())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // 尽快 200；处理异常在 handler 内吞掉并尽量回消息
        try {
            telegramBotHandler.handleUpdate(update);
        } catch (Exception ignored) {
            // webhook 必须快速 ACK，避免 Telegram 重试风暴
        }
        return ResponseEntity.ok().build();
    }
}
