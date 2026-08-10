package com.v2board.api.protocol;

import com.v2board.api.model.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoonBuilderTest {

    @Test
    void build_acl4ssrTemplate_hasPolicyGroupsAndNoRemoteRules() {
        User user = sampleUser();
        String conf = LoonBuilder.build(
                List.of(ss("香港01"), ss("日本樱花")), user, "V2Board", "https://example.com/sub", "example.com");
        assertTrue(conf.contains("🚀 节点选择"), "main select missing");
        assertTrue(conf.contains("🍎 苹果服务"), "apple group missing");
        assertTrue(conf.contains("📹 油管视频"), "youtube group missing");
        assertTrue(conf.contains("🎥 奈飞视频"), "netflix group missing");
        assertTrue(conf.contains("🌍 国外媒体"), "foreign media missing");
        assertTrue(conf.contains("📲 电报消息"), "telegram missing");
        assertTrue(conf.contains("💬 Ai平台"), "ai missing");
        assertTrue(conf.contains("🐟 漏网之鱼"), "final group missing");
        assertTrue(conf.contains("FINAL,🐟 漏网之鱼"), "final rule missing");
        assertTrue(conf.contains("[Proxy]"));
        assertTrue(conf.contains("[Proxy Group]"));
        assertTrue(conf.contains("[Rule]"));
        assertFalse(conf.contains("raw.githubusercontent.com"), "must not depend on GitHub raw");
        assertFalse(conf.toUpperCase().contains("RULE-SET,"), "must not use RULE-SET");
        assertTrue(conf.contains("香港01=Shadowsocks"));
        assertTrue(conf.contains("日本樱花=Shadowsocks"));
    }

    @Test
    void regionFilter_hkFilledAndEmptyRegionsPruned() {
        User user = sampleUser();
        String conf = LoonBuilder.build(
                List.of(ss("香港01"), ss("美国01")), user, "V2Board", "https://example.com/sub", "example.com");
        assertTrue(conf.contains("🇭🇰 香港"));
        int hk = conf.indexOf("🇭🇰 香港 = select");
        assertTrue(hk > 0);
        String hkLine = conf.substring(hk, conf.indexOf('\n', hk));
        assertTrue(hkLine.contains("香港01"));
        assertFalse(hkLine.contains("美国01"));

        assertFalse(conf.contains("🇰🇷 韩国 = select"));
        assertFalse(conf.contains("🇨🇳 台湾 = select"));
        assertFalse(conf.contains("🇸🇬 狮城 = select"));

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
        String conf = LoonBuilder.buildFromContent(
                List.of(ss("node-a")), user, "App", "https://x/sub", "x.com", template);
        assertTrue(conf.contains("node-a"));
        assertTrue(conf.contains("Proxy = select, node-a"));
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
