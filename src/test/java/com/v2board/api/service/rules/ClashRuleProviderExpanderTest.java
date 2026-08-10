package com.v2board.api.service.rules;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClashRuleProviderExpanderTest {

    @Test
    void expand_inlinesHttpProvidersAndRemovesBlock() {
        String yaml = """
                mixed-port: 7890
                rule-providers:
                  Ads:
                    type: http
                    behavior: classical
                    url: https://example.com/BanAD.list
                    interval: 86400
                proxy-groups:
                  - name: PROXY
                    type: select
                    proxies: [DIRECT]
                rules:
                  - RULE-SET,Ads,REJECT
                  - MATCH,PROXY
                """;
        AtomicInteger fetches = new AtomicInteger();
        String out = ClashRuleProviderExpander.expand(yaml, url -> {
            fetches.incrementAndGet();
            assertTrue(url.contains("BanAD.list"));
            return "DOMAIN-SUFFIX,ads.example.com\nDOMAIN-KEYWORD,adservice\n";
        });
        assertEqualsOne(fetches.get());
        assertFalse(out.contains("rule-providers"));
        assertFalse(out.contains("RULE-SET,"));
        assertTrue(out.contains("DOMAIN-SUFFIX,ads.example.com,REJECT"));
        assertTrue(out.contains("MATCH,PROXY"));
        assertTrue(out.contains("mixed-port"));
    }

    @Test
    void hasHttpProviders_detects() {
        assertTrue(ClashRuleProviderExpander.hasHttpProviders("""
                rule-providers:
                  A:
                    type: http
                    url: https://example.com/a.list
                """));
        assertFalse(ClashRuleProviderExpander.hasHttpProviders("rules:\n  - MATCH,DIRECT\n"));
    }

    private static void assertEqualsOne(int n) {
        org.junit.jupiter.api.Assertions.assertEquals(1, n);
    }
}
