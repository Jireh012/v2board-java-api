package com.v2board.api.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalClientDeliveryTest {

    @Test
    void uriClients_rebuildFromOutboundNotStaleShareUri() {
        GeneralHandler general = new GeneralHandler();
        ReflectionTestUtils.setField(general, "objectMapper", new ObjectMapper());
        String uri = general.buildPlainUriForServer("user-uuid", trojanWsExternal(true));
        assertTrue(uri.contains("allowInsecure=1"));
        assertTrue(uri.contains("type=ws"));
        assertTrue(uri.contains("path="));
        assertFalse(uri.contains("user-uuid"));
    }

    @Test
    void clashFamily_rebuildsFromOutboundNotStaleClashProxy() {
        String yaml = ClashMetaBuilder.buildFromContent(
                List.of(trojanWsExternal(true)), "user-uuid", "App",
                "proxies: []\nproxy-groups: []\nrules:\n  - MATCH,DIRECT\n");
        assertTrue(yaml.contains("skip-cert-verify: true") || yaml.contains("skip-cert-verify:true"));
        assertTrue(yaml.contains("network: ws") || yaml.contains("network:ws"));
        assertTrue(yaml.contains("/ws"));
        assertFalse(yaml.contains("stale-only"));
    }

    @Test
    void surgeLoonQx_keepInsecureAndWsFromOutbound() {
        User user = user();
        Map<String, Object> node = trojanWsExternal(false);

        String surge = SurgeBuilder.buildFromContent(List.of(node), user, "App", "https://x/s", "x.com",
                "[Proxy]\n$proxies\n");
        assertTrue(surge.contains("skip-cert-verify=true"));
        assertTrue(surge.contains("ws=true"));
        assertTrue(surge.contains("ws-path=/ws"));

        String loon = LoonBuilder.buildFromContent(List.of(node), user, "App", "https://x/s", "x.com",
                "[Proxy]\n$proxies\n");
        assertTrue(loon.contains("skip-cert-verify=true"));
        assertTrue(loon.contains("ws=true") || loon.contains("ws-path=/ws"));

        String qx = QuantumultXBuilder.buildFromContent(List.of(node), "user-uuid", "x.com",
                "[server_local]\n$proxies\n");
        assertTrue(qx.contains("tls-verification=false"));
        assertTrue(qx.contains("obfs=wss") || qx.contains("obfs-uri=/ws"));
    }

    @Test
    void surge_hysteria2_keepsObfsFromOutbound() {
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", "hysteria2");
        outbound.put("tag", "HY2");
        outbound.put("server", "9.9.9.9");
        outbound.put("server_port", 8443);
        outbound.put("password", "hy-pw");
        outbound.put("tls", Map.of("enabled", true, "server_name", "hy.example", "insecure", true));
        outbound.put("obfs", Map.of("type", "salamander", "password", "obfs-pw"));

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("type", "external");
        server.put("external", true);
        server.put("name", "⚠️ HY2");
        server.put("singbox_outbound", outbound);

        String surge = SurgeBuilder.buildFromContent(List.of(server), user(), "App", "https://x/s", "x.com",
                "[Proxy]\n$proxies\n");
        assertTrue(surge.contains("skip-cert-verify=true"));
        assertTrue(surge.contains("obfs=salamander"));
        assertTrue(surge.contains("obfs-password=obfs-pw"));
    }

    @Test
    void sagerNet_skipsExternalHysteria2() {
        Map<String, Object> outbound = Map.of(
                "type", "hysteria2",
                "tag", "HY2",
                "server", "9.9.9.9",
                "server_port", 8443,
                "password", "pw");
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("type", "external");
        server.put("external", true);
        server.put("singbox_outbound", outbound);
        assertFalse(SagerNetHandler.includeServer(server));
        assertTrue(SagerNetHandler.includeServer(trojanWsExternal(false)));
    }

    private static Map<String, Object> trojanWsExternal(boolean staleClash) {
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", "trojan");
        outbound.put("tag", "TR");
        outbound.put("server", "1.2.3.4");
        outbound.put("server_port", 443);
        outbound.put("password", "pw");
        outbound.put("tls", Map.of("enabled", true, "server_name", "sni.example", "insecure", true));
        outbound.put("transport", Map.of(
                "type", "ws",
                "path", "/ws",
                "headers", Map.of("Host", "sni.example")));

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("type", "external");
        server.put("external", true);
        server.put("name", "⚠️ TR");
        server.put("share_uri", "trojan://pw@1.2.3.4:443?sni=sni.example#old");
        server.put("singbox_outbound", outbound);
        if (staleClash) {
            server.put("clash_proxy", Map.of(
                    "name", "stale-only",
                    "type", "trojan",
                    "server", "1.2.3.4",
                    "port", 443,
                    "password", "pw"));
        }
        return server;
    }

    private static User user() {
        User user = new User();
        user.setUuid("user-uuid");
        user.setU(0L);
        user.setD(0L);
        user.setTransferEnable(0L);
        user.setExpiredAt(0L);
        return user;
    }
}
