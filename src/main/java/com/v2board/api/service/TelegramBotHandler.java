package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.TicketMapper;
import com.v2board.api.mapper.TicketMessageMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Ticket;
import com.v2board.api.model.TicketMessage;
import com.v2board.api.model.User;
import com.v2board.api.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Telegram webhook update 处理 — 对齐 PHP Guest\\TelegramController + Commands。
 */
@Service
public class TelegramBotHandler {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotHandler.class);
    private static final Pattern TICKET_ID_IN_REPLY = Pattern.compile("#(\\d+)");
    private static final Pattern TICKET_ID_FALLBACK = Pattern.compile("[#](.*)");

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private TicketMessageMapper ticketMessageMapper;

    @Autowired
    private ConfigService configService;

    @SuppressWarnings("unchecked")
    public void handleUpdate(Map<String, Object> update) {
        if (update == null || update.isEmpty()) {
            return;
        }
        try {
            if (update.containsKey("chat_join_request")) {
                handleChatJoinRequest((Map<String, Object>) update.get("chat_join_request"));
                return;
            }
            FormattedMessage msg = formatMessage(update);
            if (msg == null) {
                return;
            }
            dispatch(msg);
        } catch (Exception e) {
            logger.error("Telegram update handling failed", e);
        }
    }

    private void dispatch(FormattedMessage msg) {
        String command = msg.command;
        if (command != null && command.contains("@")) {
            String[] parts = command.split("@", 2);
            if (parts.length == 2) {
                String botName = telegramService.getMeUsername();
                if (botName != null && botName.equalsIgnoreCase(parts[1])) {
                    command = parts[0];
                    msg.command = command;
                }
            }
        }

        try {
            if ("reply_message".equals(msg.messageType)) {
                handleReplyTicket(msg);
                return;
            }
            if (!StringUtils.hasText(command)) {
                return;
            }
            switch (command.toLowerCase()) {
                case "/bind" -> handleBind(msg);
                case "/unbind" -> handleUnbind(msg);
                case "/traffic" -> handleTraffic(msg);
                case "/getlatesturl" -> handleGetLatestUrl(msg);
                default -> {
                    // ignore unknown
                }
            }
        } catch (Exception e) {
            String err = e.getMessage() != null ? e.getMessage() : "处理失败";
            if (msg.chatId != null) {
                telegramService.sendMessage(msg.chatId, err);
            }
        }
    }

    private void handleBind(FormattedMessage msg) {
        if (!msg.isPrivate) {
            return;
        }
        if (msg.args == null || msg.args.isEmpty()) {
            throw new IllegalStateException("参数有误，请携带订阅地址发送");
        }
        String token = extractTokenFromSubscribeUrl(msg.args.get(0));
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("订阅地址无效");
        }
        User user = userService.findByToken(token);
        if (user == null) {
            throw new IllegalStateException("用户不存在");
        }
        if (user.getTelegramId() != null) {
            throw new IllegalStateException("该账号已经绑定了Telegram账号");
        }
        if (findByTelegramId(msg.chatId) != null) {
            throw new IllegalStateException("该Telegram已经绑定了其他账号");
        }
        user.setTelegramId(msg.chatId);
        if (userMapper.updateById(user) <= 0) {
            throw new IllegalStateException("设置失败");
        }
        telegramService.sendMessage(msg.chatId, "绑定成功");
    }

    private void handleUnbind(FormattedMessage msg) {
        if (!msg.isPrivate) {
            return;
        }
        User user = findByTelegramId(msg.chatId);
        if (user == null) {
            telegramService.sendMessage(msg.chatId, "没有查询到您的用户信息，请先绑定账号");
            return;
        }
        user.setTelegramId(null);
        // MyBatis-Plus 默认忽略 null：用 wrapper 清空
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.eq(User::getId, user.getId()).set(User::getTelegramId, null);
        if (userMapper.update(null, uw) <= 0) {
            throw new IllegalStateException("解绑失败");
        }
        telegramService.sendMessage(msg.chatId, "解绑成功");
    }

    private void handleTraffic(FormattedMessage msg) {
        if (!msg.isPrivate) {
            return;
        }
        User user = findByTelegramId(msg.chatId);
        if (user == null) {
            telegramService.sendMessage(msg.chatId, "没有查询到您的用户信息，请先绑定账号");
            return;
        }
        long transferEnable = user.getTransferEnable() != null ? user.getTransferEnable() : 0L;
        long up = user.getU() != null ? user.getU() : 0L;
        long down = user.getD() != null ? user.getD() : 0L;
        long remaining = Math.max(0L, transferEnable - (up + down));
        String text = "🚥流量查询\n———————————————\n计划流量：`" + Helper.trafficConvert(transferEnable)
                + "`\n已用上行：`" + Helper.trafficConvert(up)
                + "`\n已用下行：`" + Helper.trafficConvert(down)
                + "`\n剩余流量：`" + Helper.trafficConvert(remaining) + "`";
        telegramService.sendMessage(msg.chatId, text);
    }

    private void handleGetLatestUrl(FormattedMessage msg) {
        // PHP GetLatestUrl 无 is_private 限制
        String text = String.format("%s的最新网址是：%s",
                configService.getAppName(),
                configService.getAppUrl());
        telegramService.sendMessage(msg.chatId, text);
    }

    private void handleReplyTicket(FormattedMessage msg) {
        if (!msg.isPrivate) {
            return;
        }
        if (!StringUtils.hasText(msg.replyText) || !StringUtils.hasText(msg.text)) {
            return;
        }
        Long ticketId = extractTicketId(msg.replyText);
        if (ticketId == null) {
            return;
        }
        User user = findByTelegramId(msg.chatId);
        if (user == null) {
            throw new IllegalStateException("用户不存在");
        }
        boolean admin = user.getIsAdmin() != null && user.getIsAdmin() == 1;
        boolean staff = user.getIsStaff() != null && user.getIsStaff() == 1;
        if (!admin && !staff) {
            return;
        }
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new IllegalStateException("工单不存在");
        }
        long now = System.currentTimeMillis() / 1000;
        TicketMessage tm = new TicketMessage();
        tm.setTicketId(ticket.getId());
        tm.setUserId(user.getId());
        tm.setMessage(msg.text);
        tm.setCreatedAt(now);
        tm.setUpdatedAt(now);
        if (ticketMessageMapper.insert(tm) <= 0) {
            throw new IllegalStateException("工单回复失败");
        }
        ticket.setStatus(0);
        ticket.setReplyStatus(1);
        ticket.setUpdatedAt(now);
        ticketMapper.updateById(ticket);

        telegramService.sendMessage(msg.chatId, "#`" + ticketId + "` 的工单已回复成功");
        telegramService.sendMessageWithAdmin(
                "#`" + ticketId + "` 的工单已由 " + user.getEmail() + " 进行回复", true);
        notifyTicketOwner(ticket, msg.text);
    }

    private void notifyTicketOwner(Ticket ticket, String message) {
        if (ticket.getUserId() == null) {
            return;
        }
        User owner = userMapper.selectById(ticket.getUserId());
        if (owner == null || owner.getTelegramId() == null) {
            return;
        }
        String text = "您的工单 #" + ticket.getId() + " 有新回复\n主题：`"
                + (ticket.getSubject() != null ? ticket.getSubject() : "")
                + "`\n内容：`" + message + "`";
        telegramService.sendMessage(owner.getTelegramId(), text);
    }

    @SuppressWarnings("unchecked")
    private void handleChatJoinRequest(Map<String, Object> join) {
        if (join == null) {
            return;
        }
        Map<String, Object> from = (Map<String, Object>) join.get("from");
        Map<String, Object> chat = (Map<String, Object>) join.get("chat");
        if (from == null || chat == null || from.get("id") == null || chat.get("id") == null) {
            return;
        }
        long fromId = ((Number) from.get("id")).longValue();
        long chatId = ((Number) chat.get("id")).longValue();
        User user = findByTelegramId(fromId);
        if (user == null || !userService.isAvailable(user)) {
            telegramService.declineChatJoinRequest(chatId, fromId);
            return;
        }
        telegramService.approveChatJoinRequest(chatId, fromId);
    }

    @SuppressWarnings("unchecked")
    static FormattedMessage formatMessage(Map<String, Object> data) {
        Object messageObj = data.get("message");
        if (!(messageObj instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> message = (Map<String, Object>) messageObj;
        Object textObj = message.get("text");
        if (textObj == null) {
            return null;
        }
        String fullText = String.valueOf(textObj);
        String[] parts = fullText.split("\\s+");
        FormattedMessage obj = new FormattedMessage();
        obj.command = parts.length > 0 ? parts[0] : "";
        obj.args = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            if (StringUtils.hasText(parts[i])) {
                obj.args.add(parts[i]);
            }
        }
        Map<String, Object> chat = (Map<String, Object>) message.get("chat");
        if (chat != null && chat.get("id") != null) {
            obj.chatId = ((Number) chat.get("id")).longValue();
            obj.isPrivate = "private".equals(String.valueOf(chat.get("type")));
        }
        obj.messageType = "message";
        obj.text = fullText;
        Object replyObj = message.get("reply_to_message");
        if (replyObj instanceof Map<?, ?> replyMap && replyMap.get("text") != null) {
            obj.messageType = "reply_message";
            obj.replyText = String.valueOf(replyMap.get("text"));
        }
        return obj;
    }

    /** 从订阅 URL 的 query `token=` 提取（支持相对/绝对地址）。 */
    static String extractTokenFromSubscribeUrl(String subscribeUrl) {
        if (!StringUtils.hasText(subscribeUrl)) {
            return null;
        }
        String raw = subscribeUrl.trim();
        int q = raw.indexOf('?');
        if (q < 0) {
            return null;
        }
        String query = raw.substring(q + 1);
        int hash = query.indexOf('#');
        if (hash >= 0) {
            query = query.substring(0, hash);
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq);
            if (!"token".equals(key)) {
                continue;
            }
            String value = pair.substring(eq + 1);
            try {
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return value;
            }
        }
        return null;
    }

    static Long extractTicketId(String replyText) {
        if (!StringUtils.hasText(replyText)) {
            return null;
        }
        Matcher m = TICKET_ID_IN_REPLY.matcher(replyText);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher m2 = TICKET_ID_FALLBACK.matcher(replyText);
        if (m2.find()) {
            String captured = m2.group(1);
            Matcher digits = Pattern.compile("(\\d+)").matcher(captured);
            if (digits.find()) {
                try {
                    return Long.parseLong(digits.group(1));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private User findByTelegramId(Long telegramId) {
        if (telegramId == null) {
            return null;
        }
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getTelegramId, telegramId).last("LIMIT 1"));
    }

    static class FormattedMessage {
        String command;
        List<String> args;
        Long chatId;
        boolean isPrivate;
        String messageType;
        String text;
        String replyText;
    }
}
