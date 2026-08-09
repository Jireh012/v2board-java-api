package com.v2board.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientApiPathFilterTest {

    private static final String DEV_KEY = "0123456789abcdef";
    private final PanelApiActionAliases aliases = PanelApiActionAliases.withKey(DEV_KEY);

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
        assertTrue(ClientApiPathFilter.isClassicGuestPayment("/api/v1/guest/payment/notify/x"));
        assertTrue(ClientApiPathFilter.isClassicGuestPayment("/api/v1/guest/payment"));
        assertFalse(ClientApiPathFilter.isClassicGuestPayment("/api/v1/guest/telegram/webhook"));
        assertFalse(ClientApiPathFilter.isClassicGuestPayment("/g/abcdefghijkl/alipay/uuid"));
    }

    @Test
    void rewritePaymentNotify_mapsPrefixMethodUuid() {
        assertEquals(
                "/api/v1/guest/payment/notify/AlipayF2F/abc-uuid",
                ClientApiPathFilter.rewritePaymentNotify("/g/abcdefghijkl/AlipayF2F/abc-uuid", "/g/abcdefghijkl"));
        assertNull(ClientApiPathFilter.rewritePaymentNotify("/g/abcdefghijkl/onlyone", "/g/abcdefghijkl"));
        assertNull(ClientApiPathFilter.rewritePaymentNotify("/g/abcdefghijkl", "/g/abcdefghijkl"));
    }

    @Test
    void rewriteAliased_mapsAliasToClassicInternalPath() {
        String userAlias = aliases.aliasFor("user", "order/fetch");
        assertEquals(
                "/api/v1/user/order/fetch",
                ClientApiPathFilter.rewriteAliased(
                        "/u/abcdefghijkl/" + userAlias, "/u/abcdefghijkl", "user", "/api/v1/user", aliases));

        String passportAlias = aliases.aliasFor("passport", "auth/login");
        assertEquals(
                "/api/v1/passport/auth/login",
                ClientApiPathFilter.rewriteAliased(
                        "/p/abcdefghijkl/" + passportAlias, "/p/abcdefghijkl", "passport", "/api/v1/passport", aliases));

        String adminAlias = aliases.aliasFor("admin", "config/fetch");
        assertEquals(
                "/api/v1/admin/config/fetch",
                ClientApiPathFilter.rewriteAliased(
                        "/a/abcdefghijkl/" + adminAlias, "/a/abcdefghijkl", "admin", "/api/v1/admin", aliases));
    }

    @Test
    void rewriteAliased_rejectsClassicActionNamesAndUnknown() {
        assertNull(ClientApiPathFilter.rewriteAliased(
                "/u/abcdefghijkl/getSubscribe", "/u/abcdefghijkl", "user", "/api/v1/user", aliases));
        assertNull(ClientApiPathFilter.rewriteAliased(
                "/u/abcdefghijkl/order/fetch", "/u/abcdefghijkl", "user", "/api/v1/user", aliases));
        assertNull(ClientApiPathFilter.rewriteAliased(
                "/u/abcdefghijkl", "/u/abcdefghijkl", "user", "/api/v1/user", aliases));
        assertNull(ClientApiPathFilter.rewriteAliased(
                "/u/other/" + aliases.aliasFor("user", "info"), "/u/abcdefghijkl", "user", "/api/v1/user", aliases));
    }
}
