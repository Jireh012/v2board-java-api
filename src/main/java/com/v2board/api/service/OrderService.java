package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

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
     * 将订单标记为已取消
     */
    public boolean cancelOrder(Order order) {
        if (order == null || order.getStatus() == null || order.getStatus() != 0) {
            return false;
        }
        order.setStatus(2);
        return orderMapper.updateById(order) > 0;
    }
}

