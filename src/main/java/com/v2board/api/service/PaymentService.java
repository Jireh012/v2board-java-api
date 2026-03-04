package com.v2board.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.PaymentMapper;
import com.v2board.api.model.Payment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

        // 支付宝当面付：直接调用支付宝开放平台，返回二维码链接
        if ("AlipayF2F".equalsIgnoreCase(method)) {
            return payWithAlipayF2F(config, payload);
        }

        // 聚合支付（MGate）实现：直接拉起第三方网关，返回 URL
        if ("MGate".equalsIgnoreCase(method)) {
            return payWithMGate(config, payload);
        }

        // 其它支付方式暂时保持兼容：返回网关标识与 payload，由前端或后续驱动处理
        Map<String, Object> result = new HashMap<>();
        result.put("type", 0);
        Map<String, Object> data = new HashMap<>();
        data.put("gateway", payment.getPayment());
        data.put("payload", payload);
        result.put("data", data);
        return result;
    }

    public List<Map<String, Object>> form(String method, Long paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException(500, "Payment method is not available");
        }
        Map<String, Object> config = parseConfig(payment.getConfig());
        // 这里不做具体表单定义，仅把配置原样返回，方便前端构建管理表单。
        return Collections.singletonList(config);
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

