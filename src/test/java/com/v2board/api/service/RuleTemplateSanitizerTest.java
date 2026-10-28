package com.v2board.api.service;

import com.v2board.api.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleTemplateSanitizerTest {

    private static final String SEED = """
            mixed-port: 7890
            proxies:
            proxy-groups:
              - { name: "$app_name", type: select, proxies: ["DIRECT"] }
            rules:
              - GEOIP,CN,DIRECT
              - MATCH,$app_name
            """;

    @Test
    void rewriteHealthCheckToHttps_upgradesGstaticProbe() {
        String in = """
                proxy-groups:
                  - name: auto
                    type: url-test
                    url: http://www.gstatic.com/generate_204
                    proxies: [a]
                """;
        String out = RuleTemplateSanitizer.rewriteHealthCheckToHttps(in);
        assertTrue(out.contains("https://www.gstatic.com/generate_204"));
        assertFalse(out.contains("http://www.gstatic.com/generate_204"));
    }

    @Test
    void sanitizeClash_stripsRuleProvidersAndGithubUrls() {
        String upstream = """
                rule-providers:
                  LocalAreaNetwork:
                    type: http
                    behavior: classical
                    url: "https://raw.githubusercontent.com/ACL4SSR/ACL4SSR/master/Clash/LocalAreaNetwork.list"
                    path: ./ruleset/LocalAreaNetwork.yaml
                    interval: 86400
                proxy-groups:
                  - { name: "$app_name", type: select, proxies: ["DIRECT"] }
                rules:
                  - RULE-SET,LocalAreaNetwork,DIRECT
                  - DOMAIN-SUFFIX,google.com,$app_name
                  - MATCH,$app_name
                """;
        RuleTemplateSanitizer.Result result = RuleTemplateSanitizer.sanitize("clash", upstream, SEED);
        assertFalse(RuleTemplateSanitizer.containsRemoteRuleDependency(result.content()));
        assertFalse(result.content().contains("raw.githubusercontent.com"));
        assertFalse(result.content().toLowerCase().contains("rule-providers"));
        assertFalse(result.content().toUpperCase().contains("RULE-SET,"));
        assertTrue(result.content().contains("MATCH") || result.content().contains("GEOIP"));
    }

    @Test
    void sanitizeClash_fillsRulesFromSeedWhenOnlyRemoteRules() {
        String upstream = """
                rule-providers:
                  Ads:
                    type: http
                    url: https://raw.githubusercontent.com/ACL4SSR/ACL4SSR/master/Clash/BanAD.list
                proxy-groups:
                  - { name: "$app_name", type: select, proxies: ["DIRECT"] }
                rules:
                  - RULE-SET,Ads,REJECT
                """;
        RuleTemplateSanitizer.Result result = RuleTemplateSanitizer.sanitize("clash", upstream, SEED);
        assertTrue(result.content().contains("GEOIP,CN,DIRECT") || result.content().contains("MATCH"));
        assertFalse(result.content().contains("raw.githubusercontent.com"));
        assertTrue(result.warning() != null && result.warning().contains("本地"));
    }

    @Test
    void containsRemoteRuleDependency_detectsGithubRuleProviders() {
        String bad = """
                rule-providers:
                  x:
                    url: https://raw.githubusercontent.com/foo/bar/main/a.list
                rules:
                  - MATCH,DIRECT
                """;
        assertTrue(RuleTemplateSanitizer.containsRemoteRuleDependency(bad));
        assertFalse(RuleTemplateSanitizer.containsRemoteRuleDependency(SEED));
    }

    @Test
    void sanitizeClash_rejectsWhenSeedAlsoRemote() {
        String poisonedSeed = """
                rule-providers:
                  x:
                    type: http
                    url: https://raw.githubusercontent.com/x/y/z.list
                rules:
                  - RULE-SET,x,DIRECT
                """;
        String upstream = """
                rule-providers:
                  x:
                    type: http
                    url: https://raw.githubusercontent.com/x/y/z.list
                rules:
                  - RULE-SET,x,DIRECT
                """;
        assertThrows(BusinessException.class,
                () -> RuleTemplateSanitizer.sanitize("clash", upstream, poisonedSeed));
    }

    @Test
    void sanitizeGeneric_stripsRemoteOrFallsBackToSeed() {
        String surge = """
                [Rule]
                RULE-SET,https://raw.githubusercontent.com/ACL4SSR/ACL4SSR/master/Clash/BanAD.list,REJECT
                FINAL,DIRECT
                """;
        RuleTemplateSanitizer.Result result = RuleTemplateSanitizer.sanitize("surge", surge, "[Rule]\nFINAL,DIRECT\n");
        assertFalse(result.content().contains("raw.githubusercontent.com"));
    }

    @Test
    void sanitizeSingbox_replacesRemoteRuleSetWithSeed() {
        String seed = """
                {"outbounds":[{"tag":"DIRECT","type":"direct"}],"route":{"final":"DIRECT","rules":[]}}
                """;
        String upstream = """
                {
                  "route": {
                    "rule_set": [
                      { "tag": "geoip-cn", "type": "remote", "format": "binary",
                        "url": "https://cdn.example.com/geoip-cn.srs" }
                    ],
                    "rules": [ { "rule_set": "geoip-cn", "outbound": "DIRECT" } ],
                    "final": "DIRECT"
                  }
                }
                """;
        assertTrue(RuleTemplateSanitizer.containsRemoteRuleDependency(upstream));
        RuleTemplateSanitizer.Result result = RuleTemplateSanitizer.sanitize("singbox", upstream, seed);
        assertFalse(RuleTemplateSanitizer.hasSingboxRemoteRuleSet(result.content()));
        assertTrue(result.content().contains("\"type\":\"direct\"") || result.content().contains("DIRECT"));
        assertTrue(result.warning() != null && result.warning().contains("rule_set"));
    }

    @Test
    void isUnsafeDstPortCatchAll_detectsLeakFishPortRules() {
        assertTrue(RuleTemplateSanitizer.isUnsafeDstPortCatchAll("DST-PORT,80,🐟 漏网之鱼"));
        assertTrue(RuleTemplateSanitizer.isUnsafeDstPortCatchAll("DST-PORT,443,🐟 漏网之鱼"));
        assertTrue(RuleTemplateSanitizer.isUnsafeDstPortCatchAll("- DST-PORT,443,🚀 节点选择"));
        assertFalse(RuleTemplateSanitizer.isUnsafeDstPortCatchAll("DST-PORT,80,DIRECT"));
        assertFalse(RuleTemplateSanitizer.isUnsafeDstPortCatchAll("DST-PORT,443,🎯 全球直连"));
        assertFalse(RuleTemplateSanitizer.isUnsafeDstPortCatchAll("GEOIP,CN,DIRECT"));
        assertFalse(RuleTemplateSanitizer.isUnsafeDstPortCatchAll("DST-PORT,22,🐟 漏网之鱼"));
    }

    @Test
    void sanitizeClash_stripsDstPortCatchAllToLeakFish() {
        String upstream = """
                proxy-groups:
                  - { name: "$app_name", type: select, proxies: ["DIRECT"] }
                rules:
                  - GEOIP,CN,DIRECT
                  - DST-PORT,80,🐟 漏网之鱼
                  - DST-PORT,443,🐟 漏网之鱼
                  - MATCH,DIRECT
                """;
        RuleTemplateSanitizer.Result result = RuleTemplateSanitizer.sanitize("clash", upstream, SEED);
        String out = result.content().toUpperCase();
        assertFalse(out.contains("DST-PORT,80,"));
        assertFalse(out.contains("DST-PORT,443,"));
        assertTrue(result.content().contains("GEOIP,CN,DIRECT") || result.content().contains("MATCH"));
    }

    @Test
    void stripUnsafeDstPortRules_mutatesConfigMap() {
        java.util.Map<String, Object> config = new java.util.LinkedHashMap<>();
        java.util.List<Object> rules = new java.util.ArrayList<>();
        rules.add("GEOIP,CN,DIRECT");
        rules.add("DST-PORT,80,🐟 漏网之鱼");
        rules.add("DST-PORT,443,🐟 漏网之鱼");
        rules.add("MATCH,DIRECT");
        config.put("rules", rules);
        RuleTemplateSanitizer.stripUnsafeDstPortRules(config);
        @SuppressWarnings("unchecked")
        java.util.List<Object> after = (java.util.List<Object>) config.get("rules");
        assertTrue(after.stream().noneMatch(r -> String.valueOf(r).toUpperCase().startsWith("DST-PORT,80,")));
        assertTrue(after.stream().noneMatch(r -> String.valueOf(r).toUpperCase().startsWith("DST-PORT,443,")));
        assertTrue(after.stream().anyMatch(r -> String.valueOf(r).startsWith("MATCH,")));
    }

}
