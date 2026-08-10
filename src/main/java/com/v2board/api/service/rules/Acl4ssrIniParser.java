package com.v2board.api.service.rules;

import com.v2board.api.common.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parse Subconverter ACL4SSR Online INI ({@code ruleset=} / {@code custom_proxy_group=}).
 */
public final class Acl4ssrIniParser {

    private Acl4ssrIniParser() {
    }

    public record Ruleset(String policy, String source) {
        public boolean isHttpUrl() {
            String s = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
            return s.startsWith("http://") || s.startsWith("https://");
        }

        public boolean isGeoIp() {
            return source != null && source.trim().toUpperCase(Locale.ROOT).startsWith("[]GEOIP");
        }

        public boolean isFinal() {
            return source != null && source.trim().toUpperCase(Locale.ROOT).startsWith("[]FINAL");
        }

        /** GEOIP code after {@code []GEOIP,} e.g. CN / LAN. */
        public String geoIpCode() {
            if (!isGeoIp()) {
                return null;
            }
            String rest = source.trim().substring("[]GEOIP".length()).trim();
            if (rest.startsWith(",")) {
                rest = rest.substring(1).trim();
            }
            return rest.isEmpty() ? "CN" : rest;
        }
    }

    public record ProxyGroup(String name, String type, List<String> members) {
    }

    public record Model(List<Ruleset> rulesets, List<ProxyGroup> groups) {
    }

    public static boolean looksLikeIni(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("ruleset=") && lower.contains("custom_proxy_group=");
    }

    public static Model parse(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(500, "ACL4SSR INI 内容为空");
        }
        List<Ruleset> rulesets = new ArrayList<>();
        List<ProxyGroup> groups = new ArrayList<>();
        for (String rawLine : content.split("\\R", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                continue;
            }
            if (line.regionMatches(true, 0, "ruleset=", 0, 8)) {
                String body = line.substring(8).trim();
                int comma = body.indexOf(',');
                if (comma <= 0 || comma >= body.length() - 1) {
                    continue;
                }
                String policy = body.substring(0, comma).trim();
                String source = body.substring(comma + 1).trim();
                if (!policy.isEmpty() && !source.isEmpty()) {
                    rulesets.add(new Ruleset(policy, source));
                }
                continue;
            }
            if (line.regionMatches(true, 0, "custom_proxy_group=", 0, 19)) {
                String body = line.substring(19).trim();
                String[] parts = body.split("`");
                if (parts.length < 2) {
                    continue;
                }
                String name = parts[0].trim();
                String type = parts[1].trim();
                List<String> members = new ArrayList<>();
                for (int i = 2; i < parts.length; i++) {
                    String m = parts[i].trim();
                    if (m.isEmpty()) {
                        continue;
                    }
                    // Subconverter: url-test/fallback/load-balance trail with url`interval`timeout`tolerance
                    if (isTestGroupType(type) && isTestGroupTrailer(m)) {
                        break;
                    }
                    members.add(m);
                }
                if (!name.isEmpty()) {
                    groups.add(new ProxyGroup(name, type, members));
                }
            }
        }
        if (rulesets.isEmpty()) {
            throw new BusinessException(500, "ACL4SSR INI 未包含任何 ruleset=");
        }
        return new Model(List.copyOf(rulesets), List.copyOf(groups));
    }

    private static boolean isTestGroupType(String type) {
        if (type == null) {
            return false;
        }
        String t = type.trim().toLowerCase(Locale.ROOT);
        return "url-test".equals(t) || "urltest".equals(t)
                || "fallback".equals(t)
                || "load-balance".equals(t) || "loadbalance".equals(t);
    }

    /** True for test-url / interval / timeout / tolerance tokens after filter members. */
    private static boolean isTestGroupTrailer(String token) {
        String t = token.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return true;
        }
        // e.g. 300 / 300,,50 / ,,50
        return t.matches("[0-9,]+");
    }
}
