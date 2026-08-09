package com.v2board.api.service.external;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalInfoNodeTest {

    @Test
    void detectsCommonInfoNames() {
        assertTrue(ExternalInfoNode.isInfoName("剩余流量：58.73 GB"));
        assertTrue(ExternalInfoNode.isInfoName("套餐到期：2026-12-01"));
        assertTrue(ExternalInfoNode.isInfoName("距离下次重置剩余：7 天"));
        assertTrue(ExternalInfoNode.isInfoName("Expire Time: 2099"));
        assertTrue(ExternalInfoNode.isInfoName("⚠️ 剩余流量：1 GB"));
    }

    @Test
    void keepsRealProxyNames() {
        assertFalse(ExternalInfoNode.isInfoName("🇭🇰【亚洲】香港01丨直连"));
        assertFalse(ExternalInfoNode.isInfoName("美国流量优化节点"));
        assertFalse(ExternalInfoNode.isInfoName("DE-Frankfurt"));
    }

    @Test
    void removeFrom_dropsInfoNodes() {
        List<CanonicalExternalNode> nodes = new ArrayList<>();
        nodes.add(node("剩余流量：1 GB"));
        nodes.add(node("HK-01"));
        nodes.add(node("套餐到期：长期"));
        int n = ExternalInfoNode.removeFrom(nodes);
        assertEquals(2, n);
        assertEquals(1, nodes.size());
        assertEquals("HK-01", nodes.get(0).getName());
    }

    private static CanonicalExternalNode node(String name) {
        CanonicalExternalNode n = new CanonicalExternalNode();
        n.setName(name);
        return n;
    }
}
