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

class ConfigServiceInviteCommissionTest {

    @Test
    void getters_readNestedInviteFromDb() throws Exception {
        ConfigService svc = newConfigService();
        stubFullConfig(svc, """
                {"invite":{
                  "invite_gen_limit":2,
                  "invite_commission":25,
                  "commission_first_time_enable":0,
                  "commission_auto_check_enable":0,
                  "commission_withdraw_limit":50,
                  "commission_withdraw_method":"alipay",
                  "withdraw_close_enable":1,
                  "commission_distribution_enable":1,
                  "commission_distribution_l1":60,
                  "commission_distribution_l2":30,
                  "commission_distribution_l3":10
                }}
                """);
        assertEquals(2, svc.getInviteGenLimit());
        assertEquals(25, svc.getInviteCommission());
        assertEquals(0, svc.getCommissionFirstTimeEnable());
        assertEquals(0, svc.getCommissionAutoCheckEnable());
        assertEquals(50, svc.getCommissionWithdrawLimit());
        assertEquals("alipay", svc.getCommissionWithdrawMethod());
        assertEquals(1, svc.getWithdrawCloseEnable());
        assertEquals(1, svc.getCommissionDistributionEnable());
        assertEquals(60, svc.getCommissionDistributionL1());
        assertEquals(30, svc.getCommissionDistributionL2());
        assertEquals(10, svc.getCommissionDistributionL3());
    }

    @Test
    void getters_acceptBooleanAndStringFlags() throws Exception {
        ConfigService svc = newConfigService();
        stubFullConfig(svc, """
                {"invite":{
                  "commission_auto_check_enable":false,
                  "withdraw_close_enable":"1",
                  "commission_distribution_enable":true
                }}
                """);
        assertEquals(0, svc.getCommissionAutoCheckEnable());
        assertEquals(1, svc.getWithdrawCloseEnable());
        assertEquals(1, svc.getCommissionDistributionEnable());
    }

    @Test
    void getters_fallBackToYmlDefaults() {
        ConfigService svc = newConfigService();
        assertEquals(5, svc.getInviteGenLimit());
        assertEquals(10, svc.getInviteCommission());
        assertEquals(1, svc.getCommissionFirstTimeEnable());
        assertEquals(1, svc.getCommissionAutoCheckEnable());
        assertEquals(100, svc.getCommissionWithdrawLimit());
        assertEquals("alipay,wechat", svc.getCommissionWithdrawMethod());
        assertEquals(0, svc.getWithdrawCloseEnable());
        assertEquals(0, svc.getCommissionDistributionEnable());
        assertEquals(100, svc.getCommissionDistributionL1());
        assertEquals(0, svc.getCommissionDistributionL2());
        assertEquals(0, svc.getCommissionDistributionL3());
    }

    private static void stubFullConfig(ConfigService svc, String json) {
        SystemConfigMapper mapper = (SystemConfigMapper) ReflectionTestUtils.getField(svc, "systemConfigMapper");
        SystemConfig row = new SystemConfig();
        row.setName(SystemConfig.NAME_V2BOARD);
        row.setValue(json);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);
    }

    private static ConfigService newConfigService() {
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
        ReflectionTestUtils.setField(configService, "ticketStatus", 0);
        ReflectionTestUtils.setField(configService, "withdrawCloseEnable", 0);
        ReflectionTestUtils.setField(configService, "commissionWithdrawLimit", 100);
        ReflectionTestUtils.setField(configService, "commissionDistributionEnable", 0);
        ReflectionTestUtils.setField(configService, "commissionDistributionL1", 100.0);
        return configService;
    }
}
