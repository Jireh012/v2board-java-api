package com.v2board.api.service.external;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalSubscribeDedupeTest {

    @Test
    void dedupe_keepsFirstByMergeOrder_crossSourceSameKey() {
        List<Map<String, Object>> input = List.of(
                server("first", "vless", "h.com", 443, "u1"),
                server("second", "vless", "H.COM", 443, "u1"),
                server("other", "vless", "h.com", 443, "u2")
        );
        List<Map<String, Object>> out = ExternalSubscribeNodeService.dedupeAndNumberNames(input);
        assertEquals(2, out.size());
        assertEquals("first", out.get(0).get("name"));
        assertEquals("other", out.get(1).get("name"));
    }

    @Test
    void numberNames_appendsCountersOnlyWhenColliding() {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(server("DE", "vless", "a.com", 443, "u1"));
        input.add(server("DE", "vless", "b.com", 443, "u2"));
        input.add(server("unique", "vless", "c.com", 443, "u3"));

        List<Map<String, Object>> out = ExternalSubscribeNodeService.dedupeAndNumberNames(input);
        assertEquals(3, out.size());
        assertEquals("DE1", out.get(0).get("name"));
        assertEquals("DE2", out.get(1).get("name"));
        assertEquals("unique", out.get(2).get("name"));
        assertEquals("DE1", ((Map<?, ?>) out.get(0).get("singbox_outbound")).get("tag"));
        assertEquals("DE1", ((Map<?, ?>) out.get(0).get("clash_proxy")).get("name"));
    }

    @Test
    void parser_dedupesTlsVariantsWithinSource() {
        String clash = """
                proxies:
                  - name: DE chrome
                    type: vless
                    server: ddc.example.com
                    port: 443
                    uuid: 5dc56757-458e-4df2-9e71-2a14ce47e8af
                    tls: true
                    client-fingerprint: chrome
                    udp: true
                  - name: DE edge
                    type: vless
                    server: ddc.example.com
                    port: 443
                    uuid: 5dc56757-458e-4df2-9e71-2a14ce47e8af
                    tls: true
                    client-fingerprint: edge
                    udp: true
                  - name: other
                    type: vless
                    server: other.example.com
                    port: 443
                    uuid: 11111111-1111-1111-1111-111111111111
                    tls: true
                    udp: true
                """;
        ExternalSubscribeParser parser = new ExternalSubscribeParser();
        List<CanonicalExternalNode> nodes = parser.parse(clash);
        assertEquals(2, nodes.size());
        assertEquals("DE chrome", nodes.get(0).getName());
        assertEquals("other", nodes.get(1).getName());
        assertEquals(nodes.get(0).getFingerprint(),
                ExternalNodeIdentity.fingerprint(nodes.get(0).getSingboxOutbound()));
    }

    private static Map<String, Object> server(String name, String type, String host, int port, String uuid) {
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", type);
        outbound.put("server", host);
        outbound.put("server_port", port);
        outbound.put("uuid", uuid);
        outbound.put("tag", name);
        Map<String, Object> clash = new LinkedHashMap<>();
        clash.put("name", name);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("singbox_outbound", outbound);
        map.put("clash_proxy", clash);
        map.put("share_uri", "vless://" + uuid + "@" + host + ":" + port + "#" + name);
        return map;
    }
}
