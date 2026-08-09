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

class ConfigServiceClientApiPrefixTest {

    @Test
    void generatePrefixes_matchPatterns() {
        assertTrue(ConfigService.generatePassportApiPrefix().matches("^/p/[a-z0-9]{12}$"));
        assertTrue(ConfigService.generateUserApiPrefix().matches("^/u/[a-z0-9]{12}$"));
        assertTrue(ConfigService.generateAdminApiPrefix().matches("^/a/[a-z0-9]{12}$"));
        assertEquals("/config", ConfigService.FIXED_PUBLIC_CONFIG_PATH);
    }

    @Test
    void isValidClientApiPrefix_rejectsReserved() {
        assertFalse(ConfigService.isValidClientApiPrefix("/api/v1/user"));
        assertFalse(ConfigService.isValidClientApiPrefix("/api/v1/passport"));
        assertFalse(ConfigService.isValidClientApiPrefix("/api/v1/admin"));
        assertFalse(ConfigService.isValidClientApiPrefix("/config"));
        assertTrue(ConfigService.isValidClientApiPrefix("/p/abcdefghijkl"));
        assertTrue(ConfigService.isValidClientApiPrefix("/a/abcdefghijkl"));
    }

    @Test
    void pathsConflict_detectsOverlap() {
        assertTrue(ConfigService.pathsConflict("/p/abc", "/p/abc/x"));
        assertFalse(ConfigService.pathsConflict("/p/abc", "/u/abc"));
    }

    @Test
    void ensureClientApiPathsInPlace_autoGeneratesAndStripsLegacyPublicPath() {
        Map<String, Object> site = new HashMap<>();
        site.put("public_config_path", "/c/oldpath");
        Map<String, Object> full = new HashMap<>();
        full.put("site", site);
        assertTrue(ConfigService.ensureClientApiPathsInPlace(full));
        assertTrue(ConfigService.getSitePathFromMap(full, "passport_api_prefix").matches("^/p/[a-z0-9]{12}$"));
        assertTrue(ConfigService.getSitePathFromMap(full, "user_api_prefix").matches("^/u/[a-z0-9]{12}$"));
        assertTrue(ConfigService.getSitePathFromMap(full, "admin_api_prefix").matches("^/a/[a-z0-9]{12}$"));
        assertFalse(((Map<?, ?>) full.get("site")).containsKey("public_config_path"));
        assertFalse(ConfigService.ensureClientApiPathsInPlace(full));
    }

    @Test
    void save_rejectsReservedPassportPrefix() {
        ConfigService configService = newConfigService(mock(SystemConfigMapper.class));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.save(siteBody("passport_api_prefix", "/api/v1/passport")));
        assertTrue(ex.getMessage().contains("Passport API 前缀"));
    }

    @Test
    void save_acceptsCustomPrefixes() throws Exception {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ConfigService configService = newConfigService(mapper);

        Map<String, Object> site = new HashMap<>();
        site.put("passport_api_prefix", "/p/mycustompath");
        site.put("user_api_prefix", "/u/mycustompath");
        site.put("admin_api_prefix", "/a/mycustompath");
        Map<String, Object> body = new HashMap<>();
        body.put("site", site);
        assertDoesNotThrow(() -> configService.save(body));
    }

    @Test
    void save_rejectsReservedAdminPrefix() {
        ConfigService configService = newConfigService(mock(SystemConfigMapper.class));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.save(siteBody("admin_api_prefix", "/api/v1/admin")));
        assertTrue(ex.getMessage().contains("管理 API 前缀"));
    }

    @Test
    void save_rejectsPrefixConflictingWithFixedPublicConfig() {
        ConfigService configService = newConfigService(mock(SystemConfigMapper.class));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.save(siteBody("user_api_prefix", "/config")));
        assertTrue(ex.getMessage().contains("/config") || ex.getMessage().contains("不合法"));
    }

    private static Map<String, Object> siteBody(String key, Object value) {
        Map<String, Object> site = new HashMap<>();
        site.put(key, value);
        Map<String, Object> body = new HashMap<>();
        body.put("site", site);
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
