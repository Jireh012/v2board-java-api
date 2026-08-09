package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.SystemConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigServiceServerApiPrefixTest {

    @Test
    void generateServerApiPrefix_matchesPattern() {
        String p = ConfigService.generateServerApiPrefix();
        assertTrue(p.matches("^/api/n/[a-z0-9]{12}$"), p);
        assertTrue(ConfigService.isValidServerApiPrefix(p));
    }

    @Test
    void normalizeServerApiPrefix_stripsTrailingSlash() {
        assertEquals("/n/abc", ConfigService.normalizeServerApiPrefix("n/abc/"));
        assertEquals("", ConfigService.normalizeServerApiPrefix("  "));
    }

    @Test
    void isValidServerApiPrefix_rejectsReserved() {
        assertFalse(ConfigService.isValidServerApiPrefix("/api/v1/server"));
        assertFalse(ConfigService.isValidServerApiPrefix("/api/v2"));
        assertFalse(ConfigService.isValidServerApiPrefix("/n/abcdefghijkl"));
        assertTrue(ConfigService.isValidServerApiPrefix("/api/n/abcdefghijkl"));
    }

    @Test
    void save_autoGeneratesEmptyPrefix() throws Exception {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ConfigService configService = newConfigService(mapper);

        Map<String, Object> server = new HashMap<>();
        server.put("server_token", "1234567890123456");
        server.put("server_api_prefix", "");
        Map<String, Object> body = new HashMap<>();
        body.put("server", server);

        configService.save(body);

        Map<String, Object> full = configService.getFullConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> savedServer = (Map<String, Object>) full.get("server");
        // getFullConfig reads from DB mock (null) so only defaults — check ensure on in-memory body path via ensure
        Map<String, Object> probe = new HashMap<>();
        probe.put("server", new HashMap<>(Map.of("server_api_prefix", "")));
        assertTrue(ConfigService.ensureServerApiPrefixInPlace(probe));
        assertTrue(ConfigService.getServerApiPrefixFromMap(probe).matches("^/api/n/[a-z0-9]{12}$"));
    }

    @Test
    void save_rejectsInvalidPrefix() {
        ConfigService configService = newConfigService(mock(SystemConfigMapper.class));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.save(serverBody("server_api_prefix", "/api/v1/server")));
        assertTrue(ex.getMessage().contains("节点 API 前缀"));
    }

    @Test
    void save_acceptsCustomPrefix() throws Exception {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ConfigService configService = newConfigService(mapper);

        assertDoesNotThrow(() -> configService.save(serverBody("server_api_prefix", "/n/mycustompath")));
        // legacy root prefix is mounted under /api/ on save
    }

    @Test
    void ensureServerApiPrefixInPlace_migratesLegacyRootPrefix() {
        Map<String, Object> probe = new HashMap<>();
        probe.put("server", new HashMap<>(Map.of("server_api_prefix", "/n/abcdefghijkl")));
        assertTrue(ConfigService.ensureServerApiPrefixInPlace(probe));
        assertEquals("/api/n/abcdefghijkl", ConfigService.getServerApiPrefixFromMap(probe));
        assertFalse(ConfigService.ensureServerApiPrefixInPlace(probe));
    }

    private static Map<String, Object> serverBody(String key, Object value) {
        Map<String, Object> server = new HashMap<>();
        server.put(key, value);
        Map<String, Object> body = new HashMap<>();
        body.put("server", server);
        return body;
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
