package com.v2board.api.controller.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.model.ServerV2node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class V2ServerControllerTest {

    private V2ServerController controller;

    @BeforeEach
    void setUp() {
        controller = new V2ServerController();
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
    }

    @Test
    void buildV2nodeConfig_includesTrustedXffAndBaseConfig() {
        ServerV2node node = new ServerV2node();
        node.setListenIp("0.0.0.0");
        node.setServerPort(443);
        node.setNetwork("ws");
        node.setNetworkSettings("{\"path\":\"/ws\"}");
        node.setTrustedXForwardedFor(List.of("X-Forwarded-For", "CF-Connecting-IP"));
        node.setProtocol("vless");
        node.setTls(1);
        node.setTlsSettings("{\"server_name\":\"example.com\"}");
        node.setUpMbps(0);
        node.setDownMbps(0);

        Map<String, Object> serverConfig = Map.of(
                "server_push_interval", 30,
                "server_pull_interval", 45,
                "server_node_report_min_traffic", 10,
                "server_device_online_min_traffic", 5
        );

        Map<String, Object> resp = controller.buildV2nodeConfig(node, serverConfig);

        assertEquals(List.of("X-Forwarded-For", "CF-Connecting-IP"), resp.get("trusted_x_forwarded_for"));
        assertEquals("vless", resp.get("protocol"));
        assertEquals(true, resp.get("ignore_client_bandwidth"));
        @SuppressWarnings("unchecked")
        Map<String, Object> base = (Map<String, Object>) resp.get("base_config");
        assertEquals(30, base.get("push_interval"));
        assertEquals(45, base.get("pull_interval"));
        assertEquals(10, base.get("node_report_min_traffic"));
        assertEquals(5, base.get("device_online_min_traffic"));
    }

    @Test
    void isValidConfiguredNodeToken_rejectsBlankOrShort() {
        assertFalse(V2ServerController.isValidConfiguredNodeToken(null));
        assertFalse(V2ServerController.isValidConfiguredNodeToken(""));
        assertFalse(V2ServerController.isValidConfiguredNodeToken("short-token"));
        assertTrue(V2ServerController.isValidConfiguredNodeToken("abcdefghijklmnop"));
    }
}
