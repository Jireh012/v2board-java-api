package com.v2board.api.service.external;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ExternalNodeIdentityTest {

    @Test
    void logicalKey_prefersUuidOverPassword_andNormalizesHost() {
        Map<String, Object> a = outbound("vless", "Host.Example.COM", 443,
                "5dc56757-458e-4df2-9e71-2a14ce47e8af", "ignored");
        Map<String, Object> b = outbound("VLESS", "host.example.com", 443.0,
                "5dc56757-458e-4df2-9e71-2a14ce47e8af", null);
        assertEquals(
                "vless|host.example.com|443|5dc56757-458e-4df2-9e71-2a14ce47e8af",
                ExternalNodeIdentity.logicalKey(a));
        assertEquals(ExternalNodeIdentity.logicalKey(a), ExternalNodeIdentity.logicalKey(b));
    }

    @Test
    void logicalKey_usesPasswordWhenNoUuid() {
        Map<String, Object> o = outbound("shadowsocks", "1.2.3.4", 8388, null, "secret");
        assertEquals("shadowsocks|1.2.3.4|8388|secret", ExternalNodeIdentity.logicalKey(o));
    }

    @Test
    void fingerprint_ignoresTagAndTlsVariants() {
        Map<String, Object> chrome = outbound("vless", "ddc.example.com", 443, "u-1", null);
        chrome.put("tag", "DE chrome");
        chrome.put("tls", Map.of("enabled", true, "utls", Map.of("enabled", true, "fingerprint", "chrome")));

        Map<String, Object> edge = outbound("vless", "ddc.example.com", 443, "u-1", null);
        edge.put("tag", "DE edge");
        edge.put("tls", Map.of("enabled", true, "utls", Map.of("enabled", true, "fingerprint", "edge")));

        assertEquals(ExternalNodeIdentity.fingerprint(chrome), ExternalNodeIdentity.fingerprint(edge));
        assertEquals(32, ExternalNodeIdentity.fingerprint(chrome).length());
    }

    @Test
    void fingerprint_differsWhenUuidDiffers() {
        Map<String, Object> a = outbound("vless", "h", 443, "uuid-a", null);
        Map<String, Object> b = outbound("vless", "h", 443, "uuid-b", null);
        assertNotEquals(ExternalNodeIdentity.fingerprint(a), ExternalNodeIdentity.fingerprint(b));
    }

    @Test
    void applyDisplayName_updatesClashTagAndShareUri() {
        Map<String, Object> outbound = outbound("trojan", "h.example", 443, null, "pw");
        outbound.put("tag", "old");
        Map<String, Object> clash = new LinkedHashMap<>();
        clash.put("name", "old");
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("name", "old");
        server.put("singbox_outbound", outbound);
        server.put("clash_proxy", clash);
        server.put("share_uri", "trojan://pw@h.example:443#old");

        ExternalNodeIdentity.applyDisplayName(server, "node1");

        assertEquals("node1", server.get("name"));
        assertEquals("node1", clash.get("name"));
        assertEquals("node1", outbound.get("tag"));
        assertEquals("trojan://pw@h.example:443#node1", server.get("share_uri"));
    }

    private static Map<String, Object> outbound(String type, String server, Object port,
                                                String uuid, String password) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", type);
        o.put("server", server);
        o.put("server_port", port);
        if (uuid != null) {
            o.put("uuid", uuid);
        }
        if (password != null) {
            o.put("password", password);
        }
        return o;
    }
}
