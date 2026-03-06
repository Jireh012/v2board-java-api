package com.v2board.api.payment.driver;

import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.PaymentDriver;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 对齐 PHP App\Payments\EPay
 * pay(): 构建 MD5 签名 URL → 跳转
 * notify(): MD5 验签
 */
public class EPayDriver implements PaymentDriver {

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String url = (String) config.get("url");
        String pid = String.valueOf(config.get("pid"));
        String key = (String) config.get("key");
        if (url == null || pid == null || key == null) {
            throw new BusinessException(500, "EPay configuration is invalid");
        }

        String tradeNo = String.valueOf(order.get("trade_no"));
        Object totalAmountObj = order.get("total_amount");
        double totalAmountYuan = 0;
        if (totalAmountObj instanceof Number) {
            totalAmountYuan = ((Number) totalAmountObj).doubleValue() / 100.0;
        }
        String notifyUrl = String.valueOf(order.get("notify_url"));
        String returnUrl = String.valueOf(order.get("return_url"));

        // 签名参数（按 PHP 版本 ksort 排序）
        TreeMap<String, String> signParams = new TreeMap<>();
        signParams.put("pid", pid);
        signParams.put("type", "alipay");
        signParams.put("out_trade_no", tradeNo);
        signParams.put("notify_url", notifyUrl);
        signParams.put("return_url", returnUrl);
        signParams.put("name", tradeNo);
        signParams.put("money", String.format("%.2f", totalAmountYuan));

        // 构建签名字符串：key=value&key=value + key
        StringBuilder signStr = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : signParams.entrySet()) {
            if (!first) signStr.append('&');
            first = false;
            signStr.append(entry.getKey()).append('=').append(entry.getValue());
        }
        signStr.append(key);
        String sign = md5Hex(signStr.toString());

        // 构建跳转 URL
        StringBuilder query = new StringBuilder();
        first = true;
        for (Map.Entry<String, String> entry : signParams.entrySet()) {
            if (!first) query.append('&');
            first = false;
            query.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        query.append("&sign=").append(sign);
        query.append("&sign_type=MD5");

        String payUrl = url + (url.endsWith("/") ? "" : "/") + "submit.php?" + query;

        Map<String, Object> result = new HashMap<>();
        result.put("type", 1); // redirect URL
        result.put("data", payUrl);
        return result;
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        String key = (String) config.get("key");
        if (key == null || key.isEmpty()) {
            return null;
        }

        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, values) -> {
            if (values != null && values.length > 0) {
                params.put(k, values[0]);
            }
        });

        String sign = params.get("sign");
        String tradeStatus = params.get("trade_status");
        if (sign == null || !"TRADE_SUCCESS".equals(tradeStatus)) {
            return null;
        }

        // 签名参数（移除 sign, sign_type, 空值）
        TreeMap<String, String> signParams = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            if ("sign".equals(k) || "sign_type".equals(k) || v == null || v.isEmpty()) continue;
            signParams.put(k, v);
        }

        StringBuilder signStr = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : signParams.entrySet()) {
            if (!first) signStr.append('&');
            first = false;
            signStr.append(entry.getKey()).append('=').append(entry.getValue());
        }
        signStr.append(key);
        String expectedSign = md5Hex(signStr.toString());

        if (!sign.equals(expectedSign)) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        result.put("trade_no", params.get("out_trade_no"));
        result.put("callback_no", params.get("trade_no"));
        return result;
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String hex = Integer.toHexString((b & 0xff));
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(500, "MD5 algorithm not available");
        }
    }
}
