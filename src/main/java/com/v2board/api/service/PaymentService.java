package com.v2board.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.PaymentMapper;
import com.v2board.api.model.Payment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.v2board.api.payment.PaymentDriver;
import com.v2board.api.payment.PaymentDriverFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

@Service
public class PaymentService {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentDriverFactory driverFactory;

    @Value("${v2board.app-url:}")
    private String appUrl;

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public Map<String, Object> pay(String method, Long paymentId, Map<String, Object> order) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException(500, "Payment method is not available");
        }
        if (payment.getEnable() == null || payment.getEnable() != 1) {
            throw new BusinessException(500, "Payment method is not enable");
        }
        String tradeNo = String.valueOf(order.get("trade_no"));
        Object totalAmount = order.get("total_amount");

        String notifyUrl = buildNotifyUrl(method, payment.getUuid(), payment.getNotifyDomain());
        String returnUrl = buildReturnUrl(tradeNo);

        Map<String, Object> payload = new HashMap<>();
        payload.put("notify_url", notifyUrl);
        payload.put("return_url", returnUrl);
        payload.put("trade_no", tradeNo);
        payload.put("total_amount", totalAmount);
        payload.put("user_id", order.get("user_id"));
        payload.put("stripe_token", order.get("stripe_token"));

        Map<String, Object> config = parseConfig(payment.getConfig());

        // 支付宝当面付：保留原有实现（含 RSA2 签名）
        if ("AlipayF2F".equalsIgnoreCase(method)) {
            return payWithAlipayF2F(config, payload);
        }

        // 聚合支付（MGate）：保留原有实现
        if ("MGate".equalsIgnoreCase(method)) {
            return payWithMGate(config, payload);
        }

        // 其它所有驱动通过 PaymentDriverFactory 分发
        PaymentDriver driver = driverFactory.getDriver(method);
        return driver.pay(config, payload);
    }

    /**
     * 返回所有已注册的支付驱动名称列表。对齐 PHP getPaymentMethods()。
     */
    public List<String> getPaymentMethods() {
        return List.of(
                "AlipayF2F", "WechatPayNative", "EPay", "MGate",
                "StripeAlipay", "StripeWepay", "StripeCredit", "StripeCheckout", "StripeALL",
                "BTCPay", "Coinbase", "CoinPayments", "BEasyPaymentUSDT"
        );
    }

    /**
     * 返回指定支付驱动的表单定义 + 当前配置值。对齐 PHP PaymentService::form()。
     */
    public List<Map<String, Object>> form(String method, Long paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException(500, "Payment method is not available");
        }
        Map<String, Object> config = parseConfig(payment.getConfig());
        Map<String, Object> formDef = getFormDefinition(method);
        // 把当前存储值填入表单定义
        for (Map.Entry<String, Object> entry : formDef.entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> field = (Map<String, Object>) entry.getValue();
                field.put("value", config.getOrDefault(entry.getKey(), ""));
            }
        }
        return Collections.singletonList(formDef);
    }

    /**
     * 根据支付驱动名返回表单字段定义。对齐各 PHP Payments/*.php 的 form() 方法。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getFormDefinition(String method) {
        if (method == null) return new HashMap<>();
        return switch (method) {
            case "AlipayF2F" -> buildForm(
                    field("app_id", "支付宝APPID", ""),
                    field("private_key", "支付宝私钥", ""),
                    field("public_key", "支付宝公钥", ""),
                    field("product_name", "自定义商品名称", "将会体现在支付宝账单中")
            );
            case "WechatPayNative" -> buildForm(
                    field("app_id", "APPID", "绑定微信支付商户的APPID"),
                    field("mch_id", "商户号", "微信支付商户号"),
                    field("api_key", "APIKEY(v1)", "")
            );
            case "EPay" -> buildForm(
                    field("url", "URL", ""),
                    field("pid", "PID", ""),
                    field("key", "KEY", "")
            );
            case "MGate" -> buildForm(
                    field("mgate_url", "API地址", ""),
                    field("mgate_app_id", "APPID", ""),
                    field("mgate_app_secret", "AppSecret", ""),
                    field("mgate_source_currency", "源货币", "默认CNY")
            );
            case "StripeAlipay" -> buildForm(
                    field("currency", "货币单位", ""),
                    field("stripe_sk_live", "SK_LIVE", ""),
                    field("stripe_webhook_key", "WebHook密钥签名", "")
            );
            case "StripeWepay" -> buildForm(
                    field("currency", "货币单位", ""),
                    field("stripe_sk_live", "SK_LIVE", ""),
                    field("stripe_webhook_key", "WebHook密钥签名", "")
            );
            case "StripeCredit" -> buildForm(
                    field("currency", "货币单位", ""),
                    field("stripe_sk_live", "SK_LIVE", ""),
                    field("stripe_pk_live", "PK_LIVE", ""),
                    field("stripe_webhook_key", "WebHook密钥签名", "")
            );
            case "StripeCheckout" -> buildForm(
                    field("currency", "货币单位", ""),
                    field("stripe_sk_live", "SK_LIVE", "API 密钥"),
                    field("stripe_pk_live", "PK_LIVE", "API 公钥"),
                    field("stripe_webhook_key", "WebHook 密钥签名", "")
            );
            case "StripeALL" -> buildForm(
                    field("currency", "货币单位", "请使用符合ISO 4217标准的三位字母，例如GBP"),
                    field("stripe_sk_live", "SK_LIVE", ""),
                    field("stripe_webhook_key", "WebHook密钥签名", "whsec_...."),
                    field("payment_method", "支付方式", "请输入alipay, wechat_pay, cards")
            );
            case "BTCPay" -> buildForm(
                    field("btcpay_url", "API接口所在网址(包含最后的斜杠)", ""),
                    field("btcpay_storeId", "storeId", ""),
                    field("btcpay_api_key", "API KEY", "个人设置中的API KEY(非商店设置中的)"),
                    field("btcpay_webhook_key", "WEBHOOK KEY", "")
            );
            case "Coinbase" -> buildForm(
                    field("coinbase_url", "接口地址", ""),
                    field("coinbase_api_key", "API KEY", ""),
                    field("coinbase_webhook_key", "WEBHOOK KEY", "")
            );
            case "CoinPayments" -> buildForm(
                    field("coinpayments_merchant_id", "Merchant ID", "商户 ID"),
                    field("coinpayments_ipn_secret", "IPN Secret", "通知密钥"),
                    field("coinpayments_currency", "货币代码", "填写您的货币代码（大写）")
            );
            case "BEasyPaymentUSDT" -> buildForm(
                    field("bepusdt_url", "API 地址", "您的 BEPUSDT API 接口地址(例如: https://xxx.com)"),
                    field("bepusdt_apitoken", "API Token", "您的 BEPUSDT API Token"),
                    field("bepusdt_trade_type", "交易类型", "您的 BEPUSDT 交易类型")
            );
            default -> new HashMap<>();
        };
    }

    private Map<String, Object> field(String key, String label, String description) {
        Map<String, Object> f = new HashMap<>();
        f.put("_key", key);
        f.put("label", label);
        f.put("description", description);
        f.put("type", "input");
        return f;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildForm(Map<String, Object>... fields) {
        Map<String, Object> form = new java.util.LinkedHashMap<>();
        for (Map<String, Object> f : fields) {
            String key = (String) f.remove("_key");
            form.put(key, f);
        }
        return form;
    }

    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String buildNotifyUrl(String method, String uuid, String notifyDomain) {
        if (uuid == null || uuid.isEmpty()) {
            throw new BusinessException(500, "notify uuid is empty");
        }
        String path = "/api/v1/guest/payment/notify/" + method + "/" + uuid;
        String base = appUrl;
        if (notifyDomain != null && !notifyDomain.isEmpty()) {
            base = notifyDomain;
        }
        if (base == null || base.isEmpty()) {
            return path;
        }
        try {
            URI uri = new URI(base);
            return uri.resolve(path).toString();
        } catch (URISyntaxException e) {
            return base + path;
        }
    }

    private String buildReturnUrl(String tradeNo) {
        if (appUrl == null || appUrl.isEmpty()) {
            return "/#/order/" + tradeNo;
        }
        try {
            URI uri = new URI(appUrl);
            return uri.resolve("/#/order/" + tradeNo).toString();
        } catch (URISyntaxException e) {
            return appUrl + "/#/order/" + tradeNo;
        }
    }

    /**
     * 对齐 PHP App\\Payments\\MGate::pay
     */
    private Map<String, Object> payWithMGate(Map<String, Object> config, Map<String, Object> order) {
        String baseUrl = (String) config.get("mgate_url");
        String appId = (String) config.get("mgate_app_id");
        String appSecret = (String) config.get("mgate_app_secret");
        if (baseUrl == null || baseUrl.isEmpty() || appId == null || appSecret == null) {
            throw new BusinessException(500, "MGate configuration is invalid");
        }

        Map<String, Object> params = new TreeMap<>();
        params.put("out_trade_no", order.get("trade_no"));
        params.put("total_amount", order.get("total_amount"));
        params.put("notify_url", order.get("notify_url"));
        params.put("return_url", order.get("return_url"));
        if (config.get("mgate_source_currency") != null) {
            params.put("source_currency", config.get("mgate_source_currency"));
        }
        params.put("app_id", appId);

        String queryForSign = buildQuery(params) + appSecret;
        params.put("sign", md5Hex(queryForSign));

        String requestBody = buildQuery(params);
        String url = baseUrl.endsWith("/") ? baseUrl + "v1/gateway/fetch" : baseUrl + "/v1/gateway/fetch";

        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "MGate")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new BusinessException(500, "Network error when requesting payment gateway");
        }

        if (response.statusCode() >= 400) {
            throw new BusinessException(500, "Payment gateway request failed with status " + response.statusCode());
        }

        Map<String, Object> body;
        try {
            body = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException(500, "Invalid response from payment gateway");
        }

        // PHP 版会优先从 errors 或 message 中取错误
        Object errors = body.get("errors");
        if (errors instanceof Map) {
            Map<?, ?> errMap = (Map<?, ?>) errors;
            Object firstKey = errMap.keySet().stream().findFirst().orElse(null);
            if (firstKey != null) {
                Object msgArr = errMap.get(firstKey);
                if (msgArr instanceof List && !((List<?>) msgArr).isEmpty()) {
                    throw new BusinessException(500, String.valueOf(((List<?>) msgArr).get(0)));
                }
            }
        }
        if (body.get("message") instanceof String && body.get("code") instanceof Number
                && ((Number) body.get("code")).intValue() != 200) {
            throw new BusinessException(500, (String) body.get("message"));
        }

        Object dataObj = body.get("data");
        if (!(dataObj instanceof Map)) {
            throw new BusinessException(500, "Payment gateway response missing data");
        }
        Map<?, ?> data = (Map<?, ?>) dataObj;
        Object payUrl = data.get("pay_url");
        if (payUrl == null) {
            throw new BusinessException(500, "Payment gateway response missing pay_url");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", 1); // 1: redirect/url
        result.put("data", String.valueOf(payUrl));
        return result;
    }

    private String buildQuery(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(encode(entry.getKey()))
              .append('=')
              .append(encode(String.valueOf(entry.getValue())));
        }
        return sb.toString();
    }

    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(encode(entry.getKey()))
              .append('=')
              .append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String hex = Integer.toHexString((b & 0xff));
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(500, "MD5 algorithm not available");
        }
    }

    /**
     * 对齐 PHP App\\Payments\\AlipayF2F::pay
     */
    private Map<String, Object> payWithAlipayF2F(Map<String, Object> config, Map<String, Object> order) {
        String appId = (String) config.get("app_id");
        String privateKey = (String) config.get("private_key");
        String productName = (String) config.get("product_name");
        if (appId == null || privateKey == null) {
            throw new BusinessException(500, "AlipayF2F configuration is invalid");
        }

        String subject = productName;
        if (subject == null || subject.isEmpty()) {
            subject = "V2Board - 订阅";
        }

        Map<String, String> params = new TreeMap<>();
        params.put("app_id", appId);
        params.put("method", "alipay.trade.precreate");
        params.put("charset", "UTF-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        params.put("version", "1.0");
        params.put("_input_charset", "UTF-8");

        // biz_content
        Map<String, Object> bizContent = new HashMap<>();
        bizContent.put("subject", subject);
        bizContent.put("out_trade_no", order.get("trade_no"));
        Object totalAmountObj = order.get("total_amount");
        double totalAmountYuan = 0;
        if (totalAmountObj instanceof Number) {
            totalAmountYuan = ((Number) totalAmountObj).doubleValue() / 100.0;
        }
        bizContent.put("total_amount", totalAmountYuan);

        try {
            params.put("biz_content", objectMapper.writeValueAsString(bizContent));
        } catch (Exception e) {
            throw new BusinessException(500, "Failed to build Alipay biz_content");
        }

        Object notifyUrlObj = order.get("notify_url");
        if (notifyUrlObj != null) {
            params.put("notify_url", String.valueOf(notifyUrlObj));
        }

        // 签名：按支付宝规范对未编码的 key=value&... 字符串做 RSA2 签名
        String contentToSign = buildSignContent(params);
        String sign = signWithPrivateKey(privateKey, contentToSign);
        params.put("sign", sign);

        // 构造请求 URL（参数需要进行 URL 编码）
        String query = buildQueryString(params);
        String url = "https://openapi.alipay.com/gateway.do?" + query;

        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new BusinessException(500, "Network error when requesting Alipay");
        }

        if (response.statusCode() >= 400) {
            throw new BusinessException(500, "Alipay request failed with status " + response.statusCode());
        }

        Map<String, Object> body;
        try {
            body = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException(500, "Invalid response from Alipay");
        }

        // 响应结构：{ "alipay_trade_precreate_response": { ... } }
        String respKey = "alipay_trade_precreate_response";
        Object respObj = body.get(respKey);
        if (!(respObj instanceof Map)) {
            throw new BusinessException(500, "Invalid Alipay response structure");
        }
        Map<?, ?> resp = (Map<?, ?>) respObj;
        Object code = resp.get("code");
        if (!"10000".equals(String.valueOf(code))) {
            Object sub = resp.get("sub_msg");
            String subMsg = sub != null ? String.valueOf(sub) : "Alipay error";
            throw new BusinessException(500, subMsg);
        }
        Object qr = resp.get("qr_code");
        if (qr == null) {
            throw new BusinessException(500, "Alipay response missing qr_code");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", 0); // 二维码
        result.put("data", String.valueOf(qr));
        return result;
    }

    private String buildSignContent(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isEmpty() || "sign".equals(key)) {
                continue;
            }
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(key).append('=').append(value);
        }
        return sb.toString();
    }

    private String signWithPrivateKey(String privateKey, String content) {
        try {
            String pem = privateKey.trim();

            PrivateKey pk;
            if (pem.contains("BEGIN")) {
                // PEM 格式（兼容 PKCS#1: BEGIN RSA PRIVATE KEY 以及 PKCS#8: BEGIN PRIVATE KEY）
                try (PEMParser parser = new PEMParser(new java.io.StringReader(pem))) {
                    Object obj = parser.readObject();
                    JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
                    if (obj instanceof PEMKeyPair) {
                        pk = converter.getKeyPair((PEMKeyPair) obj).getPrivate();
                    } else if (obj instanceof PrivateKeyInfo) {
                        pk = converter.getPrivateKey((PrivateKeyInfo) obj);
                    } else {
                        throw new BusinessException(500, "Unsupported private key format");
                    }
                }
            } else {
                // 纯 base64，按 PKCS#8 处理
                byte[] decoded = java.util.Base64.getDecoder().decode(pem);
                pk = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
            }

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(pk);
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new BusinessException(500, "Failed to sign Alipay request: " + e.getMessage());
        }
    }
}

