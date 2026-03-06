package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.PaymentMapper;
import com.v2board.api.model.Payment;
import com.v2board.api.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/payment")
public class AdminPaymentController {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${v2board.app-url:}")
    private String appUrl;

    /**
     * 返回可用的支付驱动名称列表。对齐 PHP getPaymentMethods()。
     */
    @GetMapping("/methods")
    public ApiResponse<List<String>> getPaymentMethods() {
        return ApiResponse.success(paymentService.getPaymentMethods());
    }

    /**
     * 列出所有支付方式，并附加 notify_url 字段。
     */
    @GetMapping("/fetch")
    public ApiResponse<List<Map<String, Object>>> fetch() {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Payment::getSort);
        List<Payment> list = paymentMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Payment p : list) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", p.getId());
            row.put("name", p.getName());
            row.put("payment", p.getPayment());
            row.put("icon", p.getIcon());
            row.put("handling_fee_fixed", p.getHandlingFeeFixed());
            row.put("handling_fee_percent", p.getHandlingFeePercent());
            row.put("enable", p.getEnable());
            row.put("sort", p.getSort());
            row.put("notify_domain", p.getNotifyDomain());
            row.put("uuid", p.getUuid());
            String base = p.getNotifyDomain();
            if (!StringUtils.hasText(base)) {
                base = appUrl;
            }
            String notifyUrl = (base != null && !base.isEmpty()
                    ? base
                    : "") + "/api/v1/guest/payment/notify/" + p.getPayment() + "/" + p.getUuid();
            row.put("notify_url", notifyUrl);
            result.add(row);
        }
        return ApiResponse.success(result);
    }

    /**
     * 获取某个支付方式的配置表单，占位实现，直接返回配置。
     */
    @GetMapping("/form")
    public ApiResponse<List<Map<String, Object>>> getPaymentForm(@RequestParam("payment") String payment,
            @RequestParam("id") Long id) {
        return ApiResponse.success(paymentService.form(payment, id));
    }

    /**
     * 启用/停用支付方式。
     */
    @PostMapping("/show")
    public ApiResponse<Boolean> show(@RequestParam("id") Long id) {
        Payment payment = paymentMapper.selectById(id);
        if (payment == null) {
            throw new BusinessException(500, "支付方式不存在");
        }
        Integer enable = payment.getEnable() != null ? payment.getEnable() : 0;
        payment.setEnable(enable == 1 ? 0 : 1);
        if (paymentMapper.updateById(payment) <= 0) {
            throw new BusinessException(500, "保存失败");
        }
        return ApiResponse.success(true);
    }

    /**
     * 创建或更新支付方式。
     */
    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody Map<String, Object> body) {
        if (!StringUtils.hasText(appUrl)) {
            throw new BusinessException(500, "请在站点配置中配置站点地址");
        }
        String name = (String) body.get("name");
        String paymentCode = (String) body.get("payment");
        Object configObj = body.get("config");
        String icon = (String) body.getOrDefault("icon", "");
        String notifyDomain = (String) body.getOrDefault("notify_domain", "");
        Number fixed = (Number) body.getOrDefault("handling_fee_fixed", 0);
        Number percent = (Number) body.getOrDefault("handling_fee_percent", 0);
        if (!StringUtils.hasText(name) || !StringUtils.hasText(paymentCode) || configObj == null) {
            throw new BusinessException(500, "参数有误");
        }
        String configJson;
        try {
            if (configObj instanceof String) {
                configJson = (String) configObj;
            } else {
                configJson = objectMapper.writeValueAsString(configObj);
            }
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "配置参数格式错误");
        }
        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        if (id != null) {
            Payment payment = paymentMapper.selectById(id);
            if (payment == null) {
                throw new BusinessException(500, "支付方式不存在");
            }
            payment.setName(name);
            payment.setPayment(paymentCode);
            payment.setConfig(configJson);
            payment.setIcon(icon);
            payment.setNotifyDomain(notifyDomain);
            payment.setHandlingFeeFixed(fixed.longValue());
            payment.setHandlingFeePercent(percent.intValue());
            if (paymentMapper.updateById(payment) <= 0) {
                throw new BusinessException(500, "保存失败");
            }
            return ApiResponse.success(true);
        }
        Payment payment = new Payment();
        payment.setName(name);
        payment.setPayment(paymentCode);
        payment.setConfig(configJson);
        payment.setIcon(icon);
        payment.setNotifyDomain(notifyDomain);
        payment.setHandlingFeeFixed(fixed.longValue());
        payment.setHandlingFeePercent(percent.intValue());
        payment.setEnable(0);
        payment.setUuid(UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        long now = System.currentTimeMillis() / 1000;
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        if (paymentMapper.insert(payment) <= 0) {
            throw new BusinessException(500, "保存失败");
        }
        return ApiResponse.success(true);
    }

    /**
     * 删除支付方式。
     */
    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        Payment payment = paymentMapper.selectById(id);
        if (payment == null) {
            throw new BusinessException(500, "支付方式不存在");
        }
        if (paymentMapper.deleteById(id) <= 0) {
            throw new BusinessException(500, "删除失败");
        }
        return ApiResponse.success(true);
    }

    /**
     * 支付方式排序。
     */
    @PostMapping("/sort")
    public ApiResponse<Boolean> sort(@RequestBody List<Long> ids) {
        int sort = 1;
        for (Long id : ids) {
            Payment payment = paymentMapper.selectById(id);
            if (payment != null) {
                payment.setSort(sort++);
                paymentMapper.updateById(payment);
            }
        }
        return ApiResponse.success(true);
    }
}
