package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.CouponMapper;
import com.v2board.api.model.Coupon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理端优惠券管理，对齐 PHP Admin\CouponController。
 */
@RestController
@RequestMapping("/api/v1/admin/coupon")
public class AdminCouponController {

    @Autowired
    private CouponMapper couponMapper;

    @GetMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "sort_type", defaultValue = "DESC") String sortType,
            @RequestParam(value = "sort", defaultValue = "id") String sort) {
        if (pageSize < 10) pageSize = 10;
        if (!sortType.equals("ASC") && !sortType.equals("DESC")) sortType = "DESC";

        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        if ("DESC".equals(sortType)) {
            wrapper.orderByDesc(Coupon::getId);
        } else {
            wrapper.orderByAsc(Coupon::getId);
        }
        long total = couponMapper.selectCount(new LambdaQueryWrapper<>());
        int offset = (current - 1) * pageSize;
        wrapper.last("LIMIT " + offset + "," + pageSize);
        List<Coupon> coupons = couponMapper.selectList(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("data", coupons);
        result.put("total", total);
        return ApiResponse.success(result);
    }

    @PostMapping("/generate")
    public ApiResponse<Boolean> generate(@RequestBody Coupon body) {
        long now = System.currentTimeMillis() / 1000;
        if (body.getId() == null) {
            // 新建
            if (body.getCode() == null || body.getCode().isEmpty()) {
                body.setCode(randomChar(8));
            }
            if (body.getShow() == null) body.setShow(1);
            body.setCreatedAt(now);
            body.setUpdatedAt(now);
            if (couponMapper.insert(body) <= 0) {
                throw new BusinessException(500, "创建失败");
            }
        } else {
            // 更新
            Coupon existing = couponMapper.selectById(body.getId());
            if (existing == null) {
                throw new BusinessException(500, "优惠券不存在");
            }
            body.setUpdatedAt(now);
            body.setCreatedAt(existing.getCreatedAt());
            if (couponMapper.updateById(body) <= 0) {
                throw new BusinessException(500, "保存失败");
            }
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/show")
    public ApiResponse<Boolean> show(@RequestParam("id") Long id) {
        if (id == null) throw new BusinessException(500, "参数有误");
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) throw new BusinessException(500, "优惠券不存在");
        coupon.setShow(coupon.getShow() != null && coupon.getShow() == 1 ? 0 : 1);
        if (couponMapper.updateById(coupon) <= 0) throw new BusinessException(500, "保存失败");
        return ApiResponse.success(true);
    }

    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        if (id == null) throw new BusinessException(500, "参数有误");
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) throw new BusinessException(500, "优惠券不存在");
        if (couponMapper.deleteById(id) <= 0) throw new BusinessException(500, "删除失败");
        return ApiResponse.success(true);
    }

    private static String randomChar(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
