package com.v2board.api.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumultXBuilderTest {

    @Test
    void build_acl4ssrTemplate_hasPolicyGroupsAndNoRemoteRules() {
        String conf = QuantumultXBuilder.build(List.of(ss("香港01"), ss("日本樱花")), "uuid-1", "example.com");
        assertTrue(conf.contains("🚀 节点选择"), "main select missing");
        assertTrue(conf.contains("🍎 苹果服务"), "apple group missing");
        assertTrue(conf.contains("📹 油管视频"), "youtube group missing");
        assertTrue(conf.contains("🎥 奈飞视频"), "netflix group missing");
        assertTrue(conf.contains("🌍 国外媒体"), "foreign media missing");
        assertTrue(conf.contains("📲 电报消息"), "telegram missing");
        assertTrue(conf.contains("💬 Ai平台"), "ai missing");
        assertTrue(conf.contains("🐟 漏网之鱼"), "final group missing");
        assertTrue(conf.contains("final, 🐟 漏网之鱼"), "final rule missing");
        assertTrue(conf.contains("[server_local]"));
        assertTrue(conf.contains("[policy]"));
        assertTrue(conf.contains("[filter_local]"));
        assertFalse(conf.contains("raw.githubusercontent.com"), "must not depend on GitHub raw");
        assertFalse(conf.contains("filter_remote="), "must not use filter_remote");
        assertFalse(conf.contains("cdn.jsdelivr.net"), "must not use jsDelivr rule mirrors");
        assertTrue(conf.contains("tag=香港01"));
        assertTrue(conf.contains("tag=日本樱花"));
    }

    @Test
    void regionFilter_hkFilledAndEmptyRegionsPruned() {
        String conf = QuantumultXBuilder.build(List.of(ss("香港01"), ss("美国01")), "uuid-1", "example.com");
        assertTrue(conf.contains("static=🇭🇰 香港"));
        int hk = conf.indexOf("static=🇭🇰 香港");
        assertTrue(hk > 0);
        String hkLine = conf.substring(hk, conf.indexOf('\n', hk));
        assertTrue(hkLine.contains("香港01"));
        assertFalse(hkLine.contains("美国01"));

        assertFalse(conf.contains("static=🇰🇷 韩国"));
        assertFalse(conf.contains("static=🇨🇳 台湾"));
        assertFalse(conf.contains("static=🇸🇬 狮城"));

        int main = conf.indexOf("static=🚀 节点选择");
        String mainLine = conf.substring(main, conf.indexOf('\n', main));
        assertFalse(mainLine.contains("🇰🇷 韩国"));
        assertTrue(mainLine.contains("🇭🇰 香港"));
        assertTrue(mainLine.contains("🇺🇲 美国"));
    }

    @Test
    void buildFromContent_usesProvidedTemplate() {
        String template = """
                [policy]
                static=Proxy, $proxy_group
                [server_local]
                $proxies
                [filter_local]
                final, Proxy
                """;
        String conf = QuantumultXBuilder.buildFromContent(
                List.of(ss("node-a")), "uuid-1", "x.com", template);
        assertTrue(conf.contains("tag=node-a"));
        assertTrue(conf.contains("static=Proxy, node-a"));
    }

    private static Map<String, Object> ss(String name) {
        return Map.of(
                "type", "shadowsocks",
                "name", name,
                "host", "1.2.3.4",
                "port", 443,
                "cipher", "aes-256-gcm",
                "created_at", 0L
        );
    }
}
