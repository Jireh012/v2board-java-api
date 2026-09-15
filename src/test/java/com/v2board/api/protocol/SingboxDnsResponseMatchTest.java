package com.v2board.api.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.model.User;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingboxDnsResponseMatchTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void defaultSeed_noAddressFilter_staysUntouched() throws Exception {
        User user = user();
        String json = SingboxBuilder.buildFromContent(
                user, List.of(ss()), classpath("rules/default.sing-box.json"), true, true);
        JsonNode rules = MAPPER.readTree(json).path("dns").path("rules");
        assertTrue(rules.isArray());
        assertFalse(rules.toString().contains("match_response"));
        assertFalse(rules.toString().contains("\"evaluate\""));
    }

    @Test
    void ipIsPrivate_getsEvaluateAndMatchResponse() {
        Map<String, Object> config = dnsConfig(List.of(Map.of(
                "ip_is_private", true,
                "action", "route",
                "server", "local"
        )));
        SingboxDnsResponseMatch.apply(config);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) ((Map<String, Object>) config.get("dns")).get("rules");
        assertEquals("evaluate", rules.get(0).get("action"));
        assertEquals("remote", rules.get(0).get("server"));
        assertEquals(Boolean.TRUE, rules.get(1).get("match_response"));
        assertEquals(true, rules.get(1).get("ip_is_private"));
    }

    @Test
    void geoipRuleSet_getsMatchResponse() {
        Map<String, Object> config = dnsConfig(List.of(Map.of(
                "rule_set", "geoip-cn",
                "action", "route",
                "server", "local"
        )));
        SingboxDnsResponseMatch.apply(config);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) ((Map<String, Object>) config.get("dns")).get("rules");
        assertEquals("evaluate", rules.get(0).get("action"));
        assertEquals(Boolean.TRUE, rules.get(1).get("match_response"));
    }

    @Test
    void domainSuffixOnly_notMigrated() {
        Map<String, Object> domain = new LinkedHashMap<>();
        domain.put("domain_suffix", List.of(".cn"));
        domain.put("action", "route");
        domain.put("server", "cn");
        Map<String, Object> config = dnsConfig(List.of(domain));
        SingboxDnsResponseMatch.apply(config);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) ((Map<String, Object>) config.get("dns")).get("rules");
        assertEquals(1, rules.size());
        assertFalse(rules.get(0).containsKey("match_response"));
    }

    @Test
    void apply_isIdempotent() {
        Map<String, Object> config = dnsConfig(List.of(Map.of(
                "ip_cidr", List.of("1.1.1.1/32"),
                "server", "local"
        )));
        SingboxDnsResponseMatch.apply(config);
        SingboxDnsResponseMatch.apply(config);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) ((Map<String, Object>) config.get("dns")).get("rules");
        assertEquals(2, rules.size());
        assertEquals("evaluate", rules.get(0).get("action"));
        assertEquals(Boolean.TRUE, rules.get(1).get("match_response"));
    }

    @Test
    void buildFromContent_migratesCustomTemplateWhenAsked() throws Exception {
        String template = """
                {
                  "dns": {
                    "servers": [{"type":"udp","tag":"remote","server":"1.1.1.1"}],
                    "rules": [{"ip_is_private": true, "server": "local"}],
                    "final": "remote"
                  },
                  "outbounds": [
                    {"tag":"DIRECT","type":"direct"},
                    {"tag":"🚀 节点选择","type":"selector","outbounds":[]}
                  ]
                }
                """;
        String skipped = SingboxBuilder.buildFromContent(user(), List.of(ss()), template, true, false);
        assertFalse(skipped.contains("match_response"));

        String migrated = SingboxBuilder.buildFromContent(user(), List.of(ss()), template, true, true);
        JsonNode rules = MAPPER.readTree(migrated).path("dns").path("rules");
        assertEquals("evaluate", rules.get(0).path("action").asText());
        assertTrue(rules.get(1).path("match_response").asBoolean());
    }

    @Test
    void fourArgBuild_usesBoundClientVersion() throws Exception {
        String template = """
                {
                  "dns": {
                    "servers": [{"type":"udp","tag":"remote","server":"1.1.1.1"}],
                    "rules": [{"ip_is_private": true, "server": "local"}],
                    "final": "remote"
                  },
                  "outbounds": [
                    {"tag":"DIRECT","type":"direct"},
                    {"tag":"🚀 节点选择","type":"selector","outbounds":[]}
                  ]
                }
                """;
        SingboxVersion.bind("1.14.0");
        try {
            String json = SingboxBuilder.buildFromContent(user(), List.of(ss()), template, true);
            assertEquals("evaluate", MAPPER.readTree(json).path("dns").path("rules").get(0).path("action").asText());
        } finally {
            SingboxVersion.clear();
        }
    }

    private static Map<String, Object> dnsConfig(List<Map<String, Object>> rules) {
        Map<String, Object> dns = new LinkedHashMap<>();
        dns.put("servers", List.of(Map.of("type", "udp", "tag", "remote", "server", "1.1.1.1")));
        dns.put("rules", new ArrayList<>(rules));
        dns.put("final", "remote");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("dns", dns);
        return config;
    }

    private static User user() {
        User user = new User();
        user.setUuid("uuid");
        return user;
    }

    private static Map<String, Object> ss() {
        return Map.of(
                "type", "shadowsocks",
                "name", "node-a",
                "host", "1.2.3.4",
                "port", 443,
                "cipher", "aes-256-gcm",
                "created_at", 0L
        );
    }

    private static String classpath(String path) throws Exception {
        try (var in = SingboxDnsResponseMatchTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return new String(in.readAllBytes());
        }
    }
}
