package com.v2board.api.service.external;

import com.v2board.api.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalNameFilterTest {

    @Test
    void literal_emptyReplacement_stripsMatch() {
        List<ExternalNameFilter.Rule> rules = List.of(
                new ExternalNameFilter.Rule("abc", "", false));
        assertEquals("xxxxxxname", ExternalNameFilter.apply("xxxxabcxxname", rules));
    }

    @Test
    void literal_customReplacement() {
        List<ExternalNameFilter.Rule> rules = List.of(
                new ExternalNameFilter.Rule("linux.do摇摆熊", "LD", false));
        assertEquals("DE LD [x]", ExternalNameFilter.apply("DE linux.do摇摆熊 [x]", rules));
    }

    @Test
    void regex_withCaptureGroup() {
        List<ExternalNameFilter.Rule> rules = List.of(
                new ExternalNameFilter.Rule("^(DE|NL)\\s+", "$1-", true));
        assertEquals("DE-node", ExternalNameFilter.apply("DE node", rules));
    }

    @Test
    void sequentialRules() {
        List<ExternalNameFilter.Rule> rules = List.of(
                new ExternalNameFilter.Rule("foo", "bar", false),
                new ExternalNameFilter.Rule("bar", "baz", false));
        assertEquals("baz", ExternalNameFilter.apply("foo", rules));
    }

    @Test
    void blankResultFallsBackToOriginal() {
        List<ExternalNameFilter.Rule> rules = List.of(
                new ExternalNameFilter.Rule("all", "", false));
        assertEquals("all", ExternalNameFilter.apply("all", rules));
    }

    @Test
    void validate_rejectsInvalidRegex() {
        List<Map<String, Object>> raw = List.of(Map.of(
                "pattern", "[invalid",
                "replacement", "",
                "regex", true));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ExternalNameFilter.parseAndValidateStrict(raw));
        assertTrue(ex.getMessage().contains("正则无效"));
    }

    @Test
    void validate_rejectsEmptyPattern() {
        List<Map<String, Object>> raw = List.of(Map.of(
                "pattern", "  ",
                "replacement", "x",
                "regex", false));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ExternalNameFilter.parseAndValidateStrict(raw));
        assertTrue(ex.getMessage().contains("不能为空"));
    }

    @Test
    void applyFiltersToParsed_updatesTagAndUri() {
        CanonicalExternalNode node = new CanonicalExternalNode();
        node.setName("xxxxabcxxname");
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("tag", "xxxxabcxxname");
        outbound.put("type", "vless");
        node.setSingboxOutbound(outbound);
        node.setShareUri("vless://u@h:443#xxxxabcxxname");

        ExternalNameFilter.applyFiltersToParsed(List.of(node),
                List.of(new ExternalNameFilter.Rule("abc", "", false)));

        assertEquals("xxxxxxname", node.getName());
        assertEquals("xxxxxxname", outbound.get("tag"));
        assertEquals("vless://u@h:443#xxxxxxname", node.getShareUri());
    }

    @Test
    void applyFiltersToParsed_rewritesVmessPs() throws Exception {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("v", "2");
        cfg.put("ps", "极限白嫖🇭🇰香港vmess");
        cfg.put("add", "h.example");
        cfg.put("port", 443);
        cfg.put("id", "u-1");
        cfg.put("aid", 0);
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cfg);
        String uri = "vmess://" + java.util.Base64.getEncoder()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        CanonicalExternalNode node = new CanonicalExternalNode();
        node.setName("极限白嫖🇭🇰香港vmess");
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("tag", "极限白嫖🇭🇰香港vmess");
        outbound.put("type", "vmess");
        node.setSingboxOutbound(outbound);
        node.setShareUri(uri);

        ExternalNameFilter.applyFiltersToParsed(List.of(node),
                List.of(new ExternalNameFilter.Rule("极限白嫖", "", false)));

        assertEquals("🇭🇰香港vmess", node.getName());
        String share = node.getShareUri();
        String payload = share.substring("vmess://".length(), share.indexOf('#'));
        String pad = "=".repeat((4 - payload.length() % 4) % 4);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                java.util.Base64.getDecoder().decode(payload + pad), Map.class);
        assertEquals("🇭🇰香港vmess", out.get("ps"));
    }
}
