package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
        validate(body);
        long now = System.currentTimeMillis() / 1000;
        if (body.getId() == null) {
            if (body.getCode() == null || body.getCode().isBlank()) {
                body.setCode(randomChar(16));
            }
            if (body.getType() == 4) body.setValue(0);
            if (body.getType() != 5) body.setPlanId(null);
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
            Integer value = body.getType() == 4 ? 0 : body.getValue();
            Long planId = body.getType() == 5 ? body.getPlanId() : null;
            LambdaUpdateWrapper<Giftcard> uw = new LambdaUpdateWrapper<>();
            uw.eq(Giftcard::getId, existing.getId())
                    .set(Giftcard::getName, body.getName().trim())
                    .set(Giftcard::getCode, body.getCode() != null && !body.getCode().isBlank()
                            ? body.getCode().trim() : existing.getCode())
                    .set(Giftcard::getType, body.getType())
                    .set(Giftcard::getValue, value)
                    .set(Giftcard::getPlanId, planId)
                    .set(Giftcard::getLimitUse, body.getLimitUse())
                    .set(Giftcard::getStartedAt, body.getStartedAt())
                    .set(Giftcard::getEndedAt, body.getEndedAt())
                    .set(Giftcard::getUpdatedAt, now);
            if (giftcardMapper.update(null, uw) <= 0) {
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

    private static void validate(Giftcard body) {
        if (body.getName() == null || body.getName().trim().isEmpty()) {
            throw new BusinessException(500, "礼品卡名称不能为空");
        }
        if (body.getType() == null || body.getType() < 1 || body.getType() > 5) {
            throw new BusinessException(500, "礼品卡类型有误");
        }
        if (body.getType() == 5 && body.getPlanId() == null) {
            throw new BusinessException(500, "请选择指定套餐");
        }
        if (body.getType() != 4) {
            if (body.getValue() == null) {
                throw new BusinessException(500, "面值有误");
            }
            if (body.getType() != 5 && body.getValue() <= 0) {
                throw new BusinessException(500, "面值必须大于 0");
            }
            if (body.getType() == 5 && body.getValue() < 0) {
                throw new BusinessException(500, "套餐天数不能为负");
            }
        }
        if (body.getStartedAt() == null || body.getEndedAt() == null) {
            throw new BusinessException(500, "请设置有效期");
        }
        if (body.getEndedAt() <= body.getStartedAt()) {
            throw new BusinessException(500, "结束时间必须晚于开始时间");
        }
    }

    private static String randomChar(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
