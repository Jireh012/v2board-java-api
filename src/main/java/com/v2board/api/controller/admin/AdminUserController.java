package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/user")
public class AdminUserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PlanMapper planMapper;

    @GetMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        if (pageSize < 10) {
            pageSize = 10;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getCreatedAt);
        int offset = (current - 1) * pageSize;
        List<User> users = userMapper.selectList(wrapper.last("LIMIT " + offset + "," + pageSize));
        long total = userMapper.selectCount(new LambdaQueryWrapper<>());

        List<Plan> plans = planMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, String> planNameMap = new HashMap<>();
        for (Plan plan : plans) {
            planNameMap.put(plan.getId(), plan.getName());
        }
        for (User user : users) {
            if (user.getPlanId() != null) {
                user.setGroupId(planNameMap.getOrDefault(user.getPlanId(), null) != null ? user.getGroupId() : user.getGroupId());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("data", users);
        result.put("total", total);
        return ApiResponse.success(result);
    }

    @PostMapping("/resetSecret")
    public ApiResponse<Boolean> resetSecret(@RequestParam("id") Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return ApiResponse.error(500, "用户不存在");
        }
        user.setToken(java.util.UUID.randomUUID().toString());
        user.setUuid(java.util.UUID.randomUUID().toString());
        userMapper.updateById(user);
        return ApiResponse.success(true);
    }
}

