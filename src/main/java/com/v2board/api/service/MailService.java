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
import org.springframework.util.StringUtils;
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
            sendEmailInternal(email, subject, templateName, templateValues, true);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Failed to send email to {}: {}", email, e.getMessage(), e);
            logMail(email, subject, templateName, e.getMessage());
        }
    }

    /**
     * 同步发送（管理端测试邮件）。失败抛出异常供接口返回。
     */
    public void sendEmailSync(String email, String subject, String templateName, Map<String, Object> templateValues)
            throws Exception {
        try {
            sendEmailInternal(email, subject, templateName, templateValues, false);
        } catch (Exception e) {
            logMail(email, subject, templateName, e.getMessage());
            throw e;
        }
    }

    private void sendEmailInternal(String email, String subject, String templateName,
                                   Map<String, Object> templateValues, boolean rateLimitSleep) throws Exception {
        applyDynamicMailConfig();

        if (rateLimitSleep) {
            Thread.sleep(2000);
        }

        Context context = new Context();
        if (templateValues != null) {
            templateValues.forEach(context::setVariable);
        }
        String content = templateEngine.process(resolveTemplatePath(templateName), context);

        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(email);
        helper.setSubject(subject);
        helper.setText(content, true);

        String fromAddress = emailCfg("email_from_address");
        if (StringUtils.hasText(fromAddress)) {
            helper.setFrom(fromAddress);
        }

        javaMailSender.send(message);
        logMail(email, subject, templateName, null);
    }

    /**
     * 发送到期提醒邮件 — 对齐 PHP MailService::remindExpire()
     */
    public void remindExpire(User user) {
        if (user == null || user.getEmail() == null) return;
        if (user.getExpiredAt() == null) return;

        long now = System.currentTimeMillis() / 1000;
        long diff = user.getExpiredAt() - now;
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
        if (ratio >= 0.95) {
            sendEmail(user.getEmail(), "流量使用提醒", "remindTraffic",
                    Map.of("email", user.getEmail(),
                            "used", used,
                            "total", user.getTransferEnable()));
        }
    }

    /**
     * 从 ConfigService 动态更新 JavaMailSender（读取 email.* 嵌套配置）。
     */
    void applyDynamicMailConfig() {
        if (!(javaMailSender instanceof JavaMailSenderImpl sender)) {
            return;
        }
        String host = emailCfg("email_host");
        String port = emailCfg("email_port");
        String username = emailCfg("email_username");
        String password = emailCfg("email_password");
        String encryption = emailCfg("email_encryption");

        if (StringUtils.hasText(host)) {
            sender.setHost(host);
        }
        if (StringUtils.hasText(port)) {
            try {
                sender.setPort(Integer.parseInt(port));
            } catch (NumberFormatException ignore) {
            }
        }
        if (StringUtils.hasText(username)) {
            sender.setUsername(username);
        }
        if (StringUtils.hasText(password)) {
            sender.setPassword(password);
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        if ("ssl".equalsIgnoreCase(encryption)) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
        } else if ("tls".equalsIgnoreCase(encryption)) {
            props.put("mail.smtp.ssl.enable", "false");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        } else {
            props.put("mail.smtp.ssl.enable", "false");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.starttls.required", "false");
        }
    }

    String resolveTemplatePath(String templateName) {
        String folder = emailCfg("email_template");
        if (!StringUtils.hasText(folder) || "default".equalsIgnoreCase(folder)) {
            return "mail/" + templateName;
        }
        return "mail/" + folder + "/" + templateName;
    }

    private String emailCfg(String key) {
        return configService.getStringFromGroup("email", key);
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
