package com.v2board.api.protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * sing-box 1.14：DNS 地址过滤字段必须先 {@code evaluate} 再 {@code match_response}。
 */
public final class SingboxDnsResponseMatch {

    private SingboxDnsResponseMatch() {
    }

    @SuppressWarnings("unchecked")
    public static void apply(Map<String, Object> config) {
        if (config == null) {
            return;
        }
        Object dnsObj = config.get("dns");
        if (!(dnsObj instanceof Map<?, ?> dnsRaw)) {
            return;
        }
        Map<String, Object> dns = (Map<String, Object>) dnsRaw;
        Object rulesObj = dns.get("rules");
        if (!(rulesObj instanceof List<?> rulesRaw) || rulesRaw.isEmpty()) {
            return;
        }
        List<Object> rules = new ArrayList<>();
        boolean needEvaluate = false;
        for (Object item : rulesRaw) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> rule = copyRule(m);
                if (migrateRule(rule)) {
                    needEvaluate = true;
                }
                rules.add(rule);
            } else {
                rules.add(item);
            }
        }
        if (needEvaluate && !hasLeadingEvaluate(rules)) {
            Map<String, Object> evaluate = new LinkedHashMap<>();
            evaluate.put("action", "evaluate");
            evaluate.put("server", pickEvaluateServer(dns));
            rules.add(0, evaluate);
        }
        dns.put("rules", rules);
    }

    private static boolean migrateRule(Map<String, Object> rule) {
        if (rule == null) {
            return false;
        }
        if ("evaluate".equals(String.valueOf(rule.get("action")))) {
            return false;
        }
        rule.remove("rule_set_ip_cidr_accept_empty");
        boolean nestedNeed = false;
        if ("logical".equals(String.valueOf(rule.get("type"))) && rule.get("rules") instanceof List<?> sub) {
            List<Object> next = new ArrayList<>();
            for (Object o : sub) {
                if (o instanceof Map<?, ?> sm) {
                    Map<String, Object> child = copyRule(sm);
                    if (migrateRule(child)) {
                        nestedNeed = true;
                    }
                    next.add(child);
                } else {
                    next.add(o);
                }
            }
            rule.put("rules", next);
        }
        boolean already = hasMatchResponse(rule);
        boolean needs = already || nestedNeed || hasAddressFilter(rule);
        if (needs && !already) {
            rule.put("match_response", true);
        }
        return needs;
    }

    private static Map<String, Object> copyRule(Map<?, ?> raw) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() != null) {
                copy.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return copy;
    }

    private static boolean hasMatchResponse(Map<String, Object> rule) {
        Object v = rule.get("match_response");
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return !s.isBlank() && !"false".equalsIgnoreCase(s);
        }
        return false;
    }

    private static boolean hasAddressFilter(Map<String, Object> rule) {
        if (isTruthy(rule.get("ip_is_private")) || isTruthy(rule.get("ip_accept_any"))) {
            return true;
        }
        if (nonEmpty(rule.get("ip_cidr")) || nonEmpty(rule.get("geoip")) || nonEmpty(rule.get("source_geoip"))) {
            return true;
        }
        if (nonEmpty(rule.get("response_rcode")) || nonEmpty(rule.get("response_answer"))
                || nonEmpty(rule.get("response_ns")) || nonEmpty(rule.get("response_extra"))) {
            return true;
        }
        return ruleSetLooksLikeGeoIp(rule.get("rule_set"));
    }

    private static boolean ruleSetLooksLikeGeoIp(Object ruleSet) {
        if (ruleSet instanceof String s) {
            return looksLikeGeoIpTag(s);
        }
        if (ruleSet instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && looksLikeGeoIpTag(String.valueOf(item))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean looksLikeGeoIpTag(String tag) {
        return tag.toLowerCase(Locale.ROOT).contains("geoip");
    }

    private static boolean isTruthy(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        if (v instanceof String s) {
            return "true".equalsIgnoreCase(s) || "1".equals(s);
        }
        return false;
    }

    private static boolean nonEmpty(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (v instanceof String s) {
            return !s.isBlank();
        }
        return true;
    }

    private static boolean hasLeadingEvaluate(List<Object> rules) {
        if (rules.isEmpty()) {
            return false;
        }
        Object first = rules.get(0);
        return first instanceof Map<?, ?> m && "evaluate".equals(String.valueOf(m.get("action")));
    }

    private static String pickEvaluateServer(Map<String, Object> dns) {
        Object fin = dns.get("final");
        if (fin instanceof String s && !s.isBlank()) {
            return s;
        }
        List<String> tags = serverTags(dns);
        if (tags.contains("remote")) {
            return "remote";
        }
        for (String tag : tags) {
            if (!"local".equals(tag) && !"block".equals(tag)) {
                return tag;
            }
        }
        return tags.isEmpty() ? "remote" : tags.get(0);
    }

    private static List<String> serverTags(Map<String, Object> dns) {
        List<String> tags = new ArrayList<>();
        Object servers = dns.get("servers");
        if (!(servers instanceof List<?> list)) {
            return tags;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Object tag = m.get("tag");
                if (tag != null && !String.valueOf(tag).isBlank()) {
                    tags.add(String.valueOf(tag));
                }
            }
        }
        return tags;
    }
}
