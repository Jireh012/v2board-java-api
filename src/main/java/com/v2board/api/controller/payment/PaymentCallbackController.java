package com.v2board.api.controller.payment;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.PaymentMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.Payment;
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

    /**
     * 支付回调占位接口，对齐 PHP /api/v1/guest/payment/notify/{method}/{uuid}
     * 这里只做最小化验签：检查 payment 是否启用，然后将对应订单标记为已支付。
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
        Order order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getTradeNo, tradeNo)
        );
        if (order == null) {
            throw new BusinessException(500, "Order does not exist");
        }
        order.setStatus(3);
        orderMapper.updateById(order);
        Map<String, Object> data = new HashMap<>();
        data.put("trade_no", tradeNo);
        data.put("status", order.getStatus());
        return ApiResponse.success(data);
    }
}

