package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlanService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PlanMapper planMapper;

    /**
     * 统计各个套餐下的有效用户数量，对齐 PHP PlanService::countActiveUsers。
     */
    public Map<Long, Long> countActiveUsers() {
        long now = System.currentTimeMillis() / 1000;
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(User::getPlanId)
                .and(q -> q.ge(User::getExpiredAt, now).or().isNull(User::getExpiredAt));
        List<User> users = userMapper.selectList(wrapper);
        Map<Long, Long> countMap = new HashMap<>();
        for (User u : users) {
            Long planId = u.getPlanId();
            if (planId == null) continue;
            countMap.put(planId, countMap.getOrDefault(planId, 0L) + 1);
        }
        return countMap;
    }

    /**
     * 检查指定套餐是否还有容量限制（capacity_limit）剩余。
     */
    public boolean haveCapacity(Long planId) {
        if (planId == null) {
            return true;
        }
        Plan plan = planMapper.selectById(planId);
        if (plan == null || plan.getCapacityLimit() == null) {
            return true;
        }
        Map<Long, Long> counts = countActiveUsers();
        long used = counts.getOrDefault(planId, 0L);
        return plan.getCapacityLimit() - used > 0;
    }
}

