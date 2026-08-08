package com.v2board.api.controller.user;

import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.CommissionLogMapper;
import com.v2board.api.mapper.InviteCodeMapper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InviteControllerTest {

    private InviteCodeMapper inviteCodeMapper;
    private ConfigService configService;
    private InviteController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        inviteCodeMapper = mock(InviteCodeMapper.class);
        configService = mock(ConfigService.class);
        controller = new InviteController();
        ReflectionTestUtils.setField(controller, "inviteCodeMapper", inviteCodeMapper);
        ReflectionTestUtils.setField(controller, "commissionLogMapper", mock(CommissionLogMapper.class));
        ReflectionTestUtils.setField(controller, "orderMapper", mock(OrderMapper.class));
        ReflectionTestUtils.setField(controller, "userMapper", mock(UserMapper.class));
        ReflectionTestUtils.setField(controller, "configService", configService);

        User user = new User();
        user.setId(7L);
        request = mock(HttpServletRequest.class);
        when(request.getAttribute("user")).thenReturn(user);
    }

    @Test
    void save_rejectsWhenCountReachesConfigLimit() {
        when(configService.getInviteGenLimit()).thenReturn(2);
        when(inviteCodeMapper.selectCount(any())).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.save(request));
        assertEquals("已达到邀请码生成数量上限", ex.getMessage());
        verify(inviteCodeMapper, never()).insert(any());
    }

    @Test
    void save_allowsWhenBelowConfigLimit() {
        when(configService.getInviteGenLimit()).thenReturn(3);
        when(inviteCodeMapper.selectCount(any())).thenReturn(2L);
        when(inviteCodeMapper.insert(any())).thenReturn(1);

        assertEquals(0, controller.save(request).getCode());
        verify(inviteCodeMapper).insert(any());
    }
}
