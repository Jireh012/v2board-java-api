package com.v2board.api.service.external;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clash proxy ↔ sing-box outbound 基础转换。
 */
public final class ClashProxyConverter {

    private ClashProxyConverter() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> clashToSingbox(Map<String, Object> proxy) {
        if (proxy == null) {
            return null;
        }
        String type = str(proxy.get("type")).toLowerCase();
        String name = str(proxy.get("name"));
        String server = str(proxy.get("server"));
        int port = intVal(proxy.get("port"));
        if (server.isEmpty() || port <= 0) {
            return null;
        }
        return switch (type) {
            case "ss", "shadowsocks" -> {
                Map<String, Object> out = base("shadowsocks", name, server, port);
                out.put("method", proxy.get("cipher"));
                out.put("password", proxy.get("password"));
                yield out;
            }
            case "vmess" -> {
                Map<String, Object> out = base("vmess", name, server, port);
                out.put("uuid", proxy.get("uuid"));
                out.put("security", proxy.getOrDefault("cipher", "auto"));
                out.put("alter_id", intVal(proxy.get("alterId")));
                applyClashTls(out, proxy);
                applyClashTransport(out, proxy);
                yield out;
            }
            case "vless" -> {
                Map<String, Object> out = base("vless", name, server, port);
                out.put("uuid", proxy.get("uuid"));
                out.put("packet_encoding", "xudp");
                if (proxy.get("flow") != null) {
                    out.put("flow", proxy.get("flow"));
                }
                applyClashTls(out, proxy);
                applyClashTransport(out, proxy);
                yield out;
            }
            case "trojan" -> {
                Map<String, Object> out = base("trojan", name, server, port);
                out.put("password", proxy.get("password"));
                applyClashTls(out, proxy);
                applyClashTransport(out, proxy);
                yield out;
            }
            case "hysteria2" -> {
                Map<String, Object> out = base("hysteria2", name, server, port);
                out.put("password", proxy.get("password"));
                applyClashTls(out, proxy);
                if (proxy.get("obfs") != null) {
                    Map<String, Object> obfs = new LinkedHashMap<>();
                    obfs.put("type", proxy.get("obfs"));
                    if (proxy.get("obfs-password") != null) {
                        obfs.put("password", proxy.get("obfs-password"));
                    }
                    out.put("obfs", obfs);
                }
                yield out;
            }
            case "hysteria" -> {
                Map<String, Object> out = base("hysteria", name, server, port);
                out.put("auth_str", proxy.getOrDefault("auth_str", proxy.get("auth-str")));
                out.put("up_mbps", proxy.getOrDefault("up", proxy.get("up_mbps")));
                out.put("down_mbps", proxy.getOrDefault("down", proxy.get("down_mbps")));
                applyClashTls(out, proxy);
                yield out;
            }
            case "tuic" -> {
                Map<String, Object> out = base("tuic", name, server, port);
                out.put("uuid", proxy.get("uuid"));
                out.put("password", proxy.get("password"));
                if (proxy.get("congestion-controller") != null) {
                    out.put("congestion_control", proxy.get("congestion-controller"));
                }
                if (proxy.get("udp-relay-mode") != null) {
                    out.put("udp_relay_mode", proxy.get("udp-relay-mode"));
                }
                applyClashTls(out, proxy);
                yield out;
            }
            case "anytls" -> {
                Map<String, Object> out = base("anytls", name, server, port);
                out.put("password", proxy.get("password"));
                applyClashTls(out, proxy);
                yield out;
            }
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> singboxToClash(Map<String, Object> outbound) {
        if (outbound == null) {
            return null;
        }
        String type = str(outbound.get("type")).toLowerCase();
        String name = str(outbound.get("tag"));
        String server = str(outbound.get("server"));
        Object port = outbound.get("server_port");
        if (server.isEmpty() || port == null) {
            return null;
        }
        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("name", name);
        proxy.put("server", server);
        proxy.put("port", port);
        proxy.put("udp", true);
        switch (type) {
            case "shadowsocks" -> {
                proxy.put("type", "ss");
                proxy.put("cipher", outbound.get("method"));
                proxy.put("password", outbound.get("password"));
            }
            case "vmess" -> {
                proxy.put("type", "vmess");
                proxy.put("uuid", outbound.get("uuid"));
                proxy.put("alterId", outbound.getOrDefault("alter_id", 0));
                proxy.put("cipher", outbound.getOrDefault("security", "auto"));
                applySingboxTlsToClash(proxy, outbound);
                applySingboxTransportToClash(proxy, outbound);
            }
            case "vless" -> {
                proxy.put("type", "vless");
                proxy.put("uuid", outbound.get("uuid"));
                if (outbound.get("flow") != null) {
                    proxy.put("flow", outbound.get("flow"));
                }
                applySingboxTlsToClash(proxy, outbound);
                applySingboxTransportToClash(proxy, outbound);
            }
            case "trojan" -> {
                proxy.put("type", "trojan");
                proxy.put("password", outbound.get("password"));
                applySingboxTlsToClash(proxy, outbound);
                applySingboxTransportToClash(proxy, outbound);
            }
            case "hysteria2" -> {
                proxy.put("type", "hysteria2");
                proxy.put("password", outbound.get("password"));
                applySingboxTlsToClash(proxy, outbound);
                Object obfs = outbound.get("obfs");
                if (obfs instanceof Map<?, ?> m) {
                    proxy.put("obfs", m.get("type"));
                    proxy.put("obfs-password", m.get("password"));
                }
            }
            case "hysteria" -> {
                proxy.put("type", "hysteria");
                proxy.put("auth_str", outbound.get("auth_str"));
                proxy.put("up", outbound.get("up_mbps"));
                proxy.put("down", outbound.get("down_mbps"));
                applySingboxTlsToClash(proxy, outbound);
            }
            case "tuic" -> {
                proxy.put("type", "tuic");
                proxy.put("uuid", outbound.get("uuid"));
                proxy.put("password", outbound.get("password"));
                if (outbound.get("congestion_control") != null) {
                    proxy.put("congestion-controller", outbound.get("congestion_control"));
                }
                applySingboxTlsToClash(proxy, outbound);
            }
            case "anytls" -> {
                proxy.put("type", "anytls");
                proxy.put("password", outbound.get("password"));
                applySingboxTlsToClash(proxy, outbound);
            }
            default -> {
                return null;
            }
        }
        return proxy;
    }

    private static Map<String, Object> base(String type, String name, String server, int port) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        out.put("tag", name.isEmpty() ? type + "-" + server + "-" + port : name);
        out.put("server", server);
        out.put("server_port", port);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void applyClashTls(Map<String, Object> out, Map<String, Object> proxy) {
        boolean tls = Boolean.TRUE.equals(proxy.get("tls"))
                || "tls".equalsIgnoreCase(str(proxy.get("tls")))
                || proxy.get("sni") != null
                || proxy.get("servername") != null
                || proxy.get("reality-opts") != null
                || "trojan".equalsIgnoreCase(str(proxy.get("type")))
                || "hysteria2".equalsIgnoreCase(str(proxy.get("type")))
                || "hysteria".equalsIgnoreCase(str(proxy.get("type")))
                || "tuic".equalsIgnoreCase(str(proxy.get("type")))
                || "anytls".equalsIgnoreCase(str(proxy.get("type")));
        if (!tls) {
            return;
        }
        Map<String, Object> tlsObj = new LinkedHashMap<>();
        tlsObj.put("enabled", true);
        String sni = str(proxy.get("servername"));
        if (sni.isEmpty()) {
            sni = str(proxy.get("sni"));
        }
        if (!sni.isEmpty()) {
            tlsObj.put("server_name", sni);
        }
        if (Boolean.TRUE.equals(proxy.get("skip-cert-verify"))) {
            tlsObj.put("insecure", true);
        }
        Object reality = proxy.get("reality-opts");
        if (reality instanceof Map<?, ?> r) {
            Map<String, Object> realityObj = new LinkedHashMap<>();
            realityObj.put("enabled", true);
            realityObj.put("public_key", r.get("public-key"));
            realityObj.put("short_id", r.get("short-id"));
            tlsObj.put("reality", realityObj);
        }
        if (proxy.get("client-fingerprint") != null) {
            tlsObj.put("utls", Map.of("enabled", true, "fingerprint", proxy.get("client-fingerprint")));
        }
        out.put("tls", tlsObj);
    }

    @SuppressWarnings("unchecked")
    private static void applyClashTransport(Map<String, Object> out, Map<String, Object> proxy) {
        String network = str(proxy.get("network")).toLowerCase();
        if (network.isEmpty() || "tcp".equals(network)) {
            return;
        }
        Map<String, Object> transport = new LinkedHashMap<>();
        if ("ws".equals(network)) {
            transport.put("type", "ws");
            Object opts = proxy.get("ws-opts");
            if (opts instanceof Map<?, ?> m) {
                if (m.get("path") != null) {
                    transport.put("path", m.get("path"));
                }
                Object headers = m.get("headers");
                if (headers instanceof Map<?, ?> h) {
                    transport.put("headers", h);
                }
            }
        } else if ("grpc".equals(network)) {
            transport.put("type", "grpc");
            Object opts = proxy.get("grpc-opts");
            if (opts instanceof Map<?, ?> m && m.get("grpc-service-name") != null) {
                transport.put("service_name", m.get("grpc-service-name"));
            }
        } else if ("http".equals(network)) {
            transport.put("type", "http");
            Object opts = proxy.get("http-opts");
            if (opts instanceof Map<?, ?> m) {
                if (m.get("path") != null) {
                    transport.put("path", m.get("path"));
                }
            }
        } else {
            return;
        }
        out.put("transport", transport);
    }

    @SuppressWarnings("unchecked")
    private static void applySingboxTlsToClash(Map<String, Object> proxy, Map<String, Object> outbound) {
        Object tls = outbound.get("tls");
        if (!(tls instanceof Map<?, ?> t) || !Boolean.TRUE.equals(t.get("enabled"))) {
            return;
        }
        proxy.put("tls", true);
        if (t.get("server_name") != null) {
            proxy.put("servername", t.get("server_name"));
            proxy.put("sni", t.get("server_name"));
        }
        if (Boolean.TRUE.equals(t.get("insecure"))) {
            proxy.put("skip-cert-verify", true);
        }
        Object reality = t.get("reality");
        if (reality instanceof Map<?, ?> r && Boolean.TRUE.equals(r.get("enabled"))) {
            Map<String, Object> opts = new LinkedHashMap<>();
            opts.put("public-key", r.get("public_key"));
            opts.put("short-id", r.get("short_id"));
            proxy.put("reality-opts", opts);
        }
        Object utls = t.get("utls");
        if (utls instanceof Map<?, ?> u && u.get("fingerprint") != null) {
            proxy.put("client-fingerprint", u.get("fingerprint"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void applySingboxTransportToClash(Map<String, Object> proxy, Map<String, Object> outbound) {
        Object transport = outbound.get("transport");
        if (!(transport instanceof Map<?, ?> t)) {
            return;
        }
        String type = str(t.get("type")).toLowerCase();
        if (type.isEmpty()) {
            return;
        }
        proxy.put("network", type.equals("ws") ? "ws" : type);
        if ("ws".equals(type)) {
            Map<String, Object> opts = new LinkedHashMap<>();
            if (t.get("path") != null) {
                opts.put("path", t.get("path"));
            }
            if (t.get("headers") != null) {
                opts.put("headers", t.get("headers"));
            }
            proxy.put("ws-opts", opts);
        } else if ("grpc".equals(type)) {
            Map<String, Object> opts = new LinkedHashMap<>();
            opts.put("grpc-service-name", t.get("service_name"));
            proxy.put("grpc-opts", opts);
        } else if ("http".equals(type)) {
            Map<String, Object> opts = new LinkedHashMap<>();
            Object path = t.get("path");
            if (path instanceof List<?> l) {
                opts.put("path", l);
            } else if (path != null) {
                opts.put("path", List.of(path));
            }
            proxy.put("http-opts", opts);
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int intVal(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
}
