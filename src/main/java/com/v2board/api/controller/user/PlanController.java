package com.v2board.api.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user/plan")
public class PlanController {

    @Autowired
    private PlanMapper planMapper;

    @GetMapping("/fetch")
    public ApiResponse<Object> fetch(@RequestParam(value = "id", required = false) Long id,
                                    @RequestParam(value = "order_by", required = false) String orderBy,
                                    @RequestParam(value = "order", required = false) String order,
                                    User currentUser) {
        if (id != null) {
            Plan plan = planMapper.selectById(id);
            if (plan == null) {
                throw new BusinessException(500, "Subscription plan does not exist");
            }
            if ((!Boolean.TRUE.equals(plan.getShow()) && !Boolean.TRUE.equals(plan.getRenew()))
                    || (!Boolean.TRUE.equals(plan.getShow())
                    && currentUser != null
                    && currentUser.getPlanId() != null
                    && !currentUser.getPlanId().equals(plan.getId()))) {
                throw new BusinessException(500, "Subscription plan does not exist");
            }
            return ApiResponse.success(plan);
        }
        LambdaQueryWrapper<Plan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Plan::getShow, 1);
        boolean asc = !"desc".equalsIgnoreCase(order);
        if ("period".equalsIgnoreCase(orderBy)) {
            wrapper.orderBy(true, asc, Plan::getMonthPrice);
        } else if ("traffic".equalsIgnoreCase(orderBy)) {
            wrapper.orderBy(true, asc, Plan::getTransferEnable);
        } else {
            wrapper.orderByAsc(Plan::getSort);
        }
        List<Plan> plans = planMapper.selectList(wrapper);
        return ApiResponse.success(plans);
    }
}

