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
 * 对齐 PHP App\Payments\StripeCredit
 * pay(): Stripe Charge API (直接扣款，使用前端传入的 stripe_token) → type=2
 * notify(): Stripe Webhook 验签 (source.chargeable + charge.succeeded)
 */
public class StripeCreditDriver implements PaymentDriver {

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String currency = (String) config.get("currency");
        String skLive = (String) config.get("stripe_sk_live");
        if (currency == null || skLive == null) {
            throw new BusinessException(500, "StripeCredit configuration is invalid");
        }

        Stripe.apiKey = skLive;
        double exchangeRate = StripeUtil.exchange("CNY", currency.toUpperCase());

        String tradeNo = String.valueOf(order.get("trade_no"));
        Object totalAmountObj = order.get("total_amount");
        long amount = 0;
        if (totalAmountObj instanceof Number) {
            amount = (long) Math.floor(((Number) totalAmountObj).doubleValue() * exchangeRate);
        }
        String stripeToken = order.get("stripe_token") != null ? String.valueOf(order.get("stripe_token")) : null;
        if (stripeToken == null || stripeToken.isEmpty()) {
            throw new BusinessException(500, "Stripe token is required for credit card payment");
        }
        String userId = String.valueOf(order.get("user_id"));

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("amount", amount);
            params.put("currency", currency.toLowerCase());
            params.put("source", stripeToken);
            params.put("description", tradeNo);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("user_id", userId);
            metadata.put("out_trade_no", tradeNo);
            metadata.put("identifier", "");
            params.put("metadata", metadata);

            Charge charge = Charge.create(params);
            if (!"succeeded".equals(charge.getStatus())) {
                throw new BusinessException(500, "Stripe charge failed: " + charge.getStatus());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("type", 2); // direct charge
            result.put("data", charge.getId());
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "Stripe Credit error: " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
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
