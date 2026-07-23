package com.v2board.api.payment.driver;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EpusdtDriverTest {

    @Test
    void makeSignature_skipsEmptyAndIsOrderIndependent() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("order_id", "T1");
        a.put("amount", 12.5);
        a.put("network", "");
        a.put("pid", "1");

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("pid", "1");
        b.put("amount", 12.5);
        b.put("order_id", "T1");
        b.put("network", "");

        String sa = EpusdtDriver.makeSignature(a, "secret");
        String sb = EpusdtDriver.makeSignature(b, "secret");
        assertEquals(sa, sb);
        assertEquals(32, sa.length());
    }

    @Test
    void makeSignature_changesWithToken() {
        Map<String, Object> params = Map.of("order_id", "T1", "amount", 1);
        assertNotEquals(
                EpusdtDriver.makeSignature(params, "a"),
                EpusdtDriver.makeSignature(params, "b"));
    }
}
