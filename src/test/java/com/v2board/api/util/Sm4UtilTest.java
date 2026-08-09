package com.v2board.api.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sm4UtilTest {

    @Test
    void roundTrip_utf8Key() {
        byte[] key = Sm4Util.parseKey("0123456789abcdef");
        String plain = "{\"app_name\":\"谜之站点\",\"stop_register\":1}";
        Map<String, String> env = Sm4Util.encryptToEnvelope(plain, key);
        String back = Sm4Util.decryptFromEnvelope(env.get("iv"), env.get("payload"), key);
        assertEquals(plain, back);
    }

    @Test
    void roundTrip_hexKey() {
        byte[] key = Sm4Util.parseKey("0123456789abcdeffedcba9876543210");
        String plain = "{\"email_verify\":0}";
        Map<String, String> a = Sm4Util.encryptToEnvelope(plain, key);
        Map<String, String> b = Sm4Util.encryptToEnvelope(plain, key);
        assertNotEquals(a.get("iv"), b.get("iv"));
        assertEquals(plain, Sm4Util.decryptFromEnvelope(a.get("iv"), a.get("payload"), key));
        assertEquals(plain, Sm4Util.decryptFromEnvelope(b.get("iv"), b.get("payload"), key));
    }

    @Test
    void parseKey_rejectsBadLength() {
        assertThrows(IllegalArgumentException.class, () -> Sm4Util.parseKey("short"));
        assertThrows(IllegalArgumentException.class, () -> Sm4Util.parseKey(""));
    }

    @Test
    void compact_roundTrip() {
        byte[] key = Sm4Util.parseKey("0123456789abcdef");
        String plain = "{\"k\":\"tok\",\"i\":1,\"t\":\"vn\"}";
        String compact = Sm4Util.encryptToCompact(plain, key);
        assertTrue(compact.contains("."));
        assertFalse(compact.contains("="));
        assertEquals(plain, Sm4Util.decryptFromCompact(compact, key));
    }
}
