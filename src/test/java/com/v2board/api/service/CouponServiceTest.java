package com.v2board.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.CouponMapper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.model.Coupon;
import com.v2board.api.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponServiceTest {

    private CouponMapper couponMapper;
    private CouponService service;

    @BeforeEach
    void setUp() {
        couponMapper = mock(CouponMapper.class);
        service = new CouponService();
        ReflectionTestUtils.setField(service, "couponMapper", couponMapper);
        ReflectionTestUtils.setField(service, "orderMapper", mock(OrderMapper.class));
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void use_amountCoupon_setsDiscountOnly() {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setCode("SAVE10");
        c.setShow(1);
        c.setType(1);
        c.setValue(100);
        c.setStartedAt(0L);
        c.setEndedAt(System.currentTimeMillis() / 1000 + 3600);
        when(couponMapper.selectOne(any())).thenReturn(c);

        Order order = new Order();
        order.setPlanId(1L);
        order.setUserId(1L);
        order.setPeriod("month_price");
        order.setTotalAmount(1000L);

        service.use("SAVE10", order);
        assertEquals(100L, order.getDiscountAmount());
        assertEquals(1000L, order.getTotalAmount());
        assertEquals(1L, order.getCouponId());
    }

    @Test
    void use_invalid_throws() {
        when(couponMapper.selectOne(any())).thenReturn(null);
        Order order = new Order();
        order.setTotalAmount(1000L);
        assertThrows(BusinessException.class, () -> service.use("NOPE", order));
    }
}
