package com.v2board.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecaptchaServiceTest {

    private ConfigService configService;
    private RecaptchaService service;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        service = new RecaptchaService() {
            @Override
            boolean verifyWithGoogle(String secret, String response, String remoteIp) {
                return "ok-token".equals(response) && "secret".equals(secret);
            }
        };
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void disabled_skipsVerify() {
        when(configService.getRecaptchaEnable()).thenReturn(0);
        assertDoesNotThrow(() -> service.verifyIfEnabled(null, "127.0.0.1"));
    }

    @Test
    void enabled_missingSecret_failsClosed() {
        when(configService.getRecaptchaEnable()).thenReturn(1);
        when(configService.getRecaptchaSecret()).thenReturn("");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyIfEnabled("tok", "127.0.0.1"));
        assertEquals("reCAPTCHA 未正确配置", ex.getMessage());
    }

    @Test
    void enabled_badToken_rejected() {
        when(configService.getRecaptchaEnable()).thenReturn(1);
        when(configService.getRecaptchaSecret()).thenReturn("secret");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyIfEnabled("bad", "127.0.0.1"));
        assertEquals("Invalid code is incorrect", ex.getMessage());
    }

    @Test
    void enabled_goodToken_ok() {
        when(configService.getRecaptchaEnable()).thenReturn(1);
        when(configService.getRecaptchaSecret()).thenReturn("secret");
        assertDoesNotThrow(() -> service.verifyIfEnabled("ok-token", "127.0.0.1"));
    }
}
