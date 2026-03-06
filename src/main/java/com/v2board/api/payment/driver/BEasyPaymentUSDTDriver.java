package com.v2board.api.payment.driver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.PaymentDriver;
import jakarta.servlet.http.HttpServletRequest;

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
 * 对齐 PHP App\Payments\BEasyPaymentUSDT
 * pay(): MD5 签名 → POST to /api/v1/order/create-transaction → redirect URL
 * notify(): MD5 验签
 */
public class BEasyPaymentUSDTDriver implements PaymentDriver {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String bepusdtUrl = (String) config.get("bepusdt_url");
        String apiToken = (String) config.get("bepusdt_apitoken");
        String tradeType = (String) config.get("bepusdt_trade_type");
        if (bepusdtUrl == null || apiToken == null) {
            throw new BusinessException(500, "BEasyPaymentUSDT configuration is invalid");
        }
        if (tradeType == null || tradeType.isEmpty()) tradeType = "1";

        String tradeNo = String.valueOf(order.get("trade_no"));
        Object totalAmountObj = order.get("total_amount");
        double totalAmountYuan = 0;
        if (totalAmountObj instanceof Number) {
            totalAmountYuan = ((Number) totalAmountObj).doubleValue() / 100.0;
        }
        String notifyUrl = String.valueOf(order.get("notify_url"));
        String returnUrl = String.valueOf(order.get("return_url"));

        // 构建签名参数
        TreeMap<String, String> signParams = new TreeMap<>();
        signParams.put("trade_type", tradeType);
        signParams.put("order_id", tradeNo);
        signParams.put("amount", String.format("%.2f", totalAmountYuan));
        signParams.put("notify_url", notifyUrl);
        signParams.put("redirect_url", returnUrl);

        // MD5(ksort(params) + token)
        StringBuilder signStr = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : signParams.entrySet()) {
            if (!first) signStr.append('&');
            first = false;
            signStr.append(entry.getKey()).append('=').append(entry.getValue());
        }
        signStr.append(apiToken);
        String sign = md5Hex(signStr.toString());

        signParams.put("signature", sign);

        // POST 请求
        String url = bepusdtUrl + (bepusdtUrl.endsWith("/") ? "" : "/") + "api/v1/order/create-transaction";

        try {
            String jsonBody = objectMapper.writeValueAsString(signParams);
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new BusinessException(500, "BEasyPaymentUSDT request failed: " + response.statusCode());
            }

            Map<String, Object> respBody = objectMapper.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});

            // 检查状态码
            Object codeObj = respBody.get("status_code");
            if (codeObj instanceof Number && ((Number) codeObj).intValue() != 200) {
                String msg = (String) respBody.get("message");
                throw new BusinessException(500, "BEasyPaymentUSDT error: " + msg);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            String paymentUrl = data != null ? (String) data.get("payment_url") : null;
            if (paymentUrl == null || paymentUrl.isEmpty()) {
                throw new BusinessException(500, "BEasyPaymentUSDT response missing payment_url");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("type", 1); // redirect URL
            result.put("data", paymentUrl);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "BEasyPaymentUSDT error: " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        String apiToken = (String) config.get("bepusdt_apitoken");
        if (apiToken == null) return null;

        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });

        String signature = params.remove("signature");
        if (signature == null) return null;

        // 检查状态
        String status = params.get("status");
        if (!"2".equals(status)) return null;

        // MD5(ksort(params_without_signature) + token) 验签
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder signStr = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) signStr.append('&');
            first = false;
            signStr.append(entry.getKey()).append('=').append(entry.getValue());
        }
        signStr.append(apiToken);
        String expectedSign = md5Hex(signStr.toString());

        if (!signature.equals(expectedSign)) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        result.put("trade_no", params.get("order_id"));
        result.put("callback_no", params.get("trade_id") != null ? params.get("trade_id") : "");
        return result;
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
