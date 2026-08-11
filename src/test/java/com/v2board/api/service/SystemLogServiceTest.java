package com.v2board.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.mapper.SystemLogMapper;
import com.v2board.api.model.SystemLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SystemLogServiceTest {

    private SystemLogMapper mapper;
    private SystemLogService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SystemLogMapper.class);
        service = new SystemLogService();
        ReflectionTestUtils.setField(service, "systemLogMapper", mapper);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void buildRow_nonHttp_usesPlaceholders() {
        SystemLog row = service.buildRow("ERROR", "com.example.Job", "boom", null, null, null, null);
        assertEquals("-", row.getUri());
        assertEquals("SCHEDULE", row.getMethod());
        assertEquals("ERROR", row.getLevel());
        assertEquals("boom", row.getTitle());
        assertNotNull(row.getContext());
        assertTrue(row.getContext().contains("com.example.Job"));
    }

    @Test
    void buildRow_http_metaAndQueryRedaction() {
        var meta = new SystemLogService.RequestMeta(
                "api.example.com",
                "/api/v1/user/info",
                "POST",
                "1.2.3.4",
                "token=secret123&foo=bar");
        SystemLog row = service.buildRow("ERROR", "ctrl", "fail", "RuntimeException", "x", "stack", meta);
        assertEquals("/api/v1/user/info", row.getUri());
        assertEquals("POST", row.getMethod());
        assertEquals("1.2.3.4", row.getIp());
        assertEquals("api.example.com", row.getHost());
        assertNotNull(row.getData());
        assertTrue(row.getData().contains("token=***"));
        assertTrue(row.getData().contains("foo=bar"));
        assertFalse(row.getData().contains("secret123"));
    }

    @Test
    void buildRow_truncatesTitleAndContext() {
        String longTitle = "t".repeat(SystemLogService.TITLE_MAX + 100);
        String longStack = "s".repeat(SystemLogService.CONTEXT_MAX + 2000);
        SystemLog row = service.buildRow("ERROR", "L", longTitle, "E", "m", longStack, null);
        assertEquals(SystemLogService.TITLE_MAX, row.getTitle().length());
        assertTrue(row.getContext().length() <= SystemLogService.CONTEXT_MAX);
    }

    @Test
    void redactJsonLike_masksSensitiveKeys() throws Exception {
        String json = service.redactJsonLike("{\"password\":\"p\",\"Authorization\":\"Bearer x\",\"ok\":1}");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = new ObjectMapper().readValue(json, Map.class);
        assertEquals("***", map.get("password"));
        assertEquals("***", map.get("Authorization"));
        assertEquals(1, map.get("ok"));
    }

    @Test
    void persistAsync_insertFailure_doesNotThrow() {
        doThrow(new RuntimeException("db down")).when(mapper).insert(any());
        SystemLog row = service.buildRow("ERROR", "L", "msg", null, null, null, null);
        service.persistAsync(row); // must not propagate
        verify(mapper, times(1)).insert(any());
    }
}
