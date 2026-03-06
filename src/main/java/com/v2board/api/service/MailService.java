package com.v2board.api.service;

import com.v2board.api.mapper.MailLogMapper;
import com.v2board.api.model.MailLog;
import com.v2board.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;

/**
 * 邮件发送服务 — 对齐 PHP SendEmailJob + MailService
 */
@Service
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private MailLogMapper mailLogMapper;

    @Autowired
    private ConfigService configService;

    /**
     * 异步发送邮件 — 对齐 PHP SendEmailJob
     */
    @Async("emailExecutor")
    public void sendEmail(String email, String subject, String templateName, Map<String, Object> templateValues) {
        try {
            // 动态配置 SMTP（从 v2_system_config 读取）
            applyDynamicMailConfig();

            // 休眠 2 秒做限流，对齐 PHP 行为
            Thread.sleep(2000);

            // 渲染模板
            Context context = new Context();
            if (templateValues != null) {
                templateValues.forEach(context::setVariable);
            }
            String content = templateEngine.process("mail/" + templateName, context);

            // 发送邮件
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(content, true);

            // 设置发件人
            String fromAddress = getConfigValue("email_from_address");
            if (fromAddress != null && !fromAddress.isEmpty()) {
                helper.setFrom(fromAddress);
            }

            javaMailSender.send(message);

            // 记录日志
            logMail(email, subject, templateName, null);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Failed to send email to {}: {}", email, e.getMessage(), e);
            logMail(email, subject, templateName, e.getMessage());
        }
    }

    /**
     * 发送到期提醒邮件 — 对齐 PHP MailService::remindExpire()
     */
    public void remindExpire(User user) {
        if (user == null || user.getEmail() == null) return;
        if (user.getExpiredAt() == null) return;

        long now = System.currentTimeMillis() / 1000;
        long diff = user.getExpiredAt() - now;
        // 24 小时内到期
        if (diff > 0 && diff <= 86400) {
            sendEmail(user.getEmail(), "订阅即将到期提醒", "remindExpire",
                    Map.of("email", user.getEmail(),
                            "expiredAt", user.getExpiredAt()));
        }
    }

    /**
     * 发送流量提醒邮件 — 对齐 PHP MailService::remindTraffic()
     */
    public void remindTraffic(User user) {
        if (user == null || user.getEmail() == null) return;
        if (user.getTransferEnable() == null || user.getTransferEnable() <= 0) return;

        long used = (user.getU() != null ? user.getU() : 0L) + (user.getD() != null ? user.getD() : 0L);
        double ratio = (double) used / user.getTransferEnable();
        // 使用超过 95%
        if (ratio >= 0.95) {
            sendEmail(user.getEmail(), "流量使用提醒", "remindTraffic",
                    Map.of("email", user.getEmail(),
                            "used", used,
                            "total", user.getTransferEnable()));
        }
    }

    /**
     * 从 ConfigService 动态更新 JavaMailSender 配置
     */
    private void applyDynamicMailConfig() {
        if (!(javaMailSender instanceof JavaMailSenderImpl sender)) {
            return;
        }
        String host = getConfigValue("email_host");
        String port = getConfigValue("email_port");
        String username = getConfigValue("email_username");
        String password = getConfigValue("email_password");
        String encryption = getConfigValue("email_encryption");

        if (host != null && !host.isEmpty()) {
            sender.setHost(host);
        }
        if (port != null && !port.isEmpty()) {
            try {
                sender.setPort(Integer.parseInt(port));
            } catch (NumberFormatException ignore) {
            }
        }
        if (username != null && !username.isEmpty()) {
            sender.setUsername(username);
        }
        if (password != null && !password.isEmpty()) {
            sender.setPassword(password);
        }
        if ("ssl".equalsIgnoreCase(encryption)) {
            Properties props = sender.getJavaMailProperties();
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.auth", "true");
        } else if ("tls".equalsIgnoreCase(encryption)) {
            Properties props = sender.getJavaMailProperties();
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.auth", "true");
        }
    }

    private String getConfigValue(String key) {
        try {
            Map<String, Object> config = configService.getFullConfig();
            Object val = config.get(key);
            return val != null ? String.valueOf(val) : null;
        } catch (Exception e) {
            logger.error("Failed to load config for key={}", key, e);
            return null;
        }
    }

    private void logMail(String email, String subject, String templateName, String error) {
        try {
            MailLog log = new MailLog();
            log.setEmail(email);
            log.setSubject(subject);
            log.setTemplateName(templateName);
            log.setError(error);
            long now = System.currentTimeMillis() / 1000;
            log.setCreatedAt(now);
            log.setUpdatedAt(now);
            mailLogMapper.insert(log);
        } catch (Exception e) {
            logger.error("Failed to log mail record", e);
        }
    }
}
