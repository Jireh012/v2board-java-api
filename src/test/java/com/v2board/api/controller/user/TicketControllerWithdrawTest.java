package com.v2board.api.controller.user;

import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.TicketMapper;
import com.v2board.api.mapper.TicketMessageMapper;
import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import com.v2board.api.service.TelegramService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketControllerWithdrawTest {

    private TicketMapper ticketMapper;
    private TicketMessageMapper ticketMessageMapper;
    private ConfigService configService;
    private TicketController controller;
    private HttpServletRequest request;
    private User user;

    @BeforeEach
    void setUp() {
        ticketMapper = mock(TicketMapper.class);
        ticketMessageMapper = mock(TicketMessageMapper.class);
        configService = mock(ConfigService.class);
        controller = new TicketController();
        ReflectionTestUtils.setField(controller, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(controller, "ticketMessageMapper", ticketMessageMapper);
        ReflectionTestUtils.setField(controller, "orderMapper", mock(OrderMapper.class));
        ReflectionTestUtils.setField(controller, "configService", configService);
        ReflectionTestUtils.setField(controller, "telegramService", mock(TelegramService.class));

        user = new User();
        user.setId(9L);
        user.setCommissionBalance(100L);
        request = mock(HttpServletRequest.class);
        when(request.getAttribute("user")).thenReturn(user);

        when(configService.getTicketStatus()).thenReturn(0);
        when(configService.getWithdrawCloseEnable()).thenReturn(0);
        when(configService.getCommissionWithdrawMethod()).thenReturn("alipay,wechat");
        when(configService.getCommissionWithdrawLimit()).thenReturn(100);
    }

    @Test
    void withdraw_allowsWhenBalanceCentsMeetsLimitCents() {
        when(ticketMapper.insert(any())).thenAnswer(inv -> {
            inv.getArgument(0, com.v2board.api.model.Ticket.class).setId(1L);
            return 1;
        });
        when(ticketMessageMapper.insert(any())).thenReturn(1);

        assertEquals(0, controller.withdraw(request, "alipay", "acc").getCode());
        verify(ticketMapper).insert(any());
    }

    @Test
    void withdraw_rejectsWhenBalanceCentsBelowLimitCents() {
        user.setCommissionBalance(99L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.withdraw(request, "alipay", "acc"));
        assertTrue(ex.getMessage().contains("最低限额"));
        verify(ticketMapper, never()).insert(any());
    }

    @Test
    void withdraw_rejectsWhenWithdrawClosed() {
        when(configService.getWithdrawCloseEnable()).thenReturn(1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.withdraw(request, "alipay", "acc"));
        assertEquals("当前系统暂不支持提现工单", ex.getMessage());
        verify(ticketMapper, never()).insert(any());
    }
}
