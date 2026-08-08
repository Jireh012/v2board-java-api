package com.v2board.api.service;

import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceSetOrderTypeTest {

    private ConfigService configService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        orderService = new OrderService();
        ReflectionTestUtils.setField(orderService, "configService", configService);
        ReflectionTestUtils.setField(orderService, "orderMapper", mock(OrderMapper.class));
        ReflectionTestUtils.setField(orderService, "userService", mock(UserService.class));
    }

    @Test
    void planChangeDisabled_rejectsUpgrade() {
        when(configService.getPlanChangeEnable()).thenReturn(0);
        User user = new User();
        user.setPlanId(1L);
        user.setExpiredAt(System.currentTimeMillis() / 1000 + 86400);
        Order order = new Order();
        order.setPlanId(2L);
        order.setPeriod("month_price");
        order.setTotalAmount(1000L);

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.setOrderType(order, user));
        assertEquals("目前不允许更改订阅，请联系客服或提交工单操作", ex.getMessage());
    }

    @Test
    void renew_samePlan() {
        when(configService.getPlanChangeEnable()).thenReturn(1);
        User user = new User();
        user.setPlanId(1L);
        user.setExpiredAt(System.currentTimeMillis() / 1000 + 86400);
        Order order = new Order();
        order.setPlanId(1L);
        order.setPeriod("month_price");
        orderService.setOrderType(order, user);
        assertEquals(2, order.getType());
    }

    @Test
    void vipDiscount_appliesPercent() {
        User user = new User();
        user.setDiscount(10);
        Order order = new Order();
        order.setTotalAmount(1000L);
        order.setDiscountAmount(0L);
        orderService.setVipDiscount(order, user);
        assertEquals(100L, order.getDiscountAmount());
        assertEquals(900L, order.getTotalAmount());
    }
}
