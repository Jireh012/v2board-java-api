package com.v2board.api.protocol;

import com.v2board.api.model.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurgeBuilderTest {

    @Test
    void build_acl4ssrTemplate_hasPolicyGroupsAndNoGithubRules() {
        User user = sampleUser();
        List<Map<String, Object>> servers = List.of(
                ss("香港01"),
                ss("日本樱花")
        );
        String conf = SurgeBuilder.build(servers, user, "V2Board", "https://example.com/sub", "example.com");
        assertTrue(conf.contains("🚀 节点选择"), "main select missing");
        assertTrue(conf.contains("🍎 苹果服务"), "apple group missing");
        assertTrue(conf.contains("📹 油管视频"), "youtube group missing");
        assertTrue(conf.contains("🎥 奈飞视频"), "netflix group missing");
        assertTrue(conf.contains("🌍 国外媒体"), "foreign media missing");
        assertTrue(conf.contains("📲 电报消息"), "telegram missing");
        assertTrue(conf.contains("💬 Ai平台"), "ai missing");
        assertTrue(conf.contains("🐟 漏网之鱼"), "final group missing");
        assertTrue(conf.contains("FINAL,🐟 漏网之鱼"), "final rule missing");
        assertFalse(conf.contains("raw.githubusercontent.com"), "must not depend on GitHub raw");
        assertFalse(conf.toUpperCase().contains("RULE-SET,"), "must not use RULE-SET");
        assertTrue(conf.contains("香港01"));
        assertTrue(conf.contains("日本樱花"));
    }

    @Test
    void regionFilter_hkFilledAndEmptyRegionsPruned() {
        User user = sampleUser();
        List<Map<String, Object>> servers = List.of(
                ss("香港01"),
                ss("美国01")
        );
        String conf = SurgeBuilder.build(servers, user, "V2Board", "https://example.com/sub", "example.com");
        assertTrue(conf.contains("🇭🇰 香港"));
        int hk = conf.indexOf("🇭🇰 香港 = select");
        assertTrue(hk > 0);
        String hkLine = conf.substring(hk, conf.indexOf('\n', hk));
        assertTrue(hkLine.contains("香港01"));
        assertFalse(hkLine.contains("美国01"));

        // 无韩/台/新节点时对应空组应被删除
        assertFalse(conf.contains("🇰🇷 韩国 = select"));
        assertFalse(conf.contains("🇨🇳 台湾 = select"));
        assertFalse(conf.contains("🇸🇬 狮城 = select"));

        // 主组引用中也不再残留已删地区
        int main = conf.indexOf("🚀 节点选择 = select");
        String mainLine = conf.substring(main, conf.indexOf('\n', main));
        assertFalse(mainLine.contains("🇰🇷 韩国"));
        assertTrue(mainLine.contains("🇭🇰 香港"));
        assertTrue(mainLine.contains("🇺🇲 美国"));
    }

    @Test
    void buildFromContent_usesProvidedTemplate() {
        User user = sampleUser();
        String template = """
                [Proxy]
                $proxies
                [Proxy Group]
                Proxy = select, $proxy_group
                [Rule]
                FINAL,Proxy
                """;
        String conf = SurgeBuilder.buildFromContent(
                List.of(ss("node-a")), user, "App", "https://x/sub", "x.com", template);
        assertTrue(conf.contains("node-a"));
        assertTrue(conf.contains("Proxy = select, node-a"));
    }

    @Test
    void build_externalAndHysteria2_emitsProxyLinesWithNodePassword() {
        User user = sampleUser();
        Map<String, Object> outbound = Map.of(
                "type", "shadowsocks",
                "tag", "old",
                "server", "8.8.8.8",
                "server_port", 10086,
                "method", "aes-256-gcm",
                "password", "node-ss-pw"
        );
        Map<String, Object> external = Map.of(
                "type", "external",
                "external", true,
                "name", "⚠️ 香港外链",
                "singbox_outbound", outbound
        );
        Map<String, Object> hy2 = Map.of(
                "type", "hysteria2",
                "name", "面板HY2",
                "host", "9.9.9.9",
                "port", 8443,
                "server_name", "hy.example.com",
                "insecure", 1
        );
        String conf = SurgeBuilder.build(List.of(external, hy2), user, "App", "https://x/s", "x.com");
        assertTrue(conf.lines().anyMatch(l -> l.contains("⚠️ 香港外链=ss") && l.contains("password=node-ss-pw")),
                "external ss must use node password");
        assertTrue(conf.lines().anyMatch(l -> l.contains("面板HY2=hysteria2") && l.contains("password=uuid-1")),
                "panel hy2 uses user uuid");
        assertTrue(conf.contains("9.9.9.9"));
    }

    private static User sampleUser() {
        User user = new User();
        user.setUuid("uuid-1");
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
                "created_at", 0L
        );
    }
}
