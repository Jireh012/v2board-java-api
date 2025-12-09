package com.v2board.api.protocol;

import com.v2board.api.model.User;
import com.v2board.api.util.Helper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class GeneralHandler implements ProtocolHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GeneralHandler.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public String getFlag() {
        return "general";
    }
    
    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        if (servers == null || servers.isEmpty()) {
            logger.warn("No servers provided for subscription");
            return "";
        }
        
        StringBuilder uri = new StringBuilder();
        
        for (Map<String, Object> server : servers) {
            try {
                String type = (String) server.get("type");
                if (type == null) {
                    logger.warn("Server type is null, skipping server: {}", server.get("name"));
                    continue;
                }
                
                String uuid = user.getUuid();
                if (uuid == null || uuid.isEmpty()) {
                    logger.warn("User UUID is null or empty");
                    continue;
                }
                
                switch (type) {
                    case "vmess":
                        uri.append(buildVmess(uuid, server));
                        break;
                    case "vless":
                        uri.append(buildVless(uuid, server));
                        break;
                    case "shadowsocks":
                        uri.append(buildShadowsocks(uuid, server));
                        break;
                    case "trojan":
                        uri.append(buildTrojan(uuid, server));
                        break;
                    default:
                        logger.debug("Unknown server type: {}, skipping", type);
                        break;
                }
            } catch (Exception e) {
                logger.error("Error building URI for server: {}", server.get("name"), e);
            }
        }
        
        if (uri.length() == 0) {
            logger.warn("No valid server URIs generated");
            return "";
        }
        
        // Base64 编码
        String result = Base64.getEncoder().encodeToString(uri.toString().getBytes(StandardCharsets.UTF_8));
        logger.debug("Generated {} bytes of subscribe content", result.length());
        return result;
    }
    
    /**
     * 构建 VMess URI
     */
    @SuppressWarnings("unchecked")
    private String buildVmess(String uuid, Map<String, Object> server) {
        try {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("v", "2");
            config.put("ps", server.get("name"));
            config.put("add", server.get("host"));
            config.put("port", String.valueOf(server.get("port")));
            config.put("id", uuid);
            config.put("aid", "0");
            config.put("net", server.get("network"));
            config.put("type", "none");
            config.put("host", "");
            config.put("path", "");
            
            Boolean tls = (Boolean) server.get("tls");
            config.put("tls", tls != null && tls ? "tls" : "");
            
            // 处理 TLS 设置
            if (tls != null && tls) {
                Map<String, Object> tlsSettings = (Map<String, Object>) server.get("tlsSettings");
                if (tlsSettings != null && tlsSettings.get("serverName") != null) {
                    config.put("sni", tlsSettings.get("serverName"));
                }
            }
            
            // 处理网络设置
            String network = (String) server.get("network");
            Map<String, Object> networkSettings = (Map<String, Object>) server.get("networkSettings");
            
            if ("tcp".equals(network) && networkSettings != null) {
                Map<String, Object> header = (Map<String, Object>) networkSettings.get("header");
                if (header != null) {
                    if (header.get("type") != null) {
                        config.put("type", header.get("type"));
                    }
                    Map<String, Object> request = (Map<String, Object>) header.get("request");
                    if (request != null) {
                        Map<String, Object> pathObj = (Map<String, Object>) request.get("path");
                        if (pathObj != null) {
                            List<String> paths = (List<String>) pathObj.get("path");
                            if (paths != null && !paths.isEmpty()) {
                                config.put("path", paths.get(0));
                            }
                        }
                    }
                }
            }
            
            if ("ws".equals(network) && networkSettings != null) {
                if (networkSettings.get("path") != null) {
                    config.put("path", networkSettings.get("path"));
                }
                Map<String, Object> headers = (Map<String, Object>) networkSettings.get("headers");
                if (headers != null && headers.get("Host") != null) {
                    config.put("host", headers.get("Host"));
                }
            }
            
            if ("grpc".equals(network) && networkSettings != null) {
                if (networkSettings.get("serviceName") != null) {
                    config.put("path", networkSettings.get("serviceName"));
                }
            }
            
            // 转换为 JSON 并编码
            String json = objectMapper.writeValueAsString(config);
            return "vmess://" + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)) + "\r\n";
        } catch (Exception e) {
            throw new RuntimeException("构建 VMess URI 失败", e);
        }
    }
    
    /**
     * 构建 Shadowsocks URI
     */
    private String buildShadowsocks(String password, Map<String, Object> server) {
        try {
            String cipher = (String) server.get("cipher");
            String host = (String) server.get("host");
            int port = (Integer) server.get("port");
            String name = (String) server.get("name");
            
            // 处理 2022 加密方式
            if ("2022-blake3-aes-128-gcm".equals(cipher) || 
                "2022-blake3-aes-256-gcm".equals(cipher)) {
                // createdAt 是 Long 类型（Unix 时间戳）
                Long createdAtObj = (Long) server.get("created_at");
                long createdAt = createdAtObj != null ? createdAtObj : System.currentTimeMillis() / 1000;
                int keyLength = "2022-blake3-aes-128-gcm".equals(cipher) ? 16 : 32;
                String serverKey = Helper.getServerKey(createdAt, keyLength);
                String userKey = Helper.uuidToBase64(password, keyLength);
                password = serverKey + ":" + userKey;
            }
            
            String encoded = Base64.getEncoder()
                .encodeToString((cipher + ":" + password).getBytes(StandardCharsets.UTF_8))
                .replace("+", "-")
                .replace("/", "_")
                .replace("=", "");
            
            name = URLEncoder.encode(name, StandardCharsets.UTF_8);
            
            return String.format("ss://%s@%s:%d#%s\r\n", encoded, host, port, name);
        } catch (Exception e) {
            throw new RuntimeException("构建 Shadowsocks URI 失败", e);
        }
    }
    
    /**
     * 构建 VLESS URI
     * VLESS 格式：vless://uuid@host:port?params#name
     * PHP: Helper::buildVlessUri($uuid, $server)
     */
    private String buildVless(String uuid, Map<String, Object> server) {
        try {
            String host = (String) server.get("host");
            int port = (Integer) server.get("port");
            String name = (String) server.get("name");
            String network = (String) server.getOrDefault("network", "tcp");
            
            // 获取TLS设置
            Map<String, Object> tlsSettings = null;
            Object tlsSettingsObj = server.get("tlsSettings");
            if (tlsSettingsObj == null) {
                tlsSettingsObj = server.get("tls_settings");
            }
            if (tlsSettingsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> temp = (Map<String, Object>) tlsSettingsObj;
                tlsSettings = temp;
            }
            
            // 获取TLS值（可能是Boolean或Integer：0=none, 1=tls, 2=reality）
            Object tlsObj = server.get("tls");
            Integer tls = null;
            if (tlsObj instanceof Boolean) {
                tls = ((Boolean) tlsObj) ? 1 : 0;
            } else if (tlsObj instanceof Integer) {
                tls = (Integer) tlsObj;
            } else if (tlsObj instanceof Number) {
                tls = ((Number) tlsObj).intValue();
            }
            
            // 构建基础配置参数
            Map<String, String> params = new LinkedHashMap<>();
            params.put("type", network);
            params.put("encryption", "none");
            params.put("host", "");
            params.put("path", "");
            params.put("headerType", "none");
            params.put("quicSecurity", "none");
            params.put("serviceName", "");
            
            // 处理security字段：tls=0时为空，tls=1时为"tls"，tls=2时为"reality"
            if (tls == null || tls == 0) {
                params.put("security", "");
            } else if (tls == 2) {
                params.put("security", "reality");
            } else {
                params.put("security", "tls");
            }
            
            // 处理flow字段
            Object flowObj = server.get("flow");
            if (flowObj != null) {
                params.put("flow", String.valueOf(flowObj));
            }
            
            // 处理fingerprint字段
            String fingerprint = "chrome";
            if (tlsSettings != null && tlsSettings.get("fingerprint") != null) {
                fingerprint = String.valueOf(tlsSettings.get("fingerprint"));
            }
            params.put("fp", fingerprint);
            
            // 处理insecure字段
            Integer insecure = 0;
            if (tlsSettings != null && tlsSettings.get("allow_insecure") != null) {
                Object insecureObj = tlsSettings.get("allow_insecure");
                if (insecureObj instanceof Number) {
                    insecure = ((Number) insecureObj).intValue();
                } else if (insecureObj instanceof Boolean) {
                    insecure = ((Boolean) insecureObj) ? 1 : 0;
                }
            }
            params.put("insecure", String.valueOf(insecure));
            
            // 处理TLS相关设置
            if (tls != null && tls != 0) {
                if (tlsSettings != null) {
                    // SNI
                    Object serverName = tlsSettings.get("server_name");
                    if (serverName == null) {
                        serverName = tlsSettings.get("serverName");
                    }
                    if (serverName != null) {
                        params.put("sni", String.valueOf(serverName));
                    }
                    
                    // Reality相关参数（tls=2时）
                    if (tls == 2) {
                        Object publicKey = tlsSettings.get("public_key");
                        if (publicKey == null) {
                            publicKey = tlsSettings.get("publicKey");
                        }
                        if (publicKey != null) {
                            params.put("pbk", String.valueOf(publicKey));
                        }
                        
                        Object shortId = tlsSettings.get("short_id");
                        if (shortId == null) {
                            shortId = tlsSettings.get("shortId");
                        }
                        if (shortId != null) {
                            params.put("sid", String.valueOf(shortId));
                        }
                    }
                }
            }
            
            // 处理encryption字段（mlkem768x25519plus）
            Object encryption = server.get("encryption");
            if (encryption != null && "mlkem768x25519plus".equals(String.valueOf(encryption))) {
                Map<String, Object> encSettings = null;
                Object encSettingsObj = server.get("encryption_settings");
                if (encSettingsObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> temp = (Map<String, Object>) encSettingsObj;
                    encSettings = temp;
                }
                if (encSettings != null) {
                    String mode = (String) encSettings.getOrDefault("mode", "native");
                    String rtt = (String) encSettings.getOrDefault("rtt", "1rtt");
                    String enc = "mlkem768x25519plus." + mode + "." + rtt;
                    
                    Object clientPadding = encSettings.get("client_padding");
                    if (clientPadding != null && !String.valueOf(clientPadding).isEmpty()) {
                        enc += "." + clientPadding;
                    }
                    
                    Object password = encSettings.get("password");
                    if (password != null) {
                        enc += "." + password;
                    }
                    
                    params.put("encryption", enc);
                }
            }
            
            // 配置网络设置
            configureNetworkSettings(server, params);
            
            // 构建查询字符串（过滤空值）
            StringBuilder query = new StringBuilder();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String value = entry.getValue();
                // 跳过空字符串（但保留"0"等有效值）
                if (value == null || (value.isEmpty() && !"security".equals(entry.getKey()))) {
                    continue;
                }
                if (query.length() > 0) {
                    query.append("&");
                }
                query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                     .append("=")
                     .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
            
            // 格式化host（IPv6需要加[]）
            host = formatHost(host);
            name = URLEncoder.encode(name, StandardCharsets.UTF_8);
            
            return String.format("vless://%s@%s:%d?%s#%s\r\n", uuid, host, port, query.toString(), name);
        } catch (Exception e) {
            logger.error("Error building VLESS URI for server: {}", server.get("name"), e);
            throw new RuntimeException("构建 VLESS URI 失败", e);
        }
    }
    
    /**
     * 格式化host地址（IPv6需要加[]）
     * PHP: Helper::formatHost($host)
     */
    private String formatHost(String host) {
        if (host == null) {
            return "";
        }
        // 简单判断是否为IPv6（包含冒号）
        if (host.contains(":") && !host.startsWith("[")) {
            return "[" + host + "]";
        }
        return host;
    }
    
    /**
     * 配置网络设置
     * PHP: Helper::configureNetworkSettings($server, &$config)
     */
    @SuppressWarnings("unchecked")
    private void configureNetworkSettings(Map<String, Object> server, Map<String, String> config) {
        String network = (String) server.getOrDefault("network", "tcp");
        Map<String, Object> networkSettings = (Map<String, Object>) server.get("networkSettings");
        if (networkSettings == null) {
            networkSettings = (Map<String, Object>) server.get("network_settings");
        }
        
        if (networkSettings == null) {
            return;
        }
        
        switch (network) {
            case "tcp":
                configureTcpSettings(networkSettings, config);
                break;
            case "ws":
                configureWsSettings(networkSettings, config);
                break;
            case "grpc":
                configureGrpcSettings(networkSettings, config);
                break;
            case "kcp":
                configureKcpSettings(networkSettings, config);
                break;
            case "httpupgrade":
                configureHttpupgradeSettings(networkSettings, config);
                break;
            case "xhttp":
                configureXhttpSettings(networkSettings, config);
                break;
        }
    }
    
    /**
     * 配置TCP设置
     * PHP: Helper::configureTcpSettings($settings, &$config)
     */
    @SuppressWarnings("unchecked")
    private void configureTcpSettings(Map<String, Object> settings, Map<String, String> config) {
        Map<String, Object> header = (Map<String, Object>) settings.get("header");
        if (header != null && "http".equals(header.get("type"))) {
            config.put("headerType", "http");
            Map<String, Object> request = (Map<String, Object>) header.get("request");
            if (request != null) {
                Map<String, Object> headers = (Map<String, Object>) request.get("headers");
                if (headers != null) {
                    Object hostObj = headers.get("Host");
                    if (hostObj != null) {
                        if (hostObj instanceof List && !((List<?>) hostObj).isEmpty()) {
                            config.put("host", String.valueOf(((List<?>) hostObj).get(0)));
                        } else {
                            config.put("host", String.valueOf(hostObj));
                        }
                    }
                }
                Object pathObj = request.get("path");
                if (pathObj != null) {
                    if (pathObj instanceof List && !((List<?>) pathObj).isEmpty()) {
                        config.put("path", String.valueOf(((List<?>) pathObj).get(0)));
                    } else {
                        config.put("path", String.valueOf(pathObj));
                    }
                }
            }
        }
    }
    
    /**
     * 配置WebSocket设置
     * PHP: Helper::configureWsSettings($settings, &$config)
     */
    @SuppressWarnings("unchecked")
    private void configureWsSettings(Map<String, Object> settings, Map<String, String> config) {
        if (settings.get("path") != null) {
            config.put("path", String.valueOf(settings.get("path")));
        }
        Map<String, Object> headers = (Map<String, Object>) settings.get("headers");
        if (headers != null && headers.get("Host") != null) {
            config.put("host", String.valueOf(headers.get("Host")));
        }
    }
    
    /**
     * 配置gRPC设置
     * PHP: Helper::configureGrpcSettings($settings, &$config)
     */
    private void configureGrpcSettings(Map<String, Object> settings, Map<String, String> config) {
        if (settings.get("serviceName") != null) {
            config.put("serviceName", String.valueOf(settings.get("serviceName")));
        }
    }
    
    /**
     * 配置KCP设置
     * PHP: Helper::configureKcpSettings($settings, &$config)
     */
    @SuppressWarnings("unchecked")
    private void configureKcpSettings(Map<String, Object> settings, Map<String, String> config) {
        Map<String, Object> header = (Map<String, Object>) settings.get("header");
        if (header != null) {
            Object type = header.get("type");
            config.put("headerType", type != null ? String.valueOf(type) : "none");
        } else {
            config.put("headerType", "none");
        }
        if (settings.get("seed") != null) {
            config.put("seed", String.valueOf(settings.get("seed")));
        }
    }
    
    /**
     * 配置HTTP Upgrade设置
     * PHP: Helper::configureHttpupgradeSettings($settings, &$config)
     */
    private void configureHttpupgradeSettings(Map<String, Object> settings, Map<String, String> config) {
        if (settings.get("path") != null) {
            config.put("path", String.valueOf(settings.get("path")));
        }
        if (settings.get("host") != null) {
            config.put("host", String.valueOf(settings.get("host")));
        }
    }
    
    /**
     * 配置XHTTP设置
     * PHP: Helper::configureXhttpSettings($settings, &$config)
     */
    private void configureXhttpSettings(Map<String, Object> settings, Map<String, String> config) {
        if (settings.get("path") != null) {
            config.put("path", String.valueOf(settings.get("path")));
        }
        if (settings.get("host") != null) {
            config.put("host", String.valueOf(settings.get("host")));
        }
        if (settings.get("mode") != null) {
            config.put("mode", String.valueOf(settings.get("mode")));
        } else {
            config.put("mode", "auto");
        }
        if (settings.get("extra") != null) {
            try {
                String extraJson = objectMapper.writeValueAsString(settings.get("extra"));
                config.put("extra", extraJson);
            } catch (Exception e) {
                logger.warn("Error serializing extra field", e);
            }
        }
    }
    
    /**
     * 构建 Trojan URI
     */
    private String buildTrojan(String password, Map<String, Object> server) {
        try {
            String host = (String) server.get("host");
            int port = (Integer) server.get("port");
            String serverName = (String) server.get("server_name");
            Boolean allowInsecure = (Boolean) server.getOrDefault("allow_insecure", false);
            String name = (String) server.get("name");
            
            name = URLEncoder.encode(name, StandardCharsets.UTF_8);
            
            String query = String.format("allowInsecure=%s&peer=%s&sni=%s",
                allowInsecure, serverName, serverName);
            
            return String.format("trojan://%s@%s:%d?%s#%s\r\n", 
                password, host, port, query, name);
        } catch (Exception e) {
            throw new RuntimeException("构建 Trojan URI 失败", e);
        }
    }
}

