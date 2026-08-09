package com.v2board.api.queue.handler;

import com.v2board.api.queue.JobHandler;
import com.v2board.api.queue.JobPayload;
import com.v2board.api.queue.JobQueues;
import com.v2board.api.service.MailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SendEmailJobHandler implements JobHandler {

    @Autowired
    private MailService mailService;

    @Override
    public String type() {
        return JobQueues.TYPE_SEND_EMAIL;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(JobPayload payload) throws Exception {
        String email = String.valueOf(payload.getData().getOrDefault("email", ""));
        String subject = String.valueOf(payload.getData().getOrDefault("subject", ""));
        String template = String.valueOf(payload.getData().getOrDefault("template", ""));
        Map<String, Object> values = new HashMap<>();
        Object raw = payload.getData().get("values");
        if (raw instanceof Map<?, ?> m) {
            m.forEach((k, v) -> values.put(String.valueOf(k), v));
        }
        mailService.processEmail(email, subject, template, values);
    }
}
