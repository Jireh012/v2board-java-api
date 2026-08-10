package com.v2board.api.service;

import com.v2board.api.config.V2boardRedisProperties;
import com.v2board.api.mapper.SubscribeRuleTemplateMapper;
import com.v2board.api.model.SubscribeRuleTemplate;
import com.v2board.api.service.external.ExternalSubscribeFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.TimeUnit;

import com.v2board.api.common.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertFalse(content.contains("GEOSITE,category-ad,"));
        // classpath 默认种子不写 Redis，避免发版后命中旧缓存
        verify(cacheService, never()).set(eq("v2board_subscribe:rule:clash"), anyString(), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void resolve_simpleAndNodesProfiles_useClasspathNotDb() {
        when(cacheService.get(anyString())).thenReturn("should-not-use-cache");
        SubscribeRuleTemplate row = new SubscribeRuleTemplate();
        row.setContent("db-full-only");
        when(mapper.selectById(anyString())).thenReturn(row);

        try {
            RuleTemplateService.bindRequestProfile("simple");
            String simple = service.resolve("clash");
            assertTrue(simple.contains("GEOSITE,category-ads-all"));
            assertTrue(simple.contains("GEOSITE,gfw"));
            assertFalse(simple.contains("db-full-only"));

            RuleTemplateService.bindRequestProfile("nodes");
            String nodes = service.resolve("clash");
            assertTrue(nodes.contains("name: \"PROXY\"") || nodes.contains("name: PROXY"));
            assertTrue(nodes.contains("MATCH,PROXY"));
            assertFalse(nodes.contains("db-full-only"));
        } finally {
            RuleTemplateService.clearRequestProfile();
        }
        verify(mapper, never()).selectById(anyString());
    }

    @Test
    void normalizeProfile_aliasesAndFallback() {
        assertEquals("full", RuleTemplateService.normalizeProfile(null));
        assertEquals("full", RuleTemplateService.normalizeProfile(""));
        assertEquals("nodes", RuleTemplateService.normalizeProfile("node"));
        assertEquals("simple", RuleTemplateService.normalizeProfile("SIMPLE"));
        assertEquals("full", RuleTemplateService.normalizeProfile("unknown"));
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
    void sync_expandsClashRuleProvidersAndSaves() throws Exception {
        when(fetcher.fetch("https://example.com/clash.yaml")).thenReturn("""
                mixed-port: 7890
                rule-providers:
                  Ads:
                    type: http
                    url: https://example.com/BanAD.list
                proxy-groups:
                  - name: "🚀 节点选择"
                    type: select
                    proxies: ["DIRECT"]
                rules:
                  - RULE-SET,Ads,REJECT
                  - GEOIP,CN,DIRECT
                  - MATCH,🚀 节点选择
                """);
        when(fetcher.fetch("https://example.com/BanAD.list"))
                .thenReturn("DOMAIN-SUFFIX,ads.example.com\n");
        when(mapper.selectById("clash")).thenReturn(null);
        when(mapper.insert(any(SubscribeRuleTemplate.class))).thenReturn(1);
        when(cacheService.get(anyString())).thenReturn(null);

        var data = service.sync("clash", "https://example.com/clash.yaml");
        ArgumentCaptor<SubscribeRuleTemplate> captor = ArgumentCaptor.forClass(SubscribeRuleTemplate.class);
        verify(mapper).insert(captor.capture());
        String saved = captor.getValue().getContent();
        assertFalse(saved.contains("rule-providers:"));
        assertFalse(saved.contains("RULE-SET,"));
        assertTrue(saved.contains("DOMAIN-SUFFIX,ads.example.com"));
        assertTrue(saved.contains("dns:") || saved.contains("mixed-port"));
        assertFalse(Boolean.TRUE.equals(data.get("used_seed_fallback")));
        assertTrue(String.valueOf(data.get("sync_hint")).contains("内联本地化"));
        verify(cacheService).delete("v2board_subscribe:rule:clash");
    }

    @Test
    void sync_onlineIni_inlinesListsWithoutSeedFallback() throws Exception {
        String ini = """
                ruleset=🛑 广告拦截,https://example.com/BanAD.list
                ruleset=🎯 全球直连,[]GEOIP,CN
                ruleset=🐟 漏网之鱼,[]FINAL
                custom_proxy_group=🚀 节点选择`select`[]DIRECT
                custom_proxy_group=🛑 广告拦截`select`[]REJECT`[]DIRECT
                """;
        when(fetcher.fetch(RuleTemplateService.DEFAULT_ONLINE_INI_URL)).thenReturn(ini);
        when(fetcher.fetch("https://example.com/BanAD.list"))
                .thenReturn("DOMAIN-SUFFIX,ads.example.com\nDOMAIN-KEYWORD,adservice\n");
        when(mapper.selectById("clash")).thenReturn(null);
        when(mapper.insert(any(SubscribeRuleTemplate.class))).thenReturn(1);
        when(cacheService.get(anyString())).thenReturn(null);

        var data = service.sync("clash", null);
        ArgumentCaptor<SubscribeRuleTemplate> captor = ArgumentCaptor.forClass(SubscribeRuleTemplate.class);
        verify(mapper).insert(captor.capture());
        String saved = captor.getValue().getContent();
        assertFalse(saved.contains("raw.githubusercontent.com"));
        assertFalse(saved.contains("rule-providers"));
        assertTrue(saved.contains("DOMAIN-SUFFIX,ads.example.com,🛑 广告拦截"));
        assertTrue(saved.contains("MATCH,🐟 漏网之鱼"));
        assertTrue(saved.contains("dns:") || saved.contains("mixed-port"));
        assertFalse(Boolean.TRUE.equals(data.get("used_seed_fallback")));
        assertTrue(String.valueOf(data.get("sync_hint")).contains("内联本地化"));
        assertEquals(RuleTemplateService.DEFAULT_ONLINE_INI_URL, captor.getValue().getSourceUrl());
    }

    @Test
    void sync_listFetchFailure_doesNotPersist() throws Exception {
        String ini = """
                ruleset=🛑 广告拦截,https://example.com/BanAD.list
                ruleset=🐟 漏网之鱼,[]FINAL
                custom_proxy_group=🚀 节点选择`select`[]DIRECT
                """;
        when(fetcher.fetch("https://example.com/online.ini")).thenReturn(ini);
        when(fetcher.fetch("https://example.com/BanAD.list"))
                .thenThrow(new IllegalStateException("拉取失败: HTTP 404"));
        when(mapper.selectById("clash")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sync("clash", "https://example.com/online.ini"));
        assertTrue(ex.getMessage().contains("拉取规则列表失败"));
        verify(mapper, never()).insert(any());
        verify(mapper, never()).updateById(any());
    }

    @Test
    void resolveSyncUrl_prefersRequestThenStoredThenDefault() {
        when(mapper.selectById("clash")).thenReturn(null);
        assertEquals(RuleTemplateService.DEFAULT_ONLINE_INI_URL, service.resolveSyncUrl("clash", null));

        SubscribeRuleTemplate row = new SubscribeRuleTemplate();
        row.setSourceUrl("https://stored.example/rules.ini");
        when(mapper.selectById("surge")).thenReturn(row);
        assertEquals("https://stored.example/rules.ini", service.resolveSyncUrl("surge", "  "));
        assertEquals("https://req.example/x.ini", service.resolveSyncUrl("surge", "https://req.example/x.ini"));
    }

    @Test
    void cacheKey_usesPrefix() {
        assertEquals("v2board_subscribe:rule:surge", service.cacheKey("surge"));
    }
}
