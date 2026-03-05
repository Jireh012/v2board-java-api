package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import com.v2board.api.service.PlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/plan")
public class AdminPlanController {

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PlanService planService;

    /**
     * 对齐 PHP Admin\\PlanController::fetch
     * 返回所有套餐，并附加当前有效用户数量 count 字段。
     */
    @GetMapping("/fetch")
    public ApiResponse<List<Plan>> fetch() {
        Map<Long, Long> counts = planService.countActiveUsers();
        LambdaQueryWrapper<Plan> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Plan::getSort);
        List<Plan> plans = planMapper.selectList(wrapper);
        for (Plan p : plans) {
            Long c = counts.getOrDefault(p.getId(), 0L);
            p.setCount(c);
        }
        return ApiResponse.success(plans);
    }

    /**
     * 创建或更新套餐。
     * 对齐 PHP Admin\\PlanController::save 的主要行为，未实现 force_update 对
     * user.transfer_enable 的整体更新。
     */
    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody Plan body,
            @RequestParam(value = "force_update", required = false, defaultValue = "false") boolean forceUpdate) {
        if (body.getId() != null) {
            Plan plan = planMapper.selectById(body.getId());
            if (plan == null) {
                throw new BusinessException(500, "该订阅不存在");
            }
            if (forceUpdate) {
                LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(User::getPlanId, plan.getId());
                List<User> users = userMapper.selectList(wrapper);
                for (User u : users) {
                    u.setGroupId(body.getGroupId());
                    if (body.getTransferEnable() != null) {
                        u.setTransferEnable(body.getTransferEnable() * 1073741824L);
                    }
                    // speed_limit 字段可在 User 模型扩展后一并更新
                    userMapper.updateById(u);
                }
            }
            body.setCreatedAt(plan.getCreatedAt());
            planMapper.updateById(body);
            return ApiResponse.success(true);
        }
        long now = System.currentTimeMillis() / 1000;
        body.setCreatedAt(now);
        body.setUpdatedAt(now);
        if (planMapper.insert(body) <= 0) {
            throw new BusinessException(500, "创建失败");
        }
        return ApiResponse.success(true);
    }

    /**
     * 删除套餐，需无关联订单与用户。
     */
    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getPlanId, id);
        if (orderMapper.selectCount(orderWrapper) > 0) {
            throw new BusinessException(500, "该订阅下存在订单无法删除");
        }
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getPlanId, id);
        if (userMapper.selectCount(userWrapper) > 0) {
            throw new BusinessException(500, "该订阅下存在用户无法删除");
        }
        Plan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(500, "该订阅ID不存在");
        }
        if (planMapper.deleteById(id) <= 0) {
            throw new BusinessException(500, "删除失败");
        }
        return ApiResponse.success(true);
    }

    /**
     * 更新套餐的 show / renew 字段。
     */
    @PostMapping("/update")
    public ApiResponse<Boolean> update(@RequestParam("id") Long id,
            @RequestParam(value = "show", required = false) Integer show,
            @RequestParam(value = "renew", required = false) Integer renew) {
        Plan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(500, "该订阅不存在");
        }
        if (show != null) {
            plan.setShow(show);
        }
        if (renew != null) {
            plan.setRenew(renew);
        }
        if (planMapper.updateById(plan) <= 0) {
            throw new BusinessException(500, "保存失败");
        }
        return ApiResponse.success(true);
    }

    /**
     * 套餐排序。
     */
    @PostMapping("/sort")
    public ApiResponse<Boolean> sort(@RequestBody List<Long> planIds) {
        int sort = 1;
        for (Long id : planIds) {
            Plan plan = planMapper.selectById(id);
            if (plan != null) {
                plan.setSort(sort++);
                planMapper.updateById(plan);
            }
        }
        return ApiResponse.success(true);
    }
}
