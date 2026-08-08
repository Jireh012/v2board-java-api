package com.v2board.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubscribeRouteRegistrarTest {

    @Test
    void normalizePath_defaultsAndSlashes() {
        assertEquals("/api/v1/client/subscribe", SubscribeRouteRegistrar.normalizePath(null));
        assertEquals("/api/v1/client/subscribe", SubscribeRouteRegistrar.normalizePath("  "));
        assertEquals("/api/v1/client/subscribe", SubscribeRouteRegistrar.normalizePath("/api/v1/client/subscribe"));
        assertEquals("/s", SubscribeRouteRegistrar.normalizePath("s"));
        assertEquals("/custom/sub", SubscribeRouteRegistrar.normalizePath(" custom/sub "));
    }
}
