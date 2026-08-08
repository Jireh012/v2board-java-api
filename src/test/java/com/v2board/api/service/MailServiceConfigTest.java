package com.v2board.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailServiceConfigTest {

    private ConfigService configService;
    private JavaMailSenderImpl sender;
    private MailService mailService;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        sender = new JavaMailSenderImpl();
        sender.getJavaMailProperties().put("mail.smtp.ssl.enable", "true");

        mailService = new MailService();
        ReflectionTestUtils.setField(mailService, "configService", configService);
        ReflectionTestUtils.setField(mailService, "javaMailSender", sender);
    }

    @Test
    void applyDynamicMailConfig_readsNestedEmailGroup() {
        when(configService.getStringFromGroup("email", "email_host")).thenReturn("smtp.example.com");
        when(configService.getStringFromGroup("email", "email_port")).thenReturn("587");
        when(configService.getStringFromGroup("email", "email_username")).thenReturn("user");
        when(configService.getStringFromGroup("email", "email_password")).thenReturn("pass");
        when(configService.getStringFromGroup("email", "email_encryption")).thenReturn("tls");
        when(configService.getStringFromGroup("email", "email_from_address")).thenReturn("");
        when(configService.getStringFromGroup("email", "email_template")).thenReturn("default");

        mailService.applyDynamicMailConfig();

        assertEquals("smtp.example.com", sender.getHost());
        assertEquals(587, sender.getPort());
        assertEquals("user", sender.getUsername());
        assertEquals("pass", sender.getPassword());
        assertEquals("true", String.valueOf(sender.getJavaMailProperties().get("mail.smtp.starttls.enable")));
        assertEquals("false", String.valueOf(sender.getJavaMailProperties().get("mail.smtp.ssl.enable")));
    }

    @Test
    void applyDynamicMailConfig_clearsSslWhenEncryptionEmpty() {
        when(configService.getStringFromGroup("email", "email_host")).thenReturn("smtp.example.com");
        when(configService.getStringFromGroup("email", "email_port")).thenReturn("25");
        when(configService.getStringFromGroup("email", "email_username")).thenReturn("");
        when(configService.getStringFromGroup("email", "email_password")).thenReturn("");
        when(configService.getStringFromGroup("email", "email_encryption")).thenReturn("");

        mailService.applyDynamicMailConfig();

        assertFalse(Boolean.parseBoolean(String.valueOf(sender.getJavaMailProperties().get("mail.smtp.ssl.enable"))));
        assertFalse(Boolean.parseBoolean(String.valueOf(sender.getJavaMailProperties().get("mail.smtp.starttls.enable"))));
    }

    @Test
    void resolveTemplatePath_defaultUsesFlatMailTemplates() {
        when(configService.getStringFromGroup("email", "email_template")).thenReturn("default");
        assertEquals("mail/notify", mailService.resolveTemplatePath("notify"));

        when(configService.getStringFromGroup("email", "email_template")).thenReturn("custom");
        assertEquals("mail/custom/notify", mailService.resolveTemplatePath("notify"));
    }
}
