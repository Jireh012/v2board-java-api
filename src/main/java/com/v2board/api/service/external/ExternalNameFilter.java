package com.v2board.api.service.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Per-source display-name filters: sequential match → replace (replacement may be empty).
 */
public final class ExternalNameFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RULES = 50;

    private ExternalNameFilter() {
    }

    public record Rule(String pattern, String replacement, boolean regex) {
    }

    public static List<Rule> parseAndValidate(Object raw) {
        List<Map<String, Object>> maps = coerceToMaps(raw);
        if (maps.isEmpty()) {
            return List.of();
        }
        if (maps.size() > MAX_RULES) {
            throw new BusinessException(500, "过滤规则最多 " + MAX_RULES + " 条");
        }
        List<Rule> rules = new ArrayList<>(maps.size());
        int index = 1;
        for (Map<String, Object> map : maps) {
            String pattern = str(map.get("pattern")).trim();
            if (!StringUtils.hasText(pattern)) {
                throw new BusinessException(500, "第 " + index + " 条过滤规则的匹配内容不能为空");
            }
            String replacement = map.get("replacement") == null ? "" : String.valueOf(map.get("replacement"));
            boolean regex = toBool(map.get("regex"));
            if (regex) {
                try {
                    Pattern.compile(pattern);
                } catch (PatternSyntaxException e) {
                    throw new BusinessException(500,
                            "第 " + index + " 条过滤规则正则无效: " + e.getDescription());
                }
            }
            rules.add(new Rule(pattern, replacement, regex));
            index++;
        }
        return rules;
    }

    public static String toJson(List<Rule> rules) {
        try {
            if (rules == null || rules.isEmpty()) {
                return "[]";
            }
            List<Map<String, Object>> list = new ArrayList<>(rules.size());
            for (Rule rule : rules) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("pattern", rule.pattern());
                m.put("replacement", rule.replacement() != null ? rule.replacement() : "");
                m.put("regex", rule.regex());
                list.add(m);
            }
            return MAPPER.writeValueAsString(list);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "过滤规则序列化失败");
        }
    }

    public static List<Rule> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> maps = MAPPER.readValue(json, new TypeReference<>() {});
            // sync path: tolerate historically invalid JSON by returning empty rather than failing sync
            return parseAndValidateLenient(maps);
        } catch (BusinessException e) {
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Strict parse for admin save. */
    public static List<Rule> parseAndValidateStrict(Object raw) {
        return parseAndValidate(raw);
    }

    private static List<Rule> parseAndValidateLenient(List<Map<String, Object>> maps) {
        if (maps == null || maps.isEmpty()) {
            return List.of();
        }
        List<Rule> rules = new ArrayList<>();
        for (Map<String, Object> map : maps) {
            String pattern = str(map.get("pattern")).trim();
            if (!StringUtils.hasText(pattern)) {
                continue;
            }
            String replacement = map.get("replacement") == null ? "" : String.valueOf(map.get("replacement"));
            boolean regex = toBool(map.get("regex"));
            if (regex) {
                try {
                    Pattern.compile(pattern);
                } catch (PatternSyntaxException e) {
                    continue;
                }
            }
            rules.add(new Rule(pattern, replacement, regex));
            if (rules.size() >= MAX_RULES) {
                break;
            }
        }
        return rules;
    }

    public static List<Map<String, Object>> toApiList(String json) {
        List<Rule> rules = fromJson(json);
        List<Map<String, Object>> list = new ArrayList<>(rules.size());
        for (Rule rule : rules) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pattern", rule.pattern());
            m.put("replacement", rule.replacement());
            m.put("regex", rule.regex());
            list.add(m);
        }
        return list;
    }

    public static String apply(String name, List<Rule> rules) {
        if (name == null) {
            return null;
        }
        if (rules == null || rules.isEmpty()) {
            return name;
        }
        String original = name;
        String current = name;
        for (Rule rule : rules) {
            if (rule == null || !StringUtils.hasText(rule.pattern())) {
                continue;
            }
            String replacement = rule.replacement() != null ? rule.replacement() : "";
            if (rule.regex()) {
                try {
                    current = Pattern.compile(rule.pattern()).matcher(current).replaceAll(replacement);
                } catch (Exception ignored) {
                    // skip broken rule at sync
                }
            } else {
                current = current.replace(rule.pattern(), replacement);
            }
        }
        if (!StringUtils.hasText(current.trim())) {
            return original;
        }
        return current;
    }

    public static void applyFiltersToParsed(List<CanonicalExternalNode> nodes, List<Rule> rules) {
        if (nodes == null || rules == null || rules.isEmpty()) {
            return;
        }
        for (CanonicalExternalNode node : nodes) {
            if (node == null) {
                continue;
            }
            String filtered = apply(node.getName(), rules);
            if (filtered == null) {
                continue;
            }
            node.setName(filtered);
            Map<String, Object> outbound = node.getSingboxOutbound();
            if (outbound != null) {
                outbound.put("tag", filtered);
            }
            if (node.getShareUri() != null) {
                node.setShareUri(ExternalNodeIdentity.rewriteShareUriName(node.getShareUri(), filtered));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> coerceToMaps(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof String s) {
            if (!StringUtils.hasText(s)) {
                return List.of();
            }
            try {
                raw = MAPPER.readValue(s, Object.class);
            } catch (Exception e) {
                throw new BusinessException(500, "过滤规则格式无效");
            }
        }
        if (!(raw instanceof List<?> list)) {
            throw new BusinessException(500, "过滤规则必须是数组");
        }
        if (list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> maps = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                throw new BusinessException(500, "过滤规则格式无效");
            }
            maps.add((Map<String, Object>) m);
        }
        return maps;
    }

    private static boolean toBool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }
        if (o == null) {
            return false;
        }
        String s = String.valueOf(o).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
