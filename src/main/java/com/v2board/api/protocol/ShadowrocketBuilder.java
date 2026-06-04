package com.v2board.api.protocol;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对齐 PHP App\Protocols\Shadowrocket
 */
public final class ShadowrocketBuilder {

    private ShadowrocketBuilder() {
    }

    public static String buildStatusLine(com.v2board.api.model.User user) {
        long u = user.getU() != null ? user.getU() : 0;
        long d = user.getD() != null ? user.getD() : 0;
        long total = user.getTransferEnable() != null ? user.getTransferEnable() : 0;
        double upload = roundGb(u);
        double download = roundGb(d);
        double totalGb = roundGb(total);
        String expiredDate = user.getExpiredAt() != null && user.getExpiredAt() > 0
                ? Instant.ofEpochSecond(user.getExpiredAt())
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                : "";
        return "STATUS=🚀↑:" + upload + "GB,↓:" + download + "GB,TOT:" + totalGb + "GB💡Expires:" + expiredDate + "\r\n";
    }

    public static boolean isVmessServer(Map<String, Object> server) {
        if ("vmess".equals(str(server.get("type")))) {
            return true;
        }
        return "v2node".equals(str(server.get("type"))) && "vmess".equals(str(server.get("protocol")));
    }

    @SuppressWarnings("unchecked")
    public static String buildVmess(String uuid, Map<String, Object> server) {
        String userinfo = Base64.getEncoder().encodeToString(
                ("auto:" + uuid + "@" + server.get("host") + ":" + server.get("port")).getBytes(StandardCharsets.UTF_8));

        Map<String, String> config = new LinkedHashMap<>();
        config.put("tfo", "1");
        config.put("remark", str(server.get("name")));
        config.put("alterId", "0");

        if (isTls(server)) {
            config.put("tls", "1");
            Map<String, Object> tlsSettings = tls(server);
            if (tlsSettings != null) {
                int allowInsecure = intVal(tlsSettings.get("allow_insecure"));
                if (allowInsecure == 0) {
                    allowInsecure = intVal(tlsSettings.get("allowInsecure"));
                }
                config.put("allowInsecure", String.valueOf(allowInsecure));
                Object peer = tlsSettings.get("server_name");
                if (peer == null) {
                    peer = tlsSettings.get("serverName");
                }
                if (peer != null) {
                    config.put("peer", str(peer));
                }
            }
        }

        String network = str(server.get("network"));
        Map<String, Object> netSettings = networkSettings(server);

        if ("tcp".equals(network) && netSettings != null) {
            if (netSettings.get("header") instanceof Map<?, ?> header) {
                if (header.get("type") != null && !str(header.get("type")).isEmpty()) {
                    config.put("obfs", str(header.get("type")));
                }
                if (header.get("request") instanceof Map<?, ?> req) {
                    if (req.get("path") instanceof List<?> paths && !paths.isEmpty()) {
                        config.put("path", str(paths.get(0)));
                    }
                    if (req.get("headers") instanceof Map<?, ?> hdr
                            && hdr.get("Host") instanceof List<?> hosts && !hosts.isEmpty()) {
                        config.put("obfsParam", str(hosts.get(0)));
                    }
                }
            }
        }
        if ("ws".equals(network) && netSettings != null) {
            config.put("obfs", "websocket");
            if (netSettings.get("path") != null && !str(netSettings.get("path")).isEmpty()) {
                config.put("path", str(netSettings.get("path")));
            }
            if (netSettings.get("headers") instanceof Map<?, ?> h && h.get("Host") != null && !str(h.get("Host")).isEmpty()) {
                config.put("obfsParam", str(h.get("Host")));
            }
            if (netSettings.get("security") != null) {
                config.put("method", str(netSettings.get("security")));
            }
        }
        if ("grpc".equals(network) && netSettings != null) {
            config.put("obfs", "grpc");
            if (netSettings.get("serviceName") != null && !str(netSettings.get("serviceName")).isEmpty()) {
                config.put("path", str(netSettings.get("serviceName")));
            }
            Map<String, Object> tlsSettings = tls(server);
            if (tlsSettings != null) {
                Object host = tlsSettings.get("server_name");
                if (host == null) {
                    host = tlsSettings.get("serverName");
                }
                config.put("host", host != null ? str(host) : str(server.get("host")));
            } else {
                config.put("host", str(server.get("host")));
            }
        }

        return "vmess://" + userinfo + "?" + toQuery(config) + "\r\n";
    }

    private static String toQuery(Map<String, String> config) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : config.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            parts.add(encode(e.getKey()) + "=" + encode(e.getValue()));
        }
        return String.join("&", parts);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static double roundGb(long bytes) {
        return Math.round(bytes / (1024.0 * 1024.0 * 1024.0) * 100.0) / 100.0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> networkSettings(Map<String, Object> server) {
        if (server.get("network_settings") instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (server.get("networkSettings") instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tls(Map<String, Object> server) {
        if (server.get("tls_settings") instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (server.get("tlsSettings") instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }

    private static boolean isTls(Map<String, Object> server) {
        Object t = server.get("tls");
        if (t instanceof Boolean b) {
            return b;
        }
        return t instanceof Number n && n.intValue() != 0;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int intVal(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }
}
