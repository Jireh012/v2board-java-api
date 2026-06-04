package com.v2board.api.protocol;

import com.v2board.api.util.Helper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对齐 PHP App\Protocols\QuantumultX
 */
public final class QuantumultXBuilder {

    private static final Set<String> SKIP_NETWORKS = Set.of("grpc", "httpupgrade", "xhttp");

    private QuantumultXBuilder() {
    }

    public static String buildPlain(List<Map<String, Object>> servers, String uuid) {
        StringBuilder uri = new StringBuilder();
        for (Map<String, Object> item : servers) {
            Map<String, Object> server = item;
            if ("v2node".equals(str(server.get("type"))) && server.get("protocol") != null) {
                server = new LinkedHashMap<>(item);
                server.put("type", str(server.get("protocol")));
            }
            String network = str(server.get("network"));
            if (SKIP_NETWORKS.contains(network)) {
                continue;
            }
            String type = str(server.get("type"));
            String line = switch (type) {
                case "shadowsocks" -> buildShadowsocks(uuid, server);
                case "vmess" -> buildVmess(uuid, server);
                case "vless" -> buildVless(uuid, server);
                case "trojan" -> buildTrojan(uuid, server);
                case "anytls" -> buildAnyTls(uuid, server);
                default -> "";
            };
            uri.append(line);
        }
        return uri.toString();
    }

    @SuppressWarnings("unchecked")
    public static String buildShadowsocks(String password, Map<String, Object> server) {
        if (server.get("obfs") != null && !str(server.get("obfs")).isEmpty()) {
            server = new LinkedHashMap<>(server);
            server.put("network", server.get("obfs"));
            Map<String, Object> netSettings = networkSettings(server);
            if (netSettings == null) {
                netSettings = new LinkedHashMap<>();
                server.put("network_settings", netSettings);
            }
            if (server.get("obfs_settings") instanceof Map<?, ?> obfs) {
                if (obfs.get("host") != null) {
                    Map<String, Object> headers = new LinkedHashMap<>();
                    headers.put("Host", obfs.get("host"));
                    netSettings.put("headers", headers);
                }
                if (obfs.get("path") != null) {
                    netSettings.put("path", obfs.get("path"));
                }
            }
        }

        String cipher = str(server.get("cipher"));
        if ("2022-blake3-aes-128-gcm".equals(cipher)) {
            password = Helper.getServerKey(longVal(server.get("created_at")), 16) + ":"
                    + Helper.uuidToBase64(password, 16);
        } else if ("2022-blake3-aes-256-gcm".equals(cipher)) {
            password = Helper.getServerKey(longVal(server.get("created_at")), 32) + ":"
                    + Helper.uuidToBase64(password, 32);
        }

        List<String> config = new ArrayList<>();
        config.add("shadowsocks=" + server.get("host") + ":" + server.get("port"));
        config.add("method=" + cipher);
        config.add("password=" + password);

        String network = str(server.get("network"));
        if ("http".equals(network)) {
            config.add("obfs=http");
            Map<String, Object> netSettings = networkSettings(server);
            if (netSettings != null) {
                String host = null;
                String path = null;
                if (netSettings.get("headers") instanceof Map<?, ?> h) {
                    host = str(h.get("Host"));
                }
                if (host == null || host.isEmpty()) {
                    host = str(netSettings.get("Host"));
                }
                path = str(netSettings.get("path"));
                if (!host.isEmpty()) {
                    config.add("obfs-host=" + host);
                }
                if (!path.isEmpty()) {
                    config.add("obfs-uri=" + path);
                }
            }
        }
        config.add("fast-open=false");
        config.add("udp-relay=true");
        config.add("tag=" + server.get("name"));
        return joinConfig(config);
    }

    @SuppressWarnings("unchecked")
    public static String buildVmess(String uuid, Map<String, Object> server) {
        server = normalizeVmess(server);
        List<String> config = new ArrayList<>();
        config.add("vmess=" + server.get("host") + ":" + server.get("port"));
        config.add("method=chacha20-poly1305");
        config.add("password=" + uuid);
        config.add("fast-open=true");
        config.add("udp-relay=true");
        config.add("tag=" + server.get("name"));

        Map<String, Object> tlsSettings = tls(server);
        Map<String, Object> netSettings = networkSettings(server);
        String network = str(server.get("network"));
        boolean isTls = isTls(server);

        if ("ws".equals(network) && netSettings != null && netSettings.get("security") != null
                && !"auto".equals(str(netSettings.get("security")))) {
            for (int i = 0; i < config.size(); i++) {
                if (config.get(i).startsWith("method=")) {
                    config.set(i, "method=" + netSettings.get("security"));
                    break;
                }
            }
        }

        if (isTls) {
            config.add("tls13=true");
            if (tlsSettings != null && truthy(tlsSettings.get("allow_insecure"))) {
                config.add("tls-verification=false");
            }
        }

        if ("ws".equals(network)) {
            config.add(isTls ? "obfs=wss" : "obfs=ws");
        } else if ("tcp".equals(network)) {
            if (isTls) {
                config.add("obfs=over-tls");
            } else if (netSettings != null && netSettings.get("header") instanceof Map<?, ?> header
                    && "http".equals(str(header.get("type")))) {
                config.add("obfs=http");
            }
        }

        String host = null;
        String path = null;
        if ("tcp".equals(network) && netSettings != null && netSettings.get("header") instanceof Map<?, ?> header
                && "http".equals(str(header.get("type"))) && header.get("request") instanceof Map<?, ?> req) {
            if (req.get("headers") instanceof Map<?, ?> hdr && hdr.get("Host") instanceof List<?> hosts && !hosts.isEmpty()) {
                host = str(hosts.get(0));
            }
            if (req.get("path") instanceof List<?> paths && !paths.isEmpty()) {
                path = str(paths.get(0));
            }
        } else if ("ws".equals(network) && netSettings != null) {
            if (netSettings.get("headers") instanceof Map<?, ?> h) {
                host = str(h.get("Host"));
            }
            path = str(netSettings.get("path"));
        }

        if ((host == null || host.isEmpty()) && tlsSettings != null && tlsSettings.get("server_name") != null) {
            host = str(tlsSettings.get("server_name"));
        }
        if (host != null && !host.isEmpty()) {
            config.add("obfs-host=" + host);
        }
        if (path != null && !path.isEmpty()) {
            config.add("obfs-uri=" + path);
        }
        return joinConfig(config);
    }

    @SuppressWarnings("unchecked")
    public static String buildVless(String uuid, Map<String, Object> server) {
        if (server.get("encryption") != null && !str(server.get("encryption")).isEmpty()
                && server.get("encryption_settings") != null && !str(server.get("encryption_settings")).isEmpty()) {
            return "";
        }

        List<String> config = new ArrayList<>();
        config.add("vless=" + server.get("host") + ":" + server.get("port"));
        config.add("method=none");
        config.add("password=" + uuid);
        config.add("udp-relay=true");
        config.add("tag=" + server.get("name"));
        config.add(tlsMode(server) == 2 ? "fast-open=false" : "fast-open=true");

        Map<String, Object> tlsSettings = tls(server);
        Map<String, Object> netSettings = networkSettings(server);
        String network = str(server.get("network"));
        boolean isTls = isTls(server);

        if (isTls) {
            config.add("tls13=true");
            if (tlsSettings != null && truthy(tlsSettings.get("allow_insecure"))) {
                config.add("tls-verification=false");
            }
            if (server.get("flow") != null && !str(server.get("flow")).isEmpty()) {
                config.add("vless-flow=" + server.get("flow"));
            }
            if (tlsMode(server) == 2 && tlsSettings != null) {
                if (tlsSettings.get("public_key") != null) {
                    config.add("reality-base64-pubkey=" + tlsSettings.get("public_key"));
                }
                if (tlsSettings.get("short_id") != null) {
                    config.add("reality-hex-shortid=" + tlsSettings.get("short_id"));
                }
            }
        }

        if ("ws".equals(network)) {
            config.add(isTls ? "obfs=wss" : "obfs=ws");
        } else if ("tcp".equals(network)) {
            if (isTls) {
                config.add("obfs=over-tls");
            } else if (netSettings != null && netSettings.get("header") instanceof Map<?, ?> header
                    && "http".equals(str(header.get("type")))) {
                config.add("obfs=http");
            }
        }

        String host = null;
        String path = null;
        if ("tcp".equals(network) && netSettings != null && netSettings.get("header") instanceof Map<?, ?> header
                && "http".equals(str(header.get("type"))) && header.get("request") instanceof Map<?, ?> req) {
            if (req.get("headers") instanceof Map<?, ?> hdr && hdr.get("Host") instanceof List<?> hosts && !hosts.isEmpty()) {
                host = str(hosts.get(0));
            }
            if (req.get("path") instanceof List<?> paths && !paths.isEmpty()) {
                path = str(paths.get(0));
            }
        } else if ("ws".equals(network) && netSettings != null) {
            if (netSettings.get("headers") instanceof Map<?, ?> h) {
                host = str(h.get("Host"));
            }
            path = str(netSettings.get("path"));
        }
        if ((host == null || host.isEmpty()) && tlsSettings != null && tlsSettings.get("server_name") != null) {
            host = str(tlsSettings.get("server_name"));
        }
        if (host != null && !host.isEmpty()) {
            config.add("obfs-host=" + host);
        }
        if (path != null && !path.isEmpty()) {
            config.add("obfs-uri=" + path);
        }
        return joinConfig(config);
    }

    @SuppressWarnings("unchecked")
    public static String buildTrojan(String password, Map<String, Object> server) {
        server = new LinkedHashMap<>(server);
        Map<String, Object> tlsSettings = tls(server);
        if (tlsSettings == null) {
            tlsSettings = new LinkedHashMap<>();
            server.put("tls_settings", tlsSettings);
        }
        if (server.get("allow_insecure") != null) {
            tlsSettings.put("allow_insecure", truthy(server.get("allow_insecure")));
            server.remove("allow_insecure");
        }
        if (server.get("server_name") != null) {
            tlsSettings.put("server_name", server.get("server_name"));
            server.remove("server_name");
        }

        List<String> config = new ArrayList<>();
        config.add("trojan=" + server.get("host") + ":" + server.get("port"));
        config.add("password=" + password);
        config.add("fast-open=true");
        config.add("udp-relay=true");
        config.add("tag=" + server.get("name"));

        String network = str(server.get("network"));
        String sni = tlsSettings.get("server_name") != null ? str(tlsSettings.get("server_name")) : "";
        boolean allowInsecure = truthy(tlsSettings.get("allow_insecure"));

        if ("tcp".equals(network)) {
            config.add("over-tls=true");
            if (!sni.isEmpty()) {
                config.add("tls-host=" + sni);
            }
            config.add("tls-verification=" + (allowInsecure ? "false" : "true"));
        } else if ("ws".equals(network)) {
            config.add("obfs=wss");
            Map<String, Object> netSettings = networkSettings(server);
            String host = null;
            String path = null;
            if (netSettings != null) {
                if (netSettings.get("headers") instanceof Map<?, ?> h) {
                    host = str(h.get("Host"));
                }
                path = str(netSettings.get("path"));
            }
            if ((host == null || host.isEmpty()) && !sni.isEmpty()) {
                host = sni;
            }
            if (host != null && !host.isEmpty()) {
                config.add("obfs-host=" + host);
            }
            if (path != null && !path.isEmpty()) {
                config.add("obfs-uri=" + path);
            }
            if (allowInsecure) {
                config.add("tls-verification=false");
            }
        }
        return joinConfig(config);
    }

    @SuppressWarnings("unchecked")
    public static String buildAnyTls(String password, Map<String, Object> server) {
        List<String> config = new ArrayList<>();
        config.add("anytls=" + server.get("host") + ":" + server.get("port"));
        config.add("password=" + password);
        config.add("udp-relay=true");
        config.add("tag=" + server.get("name"));

        Map<String, Object> tlsSettings = tls(server);
        String network = str(server.get("network"));
        String sni = tlsSettings != null ? str(tlsSettings.get("server_name")) : "";
        boolean allowInsecure = tlsSettings != null && truthy(tlsSettings.get("allow_insecure"));

        if ("tcp".equals(network)) {
            config.add("over-tls=true");
            if (!sni.isEmpty()) {
                config.add("tls-host=" + sni);
            }
            config.add("tls-verification=" + (allowInsecure ? "false" : "true"));
        }
        return joinConfig(config);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeVmess(Map<String, Object> server) {
        server = new LinkedHashMap<>(server);
        if (server.get("networkSettings") instanceof Map<?, ?> legacy) {
            Map<String, Object> current = networkSettings(server);
            if (current == null) {
                current = new LinkedHashMap<>();
                server.put("network_settings", current);
            }
            mergeRecursive(current, (Map<String, Object>) legacy);
            server.remove("networkSettings");
        }
        if (server.get("tlsSettings") instanceof Map<?, ?> legacyTls) {
            Map<String, Object> currentTls = tls(server);
            if (currentTls == null) {
                currentTls = new LinkedHashMap<>();
                server.put("tls_settings", currentTls);
            }
            Map<String, Object> leg = new LinkedHashMap<>((Map<String, Object>) legacyTls);
            if (leg.get("serverName") != null) {
                leg.put("server_name", leg.get("serverName"));
            }
            if (leg.get("allowInsecure") != null) {
                leg.put("allow_insecure", leg.get("allowInsecure"));
            }
            mergeRecursive(currentTls, leg);
            server.remove("tlsSettings");
        }
        return server;
    }

    private static void mergeRecursive(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> e : source.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> srcMap && target.get(e.getKey()) instanceof Map<?, ?> tgtMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> tgt = (Map<String, Object>) tgtMap;
                @SuppressWarnings("unchecked")
                Map<String, Object> src = (Map<String, Object>) srcMap;
                mergeRecursive(tgt, src);
            } else {
                target.put(e.getKey(), e.getValue());
            }
        }
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

    private static int tlsMode(Map<String, Object> server) {
        Object t = server.get("tls");
        if (t instanceof Number n) {
            return n.intValue();
        }
        return isTls(server) ? 1 : 0;
    }

    private static String joinConfig(List<String> config) {
        List<String> parts = new ArrayList<>();
        for (String s : config) {
            if (s != null && !s.isEmpty()) {
                parts.add(s);
            }
        }
        return String.join(",", parts) + "\r\n";
    }

    private static boolean truthy(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }
        return "true".equalsIgnoreCase(str(o)) || "1".equals(str(o));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static long longVal(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return 0;
    }
}
