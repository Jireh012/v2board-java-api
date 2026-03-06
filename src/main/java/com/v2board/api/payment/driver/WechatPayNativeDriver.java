package com.v2board.api.payment.driver;

import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.PaymentDriver;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 对齐 PHP App\Payments\WechatPayNative
 * pay(): 微信支付 Native 下单（统一下单 API v2，XML 格式）
 * notify(): XML 解析 + MD5 签名验证
 */
public class WechatPayNativeDriver implements PaymentDriver {

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String appId = (String) config.get("app_id");
        String mchId = (String) config.get("mch_id");
        String apiKey = (String) config.get("api_key");
        if (appId == null || mchId == null || apiKey == null) {
            throw new BusinessException(500, "WechatPayNative configuration is invalid");
        }

        String tradeNo = String.valueOf(order.get("trade_no"));
        Object totalAmountObj = order.get("total_amount");
        int totalFen = 0;
        if (totalAmountObj instanceof Number) {
            totalFen = ((Number) totalAmountObj).intValue();
        }
        String notifyUrl = String.valueOf(order.get("notify_url"));

        String nonceStr = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        // 构建签名参数
        TreeMap<String, String> params = new TreeMap<>();
        params.put("appid", appId);
        params.put("mch_id", mchId);
        params.put("nonce_str", nonceStr);
        params.put("body", "V2Board - 订阅");
        params.put("out_trade_no", tradeNo);
        params.put("total_fee", String.valueOf(totalFen));
        params.put("spbill_create_ip", "127.0.0.1");
        params.put("notify_url", notifyUrl);
        params.put("trade_type", "NATIVE");

        // MD5 签名
        String sign = wechatSign(params, apiKey);
        params.put("sign", sign);

        // 构建 XML
        StringBuilder xml = new StringBuilder("<xml>");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            xml.append("<").append(entry.getKey()).append(">")
               .append("<![CDATA[").append(entry.getValue()).append("]]>")
               .append("</").append(entry.getKey()).append(">");
        }
        xml.append("</xml>");

        // 发送请求
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mch.weixin.qq.com/pay/unifiedorder"))
                .header("Content-Type", "application/xml; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(xml.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new BusinessException(500, "Network error when requesting WeChat Pay");
        }

        String body = response.body();

        // 解析 XML 响应
        String returnCode = extractXml(body, "return_code");
        String resultCode = extractXml(body, "result_code");
        if (!"SUCCESS".equals(returnCode) || !"SUCCESS".equals(resultCode)) {
            String errMsg = extractXml(body, "return_msg");
            if (errMsg == null || errMsg.isEmpty()) errMsg = extractXml(body, "err_code_des");
            throw new BusinessException(500, "WeChat Pay error: " + errMsg);
        }

        String codeUrl = extractXml(body, "code_url");
        if (codeUrl == null || codeUrl.isEmpty()) {
            throw new BusinessException(500, "WeChat Pay response missing code_url");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", 0); // QR code
        result.put("data", codeUrl);
        return result;
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        String apiKey = (String) config.get("api_key");
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }

        String body;
        try {
            StringBuilder sb = new StringBuilder();
            var reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            body = sb.toString();
        } catch (Exception e) {
            return null;
        }

        if (body.isEmpty()) return null;

        // 解析 XML
        String returnCode = extractXml(body, "return_code");
        String resultCode = extractXml(body, "result_code");
        if (!"SUCCESS".equals(returnCode) || !"SUCCESS".equals(resultCode)) {
            return null;
        }

        String sign = extractXml(body, "sign");
        if (sign == null || sign.isEmpty()) return null;

        // 提取所有 XML 字段用于验签
        TreeMap<String, String> params = new TreeMap<>();
        String[] tags = {"appid", "mch_id", "nonce_str", "result_code", "return_code",
                "out_trade_no", "total_fee", "transaction_id", "time_end",
                "openid", "trade_type", "bank_type", "fee_type", "cash_fee",
                "cash_fee_type", "is_subscribe", "device_info"};
        for (String tag : tags) {
            String val = extractXml(body, tag);
            if (val != null && !val.isEmpty()) {
                params.put(tag, val);
            }
        }

        String expectedSign = wechatSign(params, apiKey);
        if (!sign.equalsIgnoreCase(expectedSign)) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        result.put("trade_no", params.get("out_trade_no"));
        result.put("callback_no", params.get("transaction_id"));
        return result;
    }

    /**
     * 返回微信回调成功的 XML 响应体
     */
    public static String successResponse() {
        return "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
    }

    private String wechatSign(TreeMap<String, String> params, String apiKey) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String v = entry.getValue();
            if (v == null || v.isEmpty() || "sign".equals(entry.getKey())) continue;
            if (!first) sb.append('&');
            first = false;
            sb.append(entry.getKey()).append('=').append(v);
        }
        sb.append("&key=").append(apiKey);
        return md5Hex(sb.toString()).toUpperCase();
    }

    private String extractXml(String xml, String tag) {
        String start1 = "<" + tag + ">";
        String end1 = "</" + tag + ">";
        String start2 = "<" + tag + "><![CDATA[";
        String end2 = "]]></" + tag + ">";

        int idx = xml.indexOf(start2);
        if (idx >= 0) {
            int endIdx = xml.indexOf(end2, idx);
            if (endIdx >= 0) {
                return xml.substring(idx + start2.length(), endIdx);
            }
        }
        idx = xml.indexOf(start1);
        if (idx >= 0) {
            int endIdx = xml.indexOf(end1, idx);
            if (endIdx >= 0) {
                return xml.substring(idx + start1.length(), endIdx);
            }
        }
        return null;
    }

    private String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String hex = Integer.toHexString((b & 0xff));
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(500, "MD5 algorithm not available");
        }
    }
}
