package com.v2board.api.service;

import com.v2board.api.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginPasswordLimitServiceTest {

    private ConfigService configService;
    private NodeCacheService nodeCacheService;
    private LoginPasswordLimitService service;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        nodeCacheService = mock(NodeCacheService.class);
        service = new LoginPasswordLimitService();
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "nodeCacheService", nodeCacheService);
    }

    @Test
    void disabled_skipsChecks() {
        when(configService.getPasswordLimitEnable()).thenReturn(0);
        assertDoesNotThrow(() -> service.assertNotLocked("a@b.com"));
        service.recordFailure("a@b.com");
        verify(nodeCacheService, never()).get(org.mockito.ArgumentMatchers.anyString());
        verify(nodeCacheService, never()).set(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void assertNotLocked_whenAtLimit_throws() {
        when(configService.getPasswordLimitEnable()).thenReturn(1);
        when(configService.getPasswordLimitCount()).thenReturn(5);
        when(configService.getPasswordLimitExpireMinutes()).thenReturn(60);
        when(nodeCacheService.get("PASSWORD_ERROR_LIMIT_a@b.com")).thenReturn(5);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assertNotLocked("a@b.com"));
        assertTrue(ex.getMessage().contains("60"));
        assertTrue(ex.getMessage().contains("password errors"));
    }

    @Test
    void recordFailure_incrementsWithTtl() {
        when(configService.getPasswordLimitEnable()).thenReturn(1);
        when(configService.getPasswordLimitExpireMinutes()).thenReturn(30);
        when(nodeCacheService.get("PASSWORD_ERROR_LIMIT_a@b.com")).thenReturn(2);

        service.recordFailure("a@b.com");
        verify(nodeCacheService).set(eq("PASSWORD_ERROR_LIMIT_a@b.com"), eq(3), eq(Duration.ofMinutes(30)));
    }

    @Test
    void assertNotLocked_underLimit_ok() {
        when(configService.getPasswordLimitEnable()).thenReturn(1);
        when(configService.getPasswordLimitCount()).thenReturn(5);
        when(nodeCacheService.get("PASSWORD_ERROR_LIMIT_a@b.com")).thenReturn(4);
        assertDoesNotThrow(() -> service.assertNotLocked("a@b.com"));
    }
}
