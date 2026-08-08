package com.v2board.api.service;

import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceGetConfigIntTest {

    private ConfigService configService;
    private UserMapper userMapper;
    private OrderMapper orderMapper;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        userMapper = mock(UserMapper.class);
        orderMapper = mock(OrderMapper.class);
        orderService = new OrderService();
        ReflectionTestUtils.setField(orderService, "configService", configService);
        ReflectionTestUtils.setField(orderService, "userMapper", userMapper);
        ReflectionTestUtils.setField(orderService, "orderMapper", orderMapper);
    }

    @Test
    void setInvite_acceptsBooleanTrueAsCommissionFirstTimeEnable() throws Exception {
        Map<String, Object> invite = new LinkedHashMap<>();
        invite.put("commission_first_time_enable", Boolean.TRUE);
        invite.put("invite_commission", "15");
        when(configService.getFullConfig()).thenReturn(Map.of("invite", invite));

        User buyer = new User();
        buyer.setId(2L);
        buyer.setInviteUserId(1L);
        User inviter = new User();
        inviter.setId(1L);
        inviter.setCommissionType(0);
        when(userMapper.selectById(1L)).thenReturn(inviter);
        when(orderMapper.selectCount(any())).thenReturn(0L);

        Order order = new Order();
        order.setTotalAmount(10000L);
        orderService.setInvite(order, buyer);

        assertEquals(1L, order.getInviteUserId());
        assertEquals(1500L, order.getCommissionBalance());
    }

    @Test
    void setInvite_acceptsStringZeroToDisableFirstTimeOnly() throws Exception {
        Map<String, Object> invite = new LinkedHashMap<>();
        invite.put("commission_first_time_enable", "0");
        invite.put("invite_commission", 10);
        when(configService.getFullConfig()).thenReturn(Map.of("invite", invite));

        User buyer = new User();
        buyer.setId(2L);
        buyer.setInviteUserId(1L);
        User inviter = new User();
        inviter.setId(1L);
        inviter.setCommissionType(0);
        when(userMapper.selectById(1L)).thenReturn(inviter);
        // Already has a valid order — with first_time=0 should still commission
        when(orderMapper.selectCount(any())).thenReturn(1L);

        Order order = new Order();
        order.setTotalAmount(20000L);
        orderService.setInvite(order, buyer);

        assertEquals(2000L, order.getCommissionBalance());
    }

    @Test
    void parseConfigInt_booleanAndStringViaReflection() {
        Integer t = ReflectionTestUtils.invokeMethod(orderService, "parseConfigInt", Boolean.TRUE);
        Integer f = ReflectionTestUtils.invokeMethod(orderService, "parseConfigInt", Boolean.FALSE);
        Integer n = ReflectionTestUtils.invokeMethod(orderService, "parseConfigInt", "12");
        assertEquals(1, t.intValue());
        assertEquals(0, f.intValue());
        assertEquals(12, n.intValue());
        assertNull(ReflectionTestUtils.invokeMethod(orderService, "parseConfigInt", "x"));
        assertNull(ReflectionTestUtils.invokeMethod(orderService, "parseConfigInt", (Object) null));
    }
}
