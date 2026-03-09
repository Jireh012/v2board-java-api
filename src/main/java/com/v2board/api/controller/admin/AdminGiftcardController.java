package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.GiftcardMapper;
import com.v2board.api.model.Giftcard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理端礼品卡管理，对齐 PHP Admin\GiftcardController。
 */
@RestController
@RequestMapping("/api/v1/admin/giftcard")
public class AdminGiftcardController {

    @Autowired
    private GiftcardMapper giftcardMapper;

    @GetMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "sort_type", defaultValue = "DESC") String sortType) {
        if (pageSize < 10) pageSize = 10;
        if (!sortType.equals("ASC") && !sortType.equals("DESC")) sortType = "DESC";

        LambdaQueryWrapper<Giftcard> wrapper = new LambdaQueryWrapper<>();
        if ("DESC".equals(sortType)) {
            wrapper.orderByDesc(Giftcard::getId);
        } else {
            wrapper.orderByAsc(Giftcard::getId);
        }
        long total = giftcardMapper.selectCount(new LambdaQueryWrapper<>());
        int offset = (current - 1) * pageSize;
        wrapper.last("LIMIT " + offset + "," + pageSize);
        List<Giftcard> list = giftcardMapper.selectList(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("data", list);
        result.put("total", total);
        return ApiResponse.success(result);
    }

    @PostMapping("/generate")
    public ApiResponse<Boolean> generate(@RequestBody Giftcard body) {
        long now = System.currentTimeMillis() / 1000;
        if (body.getId() == null) {
            if (body.getCode() == null || body.getCode().isEmpty()) {
                body.setCode(randomChar(16));
            }
            body.setCreatedAt(now);
            body.setUpdatedAt(now);
            if (giftcardMapper.insert(body) <= 0) {
                throw new BusinessException(500, "创建失败");
            }
        } else {
            Giftcard existing = giftcardMapper.selectById(body.getId());
            if (existing == null) {
                throw new BusinessException(500, "礼品卡不存在");
            }
            body.setUpdatedAt(now);
            body.setCreatedAt(existing.getCreatedAt());
            if (giftcardMapper.updateById(body) <= 0) {
                throw new BusinessException(500, "保存失败");
            }
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        if (id == null) throw new BusinessException(500, "参数有误");
        Giftcard giftcard = giftcardMapper.selectById(id);
        if (giftcard == null) throw new BusinessException(500, "礼品卡不存在");
        if (giftcardMapper.deleteById(id) <= 0) throw new BusinessException(500, "删除失败");
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
