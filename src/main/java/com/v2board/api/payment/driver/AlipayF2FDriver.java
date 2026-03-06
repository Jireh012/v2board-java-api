package com.v2board.api.payment.driver;

import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.PaymentDriver;
import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 对齐 PHP App\Payments\AlipayF2F
 * pay(): 已在 PaymentService 中实现，这里复制过来
 * notify(): RSA2 验签
 */
public class AlipayF2FDriver implements PaymentDriver {

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        // 已在 PaymentService.payWithAlipayF2F 中实现，此处由 PaymentService 委托调用
        // 保留原有实现不动，通过 PaymentService 统一分发
        throw new BusinessException(500, "AlipayF2F pay() should be called via PaymentService");
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });

        String tradeStatus = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus)) {
            return null;
        }

        String sign = params.remove("sign");
        String signType = params.remove("sign_type");
        if (sign == null || sign.isEmpty()) {
            return null;
        }

        String publicKey = (String) config.get("public_key");
        if (publicKey == null || publicKey.isEmpty()) {
            return null;
        }

        // 按 key 排序，构建签名内容
        TreeMap<String, String> sortedParams = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isEmpty()) continue;
            if (!first) sb.append('&');
            first = false;
            sb.append(entry.getKey()).append('=').append(value);
        }
        String content = sb.toString();

        // RSA2 (SHA256withRSA) 验签
        try {
            String pem = publicKey.trim();
            if (!pem.contains("BEGIN")) {
                pem = "-----BEGIN PUBLIC KEY-----\n" + pem + "\n-----END PUBLIC KEY-----";
            }
            pem = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                      .replace("-----END PUBLIC KEY-----", "")
                      .replaceAll("\\s", "");
            byte[] keyBytes = java.util.Base64.getDecoder().decode(pem);
            java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(keyBytes);
            java.security.PublicKey pk = java.security.KeyFactory.getInstance("RSA").generatePublic(keySpec);

            java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
            signature.initVerify(pk);
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            boolean verified = signature.verify(java.util.Base64.getDecoder().decode(sign));
            if (!verified) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        result.put("trade_no", params.get("out_trade_no"));
        result.put("callback_no", params.get("trade_no"));
        return result;
    }
}
