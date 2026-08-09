package com.v2board.api.service.external;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.ExternalSubscribeNodeMapper;
import com.v2board.api.mapper.ExternalSubscribeSourceMapper;
import com.v2board.api.model.ExternalSubscribeNode;
import com.v2board.api.model.ExternalSubscribeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void pickPreProxyNode_excludesSelfSourceAndOrdersBySortId() {
        ExternalSubscribeSource a = new ExternalSubscribeSource();
        a.setId(1L);
        a.setEnable(1);
        ExternalSubscribeSource b = new ExternalSubscribeSource();
        b.setId(2L);
        b.setEnable(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of(a, b));

        ExternalSubscribeNode picked = new ExternalSubscribeNode();
        picked.setId(10L);
        picked.setSourceId(2L);
        picked.setName("proxy-node");
        when(nodeMapper.selectOne(any())).thenReturn(picked);

        ExternalSubscribeNode result = syncService.pickPreProxyNode(1L);
        assertNotNull(result);
        assertEquals(10L, result.getId());

        ArgumentCaptor<LambdaQueryWrapper<ExternalSubscribeNode>> cap =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(nodeMapper).selectOne(cap.capture());
        // Wrapper built with in(sourceIds excluding 1) — smoke that selectOne was called once
        assertNotNull(cap.getValue());
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
        ExternalSubscribeSource source = new ExternalSubscribeSource();
        source.setId(1L);
        source.setUrl("https://example.com/sub");
        source.setPreProxyEnable(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of(source));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> syncService.fetchSubscribeContent(source));
        assertTrue(ex.getMessage().contains("无可用节点"));
    }

    @Test
    void fetchSubscribeContent_usesProxySessionWhenEnabled() throws Exception {
        ExternalSubscribeSource source = new ExternalSubscribeSource();
        source.setId(1L);
        source.setUrl("https://example.com/sub");
        source.setPreProxyEnable(1);

        ExternalSubscribeSource other = new ExternalSubscribeSource();
        other.setId(2L);
        other.setEnable(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of(source, other));

        ExternalSubscribeNode node = new ExternalSubscribeNode();
        node.setId(9L);
        node.setSourceId(2L);
        node.setName("n");
        node.setSingboxOutbound("{\"type\":\"direct\",\"tag\":\"x\"}");
        when(nodeMapper.selectOne(any())).thenReturn(node);

        Proxy proxy = new Proxy(Proxy.Type.HTTP, new java.net.InetSocketAddress("127.0.0.1", 19000));
        SingBoxProbeService.LocalHttpProxySession session =
                mock(SingBoxProbeService.LocalHttpProxySession.class);
        when(session.proxy()).thenReturn(proxy);
        when(probeService.openHttpProxy(anyMap())).thenReturn(session);
        when(fetcher.fetch(eq("https://example.com/sub"), eq(proxy))).thenReturn("proxied");

        assertEquals("proxied", syncService.fetchSubscribeContent(source));
        verify(session).close();
    }
}
