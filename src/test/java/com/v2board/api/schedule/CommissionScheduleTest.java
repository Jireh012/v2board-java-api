package com.v2board.api.schedule;

import com.v2board.api.mapper.CommissionLogMapper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.CommissionLog;
import com.v2board.api.model.Order;
import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommissionScheduleTest {

    private OrderMapper orderMapper;
    private UserMapper userMapper;
    private CommissionLogMapper commissionLogMapper;
    private ConfigService configService;
    private CommissionSchedule schedule;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        userMapper = mock(UserMapper.class);
        commissionLogMapper = mock(CommissionLogMapper.class);
        configService = mock(ConfigService.class);
        schedule = new CommissionSchedule();
        ReflectionTestUtils.setField(schedule, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(schedule, "userMapper", userMapper);
        ReflectionTestUtils.setField(schedule, "commissionLogMapper", commissionLogMapper);
        ReflectionTestUtils.setField(schedule, "configService", configService);
    }

    @Test
    void autoCheck_skipsWhenNestedEnableOff() throws Exception {
        when(configService.getCommissionAutoCheckEnable()).thenReturn(0);

        schedule.autoCheck();

        verify(orderMapper, never()).selectList(any());
        verify(orderMapper, never()).updateById(any());
    }

    @Test
    void payHandle_distributesByL1L2L3WhenEnabled() throws Exception {
        when(configService.getCommissionDistributionEnable()).thenReturn(1);
        when(configService.getCommissionDistributionL1()).thenReturn(60);
        when(configService.getCommissionDistributionL2()).thenReturn(30);
        when(configService.getCommissionDistributionL3()).thenReturn(10);
        when(configService.getWithdrawCloseEnable()).thenReturn(0);

        User l1 = new User();
        l1.setId(1L);
        l1.setInviteUserId(2L);
        l1.setCommissionBalance(0L);
        User l2 = new User();
        l2.setId(2L);
        l2.setInviteUserId(3L);
        l2.setCommissionBalance(0L);
        User l3 = new User();
        l3.setId(3L);
        l3.setInviteUserId(null);
        l3.setCommissionBalance(0L);
        when(userMapper.selectById(1L)).thenReturn(l1);
        when(userMapper.selectById(2L)).thenReturn(l2);
        when(userMapper.selectById(3L)).thenReturn(l3);

        Order order = new Order();
        order.setUserId(9L);
        order.setTradeNo("T1");
        order.setInviteUserId(1L);
        order.setCommissionBalance(1000L);
        order.setTotalAmount(10000L);
        order.setActualCommissionBalance(0L);

        schedule.payHandle(order);

        assertEquals(600L, l1.getCommissionBalance());
        assertEquals(300L, l2.getCommissionBalance());
        assertEquals(100L, l3.getCommissionBalance());
        assertEquals(2, order.getCommissionStatus());
        assertEquals(1000L, order.getActualCommissionBalance());

        ArgumentCaptor<CommissionLog> logCaptor = ArgumentCaptor.forClass(CommissionLog.class);
        verify(commissionLogMapper, times(3)).insert(logCaptor.capture());
        assertEquals(600L, logCaptor.getAllValues().get(0).getGetAmount());
        assertEquals(300L, logCaptor.getAllValues().get(1).getGetAmount());
        assertEquals(100L, logCaptor.getAllValues().get(2).getGetAmount());
    }
}
