package com.v2board.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * SMTP 凭据由管理端系统配置（email.*）在发送时动态注入，不在 yml / 环境变量中维护。
 */
@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender() {
        return new JavaMailSenderImpl();
    }
}
