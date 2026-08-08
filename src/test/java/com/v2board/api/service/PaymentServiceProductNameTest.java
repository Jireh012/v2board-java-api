package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.mapper.SystemConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceProductNameTest {

    @Test
    void defaultSubscribeProductName_usesAppName() {
        PaymentService paymentService = new PaymentService();
        ReflectionTestUtils.setField(paymentService, "configService", configServiceWithAppName("谜之站点"));

        assertEquals("谜之站点 - 订阅", paymentService.defaultSubscribeProductName());
    }

    @Test
    void ensureDefaultProductName_fillsWhenMissing() {
        PaymentService paymentService = new PaymentService();
        ReflectionTestUtils.setField(paymentService, "configService", configServiceWithAppName("PanelSite"));

        Map<String, Object> config = new HashMap<>();
        ReflectionTestUtils.invokeMethod(paymentService, "ensureDefaultProductName", config);
        assertEquals("PanelSite - 订阅", config.get("product_name"));

        config.put("product_name", "自定义商品");
        ReflectionTestUtils.invokeMethod(paymentService, "ensureDefaultProductName", config);
        assertEquals("自定义商品", config.get("product_name"));
    }

    private static ConfigService configServiceWithAppName(String name) {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ConfigService configService = new ConfigService();
        ReflectionTestUtils.setField(configService, "systemConfigMapper", mapper);
        ReflectionTestUtils.setField(configService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(configService, "appName", name);
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
