package com.v2board.api.payment.driver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.PaymentDriver;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.StripeClient;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * 对齐 PHP App\Payments\StripeALL
 * 支持 3 种支付方式: alipay, wechat_pay, cards
 * pay(): PaymentIntents API (alipay/wechat_pay) 或 Checkout Sessions (cards)
 * notify(): Stripe Webhook 验签 (payment_intent.succeeded / checkout.session.completed)
 */
public class StripeALLDriver implements PaymentDriver {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String currency = (String) config.get("currency");
        String skLive = (String) config.get("stripe_sk_live");
        String paymentMethod = (String) config.get("payment_method");
        if (currency == null || skLive == null || paymentMethod == null) {
            throw new BusinessException(500, "StripeALL configuration is invalid");
        }

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
            StripeClient client = new StripeClient(skLive);

            if ("cards".equalsIgnoreCase(paymentMethod)) {
                return payWithCards(client, currency, amount, tradeNo, returnUrl, userId);
            } else {
                return payWithPaymentIntent(client, paymentMethod, currency, amount, tradeNo, returnUrl, userId);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "StripeALL error: " + e.getMessage());
        }
    }

    private Map<String, Object> payWithPaymentIntent(StripeClient client, String method,
                                                      String currency, long amount, String tradeNo,
                                                      String returnUrl, String userId) throws Exception {
        // 创建 PaymentMethod
        PaymentMethodCreateParams pmParams = PaymentMethodCreateParams.builder()
                .setType(PaymentMethodCreateParams.Type.valueOf(method.toUpperCase().replace("_", "_")))
                .build();

        // 使用原始 API 创建 payment method
        Map<String, Object> pmMap = new HashMap<>();
        pmMap.put("type", method.toLowerCase());
        com.stripe.model.PaymentMethod pm = client.paymentMethods().create(
                PaymentMethodCreateParams.builder()
                        .setType(toPaymentMethodType(method))
                        .build()
        );

        // 创建 PaymentIntent
        String descriptor = "user-#" + userId + "-" + tradeNo.substring(Math.max(0, tradeNo.length() - 8));
        if (descriptor.length() > 22) descriptor = descriptor.substring(0, 22);

        PaymentIntentCreateParams.Builder piBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency.toLowerCase())
                .setConfirm(true)
                .setPaymentMethod(pm.getId())
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build())
                .setStatementDescriptor(descriptor)
                .putMetadata("user_id", userId)
                .putMetadata("out_trade_no", tradeNo)
                .setReturnUrl(returnUrl);

        if ("wechat_pay".equalsIgnoreCase(method)) {
            piBuilder.setPaymentMethodOptions(PaymentIntentCreateParams.PaymentMethodOptions.builder()
                    .setWechatPay(PaymentIntentCreateParams.PaymentMethodOptions.WechatPay.builder()
                            .setClient(PaymentIntentCreateParams.PaymentMethodOptions.WechatPay.Client.WEB)
                            .build())
                    .build());
        }

        PaymentIntent pi = client.paymentIntents().create(piBuilder.build());

        // 解析 next_action
        PaymentIntent.NextAction nextAction = pi.getNextAction();
        if (nextAction == null) {
            throw new BusinessException(500, "Payment requires further action but none provided");
        }

        Map<String, Object> result = new HashMap<>();
        if ("alipay".equalsIgnoreCase(method)) {
            PaymentIntent.NextAction.AlipayHandleRedirect alipayRedirect = nextAction.getAlipayHandleRedirect();
            if (alipayRedirect == null || alipayRedirect.getUrl() == null) {
                throw new BusinessException(500, "Alipay redirect URL not available");
            }
            result.put("type", 1);
            result.put("data", alipayRedirect.getUrl());
        } else if ("wechat_pay".equalsIgnoreCase(method)) {
            PaymentIntent.NextAction.WechatPayDisplayQrCode wechatQr = nextAction.getWechatPayDisplayQrCode();
            if (wechatQr == null) {
                throw new BusinessException(500, "WeChat Pay QR code not available");
            }
            result.put("type", 0);
            result.put("data", wechatQr.getData());
        } else {
            // 其它方式默认尝试获取 redirect URL
            PaymentIntent.NextAction.RedirectToUrl redirectToUrl = nextAction.getRedirectToUrl();
            if (redirectToUrl != null) {
                result.put("type", 1);
                result.put("data", redirectToUrl.getUrl());
            } else {
                throw new BusinessException(500, "No redirect URL or QR code available for method: " + method);
            }
        }

        return result;
    }

    private Map<String, Object> payWithCards(StripeClient client, String currency, long amount,
                                              String tradeNo, String returnUrl, String userId) throws Exception {
        String productName = "user-#" + userId + "-" + tradeNo.substring(Math.max(0, tradeNo.length() - 8));

        SessionCreateParams params = SessionCreateParams.builder()
                .setSuccessUrl(returnUrl)
                .setClientReferenceId(tradeNo)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency.toLowerCase())
                                .setUnitAmount(amount)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(productName)
                                        .build())
                                .build())
                        .setQuantity(1L)
                        .build())
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setInvoiceCreation(SessionCreateParams.InvoiceCreation.builder()
                        .setEnabled(true)
                        .build())
                .setCustomerEmail(null) // PHP 版从用户表获取 email
                .build();

        Session session = client.checkout().sessions().create(params);
        String url = session.getUrl();
        if (url == null || url.isEmpty()) {
            throw new BusinessException(500, "Failed to create Stripe Checkout session");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", 1);
        result.put("data", url);
        return result;
    }

    private PaymentMethodCreateParams.Type toPaymentMethodType(String method) {
        return switch (method.toLowerCase()) {
            case "alipay" -> PaymentMethodCreateParams.Type.ALIPAY;
            case "wechat_pay" -> PaymentMethodCreateParams.Type.WECHAT_PAY;
            default -> PaymentMethodCreateParams.Type.CARD;
        };
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

        // PaymentIntent 成功 (alipay / wechat_pay)
        if ("payment_intent.succeeded".equals(eventType)) {
            try {
                StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
                if (obj instanceof PaymentIntent pi) {
                    if (!"succeeded".equals(pi.getStatus())) return null;
                    Map<String, String> metadata = pi.getMetadata();
                    String outTradeNo = metadata != null ? metadata.get("out_trade_no") : null;
                    if (outTradeNo != null) {
                        Map<String, String> result = new HashMap<>();
                        result.put("trade_no", outTradeNo);
                        result.put("callback_no", pi.getId());
                        return result;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Checkout Session 完成 (cards)
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

        // 异步支付成功
        if ("checkout.session.async_payment_succeeded".equals(eventType)) {
            try {
                StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
                if (obj instanceof Session session) {
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
