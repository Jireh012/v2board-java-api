package com.v2board.api.config;

import com.v2board.api.controller.server.UniProxyController;
import com.v2board.api.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeApiRouteRegistrarTest {

    @Test
    void refresh_registersFiveActionsUnderPrefix() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        when(configService.ensureServerApiPrefix()).thenReturn("/n/abcdefghijkl");

        RecordingHandlerMapping handlerMapping = new RecordingHandlerMapping();
        NodeApiRouteRegistrar registrar = new NodeApiRouteRegistrar();
        ReflectionTestUtils.setField(registrar, "configService", configService);
        ReflectionTestUtils.setField(registrar, "handlerMapping", handlerMapping);
        ReflectionTestUtils.setField(registrar, "uniProxyController", new UniProxyController());

        registrar.refresh();

        assertEquals(5, handlerMapping.registered.size());
        String all = handlerMapping.registered.stream()
                .map(m -> String.valueOf(m.getPatternValues()))
                .reduce("", (a, b) -> a + b);
        assertTrue(all.contains("/n/abcdefghijkl/c"));
        assertTrue(all.contains("/n/abcdefghijkl/u"));
        assertTrue(all.contains("/n/abcdefghijkl/p"));
        assertTrue(all.contains("/n/abcdefghijkl/a"));
        assertTrue(all.contains("/n/abcdefghijkl/l"));
        assertFalse(all.contains("UniProxy"));
        assertFalse(all.contains("/api/v2/server"));
    }

    @Test
    void refresh_prefixChangeUnregistersOldRoutes() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        when(configService.ensureServerApiPrefix())
                .thenReturn("/n/oldprefixxxxx")
                .thenReturn("/n/newprefixxxxx");

        RecordingHandlerMapping handlerMapping = new RecordingHandlerMapping();
        NodeApiRouteRegistrar registrar = new NodeApiRouteRegistrar();
        ReflectionTestUtils.setField(registrar, "configService", configService);
        ReflectionTestUtils.setField(registrar, "handlerMapping", handlerMapping);
        ReflectionTestUtils.setField(registrar, "uniProxyController", new UniProxyController());

        registrar.refresh();
        assertEquals(5, handlerMapping.registered.size());

        registrar.refresh();
        assertEquals(5, handlerMapping.unregistered.size());
        assertEquals(10, handlerMapping.registered.size());
        String latest = String.valueOf(handlerMapping.registered.get(handlerMapping.registered.size() - 1).getPatternValues());
        assertTrue(latest.contains("/n/newprefixxxxx"));
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
