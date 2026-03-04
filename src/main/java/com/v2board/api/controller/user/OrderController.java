package com.v2board.api.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.PaymentMapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.Payment;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import com.v2board.api.service.OrderService;
import com.v2board.api.service.PaymentService;
import com.v2board.api.util.Helper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user/order")
public class OrderController {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private OrderService orderService;
    
    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 对齐 PHP User\\OrderController::fetch
     */
    @GetMapping("/fetch")
    public ApiResponse<List<Map<String, Object>>> fetch(HttpServletRequest request,
                                                        @RequestParam(value = "status", required = false) Integer status) {
        User user = requireUser(request);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, user.getId())
                .orderByDesc(Order::getCreatedAt);
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        List<Order> orders = orderMapper.selectList(wrapper);
        List<Plan> plans = planMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, Plan> planMap = new HashMap<>();
        for (Plan p : plans) {
            planMap.put(p.getId(), p);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Order o : orders) {
            Map<String, Object> row = new HashMap<>();
            row.put("trade_no", o.getTradeNo());
            row.put("plan_id", o.getPlanId());
            row.put("period", o.getPeriod());
            row.put("total_amount", o.getTotalAmount());
            row.put("status", o.getStatus());
            row.put("created_at", o.getCreatedAt());
            if (o.getPlanId() != null && planMap.containsKey(o.getPlanId())) {
                row.put("plan", planMap.get(o.getPlanId()));
            }
            result.add(row);
        }
        return ApiResponse.success(result);
    }

    /**
     * 对齐 PHP User\\OrderController::detail
     */
    @GetMapping("/detail")
    public ApiResponse<Map<String, Object>> detail(HttpServletRequest request,
                                                   @RequestParam("trade_no") String tradeNo) {
        User user = requireUser(request);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, user.getId())
                .eq(Order::getTradeNo, tradeNo);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BusinessException(500, "Order does not exist or has been paid");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("trade_no", order.getTradeNo());
        data.put("plan_id", order.getPlanId());
        data.put("period", order.getPeriod());
        data.put("total_amount", order.getTotalAmount());
        data.put("status", order.getStatus());
        data.put("surplus_order_ids", order.getSurplusOrderIds());
        if (order.getPlanId() == 0) {
            Map<String, Object> plan = new HashMap<>();
            plan.put("id", 0);
            plan.put("name", "deposit");
            data.put("plan", plan);
        } else if (order.getPlanId() != null) {
            Plan plan = planMapper.selectById(order.getPlanId());
            if (plan == null) {
                throw new BusinessException(500, "Subscription plan does not exist");
            }
            data.put("plan", plan);
        }
        return ApiResponse.success(data);
    }

    /**
     * 对齐 PHP User\\OrderController::save 的核心逻辑，暂不实现优惠券和余额抵扣。
     */
    @PostMapping("/save")
    public ApiResponse<String> save(HttpServletRequest request,
                                    @RequestParam("plan_id") Long planId,
                                    @RequestParam(value = "period", required = false) String period,
                                    @RequestParam(value = "deposit_amount", required = false) Long depositAmount) {
        User user = requireUser(request);
        if (orderService.userHasUnfinishedOrder(user.getId())) {
            throw new BusinessException(500, "You have an unpaid or pending order, please try again later or cancel it");
        }
        // 充值订单
        if (planId == 0) {
            if (depositAmount == null || depositAmount <= 0) {
                throw new BusinessException(500, "Failed to create order, deposit amount must be greater than 0");
            }
            if (depositAmount >= 9_999_999L) {
                throw new BusinessException(500, "Deposit amount too large, please contact the administrator");
            }
            Order order = new Order();
            order.setUserId(user.getId());
            order.setPlanId(0L);
            order.setPeriod("deposit");
            order.setTradeNo(Helper.getServerKey(System.currentTimeMillis(), 16));
            order.setTotalAmount(depositAmount);
            order.setStatus(0);
            order.setType(9); // 充值
            orderMapper.insert(order);
            return ApiResponse.success(order.getTradeNo());
        }

        if (!StringUtils.hasText(period)) {
            throw new BusinessException(500, "Invalid parameter");
        }
        Plan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(500, "Subscription plan does not exist");
        }
        // 简化，对应 PHP 各种 show/renew/period 校验逻辑
        Integer price = null;
        switch (period) {
            case "month_price":
                price = plan.getMonthPrice();
                break;
            case "quarter_price":
                price = plan.getQuarterPrice();
                break;
            case "half_year_price":
                price = plan.getHalfYearPrice();
                break;
            case "year_price":
                price = plan.getYearPrice();
                break;
            case "two_year_price":
                price = plan.getTwoYearPrice();
                break;
            case "three_year_price":
                price = plan.getThreeYearPrice();
                break;
            case "onetime_price":
                price = plan.getOnetimePrice();
                break;
            case "reset_price":
                price = plan.getResetPrice();
                break;
            default:
                throw new BusinessException(500, "This payment period cannot be purchased, please choose another period");
        }
        if (price == null) {
            throw new BusinessException(500, "This payment period cannot be purchased, please choose another period");
        }

        User dbUser = userMapper.selectById(user.getId());
        int orderType = resolveOrderType(dbUser, plan.getId(), period);
        long now = System.currentTimeMillis() / 1000;

        Order order = new Order();
        order.setUserId(user.getId());
        order.setPlanId(plan.getId());
        order.setPeriod(period);
        order.setTradeNo(Helper.getServerKey(System.currentTimeMillis(), 16));
        order.setTotalAmount(price.longValue());
        order.setStatus(0);
        order.setType(orderType);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        return ApiResponse.success(order.getTradeNo());
    }

    /**
     * 与 PHP OrderService::setOrderType 对齐：1-新购 2-续费 3-升级 4-流量重置 9-充值
     */
    private int resolveOrderType(User user, Long planId, String period) {
        if ("reset_price".equals(period)) {
            return 4;
        }
        if (user == null) {
            return 1;
        }
        Long userPlanId = user.getPlanId();
        Long expiredAt = user.getExpiredAt();
        boolean notExpired = expiredAt != null && expiredAt > System.currentTimeMillis() / 1000;
        if (userPlanId != null && !planId.equals(userPlanId) && (notExpired || expiredAt == null)) {
            return 3; // 升级
        }
        if (notExpired && planId.equals(userPlanId)) {
            return 2; // 续费
        }
        return 1; // 新购
    }

    /**
     * 对齐 PHP User\\OrderController::checkout 的简化版本：
     * 这里只返回支付方式或标记免费订单，无实际支付网关集成。
     */
    @PostMapping("/checkout")
    public ApiResponse<Map<String, Object>> checkout(HttpServletRequest request,
                                                     @RequestParam("trade_no") String tradeNo,
                                                     @RequestParam("method") Long method) {
        User user = requireUser(request);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTradeNo, tradeNo)
                .eq(Order::getUserId, user.getId())
                .eq(Order::getStatus, 0);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BusinessException(500, "Order does not exist or has been paid");
        }
        // 免费订单，直接视为已处理
        if (order.getTotalAmount() != null && order.getTotalAmount() <= 0) {
            order.setStatus(3);
            orderMapper.updateById(order);
            Map<String, Object> resp = new HashMap<>();
            resp.put("type", -1);
            resp.put("data", true);
            return ApiResponse.success(resp);
        }
        Payment payment = paymentMapper.selectById(method);
        if (payment == null || payment.getEnable() == null || payment.getEnable() != 1) {
            throw new BusinessException(500, "Payment method is not available");
        }
        order.setPaymentId(method);
        orderMapper.updateById(order);
        
        Map<String, Object> payOrder = new HashMap<>();
        payOrder.put("trade_no", order.getTradeNo());
        payOrder.put("total_amount", order.getTotalAmount());
        payOrder.put("user_id", order.getUserId());
        payOrder.put("stripe_token", null);
        Map<String, Object> payResult = paymentService.pay(payment.getPayment(), payment.getId(), payOrder);
        
        Map<String, Object> result = new HashMap<>();
        result.put("type", 0);
        result.put("data", payResult);
        return ApiResponse.success(result);
    }

    /**
     * 对齐 PHP User\\OrderController::check
     */
    @GetMapping("/check")
    public ApiResponse<Integer> check(HttpServletRequest request,
                                      @RequestParam("trade_no") String tradeNo) {
        User user = requireUser(request);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTradeNo, tradeNo)
                .eq(Order::getUserId, user.getId());
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BusinessException(500, "Order does not exist");
        }
        return ApiResponse.success(order.getStatus());
    }

    /**
     * 对齐 PHP User\\OrderController::getPaymentMethod
     * 兼容路径：
     * - /api/v1/user/order/getPaymentMethod
     * - /api/v1/user/order/paymentMethod
     */
    @GetMapping({"/paymentMethod", "/getPaymentMethod"})
    public ApiResponse<List<Map<String, Object>>> getPaymentMethod() {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Payment::getEnable, 1)
                .orderByAsc(Payment::getSort);
        List<Payment> methods = paymentMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Payment p : methods) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", p.getId());
            row.put("name", p.getName());
            row.put("payment", p.getPayment());
            row.put("icon", p.getIcon());
            row.put("handling_fee_fixed", p.getHandlingFeeFixed());
            row.put("handling_fee_percent", p.getHandlingFeePercent());
            result.add(row);
        }
        return ApiResponse.success(result);
    }

    /**
     * 对齐 PHP User\\OrderController::cancel 的简化版本。
     */
    @PostMapping("/cancel")
    public ApiResponse<Boolean> cancel(HttpServletRequest request,
                                       @RequestParam("trade_no") String tradeNo) {
        if (!StringUtils.hasText(tradeNo)) {
            throw new BusinessException(500, "Invalid parameter");
        }
        User user = requireUser(request);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTradeNo, tradeNo)
                .eq(Order::getUserId, user.getId());
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BusinessException(500, "Order does not exist");
        }
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new BusinessException(500, "You can only cancel pending orders");
        }
        if (!orderService.cancelOrder(order)) {
            throw new BusinessException(500, "Cancel failed");
        }
        return ApiResponse.success(true);
    }

    private User requireUser(HttpServletRequest request) {
        Object attr = request.getAttribute("user");
        if (attr instanceof User user) {
            return user;
        }
        throw new BusinessException(401, "Unauthenticated");
    }
}

