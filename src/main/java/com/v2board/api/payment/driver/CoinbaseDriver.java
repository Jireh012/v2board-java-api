package com.v2board.api.payment.driver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.PaymentDriver;
import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 对齐 PHP App\Payments\Coinbase
 * pay(): POST to Coinbase Commerce → hosted_url
 * notify(): HMAC-SHA256 验签 via X-Cc-Webhook-Signature header
 */
public class CoinbaseDriver implements PaymentDriver {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String coinbaseUrl = (String) config.get("coinbase_url");
        String apiKey = (String) config.get("coinbase_api_key");
        if (coinbaseUrl == null || apiKey == null) {
            throw new BusinessException(500, "Coinbase configuration is invalid");
        }

        String tradeNo = String.valueOf(order.get("trade_no"));
        Object totalAmountObj = order.get("total_amount");
        double totalAmountYuan = 0;
        if (totalAmountObj instanceof Number) {
            totalAmountYuan = ((Number) totalAmountObj).doubleValue() / 100.0;
        }
        String returnUrl = String.valueOf(order.get("return_url"));

        Map<String, Object> body = new HashMap<>();
        body.put("name", tradeNo);
        body.put("description", tradeNo);

        Map<String, Object> pricing = new HashMap<>();
        pricing.put("local", Map.of("amount", String.format("%.2f", totalAmountYuan), "currency", "CNY"));
        body.put("pricing_type", "fixed_price");
        body.put("local_price", Map.of("amount", String.format("%.2f", totalAmountYuan), "currency", "CNY"));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("out_trade_no", tradeNo);
        body.put("metadata", metadata);
        body.put("redirect_url", returnUrl);
        body.put("cancel_url", returnUrl);

        String url = coinbaseUrl + (coinbaseUrl.endsWith("/") ? "" : "/") + "charges";

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-CC-Api-Key", apiKey)
                    .header("X-CC-Version", "2018-03-22")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new BusinessException(500, "Coinbase request failed: " + response.statusCode());
            }

            Map<String, Object> respBody = objectMapper.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            String hostedUrl = data != null ? (String) data.get("hosted_url") : null;
            if (hostedUrl == null || hostedUrl.isEmpty()) {
                throw new BusinessException(500, "Coinbase response missing hosted_url");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("type", 1); // redirect URL
            result.put("data", hostedUrl);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "Coinbase error: " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        String webhookKey = (String) config.get("coinbase_webhook_key");
        if (webhookKey == null) return null;

        String payload;
        try {
            StringBuilder sb = new StringBuilder();
            var reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            payload = sb.toString();
        } catch (Exception e) {
            return null;
        }

        // X-Cc-Webhook-Signature header
        String sigHeader = request.getHeader("X-Cc-Webhook-Signature");
        if (sigHeader == null) return null;

        // HMAC-SHA256 验签
        String computedSig = hmacSha256Hex(webhookKey, payload);
        if (!sigHeader.equalsIgnoreCase(computedSig)) {
            return null;
        }

        try {
            Map<String, Object> body = objectMapper.readValue(payload,
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) body.get("event");
            if (event == null) return null;

            String type = (String) event.get("type");
            if (!"charge:confirmed".equals(type) && !"charge:resolved".equals(type)) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) event.get("data");
            if (data == null) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
            String outTradeNo = metadata != null ? (String) metadata.get("out_trade_no") : null;
            String callbackNo = (String) data.get("code");
            if (outTradeNo == null) return null;

            Map<String, String> result = new HashMap<>();
            result.put("trade_no", outTradeNo);
            result.put("callback_no", callbackNo != null ? callbackNo : "");
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private String hmacSha256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString((b & 0xff));
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
