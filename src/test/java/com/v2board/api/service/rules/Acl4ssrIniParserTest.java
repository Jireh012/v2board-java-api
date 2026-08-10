package com.v2board.api.service.rules;

import com.v2board.api.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Acl4ssrIniParserTest {

    @Test
    void parse_rulesetsGeoIpFinalAndGroups() {
        String ini = """
                [custom]
                ; comment
                ruleset=🎯 全球直连,https://raw.githubusercontent.com/ACL4SSR/ACL4SSR/master/Clash/LocalAreaNetwork.list
                ruleset=🛑 广告拦截,https://example.com/BanAD.list
                ruleset=🎯 全球直连,[]GEOIP,CN
                ruleset=🐟 漏网之鱼,[]FINAL
                custom_proxy_group=🚀 节点选择`select`[]🇭🇰 香港节点`[]DIRECT
                custom_proxy_group=🚀 手动切换`select`.*
                custom_proxy_group=🇭🇰 香港节点`select`(港|HK|Hong Kong)
                custom_proxy_group=♻️ 自动选择`url-test`.*`http://www.gstatic.com/generate_204`300,,50
                custom_proxy_group=🇭🇰 香港节点`url-test`(港|HK|Hong Kong)`http://www.gstatic.com/generate_204`300,,50
                """;
        assertTrue(Acl4ssrIniParser.looksLikeIni(ini));
        Acl4ssrIniParser.Model model = Acl4ssrIniParser.parse(ini);
        assertEquals(4, model.rulesets().size());
        assertTrue(model.rulesets().get(0).isHttpUrl());
        assertTrue(model.rulesets().get(2).isGeoIp());
        assertEquals("CN", model.rulesets().get(2).geoIpCode());
        assertTrue(model.rulesets().get(3).isFinal());
        assertEquals(5, model.groups().size());
        assertEquals("select", model.groups().get(0).type());
        assertEquals(".*", model.groups().get(1).members().get(0));
        assertEquals("url-test", model.groups().get(3).type());
        assertEquals(List.of(".*"), model.groups().get(3).members());
        assertEquals(List.of("(港|HK|Hong Kong)"), model.groups().get(4).members());
    }

    @Test
    void parse_rejectsEmptyRulesets() {
        assertThrows(BusinessException.class, () -> Acl4ssrIniParser.parse("""
                custom_proxy_group=A`select`.*
                """));
    }

    @Test
    void looksLikeIni_requiresBothKeys() {
        assertFalse(Acl4ssrIniParser.looksLikeIni("ruleset=A,https://x"));
        assertFalse(Acl4ssrIniParser.looksLikeIni("custom_proxy_group=A`select`.*"));
    }
}
