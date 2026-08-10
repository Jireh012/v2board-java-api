package com.v2board.api.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.model.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingboxBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void build_acl4ssrTemplate_hasPolicyGroupsAndNoRemoteRuleSet() throws Exception {
        User user = user("uuid");
        List<Map<String, Object>> servers = List.of(
                ss("香港01", "1.2.3.4"),
                ss("日本樱花", "5.6.7.8")
        );
        String json = SingboxBuilder.build(user, servers, "rules/default.sing-box.json", true);
        assertTrue(json.contains("🚀 节点选择"), "main select missing");
        assertTrue(json.contains("🍎 苹果服务"), "apple group missing");
        assertTrue(json.contains("📹 油管视频"), "youtube group missing");
        assertTrue(json.contains("🌍 国外媒体"), "foreign media missing");
        assertTrue(json.contains("📲 电报消息"), "telegram missing");
        assertTrue(json.contains("💬 Ai平台"), "ai missing");
        assertTrue(json.contains("🛑 广告拦截"), "ads missing");
        assertTrue(json.contains("🐟 漏网之鱼"), "final group missing");
        assertTrue(json.contains("香港01"), "hk node missing");
        assertTrue(json.contains("日本樱花"), "jp node missing");
        assertFalse(json.contains("raw.githubusercontent.com"), "must not depend on GitHub raw");
        assertFalse(json.contains("cdn.jsdelivr.net"), "must not depend on jsDelivr");
        assertFalse(hasRemoteRuleSet(json), "must not contain remote rule_set");
    }

    @Test
    void build_oldTemplate_localGeositeNoRemote() throws Exception {
        User user = user("uuid");
        List<Map<String, Object>> servers = List.of(ss("香港01", "1.2.3.4"));
        String json = SingboxBuilder.build(user, servers, "rules/default.sing-box.old.json", false);
        assertTrue(json.contains("🚀 节点选择"));
        assertTrue(json.contains("geosite") || json.contains("geoip"), "old template should use local geosite/geoip");
        assertFalse(json.contains("raw.githubusercontent.com"));
        assertFalse(hasRemoteRuleSet(json));
    }

    @Test
    void merge_regionFilter_andPolicyOnlyNotFilledWithAllNodes() throws Exception {
        User user = user("uuid");
        List<Map<String, Object>> servers = List.of(
                ss("香港01", "1.2.3.4"),
                ss("美国01", "5.6.7.8")
        );
        String json = SingboxBuilder.build(user, servers, "rules/default.sing-box.json", true);
        JsonNode root = MAPPER.readTree(json);
        JsonNode outbounds = root.get("outbounds");
        assertTrue(outbounds.isArray());

        JsonNode hk = findOutbound(outbounds, "🇭🇰 香港");
        assertTrue(hk != null, "hk region group should exist when hk node present");
        String hkOut = hk.get("outbounds").toString();
        assertTrue(hkOut.contains("香港01"), "hk group should contain hk node");
        assertFalse(hkOut.contains("美国01"), "hk group must not contain us node");

        JsonNode apple = findOutbound(outbounds, "🍎 苹果服务");
        assertTrue(apple != null);
        String appleOut = apple.get("outbounds").toString();
        assertTrue(appleOut.contains("🚀 节点选择"));
        assertFalse(appleOut.contains("美国01"), "policy-only group must not append all nodes");
        assertFalse(appleOut.contains("香港01"), "policy-only group must not append all nodes");
    }

    @Test
    void emptySelector_filledWithAllNodes() throws Exception {
        User user = user("uuid");
        List<Map<String, Object>> servers = List.of(ss("node-a", "1.2.3.4"));
        String template = """
                {
                  "outbounds": [
                    { "tag": "DIRECT", "type": "direct" },
                    { "tag": "🚀 节点选择", "type": "selector", "outbounds": ["🚀 手动切换"] },
                    { "tag": "🚀 手动切换", "type": "selector", "outbounds": [] }
                  ],
                  "route": { "final": "🚀 节点选择", "rules": [] }
                }
                """;
        String json = SingboxBuilder.buildFromContent(user, servers, template, true);
        JsonNode manual = findOutbound(MAPPER.readTree(json).get("outbounds"), "🚀 手动切换");
        assertTrue(manual != null);
        assertTrue(manual.get("outbounds").toString().contains("node-a"));
    }

    @Test
    void emptyRegionGroup_removedWhenNoMatch() throws Exception {
        User user = user("uuid");
        List<Map<String, Object>> servers = List.of(ss("美国01", "5.6.7.8"));
        String json = SingboxBuilder.build(user, servers, "rules/default.sing-box.json", true);
        JsonNode outbounds = MAPPER.readTree(json).get("outbounds");
        assertTrue(findOutbound(outbounds, "🇺🇲 美国") != null, "us group should remain");
        assertTrue(findOutbound(outbounds, "🇭🇰 香港") == null, "empty hk group should be removed");
    }

    private static boolean hasRemoteRuleSet(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode route = root.get("route");
        if (route == null || !route.has("rule_set")) {
            return json.contains("\"type\":\"remote\"") || json.contains("\"type\": \"remote\"");
        }
        for (JsonNode rs : route.get("rule_set")) {
            if ("remote".equals(rs.path("type").asText())) {
                return true;
            }
            if (rs.has("url") && rs.get("url").asText().startsWith("http")) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode findOutbound(JsonNode outbounds, String tag) {
        if (outbounds == null || !outbounds.isArray()) {
            return null;
        }
        for (JsonNode ob : outbounds) {
            if (tag.equals(ob.path("tag").asText())) {
                return ob;
            }
        }
        return null;
    }

    private static User user(String uuid) {
        User user = new User();
        user.setUuid(uuid);
        return user;
    }

    private static Map<String, Object> ss(String name, String host) {
        return Map.of(
                "type", "shadowsocks",
                "name", name,
                "host", host,
                "port", 443,
                "cipher", "aes-256-gcm",
                "created_at", 0L
        );
    }
}
