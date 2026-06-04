package com.v2board.api.protocol;

import com.v2board.api.util.Helper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 对齐 PHP App\Protocols\ClashMeta
 */
public final class ClashMetaBuilder {

    private ClashMetaBuilder() {
    }

    @SuppressWarnings("unchecked")
    public static String build(List<Map<String, Object>> servers, String uuid, String appName, String templateResource) {
        Map<String, Object> config = loadTemplate(templateResource);
        List<Map<String, Object>> proxy = new ArrayList<>();
        List<String> proxies = new ArrayList<>();

        for (Map<String, Object> item : servers) {
            Map<String, Object> server = new LinkedHashMap<>(item);
            if ("v2node".equals(server.get("type")) && server.get("protocol") != null) {
                server.put("type", String.valueOf(server.get("protocol")));
            }
            String type = String.valueOf(server.get("type"));
            Map<String, Object> node = switch (type) {
                case "shadowsocks" -> buildShadowsocks(uuid, server);
                case "vmess" -> buildVmess(uuid, server);
                case "vless" -> buildVless(uuid, server);
                case "trojan" -> buildTrojan(uuid, server);
                case "tuic" -> buildTuic(uuid, server);
                case "anytls" -> buildAnyTLS(uuid, server);
                case "hysteria" -> buildHysteria(uuid, server);
                case "hysteria2" -> buildHysteria2(uuid, server);
                default -> null;
            };
            if (node != null) {
                proxy.add(node);
                proxies.add(String.valueOf(server.get("name")));
            }
        }

        List<Map<String, Object>> existingProxies = config.get("proxies") instanceof List<?> l
                ? new ArrayList<>((List<Map<String, Object>>) config.get("proxies")) : new ArrayList<>();
        existingProxies.addAll(proxy);
        config.put("proxies", existingProxies);

        List<Map<String, Object>> groups = config.get("proxy-groups") instanceof List<?> gl
                ? (List<Map<String, Object>>) config.get("proxy-groups") : new ArrayList<>();
        String mainGroupName = appName != null && !appName.isEmpty() ? appName : "V2Board";
        for (Map<String, Object> group : groups) {
            mergeProxyGroup(group, proxies);
        }
        groups.removeIf(g -> {
            Object name = g.get("name");
            if (mainGroupName.equals(String.valueOf(name)) || "$app_name".equals(String.valueOf(name))) {
                return false;
            }
            Object p = g.get("proxies");
            return !(p instanceof List<?> list) || list.isEmpty();
        });
        ensureMainProxyGroup(groups, mainGroupName, proxies);
        config.put("proxy-groups", groups);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        String yaml = new Yaml(options).dump(config);
        return yaml.replace("$app_name", appName != null ? appName : "V2Board");
    }

    @SuppressWarnings("unchecked")
    private static void mergeProxyGroup(Map<String, Object> group, List<String> proxyNames) {
        Object proxiesObj = group.get("proxies");
        if (!(proxiesObj instanceof List<?> srcList)) {
            group.put("proxies", new ArrayList<>(proxyNames));
            return;
        }
        List<String> groupProxies = new ArrayList<>();
        for (Object o : srcList) {
            groupProxies.add(String.valueOf(o));
        }
        boolean isFilter = false;
        for (String src : new ArrayList<>(groupProxies)) {
            if (!isRegex(src)) {
                continue;
            }
            isFilter = true;
            groupProxies.remove(src);
            for (String dst : proxyNames) {
                if (isMatch(src, dst)) {
                    groupProxies.add(dst);
                }
            }
        }
        if (!isFilter) {
            groupProxies.addAll(proxyNames);
        }
        group.put("proxies", groupProxies);
    }

    private static void ensureMainProxyGroup(List<Map<String, Object>> groups, String mainGroupName,
                                             List<String> proxyNames) {
        for (Map<String, Object> group : groups) {
            String name = String.valueOf(group.get("name"));
            if (!mainGroupName.equals(name) && !"$app_name".equals(name)) {
                continue;
            }
            if ("$app_name".equals(name)) {
                group.put("name", mainGroupName);
            }
            List<String> proxies = group.get("proxies") instanceof List<?> list
                    ? new ArrayList<>((List<String>) list) : new ArrayList<>();
            if (proxies.isEmpty()) {
                proxies.add("自动选择");
                proxies.add("故障转移");
                proxies.addAll(proxyNames);
                group.put("proxies", proxies);
            }
            return;
        }
        Map<String, Object> main = new LinkedHashMap<>();
        main.put("name", mainGroupName);
        main.put("type", "select");
        List<String> proxies = new ArrayList<>(List.of("自动选择", "故障转移"));
        proxies.addAll(proxyNames);
        main.put("proxies", proxies);
        groups.add(0, main);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadTemplate(String resource) {
        String path = resource != null ? resource : "rules/default.clash.yaml";
        try (InputStream in = ClashMetaBuilder.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return defaultConfig();
            }
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(in);
            if (loaded instanceof Map<?, ?> m) {
                return new LinkedHashMap<>((Map<String, Object>) m);
            }
        } catch (Exception ignored) {
        }
        return defaultConfig();
    }

    private static Map<String, Object> defaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("proxies", new ArrayList<>());
        List<Map<String, Object>> groups = new ArrayList<>();
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("name", "$app_name");
        g.put("type", "select");
        g.put("proxies", new ArrayList<>());
        groups.add(g);
        config.put("proxy-groups", groups);
        config.put("rules", List.of("MATCH,$app_name"));
        return config;
    }

    public static Map<String, Object> buildShadowsocks(String password, Map<String, Object> server) {
        String cipher = String.valueOf(server.get("cipher"));
        if (cipher.contains("2022-blake3-aes-128-gcm")) {
            long created = server.get("created_at") instanceof Number n ? n.longValue() : 0L;
            password = Helper.getServerKey(created, 16) + ":" + Helper.uuidToBase64(password, 16);
        } else if (cipher.contains("2022-blake3-aes-256-gcm")) {
            long created = server.get("created_at") instanceof Number n ? n.longValue() : 0L;
            password = Helper.getServerKey(created, 32) + ":" + Helper.uuidToBase64(password, 32);
        }
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("name", server.get("name"));
        array.put("type", "ss");
        array.put("server", server.get("host"));
        array.put("port", server.get("port"));
        array.put("cipher", cipher);
        array.put("password", password);
        array.put("udp", true);
        if ("http".equals(server.get("obfs"))) {
            array.put("plugin", "obfs");
            Map<String, Object> opts = new LinkedHashMap<>();
            opts.put("mode", "http");
            opts.put("host", server.getOrDefault("obfs-host", ""));
            if (server.get("obfs-path") != null) {
                opts.put("path", server.get("obfs-path"));
            }
            array.put("plugin-opts", opts);
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildVmess(String uuid, Map<String, Object> server) {
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("name", server.get("name"));
        array.put("type", "vmess");
        array.put("server", server.get("host"));
        array.put("port", server.get("port"));
        array.put("uuid", uuid);
        array.put("alterId", 0);
        array.put("cipher", "auto");
        array.put("udp", true);
        applyTlsEch(array, server, "tlsSettings", "tls_settings");
        String network = str(server.get("network"));
        Map<String, Object> ns = networkSettings(server);
        if ("tcp".equals(network) && ns != null) {
            applyTcpHttp(array, ns);
        } else if ("ws".equals(network)) {
            array.put("network", "ws");
            applyWs(array, ns);
        } else if ("grpc".equals(network)) {
            array.put("network", "grpc");
            applyGrpc(array, ns);
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildVless(String uuid, Map<String, Object> server) {
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("name", server.get("name"));
        array.put("type", "vless");
        array.put("server", server.get("host"));
        array.put("port", server.get("port"));
        array.put("uuid", uuid);
        array.put("udp", true);
        Object tls = server.get("tls");
        boolean hasTls = tls instanceof Number n ? n.intValue() != 0 : Boolean.TRUE.equals(tls);
        if (hasTls && server.get("flow") != null && !String.valueOf(server.get("flow")).isEmpty()) {
            array.put("flow", server.get("flow"));
        }
        if (hasTls) {
            array.put("tls", true);
            Map<String, Object> tlsSettings = tlsMap(server);
            if (tlsSettings != null) {
                array.put("skip-cert-verify", intVal(tlsSettings.get("allow_insecure")) == 1);
                array.put("client-fingerprint", tlsSettings.getOrDefault("fingerprint", "chrome"));
                if (tlsSettings.get("server_name") != null) {
                    array.put("servername", tlsSettings.get("server_name"));
                }
                if (tls instanceof Number n && n.intValue() == 2) {
                    Map<String, Object> reality = new LinkedHashMap<>();
                    reality.put("public-key", tlsSettings.get("public_key"));
                    reality.put("short-id", tlsSettings.get("short_id"));
                    array.put("reality-opts", reality);
                }
                applyEchOpts(array, tlsSettings);
            }
        }
        String network = str(server.get("network"));
        Map<String, Object> ns = networkSettings(server);
        if ("tcp".equals(network) && ns != null) {
            applyTcpHttp(array, ns);
        } else if ("ws".equals(network)) {
            array.put("network", "ws");
            applyWs(array, ns);
        } else if ("grpc".equals(network)) {
            array.put("network", "grpc");
            applyGrpc(array, ns);
        } else if ("xhttp".equals(network) && ns != null) {
            array.put("network", "xhttp");
            Map<String, Object> xhttp = new LinkedHashMap<>();
            if (ns.get("path") != null) xhttp.put("path", ns.get("path"));
            if (ns.get("host") != null) xhttp.put("host", ns.get("host"));
            if (ns.get("mode") != null) xhttp.put("mode", ns.get("mode"));
            array.put("xhttp-opts", xhttp);
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildTrojan(String password, Map<String, Object> server) {
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("name", server.get("name"));
        array.put("type", "trojan");
        array.put("server", server.get("host"));
        array.put("port", server.get("port"));
        array.put("password", password);
        array.put("udp", true);
        Map<String, Object> tlsSettings = tlsMap(server);
        array.put("sni", server.getOrDefault("server_name", tlsSettings != null ? tlsSettings.get("server_name") : ""));
        int insecure = intVal(server.get("allow_insecure"));
        if (insecure == 0 && tlsSettings != null) {
            insecure = intVal(tlsSettings.get("allow_insecure"));
        }
        array.put("skip-cert-verify", insecure == 1);
        if (tlsSettings != null) {
            applyEchOpts(array, tlsSettings);
        }
        return array;
    }

    public static Map<String, Object> buildTuic(String password, Map<String, Object> server) {
        Map<String, Object> tlsSettings = tlsMap(server);
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("name", server.get("name"));
        array.put("type", "tuic");
        array.put("server", server.get("host"));
        array.put("port", server.get("port"));
        array.put("uuid", password);
        array.put("password", password);
        array.put("alpn", List.of("h3"));
        array.put("disable-sni", intVal(server.get("disable_sni")) == 1);
        array.put("reduce-rtt", intVal(server.get("zero_rtt_handshake")) == 1);
        array.put("udp-relay-mode", server.getOrDefault("udp_relay_mode", "native"));
        array.put("congestion-controller", server.getOrDefault("congestion_control", "cubic"));
        int insecure = intVal(server.get("insecure"));
        if (insecure == 0 && tlsSettings != null) {
            insecure = intVal(tlsSettings.get("allow_insecure"));
        }
        array.put("skip-cert-verify", insecure == 1);
        array.put("sni", server.getOrDefault("server_name", tlsSettings != null ? tlsSettings.get("server_name") : ""));
        return array;
    }

    public static Map<String, Object> buildAnyTLS(String password, Map<String, Object> server) {
        Map<String, Object> tlsSettings = tlsMap(server);
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("name", server.get("name"));
        array.put("type", "anytls");
        array.put("server", server.get("host"));
        array.put("port", server.get("port"));
        array.put("password", password);
        array.put("client-fingerprint", "chrome");
        array.put("udp", true);
        array.put("alpn", List.of("h2", "http/1.1"));
        int insecure = intVal(server.get("insecure"));
        if (insecure == 0 && tlsSettings != null) {
            insecure = intVal(tlsSettings.get("allow_insecure"));
        }
        array.put("skip-cert-verify", insecure == 1);
        array.put("sni", server.getOrDefault("server_name", tlsSettings != null ? tlsSettings.get("server_name") : ""));
        return array;
    }

    public static Map<String, Object> buildHysteria(String password, Map<String, Object> server) {
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("name", server.get("name"));
        array.put("server", server.get("host"));
        array.put("udp", true);
        array.put("skip-cert-verify", intVal(server.get("insecure")) == 1);
        if (server.get("server_name") != null) {
            array.put("sni", server.get("server_name"));
        }
        int firstPort = firstPort(server.get("port"));
        array.put("port", firstPort);
        String portField = String.valueOf(server.get("port"));
        if (portField.contains(",") || portField.contains("-")) {
            array.put("ports", portField);
            array.put("mport", portField);
        }
        if (intVal(server.get("version")) == 2) {
            array.put("type", "hysteria2");
            array.put("password", password);
            if (server.get("obfs") != null) {
                array.put("obfs", server.get("obfs"));
                array.put("obfs-password", server.get("obfs_password"));
            }
        } else {
            array.put("type", "hysteria");
            array.put("auth_str", password);
            array.put("up", server.get("down_mbps"));
            array.put("down", server.get("up_mbps"));
            array.put("protocol", "udp");
        }
        return array;
    }

    public static Map<String, Object> buildHysteria2(String password, Map<String, Object> server) {
        Map<String, Object> tlsSettings = tlsMap(server);
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("name", server.get("name"));
        array.put("type", "hysteria2");
        array.put("server", server.get("host"));
        array.put("password", password);
        array.put("udp", true);
        array.put("skip-cert-verify", intVal(tlsSettings != null ? tlsSettings.get("allow_insecure") : 0) == 1);
        array.put("sni", tlsSettings != null ? tlsSettings.getOrDefault("server_name", "") : "");
        int firstPort = firstPort(server.get("port"));
        array.put("port", firstPort);
        String portField = String.valueOf(server.get("port"));
        if (portField.contains(",") || portField.contains("-")) {
            array.put("ports", portField);
            array.put("mport", portField);
        }
        if (server.get("obfs") != null) {
            array.put("obfs", server.get("obfs"));
            array.put("obfs-password", server.get("obfs_password"));
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    private static void applyTlsEch(Map<String, Object> array, Map<String, Object> server, String... keys) {
        Object tls = server.get("tls");
        boolean enabled = tls instanceof Boolean b ? b : (tls instanceof Number n && n.intValue() != 0);
        if (!enabled) return;
        array.put("tls", true);
        Map<String, Object> tlsSettings = null;
        for (String k : keys) {
            if (server.get(k) instanceof Map<?, ?> m) {
                tlsSettings = (Map<String, Object>) m;
                break;
            }
        }
        if (tlsSettings == null) return;
        if (intVal(tlsSettings.get("allowInsecure")) == 1 || intVal(tlsSettings.get("allow_insecure")) == 1) {
            array.put("skip-cert-verify", true);
        }
        Object sni = tlsSettings.get("serverName");
        if (sni == null) sni = tlsSettings.get("server_name");
        if (sni != null) array.put("servername", sni);
        applyEchOpts(array, tlsSettings);
    }

    private static void applyEchOpts(Map<String, Object> array, Map<String, Object> tlsSettings) {
        Object ech = tlsSettings.get("ech");
        if (ech == null || String.valueOf(ech).isEmpty()) return;
        if ("cloudflare".equals(ech)) {
            array.put("ech-opts", Map.of("enable", true, "query-server-name", "cloudflare-ech.com"));
        } else if ("custom".equals(ech) && tlsSettings.get("ech_config") != null) {
            Object cfg = tlsSettings.get("ech_config");
            array.put("ech-opts", Map.of("enable", true, "config",
                    cfg instanceof List<?> l ? l : List.of(String.valueOf(cfg))));
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyTcpHttp(Map<String, Object> array, Map<String, Object> ns) {
        Object header = ns.get("header");
        if (header instanceof Map<?, ?> h && "http".equals(h.get("type"))) {
            array.put("network", "http");
            Map<String, Object> httpOpts = new LinkedHashMap<>();
            Object request = h.get("request");
            if (request instanceof Map<?, ?> req) {
                Object headers = req.get("headers");
                if (headers instanceof Map<?, ?> hdr && hdr.get("Host") != null) {
                    httpOpts.put("headers", Map.of("Host", hdr.get("Host")));
                }
                Object path = req.get("path");
                if (path != null) httpOpts.put("path", path);
            }
            array.put("http-opts", httpOpts);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyWs(Map<String, Object> array, Map<String, Object> ns) {
        if (ns == null) return;
        Map<String, Object> ws = new LinkedHashMap<>();
        if (ns.get("path") != null) ws.put("path", ns.get("path"));
        if (ns.get("headers") instanceof Map<?, ?> headers && headers.get("Host") != null) {
            ws.put("headers", Map.of("Host", headers.get("Host")));
        }
        array.put("ws-opts", ws);
    }

    @SuppressWarnings("unchecked")
    private static void applyGrpc(Map<String, Object> array, Map<String, Object> ns) {
        if (ns == null) return;
        Map<String, Object> grpc = new LinkedHashMap<>();
        if (ns.get("serviceName") != null) {
            grpc.put("grpc-service-name", ns.get("serviceName"));
        }
        array.put("grpc-opts", grpc);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tlsMap(Map<String, Object> server) {
        if (server.get("tls_settings") instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (server.get("tlsSettings") instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
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

    private static int firstPort(Object port) {
        String portField = String.valueOf(port);
        String first = portField.split(",")[0];
        if (first.contains("-")) {
            first = first.split("-")[0];
        }
        try {
            return Integer.parseInt(first.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 对齐 PHP isRegex：preg_match($exp, '') !== false。
     * PHP 对无分隔符的「自动选择」等组名会判为非正则；Java Pattern.compile 会误判为 true。
     */
    private static boolean isRegex(String exp) {
        if (exp == null || exp.isEmpty()) {
            return false;
        }
        if ("自动选择".equals(exp) || "故障转移".equals(exp)
                || "DIRECT".equals(exp) || "REJECT".equals(exp) || "GLOBAL".equals(exp)) {
            return false;
        }
        if (!exp.matches(".*[\\\\.^$|?*+\\[\\]{}()].*")) {
            return false;
        }
        try {
            Pattern.compile(exp);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isMatch(String exp, String str) {
        try {
            return Pattern.compile(exp).matcher(str).find();
        } catch (Exception e) {
            return false;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int intVal(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o));
            } catch (NumberFormatException ignore) {
            }
        }
        return 0;
    }
}
