package com.v2board.api.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.model.Order;
import com.v2board.api.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单检查定时任务 — 对齐 PHP check:order
 * 每分钟查找 status in [0,1] 的订单并异步处理
 */
@Component
public class OrderSchedule {

    private static final Logger logger = LoggerFactory.getLogger(OrderSchedule.class);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderService orderService;

    @Scheduled(cron = "0 * * * * ?")
    public void checkOrder() {
        try {
            LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(Order::getStatus, 0, 1);
            List<Order> orders = orderMapper.selectList(wrapper);

            for (Order order : orders) {
                orderService.handleOrderAsync(order.getTradeNo());
            }

            if (!orders.isEmpty()) {
                logger.info("checkOrder: dispatched {} orders for processing", orders.size());
            }
        } catch (Exception e) {
            logger.error("OrderSchedule failed", e);
        }
    }
}
