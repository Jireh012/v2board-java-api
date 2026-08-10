package com.v2board.api.service.rules;

import com.v2board.api.common.BusinessException;
import com.v2board.api.service.external.ExternalSubscribeFetcher;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Expand Clash/Stash YAML {@code rule-providers} (HTTP) into inline classical rules.
 */
public final class ClashRuleProviderExpander {

    private ClashRuleProviderExpander() {
    }

    public static boolean hasHttpProviders(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        if (!lower.contains("rule-providers") && !lower.contains("rule_providers")) {
            return false;
        }
        try {
            Object loaded = new Yaml().load(content);
            if (!(loaded instanceof Map<?, ?> map)) {
                return false;
            }
            Object providers = map.containsKey("rule-providers")
                    ? map.get("rule-providers") : map.get("rule_providers");
            if (!(providers instanceof Map<?, ?> pm) || pm.isEmpty()) {
                return false;
            }
            for (Object v : pm.values()) {
                if (v instanceof Map<?, ?> provider) {
                    Object type = provider.get("type");
                    Object url = provider.get("url");
                    if ("http".equals(String.valueOf(type)) && url != null
                            && String.valueOf(url).toLowerCase(Locale.ROOT).startsWith("http")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    public static String expand(String content, ExternalSubscribeFetcher fetcher) {
        return expand(content, url -> {
            try {
                return fetcher.fetch(url);
            } catch (Exception e) {
                throw new BusinessException(500, "拉取规则列表失败：" + url + ": " + e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static String expand(String content, Function<String, String> listFetcher) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(500, "规则内容不能为空");
        }
        Object loaded;
        try {
            loaded = new Yaml().load(content);
        } catch (Exception e) {
            throw new BusinessException(500, "Clash 规则 YAML 解析失败：" + e.getMessage());
        }
        if (!(loaded instanceof Map<?, ?>)) {
            throw new BusinessException(500, "Clash 规则必须是 YAML 对象");
        }
        Map<String, Object> config = new LinkedHashMap<>((Map<String, Object>) loaded);
        Object providersObj = config.containsKey("rule-providers")
                ? config.get("rule-providers") : config.get("rule_providers");
        Map<String, List<Acl4ssrListParser.Entry>> byName = new LinkedHashMap<>();
        if (providersObj instanceof Map<?, ?> providers) {
            for (Map.Entry<?, ?> e : providers.entrySet()) {
                String name = String.valueOf(e.getKey());
                if (!(e.getValue() instanceof Map<?, ?> provider)) {
                    continue;
                }
                Object type = provider.get("type");
                Object urlObj = provider.get("url");
                if (!"http".equals(String.valueOf(type)) || urlObj == null) {
                    continue;
                }
                String url = String.valueOf(urlObj).trim();
                if (!url.toLowerCase(Locale.ROOT).startsWith("http")) {
                    continue;
                }
                String body = listFetcher.apply(url);
                if (body == null) {
                    throw new BusinessException(500, "拉取规则列表失败：" + url + ": 内容为空");
                }
                byName.put(name, Acl4ssrListParser.parse(body));
            }
        }

        List<Object> rules = config.get("rules") instanceof List<?> l ? new ArrayList<>(l) : new ArrayList<>();
        List<Object> expanded = new ArrayList<>();
        for (Object rule : rules) {
            String s = String.valueOf(rule);
            String upper = s.toUpperCase(Locale.ROOT);
            if (!upper.startsWith("RULE-SET,")) {
                expanded.add(rule);
                continue;
            }
            String[] parts = s.split(",", 3);
            if (parts.length < 3) {
                continue;
            }
            String providerName = parts[1].trim();
            String policy = parts[2].trim();
            // drop trailing options like no-resolve from policy segment if present as 4th — keep full rest
            if (parts.length == 3 && policy.contains(",")) {
                // already only 3 parts
            }
            List<Acl4ssrListParser.Entry> entries = byName.get(providerName);
            if (entries == null) {
                throw new BusinessException(500, "RULE-SET 引用了未拉取的 rule-provider：" + providerName);
            }
            for (Acl4ssrListParser.Entry entry : entries) {
                String line = Acl4ssrRuleDialect.toClash(entry, policy);
                if (line != null) {
                    expanded.add(line);
                }
            }
        }
        config.put("rules", expanded);
        config.remove("rule-providers");
        config.remove("rule_providers");

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(config);
    }
}
