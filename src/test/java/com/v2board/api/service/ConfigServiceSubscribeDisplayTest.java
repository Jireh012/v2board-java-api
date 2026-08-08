package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.mapper.SystemConfigMapper;
import com.v2board.api.model.SystemConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigServiceSubscribeDisplayTest {

    @Test
    void getShowInfoToServerEnable_readsDbOverYml() throws Exception {
        ConfigService svc = newConfigService(false);
        stubFullConfig(svc, """
                {"subscribe":{"show_info_to_server_enable":1,"show_subscribe_method":2,"show_subscribe_expire":3}}
                """);
        assertTrue(svc.getShowInfoToServerEnable());
        assertEquals(2, svc.getShowSubscribeMethod());
        assertEquals(3, svc.getShowSubscribeExpire());
    }

    @Test
    void getShowInfoToServerEnable_fallsBackToYml() {
        ConfigService off = newConfigService(false);
        assertFalse(off.getShowInfoToServerEnable());
        ConfigService on = newConfigService(true);
        assertTrue(on.getShowInfoToServerEnable());
    }

    @Test
    void buildSubscribeUrl_alwaysDirectToken_ignoresDisplayMethod() throws Exception {
        ConfigService svc = newConfigService(true);
        ReflectionTestUtils.setField(svc, "showSubscribeMethod", 2);
        ReflectionTestUtils.setField(svc, "showSubscribeExpire", 1);
        stubFullConfig(svc, """
                {"subscribe":{"show_subscribe_method":2,"show_subscribe_expire":1},"site":{"subscribe_path":"/s","subscribe_url":"https://panel.example.com"}}
                """);

        String url = svc.buildSubscribeUrl("plain-token", 42L);
        assertEquals("https://panel.example.com/s?token=plain-token", url);
    }

    private static void stubFullConfig(ConfigService svc, String json) {
        SystemConfigMapper mapper = (SystemConfigMapper) ReflectionTestUtils.getField(svc, "systemConfigMapper");
        SystemConfig row = new SystemConfig();
        row.setName(SystemConfig.NAME_V2BOARD);
        row.setValue(json);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);
    }

    private static ConfigService newConfigService(boolean showInfo) {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

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
        ReflectionTestUtils.setField(configService, "showInfoToServerEnable", showInfo);
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
