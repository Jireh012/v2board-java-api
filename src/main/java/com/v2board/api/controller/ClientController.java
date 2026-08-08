package com.v2board.api.controller;

import com.v2board.api.model.User;
import com.v2board.api.protocol.ProtocolHandler;
import com.v2board.api.protocol.GeneralHandler;
import com.v2board.api.service.ConfigService;
import com.v2board.api.service.ServerService;
import com.v2board.api.service.UserService;
import com.v2board.api.service.external.ExternalSubscribeNodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.v2board.api.util.Helper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class ClientController {
    
    private static final Logger logger = LoggerFactory.getLogger(ClientController.class);

    /** 自有面板节点名前缀 */
    private static final String SAFE_NODE_PREFIX = "🔒 ";
    /** 第三方订阅节点名前缀 */
    private static final String UNSAFE_NODE_PREFIX = "⚠️ ";
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ServerService serverService;

    @Autowired
    private ExternalSubscribeNodeService externalSubscribeNodeService;
    
    @Autowired
    private GeneralHandler generalHandler;
    
    @Autowired
    private List<ProtocolHandler> protocolHandlers;

    @Autowired
    private ConfigService configService;
    
    @Value("${v2board.subscribe-path:}")
    private String subscribePath;
    
    /**
     * 订阅接口
     * 路径由 DynamicRouteConfig 动态注册
     * 如果没有配置自定义路径，则使用默认路径 /api/v1/client/subscribe
     */
    public String subscribe(
            @RequestParam(required = false) String flag,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        try {
            // 从拦截器设置的属性中获取用户
            User user = (User) request.getAttribute("user");
            if (user == null) {
                logger.warn("User not found in request attribute");
                return "";
            }
            
            logger.debug("Processing subscribe request for user: {}", user.getEmail());
            
            // 检查用户是否可用
            if (!userService.isAvailable(user)) {
                logger.warn("User {} is not available", user.getEmail());
                return "";
            }
            
            // 获取可用服务器（面板节点 + 第三方连通节点）
            List<Map<String, Object>> panelServers = serverService.getAvailableServers(user);
            List<Map<String, Object>> externalServers = externalSubscribeNodeService.listReachableAsServerMaps();
            applyNodeSecurityMarkers(panelServers, SAFE_NODE_PREFIX);
            applyNodeSecurityMarkers(externalServers, UNSAFE_NODE_PREFIX);
            List<Map<String, Object>> servers = new ArrayList<>(panelServers.size() + externalServers.size());
            servers.addAll(panelServers);
            servers.addAll(externalServers);
            logger.debug("Found {} panel + {} external servers for user {}",
                    panelServers.size(), externalServers.size(), user.getEmail());

            if (servers.isEmpty()) {
                logger.warn("No available servers found for user {}", user.getEmail());
                return "";
            }
            
            // 根据 flag 或 User-Agent 选择协议处理器
            String userAgent = request.getHeader("User-Agent");
            if (flag == null || flag.isEmpty()) {
                flag = userAgent != null ? userAgent.toLowerCase() : "";
            } else {
                flag = flag.toLowerCase();
            }
            
            logger.debug("Using flag: {}, User-Agent: {}", flag, userAgent);
            
            // 处理sing-box特殊逻辑
            if (flag.contains("sing")) {
                // 检查sing-box版本
                String version = extractSingBoxVersion(flag);
                ProtocolHandler handler = selectSingBoxHandler(version);
                if (handler != null) {
                    logger.debug("Using sing-box handler: {}", handler.getClass().getSimpleName());
                    handler.applyResponseHeaders(user, response);
                    return handler.handle(user, servers);
                }
            }
            
            // 对于非sing-box的客户端，设置订阅信息到服务器
            if (!flag.contains("sing")) {
                setSubscribeInfoToServers(servers, user);
            }
            
            // 选择协议处理器
            ProtocolHandler handler = selectHandler(flag);
            if (handler == null) {
                handler = generalHandler;
            }
            
            logger.debug("Using protocol handler: {}", handler.getClass().getSimpleName());
            
            handler.applyResponseHeaders(user, response);
            String result = handler.handle(user, servers);
            logger.debug("Generated subscribe content length: {}", result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            logger.error("Error processing subscribe request", e);
            return "";
        }
    }
    
    /**
     * 根据 flag 选择协议处理器
     */
    private ProtocolHandler selectHandler(String flag) {
        if (flag == null || flag.isEmpty()) {
            return null;
        }

        if (flag.contains("quantumult")) {
            for (ProtocolHandler handler : protocolHandlers) {
                if (handler instanceof com.v2board.api.protocol.QuantumultXHandler) {
                    return handler;
                }
            }
        }

        // verge 需在 clash 之前匹配（避免 UA 同时含 clash 与 verge 时误选）
        if (flag.contains("verge")) {
            for (ProtocolHandler handler : protocolHandlers) {
                if (handler instanceof com.v2board.api.protocol.ClashVergeHandler) {
                    return handler;
                }
            }
        }

        for (ProtocolHandler handler : protocolHandlers) {
            String name = handler.getClass().getSimpleName();
            if (name.contains("Singbox")) {
                continue;
            }
            if (flag.contains(handler.getFlag())) {
                return handler;
            }
        }
        
        return null;
    }
    
    /**
     * 提取sing-box版本号
     * PHP: preg_match('/sing-box\s+([0-9.]+)/i', $flag, $matches)
     */
    private String extractSingBoxVersion(String flag) {
        Pattern pattern = Pattern.compile("sing-box\\s+([0-9.]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(flag);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * 选择sing-box处理器
     * PHP: 根据版本选择Singbox或SingboxOld
     */
    private ProtocolHandler selectSingBoxHandler(String version) {
        if (version == null) {
            // 如果没有版本信息，尝试查找SingboxOld处理器
            for (ProtocolHandler handler : protocolHandlers) {
                if (handler.getFlag().contains("sing") && 
                    handler.getClass().getSimpleName().contains("Old")) {
                    return handler;
                }
            }
            return null;
        }
        
        // 比较版本，>= 1.12.0 使用新版本，否则使用旧版本
        try {
            double versionNum = Double.parseDouble(version);
            if (versionNum >= 1.12) {
                // 查找新版本Singbox处理器
                for (ProtocolHandler handler : protocolHandlers) {
                    if (handler.getFlag().contains("sing") && 
                        !handler.getClass().getSimpleName().contains("Old")) {
                        return handler;
                    }
                }
            } else {
                // 查找旧版本SingboxOld处理器
                for (ProtocolHandler handler : protocolHandlers) {
                    if (handler.getFlag().contains("sing") && 
                        handler.getClass().getSimpleName().contains("Old")) {
                        return handler;
                    }
                }
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid sing-box version format: {}", version);
        }
        
        return null;
    }
    
    /**
     * 为节点名称增加安全标记（同步 clash_proxy.name / singbox_outbound.tag）。
     */
    @SuppressWarnings("unchecked")
    private void applyNodeSecurityMarkers(List<Map<String, Object>> servers, String prefix) {
        if (servers == null || servers.isEmpty() || prefix == null || prefix.isEmpty()) {
            return;
        }
        for (Map<String, Object> server : servers) {
            if (server == null) {
                continue;
            }
            Object raw = server.get("name");
            if (raw == null) {
                continue;
            }
            String name = String.valueOf(raw).trim();
            if (name.isEmpty()) {
                continue;
            }
            if (name.startsWith(SAFE_NODE_PREFIX) || name.startsWith(UNSAFE_NODE_PREFIX)) {
                continue;
            }
            String marked = prefix + name;
            server.put("name", marked);

            Object clash = server.get("clash_proxy");
            if (clash instanceof Map<?, ?> clashMap) {
                ((Map<String, Object>) clashMap).put("name", marked);
            }
            Object outbound = server.get("singbox_outbound");
            if (outbound instanceof Map<?, ?> outboundMap) {
                ((Map<String, Object>) outboundMap).put("tag", marked);
            }
            Object shareUri = server.get("share_uri");
            if (shareUri != null) {
                String rewritten = rewriteShareUriName(String.valueOf(shareUri), marked);
                if (rewritten != null) {
                    server.put("share_uri", rewritten);
                }
            }
        }
    }

    /** 替换分享链接 # 后的节点备注名。 */
    private static String rewriteShareUriName(String shareUri, String newName) {
        if (shareUri == null || shareUri.isBlank() || newName == null) {
            return shareUri;
        }
        String uri = shareUri.trim();
        boolean crlf = uri.endsWith("\r\n");
        boolean lf = !crlf && uri.endsWith("\n");
        if (crlf) {
            uri = uri.substring(0, uri.length() - 2);
        } else if (lf) {
            uri = uri.substring(0, uri.length() - 1);
        }
        int hash = uri.indexOf('#');
        String base = hash >= 0 ? uri.substring(0, hash) : uri;
        String encoded = Helper.encodeURIComponent(newName);
        String result = base + "#" + encoded;
        if (crlf) {
            return result + "\r\n";
        }
        if (lf) {
            return result + "\n";
        }
        return result;
    }

    /**
     * 设置订阅信息到服务器列表
     * PHP: setSubscribeInfoToServers(&$servers, $user)
     */
    private void setSubscribeInfoToServers(List<Map<String, Object>> servers, User user) {
        if (servers == null || servers.isEmpty()) {
            return;
        }

        if (!configService.getShowInfoToServerEnable()) {
            return;
        }

        Map<String, Object> templateServer = servers.get(0);

        long useTraffic = (user.getU() != null ? user.getU() : 0) +
                          (user.getD() != null ? user.getD() : 0);
        long totalTraffic = user.getTransferEnable() != null ? user.getTransferEnable() : 0;
        long remainingTraffic = totalTraffic - useTraffic;
        String remainingTrafficStr = Helper.trafficConvert(remainingTraffic);

        String expiredDate;
        if (user.getExpiredAt() != null && user.getExpiredAt() > 0) {
            java.time.LocalDate date = java.time.Instant.ofEpochSecond(user.getExpiredAt())
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
            expiredDate = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else {
            expiredDate = "长期有效";
        }

        Integer resetDay = userService.getResetDay(user);
        boolean hasResetDay = resetDay != null && resetDay > 0;
        int method = configService.getShowSubscribeMethod();
        List<SubscribeInfoKind> kinds = resolveSubscribeInfoKinds(method, hasResetDay);

        // PHP array_unshift 顺序：method=0 时先 expire，再 reset，最后 traffic（最后 unshift 的在最前）
        for (SubscribeInfoKind kind : kinds) {
            Map<String, Object> info = new HashMap<>(templateServer);
            switch (kind) {
                case EXPIRE -> info.put("name", "套餐到期：" + expiredDate);
                case RESET -> info.put("name", "距离下次重置剩余：" + resetDay + " 天");
                case TRAFFIC -> info.put("name", "剩余流量：" + remainingTrafficStr);
            }
            servers.add(0, info);
        }

        logger.debug("Added subscribe info nodes method={}: kinds={}, remaining={}, resetDay={}, expired={}",
            method, kinds, remainingTrafficStr, resetDay, expiredDate);
    }

    /**
     * 按 show_subscribe_method 决定注入哪些信息节点，以及 add(0) 应用顺序。
     * 0/未知：expire → reset(可选) → traffic；1：仅 expire；2：仅 traffic。
     */
    static List<SubscribeInfoKind> resolveSubscribeInfoKinds(int method, boolean hasResetDay) {
        return switch (method) {
            case 1 -> List.of(SubscribeInfoKind.EXPIRE);
            case 2 -> List.of(SubscribeInfoKind.TRAFFIC);
            default -> {
                List<SubscribeInfoKind> kinds = new ArrayList<>();
                kinds.add(SubscribeInfoKind.EXPIRE);
                if (hasResetDay) {
                    kinds.add(SubscribeInfoKind.RESET);
                }
                kinds.add(SubscribeInfoKind.TRAFFIC);
                yield kinds;
            }
        };
    }

    enum SubscribeInfoKind {
        EXPIRE,
        RESET,
        TRAFFIC
    }

}

