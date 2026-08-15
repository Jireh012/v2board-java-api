package com.v2board.api.service.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareUriConverterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void trojanToUri_keepsInsecureAndWebsocket() {
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", "trojan");
        outbound.put("tag", "TR-WS");
        outbound.put("server", "1.2.3.4");
        outbound.put("server_port", 443);
        outbound.put("password", "pw");
        outbound.put("tls", Map.of("enabled", true, "server_name", "sni.example", "insecure", true));
        outbound.put("transport", Map.of(
                "type", "ws",
                "path", "/ws",
                "headers", Map.of("Host", "sni.example")));

        String uri = ShareUriConverter.singboxToUri(outbound);
        assertTrue(uri.startsWith("trojan://pw@1.2.3.4:443?"));
        assertTrue(uri.contains("allowInsecure=1"));
        assertTrue(uri.contains("sni=sni.example"));
        assertTrue(uri.contains("type=ws"));
        assertTrue(uri.contains("path=" + enc("/ws")) || uri.contains("path=%2Fws"));
        assertTrue(uri.contains("host=sni.example"));
    }

    @Test
    void vlessToUri_keepsRealityFingerprintAndFlow() {
        Map<String, Object> tls = new LinkedHashMap<>();
        tls.put("enabled", true);
        tls.put("server_name", "www.apple.com");
        tls.put("utls", Map.of("enabled", true, "fingerprint", "chrome"));
        tls.put("reality", Map.of("enabled", true, "public_key", "pk", "short_id", "abcd"));

        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", "vless");
        outbound.put("tag", "VL");
        outbound.put("server", "8.8.8.8");
        outbound.put("server_port", 443);
        outbound.put("uuid", "u-1");
        outbound.put("flow", "xtls-rprx-vision");
        outbound.put("tls", tls);

        String uri = ShareUriConverter.singboxToUri(outbound);
        assertTrue(uri.contains("security=reality"));
        assertTrue(uri.contains("pbk=pk"));
        assertTrue(uri.contains("sid=abcd"));
        assertTrue(uri.contains("fp=chrome"));
        assertTrue(uri.contains("flow=xtls-rprx-vision"));
        assertTrue(uri.contains("sni=www.apple.com"));
    }

    @Test
    void hysteria2ToUri_keepsInsecureAndObfs() {
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", "hysteria2");
        outbound.put("tag", "HY2");
        outbound.put("server", "9.9.9.9");
        outbound.put("server_port", 8443);
        outbound.put("password", "secret");
        outbound.put("tls", Map.of("enabled", true, "server_name", "hy.example", "insecure", true));
        outbound.put("obfs", Map.of("type", "salamander", "password", "obfs-pw"));

        String uri = ShareUriConverter.singboxToUri(outbound);
        assertTrue(uri.startsWith("hysteria2://"));
        assertTrue(uri.contains("insecure=1"));
        assertTrue(uri.contains("sni=hy.example"));
        assertTrue(uri.contains("obfs=salamander"));
        assertTrue(uri.contains("obfs-password="));
    }

    @Test
    void vmessToUri_keepsSkipCertAndWsHost() throws Exception {
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", "vmess");
        outbound.put("tag", "VM");
        outbound.put("server", "7.7.7.7");
        outbound.put("server_port", 80);
        outbound.put("uuid", "vm-id");
        outbound.put("tls", Map.of("enabled", true, "server_name", "vm.example", "insecure", true));
        outbound.put("transport", Map.of(
                "type", "ws",
                "path", "/v",
                "headers", Map.of("Host", "vm.example")));

        String uri = ShareUriConverter.singboxToUri(outbound);
        assertTrue(uri.startsWith("vmess://"));
        String json = new String(Base64.getDecoder().decode(uri.substring("vmess://".length())),
                StandardCharsets.UTF_8);
        Map<String, Object> cfg = MAPPER.readValue(json, new TypeReference<>() {});
        assertEquals("tls", cfg.get("tls"));
        assertEquals("vm.example", cfg.get("sni"));
        assertEquals("ws", cfg.get("net"));
        assertEquals("/v", cfg.get("path"));
        assertEquals("vm.example", cfg.get("host"));
        assertEquals(true, cfg.get("skip-cert-verify"));
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
