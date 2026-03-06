package com.v2board.api.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import com.v2board.api.service.OrderService;
import com.v2board.api.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 自动续费定时任务 — 对齐 PHP check:renewal
 * 每天 22:30 执行：检查即将到期（2 天内）的自动续费用户
 */
@Component
public class RenewalSchedule {

    private static final Logger logger = LoggerFactory.getLogger(RenewalSchedule.class);

    private static final Map<String, String> PERIOD_PRICE_FIELDS = Map.of(
            "month_price", "monthPrice",
            "quarter_price", "quarterPrice",
            "half_year_price", "halfYearPrice",
            "year_price", "yearPrice",
            "two_year_price", "twoYearPrice",
            "three_year_price", "threeYearPrice"
    );

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderService orderService;

    @Scheduled(cron = "0 30 22 * * ?")
    public void checkRenewal() {
        try {
            long now = System.currentTimeMillis() / 1000;
            long twoDaysLater = now + 2 * 86400;

            // 查找自动续费、有 plan、即将到期的用户
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getAutoRenewal, 1)
                    .isNotNull(User::getPlanId)
                    .gt(User::getExpiredAt, now)
                    .le(User::getExpiredAt, twoDaysLater);

            List<User> users = userMapper.selectList(wrapper);

            for (User user : users) {
                try {
                    processRenewal(user);
                } catch (Exception e) {
                    logger.error("checkRenewal failed for user {}", user.getId(), e);
                    // 关闭自动续费
                    user.setAutoRenewal(0);
                    user.setUpdatedAt(System.currentTimeMillis() / 1000);
                    userMapper.updateById(user);
                }
            }

            if (!users.isEmpty()) {
                logger.info("checkRenewal: processed {} users", users.size());
            }
        } catch (Exception e) {
            logger.error("RenewalSchedule failed", e);
        }
    }

    @Transactional
    public void processRenewal(User user) {
        // 查找最近一笔完成的周期订单（排除 reset_price / onetime_price / deposit）
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getUserId, user.getId())
                .eq(Order::getStatus, 3)
                .ne(Order::getPeriod, "reset_price")
                .ne(Order::getPeriod, "onetime_price")
                .ne(Order::getPeriod, "deposit")
                .orderByDesc(Order::getId)
                .last("LIMIT 1");
        Order lastOrder = orderMapper.selectOne(orderWrapper);
        if (lastOrder == null) {
            disableAutoRenewal(user);
            return;
        }

        Plan plan = planMapper.selectById(user.getPlanId());
        if (plan == null || plan.getRenew() == null || plan.getRenew() != 1) {
            disableAutoRenewal(user);
            return;
        }

        String period = lastOrder.getPeriod();
        Integer price = getPlanPrice(plan, period);
        if (price == null || price <= 0) {
            disableAutoRenewal(user);
            return;
        }

        long userBalance = user.getBalance() != null ? user.getBalance() : 0L;
        if (userBalance < price) {
            disableAutoRenewal(user);
            return;
        }

        // 创建续费订单
        long now = System.currentTimeMillis() / 1000;
        Order newOrder = new Order();
        newOrder.setUserId(user.getId());
        newOrder.setPlanId(plan.getId());
        newOrder.setPeriod(period);
        newOrder.setTradeNo(Helper.generateOrderNo());
        newOrder.setTotalAmount((long) price);
        newOrder.setBalanceAmount((long) price);
        newOrder.setStatus(1); // 已支付
        newOrder.setType(2); // 续费
        newOrder.setPaidAt(now);
        newOrder.setCreatedAt(now);
        newOrder.setUpdatedAt(now);
        orderMapper.insert(newOrder);

        // 扣除余额
        user.setBalance(userBalance - price);
        user.setUpdatedAt(now);
        userMapper.updateById(user);

        // 激活订单
        orderService.open(newOrder);
    }

    private void disableAutoRenewal(User user) {
        user.setAutoRenewal(0);
        user.setUpdatedAt(System.currentTimeMillis() / 1000);
        userMapper.updateById(user);
    }

    private Integer getPlanPrice(Plan plan, String period) {
        return switch (period) {
            case "month_price" -> plan.getMonthPrice();
            case "quarter_price" -> plan.getQuarterPrice();
            case "half_year_price" -> plan.getHalfYearPrice();
            case "year_price" -> plan.getYearPrice();
            case "two_year_price" -> plan.getTwoYearPrice();
            case "three_year_price" -> plan.getThreeYearPrice();
            default -> null;
        };
    }
}
