package com.v2board.api.controller.payment;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.PaymentMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.Payment;
import com.v2board.api.service.OrderService;
import com.v2board.api.service.TelegramService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * 支付回调接口，对齐 PHP /api/v1/guest/payment/notify/{method}/{uuid}
     * 检查 payment 是否启用，将订单标记为已支付(status=1)，然后异步处理开通。
     */
    @PostMapping("/notify/{method}/{uuid}")
    public ApiResponse<Map<String, Object>> notify(HttpServletRequest request,
                                                   @PathVariable("method") String method,
                                                   @PathVariable("uuid") String uuid) {
        Payment payment = paymentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Payment>()
                        .eq(Payment::getPayment, method)
                        .eq(Payment::getUuid, uuid)
        );
        if (payment == null || payment.getEnable() == null || payment.getEnable() != 1) {
            throw new BusinessException(500, "gate is not enable");
        }
        String tradeNo = request.getParameter("trade_no");
        if (tradeNo == null || tradeNo.isEmpty()) {
            throw new BusinessException(500, "Invalid trade_no");
        }
        String callbackNo = request.getParameter("callback_no");
        Order order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getTradeNo, tradeNo)
        );
        if (order == null) {
            throw new BusinessException(500, "Order does not exist");
        }
        if (order.getStatus() != 0) {
            // 已处理过的订单直接返回成功
            Map<String, Object> data = new HashMap<>();
            data.put("trade_no", tradeNo);
            data.put("status", order.getStatus());
            return ApiResponse.success(data);
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

        Map<String, Object> data = new HashMap<>();
        data.put("trade_no", tradeNo);
        data.put("status", order.getStatus());
        return ApiResponse.success(data);
    }
}

