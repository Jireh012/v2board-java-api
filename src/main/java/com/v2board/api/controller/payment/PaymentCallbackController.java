package com.v2board.api.controller.payment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.PaymentMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.Payment;
import com.v2board.api.payment.PaymentDriver;
import com.v2board.api.payment.PaymentDriverFactory;
import com.v2board.api.service.OrderService;
import com.v2board.api.service.TelegramService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/guest/payment")
public class PaymentCallbackController {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private PaymentDriverFactory driverFactory;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 支付回调接口，对齐 PHP /api/v1/guest/payment/notify/{method}/{uuid}
     * 通过 PaymentDriverFactory 获取对应驱动，验证回调签名，验签通过后标记订单已支付。
     */
    @PostMapping("/notify/{method}/{uuid}")
    public void notify(HttpServletRequest request, HttpServletResponse response,
                       @PathVariable("method") String method,
                       @PathVariable("uuid") String uuid) throws IOException {
        Payment payment = paymentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Payment>()
                        .eq(Payment::getPayment, method)
                        .eq(Payment::getUuid, uuid)
        );
        if (payment == null || payment.getEnable() == null || payment.getEnable() != 1) {
            response.setStatus(500);
            response.getWriter().write("gate is not enable");
            return;
        }

        // 解析支付网关配置
        Map<String, Object> config;
        try {
            config = objectMapper.readValue(payment.getConfig(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            config = new HashMap<>();
        }

        // 通过驱动工厂验签
        PaymentDriver driver = driverFactory.getDriver(method);
        Map<String, String> notifyResult = driver.notify(config, request);

        if (notifyResult == null) {
            response.setStatus(400);
            response.getWriter().write("verify failed");
            return;
        }

        // __processing 标记表示 Stripe 两步验签的中间态（source.chargeable），直接返回成功
        if ("true".equals(notifyResult.get("__processing"))) {
            response.setStatus(200);
            writeSuccessResponse(response, method);
            return;
        }

        String tradeNo = notifyResult.get("trade_no");
        String callbackNo = notifyResult.get("callback_no");
        if (tradeNo == null || tradeNo.isEmpty()) {
            response.setStatus(400);
            response.getWriter().write("trade_no is empty");
            return;
        }

        Order order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getTradeNo, tradeNo)
        );
        if (order == null) {
            response.setStatus(500);
            response.getWriter().write("order not found");
            return;
        }

        if (order.getStatus() != 0) {
            // 已处理过的订单直接返回成功
            response.setStatus(200);
            writeSuccessResponse(response, method);
            return;
        }

        // 标记为已支付（status=1），由 handleOrderAsync 完成开通（status→3）
        long now = System.currentTimeMillis() / 1000;
        order.setStatus(1);
        order.setPaidAt(now);
        if (callbackNo != null && !callbackNo.isEmpty()) {
            order.setCallbackNo(callbackNo);
        }
        order.setUpdatedAt(now);
        orderMapper.updateById(order);

        // 异步处理订单开通
        orderService.handleOrderAsync(tradeNo);

        // Telegram 通知管理员
        try {
            String msg = "\uD83D\uDCB0 新订单支付成功\n"
                    + "订单号: " + tradeNo + "\n"
                    + "金额: " + (order.getTotalAmount() != null ? order.getTotalAmount() / 100.0 : 0) + " 元\n"
                    + "支付方式: " + method;
            telegramService.sendMessageWithAdmin(msg);
        } catch (Exception ignored) {
            // 通知失败不影响主流程
        }

        response.setStatus(200);
        writeSuccessResponse(response, method);
    }

    /**
     * 根据支付方式返回合适的成功响应。
     * 不同支付网关期望不同的成功响应格式。
     */
    private void writeSuccessResponse(HttpServletResponse response, String method) throws IOException {
        if ("WechatPayNative".equalsIgnoreCase(method)) {
            // 微信支付期望 XML 响应
            response.setContentType("application/xml; charset=UTF-8");
            response.getWriter().write(
                    "<xml><return_code><![CDATA[SUCCESS]]></return_code>"
                            + "<return_msg><![CDATA[OK]]></return_msg></xml>");
        } else if ("EPay".equalsIgnoreCase(method)) {
            // EPay 期望纯文本 "success"
            response.setContentType("text/plain");
            response.getWriter().write("success");
        } else if (method != null && method.startsWith("Stripe")) {
            // Stripe 期望 200 即可
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"success\"}");
        } else {
            // 默认返回 JSON
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"success\"}");
        }
    }
}
