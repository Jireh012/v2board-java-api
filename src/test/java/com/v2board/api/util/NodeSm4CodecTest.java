package com.v2board.api.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NodeSm4CodecTest {

    private final NodeSm4Codec codec = new NodeSm4Codec(new ObjectMapper());

    @Test
    void deriveWorkingKey_isSha256First16Bytes() throws Exception {
        String token = "abcdefghijklmnopqrstuvwxyz";
        byte[] expected = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(Arrays.copyOf(expected, 16), NodeSm4Codec.deriveWorkingKey(token));
    }

    @Test
    void identityQuery_roundTrip() {
        String token = "1234567890123456";
        String e = codec.encryptIdentityQuery(token, 7L, "vn");
        assertFalse(e.contains("token"));
        assertFalse(e.contains(token));
        assertTrue(e.contains("."));

        byte[] key = NodeSm4Codec.deriveWorkingKey(token);
        NodeSm4Codec.NodeIdentity id = codec.decryptIdentityQuery(e, key);
        assertEquals(token, id.k());
        assertEquals(7L, id.nodeId());
        assertEquals("vn", id.typeCode());
    }

    @Test
    void bodyEnvelope_roundTrip() {
        String token = "1234567890123456";
        byte[] key = NodeSm4Codec.deriveWorkingKey(token);
        Map<String, Object> data = Map.of("users", java.util.List.of(Map.of("id", 1)));
        Map<String, String> env = codec.encryptBody(data, key);
        assertEquals(2, env.size());
        assertTrue(env.containsKey("iv"));
        assertTrue(env.containsKey("payload"));
        String json = codec.decryptBodyToJson(env, key);
        assertTrue(json.contains("\"users\""));
    }

    @Test
    void wrongKey_failsDecrypt() {
        String token = "1234567890123456";
        String e = codec.encryptIdentityQuery(token, 1L, "vn");
        byte[] wrong = NodeSm4Codec.deriveWorkingKey("xxxxxxxxxxxxxxxx");
        assertThrows(Exception.class, () -> codec.decryptIdentityQuery(e, wrong));
    }

    @Test
    void decryptBody_rejectsPlaintextMapWithoutEnvelope() {
        String token = "1234567890123456";
        byte[] key = NodeSm4Codec.deriveWorkingKey(token);
        assertThrows(IllegalArgumentException.class,
                () -> codec.decryptBodyToJson(Map.of("1", "plaintext"), key));
    }

    @Test
    void nodeTypeCodes_vnMapsToV2node() {
        assertEquals("v2node", NodeTypeCodes.toNodeType("vn"));
        assertEquals("vn", NodeTypeCodes.toCode("v2node"));
        assertEquals("shadowsocks", NodeTypeCodes.toNodeType("ss"));
    }
}
