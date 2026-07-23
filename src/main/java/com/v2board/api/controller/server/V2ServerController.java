package com.v2board.api.controller.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.model.ServerV2node;
import com.v2board.api.service.ConfigService;
import com.v2board.api.service.ServerService;
import com.v2board.api.util.Helper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对齐 PHP App\Http\Controllers\V2\Server\ServerController
 * 专供 v2node 后端拉取节点配置。
 */
@RestController
@RequestMapping("/api/v2/server")
public class V2ServerController {

    @Autowired
    private ServerService serverService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private ObjectMapper objectMapper;

    @RequestMapping(value = "/config", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<Map<String, Object>> config(HttpServletRequest request) throws Exception {
        String token = request.getParameter("token");
        if (!StringUtils.hasText(token)) {
            return fail("token is null");
        }

        Map<String, Object> full = configService.getFullConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) full.getOrDefault("server", Collections.emptyMap());
        String configuredToken = (String) serverConfig.getOrDefault("server_token", "");
        if (!token.equals(configuredToken)) {
            return fail("token is error");
        }

        String nodeIdStr = request.getParameter("node_id");
        if (!StringUtils.hasText(nodeIdStr)) {
            return fail("node_id is null");
        }
        Long nodeId;
        try {
            nodeId = Long.valueOf(nodeIdStr);
        } catch (NumberFormatException e) {
            return fail("node_id is invalid");
        }

        Object serverObj = serverService.findServer("v2node", nodeId);
        if (!(serverObj instanceof ServerV2node node)) {
            return fail("server is not exist");
        }

        Map<String, Object> resp = buildV2nodeConfig(node, serverConfig);

        byte[] body = objectMapper.writeValueAsBytes(resp);
        String eTag = sha1Hex(body);
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (ifNoneMatch != null && (ifNoneMatch.equals(eTag) || ifNoneMatch.contains(eTag))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, "\"" + eTag + "\"")
                    .build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + eTag + "\"")
                .body(resp);
    }

    Map<String, Object> buildV2nodeConfig(ServerV2node node, Map<String, Object> serverConfig) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("listen_ip", node.getListenIp());
        resp.put("server_port", node.getServerPort());
        resp.put("network", node.getNetwork());
        resp.put("network_settings", parseJsonMap(node.getNetworkSettings()));
        resp.put("trusted_x_forwarded_for", node.getTrustedXForwardedFor());
        resp.put("protocol", node.getProtocol());
        resp.put("tls", node.getTls());
        resp.put("tls_settings", parseJsonMap(node.getTlsSettings()));
        resp.put("encryption", node.getEncryption());
        resp.put("encryption_settings", parseJsonMap(node.getEncryptionSettings()));
        resp.put("flow", node.getFlow());
        resp.put("cipher", node.getCipher());
        resp.put("congestion_control", node.getCongestionControl());
        resp.put("zero_rtt_handshake", node.getZeroRttHandshake() != null && node.getZeroRttHandshake() == 1);
        resp.put("up_mbps", node.getUpMbps());
        resp.put("down_mbps", node.getDownMbps());
        resp.put("obfs", node.getObfs());
        resp.put("obfs_password", node.getObfsPassword());
        resp.put("padding_scheme", node.getPaddingScheme());

        String cipher = node.getCipher();
        if ("2022-blake3-aes-128-gcm".equals(cipher) && node.getCreatedAt() != null) {
            resp.put("server_key", Helper.getServerKey(node.getCreatedAt(), 16));
        } else if ("2022-blake3-aes-256-gcm".equals(cipher) && node.getCreatedAt() != null) {
            resp.put("server_key", Helper.getServerKey(node.getCreatedAt(), 32));
        }

        int up = node.getUpMbps() != null ? node.getUpMbps() : 0;
        int down = node.getDownMbps() != null ? node.getDownMbps() : 0;
        resp.put("ignore_client_bandwidth", up == 0 && down == 0);

        resp.put("base_config", Map.of(
                "push_interval", getInt(serverConfig.get("server_push_interval"), 60),
                "pull_interval", getInt(serverConfig.get("server_pull_interval"), 60),
                "node_report_min_traffic", getInt(serverConfig.get("server_node_report_min_traffic"), 0),
                "device_online_min_traffic", getInt(serverConfig.get("server_device_online_min_traffic"), 0)
        ));

        List<Integer> routeIds = node.getRouteId();
        if (routeIds != null && !routeIds.isEmpty()) {
            resp.put("routes", serverService.getRoutes(routeIds));
        }
        return resp;
    }

    private ResponseEntity<Map<String, Object>> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "fail");
        body.put("message", message);
        return ResponseEntity.ok(body);
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
}
