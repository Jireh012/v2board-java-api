package com.v2board.api.service;

import com.v2board.api.config.V2boardRedisProperties;
import com.v2board.api.mapper.SubscribeRuleTemplateMapper;
import com.v2board.api.model.SubscribeRuleTemplate;
import com.v2board.api.service.external.ExternalSubscribeFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleTemplateServiceTest {

    private SubscribeRuleTemplateMapper mapper;
    private CacheService cacheService;
    private ExternalSubscribeFetcher fetcher;
    private RuleTemplateService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SubscribeRuleTemplateMapper.class);
        cacheService = mock(CacheService.class);
        fetcher = mock(ExternalSubscribeFetcher.class);
        V2boardRedisProperties props = new V2boardRedisProperties();
        props.setPrefix("v2board_");
        service = new RuleTemplateService(mapper, cacheService, fetcher, props);
    }

    @Test
    void resolve_prefersRedisCache() {
        when(cacheService.get("v2board_subscribe:rule:clash")).thenReturn("cached-yaml");
        assertEquals("cached-yaml", service.resolve("clash"));
        verify(mapper, never()).selectById(anyString());
    }

    @Test
    void resolve_usesDbThenCaches() {
        when(cacheService.get("v2board_subscribe:rule:clash")).thenReturn(null);
        SubscribeRuleTemplate row = new SubscribeRuleTemplate();
        row.setFormat("clash");
        row.setContent("db-custom-yaml");
        when(mapper.selectById("clash")).thenReturn(row);

        assertEquals("db-custom-yaml", service.resolve("clash"));
        verify(cacheService).set(eq("v2board_subscribe:rule:clash"), eq("db-custom-yaml"), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void resolve_fallsBackToClasspathSeed() {
        when(cacheService.get("v2board_subscribe:rule:clash")).thenReturn(null);
        when(mapper.selectById("clash")).thenReturn(null);

        String content = service.resolve("clash");
        assertTrue(content.contains("proxy-groups") || content.contains("rules"));
        verify(cacheService).set(eq("v2board_subscribe:rule:clash"), anyString(), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void resolve_stashFallsBackToClashWhenNoStashRow() {
        when(cacheService.get("v2board_subscribe:rule:stash")).thenReturn(null);
        when(mapper.selectById("stash")).thenReturn(null);
        when(cacheService.get("v2board_subscribe:rule:clash")).thenReturn(null);
        when(mapper.selectById("clash")).thenReturn(null);

        String content = service.resolve("stash");
        assertTrue(content.contains("rules") || content.contains("proxy-groups"));
    }

    @Test
    void save_sanitizesAndInvalidatesCache() {
        when(mapper.selectById("clash")).thenReturn(null);
        when(mapper.insert(any(SubscribeRuleTemplate.class))).thenReturn(1);
        when(cacheService.get("v2board_subscribe:rule:clash")).thenReturn(null);

        String dirty = """
                rule-providers:
                  Ads:
                    type: http
                    url: https://raw.githubusercontent.com/ACL4SSR/ACL4SSR/master/Clash/BanAD.list
                proxy-groups:
                  - { name: "$app_name", type: select, proxies: ["DIRECT"] }
                rules:
                  - RULE-SET,Ads,REJECT
                  - MATCH,$app_name
                """;
        var data = service.save("clash", dirty, null, "manual");
        assertFalse(String.valueOf(data.get("content")).contains("raw.githubusercontent.com"));

        ArgumentCaptor<SubscribeRuleTemplate> captor = ArgumentCaptor.forClass(SubscribeRuleTemplate.class);
        verify(mapper).insert(captor.capture());
        assertFalse(captor.getValue().getContent().contains("raw.githubusercontent.com"));
        verify(cacheService).delete("v2board_subscribe:rule:clash");
    }

    @Test
    void restore_deletesDbAndCache() {
        when(mapper.deleteById("clash")).thenReturn(1);
        when(cacheService.get("v2board_subscribe:rule:clash")).thenReturn(null);
        when(mapper.selectById("clash")).thenReturn(null);

        var data = service.restore("clash");
        verify(mapper).deleteById("clash");
        verify(cacheService).delete("v2board_subscribe:rule:clash");
        assertTrue(Boolean.TRUE.equals(data.get("is_default")));
    }

    @Test
    void sync_fetchesSanitizesAndSaves() throws Exception {
        when(fetcher.fetch("https://example.com/clash.yaml")).thenReturn("""
                rule-providers:
                  Ads:
                    type: http
                    url: https://raw.githubusercontent.com/ACL4SSR/ACL4SSR/master/Clash/BanAD.list
                proxy-groups:
                  - { name: "$app_name", type: select, proxies: ["DIRECT"] }
                rules:
                  - RULE-SET,Ads,REJECT
                  - GEOIP,CN,DIRECT
                  - MATCH,$app_name
                """);
        when(mapper.selectById("clash")).thenReturn(null);
        when(mapper.insert(any(SubscribeRuleTemplate.class))).thenReturn(1);
        when(cacheService.get(anyString())).thenReturn(null);

        var data = service.sync("clash", "https://example.com/clash.yaml");
        assertFalse(String.valueOf(data.get("content")).contains("raw.githubusercontent.com"));
        verify(mapper).insert(any(SubscribeRuleTemplate.class));
        verify(cacheService).delete("v2board_subscribe:rule:clash");
    }

    @Test
    void cacheKey_usesPrefix() {
        assertEquals("v2board_subscribe:rule:surge", service.cacheKey("surge"));
    }
}
