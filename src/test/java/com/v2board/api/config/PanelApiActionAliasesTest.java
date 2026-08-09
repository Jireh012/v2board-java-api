package com.v2board.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PanelApiActionAliasesTest {

    private static final String DEV_KEY = "0123456789abcdef";

    @Test
    void deriveAlias_isStableForDevKey() {
        // Locked vector — must match frontend paths.ts
        assertEquals(
                "59327a5e63c5",
                PanelApiActionAliases.deriveAlias(DEV_KEY, "user", "getSubscribe"),
                "If this fails, recompute expected with SHA-256(key\\0zone\\0classicRel) hex[0:12] and update FE");
    }

    @Test
    void resolve_roundTripsCatalogSample() {
        PanelApiActionAliases aliases = PanelApiActionAliases.withKey(DEV_KEY);
        String alias = aliases.aliasFor("user", "getSubscribe");
        assertEquals("getSubscribe", aliases.resolveClassicRel("user", alias));
        assertEquals("order/fetch", aliases.resolveClassicRel("user", aliases.aliasFor("user", "order/fetch")));
        assertEquals("auth/login", aliases.resolveClassicRel("passport", aliases.aliasFor("passport", "auth/login")));
        assertEquals("config/fetch", aliases.resolveClassicRel("admin", aliases.aliasFor("admin", "config/fetch")));
        assertEquals(
                "server/vmess/save",
                aliases.resolveClassicRel("admin", aliases.aliasFor("admin", "server/vmess/save")));
    }

    @Test
    void differentZonesOrPaths_differ() {
        String a = PanelApiActionAliases.deriveAlias(DEV_KEY, "user", "info");
        String b = PanelApiActionAliases.deriveAlias(DEV_KEY, "admin", "info");
        String c = PanelApiActionAliases.deriveAlias(DEV_KEY, "user", "getSubscribe");
        assertNotEquals(a, c);
        // admin has no "info" leaf in catalog; still different zone input
        assertNotEquals(a, b);
    }

    @Test
    void unknownAlias_returnsNull() {
        PanelApiActionAliases aliases = PanelApiActionAliases.withKey(DEV_KEY);
        assertNull(aliases.resolveClassicRel("user", "000000000000"));
        assertNull(aliases.resolveClassicRel("user", "getSubscribe"));
    }

    @Test
    void normalizeClassicRel_stripsSlashes() {
        assertEquals("order/fetch", PanelApiActionAliases.normalizeClassicRel("/order/fetch/"));
        assertEquals("getSubscribe", PanelApiActionAliases.normalizeClassicRel("getSubscribe"));
    }

    @Test
    void catalog_buildsWithoutCollision() {
        assertNotNull(PanelApiActionAliases.withKey(DEV_KEY));
        assertNotNull(PanelApiActionAliases.withKey("another-key-16b!"));
    }
}
