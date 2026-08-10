package com.v2board.api.service.rules;

import java.util.Locale;

/**
 * Map ACL4SSR list entries to client dialect rule lines.
 */
public final class Acl4ssrRuleDialect {

    private Acl4ssrRuleDialect() {
    }

    public static String toClash(Acl4ssrListParser.Entry e, String policy) {
        if (e == null || e.value() == null || e.value().isBlank()) {
            return null;
        }
        String v = e.value();
        return switch (e.kind()) {
            case "domain-suffix" -> "DOMAIN-SUFFIX," + v + "," + policy;
            case "domain" -> "DOMAIN," + v + "," + policy;
            case "domain-keyword" -> "DOMAIN-KEYWORD," + v + "," + policy;
            case "ip-cidr" -> "IP-CIDR," + v + "," + policy + ",no-resolve";
            case "ip-cidr6" -> "IP-CIDR6," + v + "," + policy + ",no-resolve";
            case "user-agent" -> "USER-AGENT," + v + "," + policy;
            case "process-name" -> "PROCESS-NAME," + v + "," + policy;
            default -> null;
        };
    }

    public static String toSurge(Acl4ssrListParser.Entry e, String policy) {
        if (e == null || e.value() == null || e.value().isBlank()) {
            return null;
        }
        String v = e.value();
        return switch (e.kind()) {
            case "domain-suffix" -> "DOMAIN-SUFFIX," + v + "," + policy;
            case "domain" -> "DOMAIN," + v + "," + policy;
            case "domain-keyword" -> "DOMAIN-KEYWORD," + v + "," + policy;
            case "ip-cidr" -> "IP-CIDR," + v + "," + policy + ",no-resolve";
            case "ip-cidr6" -> "IP-CIDR6," + v + "," + policy + ",no-resolve";
            case "user-agent" -> "USER-AGENT," + v + "," + policy;
            case "process-name" -> "PROCESS-NAME," + v + "," + policy;
            default -> null;
        };
    }

    public static String toQuantumultX(Acl4ssrListParser.Entry e, String policy) {
        if (e == null || e.value() == null || e.value().isBlank()) {
            return null;
        }
        String v = e.value();
        String p = normalizeQxPolicy(policy);
        return switch (e.kind()) {
            case "domain-suffix" -> "host-suffix, " + v + ", " + p;
            case "domain" -> "host, " + v + ", " + p;
            case "domain-keyword" -> "host-keyword, " + v + ", " + p;
            case "ip-cidr" -> "ip-cidr, " + v + ", " + p;
            case "ip-cidr6" -> "ip6-cidr, " + v + ", " + p;
            case "user-agent" -> "user-agent, " + v + ", " + p;
            default -> null;
        };
    }

    public static String normalizeQxPolicy(String policy) {
        if (policy == null) {
            return "direct";
        }
        String p = policy.trim();
        if ("DIRECT".equalsIgnoreCase(p)) {
            return "direct";
        }
        if ("REJECT".equalsIgnoreCase(p) || "REJECT-DROP".equalsIgnoreCase(p)) {
            return "reject";
        }
        return p;
    }

    public static String normalizeConfBuiltin(String name) {
        if (name == null) {
            return "";
        }
        String n = name.trim();
        if ("DIRECT".equalsIgnoreCase(n)) {
            return "DIRECT";
        }
        if ("REJECT".equalsIgnoreCase(n) || "REJECT-DROP".equalsIgnoreCase(n)) {
            return "REJECT";
        }
        return n;
    }

    /** Map node-filter regex / .* to conf placeholders when possible. */
    public static String confFilterPlaceholder(String member) {
        if (member == null || member.isBlank() || ".*".equals(member.trim())) {
            return "$proxy_group";
        }
        String m = member.toLowerCase(Locale.ROOT);
        if (m.contains("港") || m.contains("hk") || m.contains("hong")) {
            return "$proxy_group_hk";
        }
        if (m.contains("台") || m.contains("tw") || m.contains("taiwan")) {
            return "$proxy_group_tw";
        }
        if (m.contains("新加") || m.contains("狮城") || m.contains("sg") || m.contains("singapore")) {
            return "$proxy_group_sg";
        }
        if (m.contains("日本") || m.contains("jp") || m.contains("japan") || m.contains("东京") || m.contains("大阪")) {
            return "$proxy_group_jp";
        }
        if (m.contains("美") || m.contains("us") || m.contains("united states") || m.contains("america")) {
            return "$proxy_group_us";
        }
        if (m.contains("韩") || m.contains("韓") || m.contains("kr") || m.contains("korea") || m.contains("首尔")) {
            return "$proxy_group_kr";
        }
        if (m.contains("奈飞") || m.contains("netflix") || m.contains("nf")) {
            return "$proxy_group_nf";
        }
        return "$proxy_group";
    }

    public static String mapClashGroupType(String type) {
        if (type == null || type.isBlank()) {
            return "select";
        }
        String t = type.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "url-test", "urltest" -> "url-test";
            case "fallback" -> "fallback";
            case "load-balance", "loadbalance" -> "load-balance";
            default -> "select";
        };
    }

    public static String mapSingboxGroupType(String type) {
        String t = mapClashGroupType(type);
        return switch (t) {
            case "url-test" -> "urltest";
            case "fallback" -> "urltest"; // sing-box has no fallback; approximate
            case "load-balance" -> "selector";
            default -> "selector";
        };
    }
}
