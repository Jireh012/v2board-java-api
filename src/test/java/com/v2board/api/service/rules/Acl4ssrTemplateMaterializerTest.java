package com.v2board.api.service.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Acl4ssrTemplateMaterializerTest {

    @ParameterizedTest
    @ValueSource(strings = {"clash", "stash", "surge", "surfboard", "singbox", "quantumultx", "loon"})
    void materialize_allFormatsInlineWithoutRemoteDeps(String format) throws Exception {
        String seedPath = switch (format) {
            case "clash", "stash" -> "rules/default.clash.yaml";
            case "surge" -> "rules/default.surge.conf";
            case "surfboard" -> "rules/default.surfboard.conf";
            case "singbox" -> "rules/default.sing-box.json";
            case "quantumultx" -> "rules/default.quantumultx.conf";
            case "loon" -> "rules/default.loon.conf";
            default -> throw new IllegalArgumentException(format);
        };
        Acl4ssrIniParser.Model model = Acl4ssrIniParser.parse("""
                ruleset=🛑 广告拦截,https://example.com/BanAD.list
                ruleset=🎯 全球直连,[]GEOIP,CN
                ruleset=🐟 漏网之鱼,[]FINAL
                custom_proxy_group=🚀 节点选择`select`[]DIRECT
                custom_proxy_group=🛑 广告拦截`select`[]REJECT`[]DIRECT
                """);
        Map<String, String> lists = Map.of(
                "https://example.com/BanAD.list", "DOMAIN-SUFFIX,ads.example.com\n");
        String out = Acl4ssrTemplateMaterializer.materialize(format, read(seedPath), model, lists);
        assertFalse(out.contains("raw.githubusercontent.com"), format);
        assertFalse(out.contains("rule-providers"), format);
        assertFalse(out.contains("RULE-SET,https"), format);
        assertTrue(out.contains("ads.example.com"), format);
        if ("clash".equals(format) || "stash".equals(format)) {
            assertTrue(out.contains("mixed-port") || out.contains("dns:"), format);
        } else if ("singbox".equals(format)) {
            assertTrue(out.contains("\"dns\""), format);
            assertTrue(out.contains("\"inbounds\""), format);
        } else if ("quantumultx".equals(format)) {
            assertTrue(out.contains("[general]") || out.contains("[dns]"), format);
        } else {
            assertTrue(out.contains("[General]"), format);
        }
    }

    @Test
    void materialize_clashKeepsDnsShellAndInlinesLists() throws Exception {
        String seed = read("rules/default.clash.yaml");
        Acl4ssrIniParser.Model model = Acl4ssrIniParser.parse("""
                ruleset=🛑 广告拦截,https://example.com/BanAD.list
                ruleset=🎯 全球直连,[]GEOIP,CN
                ruleset=🐟 漏网之鱼,[]FINAL
                custom_proxy_group=🚀 节点选择`select`[]🚀 手动切换`[]DIRECT
                custom_proxy_group=🚀 手动切换`select`.*
                custom_proxy_group=🛑 广告拦截`select`[]REJECT`[]DIRECT
                """);
        Map<String, String> lists = Map.of(
                "https://example.com/BanAD.list",
                "DOMAIN-SUFFIX,ads.example.com\nDOMAIN-KEYWORD,adservice\n");
        String out = Acl4ssrTemplateMaterializer.materialize("clash", seed, model, lists);
        assertTrue(out.contains("mixed-port") || out.contains("dns:"));
        assertTrue(out.contains("DOMAIN-SUFFIX,ads.example.com,🛑 广告拦截"));
        assertTrue(out.contains("GEOIP,CN,"));
        assertTrue(out.contains("MATCH,🐟 漏网之鱼"));
        assertFalse(out.contains("rule-providers"));
        assertFalse(out.contains("raw.githubusercontent.com"));
        assertFalse(out.contains("RULE-SET,"));
    }

    @Test
    void materialize_surgeKeepsGeneralAndReplacesGroupsRules() throws Exception {
        String seed = read("rules/default.surge.conf");
        Acl4ssrIniParser.Model model = Acl4ssrIniParser.parse("""
                ruleset=🛑 广告拦截,https://example.com/BanAD.list
                ruleset=🐟 漏网之鱼,[]FINAL
                custom_proxy_group=🚀 节点选择`select`[]DIRECT
                custom_proxy_group=🛑 广告拦截`select`[]REJECT
                """);
        Map<String, String> lists = Map.of(
                "https://example.com/BanAD.list", "DOMAIN-SUFFIX,ads.example.com\n");
        String out = Acl4ssrTemplateMaterializer.materialize("surge", seed, model, lists);
        assertTrue(out.contains("[General]"));
        assertTrue(out.contains("doh-server") || out.contains("dns-server"));
        assertTrue(out.contains("[Proxy Group]"));
        assertTrue(out.contains("DOMAIN-SUFFIX,ads.example.com,🛑 广告拦截"));
        assertTrue(out.contains("FINAL,🐟 漏网之鱼"));
        assertFalse(out.contains("raw.githubusercontent.com"));
    }

    @Test
    void materialize_singboxNoRemoteRuleSet() throws Exception {
        String seed = read("rules/default.sing-box.json");
        Acl4ssrIniParser.Model model = Acl4ssrIniParser.parse("""
                ruleset=📹 油管视频,https://example.com/YouTube.list
                ruleset=🐟 漏网之鱼,[]FINAL
                custom_proxy_group=🚀 节点选择`select`[]DIRECT
                custom_proxy_group=📹 油管视频`select`[]🚀 节点选择
                """);
        Map<String, String> lists = Map.of(
                "https://example.com/YouTube.list", "DOMAIN-SUFFIX,youtube.com\nDOMAIN-SUFFIX,youtu.be\n");
        String out = Acl4ssrTemplateMaterializer.materialize("singbox", seed, model, lists);
        assertTrue(out.contains("\"inbounds\""));
        assertTrue(out.contains("\"dns\""));
        assertTrue(out.contains("youtube.com"));
        assertFalse(out.contains("\"type\" : \"remote\"") || out.contains("\"type\":\"remote\""));
        assertFalse(out.contains("raw.githubusercontent.com"));
    }

    @Test
    void materialize_clashUrlTestDoesNotEmbedTestUrlAsProxy() throws Exception {
        String seed = read("rules/default.clash.yaml");
        Acl4ssrIniParser.Model model = Acl4ssrIniParser.parse("""
                ruleset=🐟 漏网之鱼,[]FINAL
                custom_proxy_group=♻️ 自动选择`url-test`.*`http://www.gstatic.com/generate_204`300,,50
                custom_proxy_group=🇭🇰 香港节点`url-test`(港|HK)`http://www.gstatic.com/generate_204`300,,50
                """);
        String out = Acl4ssrTemplateMaterializer.materialize("clash", seed, model, Map.of());
        assertTrue(out.contains("type: url-test"));
        assertTrue(out.contains("(港|HK)"));
        assertFalse(out.contains("- http://www.gstatic.com/generate_204"));
        assertFalse(out.contains("- 300,,50"));
        assertFalse(out.contains("- '300"));
    }

    @Test
    void materialize_quantumultxAndLoon() throws Exception {
        Acl4ssrIniParser.Model model = Acl4ssrIniParser.parse("""
                ruleset=🎯 全球直连,https://example.com/a.list
                ruleset=🐟 漏网之鱼,[]FINAL
                custom_proxy_group=🚀 节点选择`select`.*
                """);
        Map<String, String> lists = Map.of("https://example.com/a.list", "DOMAIN-SUFFIX,example.com\n");
        String qx = Acl4ssrTemplateMaterializer.materialize("quantumultx", read("rules/default.quantumultx.conf"), model, lists);
        assertTrue(qx.contains("[policy]"));
        assertTrue(qx.contains("host-suffix, example.com,"));
        assertTrue(qx.contains("final,"));
        String loon = Acl4ssrTemplateMaterializer.materialize("loon", read("rules/default.loon.conf"), model, lists);
        assertTrue(loon.contains("[General]"));
        assertTrue(loon.contains("DOMAIN-SUFFIX,example.com,"));
    }

    private static String read(String path) throws Exception {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
