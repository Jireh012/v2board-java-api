package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.v2board.api.queue.JobDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Autowired
    private UserService userService;

    @Autowired
    private JobDispatcher jobDispatcher;

    /**
     * VIP 折扣 — 对齐 PHP setVipDiscount（在优惠券之后一次性从 total 扣减 discount_amount）。
     */
    public void setVipDiscount(Order order, User user) {
        long total = order.getTotalAmount() != null ? order.getTotalAmount() : 0L;
        long discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : 0L;
        if (user != null && user.getDiscount() != null && user.getDiscount() > 0) {
            discount = discount + total * user.getDiscount() / 100;
        }
        order.setDiscountAmount(discount);
        order.setTotalAmount(Math.max(0L, total - discount));
    }

    /**
     * 订单类型 + 换购门禁 + 差价 — 对齐 PHP setOrderType。
     */
    public void setOrderType(Order order, User user) {
        if (order == null || user == null) {
            return;
        }
        if ("reset_price".equals(order.getPeriod())) {
            order.setType(4);
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        Long userPlanId = user.getPlanId();
        Long expiredAt = user.getExpiredAt();
        boolean notExpired = expiredAt != null && expiredAt > now;
        boolean lifetime = expiredAt == null && userPlanId != null;

        if (userPlanId != null && order.getPlanId() != null && !order.getPlanId().equals(userPlanId)
                && (notExpired || lifetime)) {
            if (configService.getPlanChangeEnable() != 1) {
                throw new BusinessException(500, "目前不允许更改订阅，请联系客服或提交工单操作");
            }
            order.setType(3);
            if (configService.getSurplusEnable() == 1) {
                getSurplusValue(user, order);
                long surplus = order.getSurplusAmount() != null ? order.getSurplusAmount() : 0L;
                long total = order.getTotalAmount() != null ? order.getTotalAmount() : 0L;
                if (surplus >= total) {
                    order.setRefundAmount(surplus - total);
                    order.setTotalAmount(0L);
                } else {
                    order.setTotalAmount(total - surplus);
                }
            }
            return;
        }
        if (notExpired && order.getPlanId() != null && order.getPlanId().equals(userPlanId)) {
            order.setType(2);
            return;
        }
        order.setType(1);
    }

    /**
     * 续费且不允许新周期时，校验 period 与最近有效订单一致。
     */
    public void assertRenewPeriodAllowed(User user, Long planId, String period) {
        if (user == null || planId == null || !StringUtils.hasText(period)) {
            return;
        }
        if (configService.getAllowNewPeriod() == 1) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        if (user.getPlanId() == null || !planId.equals(user.getPlanId())) {
            return;
        }
        if (user.getExpiredAt() == null || user.getExpiredAt() <= now) {
            return;
        }
        if ("reset_price".equals(period)) {
            return;
        }
        String last = findLastValidPeriod(user.getId(), planId);
        if (last != null && !last.equals(period)) {
            throw new BusinessException(500, "当前不允许选择新的订阅周期");
        }
    }

    public String findLastValidPeriod(Long userId, Long planId) {
        if (userId == null || planId == null) {
            return null;
        }
        Order last = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getPlanId, planId)
                .eq(Order::getStatus, 3)
                .ne(Order::getPeriod, "reset_price")
                .orderByDesc(Order::getId)
                .last("LIMIT 1"));
        return last != null ? last.getPeriod() : null;
    }

    /**
     * 余额抵扣 — 对齐 PHP OrderController::save 余额段。
     */
    public void applyBalance(Order order, User user) {
        if (order == null || user == null) {
            return;
        }
        long total = order.getTotalAmount() != null ? order.getTotalAmount() : 0L;
        long balance = user.getBalance() != null ? user.getBalance() : 0L;
        if (balance <= 0 || total <= 0) {
            return;
        }
        if (balance >= total) {
            if (!userService.addBalance(user.getId(), -total)) {
                throw new BusinessException(500, "Insufficient balance");
            }
            order.setBalanceAmount(total);
            order.setTotalAmount(0L);
        } else {
            if (!userService.addBalance(user.getId(), -balance)) {
                throw new BusinessException(500, "Insufficient balance");
            }
            order.setBalanceAmount(balance);
            order.setTotalAmount(total - balance);
        }
        user.setBalance(userMapper.selectById(user.getId()).getBalance());
    }

    private void getSurplusValue(User user, Order order) {
        if (user.getExpiredAt() == null) {
            getSurplusValueByOneTime(user, order);
        } else {
            getSurplusValueByPeriod(user, order);
        }
    }

    private void getSurplusValueByOneTime(User user, Order order) {
        Order lastOneTime = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, user.getId())
                .eq(Order::getPeriod, "onetime_price")
                .eq(Order::getStatus, 3)
                .orderByDesc(Order::getId)
                .last("LIMIT 1"));
        if (lastOneTime == null) {
            return;
        }
        double nowUserTraffic = (user.getTransferEnable() != null ? user.getTransferEnable() : 0L) / 1073741824.0;
        if (nowUserTraffic <= 0) {
            return;
        }
        long paidTotal = nz(lastOneTime.getTotalAmount()) + nz(lastOneTime.getBalanceAmount());
        if (paidTotal <= 0) {
            return;
        }
        double trafficUnitPrice = paidTotal / nowUserTraffic;
        double used = ((user.getU() != null ? user.getU() : 0L) + (user.getD() != null ? user.getD() : 0L)) / 1073741824.0;
        double notUsed = nowUserTraffic - used;
        long result = Math.round(trafficUnitPrice * notUsed);
        order.setSurplusAmount(Math.max(result, 0L));
        List<Order> all = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, user.getId())
                .ne(Order::getPeriod, "reset_price")
                .eq(Order::getStatus, 3));
        order.setSurplusOrderIds(toIdJson(all));
    }

    private void getSurplusValueByPeriod(User user, Order order) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, user.getId())
                .ne(Order::getPeriod, "reset_price")
                .ne(Order::getPeriod, "onetime_price")
                .eq(Order::getStatus, 3));
        if (orders.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        long orderAmountSum = 0;
        int orderMonthSum = 0;
        long lastValidateAt = 0;
        for (Order item : orders) {
            Integer months = PERIOD_MONTHS.get(item.getPeriod());
            if (months == null || item.getCreatedAt() == null) {
                continue;
            }
            long end = ZonedDateTime.ofInstant(Instant.ofEpochSecond(item.getCreatedAt()), ZoneId.systemDefault())
                    .plusMonths(months)
                    .toEpochSecond();
            if (end < now) {
                continue;
            }
            lastValidateAt = item.getCreatedAt();
            orderMonthSum += months;
            orderAmountSum += nz(item.getTotalAmount()) + nz(item.getBalanceAmount())
                    + nz(item.getSurplusAmount()) - nz(item.getRefundAmount());
        }
        if (lastValidateAt == 0 || orderMonthSum <= 0) {
            return;
        }
        long expiredAtByOrder = ZonedDateTime.ofInstant(Instant.ofEpochSecond(lastValidateAt), ZoneId.systemDefault())
                .plusMonths(orderMonthSum)
                .toEpochSecond();
        if (expiredAtByOrder < now) {
            return;
        }
        long orderSurplusSecond = expiredAtByOrder - now;
        long orderRangeSecond = expiredAtByOrder - lastValidateAt;
        if (orderSurplusSecond <= 0 || orderRangeSecond <= 0 || orderAmountSum <= 0) {
            return;
        }
        double avgPrice = (double) orderAmountSum / orderRangeSecond;
        long surplus = Math.round(avgPrice * orderSurplusSecond);
        order.setSurplusAmount(Math.max(surplus, 0L));
        // PHP stores all fetched period orders' ids
        order.setSurplusOrderIds(toIdJson(orders));
    }

    private static long nz(Long v) {
        return v != null ? v : 0L;
    }

    private static String toIdJson(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return "[]";
        }
        return orders.stream()
                .map(Order::getId)
                .filter(id -> id != null)
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

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
            jobDispatcher.dispatchOrderHandle(order.getTradeNo());
        } catch (Exception e) {
            logger.error("paid: failed to enqueue order_handle for {}", order.getTradeNo(), e);
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

    /** Enqueue order handle (schedules / callers that previously used @Async). */
    public void handleOrderAsync(String tradeNo) {
        if (tradeNo == null || tradeNo.isEmpty()) {
            return;
        }
        jobDispatcher.dispatchOrderHandle(tradeNo);
    }

    /**
     * Process order — 对齐 PHP OrderHandleJob（由 queue worker 调用）
     */
    public void handleOrder(String tradeNo) {
        if (tradeNo == null || tradeNo.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTradeNo, tradeNo);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        if (order.getStatus() == 0) {
            if (order.getCreatedAt() != null && (now - order.getCreatedAt()) > 7200) {
                cancel(order);
            }
        } else if (order.getStatus() == 1) {
            open(order);
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

        // type=9: 充值（total_amount>0 才加赠；佣金划转 amount=0 不加赠）
        if (order.getType() != null && order.getType() == 9) {
            long total = order.getTotalAmount() != null ? order.getTotalAmount() : 0L;
            long bonus = total > 0 ? getBonus(total) : 0L;
            user.setBalance((user.getBalance() != null ? user.getBalance() : 0L) + total + bonus);
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

        // 对齐 PHP openEvent：type 1/2/3 且对应配置为 1 时清零已用流量
        openEvent(order, user);

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
     * 开通事件 — 对齐 PHP openEvent()。
     * type 1/2/3 读取对应 *_order_event_id；仅值为 1 时清零 u/d。
     */
    private void openEvent(Order order, User user) {
        if (order == null || user == null || order.getType() == null) {
            return;
        }
        int eventId;
        switch (order.getType()) {
            case 1 -> eventId = configService.getNewOrderEventId();
            case 2 -> eventId = configService.getRenewOrderEventId();
            case 3 -> eventId = configService.getChangeOrderEventId();
            default -> {
                return;
            }
        }
        if (eventId == 1) {
            buyByResetTraffic(user);
        }
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
     * 从配置中读取整型值（先从 invite 分组中取，再从全局取）。
     * 兼容 Number / Boolean / numeric String（与 ConfigService.intFromGroup 一致）。
     */
    @SuppressWarnings("unchecked")
    private int getConfigInt(String key, int defaultValue) {
        try {
            Map<String, Object> config = configService.getFullConfig();
            Object inviteSection = config.get("invite");
            if (inviteSection instanceof Map) {
                Integer fromInvite = parseConfigInt(((Map<String, Object>) inviteSection).get(key));
                if (fromInvite != null) {
                    return fromInvite;
                }
            }
            Integer fromTop = parseConfigInt(config.get(key));
            if (fromTop != null) {
                return fromTop;
            }
        } catch (Exception e) {
            logger.error("getConfigInt: failed to load config for key={}", key, e);
        }
        return defaultValue;
    }

    private static Integer parseConfigInt(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof Boolean b) {
            return b ? 1 : 0;
        }
        String s = String.valueOf(val).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 充值奖励 — 对齐 PHP getbounus()。
     * 从嵌套配置 deposit.deposit_bounus 读取 "元:元" 阶梯，换算为分后取满足门槛的最大赠送额。
     * 键名保持 PHP 拼写 deposit_bounus。
     */
    long getBonus(Long totalAmountCents) {
        if (totalAmountCents == null || totalAmountCents <= 0) {
            return 0;
        }
        Map<String, Object> config;
        try {
            config = configService.getFullConfig();
        } catch (Exception e) {
            logger.error("getBonus: failed to load config", e);
            return 0;
        }
        Object bonusObj = resolveDepositBonusConfig(config);
        if (bonusObj == null) {
            return 0;
        }
        long add = 0;
        for (String tier : iterateBonusTiers(bonusObj)) {
            if (tier == null) {
                continue;
            }
            String line = tier.trim();
            if (line.isEmpty() || !line.contains(":")) {
                continue;
            }
            String[] parts = line.split(":", 2);
            if (parts.length < 2) {
                continue;
            }
            try {
                long threshold = Math.round(Double.parseDouble(parts[0].trim()) * 100);
                long bonus = Math.round(Double.parseDouble(parts[1].trim()) * 100);
                if (totalAmountCents >= threshold) {
                    add = Math.max(add, bonus);
                }
            } catch (NumberFormatException ignored) {
                // skip invalid tier lines
            }
        }
        return add;
    }

    /** 读取 deposit.deposit_bounus（兼容误放在顶层的旧数据）。 */
    private static Object resolveDepositBonusConfig(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object deposit = config.get("deposit");
        if (deposit instanceof Map<?, ?> depositMap) {
            Object nested = depositMap.get("deposit_bounus");
            if (nested != null) {
                return nested;
            }
        }
        return config.get("deposit_bounus");
    }

    private static Iterable<String> iterateBonusTiers(Object bonusObj) {
        java.util.List<String> tiers = new java.util.ArrayList<>();
        if (bonusObj instanceof java.util.List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    tiers.add(String.valueOf(item));
                }
            }
        } else if (bonusObj instanceof Object[] arr) {
            for (Object item : arr) {
                if (item != null) {
                    tiers.add(String.valueOf(item));
                }
            }
        } else if (bonusObj instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    tiers.add(String.valueOf(item));
                }
            }
        }
        return tiers;
    }
}
