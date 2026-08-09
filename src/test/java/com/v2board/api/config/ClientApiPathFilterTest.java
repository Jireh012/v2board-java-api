package com.v2board.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientApiPathFilterTest {

    @Test
    void isClassicPanelApi_matchesClassicBases() {
        assertTrue(ClientApiPathFilter.isClassicPanelApi("/api/v1/user/info"));
        assertTrue(ClientApiPathFilter.isClassicPanelApi("/api/v1/passport/auth/login"));
        assertTrue(ClientApiPathFilter.isClassicPanelApi("/api/v1/user"));
        assertTrue(ClientApiPathFilter.isClassicPanelApi("/api/v1/admin/config/fetch"));
        assertTrue(ClientApiPathFilter.isClassicPanelApi("/api/v1/admin"));
        assertFalse(ClientApiPathFilter.isClassicPanelApi("/u/abcdefghijkl/info"));
        assertFalse(ClientApiPathFilter.isClassicPanelApi("/a/abcdefghijkl/config/fetch"));
        assertFalse(ClientApiPathFilter.isClassicPanelApi("/api/v1/guest/payment/notify/x"));
        assertFalse(ClientApiPathFilter.isClassicPanelApi("/api/v1/guest/telegram/webhook"));
    }

    @Test
    void tryRewrite_mapsPrefixToClassicInternalPath() {
        assertEquals("/api/v1/user/order/fetch",
                ClientApiPathFilter.tryRewrite("/u/abcdefghijkl/order/fetch", "/u/abcdefghijkl", "/api/v1/user"));
        assertEquals("/api/v1/passport/auth/login",
                ClientApiPathFilter.tryRewrite("/p/abcdefghijkl/auth/login", "/p/abcdefghijkl", "/api/v1/passport"));
        assertEquals("/api/v1/admin/config/fetch",
                ClientApiPathFilter.tryRewrite("/a/abcdefghijkl/config/fetch", "/a/abcdefghijkl", "/api/v1/admin"));
        assertEquals("/api/v1/admin",
                ClientApiPathFilter.tryRewrite("/a/abcdefghijkl", "/a/abcdefghijkl", "/api/v1/admin"));
        assertEquals("/api/v1/user",
                ClientApiPathFilter.tryRewrite("/u/abcdefghijkl", "/u/abcdefghijkl", "/api/v1/user"));
        assertNull(ClientApiPathFilter.tryRewrite("/u/other/info", "/u/abcdefghijkl", "/api/v1/user"));
        assertNull(ClientApiPathFilter.tryRewrite("/api/v1/guest/payment/notify/x", "/u/abcdefghijkl", "/api/v1/user"));
    }
}
