package com.v2board.api.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.CommissionLogMapper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.CommissionLog;
import com.v2board.api.model.Order;
import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 佣金结算定时任务 — 对齐 PHP check:commission
 * 每 15 分钟执行：自动审核 + 自动发放佣金
 */
@Component
public class CommissionSchedule {

    private static final Logger logger = LoggerFactory.getLogger(CommissionSchedule.class);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CommissionLogMapper commissionLogMapper;

    @Autowired
    private ConfigService configService;

    @Scheduled(cron = "0 */15 * * * ?")
    public void checkCommission() {
        try {
            autoCheck();
            autoPayCommission();
        } catch (Exception e) {
            logger.error("CommissionSchedule failed", e);
        }
    }

    /**
     * 自动审核：已完成 3 天以上的订单 commission_status 从 0 → 1
     */
    private void autoCheck() throws Exception {
        Map<String, Object> config = configService.getFullConfig();
        Object autoCheckEnable = config.get("commission_auto_check_enable");
        if (autoCheckEnable != null && "0".equals(String.valueOf(autoCheckEnable))) {
            return;
        }

        long threeDaysAgo = System.currentTimeMillis() / 1000 - 3 * 86400;

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getCommissionStatus, 0)
                .isNotNull(Order::getInviteUserId)
                .in(Order::getStatus, 3, 4)
                .le(Order::getUpdatedAt, threeDaysAgo);

        List<Order> orders = orderMapper.selectList(wrapper);
        for (Order order : orders) {
            order.setCommissionStatus(1);
            order.setUpdatedAt(System.currentTimeMillis() / 1000);
            orderMapper.updateById(order);
        }

        if (!orders.isEmpty()) {
            logger.info("autoCheck: approved {} commission orders", orders.size());
        }
    }

    /**
     * 自动发放佣金：commission_status=1 的订单逐个发放
     */
    private void autoPayCommission() {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getCommissionStatus, 1)
                .isNotNull(Order::getInviteUserId);

        List<Order> orders = orderMapper.selectList(wrapper);
        for (Order order : orders) {
            try {
                payHandle(order);
            } catch (Exception e) {
                logger.error("autoPayCommission failed for order {}", order.getTradeNo(), e);
            }
        }
    }

    /**
     * 发放佣金 — 对齐 PHP CheckCommission::payHandle()
     * 支持 3 级分销
     */
    @Transactional
    public void payHandle(Order order) throws Exception {
        Map<String, Object> config = configService.getFullConfig();

        int[] commissionShareLevels;
        Object distEnable = config.get("commission_distribution_enable");
        if (distEnable != null && !"0".equals(String.valueOf(distEnable))) {
            commissionShareLevels = new int[]{
                    toInt(config.get("commission_distribution_l1"), 100),
                    toInt(config.get("commission_distribution_l2"), 0),
                    toInt(config.get("commission_distribution_l3"), 0)
            };
        } else {
            commissionShareLevels = new int[]{100};
        }

        boolean withdrawClose = "1".equals(String.valueOf(config.get("withdraw_close_enable")));
        Long inviteUserId = order.getInviteUserId();
        long actualCommission = order.getActualCommissionBalance() != null ? order.getActualCommissionBalance() : 0L;

        for (int l = 0; l < 3 && inviteUserId != null; l++) {
            User inviter = userMapper.selectById(inviteUserId);
            if (inviter == null) break;
            if (l >= commissionShareLevels.length) break;

            long commissionBalance = order.getCommissionBalance() != null ? order.getCommissionBalance() : 0L;
            long amount = commissionBalance * commissionShareLevels[l] / 100;
            if (amount <= 0) continue;

            if (withdrawClose) {
                inviter.setBalance((inviter.getBalance() != null ? inviter.getBalance() : 0L) + amount);
            } else {
                inviter.setCommissionBalance(
                        (inviter.getCommissionBalance() != null ? inviter.getCommissionBalance() : 0L) + amount);
            }
            inviter.setUpdatedAt(System.currentTimeMillis() / 1000);
            userMapper.updateById(inviter);

            CommissionLog log = new CommissionLog();
            log.setInviteUserId(inviteUserId);
            log.setUserId(order.getUserId());
            log.setTradeNo(order.getTradeNo());
            log.setOrderAmount(order.getTotalAmount());
            log.setGetAmount(amount);
            long now = System.currentTimeMillis() / 1000;
            log.setCreatedAt(now);
            log.setUpdatedAt(now);
            commissionLogMapper.insert(log);

            actualCommission += amount;
            inviteUserId = inviter.getInviteUserId();
        }

        order.setCommissionStatus(2);
        order.setActualCommissionBalance(actualCommission);
        order.setUpdatedAt(System.currentTimeMillis() / 1000);
        orderMapper.updateById(order);
    }

    private int toInt(Object obj, int defaultVal) {
        if (obj == null) return defaultVal;
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
