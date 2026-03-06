package com.v2board.api.payment.driver;

import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.PaymentDriver;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Source;
import com.stripe.model.Charge;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * 对齐 PHP App\Payments\StripeAlipay
 * pay(): Stripe Source API (type=alipay) → redirect URL
 * notify(): Stripe Webhook 验签，处理 source.chargeable + charge.succeeded
 */
public class StripeAlipayDriver implements PaymentDriver {

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String currency = (String) config.get("currency");
        String skLive = (String) config.get("stripe_sk_live");
        if (currency == null || skLive == null) {
            throw new BusinessException(500, "StripeAlipay configuration is invalid");
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
            params.put("type", "alipay");
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
            Source.Redirect redirectObj = source.getRedirect();
            String redirectUrl = redirectObj != null ? redirectObj.getUrl() : null;
            if (redirectUrl == null || redirectUrl.isEmpty()) {
                throw new BusinessException(500, "Failed to create Stripe Alipay source");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("type", 1); // redirect URL
            result.put("data", redirectUrl);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "Stripe Alipay error: " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        String webhookKey = (String) config.get("stripe_webhook_key");
        String skLive = (String) config.get("stripe_sk_live");
        if (webhookKey == null || skLive == null) {
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
            // 创建 Charge，不返回 trade_no（等 charge.succeeded 事件）
            try {
                EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
                StripeObject obj = deserializer.getObject().orElse(null);
                if (obj instanceof Source src) {
                    Map<String, Object> chargeParams = new HashMap<>();
                    chargeParams.put("amount", src.getAmount());
                    chargeParams.put("currency", src.getCurrency());
                    chargeParams.put("source", src.getId());
                    chargeParams.put("metadata", src.getMetadata());
                    Charge.create(chargeParams);
                }
            } catch (Exception ignored) {}
            // 返回 PROCESSING 标记，表示需要继续等待
            return Map.of("__processing", "true");
        }

        if ("charge.succeeded".equals(eventType)) {
            try {
                EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
                StripeObject obj = deserializer.getObject().orElse(null);
                if (obj instanceof Charge charge) {
                    if (!"succeeded".equals(charge.getStatus())) return null;
                    Map<String, String> metadata = charge.getMetadata();
                    String outTradeNo = metadata != null ? metadata.get("out_trade_no") : null;
                    if (outTradeNo == null && charge.getSource() != null) {
                        // 从 source metadata 获取
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
