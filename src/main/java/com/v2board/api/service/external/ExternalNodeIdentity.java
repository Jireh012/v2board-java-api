package com.v2board.api.service.external;

import com.v2board.api.util.Helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Logical identity for third-party subscribe nodes: type|server|port|uuid-or-password.
 */
public final class ExternalNodeIdentity {

    private ExternalNodeIdentity() {
    }

    public static String logicalKey(Map<String, Object> outbound) {
        if (outbound == null) {
            return "";
        }
        String type = str(outbound.get("type")).toLowerCase(Locale.ROOT).trim();
        String server = str(outbound.get("server")).toLowerCase(Locale.ROOT).trim();
        String port = normalizePort(outbound.get("server_port"));
        String uuid = str(outbound.get("uuid")).trim();
        String password = str(outbound.get("password")).trim();
        String credential = !uuid.isEmpty() ? uuid : password;
        return type + "|" + server + "|" + port + "|" + credential;
    }

    /** SHA-256 hex prefix (32 chars), matching prior fingerprint column width. */
    public static String fingerprint(Map<String, Object> outbound) {
        return fingerprintFromKey(logicalKey(outbound));
    }

    public static String fingerprintFromKey(String logicalKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(logicalKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (Exception e) {
            return Integer.toHexString(logicalKey != null ? logicalKey.hashCode() : 0);
        }
    }

    /**
     * Apply display name to server map fields used by protocol builders.
     */
    @SuppressWarnings("unchecked")
    public static void applyDisplayName(Map<String, Object> server, String name) {
        if (server == null || name == null) {
            return;
        }
        server.put("name", name);
        Object clash = server.get("clash_proxy");
        if (clash instanceof Map<?, ?> clashMap) {
            ((Map<String, Object>) clashMap).put("name", name);
        }
        Object outbound = server.get("singbox_outbound");
        if (outbound instanceof Map<?, ?> outboundMap) {
            ((Map<String, Object>) outboundMap).put("tag", name);
        }
        Object shareUri = server.get("share_uri");
        if (shareUri != null) {
            String rewritten = rewriteShareUriName(String.valueOf(shareUri), name);
            if (rewritten != null) {
                server.put("share_uri", rewritten);
            }
        }
    }

    static String rewriteShareUriName(String shareUri, String newName) {
        if (shareUri == null || shareUri.isBlank() || newName == null) {
            return shareUri;
        }
        String uri = shareUri.trim();
        boolean crlf = uri.endsWith("\r\n");
        boolean lf = !crlf && uri.endsWith("\n");
        if (crlf) {
            uri = uri.substring(0, uri.length() - 2);
        } else if (lf) {
            uri = uri.substring(0, uri.length() - 1);
        }
        int hash = uri.indexOf('#');
        String base = hash >= 0 ? uri.substring(0, hash) : uri;
        String encoded = Helper.encodeURIComponent(newName);
        String result = base + "#" + encoded;
        if (crlf) {
            return result + "\r\n";
        }
        if (lf) {
            return result + "\n";
        }
        return result;
    }

    private static String normalizePort(Object port) {
        if (port == null) {
            return "";
        }
        if (port instanceof Number n) {
            return String.valueOf(n.intValue());
        }
        String s = String.valueOf(port).trim();
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return s;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
