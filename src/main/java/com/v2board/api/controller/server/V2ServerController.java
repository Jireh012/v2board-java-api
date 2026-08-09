package com.v2board.api.controller.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.model.ServerV2node;
import com.v2board.api.service.ServerService;
import com.v2board.api.util.Helper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v2node config payload builder. Classic {@code /api/v2/server/**} mappings are removed;
 * runtime config is served via obfuscated {@code {prefix}/c} on {@link UniProxyController}.
 */
@Component
public class V2ServerController {

    @Autowired
    private ServerService serverService;

    @Autowired
    private ObjectMapper objectMapper;

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

        Map<String, Object> baseConfig = new LinkedHashMap<>();
        Map<String, Object> cfg = serverConfig != null ? serverConfig : Collections.emptyMap();
        baseConfig.put("push_interval", getInt(cfg.get("server_push_interval"), 60));
        baseConfig.put("pull_interval", getInt(cfg.get("server_pull_interval"), 60));
        baseConfig.put("node_report_min_traffic", getInt(cfg.get("server_node_report_min_traffic"), 0));
        baseConfig.put("device_online_min_traffic", getInt(cfg.get("server_device_online_min_traffic"), 0));
        resp.put("base_config", baseConfig);

        List<Integer> routeIds = node.getRouteId();
        if (routeIds != null && !routeIds.isEmpty()) {
            resp.put("routes", serverService.getRoutes(routeIds));
        }
        return resp;
    }

    /** Package-visible for tests. */
    static boolean isValidConfiguredNodeToken(String configuredToken) {
        return StringUtils.hasText(configuredToken) && configuredToken.length() >= 16;
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
}
