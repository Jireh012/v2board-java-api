package com.v2board.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerV2nodeJacksonNamingTest {

    private static ObjectMapper snakeMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return mapper;
    }

    @Test
    void trustedXForwardedFor_roundTripsWithPhpWireName() throws Exception {
        ObjectMapper mapper = snakeMapper();
        ServerV2node node = new ServerV2node();
        node.setName("n1");
        node.setTrustedXForwardedFor(List.of("X-Forwarded-For", "CF-Connecting-IP"));

        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.convertValue(node, Map.class);
        assertTrue(map.containsKey("trusted_x_forwarded_for"));
        assertEquals(List.of("X-Forwarded-For", "CF-Connecting-IP"), map.get("trusted_x_forwarded_for"));

        // Admin UI / PHP wire name must deserialize (not Jackson acronym form trusted_xforwarded_for)
        Map<String, Object> body = Map.of(
                "name", "n2",
                "trusted_x_forwarded_for", List.of("X-Real-IP"));
        ServerV2node parsed = mapper.convertValue(body, ServerV2node.class);
        assertEquals(List.of("X-Real-IP"), parsed.getTrustedXForwardedFor());
    }
}
