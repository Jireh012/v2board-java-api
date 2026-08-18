package com.v2board.api.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfTemplatePlaceholdersTest {

    @Test
    void cnReturn_fillsCnGroupAndIntlExcludesReturnNode() {
        String template = """
                [Proxy Group]
                🏠 回国 = select, $proxy_group_cn, DIRECT
                ♻️ 自动选择 = url-test, $proxy_group_intl, url=https://www.gstatic.com/generate_204, interval=300
                PROXY = select, $proxy_group
                """;
        String out = ConfTemplatePlaceholders.applyProxyGroups(
                template, List.of("CN 回国节点", "US_California", "CN2-GIA-LA"));
        assertTrue(out.contains("🏠 回国 = select, CN 回国节点, DIRECT"));
        assertTrue(out.contains("US_California"));
        assertTrue(out.contains("CN2-GIA-LA"));
        String intl = line(out, "♻️ 自动选择");
        assertFalse(intl.contains("CN 回国节点"));
        assertTrue(intl.contains("US_California"));
        assertTrue(intl.contains("CN2-GIA-LA"));
    }

    @Test
    void cnReturn_emptyLeavesDirectFallback() {
        String template = """
                [Proxy Group]
                🏠 回国 = select, $proxy_group_cn, DIRECT
                """;
        String out = ConfTemplatePlaceholders.applyProxyGroups(template, List.of("US_California"));
        assertTrue(out.contains("🏠 回国 = select, DIRECT"));
        assertFalse(out.contains("$proxy_group_cn"));
    }

    private static String line(String conf, String prefix) {
        return conf.lines().filter(l -> l.startsWith(prefix)).findFirst().orElse("");
    }
}
