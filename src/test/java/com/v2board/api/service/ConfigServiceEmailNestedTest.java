package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.mapper.SystemConfigMapper;
import com.v2board.api.model.SystemConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigServiceEmailNestedTest {

    @Test
    void getStringFromGroup_readsNestedEmailKeys() throws Exception {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        SystemConfig row = new SystemConfig();
        row.setName(SystemConfig.NAME_V2BOARD);
        row.setValue(new ObjectMapper().writeValueAsString(Map.of(
                "email", Map.of(
                        "email_host", "smtp.example.com",
                        "email_port", "465",
                        "email_encryption", "ssl",
                        "email_from_address", "noreply@example.com"
                )
        )));
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);

        ConfigService configService = newConfigService(mapper);

        assertEquals("smtp.example.com", configService.getStringFromGroup("email", "email_host"));
        assertEquals("465", configService.getStringFromGroup("email", "email_port"));
        assertEquals("ssl", configService.getStringFromGroup("email", "email_encryption"));
        assertEquals("noreply@example.com", configService.getStringFromGroup("email", "email_from_address"));
        assertEquals("", configService.getStringFromGroup("email", "email_username"));
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
