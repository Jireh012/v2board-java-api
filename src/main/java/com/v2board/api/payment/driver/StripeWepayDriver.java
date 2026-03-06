package com.v2board.api.payment.driver;

import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.PaymentDriver;
import com.stripe.Stripe;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * 对齐 PHP App\Payments\StripeWepay
 * pay(): Stripe Source API (type=wechat) → QR code URL
 * notify(): 同 StripeAlipay（source.chargeable + charge.succeeded）
 */
public class StripeWepayDriver implements PaymentDriver {

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String currency = (String) config.get("currency");
        String skLive = (String) config.get("stripe_sk_live");
        if (currency == null || skLive == null) {
            throw new BusinessException(500, "StripeWepay configuration is invalid");
        }

        Stripe.apiKey = skLive;
        double exchangeRate = StripeUtil.exchange("CNY", currency.toUpperCase());

        String tradeNo = String.valueOf(order.get("trade_no"));
        Object totalAmountObj = order.get("total_amount");
        long amount = 0;
        if (totalAmountObj instanceof Number) {
            amount = (long) Math.floor(((Number) totalAmountObj).doubleValue() * exchangeRate);
        }
        String returnUrl = String.valueOf(order.get("return_url"));
        String userId = String.valueOf(order.get("user_id"));

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("amount", amount);
            params.put("currency", currency.toLowerCase());
            params.put("type", "wechat");
            params.put("statement_descriptor", tradeNo);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("user_id", userId);
            metadata.put("out_trade_no", tradeNo);
            metadata.put("identifier", "");
            params.put("metadata", metadata);

            Map<String, String> redirect = new HashMap<>();
            redirect.put("return_url", returnUrl);
            params.put("redirect", redirect);

            Source source = Source.create(params);
            // Stripe SDK 没有直接的 getWechat() 方法，使用 toJson() 解析
            String sourceJson = source.toJson();
            String qrCodeUrl = null;
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var tree = mapper.readTree(sourceJson);
                var wechatNode = tree.get("wechat");
                if (wechatNode != null && wechatNode.has("qr_code_url")) {
                    qrCodeUrl = wechatNode.get("qr_code_url").asText();
                }
            } catch (Exception ignored) {}
            if (qrCodeUrl == null || qrCodeUrl.isEmpty()) {
                throw new BusinessException(500, "Failed to create Stripe WeChat source");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("type", 0); // QR code
            result.put("data", qrCodeUrl);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "Stripe WeChat error: " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        // 与 StripeAlipay 完全相同的 notify 逻辑
        String webhookKey = (String) config.get("stripe_webhook_key");
        String skLive = (String) config.get("stripe_sk_live");
        if (webhookKey == null || skLive == null) return null;

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

        String sigHeader = request.getHeader("Stripe-Signature");
        if (sigHeader == null) return null;

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookKey);
        } catch (Exception e) {
            return null;
        }

        Stripe.apiKey = skLive;
        String eventType = event.getType();

        if ("source.chargeable".equals(eventType)) {
            try {
                StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
                if (obj instanceof Source src) {
                    Map<String, Object> chargeParams = new HashMap<>();
                    chargeParams.put("amount", src.getAmount());
                    chargeParams.put("currency", src.getCurrency());
                    chargeParams.put("source", src.getId());
                    chargeParams.put("metadata", src.getMetadata());
                    Charge.create(chargeParams);
                }
            } catch (Exception ignored) {}
            return Map.of("__processing", "true");
        }

        if ("charge.succeeded".equals(eventType)) {
            try {
                StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
                if (obj instanceof Charge charge) {
                    if (!"succeeded".equals(charge.getStatus())) return null;
                    Map<String, String> metadata = charge.getMetadata();
                    String outTradeNo = metadata != null ? metadata.get("out_trade_no") : null;
                    if (outTradeNo == null && charge.getSource() != null) {
                        Source src = Source.retrieve(charge.getSource().getId());
                        if (src != null && src.getMetadata() != null) {
                            outTradeNo = src.getMetadata().get("out_trade_no");
                        }
                    }
                    if (outTradeNo != null) {
                        Map<String, String> result = new HashMap<>();
                        result.put("trade_no", outTradeNo);
                        result.put("callback_no", charge.getId());
                        return result;
                    }
                }
            } catch (Exception ignored) {}
        }

        return null;
    }
}
