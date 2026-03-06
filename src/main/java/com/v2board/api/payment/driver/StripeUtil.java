package com.v2board.api.payment.driver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Stripe 驱动公共工具方法
 */
public final class StripeUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private StripeUtil() {}

    /**
     * 对齐 PHP exchange() 方法，将 CNY 金额转换为目标货币。
     * 使用 exchangerate-api.com 作为主源，frankfurter.app 作为备用源。
     */
    public static double exchange(String from, String to) {
        if (from.equalsIgnoreCase(to)) return 1.0;

        HttpClient client = HttpClient.newBuilder().build();

        // 主源: exchangerate-api.com
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.exchangerate-api.com/v4/latest/" + from.toUpperCase()))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> body = objectMapper.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                Map<String, Object> rates = (Map<String, Object>) body.get("rates");
                if (rates != null && rates.get(to.toUpperCase()) != null) {
                    return ((Number) rates.get(to.toUpperCase())).doubleValue();
                }
            }
        } catch (Exception ignored) {}

        // 备用源: frankfurter.app
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.frankfurter.app/latest?from=" + from.toUpperCase()
                            + "&to=" + to.toUpperCase()))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> body = objectMapper.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                Map<String, Object> rates = (Map<String, Object>) body.get("rates");
                if (rates != null && rates.get(to.toUpperCase()) != null) {
                    return ((Number) rates.get(to.toUpperCase())).doubleValue();
                }
            }
        } catch (Exception ignored) {}

        throw new BusinessException(500, "Currency exchange failed: " + from + " -> " + to);
    }
}
