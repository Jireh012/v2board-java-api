package com.v2board.api.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.controller.ClientController;
import com.v2board.api.mapper.SystemConfigMapper;
import com.v2board.api.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscribeRouteRegistrarTest {

    @Test
    void normalizePath_defaultsAndSlashes() {
        assertEquals("/api/v1/client/subscribe", SubscribeRouteRegistrar.normalizePath(null));
        assertEquals("/api/v1/client/subscribe", SubscribeRouteRegistrar.normalizePath("  "));
        assertEquals("/api/v1/client/subscribe", SubscribeRouteRegistrar.normalizePath("/api/v1/client/subscribe"));
        assertEquals("/s", SubscribeRouteRegistrar.normalizePath("s"));
        assertEquals("/custom/sub", SubscribeRouteRegistrar.normalizePath(" custom/sub "));
    }

    @Test
    void refresh_customPathUnregistersPreviousDefaultMapping() {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ConfigService configService = new ConfigService();
        ReflectionTestUtils.setField(configService, "systemConfigMapper", mapper);
        ReflectionTestUtils.setField(configService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(configService, "appName", "Panel");
        ReflectionTestUtils.setField(configService, "phpConfigPath", "");
        ReflectionTestUtils.setField(configService, "appUrl", "");
        ReflectionTestUtils.setField(configService, "subscribeUrl", "");
        ReflectionTestUtils.setField(configService, "subscribePath", "/s/secret");
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

        RecordingHandlerMapping handlerMapping = new RecordingHandlerMapping();

        SubscribeRouteRegistrar registrar = new SubscribeRouteRegistrar();
        ReflectionTestUtils.setField(registrar, "configService", configService);
        ReflectionTestUtils.setField(registrar, "handlerMapping", handlerMapping);
        ReflectionTestUtils.setField(registrar, "clientController", new ClientController());

        RequestMappingInfo previous = RequestMappingInfo.paths("/api/v1/client/subscribe").build();
        ReflectionTestUtils.setField(registrar, "currentMapping", previous);
        ReflectionTestUtils.setField(registrar, "currentPath", "/api/v1/client/subscribe");

        registrar.refresh();

        assertEquals(1, handlerMapping.unregistered.size());
        assertEquals(previous, handlerMapping.unregistered.get(0));
        assertEquals(1, handlerMapping.registered.size());
        assertEquals("/s/secret", ReflectionTestUtils.getField(registrar, "currentPath"));
        String registeredPaths = String.valueOf(handlerMapping.registered.get(0).getPatternValues());
        assertTrue(registeredPaths.contains("/s/secret"));
        assertFalse(registeredPaths.contains("/api/v1/client/subscribe"));
    }

    /** Avoid Mockito inline mock of Spring MVC classes (Java 26 Byte Buddy limit). */
    static final class RecordingHandlerMapping extends RequestMappingHandlerMapping {
        final List<RequestMappingInfo> registered = new ArrayList<>();
        final List<RequestMappingInfo> unregistered = new ArrayList<>();

        @Override
        public void registerMapping(RequestMappingInfo mapping, Object handler, Method method) {
            registered.add(mapping);
        }

        @Override
        public void unregisterMapping(RequestMappingInfo mapping) {
            unregistered.add(mapping);
        }
    }
}
