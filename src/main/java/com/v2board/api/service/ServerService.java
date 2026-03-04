package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.ServerAnytlsMapper;
import com.v2board.api.mapper.ServerHysteriaMapper;
import com.v2board.api.mapper.ServerShadowsocksMapper;
import com.v2board.api.mapper.ServerTrojanMapper;
import com.v2board.api.mapper.ServerTuicMapper;
import com.v2board.api.mapper.ServerV2nodeMapper;
import com.v2board.api.mapper.ServerVlessMapper;
import com.v2board.api.mapper.ServerVmessMapper;
import com.v2board.api.model.ServerAnytls;
import com.v2board.api.model.ServerHysteria;
import com.v2board.api.model.ServerShadowsocks;
import com.v2board.api.model.ServerTrojan;
import com.v2board.api.model.ServerTuic;
import com.v2board.api.model.ServerV2node;
import com.v2board.api.model.ServerVless;
import com.v2board.api.model.ServerVmess;
import com.v2board.api.model.User;
import com.v2board.api.util.Helper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ServerService {
    
    private static final Logger logger = LoggerFactory.getLogger(ServerService.class);
    
    @Autowired
    private ServerVmessMapper vmessMapper;
    
    @Autowired
    private ServerShadowsocksMapper shadowsocksMapper;
    
    @Autowired
    private ServerTrojanMapper trojanMapper;
    
    @Autowired
    private ServerHysteriaMapper hysteriaMapper;
    
    @Autowired
    private ServerVlessMapper vlessMapper;

    @Autowired
    private ServerTuicMapper tuicMapper;

    @Autowired
    private ServerAnytlsMapper anytlsMapper;

    @Autowired
    private ServerV2nodeMapper v2nodeMapper;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 获取用户可用的服务器列表
     */
    public List<Map<String, Object>> getAvailableServers(User user) {
        List<Map<String, Object>> servers = new ArrayList<>();
        
        try {
            // 获取各种类型的服务器
            List<Map<String, Object>> shadowsocks = getAvailableShadowsocks(user);
            List<Map<String, Object>> vmess = getAvailableVmess(user);
            List<Map<String, Object>> trojan = getAvailableTrojan(user);
            List<Map<String, Object>> hysteria = getAvailableHysteria(user);
            List<Map<String, Object>> vless = getAvailableVless(user);
            
            logger.debug("Found servers - Shadowsocks: {}, VMess: {}, Trojan: {}, Hysteria: {}, VLESS: {}", 
                shadowsocks.size(), vmess.size(), trojan.size(), hysteria.size(), vless.size());
            
            servers.addAll(shadowsocks);
            servers.addAll(vmess);
            servers.addAll(trojan);
            servers.addAll(hysteria);
            servers.addAll(vless);
        } catch (Exception e) {
            logger.error("Error getting available servers", e);
        }
        
        // 按 sort 字段排序（与 PHP 的 array_multisort 一致）
        servers.sort(Comparator.comparing(s -> {
            Object sort = s.get("sort");
            return sort != null ? (Integer) sort : 0;
        }));
        
        // 处理端口、在线状态和 cache_key（与 PHP 的 array_map 逻辑一致）
        return servers.stream().map(server -> {
            // 端口转换为整数（PHP: $server['port'] = (int)$server['port']）
            Object portObj = server.get("port");
            if (portObj instanceof String) {
                // 如果端口是字符串且包含范围，已经在获取服务器时处理了
                // 这里只需要转换为整数
                try {
                    server.put("port", Integer.parseInt((String) portObj));
                } catch (NumberFormatException e) {
                    server.put("port", 0);
                }
            } else if (portObj instanceof Integer) {
                server.put("port", portObj);
            }
            
            // 检查在线状态（PHP: $server['is_online'] = (time() - 300 > $server['last_check_at']) ? 0 : 1）
            Long lastCheckAt = (Long) server.getOrDefault("last_check_at", 0L);
            long currentTime = System.currentTimeMillis() / 1000;
            server.put("is_online", (currentTime - 300 > lastCheckAt) ? 0 : 1);
            
            // 添加 cache_key（PHP: $server['cache_key'] = "{$server['type']}-{$server['id']}-{$server['updated_at']}-{$server['is_online']}"）
            Long updatedAt = (Long) server.getOrDefault("updated_at", 0L);
            String cacheKey = String.format("%s-%d-%d-%d", 
                server.get("type"), 
                server.get("id"), 
                updatedAt, 
                server.get("is_online"));
            server.put("cache_key", cacheKey);
            
            return server;
        }).collect(Collectors.toList());
    }

    /**
     * 查找指定类型与 ID 的服务器，供 UniProxy 使用。
     */
    public Object findServer(String serverType, Long serverId) {
        if (serverId == null || serverType == null) {
            return null;
        }
        return switch (serverType.toLowerCase(Locale.ROOT)) {
            case "vmess" -> vmessMapper.selectById(serverId);
            case "shadowsocks" -> shadowsocksMapper.selectById(serverId);
            case "trojan" -> trojanMapper.selectById(serverId);
            case "hysteria" -> hysteriaMapper.selectById(serverId);
            case "vless" -> vlessMapper.selectById(serverId);
            case "tuic" -> tuicMapper.selectById(serverId);
            case "anytls" -> anytlsMapper.selectById(serverId);
            case "v2node" -> v2nodeMapper.selectById(serverId);
            default -> null;
        };
    }
    
    /**
     * 获取可用的 Shadowsocks 服务器
     */
    private List<Map<String, Object>> getAvailableShadowsocks(User user) {
        // 先获取所有服务器（不筛选show），与PHP逻辑一致
        List<ServerShadowsocks> allServers = shadowsocksMapper.selectList(
            new LambdaQueryWrapper<ServerShadowsocks>()
                .orderByAsc(ServerShadowsocks::getSort)
        );
        
        logger.debug("Total Shadowsocks servers found: {}", allServers.size());
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (ServerShadowsocks server : allServers) {
            // 检查show字段
            if (server.getShow() == null || server.getShow() != 1) {
                logger.debug("Shadowsocks server {} skipped: show = {}", server.getId(), server.getShow());
                continue;
            }
            
            // 检查用户组匹配
            if (!isUserGroupMatched(server.getGroupId(), user.getGroupId())) {
                logger.debug("Shadowsocks server {} skipped: group_id not matched. Server groups: {}, User group: {}", 
                    server.getId(), server.getGroupId(), user.getGroupId());
                continue;
            }
            
            // 处理端口范围（PHP: if (strpos($v['port'], '-') !== false) { $shadowsocks[$key]['port'] = Helper::randomPort($v['port']); }）
            String portStr = server.getPort();
            if (portStr != null && portStr.contains("-")) {
                portStr = String.valueOf(Helper.randomPort(portStr));
            }
            
            Long targetId = server.getParentId() != null ? server.getParentId() : server.getId();
            Long lastCheckAt = getServerTimestamp("SERVER_SHADOWSOCKS_LAST_CHECK_AT", targetId);

            Map<String, Object> map = new HashMap<>();
            map.put("id", server.getId());
            map.put("type", "shadowsocks");
            map.put("name", server.getName());
            map.put("host", server.getHost());
            map.put("port", portStr); // 已经处理过端口范围
            map.put("cipher", server.getCipher());
            map.put("rate", server.getRate() != null && !server.getRate().isEmpty() ? server.getRate() : "1");
            map.put("created_at", server.getCreatedAt());
            map.put("updated_at", server.getUpdatedAt()); // 添加 updated_at 用于 cache_key
            map.put("last_check_at", lastCheckAt != null ? lastCheckAt : 0L);
            map.put("sort", server.getSort());
            result.add(map);
            
            logger.debug("Shadowsocks server {} added: {}", server.getId(), server.getName());
        }
        
        return result;
    }
    
    /**
     * 获取可用的 VMess 服务器
     */
    private List<Map<String, Object>> getAvailableVmess(User user) {
        // 先获取所有服务器（不筛选show），与PHP逻辑一致
        List<ServerVmess> allServers = vmessMapper.selectList(
            new LambdaQueryWrapper<ServerVmess>()
                .orderByAsc(ServerVmess::getSort)
        );
        
        logger.debug("Total VMess servers found: {}", allServers.size());
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (ServerVmess server : allServers) {
            // 检查show字段
            if (server.getShow() == null || server.getShow() != 1) {
                logger.debug("VMess server {} skipped: show = {}", server.getId(), server.getShow());
                continue;
            }
            
            // 检查用户组匹配
            if (!isUserGroupMatched(server.getGroupId(), user.getGroupId())) {
                logger.debug("VMess server {} skipped: group_id not matched. Server groups: {}, User group: {}", 
                    server.getId(), server.getGroupId(), user.getGroupId());
                continue;
            }
            
            // 处理端口范围（PHP: if (strpos($vmess[$key]['port'], '-') !== false) { $vmess[$key]['port'] = Helper::randomPort($vmess[$key]['port']); }）
            String portStr = server.getPort();
            if (portStr != null && portStr.contains("-")) {
                portStr = String.valueOf(Helper.randomPort(portStr));
            }
            
            Long targetId = server.getParentId() != null ? server.getParentId() : server.getId();
            Long lastCheckAt = getServerTimestamp("SERVER_VMESS_LAST_CHECK_AT", targetId);

            Map<String, Object> map = new HashMap<>();
            map.put("id", server.getId());
            map.put("type", "vmess");
            map.put("name", server.getName());
            map.put("host", server.getHost());
            map.put("port", portStr); // 已经处理过端口范围
            map.put("network", server.getNetwork());
            map.put("tls", server.getTls() != null && server.getTls() == 1);
            
            // 解析 JSON 字符串
            try {
                if (server.getTlsSettings() != null && !server.getTlsSettings().isEmpty()) {
                    Map<String, Object> tlsSettings = objectMapper.readValue(
                        server.getTlsSettings(), 
                        new TypeReference<Map<String, Object>>() {}
                    );
                    map.put("tlsSettings", tlsSettings);
                }
                if (server.getNetworkSettings() != null && !server.getNetworkSettings().isEmpty()) {
                    Map<String, Object> networkSettings = objectMapper.readValue(
                        server.getNetworkSettings(), 
                        new TypeReference<Map<String, Object>>() {}
                    );
                    map.put("networkSettings", networkSettings);
                }
            } catch (Exception e) {
                logger.warn("Error parsing JSON settings for VMess server {}: {}", server.getId(), e.getMessage());
            }
            
            map.put("created_at", server.getCreatedAt());
            map.put("sort", server.getSort());
            map.put("last_check_at", lastCheckAt != null ? lastCheckAt : 0L);
            result.add(map);
            
            logger.debug("VMess server {} added: {}", server.getId(), server.getName());
        }
        
        return result;
    }
    
    /**
     * 获取可用的 Trojan 服务器
     */
    private List<Map<String, Object>> getAvailableTrojan(User user) {
        // 先获取所有服务器（不筛选show），与PHP逻辑一致
        List<ServerTrojan> allServers = trojanMapper.selectList(
            new LambdaQueryWrapper<ServerTrojan>()
                .orderByAsc(ServerTrojan::getSort)
        );
        
        logger.debug("Total Trojan servers found: {}", allServers.size());
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (ServerTrojan server : allServers) {
            // 检查show字段
            if (server.getShow() == null || server.getShow() != 1) {
                logger.debug("Trojan server {} skipped: show = {}", server.getId(), server.getShow());
                continue;
            }
            
            // 检查用户组匹配
            if (!isUserGroupMatched(server.getGroupId(), user.getGroupId())) {
                logger.debug("Trojan server {} skipped: group_id not matched. Server groups: {}, User group: {}", 
                    server.getId(), server.getGroupId(), user.getGroupId());
                continue;
            }
            
            // 处理端口范围（PHP: if (strpos($trojan[$key]['port'], '-') !== false) { $trojan[$key]['port'] = Helper::randomPort($trojan[$key]['port']); }）
            String portStr = server.getPort();
            if (portStr != null && portStr.contains("-")) {
                portStr = String.valueOf(Helper.randomPort(portStr));
            }
            
            Long targetId = server.getParentId() != null ? server.getParentId() : server.getId();
            Long lastCheckAt = getServerTimestamp("SERVER_TROJAN_LAST_CHECK_AT", targetId);

            Map<String, Object> map = new HashMap<>();
            map.put("id", server.getId());
            map.put("type", "trojan");
            map.put("name", server.getName());
            map.put("host", server.getHost());
            map.put("port", portStr); // 已经处理过端口范围
            map.put("rate", server.getRate() != null && !server.getRate().isEmpty() ? server.getRate() : "1");
            map.put("server_name", server.getServerName());
            map.put("allow_insecure", server.getAllowInsecure() != null && server.getAllowInsecure() == 1);
            map.put("created_at", server.getCreatedAt());
            map.put("updated_at", server.getUpdatedAt()); // 添加 updated_at 用于 cache_key
            map.put("sort", server.getSort());
            map.put("last_check_at", lastCheckAt != null ? lastCheckAt : 0L);
            result.add(map);
            
            logger.debug("Trojan server {} added: {}", server.getId(), server.getName());
        }
        
        return result;
    }
    
    /**
     * 获取可用的 Hysteria 服务器
     */
    private List<Map<String, Object>> getAvailableHysteria(User user) {
        // 先获取所有服务器（不筛选show），与PHP逻辑一致
        List<ServerHysteria> allServers = hysteriaMapper.selectList(
            new LambdaQueryWrapper<ServerHysteria>()
                .orderByAsc(ServerHysteria::getSort)
        );
        
        logger.debug("Total Hysteria servers found: {}", allServers.size());
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (ServerHysteria server : allServers) {
            // 检查show字段
            if (server.getShow() == null || server.getShow() != 1) {
                logger.debug("Hysteria server {} skipped: show = {}", server.getId(), server.getShow());
                continue;
            }
            
            // 检查用户组匹配
            if (!isUserGroupMatched(server.getGroupId(), user.getGroupId())) {
                logger.debug("Hysteria server {} skipped: group_id not matched. Server groups: {}, User group: {}", 
                    server.getId(), server.getGroupId(), user.getGroupId());
                continue;
            }
            
            Long targetId = server.getParentId() != null ? server.getParentId() : server.getId();
            Long lastCheckAt = getServerTimestamp("SERVER_HYSTERIA_LAST_CHECK_AT", targetId);

            Map<String, Object> map = new HashMap<>();
            map.put("id", server.getId());
            map.put("type", "hysteria");
            map.put("name", server.getName());
            map.put("host", server.getHost());
            map.put("port", server.getPort());
            map.put("rate", server.getRate() != null && !server.getRate().isEmpty() ? server.getRate() : "1");
            map.put("created_at", server.getCreatedAt());
            map.put("sort", server.getSort());
            map.put("last_check_at", lastCheckAt != null ? lastCheckAt : 0L);
            result.add(map);
            
            logger.debug("Hysteria server {} added: {}", server.getId(), server.getName());
        }
        
        return result;
    }

    /**
     * 获取可用的 TUIC 服务器
     */
    private List<Map<String, Object>> getAvailableTuic(User user) {
        List<ServerTuic> allServers = tuicMapper.selectList(
                new LambdaQueryWrapper<ServerTuic>()
                        .orderByAsc(ServerTuic::getSort)
        );

        logger.debug("Total TUIC servers found: {}", allServers.size());

        List<Map<String, Object>> result = new ArrayList<>();
        for (ServerTuic server : allServers) {
            if (server.getShow() == null || server.getShow() != 1) {
                continue;
            }
            if (!isUserGroupMatched(server.getGroupId(), user.getGroupId())) {
                continue;
            }

            Long targetId = server.getParentId() != null ? server.getParentId() : server.getId();
            Long lastCheckAt = getServerTimestamp("SERVER_TUIC_LAST_CHECK_AT", targetId);

            Map<String, Object> map = new HashMap<>();
            map.put("id", server.getId());
            map.put("type", "tuic");
            map.put("name", server.getName());
            map.put("host", server.getHost());
            map.put("port", server.getPort());
            map.put("rate", server.getRate() != null && !server.getRate().isEmpty() ? server.getRate() : "1");
            map.put("server_port", server.getServerPort());
            map.put("server_name", server.getServerName());
            map.put("created_at", server.getCreatedAt());
            map.put("sort", server.getSort());
            map.put("last_check_at", lastCheckAt != null ? lastCheckAt : 0L);
            result.add(map);
        }
        return result;
    }

    /**
     * 获取可用的 AnyTLS 服务器
     */
    private List<Map<String, Object>> getAvailableAnyTLS(User user) {
        List<ServerAnytls> allServers = anytlsMapper.selectList(
                new LambdaQueryWrapper<ServerAnytls>()
                        .orderByAsc(ServerAnytls::getSort)
        );

        logger.debug("Total AnyTLS servers found: {}", allServers.size());

        List<Map<String, Object>> result = new ArrayList<>();
        for (ServerAnytls server : allServers) {
            if (server.getShow() == null || server.getShow() != 1) {
                continue;
            }
            if (!isUserGroupMatched(server.getGroupId(), user.getGroupId())) {
                continue;
            }

            Long targetId = server.getParentId() != null ? server.getParentId() : server.getId();
            Long lastCheckAt = getServerTimestamp("SERVER_ANYTLS_LAST_CHECK_AT", targetId);

            Map<String, Object> map = new HashMap<>();
            map.put("id", server.getId());
            map.put("type", "anytls");
            map.put("name", server.getName());
            map.put("host", server.getHost());
            map.put("port", server.getPort());
            map.put("rate", server.getRate() != null && !server.getRate().isEmpty() ? server.getRate() : "1");
            map.put("server_port", server.getServerPort());
            map.put("server_name", server.getServerName());
            map.put("padding_scheme", server.getPaddingScheme());
            map.put("created_at", server.getCreatedAt());
            map.put("sort", server.getSort());
            map.put("last_check_at", lastCheckAt != null ? lastCheckAt : 0L);
            result.add(map);
        }
        return result;
    }

    /**
     * 获取可用的 V2node 服务器
     */
    private List<Map<String, Object>> getAvailableV2node(User user) {
        List<ServerV2node> allServers = v2nodeMapper.selectList(
                new LambdaQueryWrapper<ServerV2node>()
                        .orderByAsc(ServerV2node::getSort)
        );

        logger.debug("Total V2node servers found: {}", allServers.size());

        List<Map<String, Object>> result = new ArrayList<>();
        for (ServerV2node server : allServers) {
            if (server.getShow() == null || server.getShow() != 1) {
                continue;
            }
            if (!isUserGroupMatched(server.getGroupId(), user.getGroupId())) {
                continue;
            }

            Long targetId = server.getParentId() != null ? server.getParentId() : server.getId();
            Long lastCheckAt = getServerTimestamp("SERVER_V2NODE_LAST_CHECK_AT", targetId);

            Map<String, Object> map = new HashMap<>();
            map.put("id", server.getId());
            map.put("type", "v2node");
            map.put("name", server.getName());
            map.put("host", server.getHost());
            map.put("port", server.getPort());
            map.put("rate", server.getRate() != null && !server.getRate().isEmpty() ? server.getRate() : "1");
            map.put("server_port", server.getServerPort());
            map.put("server_name", server.getServerName());
            map.put("protocol", server.getProtocol());
            map.put("network", server.getNetwork());
            map.put("created_at", server.getCreatedAt());
            map.put("sort", server.getSort());
            map.put("last_check_at", lastCheckAt != null ? lastCheckAt : 0L);
            result.add(map);
        }
        return result;
    }
    
    /**
     * 获取可用的 VLESS 服务器
     */
    private List<Map<String, Object>> getAvailableVless(User user) {
        try {
            // 先获取所有服务器（不筛选show），与PHP逻辑一致
            List<ServerVless> allServers = vlessMapper.selectList(
                new LambdaQueryWrapper<ServerVless>()
                    .orderByAsc(ServerVless::getSort)
            );
            
            logger.debug("Total VLESS servers found: {}", allServers.size());
            
            List<Map<String, Object>> result = new ArrayList<>();
            
            for (ServerVless server : allServers) {
                // 检查show字段
                if (server.getShow() == null || server.getShow() != 1) {
                    logger.debug("VLESS server {} skipped: show = {}", server.getId(), server.getShow());
                    continue;
                }
                
                // 检查用户组匹配
                // 添加详细日志用于调试
                List<?> serverGroupIds = server.getGroupId();
                logger.debug("VLESS server {} - Checking group match. Server group_id: {} (class: {}, element types: {}), User group_id: {} (class: {})", 
                    server.getId(), 
                    serverGroupIds,
                    serverGroupIds != null ? serverGroupIds.getClass().getName() : "null",
                    serverGroupIds != null && !serverGroupIds.isEmpty() ? 
                        serverGroupIds.get(0).getClass().getSimpleName() : "empty",
                    user.getGroupId(),
                    user.getGroupId() != null ? user.getGroupId().getClass().getName() : "null");
                
                if (!isUserGroupMatched(serverGroupIds, user.getGroupId())) {
                    logger.debug("VLESS server {} skipped: group_id not matched. Server groups: {}, User group: {}", 
                        server.getId(), serverGroupIds, user.getGroupId());
                    continue;
                }
                
                // 端口是 int 类型，不需要处理范围
                Integer port = server.getPort();
                
                Long targetId = server.getParentId() != null ? server.getParentId() : server.getId();
                Long lastCheckAt = getServerTimestamp("SERVER_VLESS_LAST_CHECK_AT", targetId);

                Map<String, Object> map = new HashMap<>();
                map.put("id", server.getId());
                map.put("type", "vless");
                map.put("name", server.getName());
                map.put("host", server.getHost());
                map.put("port", port != null ? port : 0);
                map.put("rate", server.getRate() != null && !server.getRate().isEmpty() ? server.getRate() : "1");
                map.put("network", server.getNetwork());
                // 直接使用Integer类型的tls值：0=none, 1=tls, 2=reality
                map.put("tls", server.getTls() != null ? server.getTls() : 0);
                map.put("flow", server.getFlow());
                
                // 解析 JSON 字符串
                try {
                    if (server.getTlsSettings() != null && !server.getTlsSettings().isEmpty()) {
                        Map<String, Object> tlsSettings = objectMapper.readValue(
                            server.getTlsSettings(), 
                            new TypeReference<Map<String, Object>>() {}
                        );
                        map.put("tlsSettings", tlsSettings);
                    }
                    if (server.getNetworkSettings() != null && !server.getNetworkSettings().isEmpty()) {
                        Map<String, Object> networkSettings = objectMapper.readValue(
                            server.getNetworkSettings(), 
                            new TypeReference<Map<String, Object>>() {}
                        );
                        map.put("networkSettings", networkSettings);
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing JSON settings for VLESS server {}: {}", server.getId(), e.getMessage());
                }
                
                map.put("created_at", server.getCreatedAt());
                map.put("updated_at", server.getUpdatedAt()); // 添加 updated_at 用于 cache_key
                map.put("sort", server.getSort());
                map.put("last_check_at", lastCheckAt != null ? lastCheckAt : 0L);
                result.add(map);
                
                logger.debug("VLESS server {} added: {}", server.getId(), server.getName());
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Error getting VLESS servers, table may not exist: {}", e.getMessage());
            return new ArrayList<>(); // 如果表不存在，返回空列表
        }
    }
    
    /**
     * 检查用户组是否匹配
     * PHP逻辑：in_array($user->group_id, $server['group_id'])
     */
    private boolean isUserGroupMatched(List<?> serverGroupIds, Integer userGroupId) {
        if (serverGroupIds == null || serverGroupIds.isEmpty()) {
            logger.debug("Server group_ids is null or empty");
            return false;
        }
        if (userGroupId == null) {
            logger.debug("User group_id is null");
            return false;
        }
        
        // 详细日志：检查每个元素的类型和值
        logger.debug("Checking group match - User group: {} (type: {}), Server groups: {} (types: {})", 
            userGroupId, userGroupId.getClass().getSimpleName(),
            serverGroupIds, 
            serverGroupIds.stream()
                .map(g -> g != null ? g.getClass().getSimpleName() : "null")
                .collect(java.util.stream.Collectors.toList()));
        
        // 使用多种方式比较，确保类型兼容
        boolean matched = false;
        for (Object groupId : serverGroupIds) {
            if (groupId == null) continue;
            
            // 转换为整数进行比较
            int serverGroupId;
            if (groupId instanceof Integer) {
                serverGroupId = (Integer) groupId;
            } else if (groupId instanceof Long) {
                serverGroupId = ((Long) groupId).intValue();
            } else if (groupId instanceof String) {
                try {
                    serverGroupId = Integer.parseInt((String) groupId);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid group_id format: {}", groupId);
                    continue;
                }
            } else {
                logger.warn("Unexpected group_id type: {} ({})", groupId, groupId.getClass().getSimpleName());
                continue;
            }
            
            if (serverGroupId == userGroupId) {
                matched = true;
                break;
            }
        }
        
        logger.debug("Group match result: user group {} in server groups {} = {}", 
            userGroupId, serverGroupIds, matched);
        return matched;
    }

    private Long getServerTimestamp(String keyPrefix, Long id) {
        try {
            String key = keyPrefix + "_" + id;
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof Number num) {
                return num.longValue();
            }
            if (value instanceof String str && !str.isEmpty()) {
                return Long.parseLong(str);
            }
        } catch (Exception e) {
            logger.warn("Error reading server timestamp for {} {}", keyPrefix, id, e);
        }
        return null;
    }
}

