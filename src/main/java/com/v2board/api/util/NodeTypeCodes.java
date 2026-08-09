package com.v2board.api.util;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Short type codes for node API identity JSON field {@code t}.
 */
public final class NodeTypeCodes {

    private static final Map<String, String> CODE_TO_TYPE = Map.ofEntries(
            Map.entry("vn", "v2node"),
            Map.entry("ss", "shadowsocks"),
            Map.entry("vm", "vmess"),
            Map.entry("vl", "vless"),
            Map.entry("tr", "trojan"),
            Map.entry("hy", "hysteria"),
            Map.entry("tu", "tuic"),
            Map.entry("at", "anytls")
    );

    private static final Map<String, String> TYPE_TO_CODE = Map.ofEntries(
            Map.entry("v2node", "vn"),
            Map.entry("shadowsocks", "ss"),
            Map.entry("vmess", "vm"),
            Map.entry("vless", "vl"),
            Map.entry("trojan", "tr"),
            Map.entry("hysteria", "hy"),
            Map.entry("tuic", "tu"),
            Map.entry("anytls", "at")
    );

    private NodeTypeCodes() {
    }

    /** Resolve short code or full type name to canonical node type (e.g. {@code vn} → {@code v2node}). */
    public static String toNodeType(String codeOrType) {
        if (!StringUtils.hasText(codeOrType)) {
            return "";
        }
        String t = codeOrType.trim().toLowerCase(Locale.ROOT);
        if ("v2ray".equals(t)) {
            t = "vmess";
        } else if ("hysteria2".equals(t)) {
            t = "hysteria";
        }
        String fromCode = CODE_TO_TYPE.get(t);
        if (fromCode != null) {
            return fromCode;
        }
        if (TYPE_TO_CODE.containsKey(t)) {
            return t;
        }
        return t;
    }

    public static String toCode(String nodeType) {
        String t = toNodeType(nodeType);
        return TYPE_TO_CODE.getOrDefault(t, t);
    }
}
