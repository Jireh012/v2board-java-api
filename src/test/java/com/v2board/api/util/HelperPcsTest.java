package com.v2board.api.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HelperPcsTest {

    @Test
    void uriBuildersIncludePcs() {
        Map<String, Object> tls = new HashMap<>();
        tls.put("pinned_peer_cert_sha256", "abc123");
        tls.put("server_name", "sni.example");
        tls.put("allow_insecure", 0);

        Map<String, Object> server = new HashMap<>();
        server.put("host", "1.2.3.4");
        server.put("port", 443);
        server.put("name", "n1");
        server.put("tls_settings", tls);
        server.put("server_name", "sni.example");
        server.put("network", "tcp");

        assertTrue(Helper.buildAnytlsUri("pw", server).contains("pcs=abc123"));
        assertTrue(Helper.buildTuicUri("pw", server).contains("pcs=abc123"));
        assertTrue(Helper.buildHysteria2Uri("pw", server).contains("pcs=abc123"));
        assertTrue(Helper.buildTrojanUri("pw", server).contains("pcs=abc123"));
    }
}
