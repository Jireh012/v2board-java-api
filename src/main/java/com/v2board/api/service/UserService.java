package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.mapper.StatServerMapper;
import com.v2board.api.mapper.StatUserMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Plan;
import com.v2board.api.model.StatServer;
import com.v2board.api.model.StatUser;
import com.v2board.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private StatUserMapper statUserMapper;

    @Autowired
    private StatServerMapper statServerMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
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
     * 根据邮箱查找用户
     */
    public User findByEmail(String email) {
        if (email == null || email.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        return userMapper.selectOne(wrapper);
    }

    /**
     * 获取可用用户（按节点分组限制）。
     * PHP: ServerService::getAvailableUsers($groupId)
     */
    public List<User> getAvailableUsers(List<Integer> groupIds) {
        long now = System.currentTimeMillis() / 1000;
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (groupIds != null && !groupIds.isEmpty()) {
            wrapper.in(User::getGroupId, groupIds);
        }
        wrapper.apply("u + d < transfer_enable");
        wrapper.and(q -> q.ge(User::getExpiredAt, now).or().isNull(User::getExpiredAt));
        wrapper.eq(User::getBanned, 0);
        return userMapper.selectList(wrapper);
    }

    /**
     * 获取有设备限制且当前可用的用户列表。
     * PHP: UserService::getDeviceLimitedUsers()
     */
    public List<User> getDeviceLimitedUsers() {
        long now = System.currentTimeMillis() / 1000;
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("u + d < transfer_enable");
        wrapper.and(q -> q.ge(User::getExpiredAt, now).or().isNull(User::getExpiredAt));
        wrapper.eq(User::getBanned, 0);
        wrapper.gt(User::getDeviceLimit, 0);
        return userMapper.selectList(wrapper);
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
     * 处理节点上报的流量数据（仅用户维度）。
     * 兼容旧调用。
     */
    public void trafficFetch(double rate, Map<String, List<Long>> data) {
        trafficFetch(null, null, rate, data);
    }

    /**
     * 处理节点上报的流量数据。
     * 对齐 PHP UserService::trafficFetch + StatUserJob + StatServerJob 的核心逻辑，简化为同步执行。
     *
     * @param serverId   节点 ID
     * @param serverType 节点类型，例如 vmess/shadowsocks 等
     * @param rate       节点流量倍率
     * @param data       key 为用户 ID，value 为 [u, d] 数组（字节）
     */
    public void trafficFetch(Long serverId, String serverType, double rate, Map<String, List<Long>> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        long today = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toEpochSecond(ZoneOffset.UTC);

        Map<Long, long[]> trafficMap = new HashMap<>();
        long totalU = 0L;
        long totalD = 0L;
        for (Map.Entry<String, List<Long>> entry : data.entrySet()) {
            try {
                Long userId = Long.valueOf(entry.getKey());
                List<Long> values = entry.getValue();
                if (values == null || values.size() < 2) {
                    continue;
                }
                long u = values.get(0) != null ? values.get(0) : 0L;
                long d = values.get(1) != null ? values.get(1) : 0L;
                trafficMap.put(userId, new long[]{u, d});

                long incU = (long) (u * rate);
                long incD = (long) (d * rate);
                redisTemplate.opsForHash().increment("v2board_upload_traffic", String.valueOf(userId), incU);
                redisTemplate.opsForHash().increment("v2board_download_traffic", String.valueOf(userId), incD);
                totalU += incU;
                totalD += incD;
            } catch (NumberFormatException ignore) {
            }
        }

        if (trafficMap.isEmpty()) {
            return;
        }

        for (Map.Entry<Long, long[]> e : trafficMap.entrySet()) {
            Long userId = e.getKey();
            long[] td = e.getValue();
            long incU = (long) (td[0] * rate);
            long incD = (long) (td[1] * rate);

            User user = userMapper.selectById(userId);
            if (user != null) {
                long currentU = user.getU() != null ? user.getU() : 0L;
                long currentD = user.getD() != null ? user.getD() : 0L;
                user.setU(currentU + incU);
                user.setD(currentD + incD);
                userMapper.updateById(user);
            }

            LambdaQueryWrapper<StatUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(StatUser::getUserId, userId)
                    .eq(StatUser::getServerRate, rate)
                    .eq(StatUser::getRecordAt, today);
            StatUser stat = statUserMapper.selectOne(wrapper);
            if (stat == null) {
                stat = new StatUser();
                stat.setUserId(userId);
                stat.setServerRate(rate);
                stat.setRecordAt(today);
                stat.setU(incU);
                stat.setD(incD);
                long nowTs = System.currentTimeMillis() / 1000;
                stat.setCreatedAt(nowTs);
                stat.setUpdatedAt(nowTs);
                statUserMapper.insert(stat);
            } else {
                stat.setU(stat.getU() + incU);
                stat.setD(stat.getD() + incD);
                stat.setUpdatedAt(System.currentTimeMillis() / 1000);
                statUserMapper.updateById(stat);
            }
        }

        // 统计节点级流量（StatServer），对齐 PHP StatServerJob
        if (serverId != null && serverType != null && (totalU > 0 || totalD > 0)) {
            LambdaQueryWrapper<StatServer> sw = new LambdaQueryWrapper<>();
            sw.eq(StatServer::getServerId, serverId)
              .eq(StatServer::getServerType, serverType)
              .eq(StatServer::getRecordAt, today);
            StatServer statServer = statServerMapper.selectOne(sw);
            long nowTs = System.currentTimeMillis() / 1000;
            if (statServer == null) {
                statServer = new StatServer();
                statServer.setServerId(serverId);
                statServer.setServerType(serverType);
                statServer.setU(totalU);
                statServer.setD(totalD);
                statServer.setRecordType("d");
                statServer.setRecordAt(today);
                statServer.setCreatedAt(nowTs);
                statServer.setUpdatedAt(nowTs);
                statServerMapper.insert(statServer);
            } else {
                statServer.setU((statServer.getU() != null ? statServer.getU() : 0L) + totalU);
                statServer.setD((statServer.getD() != null ? statServer.getD() : 0L) + totalD);
                statServer.setUpdatedAt(nowTs);
                statServerMapper.updateById(statServer);
            }
        }
    }
    
    /**
     * 校验密码
     * 兼容：
     * - PHP 端已有的 bcrypt 哈希（v2board 默认）
     * - 纯明文存储（测试环境或早期数据）
     */
    public boolean verifyPassword(User user, String rawPassword) {
        if (user == null || rawPassword == null) {
            return false;
        }
        String stored = user.getPassword();
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        
        // bcrypt 哈希（PHP: password_hash 默认生成以 $2a/$2b/$2y 开头的字符串）
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            try {
                return passwordEncoder.matches(rawPassword, stored);
            } catch (Exception e) {
                logger.warn("BCrypt password verify failed", e);
                return false;
            }
        }
        
        // 退回到明文对比
        return rawPassword.equals(stored);
    }
    
    /**
     * 更新密码
     */
    public void updatePassword(User user, String newPassword) {
        if (user == null || newPassword == null) {
            return;
        }
        user.setPassword(newPassword);
        userMapper.updateById(user);
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

