package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.mapper.SystemConfigMapper;
import com.v2board.api.model.SystemConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigServiceTicketStatusTest {

    @Test
    void getTicketStatus_readsDbOverYml() throws Exception {
        ConfigService svc = newConfigService(0);
        stubFullConfig(svc, """
                {"ticket":{"ticket_status":2}}
                """);
        assertEquals(2, svc.getTicketStatus());
    }

    @Test
    void getTicketStatus_fallsBackToYml() {
        assertEquals(0, newConfigService(0).getTicketStatus());
        assertEquals(1, newConfigService(1).getTicketStatus());
        assertEquals(2, newConfigService(2).getTicketStatus());
    }

    @Test
    void getTicketStatus_nullYmlDefaultsToZero() {
        ConfigService svc = newConfigService(0);
        ReflectionTestUtils.setField(svc, "ticketStatus", null);
        assertEquals(0, svc.getTicketStatus());
    }

    private static void stubFullConfig(ConfigService svc, String json) {
        SystemConfigMapper mapper = (SystemConfigMapper) ReflectionTestUtils.getField(svc, "systemConfigMapper");
        SystemConfig row = new SystemConfig();
        row.setName(SystemConfig.NAME_V2BOARD);
        row.setValue(json);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);
    }

    private static ConfigService newConfigService(int ticketStatus) {
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
        ReflectionTestUtils.setField(configService, "showInfoToServerEnable", false);
        ReflectionTestUtils.setField(configService, "inviteCommission", 10);
        ReflectionTestUtils.setField(configService, "inviteGenLimit", 5);
        ReflectionTestUtils.setField(configService, "ticketStatus", ticketStatus);
        ReflectionTestUtils.setField(configService, "withdrawCloseEnable", 0);
        ReflectionTestUtils.setField(configService, "commissionWithdrawLimit", 100);
        ReflectionTestUtils.setField(configService, "commissionDistributionEnable", 0);
        ReflectionTestUtils.setField(configService, "commissionDistributionL1", 100.0);
        return configService;
    }
}
