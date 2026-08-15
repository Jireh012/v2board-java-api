package com.v2board.api.service.external;

import com.v2board.api.mapper.ExternalSubscribeNodeMapper;
import com.v2board.api.mapper.ExternalSubscribeSourceMapper;
import com.v2board.api.model.ExternalSubscribeNode;
import com.v2board.api.model.ExternalSubscribeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalSubscribePreProxyTest {

    @Mock ExternalSubscribeSourceMapper sourceMapper;
    @Mock ExternalSubscribeNodeMapper nodeMapper;
    @Mock ExternalSubscribeFetcher fetcher;
    @Mock ExternalSubscribeParser parser;
    @Mock SingBoxProbeService probeService;

    @InjectMocks ExternalSubscribeSyncService syncService;

    @Test
    void listPreProxyCandidates_excludesSelfSource() {
        ExternalSubscribeSource self = enabledSource(1L);
        ExternalSubscribeSource other = enabledSource(2L);
        when(sourceMapper.selectList(any())).thenReturn(List.of(self, other));

        ExternalSubscribeNode node = proxyNode(10L, 2L, "{\"type\":\"direct\",\"tag\":\"n\"}");
        when(nodeMapper.selectList(any())).thenReturn(List.of(node));

        List<ExternalSubscribeNode> list = syncService.listPreProxyCandidates(1L);
        assertEquals(1, list.size());
        assertEquals(10L, list.get(0).getId());
        assertEquals(node, syncService.pickPreProxyNode(1L));
    }

    @Test
    void fetchSubscribeContent_directWhenDisabled() throws Exception {
        ExternalSubscribeSource source = new ExternalSubscribeSource();
        source.setId(1L);
        source.setUrl("https://example.com/sub");
        source.setPreProxyEnable(0);
        when(fetcher.fetch("https://example.com/sub")).thenReturn("body");

        assertEquals("body", syncService.fetchSubscribeContent(source));
        verify(fetcher).fetch("https://example.com/sub");
        verify(fetcher, never()).fetch(anyString(), any());
        verifyNoInteractions(probeService);
    }

    @Test
    void fetchSubscribeContent_failsWhenEnabledButNoCandidate() {
        ExternalSubscribeSource source = enabledSource(1L);
        source.setUrl("https://example.com/sub");
        source.setPreProxyEnable(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of(source));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> syncService.fetchSubscribeContent(source));
        assertTrue(ex.getMessage().contains("无可用节点"));
        verifyNoInteractions(probeService);
    }

    @Test
    void fetchSubscribeContent_requestsUrlThroughProxyNode() throws Exception {
        ExternalSubscribeSource source = enabledSource(1L);
        source.setUrl("https://blocked.example/sub");
        source.setPreProxyEnable(1);
        ExternalSubscribeSource other = enabledSource(2L);
        when(sourceMapper.selectList(any())).thenReturn(List.of(source, other));

        ExternalSubscribeNode node = proxyNode(9L, 2L, "{\"type\":\"direct\",\"tag\":\"x\"}");
        when(nodeMapper.selectList(any())).thenReturn(List.of(node));

        Proxy proxy = httpProxy(19000);
        SingBoxProbeService.LocalHttpProxySession session = mockSession(proxy);
        when(probeService.openHttpProxy(anyMap())).thenReturn(session);
        when(fetcher.fetch(eq("https://blocked.example/sub"), eq(proxy))).thenReturn("proxied-body");

        assertEquals("proxied-body", syncService.fetchSubscribeContent(source));

        verify(probeService).openHttpProxy(argThat(outbound ->
                "direct".equals(outbound.get("type")) && "x".equals(outbound.get("tag"))));
        verify(fetcher).fetch("https://blocked.example/sub", proxy);
        verify(fetcher, never()).fetch(anyString());
        verify(session).close();
    }

    @Test
    void fetchSubscribeContent_triesNextProxyWhenFirstFails() throws Exception {
        ExternalSubscribeSource source = enabledSource(1L);
        source.setUrl("https://example.com/sub");
        source.setPreProxyEnable(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of(source, enabledSource(2L)));

        ExternalSubscribeNode bad = proxyNode(1L, 2L, "{\"type\":\"direct\",\"tag\":\"bad\"}");
        ExternalSubscribeNode good = proxyNode(2L, 2L, "{\"type\":\"direct\",\"tag\":\"good\"}");
        when(nodeMapper.selectList(any())).thenReturn(List.of(bad, good));

        Proxy proxyBad = httpProxy(19001);
        Proxy proxyGood = httpProxy(19002);
        SingBoxProbeService.LocalHttpProxySession sessionBad = mockSession(proxyBad);
        SingBoxProbeService.LocalHttpProxySession sessionGood = mockSession(proxyGood);
        when(probeService.openHttpProxy(anyMap()))
                .thenReturn(sessionBad)
                .thenReturn(sessionGood);
        when(fetcher.fetch(eq("https://example.com/sub"), eq(proxyBad)))
                .thenThrow(new IllegalStateException("proxy dead"));
        when(fetcher.fetch(eq("https://example.com/sub"), eq(proxyGood))).thenReturn("ok");

        assertEquals("ok", syncService.fetchSubscribeContent(source));
        verify(probeService, times(2)).openHttpProxy(anyMap());
        verify(sessionBad).close();
        verify(sessionGood).close();
    }

    @Test
    void fetchWithDirectThenPreProxy_usesDirectWhenOk() throws Exception {
        when(fetcher.fetch("https://example.com/rules.ini")).thenReturn("ini");

        assertEquals("ini", syncService.fetchWithDirectThenPreProxy("https://example.com/rules.ini"));
        verify(fetcher).fetch("https://example.com/rules.ini");
        verify(fetcher, never()).fetch(anyString(), any());
        verifyNoInteractions(probeService);
    }

    @Test
    void fetchWithDirectThenPreProxy_fallsBackToProxyNodeUrlRequest() throws Exception {
        when(fetcher.fetch("https://github.example/raw.ini"))
                .thenThrow(new IllegalStateException("direct blocked"));
        when(sourceMapper.selectList(any())).thenReturn(List.of(enabledSource(2L)));
        ExternalSubscribeNode node = proxyNode(5L, 2L, "{\"type\":\"trojan\",\"tag\":\"p\"}");
        when(nodeMapper.selectList(any())).thenReturn(List.of(node));
        when(probeService.isSingBoxAvailable()).thenReturn(true);

        Proxy proxy = httpProxy(19100);
        SingBoxProbeService.LocalHttpProxySession session = mockSession(proxy);
        when(probeService.openHttpProxy(anyMap())).thenReturn(session);
        when(fetcher.fetch(eq("https://github.example/raw.ini"), eq(proxy))).thenReturn("via-proxy");

        assertEquals("via-proxy",
                syncService.fetchWithDirectThenPreProxy("https://github.example/raw.ini"));

        verify(fetcher).fetch("https://github.example/raw.ini");
        verify(fetcher).fetch("https://github.example/raw.ini", proxy);
        verify(probeService).openHttpProxy(anyMap());
        verify(session).close();
    }

    @Test
    void fetchWithDirectThenPreProxy_rethrowsDirectWhenNoCandidates() throws Exception {
        IllegalStateException direct = new IllegalStateException("direct blocked");
        when(fetcher.fetch("https://example.com/x")).thenThrow(direct);
        when(sourceMapper.selectList(any())).thenReturn(List.of());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> syncService.fetchWithDirectThenPreProxy("https://example.com/x"));
        assertSame(direct, ex);
        verifyNoInteractions(probeService);
    }

    private static ExternalSubscribeSource enabledSource(long id) {
        ExternalSubscribeSource s = new ExternalSubscribeSource();
        s.setId(id);
        s.setEnable(1);
        return s;
    }

    private static ExternalSubscribeNode proxyNode(long id, long sourceId, String outbound) {
        ExternalSubscribeNode n = new ExternalSubscribeNode();
        n.setId(id);
        n.setSourceId(sourceId);
        n.setName("node-" + id);
        n.setReachable(1);
        n.setSingboxOutbound(outbound);
        return n;
    }

    private static Proxy httpProxy(int port) {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", port));
    }

    private static SingBoxProbeService.LocalHttpProxySession mockSession(Proxy proxy) {
        SingBoxProbeService.LocalHttpProxySession session =
                mock(SingBoxProbeService.LocalHttpProxySession.class);
        when(session.proxy()).thenReturn(proxy);
        return session;
    }
}
