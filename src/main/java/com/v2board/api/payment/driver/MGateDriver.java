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
 * 对齐 PHP App\Payments\MGate
 * pay(): 已在 PaymentService 中实现
 * notify(): MD5(ksort(params_without_sign_urlencoded) + app_secret) 验签
 */
public class MGateDriver implements PaymentDriver {

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        throw new BusinessException(500, "MGate pay() should be called via PaymentService");
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });

        String sign = params.remove("sign");
        if (sign == null || sign.isEmpty()) {
            return null;
        }

        String appSecret = (String) config.get("mgate_app_secret");
        if (appSecret == null || appSecret.isEmpty()) {
            return null;
        }

        // 按 key 排序，URL 编码，拼接 app_secret
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) sb.append('&');
            first = false;
            sb.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        sb.append(appSecret);
        String expectedSign = md5Hex(sb.toString());

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
