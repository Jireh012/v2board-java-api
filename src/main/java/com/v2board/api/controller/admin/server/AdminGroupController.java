package com.v2board.api.controller.admin.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.*;
import com.v2board.api.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 对齐 PHP Admin\Server\GroupController
 * 权限组管理：fetch / save / drop
 */
@RestController
@RequestMapping("/api/v1/admin/server/group")
public class AdminGroupController {

    @Autowired
    private ServerGroupMapper serverGroupMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PlanMapper planMapper;
    @Autowired
    private ServerVmessMapper serverVmessMapper;
    @Autowired
    private ServerVlessMapper serverVlessMapper;
    @Autowired
    private ServerTrojanMapper serverTrojanMapper;
    @Autowired
    private ServerShadowsocksMapper serverShadowsocksMapper;
    @Autowired
    private ServerHysteriaMapper serverHysteriaMapper;
    @Autowired
    private ServerTuicMapper serverTuicMapper;
    @Autowired
    private ServerAnytlsMapper serverAnytlsMapper;
    @Autowired
    private ServerV2nodeMapper serverV2nodeMapper;

    /**
     * 获取所有权限组，附加 user_count 和 server_count
     */
    @GetMapping("/fetch")
    public ApiResponse<List<Map<String, Object>>> fetch(
            @RequestParam(value = "group_id", required = false) Long groupId) {
        if (groupId != null) {
            ServerGroup g = serverGroupMapper.selectById(groupId);
            if (g == null) return ApiResponse.success(Collections.emptyList());
            Map<String, Object> map = groupToMap(g);
            return ApiResponse.success(Collections.singletonList(map));
        }

        List<ServerGroup> groups = serverGroupMapper.selectList(null);

        // 收集所有服务器的 group_id 以统计 server_count
        List<List<Integer>> allGroupIds = new ArrayList<>();
        collectGroupIds(allGroupIds, serverVmessMapper.selectList(null), "vmess");
        collectGroupIds(allGroupIds, serverVlessMapper.selectList(null), "vless");
        collectGroupIds(allGroupIds, serverTrojanMapper.selectList(null), "trojan");
        collectGroupIds(allGroupIds, serverShadowsocksMapper.selectList(null), "shadowsocks");
        collectGroupIds(allGroupIds, serverHysteriaMapper.selectList(null), "hysteria");
        collectGroupIds(allGroupIds, serverTuicMapper.selectList(null), "tuic");
        collectGroupIds(allGroupIds, serverAnytlsMapper.selectList(null), "anytls");
        collectGroupIds(allGroupIds, serverV2nodeMapper.selectList(null), "v2node");

        List<Map<String, Object>> result = new ArrayList<>();
        for (ServerGroup g : groups) {
            Map<String, Object> map = groupToMap(g);
            // user_count
            LambdaQueryWrapper<User> uw = new LambdaQueryWrapper<>();
            uw.eq(User::getGroupId, g.getId().intValue());
            map.put("user_count", userMapper.selectCount(uw));
            // server_count
            long serverCount = 0;
            for (List<Integer> gids : allGroupIds) {
                if (gids != null && gids.contains(g.getId().intValue())) {
                    serverCount++;
                }
            }
            map.put("server_count", serverCount);
            result.add(map);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(500, "组名不能为空");
        }

        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        long now = System.currentTimeMillis() / 1000;

        if (id != null) {
            ServerGroup group = serverGroupMapper.selectById(id);
            if (group == null) throw new BusinessException(500, "组不存在");
            group.setName(name.trim());
            group.setUpdatedAt(now);
            serverGroupMapper.updateById(group);
        } else {
            ServerGroup group = new ServerGroup();
            group.setName(name.trim());
            group.setCreatedAt(now);
            group.setUpdatedAt(now);
            serverGroupMapper.insert(group);
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        ServerGroup group = serverGroupMapper.selectById(id);
        if (group == null) throw new BusinessException(500, "组不存在");

        int gid = id.intValue();

        // 检查各类节点是否使用此组
        if (isGroupUsedByServers(gid)) {
            throw new BusinessException(500, "该组已被节点所使用，无法删除");
        }
        // 检查订阅
        LambdaQueryWrapper<Plan> pw = new LambdaQueryWrapper<>();
        pw.eq(Plan::getGroupId, gid);
        if (planMapper.selectCount(pw) > 0) {
            throw new BusinessException(500, "该组已被订阅所使用，无法删除");
        }
        // 检查用户
        LambdaQueryWrapper<User> uw = new LambdaQueryWrapper<>();
        uw.eq(User::getGroupId, gid);
        if (userMapper.selectCount(uw) > 0) {
            throw new BusinessException(500, "该组已被用户所使用，无法删除");
        }

        serverGroupMapper.deleteById(id);
        return ApiResponse.success(true);
    }

    // ---- helpers ----

    private Map<String, Object> groupToMap(ServerGroup g) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", g.getId());
        map.put("name", g.getName());
        map.put("created_at", g.getCreatedAt());
        map.put("updated_at", g.getUpdatedAt());
        return map;
    }

    @SuppressWarnings("unchecked")
    private void collectGroupIds(List<List<Integer>> target, List<?> servers, String type) {
        for (Object s : servers) {
            List<Integer> gids = null;
            try {
                java.lang.reflect.Method m = s.getClass().getMethod("getGroupId");
                Object val = m.invoke(s);
                if (val instanceof List) {
                    gids = new ArrayList<>();
                    for (Object item : (List<?>) val) {
                        if (item instanceof Number) gids.add(((Number) item).intValue());
                    }
                }
            } catch (Exception ignored) {}
            target.add(gids);
        }
    }

    private boolean isGroupUsedByServers(int gid) {
        return isGroupInList(serverVmessMapper.selectList(null), gid)
                || isGroupInList(serverVlessMapper.selectList(null), gid)
                || isGroupInList(serverTrojanMapper.selectList(null), gid)
                || isGroupInList(serverShadowsocksMapper.selectList(null), gid)
                || isGroupInList(serverHysteriaMapper.selectList(null), gid)
                || isGroupInList(serverTuicMapper.selectList(null), gid)
                || isGroupInList(serverAnytlsMapper.selectList(null), gid)
                || isGroupInList(serverV2nodeMapper.selectList(null), gid);
    }

    @SuppressWarnings("unchecked")
    private boolean isGroupInList(List<?> servers, int gid) {
        for (Object s : servers) {
            try {
                java.lang.reflect.Method m = s.getClass().getMethod("getGroupId");
                Object val = m.invoke(s);
                if (val instanceof List) {
                    for (Object item : (List<?>) val) {
                        if (item instanceof Number && ((Number) item).intValue() == gid) return true;
                    }
                }
            } catch (Exception ignored) {}
        }
        return false;
    }
}
