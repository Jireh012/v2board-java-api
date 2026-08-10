package com.v2board.api.protocol;

import com.v2board.api.model.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfboardBuilderTest {

    @Test
    void build_acl4ssrTemplate_hasPolicyGroupsAndNoGithubRules() {
        User user = sampleUser();
        List<Map<String, Object>> servers = List.of(
                ss("香港01"),
                ss("日本樱花")
        );
        String conf = SurfboardBuilder.build(servers, user, "V2Board", "https://example.com/sub", "example.com");
        assertTrue(conf.contains("🚀 节点选择"), "main select missing");
        assertTrue(conf.contains("🍎 苹果服务"), "apple group missing");
        assertTrue(conf.contains("📹 油管视频"), "youtube group missing");
        assertTrue(conf.contains("🌍 国外媒体"), "foreign media missing");
        assertTrue(conf.contains("🐟 漏网之鱼"), "final group missing");
        assertTrue(conf.contains("FINAL,🐟 漏网之鱼"), "final rule missing");
        assertFalse(conf.contains("raw.githubusercontent.com"), "must not depend on GitHub raw");
        assertFalse(conf.toUpperCase().contains("RULE-SET,"), "must not use RULE-SET");
        assertTrue(conf.contains("香港01"));
    }

    @Test
    void regionFilter_hkFilledAndEmptyRegionsPruned() {
        User user = sampleUser();
        String conf = SurfboardBuilder.build(List.of(ss("香港01"), ss("美国01")), user,
                "V2Board", "https://example.com/sub", "example.com");
        int hk = conf.indexOf("🇭🇰 香港 = select");
        assertTrue(hk > 0);
        String hkLine = conf.substring(hk, conf.indexOf('\n', hk));
        assertTrue(hkLine.contains("香港01"));
        assertFalse(conf.contains("🇰🇷 韩国 = select"));
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
