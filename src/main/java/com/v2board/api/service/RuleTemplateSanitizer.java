package com.v2board.api.service;

import com.v2board.api.common.BusinessException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 订阅规则改写：剥离远程 rule-providers / RULE-SET / GitHub raw URL，缺口用本地种子补齐。
 */
public final class RuleTemplateSanitizer {

    private static final Pattern REMOTE_RULE_URL = Pattern.compile(
            "(?i)(https?://[^\\s\"']*(?:raw\\.githubusercontent\\.com|cdn\\.jsdelivr\\.net|ghproxy|github\\.com/[^\\s\"']+/raw/)[^\\s\"']*)");
    private static final Pattern HTTP_URL = Pattern.compile("(?i)https?://[^\\s\"',]+");

    private RuleTemplateSanitizer() {
    }

    /**
     * @param strippedRemote     是否剥离了远程规则依赖
     * @param usedSeedFallback   是否整段/分段回落到本地种子
     */
    public record Result(String content, String warning, boolean strippedRemote, boolean usedSeedFallback) {
        public Result(String content, String warning) {
            this(content, warning, false, false);
        }
    }

    public static Result sanitize(String format, String content, String seedContent) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(500, "规则内容不能为空");
        }
        String fmt = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if ("clash".equals(fmt) || "stash".equals(fmt)) {
            return sanitizeClash(content, seedContent);
        }
        return sanitizeGeneric(content, seedContent, fmt);
    }

    @SuppressWarnings("unchecked")
    static Result sanitizeClash(String content, String seedContent) {
        Yaml yaml = new Yaml();
        Object loaded;
        try {
            loaded = yaml.load(content);
        } catch (Exception e) {
            throw new BusinessException(500, "Clash 规则 YAML 解析失败：" + e.getMessage());
        }
        if (!(loaded instanceof Map<?, ?>)) {
            throw new BusinessException(500, "Clash 规则必须是 YAML 对象");
        }
        Map<String, Object> config = new LinkedHashMap<>((Map<String, Object>) loaded);

        config.remove("rule-providers");
        config.remove("rule_providers");

        Object proxyProviders = config.get("proxy-providers");
        if (proxyProviders instanceof Map<?, ?> pp) {
            Map<String, Object> kept = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : pp.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> provider)) {
                    continue;
                }
                Object type = provider.get("type");
                Object url = provider.get("url");
                if ("http".equals(String.valueOf(type)) || isRemoteRuleUrl(String.valueOf(url))) {
                    continue;
                }
                kept.put(String.valueOf(e.getKey()), new LinkedHashMap<>((Map<String, Object>) provider));
            }
            if (kept.isEmpty()) {
                config.remove("proxy-providers");
            } else {
                config.put("proxy-providers", kept);
            }
        }

        List<Object> rules = config.get("rules") instanceof List<?> l ? new ArrayList<>(l) : new ArrayList<>();
        rules.removeIf(r -> {
            String s = String.valueOf(r);
            String upper = s.toUpperCase(Locale.ROOT);
            return upper.startsWith("RULE-SET,") || containsRemoteRuleUrl(s);
        });
        config.put("rules", rules);

        Map<String, Object> seed = parseClashSeed(seedContent);
        boolean filledFromSeed = false;
        if (rules.isEmpty() || !hasMatchRule(rules)) {
            if (seed.get("rules") instanceof List<?> seedRules && !seedRules.isEmpty()) {
                config.put("rules", new ArrayList<>(seedRules));
                filledFromSeed = true;
            }
        }
        if (!(config.get("proxy-groups") instanceof List<?> groups) || groups.isEmpty()) {
            if (seed.get("proxy-groups") instanceof List<?> seedGroups && !seedGroups.isEmpty()) {
                config.put("proxy-groups", new ArrayList<>(seedGroups));
                filledFromSeed = true;
            }
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        String out = new Yaml(options).dump(config);

        if (containsRemoteRuleDependency(out)) {
            // 整份回退种子后再验一次
            if (seedContent != null && !seedContent.isBlank() && !containsRemoteRuleDependency(seedContent)) {
                return new Result(seedContent,
                        "上游含无法清除的远程规则依赖，已用本地默认模板替换（请用「同步」内联 Online INI，或粘贴已内联的完整模板）",
                        true, true);
            }
            throw new BusinessException(500, "规则仍含远程规则依赖（rule-providers / GitHub raw URL），拒绝保存");
        }

        boolean stripped = contentContainsRemoteDeps(content);
        String warning = null;
        if (filledFromSeed) {
            warning = "已剥离远程规则依赖，并用本地种子补齐分流段（请用「同步」从 Online INI 内联，勿直接粘贴未展开的 rule-providers）";
        } else if (stripped) {
            warning = "已剥离远程规则依赖（rule-providers / RULE-SET / GitHub raw）；请确认分流段仍完整，或改用「同步」内联";
        }
        return new Result(out, warning, stripped || filledFromSeed, filledFromSeed);
    }

    static Result sanitizeGeneric(String content, String seedContent, String format) {
        // Sing-box JSON：远程 rule_set 不宜按行外科手术，整份回退种子
        if ("singbox".equals(format) && hasSingboxRemoteRuleSet(content)) {
            if (seedContent != null && !seedContent.isBlank() && !containsRemoteRuleDependency(seedContent)) {
                return new Result(seedContent,
                        "上游含远程 rule_set，已用本地默认模板替换（请同步无 remote rule_set 的本地模板）",
                        true, true);
            }
            throw new BusinessException(500, "规则含远程 rule_set，拒绝保存");
        }
        StringBuilder sb = new StringBuilder();
        boolean stripped = false;
        for (String line : content.split("\\R", -1)) {
            if (containsRemoteRuleUrl(line) || looksLikeRemoteRuleSet(line)) {
                stripped = true;
                continue;
            }
            sb.append(line).append('\n');
        }
        String out = sb.toString().trim();
        if (out.isEmpty() || !isUsableGeneric(out, format)) {
            if (seedContent != null && !seedContent.isBlank()) {
                return new Result(seedContent,
                        "内容剥离远程依赖后不可用，已用本地默认模板替换（请同步已本地化的完整模板）",
                        true, true);
            }
            throw new BusinessException(500, "规则剥离远程依赖后不可用，且无本地种子可回退");
        }
        if (containsRemoteRuleDependency(out)) {
            if (seedContent != null && !seedContent.isBlank()) {
                return new Result(seedContent,
                        "规则仍含远程依赖，已用本地默认模板替换（请同步已本地化的完整模板）",
                        true, true);
            }
            throw new BusinessException(500, "规则仍含远程规则 URL，拒绝保存");
        }
        String warning = stripped
                ? "已剥离远程规则 URL；请确认分流段仍完整，或改用「同步」从 Online INI 内联本地化"
                : null;
        return new Result(out, warning, stripped, false);
    }

    public static boolean containsRemoteRuleDependency(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.contains("rule-providers:") || lower.contains("rule_providers:")) {
            // 空对象可接受；若块内仍有 url: http 则算远程
            if (REMOTE_RULE_URL.matcher(content).find()) {
                return true;
            }
            // 非空 rule-providers 仍视为远程依赖入口
            if (Pattern.compile("(?is)rule[-_]providers\\s*:\\s*\\n(?:\\s+\\S).*").matcher(content).find()) {
                return true;
            }
        }
        if (Pattern.compile("(?im)^\\s*-?\\s*RULE-SET,").matcher(content).find()) {
            return true;
        }
        // Sing-box remote rule_set（任意 URL；与 dns tag "remote" 无关）
        if (hasSingboxRemoteRuleSet(content)) {
            return true;
        }
        return REMOTE_RULE_URL.matcher(content).find();
    }

    /**
     * Detect Sing-box {@code "type":"remote"} rule_set entries (any download URL).
     */
    static boolean hasSingboxRemoteRuleSet(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        // Only meaningful inside route.rule_set / top-level rule_set blobs
        if (!content.contains("rule_set") && !content.contains("rule-set")) {
            return false;
        }
        return Pattern.compile("(?is)\"type\"\\s*:\\s*\"remote\"").matcher(content).find();
    }

    private static boolean contentContainsRemoteDeps(String content) {
        return content != null && (content.toLowerCase(Locale.ROOT).contains("rule-providers")
                || content.toUpperCase(Locale.ROOT).contains("RULE-SET,")
                || REMOTE_RULE_URL.matcher(content).find());
    }

    private static boolean containsRemoteRuleUrl(String s) {
        return s != null && REMOTE_RULE_URL.matcher(s).find();
    }

    private static boolean isRemoteRuleUrl(String s) {
        if (s == null || s.isBlank() || "null".equals(s)) {
            return false;
        }
        return REMOTE_RULE_URL.matcher(s).find() || HTTP_URL.matcher(s).find();
    }

    private static boolean looksLikeRemoteRuleSet(String line) {
        if (line == null) {
            return false;
        }
        String t = line.trim();
        String upper = t.toUpperCase(Locale.ROOT);
        if (upper.startsWith("RULE-SET,") || upper.contains(",RULE-SET,")) {
            return HTTP_URL.matcher(t).find() || t.contains("http");
        }
        // Surge/QX remote filter
        if ((upper.contains("DOMAIN-SET") || upper.contains("RULE-SET") || upper.contains("PAYLOAD-FILTER"))
                && HTTP_URL.matcher(t).find()) {
            return true;
        }
        return false;
    }

    private static boolean isUsableGeneric(String content, String format) {
        if (content.length() < 16) {
            return false;
        }
        if ("singbox".equals(format)) {
            return content.contains("{") && content.contains("}");
        }
        return content.contains("[") || content.contains("FINAL") || content.contains("final")
                || content.contains("geoip") || content.contains("GEOIP") || content.contains("Rule");
    }

    private static boolean hasMatchRule(List<Object> rules) {
        for (Object r : rules) {
            String s = String.valueOf(r).toUpperCase(Locale.ROOT);
            if (s.startsWith("MATCH,") || s.contains(",MATCH,")) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseClashSeed(String seedContent) {
        if (seedContent == null || seedContent.isBlank()) {
            return Map.of();
        }
        try {
            Object loaded = new Yaml().load(seedContent);
            if (loaded instanceof Map<?, ?> m) {
                return new LinkedHashMap<>((Map<String, Object>) m);
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }
}
