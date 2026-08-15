package com.v2board.api.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowrocketExternalUriTest {

    @Test
    void handle_rebuildsExternalUriFromOutboundNotStaleShareUri() {
        GeneralHandler general = new GeneralHandler();
        ReflectionTestUtils.setField(general, "objectMapper", new ObjectMapper());
        ShadowrocketHandler handler = new ShadowrocketHandler();
        ReflectionTestUtils.setField(handler, "generalHandler", general);

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

        User user = new User();
        user.setUuid("user-uuid");
        user.setU(0L);
        user.setD(0L);
        user.setTransferEnable(0L);
        user.setExpiredAt(0L);

        String decoded = new String(Base64.getDecoder().decode(handler.handle(user, List.of(server))),
                StandardCharsets.UTF_8);
        assertTrue(decoded.contains("trojan://pw@1.2.3.4:443"));
        assertTrue(decoded.contains("allowInsecure=1"));
        assertTrue(decoded.contains("type=ws"));
        assertFalse(decoded.contains("user-uuid"));
    }

    @Test
    void handle_externalVmess_usesShadowrocketQueryFormat() {
        GeneralHandler general = new GeneralHandler();
        ReflectionTestUtils.setField(general, "objectMapper", new ObjectMapper());
        ShadowrocketHandler handler = new ShadowrocketHandler();
        ReflectionTestUtils.setField(handler, "generalHandler", general);

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

        User user = new User();
        user.setUuid("user-uuid");
        user.setU(0L);
        user.setD(0L);
        user.setTransferEnable(0L);

        String decoded = new String(Base64.getDecoder().decode(handler.handle(user, List.of(server))),
                StandardCharsets.UTF_8);
        assertTrue(decoded.contains("vmess://"));
        assertTrue(decoded.contains("tls=1"));
        assertTrue(decoded.contains("obfs=websocket"));
        int start = decoded.indexOf("vmess://") + "vmess://".length();
        int q = decoded.indexOf('?', start);
        String userinfo = new String(Base64.getDecoder().decode(decoded.substring(start, q)),
                StandardCharsets.UTF_8);
        assertTrue(userinfo.startsWith("auto:vm-id@7.7.7.7:80"));
        assertFalse(userinfo.contains("user-uuid"));
    }
}
