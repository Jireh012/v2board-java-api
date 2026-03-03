package com.v2board.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.PaymentMapper;
import com.v2board.api.model.Payment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${v2board.app-url:}")
    private String appUrl;

    /**
     * 模拟 PHP PaymentService::pay 的行为，主要是拼装 notify_url / return_url 及订单信息。
     * 不直接对接第三方网关，由上层根据返回数据跳转或发起支付。
     */
    public Map<String, Object> pay(String method, Long paymentId, Map<String, Object> order) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException(500, "Payment method is not available");
        }
        if (payment.getEnable() == null || payment.getEnable() != 1) {
            throw new BusinessException(500, "Payment method is not enable");
        }
        Map<String, Object> config = parseConfig(payment.getConfig());
        config.put("enable", payment.getEnable());
        config.put("id", payment.getId());
        config.put("uuid", payment.getUuid());
        config.put("notify_domain", payment.getNotifyDomain());

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

        Map<String, Object> result = new HashMap<>();
        result.put("gateway", payment.getPayment());
        result.put("payload", payload);
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
}

