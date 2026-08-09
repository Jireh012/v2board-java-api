package com.v2board.api.controller.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.model.ServerAnytls;
import com.v2board.api.model.ServerHysteria;
import com.v2board.api.model.ServerShadowsocks;
import com.v2board.api.model.ServerTrojan;
import com.v2board.api.model.ServerTuic;
import com.v2board.api.model.ServerV2node;
import com.v2board.api.model.ServerVless;
import com.v2board.api.model.ServerVmess;
import com.v2board.api.model.User;
import com.v2board.api.queue.JobDispatcher;
import com.v2board.api.service.ConfigService;
import com.v2board.api.service.NodeCacheService;
import com.v2board.api.service.ServerService;
import com.v2board.api.service.UserService;
import com.v2board.api.util.Helper;
import com.v2board.api.util.NodeSm4Codec;
import com.v2board.api.util.NodeTypeCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * Node API handlers registered dynamically at {@code {server_api_prefix}/{c,u,p,a,l}}.
 * Classic UniProxy paths are intentionally not mapped.
 */
@Component
public class UniProxyController {

    private static final List<String> FORBIDDEN_PLAINTEXT_PARAMS =
            List.of("token", "node_id", "node_type", "k", "i", "t");

    @Autowired
    private ServerService serverService;

    @Autowired
    private UserService userService;

    @Autowired
    private JobDispatcher jobDispatcher;

    @Autowired
    private ConfigService configService;

    @Autowired
    private NodeCacheService nodeCacheService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NodeSm4Codec nodeSm4Codec;

    public ResponseEntity<?> user(HttpServletRequest request) throws Exception {
        NodeContext ctx = resolveNodeContext(request);

        String lastCheckKey = nodeCacheService.buildServerKey("SERVER_" + ctx.nodeTypeUpper + "_LAST_CHECK_AT",
                ctx.nodeId);
        long now = System.currentTimeMillis() / 1000;
        nodeCacheService.set(lastCheckKey, now, Duration.ofHours(1));

        List<Integer> groupIds = ctx.groupIds;
        List<User> users = userService.getAvailableUsers(groupIds);
        List<Map<String, Object>> userList = new ArrayList<>();
        for (User user : users) {
            userList.add(buildUserEntry(user));
        }

        Map<String, Object> response = Map.of("users", userList);
        return encryptedJsonResponse(request, response, ctx.workingKey);
    }

    public ResponseEntity<?> push(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> envelope) throws Exception {
        NodeContext ctx = resolveNodeContext(request);

        Map<String, List<Long>> data = decryptTrafficBody(envelope, ctx.workingKey);
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
        jobDispatcher.dispatchTrafficFetch(rate, data);
        jobDispatcher.dispatchStatUser(rate, data);
        jobDispatcher.dispatchStatServer(rate, ctx.nodeId, ctx.nodeType, data);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(nodeSm4Codec.encryptBody(Map.of("data", true), ctx.workingKey));
    }

    public ResponseEntity<?> alivelist(HttpServletRequest request) throws Exception {
        NodeContext ctx = resolveNodeContext(request);

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
        return encryptedJsonResponse(request, body, ctx.workingKey);
    }

    public ResponseEntity<?> alive(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> envelope) throws Exception {
        NodeContext ctx = resolveNodeContext(request);

        Map<String, List<String>> data = decryptAliveBody(envelope, ctx.workingKey);
        if (data.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(nodeSm4Codec.encryptBody(Map.of("data", true), ctx.workingKey));
        }

        long updateAt = System.currentTimeMillis() / 1000;
        Map<String, Object> fullConfig = configService.getFullConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) fullConfig.getOrDefault("server",
                Collections.emptyMap());
        int deviceLimitMode = getInt(serverConfig.get("device_limit_mode"), 0);

        List<String> cacheKeys = new ArrayList<>();
        for (String uid : data.keySet()) {
            cacheKeys.add("ALIVE_IP_USER_" + uid);
        }
        if (cacheKeys.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(nodeSm4Codec.encryptBody(Map.of("data", true), ctx.workingKey));
        }

        List<Object> cachedList = nodeCacheService.multiGet(cacheKeys);
        Map<String, Object> cachedData = new HashMap<>();
        for (int i = 0; i < cacheKeys.size(); i++) {
            cachedData.put(cacheKeys.get(i), i < cachedList.size() ? cachedList.get(i) : null);
        }

        String nodeKey = ctx.nodeType + ctx.nodeId;
        for (Map.Entry<String, List<String>> entry : data.entrySet()) {
            String uidStr = entry.getKey();
            if (!uidStr.matches("\\d+")) {
                continue;
            }
            List<String> ips = entry.getValue();
            if (ips == null) {
                continue;
            }
            String key = "ALIVE_IP_USER_" + uidStr;
            Map<String, Object> ipsMap = toMutableMap(cachedData.get(key));

            Map<String, Object> currentNode = new HashMap<>();
            currentNode.put("aliveips", ips);
            currentNode.put("lastupdateAt", updateAt);
            ipsMap.put(nodeKey, currentNode);

            ipsMap.entrySet().removeIf(e -> {
                if ("alive_ip".equals(e.getKey())) {
                    return false;
                }
                Object val = e.getValue();
                if (val instanceof Map<?, ?> map) {
                    Object last = map.get("lastupdateAt");
                    if (last instanceof Number num) {
                        return updateAt - num.longValue() > 100;
                    }
                }
                return false;
            });

            int count;
            if (deviceLimitMode == 1) {
                Set<String> ipset = new HashSet<>();
                for (Map.Entry<String, Object> e : ipsMap.entrySet()) {
                    if ("alive_ip".equals(e.getKey())) continue;
                    Object val = e.getValue();
                    if (val instanceof Map<?, ?> map) {
                        Object aliveips = map.get("aliveips");
                        if (aliveips instanceof Collection<?> col) {
                            for (Object ipNode : col) {
                                String s = String.valueOf(ipNode);
                                int idx = s.indexOf('_');
                                ipset.add(idx > 0 ? s.substring(0, idx) : s);
                            }
                        }
                    }
                }
                count = ipset.size();
            } else {
                count = 0;
                for (Map.Entry<String, Object> e : ipsMap.entrySet()) {
                    if ("alive_ip".equals(e.getKey())) continue;
                    Object val = e.getValue();
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

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(nodeSm4Codec.encryptBody(Map.of("data", true), ctx.workingKey));
    }

    public ResponseEntity<?> config(HttpServletRequest request) throws Exception {
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
                    resp.put("version", s.getVersion() != null ? s.getVersion() : 1);
                    resp.put("host", s.getHost());
                    resp.put("server_port", s.getServerPort() != null ? s.getServerPort() : parsePort(s.getPort()));
                    resp.put("server_name", s.getServerName());
                    resp.put("up_mbps", s.getUpMbps() != null ? s.getUpMbps() : 0);
                    resp.put("down_mbps", s.getDownMbps() != null ? s.getDownMbps() : 0);
                    if (Integer.valueOf(1).equals(s.getVersion())) {
                        resp.put("obfs", s.getObfsPassword());
                    } else if (Integer.valueOf(2).equals(s.getVersion())) {
                        boolean ignoreBw = (s.getUpMbps() == null || s.getUpMbps() == 0)
                                && (s.getDownMbps() == null || s.getDownMbps() == 0);
                        resp.put("ignore_client_bandwidth", ignoreBw);
                        resp.put("obfs", s.getObfs());
                        resp.put("obfs-password", s.getObfsPassword());
                    }
                }
            }
            case "tuic" -> {
                if (server instanceof ServerTuic s) {
                    resp.put("server_port", s.getServerPort());
                    resp.put("server_name", s.getServerName());
                    resp.put("congestion_control", s.getCongestionControl());
                    resp.put("zero_rtt_handshake", s.getZeroRttHandshake() != null && s.getZeroRttHandshake() == 1);
                }
            }
            case "anytls" -> {
                if (server instanceof ServerAnytls s) {
                    resp.put("server_port", s.getServerPort());
                    resp.put("server_name", s.getServerName());
                    resp.put("padding_scheme", s.getPaddingScheme());
                }
            }
            case "v2node" -> {
                if (server instanceof ServerV2node s) {
                    resp.putAll(buildV2nodeUniProxyConfig(s));
                }
            }
            default -> {
            }
        }

        List<Integer> routeIds = extractRouteIds(server);
        if (routeIds != null && !routeIds.isEmpty()) {
            resp.put("routes", serverService.getRoutes(routeIds));
        }

        Map<String, Object> fullConfig = configService.getFullConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) fullConfig.getOrDefault("server",
                Collections.emptyMap());
        resp.put("base_config", buildBaseConfig(serverConfig));

        return encryptedJsonResponse(request, resp, ctx.workingKey);
    }

    private ResponseEntity<?> encryptedJsonResponse(HttpServletRequest request, Object businessBody, byte[] workingKey)
            throws Exception {
        byte[] plainBytes = objectMapper.writeValueAsBytes(businessBody);
        String eTag = sha1Hex(plainBytes);
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (ifNoneMatch != null && ifNoneMatch.contains(eTag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, "\"" + eTag + "\"")
                    .build();
        }
        Map<String, String> envelope = nodeSm4Codec.encryptBody(businessBody, workingKey);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ETAG, "\"" + eTag + "\"")
                .body(envelope);
    }

    private Map<String, List<Long>> decryptTrafficBody(Map<String, Object> envelope, byte[] workingKey) {
        if (envelope == null || envelope.isEmpty()) {
            return new HashMap<>();
        }
        // Plain business map (legacy) is rejected — require SM4 envelope.
        if (!envelope.containsKey("iv") || !envelope.containsKey("payload")) {
            throw new BusinessException(400, "Invalid traffic data");
        }
        try {
            String json = nodeSm4Codec.decryptBodyToJson(envelope, workingKey);
            return objectMapper.readValue(json, new TypeReference<Map<String, List<Long>>>() {});
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "Invalid traffic data");
        }
    }

    private Map<String, List<String>> decryptAliveBody(Map<String, Object> envelope, byte[] workingKey) {
        if (envelope == null || envelope.isEmpty()) {
            return new HashMap<>();
        }
        if (!envelope.containsKey("iv") || !envelope.containsKey("payload")) {
            throw new BusinessException(400, "Invalid alive data");
        }
        try {
            String json = nodeSm4Codec.decryptBodyToJson(envelope, workingKey);
            return objectMapper.readValue(json, new TypeReference<Map<String, List<String>>>() {});
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "Invalid alive data");
        }
    }

    private NodeContext resolveNodeContext(HttpServletRequest request) throws Exception {
        rejectPlaintextIdentityParams(request);

        String e = request.getParameter("e");
        if (!StringUtils.hasText(e)) {
            throw new BusinessException(500, "token is null");
        }

        Map<String, Object> full = configService.getFullConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) full.getOrDefault("server", Collections.emptyMap());
        String configuredToken = configuredServerToken(serverConfig);
        if (!isValidConfiguredNodeToken(configuredToken)) {
            throw new BusinessException(500, "token is error");
        }

        byte[] workingKey = NodeSm4Codec.deriveWorkingKey(configuredToken);
        NodeSm4Codec.NodeIdentity identity;
        try {
            identity = nodeSm4Codec.decryptIdentityQuery(e.trim(), workingKey);
        } catch (Exception ex) {
            throw new BusinessException(500, "token is error");
        }
        if (!configuredToken.equals(identity.k())) {
            throw new BusinessException(500, "token is error");
        }

        String nodeType = NodeTypeCodes.toNodeType(identity.typeCode());
        if (!StringUtils.hasText(nodeType)) {
            throw new BusinessException(500, "node_type is null");
        }

        Long nodeId = identity.nodeId();
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
            addGroupIds(groupIds, s.getGroupId());
            rate = parseRate(s.getRate());
        } else if (server instanceof ServerTuic s) {
            addGroupIds(groupIds, s.getGroupId());
            rate = parseRate(s.getRate());
        } else if (server instanceof ServerAnytls s) {
            addGroupIds(groupIds, s.getGroupId());
            rate = parseRate(s.getRate());
        } else if (server instanceof ServerV2node s) {
            addGroupIds(groupIds, s.getGroupId());
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
        ctx.workingKey = workingKey;
        return ctx;
    }

    /** Package-visible for tests — plaintext identity query must be rejected. */
    static void rejectPlaintextIdentityParams(HttpServletRequest request) {
        for (String name : FORBIDDEN_PLAINTEXT_PARAMS) {
            if (StringUtils.hasText(request.getParameter(name))) {
                throw new BusinessException(500, "token is error");
            }
        }
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

    /**
     * Package-visible for tests — UniProxy user payload for v2node {@code UserInfo}
     * ({@code id}, {@code uuid}, {@code speed_limit}, {@code device_limit}) plus PHP-compatible extras.
     */
    Map<String, Object> buildUserEntry(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (user == null) {
            return m;
        }
        if (user.getId() != null)
            m.put("id", user.getId());
        if (user.getUuid() != null)
            m.put("uuid", user.getUuid());
        if (user.getSpeedLimit() != null)
            m.put("speed_limit", user.getSpeedLimit());
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
        return m;
    }

    /** Package-visible for tests — UniProxy/v2node base_config contract. */
    Map<String, Object> buildBaseConfig(Map<String, Object> serverConfig) {
        Map<String, Object> base = new LinkedHashMap<>();
        Map<String, Object> cfg = serverConfig != null ? serverConfig : Collections.emptyMap();
        base.put("push_interval", getInt(cfg.get("server_push_interval"), 60));
        base.put("pull_interval", getInt(cfg.get("server_pull_interval"), 60));
        base.put("node_report_min_traffic", getInt(cfg.get("server_node_report_min_traffic"), 0));
        base.put("device_online_min_traffic", getInt(cfg.get("server_device_online_min_traffic"), 0));
        return base;
    }

    static boolean isValidConfiguredNodeToken(String configuredToken) {
        return StringUtils.hasText(configuredToken) && configuredToken.length() >= 16;
    }

    private static String configuredServerToken(Map<String, Object> serverConfig) {
        Object raw = serverConfig != null ? serverConfig.get("server_token") : null;
        return raw == null ? "" : String.valueOf(raw);
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

    private void addGroupIds(List<Integer> groupIds, List<?> serverGroupIds) {
        if (serverGroupIds == null) return;
        for (Object g : serverGroupIds) {
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMutableMap(Object existing) {
        Map<String, Object> ipsMap = new HashMap<>();
        if (existing instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                ipsMap.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return ipsMap;
    }

    private List<Integer> extractRouteIds(Object server) {
        if (server instanceof ServerShadowsocks s) return s.getRouteId();
        if (server instanceof ServerVmess s) return s.getRouteId();
        if (server instanceof ServerTrojan s) return s.getRouteId();
        if (server instanceof ServerHysteria s) return s.getRouteId();
        if (server instanceof ServerVless s) return s.getRouteId();
        if (server instanceof ServerTuic s) return s.getRouteId();
        if (server instanceof ServerAnytls s) return s.getRouteId();
        if (server instanceof ServerV2node s) return s.getRouteId();
        return null;
    }

    private Map<String, Object> buildV2nodeUniProxyConfig(ServerV2node s) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("listen_ip", s.getListenIp());
        resp.put("server_port", s.getServerPort());
        resp.put("network", s.getNetwork());
        resp.put("network_settings", parseJsonMap(s.getNetworkSettings()));
        resp.put("trusted_x_forwarded_for", s.getTrustedXForwardedFor());
        resp.put("protocol", s.getProtocol());
        resp.put("tls", s.getTls());
        resp.put("tls_settings", parseJsonMap(s.getTlsSettings()));
        resp.put("encryption", s.getEncryption());
        resp.put("encryption_settings", parseJsonMap(s.getEncryptionSettings()));
        resp.put("flow", s.getFlow());
        resp.put("cipher", s.getCipher());
        resp.put("congestion_control", s.getCongestionControl());
        resp.put("zero_rtt_handshake", s.getZeroRttHandshake() != null && s.getZeroRttHandshake() == 1);
        resp.put("up_mbps", s.getUpMbps());
        resp.put("down_mbps", s.getDownMbps());
        resp.put("obfs", s.getObfs());
        resp.put("obfs_password", s.getObfsPassword());
        resp.put("padding_scheme", s.getPaddingScheme());
        String cipher = s.getCipher();
        if ("2022-blake3-aes-128-gcm".equals(cipher) && s.getCreatedAt() != null) {
            resp.put("server_key", Helper.getServerKey(s.getCreatedAt(), 16));
        } else if ("2022-blake3-aes-256-gcm".equals(cipher) && s.getCreatedAt() != null) {
            resp.put("server_key", Helper.getServerKey(s.getCreatedAt(), 32));
        }
        int up = s.getUpMbps() != null ? s.getUpMbps() : 0;
        int down = s.getDownMbps() != null ? s.getDownMbps() : 0;
        resp.put("ignore_client_bandwidth", up == 0 && down == 0);
        return resp;
    }

    private static class NodeContext {
        String nodeType;
        String nodeTypeUpper;
        Long nodeId;
        Object server;
        List<Integer> groupIds;
        double rate;
        byte[] workingKey;
    }
}
