package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.CommissionLogMapper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.CommissionLog;
import com.v2board.api.model.Order;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import com.v2board.api.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/order")
public class AdminOrderController {

    /** filter 允许的字段白名单，对齐 PHP OrderFetch 验证规则 */
    private static final Set<String> ALLOWED_FILTER_KEYS = Set.of(
            "email", "trade_no", "status", "commission_status",
            "user_id", "invite_user_id", "callback_no", "commission_balance"
    );

    /** filter 允许的条件白名单 */
    private static final Set<String> ALLOWED_CONDITIONS = Set.of(
            ">", "<", "=", ">=", "<=", "模糊", "!="
    );

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CommissionLogMapper commissionLogMapper;

    @Autowired
    private OrderService orderService;

    /**
     * 管理端订单详情，对齐 PHP Admin\OrderController::detail。
     * 返回订单信息、佣金日志、折抵订单。
     */
    @PostMapping("/detail")
    public ApiResponse<Map<String, Object>> detail(@RequestParam("id") Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(500, "订单不存在");
        }

        Map<String, Object> data = orderToMap(order);

        // 查询佣金日志
        List<CommissionLog> commissionLogs = commissionLogMapper.selectList(
                new LambdaQueryWrapper<CommissionLog>().eq(CommissionLog::getTradeNo, order.getTradeNo())
        );
        data.put("commission_log", commissionLogs);

        // 查询折抵订单
        if (StringUtils.hasText(order.getSurplusOrderIds())) {
            try {
                List<Long> surplusIds = parseSurplusOrderIds(order.getSurplusOrderIds());
                if (!surplusIds.isEmpty()) {
                    List<Order> surplusOrders = orderMapper.selectBatchIds(surplusIds);
                    data.put("surplus_orders", surplusOrders);
                }
            } catch (Exception e) {
                // 解析失败忽略
            }
        }

        return ApiResponse.success(data);
    }

    /**
     * 管理端订单列表，对齐 PHP Admin\OrderController::fetch。
     * 支持 filter 数组条件过滤和佣金筛选。
     */
    @GetMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch(
            @RequestParam(value = "current", required = false, defaultValue = "1") long current,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") long pageSize,
            @RequestParam(value = "is_commission", required = false) Boolean isCommission,
            HttpServletRequest request) {

        if (pageSize < 10) {
            pageSize = 10;
        }

        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");

        // 佣金筛选
        if (Boolean.TRUE.equals(isCommission)) {
            wrapper.isNotNull("invite_user_id")
                    .notIn("status", 0, 2)
                    .gt("commission_balance", 0);
        }

        // filter 数组过滤，对齐 PHP filter() 方法
        applyFilters(request, wrapper);

        Page<Order> page = new Page<>(current, pageSize);
        Page<Order> resultPage = orderMapper.selectPage(page, wrapper);

        // 构建 plan_name 映射
        List<Order> records = resultPage.getRecords();
        List<Plan> plans = planMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, String> planNameMap = new HashMap<>();
        for (Plan p : plans) {
            planNameMap.put(p.getId(), p.getName());
        }

        // 构建返回数据，包含全部字段 + plan_name
        List<Map<String, Object>> data = new ArrayList<>();
        for (Order o : records) {
            Map<String, Object> row = orderToMap(o);
            if (o.getPlanId() != null && planNameMap.containsKey(o.getPlanId())) {
                row.put("plan_name", planNameMap.get(o.getPlanId()));
            }
            data.add(row);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("data", data);
        resp.put("total", resultPage.getTotal());
        return ApiResponse.success(resp);
    }

    /**
     * 手动标记订单已支付，对齐 PHP Admin\OrderController::paid。
     * 设置 status=1, paid_at, callback_no，然后触发异步开通。
     */
    @PostMapping("/paid")
    public ApiResponse<Boolean> paid(@RequestParam("trade_no") String tradeNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getTradeNo, tradeNo)
        );
        if (order == null) {
            throw new BusinessException(500, "订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new BusinessException(500, "只能对待支付的订单进行操作");
        }
        if (!orderService.paid(order, "manual_operation")) {
            throw new BusinessException(500, "更新失败");
        }
        return ApiResponse.success(true);
    }

    /**
     * 取消订单，对齐 PHP Admin\OrderController::cancel。
     */
    @PostMapping("/cancel")
    public ApiResponse<Boolean> cancel(@RequestParam("trade_no") String tradeNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getTradeNo, tradeNo)
        );
        if (order == null) {
            throw new BusinessException(500, "订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new BusinessException(500, "只能对待支付的订单进行操作");
        }
        if (!orderService.cancelOrder(order)) {
            throw new BusinessException(500, "更新失败");
        }
        return ApiResponse.success(true);
    }

    /**
     * 更新订单佣金状态，对齐 PHP Admin\OrderController::update。
     * 仅允许更新 commission_status (0, 1, 3)。
     */
    @PostMapping("/update")
    public ApiResponse<Boolean> update(@RequestParam("trade_no") String tradeNo,
                                       @RequestParam("commission_status") Integer commissionStatus) {
        if (commissionStatus == null || (commissionStatus != 0 && commissionStatus != 1 && commissionStatus != 3)) {
            throw new BusinessException(500, "佣金状态参数有误");
        }
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getTradeNo, tradeNo)
        );
        if (order == null) {
            throw new BusinessException(500, "订单不存在");
        }
        order.setCommissionStatus(commissionStatus);
        order.setUpdatedAt(System.currentTimeMillis() / 1000);
        if (orderMapper.updateById(order) <= 0) {
            throw new BusinessException(500, "更新失败");
        }
        return ApiResponse.success(true);
    }

    /**
     * 手动分配订单（创建人工订单），对齐 PHP Admin\OrderController::assign。
     * 设置佣金 (setInvite) 后创建订单。
     */
    @PostMapping("/assign")
    public ApiResponse<String> assign(@RequestParam("plan_id") Long planId,
                                      @RequestParam("email") String email,
                                      @RequestParam("period") String period,
                                      @RequestParam("total_amount") Long totalAmount) {
        Plan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(500, "该订阅不存在");
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
        if (user == null) {
            throw new BusinessException(500, "该用户不存在");
        }
        if (orderService.userHasUnfinishedOrder(user.getId())) {
            throw new BusinessException(500, "该用户还有待支付的订单，无法分配");
        }
        long now = System.currentTimeMillis() / 1000;
        Order order = new Order();
        order.setUserId(user.getId());
        order.setPlanId(plan.getId());
        order.setPeriod(period);
        order.setTradeNo(com.v2board.api.util.Helper.generateOrderNo());
        order.setTotalAmount(totalAmount);
        order.setStatus(0);
        order.setCommissionStatus(0);
        order.setCommissionBalance(0L);
        order.setType(resolveOrderTypeForUser(user, plan.getId(), period));

        // 设置佣金，对齐 PHP setInvite
        orderService.setInvite(order, user);

        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        if (orderMapper.insert(order) <= 0) {
            throw new BusinessException(500, "订单创建失败");
        }
        return ApiResponse.success(order.getTradeNo());
    }

    // ==================== 私有方法 ====================

    /**
     * 应用 filter 条件，对齐 PHP filter() 方法。
     * 前端传递 filter[0][key]=trade_no&filter[0][condition]==&filter[0][value]=xxx 格式。
     */
    private void applyFilters(HttpServletRequest request, QueryWrapper<Order> wrapper) {
        for (int i = 0; i < 20; i++) {
            String key = request.getParameter("filter[" + i + "][key]");
            String condition = request.getParameter("filter[" + i + "][condition]");
            String value = request.getParameter("filter[" + i + "][value]");
            if (key == null || condition == null) {
                break;
            }
            if (!ALLOWED_FILTER_KEYS.contains(key) || !ALLOWED_CONDITIONS.contains(condition)) {
                continue;
            }

            // email 字段特殊处理：先查用户，再按 user_id 过滤
            if ("email".equals(key)) {
                User user = userMapper.selectOne(
                        new LambdaQueryWrapper<User>().like(User::getEmail, value)
                );
                if (user != null) {
                    wrapper.eq("user_id", user.getId());
                }
                continue;
            }

            // 模糊查询
            if ("模糊".equals(condition)) {
                wrapper.like(key, value);
            } else {
                // 条件映射：>, <, =, >=, <=, !=
                switch (condition) {
                    case ">" -> wrapper.gt(key, value);
                    case "<" -> wrapper.lt(key, value);
                    case "=" -> wrapper.eq(key, value);
                    case ">=" -> wrapper.ge(key, value);
                    case "<=" -> wrapper.le(key, value);
                    case "!=" -> wrapper.ne(key, value);
                }
            }
        }
    }

    /**
     * 根据用户当前订阅与周期推断订单类型：
     * 1-新购 2-续费 3-升级 4-流量重置。
     */
    private int resolveOrderTypeForUser(User user, Long planId, String period) {
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
            return 3;
        }
        if (notExpired && planId.equals(userPlanId)) {
            return 2;
        }
        return 1;
    }

    /**
     * Order 转 Map，返回全部字段（snake_case）。
     */
    private Map<String, Object> orderToMap(Order o) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", o.getId());
        row.put("invite_user_id", o.getInviteUserId());
        row.put("user_id", o.getUserId());
        row.put("plan_id", o.getPlanId());
        row.put("coupon_id", o.getCouponId());
        row.put("payment_id", o.getPaymentId());
        row.put("type", o.getType());
        row.put("period", o.getPeriod());
        row.put("trade_no", o.getTradeNo());
        row.put("callback_no", o.getCallbackNo());
        row.put("total_amount", o.getTotalAmount());
        row.put("handling_amount", o.getHandlingAmount());
        row.put("discount_amount", o.getDiscountAmount());
        row.put("surplus_amount", o.getSurplusAmount());
        row.put("refund_amount", o.getRefundAmount());
        row.put("balance_amount", o.getBalanceAmount());
        row.put("surplus_order_ids", o.getSurplusOrderIds());
        row.put("status", o.getStatus());
        row.put("commission_status", o.getCommissionStatus());
        row.put("commission_balance", o.getCommissionBalance());
        row.put("actual_commission_balance", o.getActualCommissionBalance());
        row.put("paid_at", o.getPaidAt());
        row.put("created_at", o.getCreatedAt());
        row.put("updated_at", o.getUpdatedAt());
        return row;
    }

    /**
     * 解析 surplus_order_ids JSON 数组字符串。
     */
    private List<Long> parseSurplusOrderIds(String surplusOrderIds) {
        if (surplusOrderIds == null || surplusOrderIds.isBlank()) {
            return List.of();
        }
        String cleaned = surplusOrderIds.replaceAll("[\\[\\] ]", "");
        if (cleaned.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(cleaned.split(","))
                .filter(s -> !s.isEmpty())
                .map(s -> Long.valueOf(s.trim()))
                .collect(Collectors.toList());
    }
}
