package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import com.v2board.api.service.OrderService;
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
@RequestMapping("/api/v1/admin/order")
public class AdminOrderController {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrderService orderService;

    /**
     * 管理端订单详情，对齐 PHP Admin\\OrderController::detail（简化版，不含佣金日志）。
     */
    @GetMapping("/detail")
    public ApiResponse<Order> detail(@RequestParam("id") Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(500, "订单不存在");
        }
        return ApiResponse.success(order);
    }

    /**
     * 管理端订单列表，对齐 PHP Admin\\OrderController::fetch 的主要行为。
     */
    @GetMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch(
            @RequestParam(value = "current", required = false, defaultValue = "1") long current,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") long pageSize,
            @RequestParam(value = "is_commission", required = false) Boolean isCommission,
            @RequestParam(value = "email", required = false) String email) {

        if (pageSize < 10) {
            pageSize = 10;
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Order::getCreatedAt);

        if (Boolean.TRUE.equals(isCommission)) {
            wrapper.isNotNull(Order::getInviteUserId)
                    .notIn(Order::getStatus, 0, 2)
                    .gt(Order::getCommissionBalance, 0);
        }

        if (StringUtils.hasText(email)) {
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().like(User::getEmail, email)
            );
            if (user != null) {
                wrapper.eq(Order::getUserId, user.getId());
            }
        }

        Page<Order> page = new Page<>(current, pageSize);
        Page<Order> resultPage = orderMapper.selectPage(page, wrapper);

        List<Order> records = resultPage.getRecords();
        List<Plan> plans = planMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, String> planNameMap = new HashMap<>();
        for (Plan p : plans) {
            planNameMap.put(p.getId(), p.getName());
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (Order o : records) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", o.getId());
            row.put("trade_no", o.getTradeNo());
            row.put("user_id", o.getUserId());
            row.put("plan_id", o.getPlanId());
            row.put("period", o.getPeriod());
            row.put("total_amount", o.getTotalAmount());
            row.put("status", o.getStatus());
            row.put("commission_balance", o.getCommissionBalance());
            row.put("commission_status", o.getCommissionStatus());
            row.put("created_at", o.getCreatedAt());
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
     * 手动标记订单已支付，简化版：仅修改状态，不做用户套餐变更。
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
        order.setStatus(3);
        if (orderMapper.updateById(order) <= 0) {
            throw new BusinessException(500, "更新失败");
        }
        return ApiResponse.success(true);
    }

    /**
     * 取消订单。
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
     * 更新订单佣金状态。
     */
    @PostMapping("/update")
    public ApiResponse<Boolean> update(@RequestParam("trade_no") String tradeNo,
                                       @RequestParam("commission_status") Integer commissionStatus) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getTradeNo, tradeNo)
        );
        if (order == null) {
            throw new BusinessException(500, "订单不存在");
        }
        order.setCommissionStatus(commissionStatus);
        if (orderMapper.updateById(order) <= 0) {
            throw new BusinessException(500, "更新失败");
        }
        return ApiResponse.success(true);
    }

    /**
     * 手动分配订单（创建人工订单），简化版：仅创建订单记录，不立即变更用户套餐。
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
        Order order = new Order();
        order.setUserId(user.getId());
        order.setPlanId(plan.getId());
        order.setPeriod(period);
        order.setTradeNo(java.util.UUID.randomUUID().toString().replace("-", ""));
        order.setTotalAmount(totalAmount);
        order.setStatus(0);
        if (orderMapper.insert(order) <= 0) {
            throw new BusinessException(500, "订单创建失败");
        }
        return ApiResponse.success(order.getTradeNo());
    }
}

