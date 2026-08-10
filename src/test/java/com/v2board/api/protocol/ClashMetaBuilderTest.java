package com.v2board.api.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClashMetaBuilderTest {

    @Test
    void build_acl4ssrTemplate_hasPolicyGroupsAndLocalRules() {
        List<Map<String, Object>> servers = List.of(
                Map.of(
                        "type", "shadowsocks",
                        "name", "香港01",
                        "host", "1.2.3.4",
                        "port", 443,
                        "cipher", "aes-256-gcm",
                        "created_at", 0L
                ),
                Map.of(
                        "type", "shadowsocks",
                        "name", "日本樱花",
                        "host", "5.6.7.8",
                        "port", 443,
                        "cipher", "aes-256-gcm",
                        "created_at", 0L
                )
        );
        String yaml = ClashMetaBuilder.build(servers, "uuid", "V2Board", "rules/default.clash.yaml");
        assertTrue(yaml.contains("🚀 节点选择"), "main select missing");
        assertTrue(yaml.contains("🍎 苹果服务"), "apple group missing");
        assertTrue(yaml.contains("📹 油管视频"), "youtube group missing");
        assertTrue(yaml.contains("🌍 国外媒体"), "foreign media missing");
        assertTrue(yaml.contains("🐟 漏网之鱼"), "final group missing");
        assertTrue(yaml.contains("GEOSITE,category-ads-all"), "ads geosite missing");
        assertFalse(yaml.contains("GEOSITE,category-ad,"), "category-ad is not in Loyalsoldier GeoSite.dat");
        assertFalse(yaml.toLowerCase().contains("rule-providers"), "must not use rule-providers");
        assertFalse(yaml.contains("raw.githubusercontent.com"), "must not depend on GitHub raw");
        assertTrue(yaml.contains("香港01"), "hk node missing");
        assertTrue(yaml.contains("日本樱花"), "jp node missing");
    }

    @Test
    void merge_regionFilter_andPolicyOnlyNotFilledWithAllNodes() {
        List<Map<String, Object>> servers = List.of(
                Map.of(
                        "type", "shadowsocks",
                        "name", "香港01",
                        "host", "1.2.3.4",
                        "port", 443,
                        "cipher", "aes-256-gcm",
                        "created_at", 0L
                ),
                Map.of(
                        "type", "shadowsocks",
                        "name", "美国01",
                        "host", "5.6.7.8",
                        "port", 443,
                        "cipher", "aes-256-gcm",
                        "created_at", 0L
                )
        );
        String yaml = ClashMetaBuilder.build(servers, "uuid", "V2Board", "rules/default.clash.yaml");
        // 港组应含香港节点；美组含美国；苹果组不应被塞满全部节点名作为唯一内容以外的误填充——
        // 策略组 proxies 仍含「🚀 节点选择」等引用即可
        assertTrue(yaml.contains("🇭🇰 香港"));
        int appleIdx = yaml.indexOf("🍎 苹果服务");
        assertTrue(appleIdx > 0);
        String appleSection = yaml.substring(appleIdx, Math.min(yaml.length(), appleIdx + 400));
        assertTrue(appleSection.contains("🚀 节点选择"));
        // 苹果组不应直接列出「美国01」节点（仅策略引用）
        assertFalse(appleSection.contains("美国01"), "policy-only group must not append all nodes");
    }

    @Test
    void buildFromContent_usesProvidedYamlTemplate() {
        List<Map<String, Object>> servers = List.of(Map.of(
                "type", "shadowsocks",
                "name", "hk-1",
                "host", "1.2.3.4",
                "port", 443,
                "cipher", "aes-256-gcm",
                "created_at", 0L
        ));
        String template = """
                proxies:
                proxy-groups:
                  - { name: "$app_name", type: select, proxies: ["DIRECT"] }
                rules:
                  - MATCH,$app_name
                """;
        String yaml = ClashMetaBuilder.buildFromContent(servers, "uuid", "MyApp", template);
        assertTrue(yaml.contains("name: MyApp"));
        assertTrue(yaml.contains("hk-1"));
        assertTrue(yaml.contains("MATCH"));
    }

    @Test
    void emptyRegionGroup_removedWhenNoMatch() {
        List<Map<String, Object>> servers = List.of(Map.of(
                "type", "shadowsocks",
                "name", "美国01",
                "host", "5.6.7.8",
                "port", 443,
                "cipher", "aes-256-gcm",
                "created_at", 0L
        ));
        String yaml = ClashMetaBuilder.build(servers, "uuid", "V2Board", "rules/default.clash.yaml");
        assertTrue(yaml.contains("🇺🇲 美国"));
        assertFalse(yaml.contains("name: \"🇭🇰 香港\"") || yaml.contains("name: 🇭🇰 香港"),
                "empty hk region group should be removed");
    }

    @Test
    void emptyProxies_filledWithAllNodes() {
        List<Map<String, Object>> servers = List.of(Map.of(
                "type", "shadowsocks",
                "name", "node-a",
                "host", "1.2.3.4",
                "port", 443,
                "cipher", "aes-256-gcm",
                "created_at", 0L
        ));
        String template = """
                proxies:
                proxy-groups:
                  - name: "🚀 节点选择"
                    type: select
                    proxies: ["🚀 手动切换"]
                  - name: "🚀 手动切换"
                    type: select
                    proxies: []
                rules:
                  - MATCH,🚀 节点选择
                """;
        String yaml = ClashMetaBuilder.buildFromContent(servers, "uuid", "App", template);
        assertTrue(yaml.contains("node-a"));
        int manual = yaml.indexOf("🚀 手动切换");
        assertTrue(manual > 0);
        assertTrue(yaml.substring(manual).contains("node-a"));
    }
}
