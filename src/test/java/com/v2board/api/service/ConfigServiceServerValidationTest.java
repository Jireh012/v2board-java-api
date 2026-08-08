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

class ConfigServiceServerValidationTest {

    @Test
    void save_rejectsShortOrEmptyServerToken() {
        ConfigService configService = newConfigService(mock(SystemConfigMapper.class));

        BusinessException empty = assertThrows(BusinessException.class,
                () -> configService.save(serverBody("server_token", "")));
        assertTrue(empty.getMessage().contains("通讯密钥至少 16 位"));

        BusinessException shortToken = assertThrows(BusinessException.class,
                () -> configService.save(serverBody("server_token", "123456789012345")));
        assertTrue(shortToken.getMessage().contains("通讯密钥至少 16 位"));
    }

    @Test
    void save_acceptsTokenAtLeast16() throws Exception {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ConfigService configService = newConfigService(mapper);

        assertDoesNotThrow(() -> configService.save(serverBody("server_token", "1234567890123456")));
    }

    @Test
    void save_trimsServerTokenBeforePersist() throws Exception {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ConfigService configService = newConfigService(mapper);

        Map<String, Object> body = serverBody("server_token", "  1234567890123456  ");
        assertDoesNotThrow(() -> configService.save(body));
        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) body.get("server");
        assertEquals("1234567890123456", server.get("server_token"));
    }

    @Test
    void save_rejectsInvalidIntervalsAndTraffic() {
        ConfigService configService = newConfigService(mock(SystemConfigMapper.class));

        BusinessException pull = assertThrows(BusinessException.class,
                () -> configService.save(serverBody("server_pull_interval", 0)));
        assertTrue(pull.getMessage().contains("拉取间隔"));

        BusinessException push = assertThrows(BusinessException.class,
                () -> configService.save(serverBody("server_push_interval", -1)));
        assertTrue(push.getMessage().contains("推送间隔"));

        BusinessException report = assertThrows(BusinessException.class,
                () -> configService.save(serverBody("server_node_report_min_traffic", -1)));
        assertTrue(report.getMessage().contains("最低上报流量"));

        BusinessException online = assertThrows(BusinessException.class,
                () -> configService.save(serverBody("server_device_online_min_traffic", -5)));
        assertTrue(online.getMessage().contains("在线判定最低流量"));
    }

    @Test
    void save_rejectsInvalidDeviceLimitMode() {
        ConfigService configService = newConfigService(mock(SystemConfigMapper.class));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.save(serverBody("device_limit_mode", 2)));
        assertTrue(ex.getMessage().contains("设备限制模式"));
    }

    @Test
    void save_acceptsValidServerFields() throws Exception {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ConfigService configService = newConfigService(mapper);

        Map<String, Object> server = new HashMap<>();
        server.put("server_token", "abcdefghijklmnopqrstuvwxyz");
        server.put("server_pull_interval", 1);
        server.put("server_push_interval", 60);
        server.put("server_node_report_min_traffic", 0);
        server.put("server_device_online_min_traffic", 100);
        server.put("device_limit_mode", 1);
        Map<String, Object> body = new HashMap<>();
        body.put("server", server);

        assertDoesNotThrow(() -> configService.save(body));
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
