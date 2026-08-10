package com.v2board.api.protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Surge / Surfboard / Loon / Quantumult X 文本模板占位符：
 * 全量节点与地区过滤，并剔除空地区组。
 */
final class ConfTemplatePlaceholders {

    private static final Map<String, Pattern> REGION_PLACEHOLDERS = new LinkedHashMap<>();

    static {
        REGION_PLACEHOLDERS.put("$proxy_group_hk", Pattern.compile("(?i)港|HK|Hong\\s*Kong"));
        REGION_PLACEHOLDERS.put("$proxy_group_tw", Pattern.compile("(?i)台|TW|Taiwan"));
        REGION_PLACEHOLDERS.put("$proxy_group_sg", Pattern.compile("(?i)新加坡|狮城|SG|Singapore"));
        REGION_PLACEHOLDERS.put("$proxy_group_jp", Pattern.compile("(?i)日本|JP|Japan"));
        REGION_PLACEHOLDERS.put("$proxy_group_us", Pattern.compile("(?i)美|US|United\\s*States|America"));
        REGION_PLACEHOLDERS.put("$proxy_group_kr", Pattern.compile("(?i)韩|KR|Korea"));
        REGION_PLACEHOLDERS.put("$proxy_group_nf", Pattern.compile("(?i)奈飞|Netflix|NF"));
    }

    private static final Pattern EMPTY_SURGE_GROUP_LINE = Pattern.compile(
            "^\\s*(.+?)\\s*=\\s*(select|url-test|fallback)\\s*,?\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern QX_POLICY_LINE = Pattern.compile(
            "^(static|url-latency-benchmark|available|round-robin|dest-hash)\\s*=\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    private ConfTemplatePlaceholders() {
    }

    /**
     * 先替换地区占位（避免 {@code $proxy_group} 前缀误伤），再替换全量 {@code $proxy_group}，
     * 并删除无节点的地区组及其引用（Surge/Loon 的 {@code [Proxy Group]} 与 QX 的 {@code [policy]}）。
     */
    static String applyProxyGroups(String config, List<String> proxyNames) {
        if (config == null) {
            return "";
        }
        List<String> names = proxyNames != null ? proxyNames : List.of();
        String out = config;
        for (Map.Entry<String, Pattern> e : REGION_PLACEHOLDERS.entrySet()) {
            out = out.replace(e.getKey(), String.join(", ", filter(names, e.getValue())));
        }
        out = out.replace("$proxy_group", String.join(", ", names));
        return pruneEmptyProxyGroups(out);
    }

    static List<String> filter(List<String> names, Pattern pattern) {
        List<String> out = new ArrayList<>();
        for (String n : names) {
            if (n != null && pattern.matcher(n).find()) {
                out.add(n);
            }
        }
        return out;
    }

    static String pruneEmptyProxyGroups(String config) {
        String[] lines = config.split("\\R", -1);
        String section = "";
        Set<String> removed = new LinkedHashSet<>();
        List<String> kept = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = trimmed.toLowerCase(Locale.ROOT);
                kept.add(line);
                continue;
            }
            if (isProxyGroupSection(section) && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
                if (isEmptySurgeGroup(trimmed) || isEmptyQxPolicy(trimmed)) {
                    String name = extractGroupName(trimmed);
                    if (name != null && !name.isEmpty()) {
                        removed.add(name);
                    }
                    continue;
                }
            }
            kept.add(line);
        }

        if (removed.isEmpty()) {
            return joinLines(kept);
        }

        List<String> result = new ArrayList<>();
        section = "";
        for (String line : kept) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = trimmed.toLowerCase(Locale.ROOT);
                result.add(line);
                continue;
            }
            if (isProxyGroupSection(section) && !trimmed.isEmpty() && !trimmed.startsWith("#")
                    && (trimmed.contains("="))) {
                String cleaned = line;
                for (String name : removed) {
                    cleaned = removePolicyRef(cleaned, name);
                }
                String cleanedTrim = cleaned.trim();
                if (isEmptySurgeGroup(cleanedTrim) || isEmptyQxPolicy(cleanedTrim)) {
                    continue;
                }
                result.add(cleaned);
            } else {
                result.add(line);
            }
        }
        return joinLines(result);
    }

    private static boolean isProxyGroupSection(String section) {
        return "[proxy group]".equals(section) || "[policy]".equals(section);
    }

    private static boolean isEmptySurgeGroup(String trimmed) {
        return EMPTY_SURGE_GROUP_LINE.matcher(trimmed).matches();
    }

    /**
     * QX：策略名后无 peer，或仅剩 check-interval / tolerance / img-url 等参数。
     */
    static boolean isEmptyQxPolicy(String trimmed) {
        Matcher m = QX_POLICY_LINE.matcher(trimmed);
        if (!m.matches()) {
            return false;
        }
        String rest = m.group(2).trim();
        int comma = rest.indexOf(',');
        if (comma < 0) {
            return true;
        }
        String after = rest.substring(comma + 1).trim();
        if (after.isEmpty()) {
            return true;
        }
        for (String part : after.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (isQxPolicyParam(p)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isQxPolicyParam(String part) {
        int eq = part.indexOf('=');
        if (eq <= 0) {
            return false;
        }
        String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        return key.equals("check-interval")
                || key.equals("tolerance")
                || key.equals("alive-checking")
                || key.equals("img-url")
                || key.equals("url");
    }

    private static String extractGroupName(String trimmed) {
        Matcher qx = QX_POLICY_LINE.matcher(trimmed);
        if (qx.matches()) {
            String rest = qx.group(2).trim();
            int comma = rest.indexOf(',');
            return (comma < 0 ? rest : rest.substring(0, comma)).trim();
        }
        Matcher surge = EMPTY_SURGE_GROUP_LINE.matcher(trimmed);
        if (surge.matches()) {
            return surge.group(1).trim();
        }
        // non-empty surge line: "name = select, ..."
        int eq = trimmed.indexOf('=');
        if (eq > 0) {
            return trimmed.substring(0, eq).trim();
        }
        return null;
    }

    private static String removePolicyRef(String line, String name) {
        String escaped = Pattern.quote(name);
        String out = line.replaceAll(",\\s*" + escaped + "(?=\\s*,|\\s*$)", "");
        out = out.replaceAll("(?<=[=,])\\s*" + escaped + "\\s*,\\s*", "");
        return out;
    }

    private static String joinLines(List<String> lines) {
        return String.join("\n", lines);
    }
}
