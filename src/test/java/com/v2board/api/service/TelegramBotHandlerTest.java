package com.v2board.api.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramBotHandlerTest {

    @Test
    void extractTokenFromSubscribeUrl_supportsAbsoluteAndRelative() {
        assertEquals("abc123", TelegramBotHandler.extractTokenFromSubscribeUrl(
                "https://example.com/api/v1/client/subscribe?token=abc123"));
        assertEquals("tok+", TelegramBotHandler.extractTokenFromSubscribeUrl(
                "/s?token=tok%2B"));
        assertEquals("x", TelegramBotHandler.extractTokenFromSubscribeUrl("?token=x&flag=1"));
        assertNull(TelegramBotHandler.extractTokenFromSubscribeUrl("https://example.com/no-query"));
        assertNull(TelegramBotHandler.extractTokenFromSubscribeUrl(null));
    }

    @Test
    void extractTicketId_fromReplyText() {
        assertEquals(42L, TelegramBotHandler.extractTicketId("📮工单提醒 #42\n主题：测试"));
        assertEquals(7L, TelegramBotHandler.extractTicketId("#`7` 的工单已回复成功"));
        assertNull(TelegramBotHandler.extractTicketId("没有工单号"));
    }

    @Test
    void formatMessage_parsesPrivateCommandAndReply() {
        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("id", 1001L);
        chat.put("type", "private");

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("text", "/bind https://x.test/sub?token=t1");
        message.put("chat", chat);
        message.put("message_id", 9);

        Map<String, Object> update = new HashMap<>();
        update.put("message", message);

        TelegramBotHandler.FormattedMessage msg = TelegramBotHandler.formatMessage(update);
        assertNotNull(msg);
        assertEquals("/bind", msg.command);
        assertEquals(1, msg.args.size());
        assertTrue(msg.args.get(0).contains("token=t1"));
        assertEquals(1001L, msg.chatId);
        assertTrue(msg.isPrivate);
        assertEquals("message", msg.messageType);

        Map<String, Object> replyTo = new LinkedHashMap<>();
        replyTo.put("text", "📮工单提醒 #9\n主题：hi");
        message.put("reply_to_message", replyTo);
        message.put("text", "已处理，请查收");
        msg = TelegramBotHandler.formatMessage(update);
        assertNotNull(msg);
        assertEquals("reply_message", msg.messageType);
        assertTrue(msg.replyText.contains("#9"));
        assertFalse(msg.replyText.isEmpty());
    }

    @Test
    void md5Hex_matchesExpected() {
        // echo -n 'test-token' | md5
        assertEquals("90567ff86d28e6fad19b5dece8296c59", TelegramService.md5Hex("test-token"));
    }
}
