package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.mapper.*;
import com.v2board.api.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

/**
 * 管理端统计，对齐 PHP Admin\StatController。
 */
@RestController
@RequestMapping("/api/v1/admin/stat")
public class AdminStatController {

    @Autowired private UserMapper userMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private TicketMapper ticketMapper;
    @Autowired private CommissionLogMapper commissionLogMapper;
    @Autowired private StatMapper statMapper;
    @Autowired private StatServerMapper statServerMapper;
    @Autowired private StatUserMapper statUserMapper;
    @Autowired private ServerShadowsocksMapper serverShadowsocksMapper;
    @Autowired private ServerVmessMapper serverVmessMapper;
    @Autowired private ServerVlessMapper serverVlessMapper;
    @Autowired private ServerTrojanMapper serverTrojanMapper;
    @Autowired private ServerHysteriaMapper serverHysteriaMapper;
    @Autowired private ServerTuicMapper serverTuicMapper;
    @Autowired private ServerAnytlsMapper serverAnytlsMapper;
    @Autowired private ServerV2nodeMapper serverV2nodeMapper;

    /**
     * GET /api/v1/admin/stat/getOverride — 仪表盘概览数据
     */
    @GetMapping("/getOverride")
    public ApiResponse<Map<String, Object>> getOverride() {
        long now = System.currentTimeMillis() / 1000;
        ZoneId zone = ZoneId.systemDefault();
        long monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(zone).toEpochSecond();
        long dayStart = LocalDate.now().atStartOfDay(zone).toEpochSecond();
        long lastMonthStart = LocalDate.now().minusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toEpochSecond();

        Map<String, Object> data = new LinkedHashMap<>();
        // 在线用户（10分钟内有流量）
        data.put("online_user", userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getT, now - 600)));
        // 本月收入
        data.put("month_income", sumOrderAmount(monthStart, now));
        // 本月注册
        data.put("month_register_total", userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreatedAt, monthStart).lt(User::getCreatedAt, now)));
        // 今日注册
        data.put("day_register_total", userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreatedAt, dayStart).lt(User::getCreatedAt, now)));
        // 待处理工单
        data.put("ticket_pending_total", ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>().eq(Ticket::getStatus, 0).eq(Ticket::getReplyStatus, 0)));
        // 待确认佣金订单
        data.put("commission_pending_total", orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getCommissionStatus, 0)
                        .isNotNull(Order::getInviteUserId)
                        .notIn(Order::getStatus, 0, 2)
                        .gt(Order::getCommissionBalance, 0)));
        // 今日收入
        data.put("day_income", sumOrderAmount(dayStart, now));
        // 上月收入
        data.put("last_month_income", sumOrderAmount(lastMonthStart, monthStart));
        // 本月佣金发放
        data.put("commission_month_payout", sumCommission(monthStart, now));
        // 上月佣金发放
        data.put("commission_last_month_payout", sumCommission(lastMonthStart, monthStart));
        return ApiResponse.success(data);
    }

    /**
     * GET /api/v1/admin/stat/getOrder — 31天订单趋势（注册、收款金额/笔数、佣金）
     */
    @GetMapping("/getOrder")
    public ApiResponse<List<Map<String, Object>>> getOrder() {
        List<Stat> stats = statMapper.selectList(
                new LambdaQueryWrapper<Stat>()
                        .eq(Stat::getRecordType, "d")
                        .orderByDesc(Stat::getRecordAt)
                        .last("LIMIT 31"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Stat s : stats) {
            String date = formatDate(s.getRecordAt());
            result.add(Map.of("type", "注册人数", "date", date, "value", s.getRegisterCount() != null ? s.getRegisterCount() : 0));
            result.add(Map.of("type", "收款金额", "date", date, "value", s.getPaidTotal() != null ? s.getPaidTotal() / 100.0 : 0));
            result.add(Map.of("type", "收款笔数", "date", date, "value", s.getPaidCount() != null ? s.getPaidCount() : 0));
            result.add(Map.of("type", "佣金金额(已发放)", "date", date, "value", s.getCommissionTotal() != null ? s.getCommissionTotal() / 100.0 : 0));
            result.add(Map.of("type", "佣金笔数(已发放)", "date", date, "value", s.getCommissionCount() != null ? s.getCommissionCount() : 0));
        }
        Collections.reverse(result);
        return ApiResponse.success(result);
    }

    /**
     * GET /api/v1/admin/stat/getServerLastRank — 昨日节点流量排行
     */
    @GetMapping("/getServerLastRank")
    public ApiResponse<List<Map<String, Object>>> getServerLastRank() {
        ZoneId zone = ZoneId.systemDefault();
        long startAt = LocalDate.now().minusDays(1).atStartOfDay(zone).toEpochSecond();
        long endAt = LocalDate.now().atStartOfDay(zone).toEpochSecond();
        return ApiResponse.success(getServerRank(startAt, endAt));
    }

    /**
     * GET /api/v1/admin/stat/getServerTodayRank — 今日节点流量排行
     */
    @GetMapping("/getServerTodayRank")
    public ApiResponse<List<Map<String, Object>>> getServerTodayRank() {
        ZoneId zone = ZoneId.systemDefault();
        long startAt = LocalDate.now().atStartOfDay(zone).toEpochSecond();
        long endAt = System.currentTimeMillis() / 1000;
        return ApiResponse.success(getServerRank(startAt, endAt));
    }

    /**
     * GET /api/v1/admin/stat/getUserTodayRank — 今日用户流量排行
     */
    @GetMapping("/getUserTodayRank")
    public ApiResponse<List<Map<String, Object>>> getUserTodayRank() {
        ZoneId zone = ZoneId.systemDefault();
        long startAt = LocalDate.now().atStartOfDay(zone).toEpochSecond();
        long endAt = System.currentTimeMillis() / 1000;
        return ApiResponse.success(getUserRank(startAt, endAt));
    }

    /**
     * GET /api/v1/admin/stat/getUserLastRank — 昨日用户流量排行
     */
    @GetMapping("/getUserLastRank")
    public ApiResponse<List<Map<String, Object>>> getUserLastRank() {
        ZoneId zone = ZoneId.systemDefault();
        long startAt = LocalDate.now().minusDays(1).atStartOfDay(zone).toEpochSecond();
        long endAt = LocalDate.now().atStartOfDay(zone).toEpochSecond();
        return ApiResponse.success(getUserRank(startAt, endAt));
    }

    /**
     * GET /api/v1/admin/stat/getStatUser — 用户流量统计详情（分页）
     */
    @GetMapping("/getStatUser")
    public ApiResponse<Map<String, Object>> getStatUser(
            @RequestParam("user_id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        if (pageSize < 10) pageSize = 10;
        LambdaQueryWrapper<StatUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StatUser::getUserId, userId).orderByDesc(StatUser::getRecordAt);
        long total = statUserMapper.selectCount(wrapper);
        int offset = (current - 1) * pageSize;
        wrapper.last("LIMIT " + offset + "," + pageSize);
        List<StatUser> records = statUserMapper.selectList(wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("data", records);
        result.put("total", total);
        return ApiResponse.success(result);
    }

    // ==================== 私有方法 ====================

    private Long sumOrderAmount(long from, long to) {
        QueryWrapper<Order> w = new QueryWrapper<>();
        w.ge("created_at", from).lt("created_at", to).notIn("status", 0, 2);
        w.select("COALESCE(SUM(total_amount),0) as total_amount");
        Map<String, Object> map = orderMapper.selectMaps(w).stream().findFirst().orElse(null);
        if (map != null && map.get("total_amount") != null) {
            return ((Number) map.get("total_amount")).longValue();
        }
        return 0L;
    }

    private Long sumCommission(long from, long to) {
        QueryWrapper<CommissionLog> w = new QueryWrapper<>();
        w.ge("created_at", from).lt("created_at", to);
        w.select("COALESCE(SUM(get_amount),0) as get_amount");
        Map<String, Object> map = commissionLogMapper.selectMaps(w).stream().findFirst().orElse(null);
        if (map != null && map.get("get_amount") != null) {
            return ((Number) map.get("get_amount")).longValue();
        }
        return 0L;
    }

    private String formatDate(Long recordAt) {
        if (recordAt == null) return "";
        LocalDate d = Instant.ofEpochSecond(recordAt).atZone(ZoneId.systemDefault()).toLocalDate();
        return String.format("%02d-%02d", d.getMonthValue(), d.getDayOfMonth());
    }

    private Map<String, Map<Long, String>> loadAllServers() {
        Map<String, Map<Long, String>> servers = new HashMap<>();
        servers.put("shadowsocks", toNameMap(serverShadowsocksMapper.selectList(new LambdaQueryWrapper<ServerShadowsocks>().isNull(ServerShadowsocks::getParentId))));
        servers.put("vmess", toNameMap(serverVmessMapper.selectList(new LambdaQueryWrapper<ServerVmess>().isNull(ServerVmess::getParentId))));
        servers.put("vless", toNameMap(serverVlessMapper.selectList(new LambdaQueryWrapper<ServerVless>().isNull(ServerVless::getParentId))));
        servers.put("trojan", toNameMap(serverTrojanMapper.selectList(new LambdaQueryWrapper<ServerTrojan>().isNull(ServerTrojan::getParentId))));
        servers.put("hysteria", toNameMap(serverHysteriaMapper.selectList(new LambdaQueryWrapper<ServerHysteria>().isNull(ServerHysteria::getParentId))));
        servers.put("tuic", toNameMap(serverTuicMapper.selectList(new LambdaQueryWrapper<ServerTuic>().isNull(ServerTuic::getParentId))));
        servers.put("anytls", toNameMap(serverAnytlsMapper.selectList(new LambdaQueryWrapper<ServerAnytls>().isNull(ServerAnytls::getParentId))));
        servers.put("v2node", toNameMap(serverV2nodeMapper.selectList(new LambdaQueryWrapper<ServerV2node>().isNull(ServerV2node::getParentId))));
        return servers;
    }

    @SuppressWarnings("unchecked")
    private <T> Map<Long, String> toNameMap(List<T> list) {
        Map<Long, String> map = new HashMap<>();
        for (T item : list) {
            try {
                Long id = (Long) item.getClass().getMethod("getId").invoke(item);
                String name = (String) item.getClass().getMethod("getName").invoke(item);
                if (id != null) map.put(id, name);
            } catch (Exception ignored) {}
        }
        return map;
    }

    private List<Map<String, Object>> getServerRank(long startAt, long endAt) {
        Map<String, Map<Long, String>> allServers = loadAllServers();
        QueryWrapper<StatServer> w = new QueryWrapper<>();
        w.select("server_id", "server_type", "u", "d", "(u+d) as total")
                .ge("record_at", startAt).lt("record_at", endAt)
                .eq("record_type", "d")
                .orderByDesc("total").last("LIMIT 15");
        List<Map<String, Object>> rows = statServerMapper.selectMaps(w);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            String serverType = String.valueOf(row.get("server_type"));
            Long serverId = row.get("server_id") != null ? ((Number) row.get("server_id")).longValue() : null;
            Map<Long, String> nameMap = allServers.getOrDefault(serverType, Collections.emptyMap());
            item.put("server_name", serverId != null ? nameMap.getOrDefault(serverId, "") : "");
            Number total = (Number) row.get("total");
            item.put("total", total != null ? total.doubleValue() / 1073741824.0 : 0);
            result.add(item);
        }
        result.sort((a, b) -> Double.compare(
                ((Number) b.getOrDefault("total", 0)).doubleValue(),
                ((Number) a.getOrDefault("total", 0)).doubleValue()));
        return result;
    }

    private List<Map<String, Object>> getUserRank(long startAt, long endAt) {
        QueryWrapper<StatUser> w = new QueryWrapper<>();
        w.select("user_id", "server_rate", "u", "d", "(u+d) as total")
                .ge("record_at", startAt).lt("record_at", endAt)
                .eq("record_type", "d")
                .orderByDesc("total").last("LIMIT 30");
        List<Map<String, Object>> rows = statUserMapper.selectMaps(w);

        // 按用户聚合
        Map<Long, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long userId = row.get("user_id") != null ? ((Number) row.get("user_id")).longValue() : 0L;
            double rate = row.get("server_rate") != null ? ((Number) row.get("server_rate")).doubleValue() : 1.0;
            double total = row.get("total") != null ? ((Number) row.get("total")).doubleValue() : 0;
            double weighted = total * rate / 1073741824.0;

            if (merged.containsKey(userId)) {
                Map<String, Object> existing = merged.get(userId);
                existing.put("total", ((Number) existing.get("total")).doubleValue() + weighted);
            } else {
                User user = userMapper.selectById(userId);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("user_id", userId);
                item.put("u", row.get("u"));
                item.put("d", row.get("d"));
                item.put("email", user != null ? user.getEmail() : "null");
                item.put("total", weighted);
                merged.put(userId, item);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(merged.values());
        result.sort((a, b) -> Double.compare(
                ((Number) b.getOrDefault("total", 0)).doubleValue(),
                ((Number) a.getOrDefault("total", 0)).doubleValue()));
        return result.size() > 15 ? result.subList(0, 15) : result;
    }
}
