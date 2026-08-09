package com.v2board.api.queue.handler;

import com.v2board.api.queue.JobHandler;
import com.v2board.api.queue.JobPayload;
import com.v2board.api.queue.JobQueues;
import com.v2board.api.service.TelegramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SendTelegramJobHandler implements JobHandler {

    @Autowired
    private TelegramService telegramService;

    @Override
    public String type() {
        return JobQueues.TYPE_SEND_TELEGRAM;
    }

    @Override
    public void handle(JobPayload payload) {
        long chatId = payload.getData().get("chat_id") instanceof Number n ? n.longValue() : 0L;
        String text = String.valueOf(payload.getData().getOrDefault("text", ""));
        telegramService.sendMessage(chatId, text);
    }
}
