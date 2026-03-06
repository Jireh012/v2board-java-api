package com.v2board.api.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.*;
import com.v2board.api.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 每日统计定时任务 — 对齐 PHP v2board:statistics
 * 每天 00:10 统计前一天的订单/支付/佣金/注册/流量数据
 */
@Component
public class StatisticsSchedule {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsSchedule.class);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CommissionLogMapper commissionLogMapper;

    @Autowired
    private StatServerMapper statServerMapper;

    @Autowired
    private StatMapper statMapper;

    @Scheduled(cron = "0 10 0 * * ?")
    public void generateStatistics() {
        try {
            LocalDate yesterday = LocalDate.now(ZoneId.systemDefault()).minusDays(1);
            long dayStart = yesterday.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            long dayEnd = yesterday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond();

            // 订单统计
            LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
            orderWrapper.ge(Order::getCreatedAt, dayStart).lt(Order::getCreatedAt, dayEnd);
            Long orderCount = orderMapper.selectCount(orderWrapper);

            LambdaQueryWrapper<Order> orderTotalWrapper = new LambdaQueryWrapper<>();
            orderTotalWrapper.ge(Order::getCreatedAt, dayStart).lt(Order::getCreatedAt, dayEnd);
            java.util.List<Order> allOrders = orderMapper.selectList(orderTotalWrapper);
            long orderTotal = allOrders.stream()
                    .mapToLong(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0L).sum();

            // 已支付订单
            LambdaQueryWrapper<Order> paidWrapper = new LambdaQueryWrapper<>();
            paidWrapper.ge(Order::getPaidAt, dayStart).lt(Order::getPaidAt, dayEnd)
                    .ne(Order::getStatus, 0);
            Long paidCount = orderMapper.selectCount(paidWrapper);
            java.util.List<Order> paidOrders = orderMapper.selectList(paidWrapper);
            long paidTotal = paidOrders.stream()
                    .mapToLong(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0L).sum();

            // 佣金统计
            LambdaQueryWrapper<CommissionLog> commWrapper = new LambdaQueryWrapper<>();
            commWrapper.ge(CommissionLog::getCreatedAt, dayStart).lt(CommissionLog::getCreatedAt, dayEnd);
            Long commissionCount = commissionLogMapper.selectCount(commWrapper);
            java.util.List<CommissionLog> commLogs = commissionLogMapper.selectList(commWrapper);
            long commissionTotal = commLogs.stream()
                    .mapToLong(c -> c.getGetAmount() != null ? c.getGetAmount() : 0L).sum();

            // 注册统计
            LambdaQueryWrapper<User> regWrapper = new LambdaQueryWrapper<>();
            regWrapper.ge(User::getCreatedAt, dayStart).lt(User::getCreatedAt, dayEnd);
            Long registerCount = userMapper.selectCount(regWrapper);

            // 邀请注册统计
            LambdaQueryWrapper<User> inviteWrapper = new LambdaQueryWrapper<>();
            inviteWrapper.ge(User::getCreatedAt, dayStart).lt(User::getCreatedAt, dayEnd)
                    .isNotNull(User::getInviteUserId);
            Long inviteCount = userMapper.selectCount(inviteWrapper);

            // 流量统计
            LambdaQueryWrapper<StatServer> trafficWrapper = new LambdaQueryWrapper<>();
            trafficWrapper.ge(StatServer::getRecordAt, dayStart).lt(StatServer::getRecordAt, dayEnd);
            java.util.List<StatServer> statServers = statServerMapper.selectList(trafficWrapper);
            long transferUsedTotal = statServers.stream()
                    .mapToLong(s -> (s.getU() != null ? s.getU() : 0L) + (s.getD() != null ? s.getD() : 0L)).sum();

            // 写入 Stat 表（upsert）
            LambdaQueryWrapper<Stat> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(Stat::getRecordAt, dayStart).eq(Stat::getRecordType, "d");
            Stat stat = statMapper.selectOne(existWrapper);

            long now = System.currentTimeMillis() / 1000;
            if (stat == null) {
                stat = new Stat();
                stat.setRecordAt(dayStart);
                stat.setRecordType("d");
                stat.setCreatedAt(now);
            }
            stat.setOrderCount(orderCount.intValue());
            stat.setOrderTotal(orderTotal);
            stat.setPaidCount(paidCount.intValue());
            stat.setPaidTotal(paidTotal);
            stat.setCommissionCount(commissionCount.intValue());
            stat.setCommissionTotal(commissionTotal);
            stat.setRegisterCount(registerCount.intValue());
            stat.setInviteCount(inviteCount.intValue());
            stat.setTransferUsedTotal(transferUsedTotal);
            stat.setUpdatedAt(now);

            if (stat.getId() == null) {
                statMapper.insert(stat);
            } else {
                statMapper.updateById(stat);
            }

            logger.info("StatisticsSchedule: recorded stats for {}", yesterday);
        } catch (Exception e) {
            logger.error("StatisticsSchedule failed", e);
        }
    }
}
