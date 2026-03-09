package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * 订单服务 — 对齐 PHP OrderService
 */
@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    /** 周期字符串 → 月数映射，对齐 PHP STR_TO_TIME */
    private static final Map<String, Integer> PERIOD_MONTHS = Map.of(
            "month_price", 1,
            "quarter_price", 3,
            "half_year_price", 6,
            "year_price", 12,
            "two_year_price", 24,
            "three_year_price", 36
    );

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private ConfigService configService;

    /**
     * 订单支付 — 对齐 PHP OrderService::paid()
     * 设置 status=1, paid_at, callback_no，然后触发异步开通。
     */
    @Transactional
    public boolean paid(Order order, String callbackNo) {
        if (order == null || order.getStatus() == null || order.getStatus() != 0) {
            return true;
        }
        order.setStatus(1);
        order.setPaidAt(System.currentTimeMillis() / 1000);
        order.setCallbackNo(callbackNo);
        order.setUpdatedAt(System.currentTimeMillis() / 1000);
        if (orderMapper.updateById(order) <= 0) {
            return false;
        }
        try {
            handleOrderAsync(order.getTradeNo());
        } catch (Exception e) {
            logger.error("paid: failed to dispatch handleOrderAsync for {}", order.getTradeNo(), e);
            return false;
        }
        return true;
    }

    /**
     * 设置佣金 — 对齐 PHP OrderService::setInvite()
     * 根据邀请人佣金类型和费率设置订单佣金。
     */
    public void setInvite(Order order, User user) {
        if (user.getInviteUserId() == null || (order.getTotalAmount() != null && order.getTotalAmount() <= 0)) {
            return;
        }
        order.setInviteUserId(user.getInviteUserId());
        User inviter = userMapper.selectById(user.getInviteUserId());
        if (inviter == null) {
            return;
        }
        boolean isCommission = false;
        int commissionType = inviter.getCommissionType() != null ? inviter.getCommissionType() : 0;
        switch (commissionType) {
            case 0 -> {
                // 系统默认：根据配置决定是仅首次还是每次
                int commissionFirstTime = getConfigInt("commission_first_time_enable", 1);
                isCommission = (commissionFirstTime == 0) || !haveValidOrder(user);
            }
            case 1 -> isCommission = true; // 每次都发放
            case 2 -> isCommission = !haveValidOrder(user); // 仅首次
        }
        if (!isCommission) {
            return;
        }
        long totalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : 0L;
        if (inviter.getCommissionRate() != null && inviter.getCommissionRate() > 0) {
            order.setCommissionBalance(totalAmount * inviter.getCommissionRate() / 100);
        } else {
            int defaultRate = getConfigInt("invite_commission", 10);
            order.setCommissionBalance(totalAmount * defaultRate / 100);
        }
    }

    /**
     * 用户是否存在未完成订单（待支付或处理中）
     */
    public boolean userHasUnfinishedOrder(Long userId) {
        if (userId == null) {
            return false;
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId)
                .in(Order::getStatus, 0, 1);
        return orderMapper.selectCount(wrapper) > 0;
    }

    /**
     * 异步处理订单 — 对齐 PHP OrderHandleJob
     */
    @Async("orderExecutor")
    public void handleOrderAsync(String tradeNo) {
        if (tradeNo == null || tradeNo.isEmpty()) {
            return;
        }
        try {
            LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Order::getTradeNo, tradeNo);
            Order order = orderMapper.selectOne(wrapper);
            if (order == null) {
                return;
            }
            long now = System.currentTimeMillis() / 1000;
            if (order.getStatus() == 0) {
                // 未支付且超过 2 小时 → 取消
                if (order.getCreatedAt() != null && (now - order.getCreatedAt()) > 7200) {
                    cancel(order);
                }
            } else if (order.getStatus() == 1) {
                // 已支付 → 开通
                open(order);
            }
        } catch (Exception e) {
            logger.error("handleOrderAsync failed for tradeNo={}", tradeNo, e);
        }
    }

    /**
     * 订单激活 — 对齐 PHP OrderService::open()
     */
    @Transactional
    public void open(Order order) {
        User user = userMapper.selectById(order.getUserId());
        if (user == null) {
            logger.error("open: user not found for order {}", order.getTradeNo());
            return;
        }

        // type=9: 充值
        if (order.getType() != null && order.getType() == 9) {
            long bonus = getBonus(order.getTotalAmount());
            user.setBalance((user.getBalance() != null ? user.getBalance() : 0L)
                    + (order.getTotalAmount() != null ? order.getTotalAmount() : 0L) + bonus);
            user.setUpdatedAt(System.currentTimeMillis() / 1000);
            userMapper.updateById(user);
            order.setStatus(3);
            order.setUpdatedAt(System.currentTimeMillis() / 1000);
            orderMapper.updateById(order);
            return;
        }

        Plan plan = planMapper.selectById(order.getPlanId());
        if (plan == null) {
            logger.error("open: plan not found for order {}", order.getTradeNo());
            return;
        }

        // 退差价到余额
        if (order.getRefundAmount() != null && order.getRefundAmount() > 0) {
            user.setBalance((user.getBalance() != null ? user.getBalance() : 0L) + order.getRefundAmount());
        }

        // 处理 surplus_order_ids — 旧订单标记为状态 4
        if (order.getSurplusOrderIds() != null && !order.getSurplusOrderIds().isEmpty()) {
            try {
                String[] ids = order.getSurplusOrderIds().replaceAll("[\\[\\] ]", "").split(",");
                for (String idStr : ids) {
                    if (idStr.isEmpty()) continue;
                    Order old = orderMapper.selectById(Long.valueOf(idStr.trim()));
                    if (old != null) {
                        old.setStatus(4);
                        old.setUpdatedAt(System.currentTimeMillis() / 1000);
                        orderMapper.updateById(old);
                    }
                }
            } catch (Exception e) {
                logger.error("open: failed to update surplus orders for {}", order.getTradeNo(), e);
            }
        }

        // 按周期类型处理
        String period = order.getPeriod();
        if ("onetime_price".equals(period)) {
            buyByOneTime(order, plan, user);
        } else if ("reset_price".equals(period)) {
            buyByResetTraffic(user);
        } else {
            buyByPeriod(order, plan, user);
        }

        // 设置速度限制
        user.setSpeedLimit(plan.getSpeedLimit());

        user.setUpdatedAt(System.currentTimeMillis() / 1000);
        userMapper.updateById(user);

        order.setStatus(3);
        order.setUpdatedAt(System.currentTimeMillis() / 1000);
        orderMapper.updateById(order);
    }

    /**
     * 取消订单 — 对齐 PHP OrderService::cancel()
     * 设置状态为2，如有使用余额则退回
     */
    @Transactional
    public boolean cancel(Order order) {
        if (order == null || order.getStatus() == null || order.getStatus() != 0) {
            return false;
        }
        order.setStatus(2);
        order.setUpdatedAt(System.currentTimeMillis() / 1000);
        if (orderMapper.updateById(order) <= 0) {
            return false;
        }
        // 退回余额
        if (order.getBalanceAmount() != null && order.getBalanceAmount() > 0) {
            User user = userMapper.selectById(order.getUserId());
            if (user != null) {
                user.setBalance((user.getBalance() != null ? user.getBalance() : 0L) + order.getBalanceAmount());
                user.setUpdatedAt(System.currentTimeMillis() / 1000);
                userMapper.updateById(user);
            }
        }
        return true;
    }

    /**
     * cancelOrder 别名 — 兼容已有调用方
     */
    @Transactional
    public boolean cancelOrder(Order order) {
        return cancel(order);
    }

    /**
     * 按周期购买 — 对齐 PHP buyByPeriod()
     */
    private void buyByPeriod(Order order, Plan plan, User user) {
        // 升级时先将到期时间设置为当前
        if (order.getType() != null && order.getType() == 3) {
            user.setExpiredAt(System.currentTimeMillis() / 1000);
        }

        user.setTransferEnable(plan.getTransferEnable() * 1073741824L);
        user.setDeviceLimit(plan.getCapacityLimit());

        // 从一次性转换到循环 → 重置流量
        if (user.getExpiredAt() == null) {
            buyByResetTraffic(user);
        }
        // 新购 → 重置流量
        if (order.getType() != null && order.getType() == 1) {
            buyByResetTraffic(user);
        }

        // 到期当天续费刷新流量
        if (order.getType() != null && order.getType() == 2 && user.getExpiredAt() != null) {
            ZonedDateTime expireTime = Instant.ofEpochSecond(user.getExpiredAt()).atZone(ZoneId.systemDefault());
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            if (expireTime.getMonthValue() == now.getMonthValue()
                    && expireTime.getDayOfMonth() == now.getDayOfMonth()) {
                buyByResetTraffic(user);
            }
        }

        user.setPlanId(plan.getId());
        user.setGroupId(plan.getGroupId());
        user.setExpiredAt(getTime(order.getPeriod(), user.getExpiredAt()));
    }

    /**
     * 一次性购买 — 对齐 PHP buyByOneTime()
     */
    private void buyByOneTime(Order order, Plan plan, User user) {
        long transferEnable = plan.getTransferEnable();
        if (order.getSurplusOrderIds() == null || order.getSurplusOrderIds().isEmpty()) {
            long notUsedTraffic = ((user.getTransferEnable() != null ? user.getTransferEnable() : 0L)
                    - ((user.getU() != null ? user.getU() : 0L) + (user.getD() != null ? user.getD() : 0L)))
                    / 1073741824L;
            if (notUsedTraffic > 0 && user.getExpiredAt() == null) {
                transferEnable += notUsedTraffic;
            }
        }
        buyByResetTraffic(user);
        user.setTransferEnable(transferEnable * 1073741824L);
        user.setDeviceLimit(plan.getCapacityLimit());
        user.setPlanId(plan.getId());
        user.setGroupId(plan.getGroupId());
        user.setExpiredAt(null);
    }

    /**
     * 重置流量
     */
    private void buyByResetTraffic(User user) {
        user.setU(0L);
        user.setD(0L);
    }

    /**
     * 根据周期字符串计算到期时间 — 对齐 PHP getTime()
     */
    public Long getTime(String period, Long timestamp) {
        long now = System.currentTimeMillis() / 1000;
        if (timestamp == null || timestamp < now) {
            timestamp = now;
        }
        Integer months = PERIOD_MONTHS.get(period);
        if (months == null) {
            return timestamp;
        }
        ZonedDateTime dt = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault());
        return dt.plusMonths(months).toEpochSecond();
    }

    /**
     * 用户是否有有效订单（非待支付、非取消）— 对齐 PHP haveValidOrder()
     */
    private boolean haveValidOrder(User user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, user.getId())
                .notIn(Order::getStatus, 0, 2);
        return orderMapper.selectCount(wrapper) > 0;
    }

    /**
     * 从配置中读取整型值（先从 invite 分组中取，再从全局取）
     */
    @SuppressWarnings("unchecked")
    private int getConfigInt(String key, int defaultValue) {
        try {
            Map<String, Object> config = configService.getFullConfig();
            // 先从 invite 分组中查找
            Object inviteSection = config.get("invite");
            if (inviteSection instanceof Map) {
                Object val = ((Map<String, Object>) inviteSection).get(key);
                if (val instanceof Number) {
                    return ((Number) val).intValue();
                }
            }
            // 再从全局查找
            Object val = config.get(key);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
        } catch (Exception e) {
            logger.error("getConfigInt: failed to load config for key={}", key, e);
        }
        return defaultValue;
    }

    /**
     * 充值奖励 — 对齐 PHP getbounus()
     */
    private long getBonus(Long totalAmount) {
        if (totalAmount == null) return 0;
        Map<String, Object> config;
        try {
            config = configService.getFullConfig();
        } catch (Exception e) {
            logger.error("getBonus: failed to load config", e);
            return 0;
        }
        Object bonusObj = config.get("deposit_bounus");
        if (bonusObj == null) return 0;
        // deposit_bounus 为逗号分隔的 "amount:bonus" 字符串列表
        // 例如 ["100:10", "500:100"]
        long add = 0;
        if (bonusObj instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<String> tiers = (java.util.List<String>) bonusObj;
            for (String tier : tiers) {
                if (tier == null || !tier.contains(":")) continue;
                String[] parts = tier.split(":");
                long amount = (long) (Double.parseDouble(parts[0]) * 100);
                long bonus = (long) (Double.parseDouble(parts[1]) * 100);
                if (totalAmount >= amount) {
                    add = Math.max(add, bonus);
                }
            }
        }
        return add;
    }
}
