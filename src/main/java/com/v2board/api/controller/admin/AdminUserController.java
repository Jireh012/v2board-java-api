package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.*;
import com.v2board.api.model.*;
import com.v2board.api.service.ConfigService;
import com.v2board.api.util.Helper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.util.*;

/**
 * 管理端用户管理，对齐 PHP Admin\UserController。
 */
@RestController
@RequestMapping("/api/v1/admin/user")
public class AdminUserController {

    @Autowired private UserMapper userMapper;
    @Autowired private PlanMapper planMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private InviteCodeMapper inviteCodeMapper;
    @Autowired private TicketMapper ticketMapper;
    @Autowired private TicketMessageMapper ticketMessageMapper;
    @Autowired private ConfigService configService;

    private static final Set<String> ALLOWED_FILTER_KEYS = Set.of(
            "id", "email", "plan_id", "transfer_enable", "d", "invite_user_id",
            "invite_by_email", "banned", "uuid", "token"
    );
    private static final Set<String> ALLOWED_CONDITIONS = Set.of(
            ">", "<", "=", ">=", "<=", "模糊", "!="
    );

    // ==================== fetch ====================
    @GetMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch(
            HttpServletRequest request,
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "sort", defaultValue = "created_at") String sort,
            @RequestParam(value = "sort_type", defaultValue = "DESC") String sortType) {
        if (pageSize < 10) pageSize = 10;
        if (!sortType.equals("ASC") && !sortType.equals("DESC")) sortType = "DESC";

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.orderBy(true, "ASC".equals(sortType), sort);
        applyFilters(request, wrapper);

        long total = userMapper.selectCount(wrapper);
        int offset = (current - 1) * pageSize;
        wrapper.last("LIMIT " + offset + "," + pageSize);
        List<User> users = userMapper.selectList(wrapper);

        List<Plan> plans = planMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, String> planNameMap = new HashMap<>();
        for (Plan plan : plans) {
            planNameMap.put(plan.getId(), plan.getName());
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> map = userToMap(u);
            if (u.getPlanId() != null) {
                map.put("plan_name", planNameMap.getOrDefault(u.getPlanId(), null));
            }
            long totalUsed = (u.getU() != null ? u.getU() : 0) + (u.getD() != null ? u.getD() : 0);
            map.put("total_used", totalUsed);
            data.add(map);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        result.put("total", total);
        return ApiResponse.success(result);
    }

    // ==================== getUserInfoById ====================
    @GetMapping("/getUserInfoById")
    public ApiResponse<Map<String, Object>> getUserInfoById(@RequestParam("id") Long id) {
        if (id == null) throw new BusinessException(500, "参数错误");
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(500, "用户不存在");
        Map<String, Object> data = userToMap(user);
        if (user.getInviteUserId() != null) {
            User inviter = userMapper.selectById(user.getInviteUserId());
            if (inviter != null) {
                data.put("invite_user", userToMap(inviter));
            }
        }
        return ApiResponse.success(data);
    }

    // ==================== update ====================
    @PostMapping("/update")
    public ApiResponse<Boolean> update(@RequestBody Map<String, Object> params) {
        Object idObj = params.get("id");
        if (idObj == null) throw new BusinessException(500, "参数错误");
        Long id = Long.valueOf(String.valueOf(idObj));
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(500, "用户不存在");

        // 邮箱
        if (params.containsKey("email")) {
            String email = String.valueOf(params.get("email"));
            if (!email.equals(user.getEmail())) {
                User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
                if (exist != null) throw new BusinessException(500, "邮箱已被使用");
            }
            user.setEmail(email);
        }
        // 密码
        if (params.containsKey("password") && params.get("password") != null) {
            String pwd = String.valueOf(params.get("password"));
            if (!pwd.isEmpty()) {
                user.setPassword(new BCryptPasswordEncoder().encode(pwd));
            }
        }
        // 套餐
        if (params.containsKey("plan_id")) {
            Object planIdObj = params.get("plan_id");
            if (planIdObj != null && !String.valueOf(planIdObj).isEmpty()) {
                Long planId = Long.valueOf(String.valueOf(planIdObj));
                Plan plan = planMapper.selectById(planId);
                if (plan == null) throw new BusinessException(500, "订阅计划不存在");
                user.setPlanId(planId);
                user.setGroupId(plan.getGroupId());
            } else {
                user.setPlanId(null);
                user.setGroupId(null);
            }
        }
        // 邀请人
        if (params.containsKey("invite_user_email")) {
            String inviteEmail = String.valueOf(params.get("invite_user_email"));
            if (inviteEmail != null && !inviteEmail.isEmpty()) {
                User inviter = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, inviteEmail));
                if (inviter != null) {
                    user.setInviteUserId(inviter.getId());
                }
            } else {
                user.setInviteUserId(null);
            }
        }
        // 简单字段映射
        setIfPresent(params, "balance", v -> user.setBalance(toLong(v)));
        setIfPresent(params, "commission_balance", v -> user.setCommissionBalance(toLong(v)));
        setIfPresent(params, "commission_type", v -> user.setCommissionType(toInt(v)));
        setIfPresent(params, "commission_rate", v -> user.setCommissionRate(toInt(v)));
        setIfPresent(params, "discount", v -> user.setDiscount(toInt(v)));
        setIfPresent(params, "transfer_enable", v -> user.setTransferEnable(toLong(v)));
        setIfPresent(params, "device_limit", v -> user.setDeviceLimit(toInt(v)));
        setIfPresent(params, "speed_limit", v -> user.setSpeedLimit(toInt(v)));
        setIfPresent(params, "expired_at", v -> user.setExpiredAt(toLong(v)));
        setIfPresent(params, "banned", v -> user.setBanned(toInt(v)));
        setIfPresent(params, "is_admin", v -> user.setIsAdmin(toInt(v)));
        setIfPresent(params, "u", v -> user.setU(toLong(v)));
        setIfPresent(params, "d", v -> user.setD(toLong(v)));

        user.setUpdatedAt(System.currentTimeMillis() / 1000);
        if (userMapper.updateById(user) <= 0) {
            throw new BusinessException(500, "保存失败");
        }
        return ApiResponse.success(true);
    }

    // ==================== generate ====================
    @PostMapping("/generate")
    public ApiResponse<Boolean> generate(@RequestBody Map<String, Object> params) {
        String emailPrefix = getStr(params, "email_prefix");
        String emailSuffix = getStr(params, "email_suffix");
        if (emailPrefix == null || emailPrefix.isEmpty()) {
            throw new BusinessException(500, "邮箱前缀不能为空");
        }
        String email = emailPrefix + "@" + (emailSuffix != null ? emailSuffix : "gmail.com");
        if (userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) != null) {
            throw new BusinessException(500, "邮箱已存在于系统中");
        }

        Plan plan = null;
        if (params.containsKey("plan_id") && params.get("plan_id") != null) {
            Long planId = toLong(params.get("plan_id"));
            if (planId != null && planId > 0) {
                plan = planMapper.selectById(planId);
                if (plan == null) throw new BusinessException(500, "订阅计划不存在");
            }
        }

        User user = new User();
        user.setEmail(email);
        user.setPlanId(plan != null ? plan.getId() : null);
        user.setGroupId(plan != null ? plan.getGroupId() : null);
        user.setTransferEnable(plan != null && plan.getTransferEnable() != null ? plan.getTransferEnable() * 1073741824L : 0L);
        user.setDeviceLimit(plan != null ? plan.getDeviceLimit() : null);
        if (params.containsKey("expired_at") && params.get("expired_at") != null) {
            user.setExpiredAt(toLong(params.get("expired_at")));
        }
        user.setUuid(java.util.UUID.randomUUID().toString());
        user.setToken(java.util.UUID.randomUUID().toString().replace("-", ""));

        String password = getStr(params, "password");
        if (password == null || password.isEmpty()) password = email;
        user.setPassword(new BCryptPasswordEncoder().encode(password));

        long now = System.currentTimeMillis() / 1000;
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        if (userMapper.insert(user) <= 0) {
            throw new BusinessException(500, "生成失败");
        }
        return ApiResponse.success(true);
    }

    // ==================== ban ====================
    @PostMapping("/ban")
    public ApiResponse<Boolean> ban(HttpServletRequest request) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        applyFilters(request, wrapper);
        List<User> users = userMapper.selectList(wrapper);
        for (User u : users) {
            u.setBanned(1);
            u.setUpdatedAt(System.currentTimeMillis() / 1000);
            userMapper.updateById(u);
        }
        return ApiResponse.success(true);
    }

    // ==================== delUser ====================
    @PostMapping("/delUser")
    public ApiResponse<Boolean> delUser(@RequestParam("id") Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(500, "用户不存在");

        orderMapper.delete(new LambdaQueryWrapper<Order>().eq(Order::getUserId, id));
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getInviteUserId, id)
                .set(User::getInviteUserId, null));
        inviteCodeMapper.delete(new LambdaQueryWrapper<InviteCode>().eq(InviteCode::getUserId, id));
        List<Ticket> tickets = ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>().eq(Ticket::getUserId, id));
        for (Ticket t : tickets) {
            ticketMessageMapper.delete(new LambdaQueryWrapper<TicketMessage>().eq(TicketMessage::getTicketId, t.getId()));
        }
        ticketMapper.delete(new LambdaQueryWrapper<Ticket>().eq(Ticket::getUserId, id));
        userMapper.deleteById(id);
        return ApiResponse.success(true);
    }

    // ==================== resetSecret ====================
    @PostMapping("/resetSecret")
    public ApiResponse<Boolean> resetSecret(@RequestParam("id") Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(500, "用户不存在");
        user.setToken(java.util.UUID.randomUUID().toString().replace("-", ""));
        user.setUuid(java.util.UUID.randomUUID().toString());
        userMapper.updateById(user);
        return ApiResponse.success(true);
    }

    // ==================== dumpCSV ====================
    @PostMapping("/dumpCSV")
    public void dumpCSV(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=users.csv");
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.orderBy(true, true, "id");
        applyFilters(request, wrapper);
        List<User> users = userMapper.selectList(wrapper);
        List<Plan> plans = planMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, String> planNameMap = new HashMap<>();
        for (Plan p : plans) planNameMap.put(p.getId(), p.getName());

        PrintWriter writer = response.getWriter();
        writer.write("\uFEFF"); // BOM
        writer.write("邮箱,余额,推广佣金,总流量,设备数限制,剩余流量,套餐到期时间,订阅计划\r\n");
        for (User u : users) {
            String expireDate = u.getExpiredAt() == null ? "长期有效" : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(u.getExpiredAt() * 1000));
            double balance = (u.getBalance() != null ? u.getBalance() : 0) / 100.0;
            double commBalance = (u.getCommissionBalance() != null ? u.getCommissionBalance() : 0) / 100.0;
            double transferGB = u.getTransferEnable() != null ? u.getTransferEnable() / 1073741824.0 : 0;
            Integer deviceLimit = u.getDeviceLimit();
            long used = (u.getU() != null ? u.getU() : 0) + (u.getD() != null ? u.getD() : 0);
            double remaining = u.getTransferEnable() != null ? (u.getTransferEnable() - used) / 1073741824.0 : 0;
            String planName = u.getPlanId() != null ? planNameMap.getOrDefault(u.getPlanId(), "无订阅") : "无订阅";
            writer.write(String.format("%s,%.2f,%.2f,%.2f,%s,%.2f,%s,%s\r\n",
                    u.getEmail(), balance, commBalance, transferGB,
                    deviceLimit != null ? String.valueOf(deviceLimit) : "",
                    remaining, expireDate, planName));
        }
        writer.flush();
    }

    // ==================== 过滤器 ====================
    private void applyFilters(HttpServletRequest request, QueryWrapper<User> wrapper) {
        for (int i = 0; i < 20; i++) {
            String key = request.getParameter("filter[" + i + "][key]");
            String condition = request.getParameter("filter[" + i + "][condition]");
            String value = request.getParameter("filter[" + i + "][value]");
            if (key == null || condition == null || value == null) break;
            if (!ALLOWED_FILTER_KEYS.contains(key)) continue;
            if (!ALLOWED_CONDITIONS.contains(condition)) continue;

            if ("d".equals(key) || "transfer_enable".equals(key)) {
                try {
                    long bytes = (long) (Double.parseDouble(value) * 1073741824L);
                    value = String.valueOf(bytes);
                } catch (NumberFormatException ignored) {}
            }
            if ("invite_by_email".equals(key)) {
                User inviter = userMapper.selectOne(new QueryWrapper<User>().apply(
                        "email " + mapCondition(condition) + " {0}", mapValue(condition, value)));
                wrapper.eq("invite_user_id", inviter != null ? inviter.getId() : 0);
                continue;
            }
            if ("plan_id".equals(key) && "null".equals(value)) {
                wrapper.isNull("plan_id");
                continue;
            }
            if ("模糊".equals(condition)) {
                wrapper.like(key, value);
            } else {
                wrapper.apply(key + " " + condition + " {0}", value);
            }
        }
    }

    private String mapCondition(String condition) {
        return "模糊".equals(condition) ? "LIKE" : condition;
    }

    private String mapValue(String condition, String value) {
        return "模糊".equals(condition) ? "%" + value + "%" : value;
    }

    // ==================== 工具方法 ====================
    private Map<String, Object> userToMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("uuid", u.getUuid());
        m.put("token", u.getToken());
        m.put("group_id", u.getGroupId());
        m.put("plan_id", u.getPlanId());
        m.put("expired_at", u.getExpiredAt());
        m.put("u", u.getU());
        m.put("d", u.getD());
        m.put("transfer_enable", u.getTransferEnable());
        m.put("device_limit", u.getDeviceLimit());
        m.put("banned", u.getBanned());
        m.put("is_admin", u.getIsAdmin());
        m.put("balance", u.getBalance());
        m.put("commission_balance", u.getCommissionBalance());
        m.put("commission_type", u.getCommissionType());
        m.put("commission_rate", u.getCommissionRate());
        m.put("discount", u.getDiscount());
        m.put("speed_limit", u.getSpeedLimit());
        m.put("invite_user_id", u.getInviteUserId());
        m.put("telegram_id", u.getTelegramId());
        m.put("t", u.getT());
        m.put("created_at", u.getCreatedAt());
        m.put("updated_at", u.getUpdatedAt());
        return m;
    }

    private void setIfPresent(Map<String, Object> params, String key, java.util.function.Consumer<Object> setter) {
        if (params.containsKey(key) && params.get(key) != null) {
            setter.accept(params.get(key));
        }
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        return Long.valueOf(String.valueOf(v));
    }

    private Integer toInt(Object v) {
        if (v == null) return null;
        return Integer.valueOf(String.valueOf(v));
    }

    private String getStr(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v != null ? String.valueOf(v) : null;
    }
}
