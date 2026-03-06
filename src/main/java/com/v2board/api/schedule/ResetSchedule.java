package com.v2board.api.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import com.v2board.api.service.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 流量重置定时任务 — 对齐 PHP reset:traffic
 * 每天凌晨执行，按 plan 的 reset_traffic_method 重置用户流量
 */
@Component
public class ResetSchedule {

    private static final Logger logger = LoggerFactory.getLogger(ResetSchedule.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TelegramService telegramService;

    @Value("${v2board.reset-traffic-method:0}")
    private Integer defaultResetTrafficMethod;

    @Scheduled(cron = "0 0 0 * * ?")
    public void resetTraffic() {
        logger.info("ResetSchedule: starting traffic reset");

        // 设置 Redis 锁，阻止 TrafficSchedule 并发执行
        redisTemplate.opsForValue().set("traffic_reset_lock", "1", 300, TimeUnit.SECONDS);

        try {
            // 获取所有 plan，按 resetTrafficMethod 分组
            List<Plan> plans = planMapper.selectList(null);
            Map<Integer, List<Long>> methodPlans = new HashMap<>();
            for (Plan plan : plans) {
                int method = plan.getResetTrafficMethod() != null
                        ? plan.getResetTrafficMethod() : defaultResetTrafficMethod;
                methodPlans.computeIfAbsent(method, k -> new ArrayList<>()).add(plan.getId());
            }

            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            long timestamp = System.currentTimeMillis() / 1000;

            for (Map.Entry<Integer, List<Long>> entry : methodPlans.entrySet()) {
                int method = entry.getKey();
                List<Long> planIds = entry.getValue();

                try {
                    List<Long> userIds = switch (method) {
                        case 0 -> resetByMonthFirstDay(planIds, now);
                        case 1 -> resetByExpireDay(planIds, now);
                        case 2 -> List.of(); // 不重置
                        case 3 -> resetByYearFirstDay(planIds, now);
                        case 4 -> resetByExpireYear(planIds, now);
                        default -> List.of();
                    };

                    if (!userIds.isEmpty()) {
                        resetWithRetry(userIds, timestamp);
                        logger.info("ResetSchedule: method={}, reset {} users", method, userIds.size());
                    }
                } catch (Exception e) {
                    logger.error("ResetSchedule: method={} failed", method, e);
                    telegramService.sendMessageWithAdmin("⚠️ 流量重置失败 method=" + method + ": " + e.getMessage());
                }
            }

            logger.info("ResetSchedule: traffic reset completed");
        } finally {
            redisTemplate.delete("traffic_reset_lock");
        }
    }

    /** Method 0: 每月 1 日重置 */
    private List<Long> resetByMonthFirstDay(List<Long> planIds, ZonedDateTime now) {
        if (now.getDayOfMonth() != 1) return List.of();
        return getUserIdsByPlanIds(planIds);
    }

    /** Method 1: 到期日重置（每月到期日当天） */
    private List<Long> resetByExpireDay(List<Long> planIds, ZonedDateTime now) {
        int today = now.getDayOfMonth();
        List<User> users = getUsersByPlanIds(planIds);
        return users.stream()
                .filter(u -> u.getExpiredAt() != null)
                .filter(u -> {
                    ZonedDateTime expireTime = Instant.ofEpochSecond(u.getExpiredAt()).atZone(ZoneId.systemDefault());
                    return expireTime.getDayOfMonth() == today;
                })
                .map(User::getId)
                .collect(Collectors.toList());
    }

    /** Method 3: 每年 1 月 1 日重置 */
    private List<Long> resetByYearFirstDay(List<Long> planIds, ZonedDateTime now) {
        if (now.getMonthValue() != 1 || now.getDayOfMonth() != 1) return List.of();
        return getUserIdsByPlanIds(planIds);
    }

    /** Method 4: 每年到期日重置 */
    private List<Long> resetByExpireYear(List<Long> planIds, ZonedDateTime now) {
        int todayMonth = now.getMonthValue();
        int todayDay = now.getDayOfMonth();
        List<User> users = getUsersByPlanIds(planIds);
        return users.stream()
                .filter(u -> u.getExpiredAt() != null)
                .filter(u -> {
                    ZonedDateTime expireTime = Instant.ofEpochSecond(u.getExpiredAt()).atZone(ZoneId.systemDefault());
                    return expireTime.getMonthValue() == todayMonth && expireTime.getDayOfMonth() == todayDay;
                })
                .map(User::getId)
                .collect(Collectors.toList());
    }

    /** 带重试的批量重置 */
    private void resetWithRetry(List<Long> userIds, long timestamp) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                userMapper.resetTrafficByUserIds(userIds, timestamp);
                return;
            } catch (Exception e) {
                if (attempt < 2) {
                    logger.warn("resetTraffic deadlock, retry attempt {}", attempt + 1);
                    try {
                        Thread.sleep((long) Math.pow(2, attempt) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    private List<Long> getUserIdsByPlanIds(List<Long> planIds) {
        return getUsersByPlanIds(planIds).stream().map(User::getId).collect(Collectors.toList());
    }

    private List<User> getUsersByPlanIds(List<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) return List.of();
        long now = System.currentTimeMillis() / 1000;
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getPlanId, planIds);
        wrapper.and(q -> q.gt(User::getExpiredAt, now).or().isNull(User::getExpiredAt));
        wrapper.eq(User::getBanned, 0);
        return userMapper.selectList(wrapper);
    }
}
