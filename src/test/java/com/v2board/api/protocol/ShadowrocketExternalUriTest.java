package com.v2board.api.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.service.external.ExternalServerAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowrocketExternalUriTest {

    @Test
    void rebuildsExternalUriFromOutboundNotStaleShareUri() {
        GeneralHandler general = new GeneralHandler();
        ReflectionTestUtils.setField(general, "objectMapper", new ObjectMapper());

        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", "trojan");
        outbound.put("tag", "old");
        outbound.put("server", "1.2.3.4");
        outbound.put("server_port", 443);
        outbound.put("password", "pw");
        outbound.put("tls", Map.of("enabled", true, "server_name", "sni.example", "insecure", true));
        outbound.put("transport", Map.of("type", "ws", "path", "/ws", "headers", Map.of("Host", "sni.example")));

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("type", "external");
        server.put("external", true);
        server.put("name", "⚠️ TR");
        server.put("share_uri", "trojan://pw@1.2.3.4:443?sni=sni.example#old");
        server.put("singbox_outbound", outbound);

        String uri = general.buildPlainUriForServer("user-uuid", server);
        assertTrue(uri.contains("trojan://pw@1.2.3.4:443"));
        assertTrue(uri.contains("allowInsecure=1"));
        assertTrue(uri.contains("type=ws"));
        assertFalse(uri.contains("user-uuid"));
    }

    @Test
    void externalVmess_usesShadowrocketQueryFormat() {
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", "vmess");
        outbound.put("tag", "VM");
        outbound.put("server", "7.7.7.7");
        outbound.put("server_port", 80);
        outbound.put("uuid", "vm-id");
        outbound.put("tls", Map.of("enabled", true, "server_name", "vm.example", "insecure", true));
        outbound.put("transport", Map.of("type", "ws", "path", "/v", "headers", Map.of("Host", "vm.example")));

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("type", "external");
        server.put("external", true);
        server.put("name", "⚠️ VM");
        server.put("singbox_outbound", outbound);

        ExternalServerAdapter.Resolved external = ExternalServerAdapter.resolve(server);
        String uri = ShadowrocketBuilder.buildVmess(external.credential(), external.server());
        assertTrue(uri.contains("vmess://"));
        assertTrue(uri.contains("tls=1"));
        assertTrue(uri.contains("obfs=websocket"));
        int start = uri.indexOf("vmess://") + "vmess://".length();
        int q = uri.indexOf('?', start);
        String userinfo = new String(Base64.getDecoder().decode(uri.substring(start, q)),
                StandardCharsets.UTF_8);
        assertTrue(userinfo.startsWith("auto:vm-id@7.7.7.7:80"));
        assertFalse(userinfo.contains("user-uuid"));
    }
}
