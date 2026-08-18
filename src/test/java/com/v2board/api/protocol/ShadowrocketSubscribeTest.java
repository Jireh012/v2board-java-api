package com.v2board.api.protocol;

import com.v2board.api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowrocketSubscribeTest {

    @Test
    void defaultSeed_routesCnAndBilibiliToReturnGroup() throws Exception {
        User user = sampleUser();
        String conf = SurgeBuilder.buildFromContent(
                List.of(ss("US_California"), hy2Return()),
                user, "App", "https://example.com/sub", "example.com",
                read("rules/default.shadowrocket.conf"));
        assertTrue(conf.contains("[Proxy]"));
        assertTrue(conf.contains("[Rule]"));
        assertFalse(conf.matches("(?s).*^[A-Za-z0-9+/]+=*$"), "must be plaintext conf, not URI list");
        assertTrue(conf.contains("🍎 苹果服务"), "ACL4SSR groups");
        assertTrue(conf.contains("🌏 国内媒体 = select, 🏠 回国, DIRECT"));
        assertTrue(conf.contains("🎯 全球直连 = select, 🏠 回国, DIRECT"));
        assertTrue(conf.contains("GEOIP,CN,🏠 回国"));
        assertTrue(conf.contains("DOMAIN-SUFFIX,bilibili.com,🌏 国内媒体"));
        assertTrue(line(conf, "🏠 回国").contains("CN 回国节点"));
        String auto = line(conf, "♻️ 自动选择");
        assertFalse(auto.contains("CN 回国节点"));
        assertTrue(auto.contains("US_California"));
        assertTrue(conf.contains("skip-cert-verify=true"));
        assertTrue(conf.contains("sni=cn.821561.xyz"));
        assertTrue(conf.contains("obfs=salamander"));
        assertFalse(conf.contains("raw.githubusercontent.com"));
    }

    @Test
    void nodesSeed_geoipCnStillUsesReturnGroup() throws Exception {
        String conf = SurgeBuilder.buildFromContent(
                List.of(ss("LA-GIA-E"), hy2Return()),
                sampleUser(), "App", "https://x/s", "x.com",
                read("rules/nodes.shadowrocket.conf"));
        assertTrue(conf.contains("GEOIP,CN,🏠 回国"));
        assertTrue(conf.contains("FINAL,PROXY"));
    }

    private static User sampleUser() {
        User user = new User();
        user.setUuid("uuid-1");
        user.setToken("tok");
        user.setId(1L);
        user.setU(0L);
        user.setD(0L);
        user.setTransferEnable(1024L * 1024 * 1024);
        user.setExpiredAt(0L);
        return user;
    }

    private static Map<String, Object> ss(String name) {
        return Map.of(
                "type", "shadowsocks",
                "name", name,
                "host", "1.2.3.4",
                "port", 443,
                "cipher", "aes-256-gcm",
                "created_at", 0L);
    }

    private static Map<String, Object> hy2Return() {
        Map<String, Object> tls = new LinkedHashMap<>();
        tls.put("allow_insecure", "1");
        tls.put("server_name", "cn.821561.xyz");
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("type", "v2node");
        server.put("protocol", "hysteria2");
        server.put("name", "CN 回国节点");
        server.put("host", "cn.821561.xyz");
        server.put("port", "32711");
        server.put("obfs", "salamander");
        server.put("obfs_password", "obfs-secret");
        server.put("tls_settings", tls);
        return server;
    }

    private static String line(String conf, String prefix) {
        return conf.lines().filter(l -> l.startsWith(prefix)).findFirst().orElse("");
    }

    private static String read(String path) throws IOException {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
