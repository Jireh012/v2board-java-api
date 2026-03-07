package com.v2board.api.controller.admin.server;

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
 * 对齐 PHP Admin\Server 下各协议 Controller (Vmess/Vless/Trojan/Shadowsocks/Hysteria/Tuic/AnyTLS/V2node)
 * 统一处理 save / drop / update / copy 四个操作
 *
 * API 路径:
 *   POST /api/v1/admin/server/{type}/save
 *   POST /api/v1/admin/server/{type}/drop
 *   POST /api/v1/admin/server/{type}/update
 *   POST /api/v1/admin/server/{type}/copy
 *
 * type: vmess, vless, trojan, shadowsocks, hysteria, tuic, anytls, v2node
 */
@RestController
@RequestMapping("/api/v1/admin/server")
public class AdminNodeController {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private ServerVmessMapper serverVmessMapper;
    @Autowired private ServerVlessMapper serverVlessMapper;
    @Autowired private ServerTrojanMapper serverTrojanMapper;
    @Autowired private ServerShadowsocksMapper serverShadowsocksMapper;
    @Autowired private ServerHysteriaMapper serverHysteriaMapper;
    @Autowired private ServerTuicMapper serverTuicMapper;
    @Autowired private ServerAnytlsMapper serverAnytlsMapper;
    @Autowired private ServerV2nodeMapper serverV2nodeMapper;

    /**
     * 保存节点（新建或更新）
     */
    @PostMapping("/{type}/save")
    public ApiResponse<Boolean> save(@PathVariable("type") String type, @RequestBody Map<String, Object> body) {
        BaseMapper<?> mapper = getMapper(type);
        Class<?> modelClass = getModelClass(type);

        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        long now = System.currentTimeMillis() / 1000;

        // 预处理参数
        preprocessParams(type, body);

        if (id != null) {
            Object entity = mapper.selectById(id);
            if (entity == null) throw new BusinessException(500, "服务器不存在");
            // 更新字段
            applyFields(entity, body, modelClass);
            setField(entity, "updatedAt", now, Long.class);
            ((BaseMapper<Object>) mapper).updateById(entity);
        } else {
            try {
                Object entity = modelClass.getDeclaredConstructor().newInstance();
                applyFields(entity, body, modelClass);
                setField(entity, "createdAt", now, Long.class);
                setField(entity, "updatedAt", now, Long.class);
                ((BaseMapper<Object>) mapper).insert(entity);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(500, "创建失败");
            }
        }
        return ApiResponse.success(true);
    }

    /**
     * 删除节点
     */
    @PostMapping("/{type}/drop")
    public ApiResponse<Boolean> drop(@PathVariable("type") String type, @RequestParam("id") Long id) {
        BaseMapper<?> mapper = getMapper(type);
        Object entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException(500, "节点ID不存在");
        mapper.deleteById(id);
        return ApiResponse.success(true);
    }

    /**
     * 更新节点显示状态
     */
    @PostMapping("/{type}/update")
    public ApiResponse<Boolean> update(@PathVariable("type") String type, @RequestBody Map<String, Object> body) {
        Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
        if (id == null) throw new BusinessException(500, "ID不能为空");
        BaseMapper<?> mapper = getMapper(type);
        Object entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException(500, "该服务器不存在");

        if (body.containsKey("show")) {
            Integer show = body.get("show") != null ? Integer.valueOf(body.get("show").toString()) : null;
            setField(entity, "show", show, Integer.class);
        }
        setField(entity, "updatedAt", System.currentTimeMillis() / 1000, Long.class);
        ((BaseMapper<Object>) mapper).updateById(entity);
        return ApiResponse.success(true);
    }

    /**
     * 复制节点
     */
    @PostMapping("/{type}/copy")
    public ApiResponse<Boolean> copy(@PathVariable("type") String type, @RequestParam("id") Long id) {
        BaseMapper<?> mapper = getMapper(type);
        Class<?> modelClass = getModelClass(type);
        Object entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException(500, "服务器不存在");

        try {
            // 通过 JSON 序列化/反序列化复制一个新对象
            String json = objectMapper.writeValueAsString(entity);
            Object copy = objectMapper.readValue(json, modelClass);
            // 清空 id，设置 show=0
            setField(copy, "id", null, Long.class);
            setField(copy, "show", 0, Integer.class);
            long now = System.currentTimeMillis() / 1000;
            setField(copy, "createdAt", now, Long.class);
            setField(copy, "updatedAt", now, Long.class);
            ((BaseMapper<Object>) mapper).insert(copy);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "复制失败");
        }
        return ApiResponse.success(true);
    }

    // ---- helpers ----

    private BaseMapper<?> getMapper(String type) {
        return switch (type) {
            case "vmess" -> serverVmessMapper;
            case "vless" -> serverVlessMapper;
            case "trojan" -> serverTrojanMapper;
            case "shadowsocks" -> serverShadowsocksMapper;
            case "hysteria" -> serverHysteriaMapper;
            case "tuic" -> serverTuicMapper;
            case "anytls" -> serverAnytlsMapper;
            case "v2node" -> serverV2nodeMapper;
            default -> throw new BusinessException(500, "未知的节点类型: " + type);
        };
    }

    private Class<?> getModelClass(String type) {
        return switch (type) {
            case "vmess" -> ServerVmess.class;
            case "vless" -> ServerVless.class;
            case "trojan" -> ServerTrojan.class;
            case "shadowsocks" -> ServerShadowsocks.class;
            case "hysteria" -> ServerHysteria.class;
            case "tuic" -> ServerTuic.class;
            case "anytls" -> ServerAnytls.class;
            case "v2node" -> ServerV2node.class;
            default -> throw new BusinessException(500, "未知的节点类型: " + type);
        };
    }

    /**
     * 预处理参数 — 对齐 PHP 各 Controller 中的特殊逻辑
     */
    private void preprocessParams(String type, Map<String, Object> body) {
        switch (type) {
            case "hysteria" -> preprocessHysteria(body);
            case "v2node" -> preprocessV2node(body);
            default -> {}
        }
    }

    private void preprocessHysteria(Map<String, Object> body) {
        if (!body.containsKey("up_mbps")) body.put("up_mbps", 0);
        if (!body.containsKey("down_mbps")) body.put("down_mbps", 0);
        if (body.get("obfs") == null || body.get("obfs").toString().isEmpty()) {
            body.put("obfs_password", null);
        }
    }

    private void preprocessV2node(Map<String, Object> body) {
        String protocol = (String) body.get("protocol");
        if (protocol != null) {
            // anytls 强制 tls
            if ("anytls".equals(protocol) && Integer.valueOf(0).equals(toInt(body.get("tls")))) {
                body.put("tls", 1);
            }
            // hysteria2, trojan, tuic 强制 tls=1
            if (Set.of("hysteria2", "trojan", "tuic").contains(protocol)) {
                body.put("tls", 1);
            }
            // shadowsocks 默认 cipher
            if ("shadowsocks".equals(protocol) && !body.containsKey("cipher")) {
                body.put("cipher", "aes-128-gcm");
            }
        }
        // network != tcp 时清除 flow
        String network = (String) body.get("network");
        String encryption = (String) body.get("encryption");
        if (network != null && !"tcp".equals(network) && !"mlkem768x25519plus".equals(encryption)) {
            body.put("flow", null);
        }
        if (!body.containsKey("up_mbps")) body.put("up_mbps", 0);
        if (!body.containsKey("down_mbps")) body.put("down_mbps", 0);
        if (body.get("obfs") == null || body.get("obfs").toString().isEmpty()) {
            body.put("obfs_password", null);
        }
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return null; }
    }

    /**
     * 将 map 中的键值应用到 entity 的对应字段
     * 使用 ObjectMapper 来处理 JSON 数据和类型转换
     */
    private void applyFields(Object entity, Map<String, Object> body, Class<?> modelClass) {
        // 不拷贝 id、created_at、updated_at
        Map<String, Object> filtered = new HashMap<>(body);
        filtered.remove("id");
        filtered.remove("created_at");
        filtered.remove("updated_at");

        try {
            // 先将现有 entity 序列化为 map，再合并新值
            String existingJson = objectMapper.writeValueAsString(entity);
            Map<String, Object> existingMap = objectMapper.readValue(existingJson,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            existingMap.putAll(filtered);
            // 不覆盖 id
            existingMap.remove("type"); // 前端可能附加 type 字段

            Object merged = objectMapper.convertValue(existingMap, modelClass);
            // 将 merged 的所有字段拷贝回 entity
            for (java.lang.reflect.Field f : modelClass.getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(merged);
                f.set(entity, val);
            }
        } catch (Exception e) {
            throw new BusinessException(500, "参数解析失败: " + e.getMessage());
        }
    }

    private void setField(Object entity, String fieldName, Object value, Class<?> type) {
        try {
            String setter = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            entity.getClass().getMethod(setter, type).invoke(entity, value);
        } catch (Exception ignored) {}
    }
}
