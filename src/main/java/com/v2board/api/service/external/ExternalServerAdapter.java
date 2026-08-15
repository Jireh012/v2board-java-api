package com.v2board.api.service.external;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将外链节点的 sing-box outbound 压平成面板 Builder 可用的 server map + 凭证。
 * Clash / Sing-box 直接消费 clash_proxy / singbox_outbound；Surge / Surfboard / QX / Loon 走此适配。
 */
public final class ExternalServerAdapter {

    public record Resolved(Map<String, Object> server, String credential) {
    }

    private ExternalServerAdapter() {
    }

    public static boolean isExternal(Map<String, Object> server) {
        if (server == null) {
            return false;
        }
        return "external".equals(String.valueOf(server.get("type")))
                || Boolean.TRUE.equals(server.get("external"));
    }

    @SuppressWarnings("unchecked")
    public static Resolved resolve(Map<String, Object> item) {
        if (!isExternal(item)) {
            return null;
        }
        Object outboundObj = item.get("singbox_outbound");
        if (!(outboundObj instanceof Map<?, ?> m) || m.isEmpty()) {
            return null;
        }
        Map<String, Object> outbound = (Map<String, Object>) m;
        String displayName = str(item.get("name"));
        if (displayName.isEmpty()) {
            displayName = str(outbound.get("tag"));
        }
        return flatten(outbound, displayName);
    }

    @SuppressWarnings("unchecked")
    public static Resolved flatten(Map<String, Object> outbound, String displayName) {
        if (outbound == null || outbound.isEmpty()) {
            return null;
        }
        String type = str(outbound.get("type")).toLowerCase();
        String host = str(outbound.get("server"));
        Object port = outbound.get("server_port");
        if (type.isEmpty() || host.isEmpty() || port == null) {
            return null;
        }

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("name", displayName != null && !displayName.isEmpty() ? displayName : str(outbound.get("tag")));
        server.put("host", host);
        server.put("port", port);
        server.put("external", true);

        String credential = "";
        switch (type) {
            case "shadowsocks" -> {
                server.put("type", "shadowsocks");
                server.put("cipher", outbound.get("method"));
                credential = str(outbound.get("password"));
            }
            case "vmess" -> {
                server.put("type", "vmess");
                credential = str(outbound.get("uuid"));
                server.put("cipher", outbound.getOrDefault("security", "auto"));
                applyTls(server, outbound);
                applyTransport(server, outbound);
            }
            case "vless" -> {
                server.put("type", "vless");
                credential = str(outbound.get("uuid"));
                if (outbound.get("flow") != null) {
                    server.put("flow", outbound.get("flow"));
                }
                applyTls(server, outbound);
                applyTransport(server, outbound);
            }
            case "trojan" -> {
                server.put("type", "trojan");
                credential = str(outbound.get("password"));
                applyTls(server, outbound);
                applyTransport(server, outbound);
            }
            case "hysteria2" -> {
                server.put("type", "hysteria2");
                server.put("version", 2);
                credential = str(outbound.get("password"));
                applyTls(server, outbound);
                Object obfs = outbound.get("obfs");
                if (obfs instanceof Map<?, ?> o) {
                    if (o.get("type") != null) {
                        server.put("obfs", o.get("type"));
                    }
                    if (o.get("password") != null) {
                        server.put("obfs_password", o.get("password"));
                    }
                }
            }
            case "hysteria" -> {
                server.put("type", "hysteria");
                server.put("version", 1);
                credential = str(outbound.getOrDefault("auth_str", outbound.get("password")));
                if (outbound.get("up_mbps") != null) {
                    server.put("up_mbps", outbound.get("up_mbps"));
                }
                if (outbound.get("down_mbps") != null) {
                    server.put("down_mbps", outbound.get("down_mbps"));
                }
                applyTls(server, outbound);
            }
            case "anytls" -> {
                server.put("type", "anytls");
                credential = str(outbound.get("password"));
                applyTls(server, outbound);
                if (!server.containsKey("network")) {
                    server.put("network", "tcp");
                }
            }
            default -> {
                return null;
            }
        }

        if (credential == null || credential.isEmpty()) {
            return null;
        }
        if (!server.containsKey("network")) {
            server.put("network", "tcp");
        }
        return new Resolved(server, credential);
    }

    @SuppressWarnings("unchecked")
    private static void applyTls(Map<String, Object> server, Map<String, Object> outbound) {
        Object tlsObj = outbound.get("tls");
        if (!(tlsObj instanceof Map<?, ?> t)) {
            return;
        }
        if (!Boolean.TRUE.equals(t.get("enabled")) && t.get("server_name") == null && t.get("reality") == null) {
            return;
        }
        Map<String, Object> tlsSettings = new LinkedHashMap<>();
        if (t.get("server_name") != null) {
            String sni = str(t.get("server_name"));
            tlsSettings.put("server_name", sni);
            server.put("server_name", sni);
        }
        boolean insecure = Boolean.TRUE.equals(t.get("insecure"));
        if (insecure) {
            tlsSettings.put("allow_insecure", 1);
            server.put("allow_insecure", 1);
            server.put("insecure", 1);
        }
        Object utls = t.get("utls");
        if (utls instanceof Map<?, ?> u && u.get("fingerprint") != null) {
            tlsSettings.put("fingerprint", u.get("fingerprint"));
        }
        Object reality = t.get("reality");
        boolean realityEnabled = reality instanceof Map<?, ?> r && Boolean.TRUE.equals(r.get("enabled"));
        if (realityEnabled) {
            Map<?, ?> r = (Map<?, ?>) reality;
            if (r.get("public_key") != null) {
                tlsSettings.put("public_key", r.get("public_key"));
            }
            if (r.get("short_id") != null) {
                tlsSettings.put("short_id", r.get("short_id"));
            }
            server.put("tls", 2);
        } else {
            server.put("tls", 1);
        }
        server.put("tls_settings", tlsSettings);
        server.put("tlsSettings", tlsSettings);
    }

    @SuppressWarnings("unchecked")
    private static void applyTransport(Map<String, Object> server, Map<String, Object> outbound) {
        Object transport = outbound.get("transport");
        if (!(transport instanceof Map<?, ?> t)) {
            server.putIfAbsent("network", "tcp");
            return;
        }
        String type = str(t.get("type")).toLowerCase();
        if (type.isEmpty()) {
            server.putIfAbsent("network", "tcp");
            return;
        }
        server.put("network", type);
        Map<String, Object> settings = new LinkedHashMap<>();
        if ("ws".equals(type)) {
            if (t.get("path") != null) {
                settings.put("path", t.get("path"));
            }
            if (t.get("headers") instanceof Map<?, ?> h) {
                settings.put("headers", h);
            }
        } else if ("grpc".equals(type)) {
            if (t.get("service_name") != null) {
                settings.put("serviceName", t.get("service_name"));
                settings.put("service_name", t.get("service_name"));
            }
        } else if ("http".equals(type) || "httpupgrade".equals(type) || "xhttp".equals(type)) {
            if (t.get("path") != null) {
                settings.put("path", t.get("path"));
            }
            if (t.get("headers") instanceof Map<?, ?> h) {
                settings.put("headers", h);
            }
        }
        if (!settings.isEmpty()) {
            server.put("network_settings", settings);
            server.put("networkSettings", settings);
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
