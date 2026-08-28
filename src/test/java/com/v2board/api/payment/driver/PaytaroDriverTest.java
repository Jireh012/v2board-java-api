package com.v2board.api.payment.driver;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaytaroDriverTest {

    @Test
    void sign_skipsEmptyAndIsOrderIndependent() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("out_trade_no", "T1");
        a.put("money", "1.00");
        a.put("pid", "9");
        a.put("name", "");

        Map<String, String> b = new HashMap<>();
        b.put("pid", "9");
        b.put("money", "1.00");
        b.put("out_trade_no", "T1");
        b.put("name", "");

        assertEquals(PaytaroDriver.sign(a, "secret"), PaytaroDriver.sign(b, "secret"));
        assertEquals(32, PaytaroDriver.sign(a, "secret").length());
    }

    @Test
    void pay_usesFixedGatewayAndAlipayType() {
        PaytaroDriver driver = new PaytaroDriver();
        Map<String, Object> config = Map.of("pid", "1001", "key", "k");
        Map<String, Object> order = new HashMap<>();
        order.put("trade_no", "N1");
        order.put("total_amount", 1234L);
        order.put("notify_url", "https://example.com/n");
        order.put("return_url", "https://example.com/r");

        Map<String, Object> result = driver.pay(config, order);
        assertEquals(1, result.get("type"));
        String url = String.valueOf(result.get("data"));
        assertTrue(url.startsWith("https://v3.paytaro.com/submit.php?"));
        assertTrue(url.contains("type=alipay"));
        assertTrue(url.contains("money=12.34"));
        assertTrue(url.contains("sign_type=MD5"));
    }

    @Test
    void notify_acceptsValidSignature() {
        Map<String, String> params = new HashMap<>();
        params.put("pid", "1001");
        params.put("out_trade_no", "N1");
        params.put("trade_no", "P1");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("money", "12.34");
        String sign = PaytaroDriver.sign(params, "k");

        MockHttpServletRequest req = new MockHttpServletRequest();
        params.forEach(req::setParameter);
        req.setParameter("sign", sign);
        req.setParameter("sign_type", "MD5");

        PaytaroDriver driver = new PaytaroDriver();
        Map<String, String> out = driver.notify(Map.of("pid", "1001", "key", "k"), req);
        assertNotNull(out);
        assertEquals("N1", out.get("trade_no"));
        assertEquals("P1", out.get("callback_no"));
        assertEquals("success", out.get("custom_result"));
    }

    @Test
    void notify_rejectsBadSignOrStatus() {
        PaytaroDriver driver = new PaytaroDriver();
        MockHttpServletRequest badSign = new MockHttpServletRequest();
        badSign.setParameter("out_trade_no", "N1");
        badSign.setParameter("trade_status", "TRADE_SUCCESS");
        badSign.setParameter("sign", "deadbeef");
        assertNull(driver.notify(Map.of("key", "k"), badSign));

        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", "N1");
        params.put("trade_status", "WAIT");
        String sign = PaytaroDriver.sign(params, "k");
        MockHttpServletRequest wait = new MockHttpServletRequest();
        params.forEach(wait::setParameter);
        wait.setParameter("sign", sign);
        assertNull(driver.notify(Map.of("key", "k"), wait));
    }
}
