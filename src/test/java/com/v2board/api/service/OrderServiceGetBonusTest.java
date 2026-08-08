package com.v2board.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceGetBonusTest {

    private ConfigService configService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        orderService = new OrderService();
        ReflectionTestUtils.setField(orderService, "configService", configService);
    }

    @Test
    void nestedConfig_matchTier() throws Exception {
        when(configService.getFullConfig()).thenReturn(nestedDeposit(List.of("100:10")));
        // 充值 100 元 = 10000 分 → 赠送 10 元 = 1000 分
        assertEquals(1000L, orderService.getBonus(10000L));
    }

    @Test
    void nestedConfig_belowThreshold_noBonus() throws Exception {
        when(configService.getFullConfig()).thenReturn(nestedDeposit(List.of("100:10")));
        assertEquals(0L, orderService.getBonus(9999L));
    }

    @Test
    void missingDepositSection_returnsZero() throws Exception {
        when(configService.getFullConfig()).thenReturn(Map.of("site", Map.of("app_name", "x")));
        assertEquals(0L, orderService.getBonus(10000L));
    }

    @Test
    void multiTier_picksMaxMatchingBonus() throws Exception {
        when(configService.getFullConfig()).thenReturn(
                nestedDeposit(List.of("100:10", "500:100", "200:30")));
        // 充值 500 元：满足全部门槛，最大赠送 100 元
        assertEquals(10000L, orderService.getBonus(50000L));
        // 充值 200 元：满足 100/200，最大 30 元
        assertEquals(3000L, orderService.getBonus(20000L));
    }

    @Test
    void objectArrayTiers_supported() throws Exception {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("deposit", Map.of("deposit_bounus", new Object[]{"100:10", "500:50"}));
        when(configService.getFullConfig()).thenReturn(config);
        assertEquals(5000L, orderService.getBonus(50000L));
    }

    @Test
    void nullOrZeroAmount_returnsZero() throws Exception {
        when(configService.getFullConfig()).thenReturn(nestedDeposit(List.of("100:10")));
        assertEquals(0L, orderService.getBonus(null));
        assertEquals(0L, orderService.getBonus(0L));
    }

    @Test
    void invalidLines_skipped() throws Exception {
        when(configService.getFullConfig()).thenReturn(
                nestedDeposit(List.of("bad", "100:10", ":20", "x:y")));
        assertEquals(1000L, orderService.getBonus(10000L));
    }

    private static Map<String, Object> nestedDeposit(List<String> tiers) {
        Map<String, Object> config = new LinkedHashMap<>();
        Map<String, Object> deposit = new LinkedHashMap<>();
        deposit.put("deposit_bounus", tiers);
        config.put("deposit", deposit);
        return config;
    }
}
