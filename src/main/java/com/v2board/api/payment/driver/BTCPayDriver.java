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
 * 对齐 PHP App\Payments\BTCPay
 * pay(): POST JSON to BTCPay Server REST API → redirect checkoutLink
 * notify(): HMAC-SHA256 验签 via Btcpay-Sig header
 */
public class BTCPayDriver implements PaymentDriver {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String btcpayUrl = (String) config.get("btcpay_url");
        String storeId = (String) config.get("btcpay_storeId");
        String apiKey = (String) config.get("btcpay_api_key");
        if (btcpayUrl == null || storeId == null || apiKey == null) {
            throw new BusinessException(500, "BTCPay configuration is invalid");
        }

        String tradeNo = String.valueOf(order.get("trade_no"));
        Object totalAmountObj = order.get("total_amount");
        double totalAmountYuan = 0;
        if (totalAmountObj instanceof Number) {
            totalAmountYuan = ((Number) totalAmountObj).doubleValue() / 100.0;
        }
        String notifyUrl = String.valueOf(order.get("notify_url"));
        String returnUrl = String.valueOf(order.get("return_url"));

        // 构建请求体
        Map<String, Object> body = new HashMap<>();
        body.put("amount", totalAmountYuan);
        body.put("currency", "CNY");
        body.put("metadata", Map.of("orderId", tradeNo));
        Map<String, Object> checkout = new HashMap<>();
        checkout.put("redirectURL", returnUrl);
        checkout.put("speedPolicy", "LowSpeed");
        body.put("checkout", checkout);
        body.put("notificationUrl", notifyUrl);

        String url = btcpayUrl + "api/v1/stores/" + storeId + "/invoices";

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "token " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new BusinessException(500, "BTCPay request failed: " + response.statusCode());
            }

            Map<String, Object> respBody = objectMapper.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});
            String checkoutLink = (String) respBody.get("checkoutLink");
            if (checkoutLink == null || checkoutLink.isEmpty()) {
                throw new BusinessException(500, "BTCPay response missing checkoutLink");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("type", 1); // redirect URL
            result.put("data", checkoutLink);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "BTCPay error: " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        String webhookKey = (String) config.get("btcpay_webhook_key");
        String btcpayUrl = (String) config.get("btcpay_url");
        String storeId = (String) config.get("btcpay_storeId");
        String apiKey = (String) config.get("btcpay_api_key");
        if (webhookKey == null || btcpayUrl == null || storeId == null || apiKey == null) {
            return null;
        }

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

        // Btcpay-Sig header: sha256=<hex>
        String sigHeader = request.getHeader("Btcpay-Sig");
        if (sigHeader == null || !sigHeader.startsWith("sha256=")) return null;
        String expectedSig = sigHeader.substring("sha256=".length());

        // HMAC-SHA256 验签
        String computedSig = hmacSha256Hex(webhookKey, payload);
        if (!expectedSig.equalsIgnoreCase(computedSig)) {
            return null;
        }

        try {
            Map<String, Object> body = objectMapper.readValue(payload,
                    new TypeReference<Map<String, Object>>() {});

            String invoiceId = (String) body.get("invoiceId");
            if (invoiceId == null) return null;

            // 查询 invoice 详情以获取 orderId
            String url = btcpayUrl + "api/v1/stores/" + storeId + "/invoices/" + invoiceId;
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "token " + apiKey)
                    .GET().build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> invoice = objectMapper.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});

            // 检查状态
            String status = (String) invoice.get("status");
            if (!"Settled".equalsIgnoreCase(status) && !"Processing".equalsIgnoreCase(status)) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) invoice.get("metadata");
            String orderId = metadata != null ? String.valueOf(metadata.get("orderId")) : null;
            if (orderId == null) return null;

            Map<String, String> result = new HashMap<>();
            result.put("trade_no", orderId);
            result.put("callback_no", invoiceId);
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
