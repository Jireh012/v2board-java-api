package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Calendar;

@Service
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private PlanMapper planMapper;
    
    @Value("${v2board.reset-traffic-method:0}")
    private Integer defaultResetTrafficMethod;
    
    /**
     * 根据 ID 查找用户
     */
    public User findById(Long id) {
        if (id == null) {
            return null;
        }
        return userMapper.selectById(id);
    }
    
    /**
     * 根据 token 查找用户
     */
    public User findByToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getToken, token);
        return userMapper.selectOne(wrapper);
    }
    
    /**
     * 检查用户是否可用
     * 用户必须：未封禁、有流量配额、未过期
     */
    public boolean isAvailable(User user) {
        if (user == null) {
            return false;
        }
        
        // 检查是否被封禁
        if (user.getBanned() != null && user.getBanned() == 1) {
            return false;
        }
        
        // 检查是否有流量配额
        if (user.getTransferEnable() == null || user.getTransferEnable() <= 0) {
            return false;
        }
        
        // 检查是否过期
        long currentTime = System.currentTimeMillis() / 1000;
        if (user.getExpiredAt() != null && user.getExpiredAt() <= currentTime) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取流量重置剩余天数
     * PHP: getResetDay(User $user)
     */
    public Integer getResetDay(User user) {
        if (user == null || user.getPlanId() == null) {
            return null;
        }
        
        // 获取计划信息
        Plan plan = planMapper.selectById(user.getPlanId());
        if (plan == null) {
            return null;
        }
        
        // 检查是否过期
        long currentTime = System.currentTimeMillis() / 1000;
        if (user.getExpiredAt() == null || user.getExpiredAt() <= currentTime) {
            return null;
        }
        
        // 如果重置方式为 2（不重置），返回 null
        Integer resetMethod = plan.getResetTrafficMethod();
        if (resetMethod != null && resetMethod == 2) {
            return null;
        }
        
        // 如果计划的重置方式为 null，使用默认配置
        if (resetMethod == null) {
            resetMethod = defaultResetTrafficMethod;
        }
        
        // 根据重置方式计算剩余天数
        switch (resetMethod) {
            case 0:  // 每月第一天
                return calcResetDayByMonthFirstDay();
            case 1:  // 到期日
                return calcResetDayByExpireDay(user.getExpiredAt());
            case 2:  // 不重置
                return null;
            case 3:  // 每年第一天
                return calcResetDayByYearFirstDay();
            case 4:  // 每年到期日
                return calcResetDayByYearExpiredAt(user.getExpiredAt());
            default:
                return null;
        }
    }
    
    /**
     * 计算到每月第一天的剩余天数
     * PHP: calcResetDayByMonthFirstDay()
     */
    private Integer calcResetDayByMonthFirstDay() {
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.DAY_OF_MONTH);
        int lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        return lastDay - today;
    }
    
    /**
     * 计算到到期日的剩余天数
     * PHP: calcResetDayByExpireDay(int $expiredAt)
     */
    private Integer calcResetDayByExpireDay(Long expiredAt) {
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.DAY_OF_MONTH);
        int lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        
        cal.setTimeInMillis(expiredAt * 1000);
        int expireDay = cal.get(Calendar.DAY_OF_MONTH);
        
        if (expireDay >= today && expireDay >= lastDay) {
            return lastDay - today;
        }
        if (expireDay >= today) {
            return expireDay - today;
        }
        
        return lastDay - today + expireDay;
    }
    
    /**
     * 计算到每年第一天的剩余天数
     * PHP: calcResetDayByYearFirstDay()
     */
    private Integer calcResetDayByYearFirstDay() {
        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        cal.set(currentYear + 1, Calendar.JANUARY, 1, 0, 0, 0);
        long nextYear = cal.getTimeInMillis() / 1000;
        long currentTime = System.currentTimeMillis() / 1000;
        return (int) ((nextYear - currentTime) / 86400);
    }
    
    /**
     * 计算到每年到期日的剩余天数
     * PHP: calcResetDayByYearExpiredAt(int $expiredAt)
     */
    private Integer calcResetDayByYearExpiredAt(Long expiredAt) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(expiredAt * 1000);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        cal.set(currentYear, month, day, 0, 0, 0);
        long nowYear = cal.getTimeInMillis() / 1000;
        
        cal.set(currentYear + 1, month, day, 0, 0, 0);
        long nextYear = cal.getTimeInMillis() / 1000;
        
        long currentTime = System.currentTimeMillis() / 1000;
        if (nowYear > currentTime) {
            return (int) ((nowYear - currentTime) / 86400);
        }
        return (int) ((nextYear - currentTime) / 86400);
    }
}

