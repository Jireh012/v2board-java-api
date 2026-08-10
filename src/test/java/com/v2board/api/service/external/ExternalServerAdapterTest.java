package com.v2board.api.service.external;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalServerAdapterTest {

    @Test
    void resolve_vlessReality_setsTlsMode2AndCredential() {
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", "vless");
        outbound.put("tag", "old");
        outbound.put("server", "1.2.3.4");
        outbound.put("server_port", 443);
        outbound.put("uuid", "u-1");
        outbound.put("flow", "xtls-rprx-vision");
        Map<String, Object> tls = new LinkedHashMap<>();
        tls.put("enabled", true);
        tls.put("server_name", "www.example.com");
        Map<String, Object> reality = new LinkedHashMap<>();
        reality.put("enabled", true);
        reality.put("public_key", "pk");
        reality.put("short_id", "abcd");
        tls.put("reality", reality);
        outbound.put("tls", tls);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "external");
        item.put("external", true);
        item.put("name", "⚠️ DE1");
        item.put("singbox_outbound", outbound);

        ExternalServerAdapter.Resolved resolved = ExternalServerAdapter.resolve(item);
        assertNotNull(resolved);
        assertEquals("u-1", resolved.credential());
        assertEquals("⚠️ DE1", resolved.server().get("name"));
        assertEquals("vless", resolved.server().get("type"));
        assertEquals(2, resolved.server().get("tls"));
        assertEquals("www.example.com", resolved.server().get("server_name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> tlsSettings = (Map<String, Object>) resolved.server().get("tls_settings");
        assertEquals("pk", tlsSettings.get("public_key"));
        assertEquals("abcd", tlsSettings.get("short_id"));
        assertEquals("xtls-rprx-vision", resolved.server().get("flow"));
    }

    @Test
    void resolve_hysteria2_andSs() {
        Map<String, Object> hy = Map.of(
                "type", "hysteria2",
                "tag", "hy",
                "server", "9.9.9.9",
                "server_port", 8443,
                "password", "pw-hy",
                "tls", Map.of("enabled", true, "server_name", "sni.test", "insecure", true)
        );
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("external", true);
        item.put("type", "external");
        item.put("name", "HY");
        item.put("singbox_outbound", hy);
        ExternalServerAdapter.Resolved r = ExternalServerAdapter.resolve(item);
        assertNotNull(r);
        assertEquals("hysteria2", r.server().get("type"));
        assertEquals("pw-hy", r.credential());
        assertEquals(1, r.server().get("insecure"));

        Map<String, Object> ss = Map.of(
                "type", "shadowsocks",
                "tag", "ss",
                "server", "8.8.8.8",
                "server_port", 1000,
                "method", "aes-256-gcm",
                "password", "ss-pw"
        );
        item.put("singbox_outbound", ss);
        item.put("name", "SS1");
        r = ExternalServerAdapter.resolve(item);
        assertNotNull(r);
        assertEquals("shadowsocks", r.server().get("type"));
        assertEquals("aes-256-gcm", r.server().get("cipher"));
        assertEquals("ss-pw", r.credential());
    }

    @Test
    void resolve_nonExternal_returnsNull() {
        assertNull(ExternalServerAdapter.resolve(Map.of("type", "vmess", "name", "n")));
    }

    @Test
    void resolve_wsTransport() {
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", "vmess");
        outbound.put("server", "1.1.1.1");
        outbound.put("server_port", 80);
        outbound.put("uuid", "vm-uuid");
        outbound.put("transport", Map.of(
                "type", "ws",
                "path", "/ray",
                "headers", Map.of("Host", "cdn.example.com")
        ));
        Map<String, Object> item = Map.of(
                "external", true,
                "type", "external",
                "name", "WS",
                "singbox_outbound", outbound
        );
        ExternalServerAdapter.Resolved r = ExternalServerAdapter.resolve(item);
        assertNotNull(r);
        assertEquals("ws", r.server().get("network"));
        assertTrue(String.valueOf(r.server().get("network_settings")).contains("/ray"));
    }
}
