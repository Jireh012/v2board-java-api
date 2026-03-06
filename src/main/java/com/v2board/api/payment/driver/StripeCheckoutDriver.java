package com.v2board.api.payment.driver;

import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.PaymentDriver;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * 对齐 PHP App\Payments\StripeCheckout
 * pay(): Stripe Checkout Session API → redirect URL
 * notify(): Stripe Webhook 验签 (checkout.session.completed)
 */
public class StripeCheckoutDriver implements PaymentDriver {

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String currency = (String) config.get("currency");
        String skLive = (String) config.get("stripe_sk_live");
        if (currency == null || skLive == null) {
            throw new BusinessException(500, "StripeCheckout configuration is invalid");
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
            SessionCreateParams params = SessionCreateParams.builder()
                    .setSuccessUrl(returnUrl)
                    .setClientReferenceId(tradeNo)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(currency.toLowerCase())
                                    .setUnitAmount(amount)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("user-#" + userId + "-" + tradeNo.substring(Math.max(0, tradeNo.length() - 8)))
                                            .build())
                                    .build())
                            .setQuantity(1L)
                            .build())
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .build();

            Session session = Session.create(params);
            String sessionUrl = session.getUrl();
            if (sessionUrl == null || sessionUrl.isEmpty()) {
                throw new BusinessException(500, "Failed to create Stripe Checkout session");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("type", 1); // redirect URL
            result.put("data", sessionUrl);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "Stripe Checkout error: " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        String webhookKey = (String) config.get("stripe_webhook_key");
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

        String sigHeader = request.getHeader("Stripe-Signature");
        if (sigHeader == null) return null;

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookKey);
        } catch (Exception e) {
            return null;
        }

        String eventType = event.getType();

        if ("checkout.session.completed".equals(eventType)) {
            try {
                StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
                if (obj instanceof Session session) {
                    if (!"paid".equals(session.getPaymentStatus())) return null;
                    Map<String, String> result = new HashMap<>();
                    result.put("trade_no", session.getClientReferenceId());
                    result.put("callback_no", session.getPaymentIntent());
                    return result;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }
}
