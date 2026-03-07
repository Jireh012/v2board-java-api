package com.v2board.api.controller.admin.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.*;
import com.v2board.api.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 对齐 PHP Admin\Server\ManageController
 * 节点总管理：getNodes(所有类型节点列表) / sort(全局排序)
 */
@RestController
@RequestMapping("/api/v1/admin/server/manage")
public class AdminManageController {

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
     * 获取所有节点列表（合并所有协议），按 sort 排序
     */
    @GetMapping("/getNodes")
    public ApiResponse<List<Map<String, Object>>> getNodes() {
        List<Map<String, Object>> all = new ArrayList<>();

        addServersWithType(all, serverShadowsocksMapper.selectList(
                new LambdaQueryWrapper<ServerShadowsocks>().orderByAsc(ServerShadowsocks::getSort)), "shadowsocks");
        addServersWithType(all, serverVmessMapper.selectList(
                new LambdaQueryWrapper<ServerVmess>().orderByAsc(ServerVmess::getSort)), "vmess");
        addServersWithType(all, serverTrojanMapper.selectList(
                new LambdaQueryWrapper<ServerTrojan>().orderByAsc(ServerTrojan::getSort)), "trojan");
        addServersWithType(all, serverTuicMapper.selectList(
                new LambdaQueryWrapper<ServerTuic>().orderByAsc(ServerTuic::getSort)), "tuic");
        addServersWithType(all, serverHysteriaMapper.selectList(
                new LambdaQueryWrapper<ServerHysteria>().orderByAsc(ServerHysteria::getSort)), "hysteria");
        addServersWithType(all, serverVlessMapper.selectList(
                new LambdaQueryWrapper<ServerVless>().orderByAsc(ServerVless::getSort)), "vless");
        addServersWithType(all, serverAnytlsMapper.selectList(
                new LambdaQueryWrapper<ServerAnytls>().orderByAsc(ServerAnytls::getSort)), "anytls");
        addServersWithType(all, serverV2nodeMapper.selectList(
                new LambdaQueryWrapper<ServerV2node>().orderByAsc(ServerV2node::getSort)), "v2node");

        // 按 sort 排序
        all.sort(Comparator.comparingInt(a -> {
            Object s = a.get("sort");
            return s instanceof Number ? ((Number) s).intValue() : 0;
        }));

        return ApiResponse.success(all);
    }

    /**
     * 全局节点排序
     * Body: { "vmess": { "1": 0, "2": 1 }, "vless": { "3": 0 }, ... }
     */
    @PostMapping("/sort")
    @SuppressWarnings("unchecked")
    public ApiResponse<Boolean> sort(@RequestBody Map<String, Object> body) {
        Map<String, BaseMapper<?>> mapperMap = new LinkedHashMap<>();
        mapperMap.put("shadowsocks", serverShadowsocksMapper);
        mapperMap.put("vmess", serverVmessMapper);
        mapperMap.put("vless", serverVlessMapper);
        mapperMap.put("trojan", serverTrojanMapper);
        mapperMap.put("tuic", serverTuicMapper);
        mapperMap.put("hysteria", serverHysteriaMapper);
        mapperMap.put("anytls", serverAnytlsMapper);
        mapperMap.put("v2node", serverV2nodeMapper);

        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String type = entry.getKey();
            BaseMapper<?> mapper = mapperMap.get(type);
            if (mapper == null) continue;

            Map<String, Object> sortMap;
            if (entry.getValue() instanceof Map) {
                sortMap = (Map<String, Object>) entry.getValue();
            } else continue;

            for (Map.Entry<String, Object> e : sortMap.entrySet()) {
                Long id = Long.valueOf(e.getKey());
                int sortVal = e.getValue() instanceof Number ? ((Number) e.getValue()).intValue() : 0;
                updateSort(mapper, id, sortVal, type);
            }
        }
        return ApiResponse.success(true);
    }

    private void updateSort(BaseMapper<?> mapper, Long id, int sort, String type) {
        Object entity = mapper.selectById(id);
        if (entity == null) return;
        try {
            entity.getClass().getMethod("setSort", Integer.class).invoke(entity, sort);
            entity.getClass().getMethod("setUpdatedAt", Long.class).invoke(entity, System.currentTimeMillis() / 1000);
            ((BaseMapper<Object>) mapper).updateById(entity);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void addServersWithType(List<Map<String, Object>> target, List<?> servers, String type) {
        for (Object s : servers) {
            try {
                Map<String, Object> map = objectMapper.convertValue(s, Map.class);
                map.put("type", type);
                target.add(map);
            } catch (Exception ignored) {}
        }
    }
}
