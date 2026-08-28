package com.v2board.api.payment.driver;

import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.PaymentDriver;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 对齐 PHP App\Payments\Paytaro
 * 网关写死 https://v3.paytaro.com ，type 固定 alipay。
 */
public class PaytaroDriver implements PaymentDriver {

    static final String GATEWAY_URL = "https://v3.paytaro.com";

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String pid = trim(config.get("pid"));
        String key = trim(config.get("key"));
        if (pid.isEmpty() || key.isEmpty()) {
            throw new BusinessException(500, "Paytaro configuration is invalid");
        }

        String tradeNo = String.valueOf(order.get("trade_no"));
        Object totalAmountObj = order.get("total_amount");
        double totalAmountYuan = 0;
        if (totalAmountObj instanceof Number) {
            totalAmountYuan = ((Number) totalAmountObj).doubleValue() / 100.0;
        }
        Map<String, String> params = new HashMap<>();
        params.put("pid", pid);
        params.put("type", "alipay");
        params.put("out_trade_no", tradeNo);
        params.put("notify_url", String.valueOf(order.get("notify_url")));
        params.put("return_url", String.valueOf(order.get("return_url")));
        params.put("name", tradeNo);
        params.put("money", String.format(Locale.US, "%.2f", totalAmountYuan));

        params.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isEmpty());
        String sign = sign(params, key);
        params.put("sign", sign);
        params.put("sign_type", "MD5");

        StringBuilder query = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                query.append('&');
            }
            first = false;
            query.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", 1);
        result.put("data", GATEWAY_URL + "/submit.php?" + query);
        return result;
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        String key = trim(config.get("key"));
        if (key.isEmpty()) {
            return null;
        }

        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, values) -> {
            if (values != null && values.length > 0) {
                params.put(k, values[0]);
            }
        });

        String sign = params.get("sign");
        if (sign == null || sign.isEmpty()) {
            return null;
        }
        if (!sign.equalsIgnoreCase(sign(params, key))) {
            return null;
        }
        if (!"TRADE_SUCCESS".equals(params.get("trade_status"))) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        result.put("trade_no", params.get("out_trade_no"));
        result.put("callback_no", params.get("trade_no"));
        result.put("custom_result", "success");
        return result;
    }

    static String sign(Map<String, String> params, String secret) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            if ("sign".equals(k) || "sign_type".equals(k) || v == null || v.isEmpty()) {
                continue;
            }
            sorted.put(k, v);
        }
        StringBuilder signStr = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) {
                signStr.append('&');
            }
            first = false;
            signStr.append(entry.getKey()).append('=').append(entry.getValue());
        }
        signStr.append(secret);
        return md5Hex(signStr.toString());
    }

    private static String trim(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(500, "MD5 algorithm not available");
        }
    }
}
