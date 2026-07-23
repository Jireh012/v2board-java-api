package com.v2board.api.payment.driver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.PaymentDriver;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 对齐 PHP App\Payments\Epusdt（GMPay / epusdt）
 * pay(): 签名 → create-transaction；可选 switch-network → redirect URL
 * notify(): MD5 验签，status==2 成功，custom_result=ok
 */
public class EpusdtDriver implements PaymentDriver {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String baseUrl = trimSlash((String) config.get("epusdt_url"));
        String pid = trim((String) config.get("epusdt_pid"));
        String token = trim((String) config.get("epusdt_token"));
        if (baseUrl == null || baseUrl.isEmpty() || pid.isEmpty() || token.isEmpty()) {
            throw new BusinessException(500, "Epusdt configuration is invalid");
        }

        String networkRaw = trim((String) config.get("epusdt_network")).toLowerCase(Locale.ROOT);
        String asset = trim((String) config.get("epusdt_asset"));
        String currency = trim((String) config.get("epusdt_currency"));
        String tokenName = asset.isEmpty() ? "usdt" : asset.toLowerCase(Locale.ROOT);
        if (currency.isEmpty()) {
            currency = "cny";
        } else {
            currency = currency.toLowerCase(Locale.ROOT);
        }

        String tradeNo = String.valueOf(order.get("trade_no"));
        double amountYuan = 0;
        Object totalAmountObj = order.get("total_amount");
        if (totalAmountObj instanceof Number) {
            amountYuan = Math.round(((Number) totalAmountObj).doubleValue()) / 100.0;
        }

        Map<String, Object> params = new TreeMap<>();
        params.put("pid", pid);
        params.put("order_id", tradeNo);
        params.put("currency", currency);
        params.put("token", tokenName);
        params.put("network", networkRaw.isEmpty() ? "tron" : networkRaw);
        params.put("amount", amountYuan);
        params.put("notify_url", String.valueOf(order.get("notify_url")));
        params.put("redirect_url", String.valueOf(order.get("return_url")));
        params.put("signature", makeSignature(params, token));

        Map<String, Object> createResult = postJson(
                baseUrl + "/payments/gmpay/v1/order/create-transaction", params);
        assertOk(createResult, "epusdt create order failed");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) createResult.get("data");
        String paymentUrl = data != null ? str(data.get("payment_url")) : null;

        if (!networkRaw.isEmpty()) {
            String tradeId = data != null ? str(data.get("trade_id")) : "";
            if (tradeId.isEmpty()) {
                throw new BusinessException(500, "epusdt create order response missing trade_id");
            }
            Map<String, Object> switchParams = new HashMap<>();
            switchParams.put("trade_id", tradeId);
            switchParams.put("token", tokenName);
            switchParams.put("network", networkRaw);
            Map<String, Object> switchResult = postJson(baseUrl + "/pay/switch-network", switchParams);
            assertOk(switchResult, "epusdt switch network failed");
            @SuppressWarnings("unchecked")
            Map<String, Object> switchData = (Map<String, Object>) switchResult.get("data");
            if (switchData != null && switchData.get("payment_url") != null) {
                paymentUrl = str(switchData.get("payment_url"));
            }
        }

        if (paymentUrl == null || paymentUrl.isEmpty()) {
            throw new BusinessException(500, "epusdt payment url missing");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", 1);
        result.put("data", paymentUrl);
        return result;
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        String token = trim((String) config.get("epusdt_token"));
        if (token.isEmpty()) {
            return null;
        }

        Map<String, Object> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });

        Object signatureObj = params.remove("signature");
        if (signatureObj == null) {
            return null;
        }
        String signature = String.valueOf(signatureObj).toLowerCase(Locale.ROOT);
        String expected = makeSignature(params, token);
        if (!MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }

        Object statusObj = params.get("status");
        int status;
        try {
            status = Integer.parseInt(String.valueOf(statusObj));
        } catch (Exception e) {
            return null;
        }
        if (status != 2) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        result.put("trade_no", str(params.get("order_id")));
        result.put("callback_no", str(params.get("trade_id")));
        result.put("custom_result", "ok");
        return result;
    }

    /**
     * 对齐 PHP makeSignature：ksort，跳过空值，数值格式化后 md5(pairs + token).toLowerCase
     */
    static String makeSignature(Map<String, ?> params, String token) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, ?> e : params.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if ("signature".equals(key) || value == null) {
                continue;
            }
            String formatted;
            if (value instanceof Float || value instanceof Double) {
                formatted = formatNumber(((Number) value).doubleValue());
            } else if (value instanceof Number) {
                formatted = formatNumber(((Number) value).doubleValue());
            } else {
                formatted = String.valueOf(value);
            }
            if (formatted.isEmpty()) {
                continue;
            }
            sorted.put(key, formatted);
        }
        StringBuilder pairs = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (!first) {
                pairs.append('&');
            }
            first = false;
            pairs.append(e.getKey()).append('=').append(e.getValue());
        }
        pairs.append(token);
        return md5Hex(pairs.toString()).toLowerCase(Locale.ROOT);
    }

    private static String formatNumber(double value) {
        // 对齐 PHP: rtrim(rtrim(sprintf('%.12F', $value), '0'), '.')
        String s = String.format(Locale.US, "%.12f", value);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.isEmpty() ? "0" : s;
    }

    private Map<String, Object> postJson(String url, Map<String, Object> body) {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "epusdt")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "epusdt request failed: " + e.getMessage());
        }
    }

    private static void assertOk(Map<String, Object> result, String fallback) {
        Object code = result.get("status_code");
        int status = code instanceof Number n ? n.intValue() : -1;
        if (status != 200) {
            Object message = result.get("message");
            throw new BusinessException(500, message != null ? String.valueOf(message) : fallback);
        }
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

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String trimSlash(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
