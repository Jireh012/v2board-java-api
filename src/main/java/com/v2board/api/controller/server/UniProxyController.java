package com.v2board.api.controller.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.model.ServerHysteria;
import com.v2board.api.model.ServerShadowsocks;
import com.v2board.api.model.ServerTrojan;
import com.v2board.api.model.ServerVless;
import com.v2board.api.model.ServerVmess;
import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import com.v2board.api.service.NodeCacheService;
import com.v2board.api.service.ServerService;
import com.v2board.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/v1/server/UniProxy")
public class UniProxyController {

    @Autowired
    private ServerService serverService;

    @Autowired
    private UserService userService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private NodeCacheService nodeCacheService;

    @Autowired
    private ObjectMapper objectMapper;

    private final ObjectMapper msgpackMapper = new ObjectMapper(new MessagePackFactory());

    @GetMapping("/user")
    public ResponseEntity<byte[]> user(HttpServletRequest request) throws Exception {
        NodeContext ctx = resolveNodeContext(request);

        String lastCheckKey = nodeCacheService.buildServerKey("SERVER_" + ctx.nodeTypeUpper + "_LAST_CHECK_AT",
                ctx.nodeId);
        long now = System.currentTimeMillis() / 1000;
        nodeCacheService.set(lastCheckKey, now, Duration.ofHours(1));

        List<Integer> groupIds = ctx.groupIds;
        List<User> users = userService.getAvailableUsers(groupIds);
        List<Map<String, Object>> userList = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (user.getId() != null)
                m.put("id", user.getId());
            if (user.getUuid() != null)
                m.put("uuid", user.getUuid());
            if (user.getDeviceLimit() != null)
                m.put("device_limit", user.getDeviceLimit());
            if (user.getGroupId() != null)
                m.put("group_id", user.getGroupId());
            if (user.getTransferEnable() != null)
                m.put("transfer_enable", user.getTransferEnable());
            if (user.getU() != null)
                m.put("u", user.getU());
            if (user.getD() != null)
                m.put("d", user.getD());
            if (user.getExpiredAt() != null)
                m.put("expired_at", user.getExpiredAt());
            userList.add(m);
        }

        Map<String, Object> response = Map.of("users", userList);
        String responseFormat = request.getHeader("X-Response-Format");
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);

        if (responseFormat != null && responseFormat.toLowerCase().contains("msgpack")) {
            byte[] body = msgpackMapper.writeValueAsBytes(response);
            String eTag = sha1Hex(body);
            if (ifNoneMatch != null && ifNoneMatch.contains(eTag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .header(HttpHeaders.ETAG, "\"" + eTag + "\"")
                        .build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("application/x-msgpack"))
                    .header(HttpHeaders.ETAG, "\"" + eTag + "\"")
                    .body(body);
        } else {
            byte[] body = objectMapper.writeValueAsBytes(response);
            String eTag = sha1Hex(body);
            if (ifNoneMatch != null && ifNoneMatch.contains(eTag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .header(HttpHeaders.ETAG, "\"" + eTag + "\"")
                        .build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ETAG, "\"" + eTag + "\"")
                    .body(body);
        }
    }

    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> push(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, List<Long>> body) throws Exception {
        NodeContext ctx = resolveNodeContext(request);

        Map<String, List<Long>> data = body != null ? body : new HashMap<>();
        if (data.isEmpty()) {
            throw new BusinessException(400, "Invalid traffic data");
        }

        String onlineKey = nodeCacheService.buildServerKey("SERVER_" + ctx.nodeTypeUpper + "_ONLINE_USER", ctx.nodeId);
        String lastPushKey = nodeCacheService.buildServerKey("SERVER_" + ctx.nodeTypeUpper + "_LAST_PUSH_AT",
                ctx.nodeId);
        long now = System.currentTimeMillis() / 1000;
        nodeCacheService.set(onlineKey, data.size(), Duration.ofHours(1));
        nodeCacheService.set(lastPushKey, now, Duration.ofHours(1));

        double rate = ctx.rate;
        userService.trafficFetch(ctx.nodeId, ctx.nodeType, rate, data);

        return ResponseEntity.ok(Map.of("data", true));
    }

    @GetMapping("/alivelist")
    public ResponseEntity<Map<String, Object>> alivelist() throws Exception {
        String cacheKey = "ALIVE_LIST";
        Object cached = nodeCacheService.get(cacheKey);
        Map<Long, Integer> alive;
        if (cached instanceof Map<?, ?> m) {
            alive = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Object k = e.getKey();
                Object v = e.getValue();
                Long uid = null;
                if (k instanceof Number) {
                    uid = ((Number) k).longValue();
                } else if (k != null) {
                    try {
                        uid = Long.parseLong(k.toString());
                    } catch (NumberFormatException ignore) {
                        // skip
                    }
                }
                if (uid != null && v instanceof Number) {
                    alive.put(uid, ((Number) v).intValue());
                }
            }
        } else {
            List<User> users = userService.getDeviceLimitedUsers();
            if (users.isEmpty()) {
                alive = Collections.emptyMap();
            } else {
                List<String> keys = new ArrayList<>();
                Map<String, Long> idMap = new HashMap<>();
                for (User user : users) {
                    String key = "ALIVE_IP_USER_" + user.getId();
                    keys.add(key);
                    idMap.put(key, user.getId());
                }
                List<Object> results = nodeCacheService.multiGet(keys);
                alive = new LinkedHashMap<>();
                for (int i = 0; i < keys.size(); i++) {
                    String k = keys.get(i);
                    Object data = i < results.size() ? results.get(i) : null;
                    if (data instanceof Map<?, ?> map) {
                        Object aliveIp = map.get("alive_ip");
                        if (aliveIp instanceof Number) {
                            Long userId = idMap.get(k);
                            if (userId != null) {
                                alive.put(userId, ((Number) aliveIp).intValue());
                            }
                        }
                    }
                }
            }
            nodeCacheService.set(cacheKey, alive, Duration.ofSeconds(60));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("alive", alive);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/alive")
    public ResponseEntity<Map<String, Object>> alive(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, List<String>> body) throws Exception {
        NodeContext ctx = resolveNodeContext(request);

        Map<String, List<String>> data = body != null ? body : new HashMap<>();
        if (data.isEmpty()) {
            throw new BusinessException(400, "Invalid online data");
        }
        long updateAt = System.currentTimeMillis() / 1000;

        Map<String, Object> fullConfig = configService.getFullConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) fullConfig.getOrDefault("server",
                Collections.emptyMap());
        int deviceLimitMode = getInt(serverConfig.get("device_limit_mode"), 0);

        String nodeKey = ctx.nodeType + ctx.nodeId;
        for (Map.Entry<String, List<String>> entry : data.entrySet()) {
            Long uid = Long.valueOf(entry.getKey());
            List<String> ips = entry.getValue();
            String key = "ALIVE_IP_USER_" + uid;
            Object existing = nodeCacheService.get(key);
            Map<String, Object> ipsMap;
            if (existing instanceof Map<?, ?> map) {
                ipsMap = new HashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    ipsMap.put(String.valueOf(e.getKey()), e.getValue());
                }
            } else {
                ipsMap = new HashMap<>();
            }

            Map<String, Object> currentNode = new HashMap<>();
            currentNode.put("aliveips", ips);
            currentNode.put("lastupdateAt", updateAt);
            ipsMap.put(nodeKey, currentNode);

            Iterator<Map.Entry<String, Object>> it = ipsMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> e = it.next();
                Object val = e.getValue();
                if (val instanceof Map<?, ?> map) {
                    Object last = map.get("lastupdateAt");
                    if (last instanceof Number num) {
                        if (updateAt - num.longValue() > 100) {
                            it.remove();
                        }
                    }
                }
            }

            int count = 0;
            if (deviceLimitMode == 1) {
                Set<String> ipset = new HashSet<>();
                for (Object val : ipsMap.values()) {
                    if (val instanceof Map<?, ?> map) {
                        Object aliveips = map.get("aliveips");
                        if (aliveips instanceof Collection<?> col) {
                            for (Object ipNode : col) {
                                String s = String.valueOf(ipNode);
                                int idx = s.indexOf('_');
                                String ip = idx > 0 ? s.substring(0, idx) : s;
                                ipset.add(ip);
                            }
                        }
                    }
                }
                count = ipset.size();
            } else {
                for (Object val : ipsMap.values()) {
                    if (val instanceof Map<?, ?> map) {
                        Object aliveips = map.get("aliveips");
                        if (aliveips instanceof Collection<?> col) {
                            count += col.size();
                        }
                    }
                }
            }
            ipsMap.put("alive_ip", count);
            nodeCacheService.set(key, ipsMap, Duration.ofSeconds(120));
        }

        return ResponseEntity.ok(Map.of("data", true));
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config(HttpServletRequest request) throws Exception {
        NodeContext ctx = resolveNodeContext(request);
        Object server = ctx.server;

        Map<String, Object> resp = new LinkedHashMap<>();
        switch (ctx.nodeType) {
            case "shadowsocks" -> {
                if (server instanceof ServerShadowsocks s) {
                    resp.put("server_port", parsePort(s.getPort()));
                    resp.put("cipher", s.getCipher());
                }
            }
            case "vmess" -> {
                if (server instanceof ServerVmess s) {
                    resp.put("server_port", parsePort(s.getPort()));
                    resp.put("network", s.getNetwork());
                    resp.put("networkSettings", parseJsonMap(s.getNetworkSettings()));
                    resp.put("tls", s.getTls());
                }
            }
            case "vless" -> {
                if (server instanceof ServerVless s) {
                    Integer port = s.getServerPort() != null ? s.getServerPort() : s.getPort();
                    resp.put("server_port", port);
                    resp.put("network", s.getNetwork());
                    resp.put("networkSettings", parseJsonMap(s.getNetworkSettings()));
                    resp.put("tls", s.getTls());
                    resp.put("flow", s.getFlow());
                    resp.put("tls_settings", parseJsonMap(s.getTlsSettings()));
                }
            }
            case "trojan" -> {
                if (server instanceof ServerTrojan s) {
                    resp.put("host", s.getHost());
                    resp.put("network", "tcp");
                    resp.put("server_port", parsePort(s.getPort()));
                    resp.put("server_name", s.getServerName());
                }
            }
            case "hysteria" -> {
                if (server instanceof ServerHysteria s) {
                    resp.put("host", s.getHost());
                    resp.put("server_port", parsePort(s.getPort()));
                }
            }
            default -> {
            }
        }

        Map<String, Object> fullConfig = configService.getFullConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) fullConfig.getOrDefault("server",
                Collections.emptyMap());
        int pushInterval = getInt(serverConfig.get("server_push_interval"), 60);
        int pullInterval = getInt(serverConfig.get("server_pull_interval"), 60);
        resp.put("base_config", Map.of(
                "push_interval", pushInterval,
                "pull_interval", pullInterval));

        byte[] body = objectMapper.writeValueAsBytes(resp);
        String eTag = sha1Hex(body);
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (ifNoneMatch != null && ifNoneMatch.contains(eTag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, "\"" + eTag + "\"")
                    .build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + eTag + "\"")
                .body(resp);
    }

    private NodeContext resolveNodeContext(HttpServletRequest request) throws Exception {
        String token = request.getParameter("token");
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(500, "token is null");
        }

        Map<String, Object> full = configService.getFullConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) full.getOrDefault("server", Collections.emptyMap());
        String configuredToken = (String) serverConfig.getOrDefault("server_token", "");
        if (!token.equals(configuredToken)) {
            throw new BusinessException(500, "token is error");
        }

        String nodeType = request.getParameter("node_type");
        if (!StringUtils.hasText(nodeType)) {
            throw new BusinessException(500, "node_type is null");
        }
        if ("v2ray".equals(nodeType))
            nodeType = "vmess";
        if ("hysteria2".equals(nodeType))
            nodeType = "hysteria";

        String nodeIdStr = request.getParameter("node_id");
        if (!StringUtils.hasText(nodeIdStr)) {
            throw new BusinessException(500, "node_id is null");
        }
        Long nodeId;
        try {
            nodeId = Long.valueOf(nodeIdStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(500, "node_id is invalid");
        }

        Object server = serverService.findServer(nodeType, nodeId);
        if (server == null) {
            throw new BusinessException(500, "server is not exist");
        }

        List<Integer> groupIds = new ArrayList<>();
        double rate = 1.0;
        if (server instanceof ServerShadowsocks s) {
            if (s.getGroupId() != null)
                groupIds.addAll(s.getGroupId());
            rate = parseRate(s.getRate());
        } else if (server instanceof ServerVmess s) {
            if (s.getGroupId() != null)
                groupIds.addAll(s.getGroupId());
            rate = parseRate(s.getRate());
        } else if (server instanceof ServerTrojan s) {
            if (s.getGroupId() != null)
                groupIds.addAll(s.getGroupId());
            rate = parseRate(s.getRate());
        } else if (server instanceof ServerHysteria s) {
            if (s.getGroupId() != null)
                groupIds.addAll(s.getGroupId());
            rate = parseRate(s.getRate());
        } else if (server instanceof ServerVless s) {
            if (s.getGroupId() != null) {
                for (Object g : s.getGroupId()) {
                    if (g instanceof Number num) {
                        groupIds.add(num.intValue());
                    } else if (g instanceof String str && !str.isEmpty()) {
                        try {
                            groupIds.add(Integer.parseInt(str));
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }
            }
            rate = parseRate(s.getRate());
        }

        if (groupIds.isEmpty()) {
            throw new BusinessException(500, "server group_id is empty");
        }

        NodeContext ctx = new NodeContext();
        ctx.nodeType = nodeType;
        ctx.nodeTypeUpper = nodeType.toUpperCase(Locale.ROOT);
        ctx.nodeId = nodeId;
        ctx.server = server;
        ctx.groupIds = groupIds;
        ctx.rate = rate;
        return ctx;
    }

    private String sha1Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private int parsePort(String port) {
        if (port == null)
            return 0;
        if (port.contains("-")) {
            String[] parts = port.split("-");
            port = parts[0];
        }
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return map;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private int getInt(Object value, int defaultValue) {
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value instanceof String str && !str.isEmpty()) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignore) {
            }
        }
        return defaultValue;
    }

    private double parseRate(String rate) {
        if (rate == null || rate.isEmpty()) {
            return 1.0;
        }
        try {
            return Double.parseDouble(rate);
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    private static class NodeContext {
        String nodeType;
        String nodeTypeUpper;
        Long nodeId;
        Object server;
        List<Integer> groupIds;
        double rate;
    }
}
