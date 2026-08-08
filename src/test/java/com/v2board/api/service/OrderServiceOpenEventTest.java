package com.v2board.api.service;

import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceOpenEventTest {

    private ConfigService configService;
    private UserMapper userMapper;
    private PlanMapper planMapper;
    private OrderMapper orderMapper;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        userMapper = mock(UserMapper.class);
        planMapper = mock(PlanMapper.class);
        orderMapper = mock(OrderMapper.class);
        orderService = new OrderService();
        ReflectionTestUtils.setField(orderService, "configService", configService);
        ReflectionTestUtils.setField(orderService, "userMapper", userMapper);
        ReflectionTestUtils.setField(orderService, "planMapper", planMapper);
        ReflectionTestUtils.setField(orderService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(orderService, "userService", mock(UserService.class));
    }

    @Test
    void renew_eventOn_clearsUsedTraffic() {
        when(configService.getRenewOrderEventId()).thenReturn(1);
        User user = baseUser(100L, 200L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(planMapper.selectById(10L)).thenReturn(basePlan());

        Order order = renewOrder();
        orderService.open(order);

        assertEquals(0L, user.getU());
        assertEquals(0L, user.getD());
        verify(userMapper).updateById(user);
    }

    @Test
    void renew_eventOff_keepsUsedTraffic() {
        when(configService.getRenewOrderEventId()).thenReturn(0);
        User user = baseUser(100L, 200L);
        // far-future expiry so same-day renew reset does not fire
        user.setExpiredAt(System.currentTimeMillis() / 1000 + 86400L * 40);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(planMapper.selectById(10L)).thenReturn(basePlan());

        Order order = renewOrder();
        orderService.open(order);

        assertEquals(100L, user.getU());
        assertEquals(200L, user.getD());
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    void resetPrice_skipsOpenEventConfig() {
        when(configService.getNewOrderEventId()).thenReturn(0);
        when(configService.getRenewOrderEventId()).thenReturn(0);
        when(configService.getChangeOrderEventId()).thenReturn(0);
        User user = baseUser(50L, 60L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(planMapper.selectById(10L)).thenReturn(basePlan());

        Order order = new Order();
        order.setUserId(1L);
        order.setPlanId(10L);
        order.setType(4);
        order.setPeriod("reset_price");
        order.setTradeNo("T-reset");

        orderService.open(order);

        // reset_price itself clears traffic; openEvent must not depend on type 1/2/3 getters
        assertEquals(0L, user.getU());
        assertEquals(0L, user.getD());
    }

    private static User baseUser(long u, long d) {
        User user = new User();
        user.setId(1L);
        user.setU(u);
        user.setD(d);
        user.setTransferEnable(10L * 1073741824L);
        user.setPlanId(10L);
        user.setGroupId(1);
        user.setExpiredAt(System.currentTimeMillis() / 1000 + 86400L * 40);
        user.setBalance(0L);
        return user;
    }

    private static Plan basePlan() {
        Plan plan = new Plan();
        plan.setId(10L);
        plan.setTransferEnable(10L);
        plan.setGroupId(1);
        plan.setCapacityLimit(null);
        plan.setSpeedLimit(null);
        return plan;
    }

    private static Order renewOrder() {
        Order order = new Order();
        order.setUserId(1L);
        order.setPlanId(10L);
        order.setType(2);
        order.setPeriod("month_price");
        order.setTradeNo("T-renew");
        return order;
    }
}
