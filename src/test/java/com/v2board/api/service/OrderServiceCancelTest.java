package com.v2board.api.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.User;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceCancelTest {

    private OrderMapper orderMapper;
    private UserMapper userMapper;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
        orderMapper = mock(OrderMapper.class);
        userMapper = mock(UserMapper.class);
        orderService = new OrderService();
        ReflectionTestUtils.setField(orderService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(orderService, "userMapper", userMapper);
    }

    @Test
    void cancel_casSuccessRefundsBalance() {
        Order order = pendingOrder();
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        User user = new User();
        user.setId(9L);
        user.setBalance(100L);
        when(userMapper.selectById(9L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        assertTrue(orderService.cancel(order));
        assertEquals(2, order.getStatus());
        assertEquals(150L, user.getBalance());
    }

    @Test
    void cancel_casFailureDoesNotRefund() {
        Order order = pendingOrder();
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertFalse(orderService.cancel(order));
        verify(userMapper, never()).selectById(any());
        verify(userMapper, never()).updateById(any());
        assertEquals(0, order.getStatus());
    }

    @Test
    void cancel_nonPendingSkipsUpdate() {
        Order order = pendingOrder();
        order.setStatus(1);

        assertFalse(orderService.cancel(order));
        verify(orderMapper, never()).update(any(), any());
    }

    private static Order pendingOrder() {
        Order order = new Order();
        order.setId(3L);
        order.setUserId(9L);
        order.setStatus(0);
        order.setBalanceAmount(50L);
        return order;
    }
}
