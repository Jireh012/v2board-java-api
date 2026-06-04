package com.v2board.api.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.model.User;
import com.v2board.api.util.Helper;

import java.io.InputStream;
import java.util.*;

/**
 * 对齐 PHP App\Protocols\Singbox\Singbox / SingboxOld
 */
public final class SingboxBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SingboxBuilder() {
    }

    @SuppressWarnings("unchecked")
    public static String build(User user, List<Map<String, Object>> servers, String templateResource, boolean includeAnytls) {
        try {
            Map<String, Object> config = loadTemplate(templateResource);
            List<Map<String, Object>> proxies = buildProxies(servers, user.getUuid(), includeAnytls);
            List<Map<String, Object>> outbounds = addProxies(config, proxies);
            config.put("outbounds", outbounds);
            return MAPPER.writeValueAsString(config);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> buildProxies(List<Map<String, Object>> servers, String uuid, boolean includeAnytls) {
        List<Map<String, Object>> proxies = new ArrayList<>();
        for (Map<String, Object> item : servers) {
            Map<String, Object> server = new LinkedHashMap<>(item);
            if ("v2node".equals(str(server.get("type"))) && server.get("protocol") != null) {
                server.put("type", str(server.get("protocol")));
            }
            Map<String, Object> node = switch (str(server.get("type"))) {
                case "shadowsocks" -> buildShadowsocks(uuid, server);
                case "trojan" -> buildTrojan(uuid, server);
                case "vmess" -> buildVmess(uuid, server);
                case "vless" -> buildVless(uuid, server);
                case "tuic" -> buildTuic(uuid, server);
                case "anytls" -> includeAnytls ? buildAnyTLS(uuid, server) : null;
                case "hysteria" -> buildHysteria(uuid, server, null);
                case "hysteria2" -> buildHysteria2(uuid, server);
                default -> null;
            };
            if (node != null) {
                proxies.add(node);
            }
        }
        return proxies;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> addProxies(Map<String, Object> config, List<Map<String, Object>> proxies) {
        List<String> tags = proxies.stream()
                .map(p -> str(p.get("tag")))
                .filter(t -> !t.isEmpty())
                .toList();
        List<Map<String, Object>> outbounds = config.get("outbounds") instanceof List<?> ol
                ? new ArrayList<>((List<Map<String, Object>>) ol) : new ArrayList<>();
        for (Map<String, Object> outbound : outbounds) {
            String type = str(outbound.get("type"));
            String tag = str(outbound.get("tag"));
            if (("selector".equals(type) && "节点选择".equals(tag))
                    || ("urltest".equals(type) && "自动选择".equals(tag))
                    || ("selector".equals(type) && tag.startsWith("#"))) {
                List<String> ob = outbound.get("outbounds") instanceof List<?> l
                        ? new ArrayList<>((List<String>) l) : new ArrayList<>();
                ob.addAll(tags);
                outbound.put("outbounds", ob);
            }
        }
        List<Map<String, Object>> merged = new ArrayList<>(outbounds);
        merged.addAll(proxies);
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadTemplate(String resource) {
        String path = resource != null ? resource : "rules/default.sing-box.json";
        try (InputStream in = SingboxBuilder.class.getClassLoader().getResourceAsStream(path)) {
            if (in != null) {
                return MAPPER.readValue(in, Map.class);
            }
        } catch (Exception ignored) {
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("outbounds", List.of(
                Map.of("tag", "DIRECT", "type", "direct"),
                Map.of("tag", "节点选择", "type", "selector", "outbounds", List.of("自动选择")),
                Map.of("tag", "自动选择", "type", "urltest", "outbounds", List.of())
        ));
        return config;
    }

    public static Map<String, Object> buildShadowsocks(String password, Map<String, Object> server) {
        if (str(server.get("cipher")).contains("2022-blake3")) {
            int len = "2022-blake3-aes-128-gcm".equals(server.get("cipher")) ? 16 : 32;
            long created = longVal(server.get("created_at"));
            password = Helper.getServerKey(created, len) + ":" + Helper.uuidToBase64(password, len);
        }
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("tag", server.get("name"));
        array.put("type", "shadowsocks");
        array.put("server", server.get("host"));
        array.put("server_port", server.get("port"));
        array.put("method", server.get("cipher"));
        array.put("password", password);
        array.put("domain_resolver", "local");
        return array;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildVmess(String uuid, Map<String, Object> server) {
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("tag", server.get("name"));
        array.put("type", "vmess");
        array.put("server", server.get("host"));
        array.put("server_port", server.get("port"));
        array.put("uuid", uuid);
        array.put("security", "auto");
        array.put("alter_id", 0);
        array.put("transport", new LinkedHashMap<>());
        array.put("domain_resolver", "local");
        if (isTlsOn(server)) {
            array.put("tls", tlsConfig(server));
        }
        applyTransport(array, server);
        return array;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildVless(String uuid, Map<String, Object> server) {
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("type", "vless");
        array.put("tag", server.get("name"));
        array.put("server", server.get("host"));
        array.put("server_port", server.get("port"));
        array.put("uuid", uuid);
        array.put("domain_resolver", "local");
        array.put("packet_encoding", "xudp");
        array.put("transport", new LinkedHashMap<>());
        if (isTlsOn(server)) {
            Map<String, Object> tls = tlsConfig(server);
            if (server.get("flow") != null) {
                array.put("flow", server.get("flow"));
            }
            array.put("tls", tls);
        }
        applyTransport(array, server);
        return array;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildTrojan(String password, Map<String, Object> server) {
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("tag", server.get("name"));
        array.put("type", "trojan");
        array.put("server", server.get("host"));
        array.put("server_port", server.get("port"));
        array.put("password", password);
        array.put("domain_resolver", "local");
        array.put("transport", new LinkedHashMap<>());
        Map<String, Object> tls = new LinkedHashMap<>();
        tls.put("enabled", true);
        Map<String, Object> tlsSettings = tlsMap(server);
        tls.put("insecure", intVal(server.get("allow_insecure")) == 1
                || (tlsSettings != null && intVal(tlsSettings.get("allow_insecure")) == 1));
        tls.put("server_name", server.getOrDefault("server_name",
                tlsSettings != null ? tlsSettings.get("server_name") : ""));
        applyEch(tls, tlsSettings);
        array.put("tls", tls);
        String network = str(server.get("network"));
        if ("grpc".equals(network) || "ws".equals(network)) {
            Map<String, Object> transport = transportMap(array);
            transport.put("type", network);
            Map<String, Object> ns = networkSettings(server);
            if (ns != null) {
                if ("grpc".equals(network) && ns.get("serviceName") != null) {
                    transport.put("service_name", ns.get("serviceName"));
                }
                if ("ws".equals(network)) {
                    transport.put("path", ns.getOrDefault("path", "/"));
                    if (ns.get("headers") instanceof Map<?, ?> h && h.get("Host") != null) {
                        transport.put("headers", Map.of("Host", List.of(String.valueOf(h.get("Host")))));
                    }
                    transport.put("max_early_data", 2048);
                    transport.put("early_data_header_name", "Sec-WebSocket-Protocol");
                }
            }
        }
        return array;
    }

    public static Map<String, Object> buildTuic(String password, Map<String, Object> server) {
        Map<String, Object> tlsSettings = tlsMap(server);
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("tag", server.get("name"));
        array.put("type", "tuic");
        array.put("server", server.get("host"));
        array.put("server_port", server.get("port"));
        array.put("uuid", password);
        array.put("password", password);
        array.put("congestion_control", server.getOrDefault("congestion_control", "cubic"));
        array.put("udp_relay_mode", server.getOrDefault("udp_relay_mode", "native"));
        array.put("zero_rtt_handshake", intVal(server.get("zero_rtt_handshake")) == 1);
        array.put("domain_resolver", "local");
        Map<String, Object> tls = new LinkedHashMap<>();
        tls.put("enabled", true);
        tls.put("insecure", intVal(server.get("insecure")) == 1
                || (tlsSettings != null && intVal(tlsSettings.get("allow_insecure")) == 1));
        tls.put("alpn", List.of("h3"));
        tls.put("disable_sni", intVal(server.get("disable_sni")) == 1);
        tls.put("server_name", server.getOrDefault("server_name",
                tlsSettings != null ? tlsSettings.get("server_name") : ""));
        array.put("tls", tls);
        return array;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildAnyTLS(String password, Map<String, Object> server) {
        Map<String, Object> tlsSettings = tlsMap(server);
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("tag", server.get("name"));
        array.put("type", "anytls");
        array.put("server", server.get("host"));
        array.put("server_port", server.get("port"));
        array.put("password", password);
        array.put("domain_resolver", "local");
        array.put("transport", new LinkedHashMap<>());
        Map<String, Object> tls = new LinkedHashMap<>();
        tls.put("enabled", true);
        tls.put("insecure", intVal(server.get("insecure")) == 1
                || (tlsSettings != null && intVal(tlsSettings.get("allow_insecure")) == 1));
        tls.put("alpn", List.of("h2", "http/1.1"));
        tls.put("server_name", server.getOrDefault("server_name",
                tlsSettings != null ? tlsSettings.get("server_name") : ""));
        if (tlsMode(server) == 2 && tlsSettings != null) {
            tls.put("reality", Map.of(
                    "enabled", true,
                    "public_key", tlsSettings.get("public_key"),
                    "short_id", tlsSettings.get("short_id")
            ));
            tls.put("utls", Map.of("enabled", true, "fingerprint", tlsSettings.getOrDefault("fingerprint", "chrome")));
        }
        array.put("tls", tls);
        applyTransport(array, server);
        return array;
    }

    public static Map<String, Object> buildHysteria2(String password, Map<String, Object> server) {
        String portField = str(server.get("port"));
        String first = portField.split(",")[0];
        if (first.contains("-")) first = first.split("-")[0];
        Map<String, Object> tlsSettings = tlsMap(server);
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("tag", server.get("name"));
        array.put("type", "hysteria2");
        array.put("server", server.get("host"));
        array.put("server_port", Integer.parseInt(first.trim()));
        array.put("password", password);
        array.put("domain_resolver", "local");
        Map<String, Object> tls = new LinkedHashMap<>();
        tls.put("enabled", true);
        tls.put("insecure", tlsSettings != null && intVal(tlsSettings.get("allow_insecure")) == 1);
        tls.put("server_name", tlsSettings != null ? tlsSettings.getOrDefault("server_name", "") : "");
        array.put("tls", tls);
        if (server.get("obfs") != null) {
            array.put("obfs", Map.of("type", server.get("obfs"), "password", server.get("obfs_password")));
        }
        return array;
    }

    public static Map<String, Object> buildHysteria(String password, Map<String, Object> server, User user) {
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("tag", server.get("name"));
        array.put("server", server.get("host"));
        array.put("domain_resolver", "local");
        Map<String, Object> tls = new LinkedHashMap<>();
        tls.put("enabled", true);
        tls.put("insecure", intVal(server.get("insecure")) == 1);
        tls.put("server_name", server.get("server_name"));
        array.put("tls", tls);
        int version = intVal(server.get("version"));
        if (version == 0 || version == 1) {
            array.put("type", "hysteria");
            array.put("auth_str", password);
            int up = intVal(server.get("down_mbps"));
            int down = intVal(server.get("up_mbps"));
            if (user != null && user.getSpeedLimit() != null && user.getSpeedLimit() > 0) {
                up = Math.min(up, user.getSpeedLimit());
                down = Math.min(down, user.getSpeedLimit());
            }
            array.put("up_mbps", up);
            array.put("down_mbps", down);
            array.put("disable_mtu_discovery", true);
            if (server.get("obfs") != null && server.get("obfs_password") != null) {
                array.put("obfs", server.get("obfs_password"));
            }
        } else {
            array.put("type", "hysteria2");
            array.put("password", password);
            array.put("server_port", firstPort(server));
            if (server.get("obfs") != null) {
                array.put("obfs", Map.of("type", server.get("obfs"), "password", server.get("obfs_password")));
            }
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    private static void applyTransport(Map<String, Object> array, Map<String, Object> server) {
        String network = str(server.get("network"));
        Map<String, Object> ns = networkSettings(server);
        if (ns == null) return;
        Map<String, Object> transport = transportMap(array);
        if ("tcp".equals(network)) {
            Object header = ns.get("header");
            if (header instanceof Map<?, ?> h && "http".equals(h.get("type"))) {
                transport.put("type", "http");
                if (h.get("request") instanceof Map<?, ?> req) {
                    if (req.get("headers") instanceof Map<?, ?> hdr && hdr.get("Host") != null) {
                        transport.put("host", hdr.get("Host"));
                    }
                    if (req.get("path") instanceof List<?> paths && !paths.isEmpty()) {
                        transport.put("path", paths.get(0));
                    }
                }
            }
        } else if ("ws".equals(network)) {
            transport.put("type", "ws");
            transport.put("path", ns.getOrDefault("path", "/"));
            if (ns.get("headers") instanceof Map<?, ?> h && h.get("Host") != null) {
                transport.put("headers", Map.of("Host", List.of(String.valueOf(h.get("Host")))));
            }
            transport.put("max_early_data", 2048);
            transport.put("early_data_header_name", "Sec-WebSocket-Protocol");
        } else if ("grpc".equals(network)) {
            transport.put("type", "grpc");
            if (ns.get("serviceName") != null) {
                transport.put("service_name", ns.get("serviceName"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tlsConfig(Map<String, Object> server) {
        Map<String, Object> tls = new LinkedHashMap<>();
        tls.put("enabled", true);
        Map<String, Object> tlsSettings = tlsMap(server);
        if (tlsSettings == null) return tls;
        tls.put("insecure", intVal(tlsSettings.get("allow_insecure")) == 1
                || intVal(tlsSettings.get("allowInsecure")) == 1);
        Object sn = tlsSettings.get("server_name");
        if (sn == null) sn = tlsSettings.get("serverName");
        tls.put("server_name", sn != null ? sn : "");
        if (tlsMode(server) == 2) {
            tls.put("reality", Map.of(
                    "enabled", true,
                    "public_key", tlsSettings.get("public_key"),
                    "short_id", tlsSettings.get("short_id")
            ));
            tls.put("utls", Map.of("enabled", true, "fingerprint", tlsSettings.getOrDefault("fingerprint", "chrome")));
        }
        applyEch(tls, tlsSettings);
        return tls;
    }

    private static void applyEch(Map<String, Object> tls, Map<String, Object> tlsSettings) {
        if (tlsSettings == null) return;
        Object ech = tlsSettings.get("ech");
        if ("cloudflare".equals(ech)) {
            tls.put("ech", Map.of("enabled", true, "query_server_name", "cloudflare-ech.com"));
        } else if ("custom".equals(ech) && tlsSettings.get("ech_config") != null) {
            Object cfg = tlsSettings.get("ech_config");
            tls.put("ech", Map.of("enabled", true, "config",
                    cfg instanceof List<?> l ? l : List.of(String.valueOf(cfg))));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> transportMap(Map<String, Object> array) {
        Object t = array.get("transport");
        if (t instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        Map<String, Object> transport = new LinkedHashMap<>();
        array.put("transport", transport);
        return transport;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tlsMap(Map<String, Object> server) {
        if (server.get("tls_settings") instanceof Map<?, ?> m) return (Map<String, Object>) m;
        if (server.get("tlsSettings") instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> networkSettings(Map<String, Object> server) {
        if (server.get("network_settings") instanceof Map<?, ?> m) return (Map<String, Object>) m;
        if (server.get("networkSettings") instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    private static boolean isTlsOn(Map<String, Object> server) {
        Object t = server.get("tls");
        return t instanceof Number n ? n.intValue() != 0 : Boolean.TRUE.equals(t);
    }

    private static int tlsMode(Map<String, Object> server) {
        Object t = server.get("tls");
        return t instanceof Number n ? n.intValue() : 0;
    }

    private static int firstPort(Map<String, Object> server) {
        String p = str(server.get("port")).split(",")[0];
        if (p.contains("-")) p = p.split("-")[0];
        return Integer.parseInt(p.trim());
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int intVal(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try { return Integer.parseInt(String.valueOf(o)); } catch (Exception ignore) {}
        }
        return 0;
    }

    private static long longVal(Object o) {
        if (o instanceof Number n) return n.longValue();
        return 0;
    }
}
