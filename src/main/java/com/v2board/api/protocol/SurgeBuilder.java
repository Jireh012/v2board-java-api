package com.v2board.api.protocol;

import com.v2board.api.model.User;
import com.v2board.api.util.Helper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对齐 PHP App\Protocols\Surge
 */
public final class SurgeBuilder {

    private SurgeBuilder() {
    }

    public static String build(List<Map<String, Object>> servers, User user, String appName,
                               String subsLink, String subsDomain) {
        StringBuilder proxies = new StringBuilder();
        StringBuilder proxyGroup = new StringBuilder();
        String uuid = user.getUuid();

        for (Map<String, Object> item : servers) {
            Map<String, Object> server = item;
            if ("v2node".equals(str(server.get("type"))) && server.get("protocol") != null) {
                server = new LinkedHashMap<>(item);
                server.put("type", str(server.get("protocol")));
            }
            String type = str(server.get("type"));
            String line = switch (type) {
                case "shadowsocks" -> buildShadowsocks(uuid, server);
                case "vmess" -> buildVmess(uuid, server);
                case "trojan" -> buildTrojan(uuid, server);
                case "hysteria" -> intVal(server.get("version")) == 2 ? buildHysteria(uuid, server) : "";
                case "anytls" -> buildAnyTls(uuid, server);
                default -> "";
            };
            if (!line.isEmpty()) {
                proxies.append(line);
                proxyGroup.append(server.get("name")).append(", ");
            }
        }

        String config = loadTemplate("rules/custom.surge.conf");
        if (config.isEmpty()) {
            config = loadTemplate("rules/default.surge.conf");
        }

        long u = user.getU() != null ? user.getU() : 0;
        long d = user.getD() != null ? user.getD() : 0;
        long total = user.getTransferEnable() != null ? user.getTransferEnable() : 0;
        double upload = roundGb(u);
        double download = roundGb(d);
        double useTraffic = upload + download;
        double totalTraffic = roundGb(total);
        String expireDate = user.getExpiredAt() == null || user.getExpiredAt() == 0
                ? "长期有效"
                : Instant.ofEpochSecond(user.getExpiredAt())
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String subscribeInfo = "title=" + appName + "订阅信息, content=上传流量：" + upload + "GB\\n下载流量："
                + download + "GB\\n剩余流量：" + useTraffic + "GB\\n套餐流量：" + totalTraffic + "GB\\n到期时间：" + expireDate;

        config = config.replace("$subs_link", subsLink != null ? subsLink : "");
        config = config.replace("$subs_domain", subsDomain != null ? subsDomain : "");
        config = config.replace("$proxies", proxies.toString());
        config = config.replace("$proxy_group", proxyGroup.toString().replaceAll(",\\s*$", ""));
        config = config.replace("$subscribe_info", subscribeInfo);
        return config;
    }

    public static String buildShadowsocks(String password, Map<String, Object> server) {
        String cipher = str(server.get("cipher"));
        if ("2022-blake3-aes-128-gcm".equals(cipher)) {
            password = Helper.getServerKey(longVal(server.get("created_at")), 16) + ":"
                    + Helper.uuidToBase64(password, 16);
        } else if ("2022-blake3-aes-256-gcm".equals(cipher)) {
            password = Helper.getServerKey(longVal(server.get("created_at")), 32) + ":"
                    + Helper.uuidToBase64(password, 32);
        }
        List<String> config = new ArrayList<>();
        config.add(server.get("name") + "=ss");
        config.add(str(server.get("host")));
        config.add(str(server.get("port")));
        config.add("encrypt-method=" + cipher);
        config.add("password=" + password);
        if ("http".equals(server.get("obfs"))) {
            config.add("obfs=" + server.get("obfs"));
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
        List<String> config = new ArrayList<>();
        config.add(server.get("name") + "=vmess");
        config.add(str(server.get("host")));
        config.add(str(server.get("port")));
        config.add("username=" + uuid);
        config.add("vmess-aead=true");
        config.add("tfo=true");
        config.add("udp-relay=true");

        if (isTls(server)) {
            config.add("tls=true");
            Map<String, Object> tlsSettings = tls(server);
            if (tlsSettings != null) {
                if (tlsSettings.get("allowInsecure") != null) {
                    config.add("skip-cert-verify=" + (truthy(tlsSettings.get("allowInsecure")) ? "true" : "false"));
                } else if (tlsSettings.get("allow_insecure") != null) {
                    config.add("skip-cert-verify=" + (truthy(tlsSettings.get("allow_insecure")) ? "true" : "false"));
                }
                Object sn = tlsSettings.get("serverName");
                if (sn == null) sn = tlsSettings.get("server_name");
                if (sn != null && !str(sn).isEmpty()) {
                    config.add("sni=" + sn);
                }
            }
        }
        if ("ws".equals(str(server.get("network")))) {
            config.add("ws=true");
            Map<String, Object> ws = ns(server);
            if (ws != null) {
                if (ws.get("path") != null && !str(ws.get("path")).isEmpty()) {
                    config.add("ws-path=" + ws.get("path"));
                }
                if (ws.get("headers") instanceof Map<?, ?> h && h.get("Host") != null && !str(h.get("Host")).isEmpty()) {
                    config.add("ws-headers=Host:" + h.get("Host"));
                }
                if (ws.get("security") != null) {
                    config.add("encrypt-method=" + ws.get("security"));
                }
            }
        }
        return String.join(",", config) + "\r\n";
    }

    @SuppressWarnings("unchecked")
    public static String buildTrojan(String password, Map<String, Object> server) {
        List<String> config = new ArrayList<>();
        config.add(server.get("name") + "=trojan");
        config.add(str(server.get("host")));
        config.add(str(server.get("port")));
        config.add("password=" + password);
        if (server.get("server_name") != null && !str(server.get("server_name")).isEmpty()) {
            config.add("sni=" + server.get("server_name"));
        }
        config.add("tfo=true");
        config.add("udp-relay=true");
        if (server.get("allow_insecure") != null) {
            config.add(truthy(server.get("allow_insecure")) ? "skip-cert-verify=true" : "skip-cert-verify=false");
        }
        if ("ws".equals(str(server.get("network")))) {
            config.add("ws=true");
            Map<String, Object> ws = networkSettings(server);
            if (ws != null) {
                if (ws.get("path") != null && !str(ws.get("path")).isEmpty()) {
                    config.add("ws-path=" + ws.get("path"));
                }
                if (ws.get("headers") instanceof Map<?, ?> h && h.get("Host") != null && !str(h.get("Host")).isEmpty()) {
                    config.add("ws-headers=Host:" + h.get("Host"));
                }
            }
        }
        return joinConfig(config);
    }

    public static String buildHysteria(String password, Map<String, Object> server) {
        String portField = str(server.get("port"));
        String firstPart = portField.split(",")[0];
        String firstPort = firstPart.contains("-") ? firstPart.split("-")[0] : firstPart;

        List<String> config = new ArrayList<>();
        config.add(server.get("name") + "=hysteria2");
        config.add(str(server.get("host")));
        config.add(firstPort);
        config.add("password=" + password);
        config.add("download-bandwidth=" + server.getOrDefault("up_mbps", 0));
        if (server.get("server_name") != null && !str(server.get("server_name")).isEmpty()) {
            config.add("sni=" + server.get("server_name"));
        }
        config.add("udp-relay=true");
        if (server.get("insecure") != null && !str(server.get("insecure")).isEmpty()) {
            config.add(truthy(server.get("insecure")) ? "skip-cert-verify=true" : "skip-cert-verify=false");
        }
        return joinConfig(config);
    }

    @SuppressWarnings("unchecked")
    public static String buildAnyTls(String password, Map<String, Object> server) {
        Map<String, Object> tlsSettings = tls(server);
        boolean allowInsecure = intVal(server.get("insecure")) == 1
                || (tlsSettings != null && intVal(tlsSettings.get("allow_insecure")) == 1);
        String sni = server.get("server_name") != null ? str(server.get("server_name"))
                : (tlsSettings != null ? str(tlsSettings.get("server_name")) : "");

        List<String> config = new ArrayList<>();
        config.add(server.get("name") + "=anytls");
        config.add(str(server.get("host")));
        config.add(str(server.get("port")));
        config.add("password=" + password);
        config.add("skip-cert-verify=" + (allowInsecure ? "true" : "false"));
        config.add("tfo=true");
        if (!sni.isEmpty()) {
            config.add("sni=" + sni);
        }
        return String.join(", ", config) + "\r\n";
    }

    private static String loadTemplate(String resource) {
        try (InputStream in = SurgeBuilder.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static double roundGb(long bytes) {
        return Math.round(bytes / (1024.0 * 1024.0 * 1024.0) * 100.0) / 100.0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ns(Map<String, Object> server) {
        if (server.get("networkSettings") instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return networkSettings(server);
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
        if (server.get("tlsSettings") instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (server.get("tls_settings") instanceof Map<?, ?> m) {
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

    private static int intVal(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o));
            } catch (Exception ignore) {
            }
        }
        return 0;
    }

    private static long longVal(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return 0;
    }
}
