package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.SystemConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigServiceSubscribePathValidationTest {

    @Test
    void normalizeSubscribePathInput_rules() {
        assertEquals("", ConfigService.normalizeSubscribePathInput(null));
        assertEquals("", ConfigService.normalizeSubscribePathInput("  "));
        assertEquals("/s", ConfigService.normalizeSubscribePathInput("s"));
        assertEquals("/custom/sub", ConfigService.normalizeSubscribePathInput(" custom/sub/ "));
        assertEquals("/api/v1/client/subscribe",
                ConfigService.normalizeSubscribePathInput("/api/v1/client/subscribe"));
    }

    @Test
    void isValidSubscribePath_acceptsCustomAndDefault() {
        assertTrue(ConfigService.isValidSubscribePath("", "admin888"));
        assertTrue(ConfigService.isValidSubscribePath("/s", "admin888"));
        assertTrue(ConfigService.isValidSubscribePath("/api/v1/client/subscribe", "admin888"));
        assertTrue(ConfigService.isValidSubscribePath("/sub.link_v1", "admin888"));
    }

    @Test
    void isValidSubscribePath_rejectsIllegal() {
        assertFalse(ConfigService.isValidSubscribePath("/", "admin888"));
        assertFalse(ConfigService.isValidSubscribePath("/foo/../bar", "admin888"));
        assertFalse(ConfigService.isValidSubscribePath("/path with space", "admin888"));
        assertFalse(ConfigService.isValidSubscribePath("/path?x=1", "admin888"));
        assertFalse(ConfigService.isValidSubscribePath("/api/v1/user", "admin888"));
        assertFalse(ConfigService.isValidSubscribePath("/api/v1/user/extra", "admin888"));
        assertFalse(ConfigService.isValidSubscribePath("/api/v1/admin", "admin888"));
        assertFalse(ConfigService.isValidSubscribePath("/api/v1/passport", "admin888"));
        assertFalse(ConfigService.isValidSubscribePath("/api/v1/guest", "admin888"));
        assertFalse(ConfigService.isValidSubscribePath("/api/v1/server", "admin888"));
        assertFalse(ConfigService.isValidSubscribePath("/admin888", "admin888"));
        assertFalse(ConfigService.isValidSubscribePath("/admin888/x", "admin888"));
        assertFalse(ConfigService.isValidSubscribePath("/" + "a".repeat(128), "admin888")); // 129 chars
    }

    @Test
    void save_rejectsIllegalSubscribePath() {
        ConfigService configService = newConfigService(mock(SystemConfigMapper.class));
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> site = new HashMap<>();
        site.put("subscribe_path", "/api/v1/user/steal");
        body.put("site", site);

        BusinessException ex = assertThrows(BusinessException.class, () -> configService.save(body));
        assertTrue(ex.getMessage().contains("订阅路径不合法"));
    }

    @Test
    void save_allowsEmptyAndCustomSubscribePath() throws Exception {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ConfigService configService = newConfigService(mapper);

        assertDoesNotThrow(() -> configService.save(Map.of(
                "site", mutableSite("subscribe_path", "")
        )));
        assertDoesNotThrow(() -> configService.save(Map.of(
                "site", mutableSite("subscribe_path", "link/abc")
        )));
    }

    private static Map<String, Object> mutableSite(String key, String value) {
        Map<String, Object> site = new HashMap<>();
        site.put(key, value);
        Map<String, Object> body = new HashMap<>();
        body.put("site", site);
        // save expects top-level body; callers wrap again — return site map for Map.of("site", ...)
        return site;
    }

    private static ConfigService newConfigService(SystemConfigMapper mapper) {
        ConfigService configService = new ConfigService();
        ReflectionTestUtils.setField(configService, "systemConfigMapper", mapper);
        ReflectionTestUtils.setField(configService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(configService, "appName", "V2Board");
        ReflectionTestUtils.setField(configService, "phpConfigPath", "");
        ReflectionTestUtils.setField(configService, "appUrl", "");
        ReflectionTestUtils.setField(configService, "subscribeUrl", "");
        ReflectionTestUtils.setField(configService, "subscribePath", "/api/v1/client/subscribe");
        ReflectionTestUtils.setField(configService, "showSubscribeMethod", 0);
        ReflectionTestUtils.setField(configService, "showSubscribeExpire", 5);
        ReflectionTestUtils.setField(configService, "allowNewPeriod", 0);
        ReflectionTestUtils.setField(configService, "resetTrafficMethod", 0);
        ReflectionTestUtils.setField(configService, "showInfoToServerEnable", false);
        ReflectionTestUtils.setField(configService, "inviteCommission", 10);
        ReflectionTestUtils.setField(configService, "inviteGenLimit", 5);
        ReflectionTestUtils.setField(configService, "ticketStatus", 0);
        ReflectionTestUtils.setField(configService, "withdrawCloseEnable", 0);
        ReflectionTestUtils.setField(configService, "commissionWithdrawLimit", 100);
        ReflectionTestUtils.setField(configService, "commissionDistributionEnable", 0);
        ReflectionTestUtils.setField(configService, "commissionDistributionL1", 100.0);
        return configService;
    }
}
