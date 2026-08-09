package com.v2board.api.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PanelSm4SupportTest {

    @Test
    void compactRoundTrip_forXaHeader() {
        byte[] key = Sm4Util.parseKey("0123456789abcdef");
        String jwt = "eyJhbGciOiJIUzI1NiJ9.payload.sig";
        String compact = Sm4Util.encryptToCompact(jwt, key);
        assertEquals(jwt, Sm4Util.decryptFromCompact(compact, key));
        String again = Sm4Util.encryptToCompact(jwt, key);
        assertNotEquals(compact, again);
    }

    @Test
    void envelopeRoundTrip_forResponseBody() throws Exception {
        byte[] key = Sm4Util.parseKey("0123456789abcdef");
        String json = new ObjectMapper().writeValueAsString(Map.of("code", 0, "message", "success", "data", Map.of("ok", true)));
        Map<String, String> env = Sm4Util.encryptToEnvelope(json, key);
        assertEquals(2, env.size());
        String plain = Sm4Util.decryptFromEnvelope(env.get("iv"), env.get("payload"), key);
        assertEquals(json, plain);
    }
}
