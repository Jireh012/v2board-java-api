package com.v2board.api.protocol;

import com.v2board.api.util.Helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对齐 PHP App\Protocols\Loon
 */
public final class LoonBuilder {

    private LoonBuilder() {
    }

    public static String build(List<Map<String, Object>> servers, String uuid) {
        StringBuilder uri = new StringBuilder();
        for (Map<String, Object> item : servers) {
            Map<String, Object> server = item;
            if ("v2node".equals(str(server.get("type"))) && server.get("protocol") != null) {
                server = new java.util.LinkedHashMap<>(item);
                server.put("type", str(server.get("protocol")));
            }
            String type = str(server.get("type"));
            String network = str(server.get("network"));
            String line = switch (type) {
                case "shadowsocks" -> buildShadowsocks(uuid, server);
                case "vmess" -> buildVmess(uuid, server);
                case "vless" -> ("tcp".equals(network) || "ws".equals(network)) ? buildVless(uuid, server) : "";
                case "trojan" -> !"grpc".equals(network) ? buildTrojan(uuid, server) : "";
                case "hysteria" -> intVal(server.get("version")) == 2 ? buildHysteria(uuid, server) : "";
                case "anytls" -> buildAnytls(uuid, server);
                default -> "";
            };
            uri.append(line);
        }
        return uri.toString();
    }

    public static String buildShadowsocks(String password, Map<String, Object> server) {
        String cipher = str(server.get("cipher"));
        if ("2022-blake3-aes-128-gcm".equals(cipher)) {
            long created = longVal(server.get("created_at"));
            password = Helper.getServerKey(created, 16) + ":" + Helper.uuidToBase64(password, 16);
        } else if ("2022-blake3-aes-256-gcm".equals(cipher)) {
            long created = longVal(server.get("created_at"));
            password = Helper.getServerKey(created, 32) + ":" + Helper.uuidToBase64(password, 32);
        }
        List<String> config = new ArrayList<>();
        config.add(server.get("name") + "=Shadowsocks");
        config.add(str(server.get("host")));
        config.add(str(server.get("port")));
        config.add(cipher);
        config.add(password);
        if ("http".equals(server.get("obfs"))) {
            config.add("obfs-name=" + server.get("obfs"));
            if (server.get("obfs-host") != null && !str(server.get("obfs-host")).isEmpty()) {
                config.add("obfs-host=" + server.get("obfs-host"));
            }
            if (server.get("obfs-path") != null) {
                config.add("obfs-uri=" + server.get("obfs-path"));
            }
        }
        config.add("fast-open=false");
        config.add("udp=true");
        return String.join(",", config) + "\r\n";
    }

    @SuppressWarnings("unchecked")
    public static String buildVmess(String uuid, Map<String, Object> server) {
        Map<String, Object> networkSettings = ns(server);
        List<String> config = new ArrayList<>();
        config.add(server.get("name") + "=vmess");
        config.add(str(server.get("host")));
        config.add(str(server.get("port")));
        config.add(networkSettings != null && networkSettings.get("security") != null
                ? str(networkSettings.get("security")) : "auto");
        config.add(uuid);
        config.add("fast-open=false");
        config.add("udp=true");
        config.add("alterId=0");
        String network = str(server.get("network"));
        if ("tcp".equals(network)) {
            config.add("transport=tcp");
            applyTcp(config, networkSettings);
        }
        if (isTls(server)) {
            config.add("over-tls=true");
            Map<String, Object> tls = tls(server);
            if (tls != null) {
                if (intVal(tls.get("allowInsecure")) == 1 || intVal(tls.get("allow_insecure")) == 1) {
                    config.add("skip-cert-verify=true");
                }
                Object sn = tls.get("serverName");
                if (sn == null) sn = tls.get("server_name");
                if (sn != null && !str(sn).isEmpty()) {
                    config.add("tls-name=" + sn);
                }
            }
        }
        if ("ws".equals(network)) {
            config.add("transport=ws");
            applyWs(config, networkSettings);
        }
        return String.join(",", config) + "\r\n";
    }

    @SuppressWarnings("unchecked")
    public static String buildVless(String uuid, Map<String, Object> server) {
        List<String> config = new ArrayList<>();
        config.add(server.get("name") + "=vless");
        config.add(str(server.get("host")));
        config.add(str(server.get("port")));
        config.add(uuid);
        config.add("fast-open=false");
        config.add("udp=true");
        config.add("alterId=0");
        String network = str(server.get("network"));
        Map<String, Object> ns = networkSettings(server);
        if ("tcp".equals(network)) {
            config.add("transport=tcp");
            applyTcpVless(config, ns);
        }
        int tls = tlsMode(server);
        if (tls == 1) {
            config.add("over-tls=true");
            if (server.get("flow") != null) {
                config.add("flow=" + server.get("flow"));
            }
            Map<String, Object> tlsSettings = tls(server);
            if (tlsSettings != null) {
                if (intVal(tlsSettings.get("allow_insecure")) == 1) {
                    config.add("skip-cert-verify=true");
                }
                if (tlsSettings.get("server_name") != null) {
                    config.add("tls-name=" + tlsSettings.get("server_name"));
                }
            }
        } else if (tls == 2) {
            if (server.get("flow") != null) {
                config.add("flow=" + server.get("flow"));
            }
            Map<String, Object> tlsSettings = tls(server);
            if (tlsSettings != null) {
                if (tlsSettings.get("public_key") != null) {
                    config.add("public-key=" + tlsSettings.get("public_key"));
                }
                if (tlsSettings.get("short_id") != null) {
                    config.add("short-id=" + tlsSettings.get("short_id"));
                }
                if (tlsSettings.get("server_name") != null) {
                    config.add("sni=" + tlsSettings.get("server_name"));
                }
                if (intVal(tlsSettings.get("allow_insecure")) == 1) {
                    config.add("skip-cert-verify=true");
                }
            }
        }
        if ("ws".equals(network)) {
            config.add("transport=ws");
            applyWs(config, ns);
        }
        return String.join(",", config) + "\r\n";
    }

    @SuppressWarnings("unchecked")
    public static String buildTrojan(String password, Map<String, Object> server) {
        List<String> config = new ArrayList<>();
        config.add(server.get("name") + "=trojan");
        config.add(str(server.get("host")));
        config.add(str(server.get("port")));
        config.add(password);
        if (server.get("server_name") != null && !str(server.get("server_name")).isEmpty()) {
            config.add("tls-name=" + server.get("server_name"));
        }
        config.add("fast-open=false");
        config.add("udp=true");
        if (intVal(server.get("allow_insecure")) == 1) {
            config.add("skip-cert-verify=true");
        }
        if ("ws".equals(str(server.get("network")))) {
            config.add("ws=true");
            Map<String, Object> ws = networkSettings(server);
            if (ws != null) {
                if (ws.get("path") != null) config.add("ws-path=" + ws.get("path"));
                if (ws.get("headers") instanceof Map<?, ?> h && h.get("Host") != null) {
                    config.add("ws-headers=Host:" + h.get("Host"));
                }
            }
        }
        return joinConfig(config);
    }

    public static String buildHysteria(String password, Map<String, Object> server) {
        String portField = str(server.get("port"));
        String firstPort = portField.split(",")[0];
        if (firstPort.contains("-")) {
            firstPort = firstPort.split("-")[0];
        }
        List<String> config = new ArrayList<>();
        config.add(server.get("name") + "=hysteria2");
        config.add(str(server.get("host")));
        config.add(firstPort);
        config.add("password=" + password);
        config.add("download-bandwidth=" + server.getOrDefault("up_mbps", 0));
        if (server.get("server_name") != null && !str(server.get("server_name")).isEmpty()) {
            config.add("sni=" + server.get("server_name"));
        }
        config.add("udp=true");
        if (intVal(server.get("insecure")) == 1) {
            config.add("skip-cert-verify=true");
        }
        if (server.get("obfs") != null) {
            config.add("salamander-password=" + server.get("obfs_password"));
        }
        return joinConfig(config);
    }

    @SuppressWarnings("unchecked")
    public static String buildAnytls(String password, Map<String, Object> server) {
        List<String> config = new ArrayList<>();
        config.add(server.get("name") + "=anytls");
        config.add(str(server.get("host")));
        config.add(str(server.get("port")));
        config.add(password);
        config.add("udp=true");
        Map<String, Object> tlsSettings = tls(server);
        String sni = server.get("server_name") != null ? str(server.get("server_name"))
                : (tlsSettings != null ? str(tlsSettings.get("server_name")) : "");
        if (!sni.isEmpty()) {
            config.add("sni=" + sni);
        }
        int insecure = intVal(server.get("insecure"));
        if (insecure == 0 && tlsSettings != null) {
            insecure = intVal(tlsSettings.get("allow_insecure"));
        }
        config.add("skip-cert-verify=" + (insecure == 1 ? "true" : "false"));
        return String.join(",", config) + "\r\n";
    }

    @SuppressWarnings("unchecked")
    private static void applyTcp(List<String> config, Map<String, Object> tcpSettings) {
        if (tcpSettings == null) return;
        Object header = tcpSettings.get("header");
        if (header instanceof Map<?, ?> h && "http".equals(h.get("type"))) {
            for (int i = 0; i < config.size(); i++) {
                if ("transport=tcp".equals(config.get(i))) {
                    config.set(i, "transport=http");
                    break;
                }
            }
            if (h.get("request") instanceof Map<?, ?> req) {
                if (req.get("path") instanceof List<?> paths && !paths.isEmpty()) {
                    config.add("path=" + paths.get(0));
                }
                if (req.get("headers") instanceof Map<?, ?> hdr) {
                    Object host = hdr.get("Host");
                    if (host instanceof List<?> hosts && !hosts.isEmpty()) {
                        config.add("host=" + hosts.get(0));
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyTcpVless(List<String> config, Map<String, Object> tcpSettings) {
        if (tcpSettings == null) return;
        Object header = tcpSettings.get("header");
        if (header instanceof Map<?, ?> h && "http".equals(h.get("type"))) {
            for (int i = 0; i < config.size(); i++) {
                if ("transport=tcp".equals(config.get(i))) {
                    config.set(i, "transport=http");
                    break;
                }
            }
            if (h.get("request") instanceof Map<?, ?> req) {
                if (req.get("path") instanceof List<?> paths && !paths.isEmpty()) {
                    config.add("path=" + paths.get(0));
                }
                if (req.get("headers") instanceof Map<?, ?> hdr) {
                    Object host = hdr.get("Host");
                    if (host instanceof List<?> hosts && !hosts.isEmpty()) {
                        config.add("host=" + hosts.get(0));
                    }
                }
            }
        }
    }

    private static void applyWs(List<String> config, Map<String, Object> wsSettings) {
        if (wsSettings == null) return;
        if (wsSettings.get("path") != null) config.add("path=" + wsSettings.get("path"));
        if (wsSettings.get("headers") instanceof Map<?, ?> h && h.get("Host") != null) {
            config.add("host=" + h.get("Host"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ns(Map<String, Object> server) {
        if (server.get("networkSettings") instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return networkSettings(server);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> networkSettings(Map<String, Object> server) {
        if (server.get("network_settings") instanceof Map<?, ?> m) return (Map<String, Object>) m;
        if (server.get("networkSettings") instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tls(Map<String, Object> server) {
        if (server.get("tlsSettings") instanceof Map<?, ?> m) return (Map<String, Object>) m;
        if (server.get("tls_settings") instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    private static boolean isTls(Map<String, Object> server) {
        Object t = server.get("tls");
        return t instanceof Boolean b ? b : (t instanceof Number n && n.intValue() != 0);
    }

    private static int tlsMode(Map<String, Object> server) {
        Object t = server.get("tls");
        if (t instanceof Number n) return n.intValue();
        return isTls(server) ? 1 : 0;
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

    private static String joinConfig(List<String> config) {
        List<String> parts = new ArrayList<>();
        for (String s : config) {
            if (s != null && !s.isEmpty()) {
                parts.add(s);
            }
        }
        return String.join(",", parts) + "\r\n";
    }
}
