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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalSubscribeNodeServiceTrafficTest {

    @Mock ExternalSubscribeSourceMapper sourceMapper;
    @Mock ExternalSubscribeNodeMapper nodeMapper;
    @InjectMocks ExternalSubscribeNodeService nodeService;

    @Test
    void listReachableAsServerMaps_skipsExhaustedSources() {
        ExternalSubscribeSource live = enabled(1L, 0);
        ExternalSubscribeSource dead = enabled(2L, 1);
        when(sourceMapper.selectList(any())).thenReturn(List.of(live, dead));
        when(nodeMapper.selectList(any())).thenReturn(List.of(
                node(10L, 1L, "live-node"),
                node(20L, 2L, "dead-node")
        ));

        List<Map<String, Object>> out = nodeService.listReachableAsServerMaps();
        assertEquals(1, out.size());
        assertEquals("live-node", out.get(0).get("name"));
    }

    private static ExternalSubscribeSource enabled(long id, int exhausted) {
        ExternalSubscribeSource s = new ExternalSubscribeSource();
        s.setId(id);
        s.setEnable(1);
        s.setTrafficExhausted(exhausted);
        return s;
    }

    private static ExternalSubscribeNode node(long id, long sourceId, String name) {
        ExternalSubscribeNode n = new ExternalSubscribeNode();
        n.setId(id);
        n.setSourceId(sourceId);
        n.setName(name);
        n.setReachable(1);
        n.setProtocol("trojan");
        n.setSingboxOutbound("{\"type\":\"trojan\",\"tag\":\"" + name
                + "\",\"server\":\"" + name + ".example\",\"port\":443,\"password\":\"p\"}");
        return n;
    }
}
