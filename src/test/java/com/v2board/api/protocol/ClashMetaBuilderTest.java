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
        assertTrue(yaml.contains("type: url-test"), "auto/region url-test missing");
        int mainIdx = yaml.indexOf("🚀 节点选择");
        assertTrue(mainIdx >= 0);
        String mainSection = yaml.substring(mainIdx, Math.min(yaml.length(), mainIdx + 350));
        int autoIdx = mainSection.indexOf("♻️ 自动选择");
        int manualIdx = mainSection.indexOf("🚀 手动切换");
        assertTrue(autoIdx >= 0 && manualIdx > autoIdx, "auto select must be before manual in 节点选择");
    }

    @Test
    void preferAutoSelect_reordersLegacyManualFirstTemplate() {
        List<Map<String, Object>> servers = List.of(Map.of(
                "type", "shadowsocks",
                "name", "香港01",
                "host", "1.2.3.4",
                "port", 443,
                "cipher", "aes-256-gcm",
                "created_at", 0L
        ));
        String template = """
                proxies: []
                proxy-groups:
                  - name: "🚀 节点选择"
                    type: select
                    proxies:
                      - "🚀 手动切换"
                      - "♻️ 自动选择"
                      - DIRECT
                  - name: "🚀 手动切换"
                    type: select
                    proxies: []
                  - name: "♻️ 自动选择"
                    type: url-test
                    url: "https://www.gstatic.com/generate_204"
                    interval: 300
                    proxies: []
                rules:
                  - MATCH,🚀 节点选择
                """;
        String yaml = ClashMetaBuilder.buildFromContent(servers, "uuid", "App", template);
        int mainIdx = yaml.indexOf("🚀 节点选择");
        String mainSection = yaml.substring(mainIdx, Math.min(yaml.length(), mainIdx + 280));
        assertTrue(mainSection.indexOf("♻️ 自动选择") < mainSection.indexOf("🚀 手动切换"),
                "builder must promote url-test auto group ahead of manual");
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
    void danglingRefs_prunedWhenRegionGroupRemoved() {
        List<Map<String, Object>> servers = List.of(Map.of(
                "type", "shadowsocks",
                "name", "⚠️ 🇭🇰【亚洲】香港01丨直连1",
                "host", "1.2.3.4",
                "port", 443,
                "cipher", "aes-256-gcm",
                "created_at", 0L
        ));
        String template = """
                proxies: []
                proxy-groups:
                  - name: "🚀 节点选择"
                    type: select
                    proxies:
                      - "🇭🇰 香港节点"
                      - "🇨🇳 台湾节点"
                      - "🚀 手动切换"
                      - DIRECT
                  - name: "🚀 手动切换"
                    type: select
                    proxies: []
                  - name: "🇭🇰 香港节点"
                    type: select
                    proxies:
                      - "(港|HK|hk|Hong Kong|HongKong|hongkong)"
                  - name: "🇨🇳 台湾节点"
                    type: select
                    proxies:
                      - "(台|新北|彰化|TW|Taiwan)"
                  - name: "🎥 奈飞视频"
                    type: select
                    proxies:
                      - "🎥 奈飞节点"
                      - "🚀 节点选择"
                      - DIRECT
                  - name: "🎥 奈飞节点"
                    type: select
                    proxies:
                      - "(NF|奈飞|Netflix|NETFLIX)"
                rules:
                  - MATCH,🚀 节点选择
                """;
        String yaml = ClashMetaBuilder.buildFromContent(servers, "uuid", "App", template);
        assertTrue(yaml.contains("🇭🇰 香港节点"));
        assertFalse(yaml.contains("🇨🇳 台湾节点"), "empty TW group and dangling refs must be gone");
        assertFalse(yaml.contains("🎥 奈飞节点"), "empty Netflix filter group must be gone");
        assertTrue(yaml.contains("🎥 奈飞视频"));
        assertTrue(yaml.contains("香港01") || yaml.contains("香港节点"));
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
