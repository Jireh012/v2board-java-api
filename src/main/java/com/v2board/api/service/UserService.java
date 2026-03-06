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
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Calendar;
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
        if (user.getBanned() != null && user.getBanned() == 1) {
            return false;
        }
        if (user.getTransferEnable() == null || user.getTransferEnable() <= 0) {
            return false;
        }
        long currentTime = System.currentTimeMillis() / 1000;
        if (user.getExpiredAt() != null && user.getExpiredAt() <= currentTime) {
            return false;
        }
        return true;
    }

    /**
     * 处理节点上报的流量数据 — 第一阶段：仅写入 Redis hash。
     * 对齐 PHP TrafficFetchJob：将流量乘以倍率后写入 Redis，由 TrafficSchedule 每分钟批量刷入数据库。
     *
     * @param rate 节点流量倍率
     * @param data key 为用户 ID，value 为 [u, d] 数组（字节）
     */
    public void trafficFetch(double rate, Map<String, List<Long>> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<Long>> entry : data.entrySet()) {
            try {
                String userId = entry.getKey();
                List<Long> values = entry.getValue();
                if (values == null || values.size() < 2) {
                    continue;
                }
                long u = values.get(0) != null ? values.get(0) : 0L;
                long d = values.get(1) != null ? values.get(1) : 0L;

                long incU = (long) (u * rate);
                long incD = (long) (d * rate);

                redisTemplate.opsForHash().increment("v2board_upload_traffic", userId, incU);
                redisTemplate.opsForHash().increment("v2board_download_traffic", userId, incD);
            } catch (NumberFormatException ignore) {
            }
        }
    }

    /**
     * 异步记录用户流量统计 — 对齐 PHP StatUserJob
     * 含死锁重试逻辑（最多 3 次，指数退避）
     */
    @Async("trafficExecutor")
    @Transactional
    public void recordStatUserAsync(Map<String, List<Long>> data, double rate) {
        if (data == null || data.isEmpty()) {
            return;
        }

        long today = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long nowTs = System.currentTimeMillis() / 1000;

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                for (Map.Entry<String, List<Long>> entry : data.entrySet()) {
                    try {
                        Long userId = Long.valueOf(entry.getKey());
                        List<Long> values = entry.getValue();
                        if (values == null || values.size() < 2) continue;

                        long incU = (long) ((values.get(0) != null ? values.get(0) : 0L) * rate);
                        long incD = (long) ((values.get(1) != null ? values.get(1) : 0L) * rate);

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
                            stat.setCreatedAt(nowTs);
                            stat.setUpdatedAt(nowTs);
                            statUserMapper.insert(stat);
                        } else {
                            stat.setU(stat.getU() + incU);
                            stat.setD(stat.getD() + incD);
                            stat.setUpdatedAt(nowTs);
                            statUserMapper.updateById(stat);
                        }
                    } catch (NumberFormatException ignore) {
                    }
                }
                return; // 成功则退出重试循环
            } catch (Exception e) {
                if (attempt < 2 && isDeadlockException(e)) {
                    logger.warn("StatUser deadlock detected, retry attempt {}", attempt + 1);
                    try {
                        Thread.sleep((long) Math.pow(2, attempt) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    logger.error("recordStatUserAsync failed", e);
                    throw e;
                }
            }
        }
    }

    /**
     * 异步记录服务器流量统计 — 对齐 PHP StatServerJob
     */
    @Async("trafficExecutor")
    @Transactional
    public void recordStatServerAsync(Map<String, List<Long>> data, Long serverId, String serverType, double rate) {
        if (data == null || data.isEmpty() || serverId == null || serverType == null) {
            return;
        }

        long totalU = 0L;
        long totalD = 0L;
        for (Map.Entry<String, List<Long>> entry : data.entrySet()) {
            List<Long> values = entry.getValue();
            if (values == null || values.size() < 2) continue;
            totalU += (long) ((values.get(0) != null ? values.get(0) : 0L) * rate);
            totalD += (long) ((values.get(1) != null ? values.get(1) : 0L) * rate);
        }

        if (totalU == 0 && totalD == 0) {
            return;
        }

        long today = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long nowTs = System.currentTimeMillis() / 1000;

        LambdaQueryWrapper<StatServer> sw = new LambdaQueryWrapper<>();
        sw.eq(StatServer::getServerId, serverId)
          .eq(StatServer::getServerType, serverType)
          .eq(StatServer::getRecordAt, today);
        StatServer statServer = statServerMapper.selectOne(sw);

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

    /**
     * 增加用户余额
     */
    @Transactional
    public boolean addBalance(Long userId, Long amount) {
        if (userId == null || amount == null || amount <= 0) {
            return false;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        user.setBalance((user.getBalance() != null ? user.getBalance() : 0L) + amount);
        user.setUpdatedAt(System.currentTimeMillis() / 1000);
        return userMapper.updateById(user) > 0;
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
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            try {
                return passwordEncoder.matches(rawPassword, stored);
            } catch (Exception e) {
                logger.warn("BCrypt password verify failed", e);
                return false;
            }
        }
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
        Plan plan = planMapper.selectById(user.getPlanId());
        if (plan == null) {
            return null;
        }
        long currentTime = System.currentTimeMillis() / 1000;
        if (user.getExpiredAt() == null || user.getExpiredAt() <= currentTime) {
            return null;
        }
        Integer resetMethod = plan.getResetTrafficMethod();
        if (resetMethod != null && resetMethod == 2) {
            return null;
        }
        if (resetMethod == null) {
            resetMethod = defaultResetTrafficMethod;
        }
        switch (resetMethod) {
            case 0:
                return calcResetDayByMonthFirstDay();
            case 1:
                return calcResetDayByExpireDay(user.getExpiredAt());
            case 2:
                return null;
            case 3:
                return calcResetDayByYearFirstDay();
            case 4:
                return calcResetDayByYearExpiredAt(user.getExpiredAt());
            default:
                return null;
        }
    }

    private Integer calcResetDayByMonthFirstDay() {
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.DAY_OF_MONTH);
        int lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        return lastDay - today;
    }

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

    private Integer calcResetDayByYearFirstDay() {
        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        cal.set(currentYear + 1, Calendar.JANUARY, 1, 0, 0, 0);
        long nextYear = cal.getTimeInMillis() / 1000;
        long currentTime = System.currentTimeMillis() / 1000;
        return (int) ((nextYear - currentTime) / 86400);
    }

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

    private boolean isDeadlockException(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && msg.toLowerCase().contains("deadlock")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
