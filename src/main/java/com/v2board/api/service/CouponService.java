package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.CouponMapper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.model.Coupon;
import com.v2board.api.model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * PHP App\Services\CouponService parity for user order save.
 */
@Service
public class CouponService {

    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Apply coupon to order: sets discount_amount and coupon_id; may decrement limit_use.
     */
    public void use(String code, Order order) {
        if (!StringUtils.hasText(code) || order == null) {
            return;
        }
        Coupon coupon = couponMapper.selectOne(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getCode, code.trim())
                .last("LIMIT 1"));
        if (coupon == null || coupon.getShow() == null || coupon.getShow() != 1) {
            throw new BusinessException(500, "Invalid coupon");
        }
        check(coupon, order.getPlanId(), order.getUserId(), order.getPeriod());

        // PHP only sets discount_amount here; total is reduced later in setVipDiscount.
        long total = order.getTotalAmount() != null ? order.getTotalAmount() : 0L;
        long discount;
        if (coupon.getType() != null && coupon.getType() == 1) {
            discount = coupon.getValue() != null ? coupon.getValue().longValue() : 0L;
        } else if (coupon.getType() != null && coupon.getType() == 2) {
            discount = total * (coupon.getValue() != null ? coupon.getValue() : 0) / 100;
        } else {
            throw new BusinessException(500, "Invalid coupon");
        }
        if (discount > total) {
            discount = total;
        }
        order.setDiscountAmount(discount);
        order.setCouponId(coupon.getId());

        if (coupon.getLimitUse() != null) {
            if (coupon.getLimitUse() <= 0) {
                throw new BusinessException(500, "This coupon is no longer available");
            }
            coupon.setLimitUse(coupon.getLimitUse() - 1);
            coupon.setUpdatedAt(System.currentTimeMillis() / 1000);
            if (couponMapper.updateById(coupon) <= 0) {
                throw new BusinessException(500, "Coupon failed");
            }
        }
    }

    void check(Coupon coupon, Long planId, Long userId, String period) {
        long now = System.currentTimeMillis() / 1000;
        if (coupon.getLimitUse() != null && coupon.getLimitUse() <= 0) {
            throw new BusinessException(500, "This coupon is no longer available");
        }
        if (coupon.getStartedAt() != null && now < coupon.getStartedAt()) {
            throw new BusinessException(500, "This coupon has not yet started");
        }
        if (coupon.getEndedAt() != null && now > coupon.getEndedAt()) {
            throw new BusinessException(500, "This coupon has expired");
        }
        List<Long> planIds = parseLongList(coupon.getLimitPlanIds());
        if (!planIds.isEmpty() && planId != null && !planIds.contains(planId)) {
            throw new BusinessException(500, "The coupon code cannot be used for this subscription");
        }
        List<String> periods = parseStringList(coupon.getLimitPeriod());
        if (!periods.isEmpty() && StringUtils.hasText(period) && !periods.contains(period)) {
            throw new BusinessException(500, "The coupon code cannot be used for this period");
        }
        if (coupon.getLimitUseWithUser() != null && userId != null) {
            long used = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .eq(Order::getCouponId, coupon.getId())
                    .eq(Order::getUserId, userId)
                    .notIn(Order::getStatus, 0, 2));
            if (used >= coupon.getLimitUseWithUser()) {
                throw new BusinessException(500,
                        "The coupon can only be used " + coupon.getLimitUseWithUser() + " per person");
            }
        }
    }

    private List<Long> parseLongList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<String> parseStringList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
