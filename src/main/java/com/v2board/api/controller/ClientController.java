package com.v2board.api.controller;

import com.v2board.api.model.User;
import com.v2board.api.protocol.ProtocolHandler;
import com.v2board.api.protocol.GeneralHandler;
import com.v2board.api.service.ServerService;
import com.v2board.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.v2board.api.util.Helper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/client")
public class ClientController {
    
    private static final Logger logger = LoggerFactory.getLogger(ClientController.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ServerService serverService;
    
    @Autowired
    private GeneralHandler generalHandler;
    
    @Autowired
    private List<ProtocolHandler> protocolHandlers;
    
    @Value("${v2board.show-info-to-server-enable:false}")
    private Boolean showInfoToServerEnable;
    
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_PLAIN_VALUE)
    public String subscribe(
            @RequestParam(required = false) String flag,
            HttpServletRequest request) {
        
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
            
            // 获取可用服务器
            List<Map<String, Object>> servers = serverService.getAvailableServers(user);
            logger.debug("Found {} available servers for user {}", servers.size(), user.getEmail());
            
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
        
        // 遍历所有协议处理器，查找匹配的
        for (ProtocolHandler handler : protocolHandlers) {
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
     * 设置订阅信息到服务器列表
     * PHP: setSubscribeInfoToServers(&$servers, $user)
     */
    private void setSubscribeInfoToServers(List<Map<String, Object>> servers, User user) {
        if (servers == null || servers.isEmpty()) {
            return;
        }
        
        // 检查是否启用订阅信息显示（从配置读取）
        if (showInfoToServerEnable == null || !showInfoToServerEnable) {
            return;
        }
        
        // 获取第一个服务器作为模板
        Map<String, Object> templateServer = servers.get(0);
        
        // 计算已用流量
        long useTraffic = (user.getU() != null ? user.getU() : 0) + 
                          (user.getD() != null ? user.getD() : 0);
        long totalTraffic = user.getTransferEnable() != null ? user.getTransferEnable() : 0;
        long remainingTraffic = totalTraffic - useTraffic;
        
        // 格式化剩余流量
        String remainingTrafficStr = Helper.trafficConvert(remainingTraffic);
        
        // 格式化到期日期（格式：Y-m-d）
        String expiredDate;
        if (user.getExpiredAt() != null && user.getExpiredAt() > 0) {
            java.time.LocalDate date = java.time.Instant.ofEpochSecond(user.getExpiredAt())
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
            expiredDate = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else {
            expiredDate = "长期有效";
        }
        
        // 获取重置天数
        Integer resetDay = userService.getResetDay(user);
        
        // 在服务器列表前添加信息节点（使用 array_unshift 的逻辑，即添加到列表开头）
        // 注意：PHP中array_unshift是倒序添加的，所以最后添加的会在最前面
        // 1. 套餐到期时间（最后添加，会在最前面）
        Map<String, Object> expireInfo = new HashMap<>(templateServer);
        expireInfo.put("name", "套餐到期：" + expiredDate);
        servers.add(0, expireInfo);
        
        // 2. 重置天数（如果有，倒数第二个添加）
        if (resetDay != null && resetDay > 0) {
            Map<String, Object> resetInfo = new HashMap<>(templateServer);
            resetInfo.put("name", "距离下次重置剩余：" + resetDay + " 天");
            servers.add(0, resetInfo);
        }
        
        // 3. 剩余流量（第一个添加，会在最后面）
        Map<String, Object> trafficInfo = new HashMap<>(templateServer);
        trafficInfo.put("name", "剩余流量：" + remainingTrafficStr);
        servers.add(0, trafficInfo);
        
        logger.debug("Added subscribe info nodes: remaining traffic={}, reset day={}, expired date={}", 
            remainingTrafficStr, resetDay, expiredDate);
    }
    
}

