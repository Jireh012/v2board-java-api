package com.v2board.api.controller.admin.server;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.ServerRouteMapper;
import com.v2board.api.model.ServerRoute;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 对齐 PHP Admin\Server\RouteController
 * 路由管理：fetch / save / drop
 */
@RestController
@RequestMapping("/api/v1/admin/server/route")
public class AdminRouteController {

    private static final Set<String> VALID_ACTIONS = Set.of(
            "block", "block_ip", "block_port", "protocol", "dns", "route", "route_ip", "default_out");

    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private ServerRouteMapper serverRouteMapper;

    @GetMapping("/fetch")
    public ApiResponse<List<Map<String, Object>>> fetch() {
        List<ServerRoute> routes = serverRouteMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ServerRoute r : routes) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("remarks", r.getRemarks());
            // match 存储为 JSON 字符串，返回时转为数组
            Object matchParsed = parseJson(r.getMatch());
            map.put("match", matchParsed != null ? matchParsed : r.getMatch());
            map.put("action", r.getAction());
            map.put("action_value", r.getActionValue());
            map.put("created_at", r.getCreatedAt());
            map.put("updated_at", r.getUpdatedAt());
            result.add(map);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody Map<String, Object> body) {
        String remarks = (String) body.get("remarks");
        if (remarks == null || remarks.trim().isEmpty()) {
            throw new BusinessException(500, "备注不能为空");
        }

        String action = (String) body.get("action");
        if (action == null || !VALID_ACTIONS.contains(action)) {
            throw new BusinessException(500, "动作类型参数有误");
        }

        // match 处理
        String matchJson;
        if ("default_out".equals(action)) {
            matchJson = "[]";
        } else {
            Object matchObj = body.get("match");
            if (matchObj == null) {
                throw new BusinessException(500, "匹配值不能为空");
            }
            if (matchObj instanceof List) {
                List<?> matchList = (List<?>) matchObj;
                // 过滤空值
                List<Object> filtered = new ArrayList<>();
                for (Object item : matchList) {
                    if (item != null && !item.toString().isEmpty()) filtered.add(item);
                }
                if (filtered.isEmpty() && !"default_out".equals(action)) {
                    throw new BusinessException(500, "匹配值不能为空");
                }
                try {
                    matchJson = mapper.writeValueAsString(filtered);
                } catch (Exception e) {
                    matchJson = "[]";
                }
            } else {
                matchJson = matchObj.toString();
            }
        }

        String actionValue = body.get("action_value") != null ? body.get("action_value").toString() : null;
        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        long now = System.currentTimeMillis() / 1000;

        if (id != null) {
            ServerRoute route = serverRouteMapper.selectById(id);
            if (route == null) throw new BusinessException(500, "路由不存在");
            route.setRemarks(remarks.trim());
            route.setMatch(matchJson);
            route.setAction(action);
            route.setActionValue(actionValue);
            route.setUpdatedAt(now);
            serverRouteMapper.updateById(route);
        } else {
            ServerRoute route = new ServerRoute();
            route.setRemarks(remarks.trim());
            route.setMatch(matchJson);
            route.setAction(action);
            route.setActionValue(actionValue);
            route.setCreatedAt(now);
            route.setUpdatedAt(now);
            serverRouteMapper.insert(route);
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        ServerRoute route = serverRouteMapper.selectById(id);
        if (route == null) throw new BusinessException(500, "路由不存在");
        serverRouteMapper.deleteById(id);
        return ApiResponse.success(true);
    }

    private Object parseJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return mapper.readValue(json, new TypeReference<List<Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
