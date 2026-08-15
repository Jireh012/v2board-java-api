package com.v2board.api.service.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分享链接 ↔ sing-box outbound 基础转换。
 */
public final class ShareUriConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShareUriConverter() {
    }

    public static Map<String, Object> uriToSingbox(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String trimmed = uri.trim();
        int schemeIdx = trimmed.indexOf("://");
        if (schemeIdx <= 0) {
            return null;
        }
        String scheme = trimmed.substring(0, schemeIdx).toLowerCase();
        return switch (scheme) {
            case "ss" -> parseSs(trimmed);
            case "vmess" -> parseVmess(trimmed);
            case "vless" -> parseVless(trimmed);
            case "trojan" -> parseTrojan(trimmed);
            case "hysteria2", "hy2" -> parseHysteria2(trimmed);
            case "tuic" -> parseTuic(trimmed);
            case "anytls" -> parseAnytls(trimmed);
            default -> null;
        };
    }

    public static String singboxToUri(Map<String, Object> outbound) {
        if (outbound == null) {
            return null;
        }
        String type = str(outbound.get("type")).toLowerCase();
        return switch (type) {
            case "shadowsocks" -> ssToUri(outbound);
            case "vmess" -> vmessToUri(outbound);
            case "vless" -> vlessToUri(outbound);
            case "trojan" -> trojanToUri(outbound);
            case "hysteria2" -> hysteria2ToUri(outbound);
            case "tuic" -> tuicToUri(outbound);
            case "anytls" -> anytlsToUri(outbound);
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseSs(String uri) {
        try {
            String rest = uri.substring("ss://".length());
            String name = "";
            int hash = rest.indexOf('#');
            if (hash >= 0) {
                name = urlDecode(rest.substring(hash + 1));
                rest = rest.substring(0, hash);
            }
            String userInfo;
            String hostPort;
            if (rest.contains("@")) {
                String[] parts = rest.split("@", 2);
                String decoded = tryBase64(parts[0]);
                userInfo = decoded != null ? decoded : parts[0];
                hostPort = parts[1];
            } else {
                String decoded = tryBase64(rest);
                if (decoded == null || !decoded.contains("@")) {
                    return null;
                }
                String[] parts = decoded.split("@", 2);
                userInfo = parts[0];
                hostPort = parts[1];
            }
            int colon = userInfo.indexOf(':');
            if (colon < 0) {
                return null;
            }
            String method = userInfo.substring(0, colon);
            String password = userInfo.substring(colon + 1);
            HostPort hp = parseHostPort(hostPort);
            if (hp == null) {
                return null;
            }
            Map<String, Object> out = baseOutbound("shadowsocks", name, hp);
            out.put("method", method);
            out.put("password", password);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseVmess(String uri) {
        try {
            String payload = uri.substring("vmess://".length()).trim();
            String json = tryBase64(payload);
            if (json == null) {
                return null;
            }
            Map<String, Object> cfg = MAPPER.readValue(json, new TypeReference<>() {});
            String name = str(cfg.get("ps"));
            if (name.isEmpty()) {
                name = str(cfg.get("remark"));
            }
            HostPort hp = new HostPort(str(cfg.get("add")), parseInt(cfg.get("port"), 0));
            if (hp.host.isEmpty() || hp.port <= 0) {
                return null;
            }
            Map<String, Object> out = baseOutbound("vmess", name, hp);
            out.put("uuid", str(cfg.get("id")));
            out.put("security", strOr(cfg.get("scy"), "auto"));
            out.put("alter_id", parseInt(cfg.get("aid"), 0));
            String net = strOr(cfg.get("net"), "tcp");
            String tls = str(cfg.get("tls"));
            if ("tls".equalsIgnoreCase(tls) || "1".equals(tls)) {
                Map<String, Object> tlsObj = new LinkedHashMap<>();
                tlsObj.put("enabled", true);
                String sni = str(cfg.get("sni"));
                if (sni.isEmpty()) {
                    sni = str(cfg.get("host"));
                }
                if (!sni.isEmpty()) {
                    tlsObj.put("server_name", sni);
                }
                out.put("tls", tlsObj);
            }
            applyUriTransport(out, net, str(cfg.get("path")), str(cfg.get("host")), str(cfg.get("type")));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> parseVless(String uri) {
        return parseUserAtHostQuery(uri, "vless", true);
    }

    private static Map<String, Object> parseTrojan(String uri) {
        return parseUserAtHostQuery(uri, "trojan", false);
    }

    private static Map<String, Object> parseHysteria2(String uri) {
        try {
            String scheme = uri.startsWith("hy2://") ? "hy2://" : "hysteria2://";
            String rest = uri.substring(scheme.length());
            String name = "";
            int hash = rest.indexOf('#');
            if (hash >= 0) {
                name = urlDecode(rest.substring(hash + 1));
                rest = rest.substring(0, hash);
            }
            String query = "";
            int q = rest.indexOf('?');
            if (q >= 0) {
                query = rest.substring(q + 1);
                rest = rest.substring(0, q);
            }
            String password;
            String hostPort;
            if (rest.contains("@")) {
                String[] parts = rest.split("@", 2);
                password = urlDecode(parts[0]);
                hostPort = parts[1];
            } else {
                password = "";
                hostPort = rest;
            }
            HostPort hp = parseHostPort(hostPort);
            if (hp == null) {
                return null;
            }
            Map<String, String> params = parseQuery(query);
            Map<String, Object> out = baseOutbound("hysteria2", name, hp);
            out.put("password", password);
            Map<String, Object> tls = new LinkedHashMap<>();
            tls.put("enabled", true);
            if (params.containsKey("sni")) {
                tls.put("server_name", params.get("sni"));
            }
            if ("1".equals(params.get("insecure"))) {
                tls.put("insecure", true);
            }
            out.put("tls", tls);
            if (params.containsKey("obfs")) {
                Map<String, Object> obfs = new LinkedHashMap<>();
                obfs.put("type", params.get("obfs"));
                if (params.containsKey("obfs-password")) {
                    obfs.put("password", params.get("obfs-password"));
                }
                out.put("obfs", obfs);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> parseTuic(String uri) {
        try {
            ParsedUserHostQuery p = parseUserHostQuery(uri, "tuic://");
            if (p == null) {
                return null;
            }
            Map<String, Object> out = baseOutbound("tuic", p.name, p.hostPort);
            // uuid:password@host or uuid@host
            String user = p.user;
            String password = p.params.getOrDefault("password", user);
            if (user.contains(":")) {
                String[] up = user.split(":", 2);
                out.put("uuid", up[0]);
                out.put("password", up[1]);
            } else {
                out.put("uuid", user);
                out.put("password", password);
            }
            Map<String, Object> tls = new LinkedHashMap<>();
            tls.put("enabled", true);
            if (p.params.containsKey("sni")) {
                tls.put("server_name", p.params.get("sni"));
            }
            if ("1".equals(p.params.get("insecure"))) {
                tls.put("insecure", true);
            }
            out.put("tls", tls);
            if (p.params.containsKey("congestion_control")) {
                out.put("congestion_control", p.params.get("congestion_control"));
            }
            if (p.params.containsKey("udp_relay_mode")) {
                out.put("udp_relay_mode", p.params.get("udp_relay_mode"));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> parseAnytls(String uri) {
        try {
            ParsedUserHostQuery p = parseUserHostQuery(uri, "anytls://");
            if (p == null) {
                return null;
            }
            Map<String, Object> out = baseOutbound("anytls", p.name, p.hostPort);
            out.put("password", p.user);
            Map<String, Object> tls = new LinkedHashMap<>();
            tls.put("enabled", true);
            if (p.params.containsKey("sni")) {
                tls.put("server_name", p.params.get("sni"));
            }
            if ("1".equals(p.params.get("insecure"))) {
                tls.put("insecure", true);
            }
            out.put("tls", tls);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> parseUserAtHostQuery(String uri, String type, boolean isVless) {
        try {
            ParsedUserHostQuery p = parseUserHostQuery(uri, type + "://");
            if (p == null) {
                return null;
            }
            Map<String, Object> out = baseOutbound(type, p.name, p.hostPort);
            if (isVless) {
                out.put("uuid", p.user);
                out.put("packet_encoding", "xudp");
            } else {
                out.put("password", p.user);
            }
            String security = p.params.getOrDefault("security", "");
            boolean tlsOn = "tls".equalsIgnoreCase(security) || "reality".equalsIgnoreCase(security)
                    || "trojan".equals(type);
            if (tlsOn) {
                Map<String, Object> tls = new LinkedHashMap<>();
                tls.put("enabled", true);
                String sni = p.params.getOrDefault("sni", p.params.getOrDefault("peer", ""));
                if (!sni.isEmpty()) {
                    tls.put("server_name", sni);
                }
                if ("1".equals(p.params.get("allowInsecure")) || "1".equals(p.params.get("insecure"))) {
                    tls.put("insecure", true);
                }
                if ("reality".equalsIgnoreCase(security)) {
                    Map<String, Object> reality = new LinkedHashMap<>();
                    reality.put("enabled", true);
                    if (p.params.containsKey("pbk")) {
                        reality.put("public_key", p.params.get("pbk"));
                    }
                    if (p.params.containsKey("sid")) {
                        reality.put("short_id", p.params.get("sid"));
                    }
                    tls.put("reality", reality);
                }
                if (p.params.containsKey("fp")) {
                    tls.put("utls", Map.of("enabled", true, "fingerprint", p.params.get("fp")));
                }
                out.put("tls", tls);
            }
            if (isVless && p.params.containsKey("flow") && !p.params.get("flow").isEmpty()) {
                out.put("flow", p.params.get("flow"));
            }
            String net = p.params.getOrDefault("type", p.params.getOrDefault("network", "tcp"));
            applyUriTransport(out, net, p.params.getOrDefault("path", ""),
                    p.params.getOrDefault("host", ""), p.params.getOrDefault("headerType", ""));
            if (p.params.containsKey("serviceName")) {
                Map<String, Object> transport = outboundTransport(out);
                transport.put("type", "grpc");
                transport.put("service_name", p.params.get("serviceName"));
                out.put("transport", transport);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> outboundTransport(Map<String, Object> out) {
        Object t = out.get("transport");
        if (t instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        Map<String, Object> transport = new LinkedHashMap<>();
        out.put("transport", transport);
        return transport;
    }

    private static void applyUriTransport(Map<String, Object> out, String net, String path, String host, String headerType) {
        if (net == null || net.isBlank() || "tcp".equalsIgnoreCase(net)) {
            if ("http".equalsIgnoreCase(headerType)) {
                Map<String, Object> transport = new LinkedHashMap<>();
                transport.put("type", "http");
                if (!path.isEmpty()) {
                    transport.put("path", List.of(path));
                }
                if (!host.isEmpty()) {
                    transport.put("host", List.of(host));
                }
                out.put("transport", transport);
            }
            return;
        }
        Map<String, Object> transport = new LinkedHashMap<>();
        if ("ws".equalsIgnoreCase(net) || "websocket".equalsIgnoreCase(net)) {
            transport.put("type", "ws");
            if (!path.isEmpty()) {
                transport.put("path", path);
            }
            if (!host.isEmpty()) {
                transport.put("headers", Map.of("Host", host));
            }
            out.put("transport", transport);
        } else if ("grpc".equalsIgnoreCase(net)) {
            transport.put("type", "grpc");
            if (!path.isEmpty()) {
                transport.put("service_name", path);
            }
            out.put("transport", transport);
        } else if ("httpupgrade".equalsIgnoreCase(net) || "http".equalsIgnoreCase(net)) {
            transport.put("type", "httpupgrade");
            if (!path.isEmpty()) {
                transport.put("path", path);
            }
            if (!host.isEmpty()) {
                transport.put("host", host);
            }
            out.put("transport", transport);
        }
    }

    private static String ssToUri(Map<String, Object> o) {
        String method = str(o.get("method"));
        String password = str(o.get("password"));
        String userInfo = Base64.getEncoder().encodeToString((method + ":" + password).getBytes(StandardCharsets.UTF_8))
                .replace("=", "");
        String tag = str(o.get("tag"));
        String uri = "ss://" + userInfo + "@" + o.get("server") + ":" + o.get("server_port");
        if (!tag.isEmpty()) {
            uri += "#" + urlEncode(tag);
        }
        return uri;
    }

    private static String vmessToUri(Map<String, Object> o) {
        try {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("v", "2");
            cfg.put("ps", str(o.get("tag")));
            cfg.put("add", str(o.get("server")));
            cfg.put("port", o.get("server_port"));
            cfg.put("id", str(o.get("uuid")));
            cfg.put("aid", o.getOrDefault("alter_id", 0));
            cfg.put("scy", o.getOrDefault("security", "auto"));
            cfg.put("net", transportType(o));
            Object tls = o.get("tls");
            if (tls instanceof Map<?, ?> t && Boolean.TRUE.equals(t.get("enabled"))) {
                cfg.put("tls", "tls");
                if (t.get("server_name") != null) {
                    cfg.put("sni", t.get("server_name"));
                }
                if (Boolean.TRUE.equals(t.get("insecure"))) {
                    cfg.put("skip-cert-verify", true);
                    cfg.put("verify", false);
                    cfg.put("allowInsecure", 1);
                }
            } else {
                cfg.put("tls", "");
            }
            Map<String, Object> transport = asMap(o.get("transport"));
            if (transport != null) {
                if (transport.get("path") != null) {
                    cfg.put("path", transport.get("path") instanceof List<?> l && !l.isEmpty() ? l.get(0) : transport.get("path"));
                }
                if (transport.get("service_name") != null && cfg.get("path") == null) {
                    cfg.put("path", transport.get("service_name"));
                }
                Object headers = transport.get("headers");
                if (headers instanceof Map<?, ?> h && h.get("Host") != null) {
                    cfg.put("host", h.get("Host"));
                }
            }
            String json = MAPPER.writeValueAsString(cfg);
            return "vmess://" + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static String vlessToUri(Map<String, Object> o) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("encryption", "none");
        putTlsQuery(q, o, true);
        String net = transportType(o);
        if (!net.isEmpty()) {
            q.put("type", net);
        }
        putTransportQuery(q, o);
        if (o.get("flow") != null && !str(o.get("flow")).isEmpty()) {
            q.put("flow", str(o.get("flow")));
        }
        return "vless://" + o.get("uuid") + "@" + o.get("server") + ":" + o.get("server_port")
                + queryString(q) + fragment(o);
    }

    private static String trojanToUri(Map<String, Object> o) {
        Map<String, String> q = new LinkedHashMap<>();
        putTlsQuery(q, o, false);
        String net = transportType(o);
        if (!net.isEmpty() && !"tcp".equalsIgnoreCase(net)) {
            q.put("type", net);
        }
        putTransportQuery(q, o);
        return "trojan://" + urlEncode(str(o.get("password"))) + "@"
                + o.get("server") + ":" + o.get("server_port")
                + queryString(q) + fragment(o);
    }

    private static String hysteria2ToUri(Map<String, Object> o) {
        Map<String, String> q = new LinkedHashMap<>();
        putTlsQuery(q, o, false);
        Object obfs = o.get("obfs");
        if (obfs instanceof Map<?, ?> m) {
            if (m.get("type") != null) {
                q.put("obfs", str(m.get("type")));
            }
            if (m.get("password") != null) {
                q.put("obfs-password", str(m.get("password")));
            }
        }
        return "hysteria2://" + urlEncode(str(o.get("password"))) + "@"
                + o.get("server") + ":" + o.get("server_port")
                + queryString(q) + fragment(o);
    }

    private static String tuicToUri(Map<String, Object> o) {
        Map<String, String> q = new LinkedHashMap<>();
        putTlsQuery(q, o, false);
        if (o.get("congestion_control") != null) {
            q.put("congestion_control", str(o.get("congestion_control")));
        }
        if (o.get("udp_relay_mode") != null) {
            q.put("udp_relay_mode", str(o.get("udp_relay_mode")));
        }
        return "tuic://" + o.get("uuid") + ":" + urlEncode(str(o.get("password")))
                + "@" + o.get("server") + ":" + o.get("server_port")
                + queryString(q) + fragment(o);
    }

    private static String anytlsToUri(Map<String, Object> o) {
        Map<String, String> q = new LinkedHashMap<>();
        putTlsQuery(q, o, false);
        return "anytls://" + urlEncode(str(o.get("password"))) + "@"
                + o.get("server") + ":" + o.get("server_port")
                + queryString(q) + fragment(o);
    }

    private static void putTlsQuery(Map<String, String> q, Map<String, Object> o, boolean includeSecurity) {
        Object tls = o.get("tls");
        if (!(tls instanceof Map<?, ?> t)) {
            return;
        }
        boolean enabled = Boolean.TRUE.equals(t.get("enabled"))
                || t.get("server_name") != null
                || t.get("reality") != null;
        if (!enabled) {
            return;
        }
        Object reality = t.get("reality");
        boolean realityOn = reality instanceof Map<?, ?> r && Boolean.TRUE.equals(r.get("enabled"));
        if (includeSecurity) {
            q.put("security", realityOn ? "reality" : "tls");
        }
        if (realityOn) {
            Map<?, ?> r = (Map<?, ?>) reality;
            if (r.get("public_key") != null) {
                q.put("pbk", str(r.get("public_key")));
            }
            if (r.get("short_id") != null) {
                q.put("sid", str(r.get("short_id")));
            }
        }
        if (t.get("server_name") != null) {
            String sni = str(t.get("server_name"));
            q.put("sni", sni);
            q.put("peer", sni);
        }
        if (Boolean.TRUE.equals(t.get("insecure"))) {
            q.put("insecure", "1");
            q.put("allowInsecure", "1");
        }
        Object utls = t.get("utls");
        if (utls instanceof Map<?, ?> u && u.get("fingerprint") != null) {
            q.put("fp", str(u.get("fingerprint")));
        }
    }

    private static void putTransportQuery(Map<String, String> q, Map<String, Object> o) {
        Map<String, Object> transport = asMap(o.get("transport"));
        if (transport == null) {
            return;
        }
        if (transport.get("path") != null) {
            Object path = transport.get("path");
            if (path instanceof List<?> l && !l.isEmpty()) {
                q.put("path", String.valueOf(l.get(0)));
            } else {
                q.put("path", String.valueOf(path));
            }
        }
        if (transport.get("service_name") != null) {
            q.put("serviceName", str(transport.get("service_name")));
        }
        Object headers = transport.get("headers");
        if (headers instanceof Map<?, ?> h && h.get("Host") != null) {
            q.put("host", str(h.get("Host")));
        } else if (transport.get("host") != null) {
            Object host = transport.get("host");
            if (host instanceof List<?> l && !l.isEmpty()) {
                q.put("host", String.valueOf(l.get(0)));
            } else {
                q.put("host", String.valueOf(host));
            }
        }
    }

    private static String queryString(Map<String, String> q) {
        if (q == null || q.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append("&");
            }
            first = false;
            sb.append(urlEncode(e.getKey())).append("=").append(urlEncode(e.getValue()));
        }
        return sb.length() == 1 ? "" : sb.toString();
    }

    private static String fragment(Map<String, Object> o) {
        String tag = str(o.get("tag"));
        return tag.isEmpty() ? "" : "#" + urlEncode(tag);
    }

    private static String transportType(Map<String, Object> o) {
        Map<String, Object> transport = asMap(o.get("transport"));
        if (transport == null) {
            return "tcp";
        }
        return strOr(transport.get("type"), "tcp");
    }

    private static ParsedUserHostQuery parseUserHostQuery(String uri, String prefix) {
        if (!uri.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        String rest = uri.substring(prefix.length());
        String name = "";
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            name = urlDecode(rest.substring(hash + 1));
            rest = rest.substring(0, hash);
        }
        String query = "";
        int q = rest.indexOf('?');
        if (q >= 0) {
            query = rest.substring(q + 1);
            rest = rest.substring(0, q);
        }
        int at = rest.lastIndexOf('@');
        if (at < 0) {
            return null;
        }
        String user = urlDecode(rest.substring(0, at));
        HostPort hp = parseHostPort(rest.substring(at + 1));
        if (hp == null) {
            return null;
        }
        ParsedUserHostQuery p = new ParsedUserHostQuery();
        p.user = user;
        p.hostPort = hp;
        p.name = name;
        p.params = parseQuery(query);
        return p;
    }

    private static Map<String, Object> baseOutbound(String type, String name, HostPort hp) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        out.put("tag", name == null || name.isBlank() ? type + "-" + hp.host + "-" + hp.port : name);
        out.put("server", hp.host);
        out.put("server_port", hp.port);
        return out;
    }

    private static HostPort parseHostPort(String hostPort) {
        if (hostPort == null || hostPort.isBlank()) {
            return null;
        }
        String hp = hostPort.trim();
        if (hp.startsWith("[")) {
            int end = hp.indexOf(']');
            if (end < 0) {
                return null;
            }
            String host = hp.substring(1, end);
            String portPart = hp.substring(end + 1);
            if (!portPart.startsWith(":")) {
                return null;
            }
            return new HostPort(host, Integer.parseInt(portPart.substring(1)));
        }
        int colon = hp.lastIndexOf(':');
        if (colon <= 0) {
            return null;
        }
        return new HostPort(hp.substring(0, colon), Integer.parseInt(hp.substring(colon + 1)));
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return map;
        }
        for (String part : query.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq < 0) {
                map.put(urlDecode(part), "");
            } else {
                map.put(urlDecode(part.substring(0, eq)), urlDecode(part.substring(eq + 1)));
            }
        }
        return map;
    }

    private static String tryBase64(String raw) {
        try {
            String s = raw.replace('-', '+').replace('_', '/');
            int mod = s.length() % 4;
            if (mod > 0) {
                s = s + "====".substring(mod);
            }
            return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String strOr(Object o, String def) {
        String s = str(o);
        return s.isEmpty() ? def : s;
    }

    private static int parseInt(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }

    private static final class HostPort {
        final String host;
        final int port;

        HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static final class ParsedUserHostQuery {
        String user;
        HostPort hostPort;
        String name;
        Map<String, String> params;
    }
}
