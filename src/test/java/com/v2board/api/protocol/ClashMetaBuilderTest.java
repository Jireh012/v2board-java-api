package com.v2board.api.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClashMetaBuilderTest {

    @Test
    void build_preservesMainProxyGroupNamedAppName() {
        List<Map<String, Object>> servers = List.of(Map.of(
                "type", "shadowsocks",
                "name", "test-node",
                "host", "1.2.3.4",
                "port", 443,
                "cipher", "aes-256-gcm",
                "created_at", 0L
        ));
        String yaml = ClashMetaBuilder.build(servers, "uuid", "V2Board", "rules/default.clash.yaml");
        assertTrue(yaml.contains("name: V2Board"), "main proxy-group missing");
        assertTrue(yaml.contains("test-node"), "node not in config");
        assertTrue(yaml.contains("自动选择"), "url-test group ref missing");
        assertTrue(yaml.contains("IP-CIDR,1.1.1.1/32,V2Board"), "rule must target main group");
    }
}
