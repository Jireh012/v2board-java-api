package com.v2board.api.service.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.v2board.api.common.BusinessException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Materialize ACL4SSR Online INI (+ fetched lists) into full client templates,
 * keeping classpath seed shell (DNS / General / inbounds) and replacing groups + rules.
 */
public final class Acl4ssrTemplateMaterializer {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private Acl4ssrTemplateMaterializer() {
    }

    public static String materialize(String format,
                                     String seedContent,
                                     Acl4ssrIniParser.Model model,
                                     Map<String, String> listByUrl) {
        if (model == null || model.rulesets().isEmpty()) {
            throw new BusinessException(500, "无可物化的 ACL4SSR 规则");
        }
        String fmt = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        Map<String, String> lists = listByUrl != null ? listByUrl : Map.of();
        String raw = switch (fmt) {
            case "clash", "stash" -> materializeClash(seedContent, model, lists);
            case "surge", "surfboard", "loon", "shadowrocket" -> materializeSurgeFamily(seedContent, model, lists);
            case "quantumultx" -> materializeQuantumultX(seedContent, model, lists);
            case "singbox" -> materializeSingbox(seedContent, model, lists);
            default -> throw new BusinessException(500, "不支持的规则格式：" + format);
        };
        return injectReturnHome(fmt, raw);
    }

    /**
     * Keep seed shell keys; replace proxy-groups / rules from an already-expanded Clash YAML.
     */
    @SuppressWarnings("unchecked")
    public static String mergeClashShell(String seedContent, String expandedClashYaml) {
        Map<String, Object> seed = parseYamlMap(seedContent);
        Map<String, Object> expanded = parseYamlMap(expandedClashYaml);
        if (seed.isEmpty()) {
            return injectReturnHome("clash", dumpYaml(expanded));
        }
        if (expanded.get("proxy-groups") instanceof List<?> g) {
            seed.put("proxy-groups", new ArrayList<>(g));
        }
        if (expanded.get("rules") instanceof List<?> r) {
            seed.put("rules", new ArrayList<>(r));
        }
        seed.remove("rule-providers");
        seed.remove("rule_providers");
        if (!(seed.get("proxies") instanceof List)) {
            seed.put("proxies", new ArrayList<>());
        }
        return injectReturnHome("clash", dumpYaml(seed));
    }

    @SuppressWarnings("unchecked")
    private static String materializeClash(String seedContent, Acl4ssrIniParser.Model model,
                                           Map<String, String> lists) {
        Map<String, Object> config = parseYamlMap(seedContent);
        if (config.isEmpty()) {
            config = new LinkedHashMap<>();
            config.put("mixed-port", 7890);
            config.put("mode", "rule");
        }
        config.put("proxies", new ArrayList<>());
        config.put("proxy-groups", buildClashGroups(model.groups()));
        config.put("rules", buildClashRules(model.rulesets(), lists));
        config.remove("rule-providers");
        config.remove("rule_providers");
        return dumpYaml(config);
    }

    private static List<Map<String, Object>> buildClashGroups(List<Acl4ssrIniParser.ProxyGroup> groups) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Acl4ssrIniParser.ProxyGroup g : groups) {
            Map<String, Object> row = new LinkedHashMap<>();
            String type = Acl4ssrRuleDialect.mapClashGroupType(g.type());
            row.put("name", g.name());
            row.put("type", type);
            List<String> proxies = new ArrayList<>();
            for (String m : g.members()) {
                if (m.startsWith("[]")) {
                    proxies.add(m.substring(2).trim());
                } else if (".*".equals(m.trim())) {
                    // empty → ClashMetaBuilder fills all nodes
                } else {
                    proxies.add(m);
                }
            }
            row.put("proxies", proxies);
            if ("url-test".equals(type) || "fallback".equals(type) || "load-balance".equals(type)) {
                row.put("url", "https://www.gstatic.com/generate_204");
                row.put("interval", 300);
                if ("url-test".equals(type)) {
                    row.put("tolerance", 50);
                }
            }
            out.add(row);
        }
        return out;
    }

    private static List<String> buildClashRules(List<Acl4ssrIniParser.Ruleset> rulesets,
                                                Map<String, String> lists) {
        List<String> rules = new ArrayList<>();
        for (Acl4ssrIniParser.Ruleset rs : rulesets) {
            String policy = rs.policy();
            if (rs.isFinal()) {
                rules.add("MATCH," + policy);
                continue;
            }
            if (rs.isGeoIp()) {
                String code = rs.geoIpCode();
                if (code != null && ("LAN".equalsIgnoreCase(code) || "PRIVATE".equalsIgnoreCase(code))) {
                    rules.add("GEOIP,private," + policy + ",no-resolve");
                } else {
                    rules.add("GEOIP," + (code != null ? code : "CN") + "," + policy + ",no-resolve");
                }
                continue;
            }
            if (rs.isHttpUrl()) {
                String body = lists.get(rs.source().trim());
                if (body == null) {
                    throw new BusinessException(500, "缺少已拉取的规则列表：" + rs.source());
                }
                for (Acl4ssrListParser.Entry e : Acl4ssrListParser.parse(body)) {
                    String line = Acl4ssrRuleDialect.toClash(e, policy);
                    if (line != null) {
                        rules.add(line);
                    }
                }
            }
        }
        return rules;
    }

    private static String materializeSurgeFamily(String seedContent, Acl4ssrIniParser.Model model,
                                                 Map<String, String> lists) {
        String seed = seedContent != null ? seedContent : "";
        String groupSection = buildSurgeProxyGroups(model.groups());
        String ruleSection = buildSurgeRules(model.rulesets(), lists);
        String out = replaceNamedSection(seed, "Proxy Group", groupSection);
        out = replaceNamedSection(out, "Rule", ruleSection);
        if (!out.contains("[Proxy Group]") || !out.contains("[Rule]")) {
            // minimal shell if seed missing sections
            StringBuilder sb = new StringBuilder();
            if (seed.contains("[General]")) {
                sb.append(extractThroughSection(seed, "General"));
            } else {
                sb.append("[General]\n");
            }
            sb.append("\n[Proxy]\n$proxies\n\n[Proxy Group]\n").append(groupSection)
                    .append("\n[Rule]\n").append(ruleSection);
            out = sb.toString();
        }
        return out;
    }

    private static String buildSurgeProxyGroups(List<Acl4ssrIniParser.ProxyGroup> groups) {
        StringBuilder sb = new StringBuilder();
        for (Acl4ssrIniParser.ProxyGroup g : groups) {
            String type = Acl4ssrRuleDialect.mapClashGroupType(g.type());
            List<String> members = new ArrayList<>();
            boolean hasFilter = false;
            for (String m : g.members()) {
                if (m.startsWith("[]")) {
                    members.add(Acl4ssrRuleDialect.normalizeConfBuiltin(m.substring(2).trim()));
                } else {
                    members.add(Acl4ssrRuleDialect.confFilterPlaceholder(m));
                    hasFilter = true;
                }
            }
            if ("url-test".equals(type) || "fallback".equals(type)) {
                if (members.isEmpty() || hasFilter && members.stream().allMatch(s -> s.startsWith("$proxy_group"))) {
                    // ensure at least one filter placeholder
                    if (members.isEmpty()) {
                        members.add("$proxy_group");
                    }
                }
                sb.append(g.name()).append(" = ").append(type).append(", ")
                        .append(String.join(", ", members))
                        .append(", url=https://www.gstatic.com/generate_204, interval=300\n");
            } else {
                sb.append(g.name()).append(" = select");
                if (!members.isEmpty()) {
                    sb.append(", ").append(String.join(", ", members));
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String buildSurgeRules(List<Acl4ssrIniParser.Ruleset> rulesets, Map<String, String> lists) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 强制订阅域名直连\n");
        sb.append("DOMAIN,$subs_domain,DIRECT\n\n");
        sb.append("# BEGIN-ACL4SSR-INLINE\n");
        for (Acl4ssrIniParser.Ruleset rs : rulesets) {
            String policy = rs.policy();
            if (rs.isFinal()) {
                sb.append("FINAL,").append(policy).append('\n');
                continue;
            }
            if (rs.isGeoIp()) {
                String code = rs.geoIpCode();
                if (code != null && ("LAN".equalsIgnoreCase(code) || "PRIVATE".equalsIgnoreCase(code))) {
                    sb.append("GEOIP,LAN,").append(policy).append('\n');
                } else {
                    sb.append("GEOIP,").append(code != null ? code : "CN").append(',').append(policy).append('\n');
                }
                continue;
            }
            if (rs.isHttpUrl()) {
                String body = requireList(lists, rs.source());
                sb.append("# --- ").append(shortName(rs.source())).append(" ---\n");
                for (Acl4ssrListParser.Entry e : Acl4ssrListParser.parse(body)) {
                    String line = Acl4ssrRuleDialect.toSurge(e, policy);
                    if (line != null) {
                        sb.append(line).append('\n');
                    }
                }
            }
        }
        sb.append("# END-ACL4SSR-INLINE\n");
        return sb.toString();
    }

    private static String materializeQuantumultX(String seedContent, Acl4ssrIniParser.Model model,
                                                Map<String, String> lists) {
        String seed = seedContent != null ? seedContent : "";
        String policy = buildQxPolicy(model.groups());
        String filter = buildQxFilter(model.rulesets(), lists);
        String out = replaceNamedSection(seed, "policy", policy);
        out = replaceNamedSection(out, "filter_local", filter);
        if (!out.toLowerCase(Locale.ROOT).contains("[policy]")) {
            out = """
                    [general]
                    server_check_url=https://www.gstatic.com/generate_204

                    [dns]
                    server=223.5.5.5

                    [policy]
                    """ + policy + "\n[server_local]\n$proxies\n\n[filter_local]\n" + filter;
        }
        return out;
    }

    private static String buildQxPolicy(List<Acl4ssrIniParser.ProxyGroup> groups) {
        StringBuilder sb = new StringBuilder();
        for (Acl4ssrIniParser.ProxyGroup g : groups) {
            String type = Acl4ssrRuleDialect.mapClashGroupType(g.type());
            List<String> members = new ArrayList<>();
            for (String m : g.members()) {
                if (m.startsWith("[]")) {
                    members.add(Acl4ssrRuleDialect.normalizeQxPolicy(m.substring(2).trim()));
                } else {
                    members.add(Acl4ssrRuleDialect.confFilterPlaceholder(m));
                }
            }
            if ("url-test".equals(type) || "fallback".equals(type)) {
                if (members.isEmpty()) {
                    members.add("$proxy_group");
                }
                sb.append("url-latency-benchmark=").append(g.name()).append(", ")
                        .append(String.join(", ", members))
                        .append(", check-interval=600, tolerance=0\n");
            } else {
                sb.append("static=").append(g.name());
                if (!members.isEmpty()) {
                    sb.append(", ").append(String.join(", ", members));
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String buildQxFilter(List<Acl4ssrIniParser.Ruleset> rulesets, Map<String, String> lists) {
        StringBuilder sb = new StringBuilder();
        sb.append("host, $subs_domain, direct\n\n");
        sb.append("# BEGIN-ACL4SSR-INLINE\n");
        for (Acl4ssrIniParser.Ruleset rs : rulesets) {
            String policy = Acl4ssrRuleDialect.normalizeQxPolicy(rs.policy());
            if (rs.isFinal()) {
                sb.append("final, ").append(policy).append('\n');
                continue;
            }
            if (rs.isGeoIp()) {
                String code = rs.geoIpCode();
                if (code != null && ("LAN".equalsIgnoreCase(code) || "PRIVATE".equalsIgnoreCase(code))) {
                    sb.append("ip-cidr, 10.0.0.0/8, ").append(policy).append('\n');
                    sb.append("ip-cidr, 172.16.0.0/12, ").append(policy).append('\n');
                    sb.append("ip-cidr, 192.168.0.0/16, ").append(policy).append('\n');
                } else {
                    // QX has no GEOIP in filter_local the same way; emit geoip if supported
                    sb.append("geoip, ").append(code != null ? code.toLowerCase(Locale.ROOT) : "cn")
                            .append(", ").append(policy).append('\n');
                }
                continue;
            }
            if (rs.isHttpUrl()) {
                String body = requireList(lists, rs.source());
                sb.append("# --- ").append(shortName(rs.source())).append(" ---\n");
                for (Acl4ssrListParser.Entry e : Acl4ssrListParser.parse(body)) {
                    String line = Acl4ssrRuleDialect.toQuantumultX(e, policy);
                    if (line != null) {
                        sb.append(line).append('\n');
                    }
                }
            }
        }
        sb.append("# END-ACL4SSR-INLINE\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String materializeSingbox(String seedContent, Acl4ssrIniParser.Model model,
                                             Map<String, String> lists) {
        Map<String, Object> root;
        try {
            if (seedContent != null && !seedContent.isBlank()) {
                root = JSON.readValue(seedContent, Map.class);
            } else {
                root = new LinkedHashMap<>();
            }
        } catch (Exception e) {
            throw new BusinessException(500, "Sing-box 种子 JSON 解析失败：" + e.getMessage());
        }
        List<Map<String, Object>> outbounds = new ArrayList<>();
        Map<String, Object> direct = new LinkedHashMap<>();
        direct.put("tag", "DIRECT");
        direct.put("type", "direct");
        Map<String, Object> resolver = new LinkedHashMap<>();
        resolver.put("server", "local");
        direct.put("domain_resolver", resolver);
        outbounds.add(direct);

        for (Acl4ssrIniParser.ProxyGroup g : model.groups()) {
            Map<String, Object> ob = new LinkedHashMap<>();
            ob.put("tag", g.name());
            String type = Acl4ssrRuleDialect.mapSingboxGroupType(g.type());
            ob.put("type", type);
            List<String> members = new ArrayList<>();
            for (String m : g.members()) {
                if (m.startsWith("[]")) {
                    String ref = m.substring(2).trim();
                    if ("REJECT".equalsIgnoreCase(ref)) {
                        // sing-box has no REJECT outbound; skip — ads use action reject in route
                        continue;
                    }
                    members.add(ref);
                } else if (".*".equals(m.trim())) {
                    // empty → SingboxBuilder fills
                } else {
                    members.add(m);
                }
            }
            ob.put("outbounds", members);
            if ("urltest".equals(type)) {
                ob.put("url", "https://www.gstatic.com/generate_204");
                ob.put("interval", "5m");
                ob.put("tolerance", 50);
            }
            outbounds.add(ob);
        }
        root.put("outbounds", outbounds);

        Map<String, Object> route = root.get("route") instanceof Map<?, ?> r
                ? new LinkedHashMap<>((Map<String, Object>) r) : new LinkedHashMap<>();
        List<Object> prefix = new ArrayList<>();
        if (route.get("rules") instanceof List<?> existing) {
            for (Object o : existing) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                if (m.containsKey("action") && ("sniff".equals(String.valueOf(m.get("action")))
                        || "hijack-dns".equals(String.valueOf(m.get("action"))))) {
                    prefix.add(o);
                    continue;
                }
                if (m.containsKey("clash_mode")) {
                    prefix.add(o);
                }
            }
        } else {
            prefix.add(Map.of("action", "sniff"));
            prefix.add(Map.of("protocol", "dns", "action", "hijack-dns"));
        }
        List<Object> rules = new ArrayList<>(prefix);
        String finalOutbound = "🚀 节点选择";
        for (Acl4ssrIniParser.Ruleset rs : model.rulesets()) {
            if (rs.isFinal()) {
                finalOutbound = rs.policy();
                continue;
            }
            if (rs.isGeoIp()) {
                String code = rs.geoIpCode();
                Map<String, Object> rule = new LinkedHashMap<>();
                if (code != null && ("LAN".equalsIgnoreCase(code) || "PRIVATE".equalsIgnoreCase(code))) {
                    rule.put("ip_is_private", true);
                } else {
                    rule.put("geoip", code != null ? code.toLowerCase(Locale.ROOT) : "cn");
                }
                applySingboxAction(rule, rs.policy());
                rules.add(rule);
                continue;
            }
            if (rs.isHttpUrl()) {
                String body = requireList(lists, rs.source());
                List<String> domainSuffix = new ArrayList<>();
                List<String> domain = new ArrayList<>();
                List<String> domainKeyword = new ArrayList<>();
                List<String> ipCidr = new ArrayList<>();
                for (Acl4ssrListParser.Entry e : Acl4ssrListParser.parse(body)) {
                    switch (e.kind()) {
                        case "domain-suffix" -> domainSuffix.add(e.value());
                        case "domain" -> domain.add(e.value());
                        case "domain-keyword" -> domainKeyword.add(e.value());
                        case "ip-cidr", "ip-cidr6" -> ipCidr.add(e.value());
                        default -> {
                        }
                    }
                }
                if (!domainSuffix.isEmpty()) {
                    Map<String, Object> rule = new LinkedHashMap<>();
                    rule.put("domain_suffix", domainSuffix);
                    applySingboxAction(rule, rs.policy());
                    rules.add(rule);
                }
                if (!domain.isEmpty()) {
                    Map<String, Object> rule = new LinkedHashMap<>();
                    rule.put("domain", domain);
                    applySingboxAction(rule, rs.policy());
                    rules.add(rule);
                }
                if (!domainKeyword.isEmpty()) {
                    Map<String, Object> rule = new LinkedHashMap<>();
                    rule.put("domain_keyword", domainKeyword);
                    applySingboxAction(rule, rs.policy());
                    rules.add(rule);
                }
                if (!ipCidr.isEmpty()) {
                    Map<String, Object> rule = new LinkedHashMap<>();
                    rule.put("ip_cidr", ipCidr);
                    applySingboxAction(rule, rs.policy());
                    rules.add(rule);
                }
            }
        }
        route.put("rules", rules);
        route.put("final", finalOutbound);
        route.putIfAbsent("auto_detect_interface", true);
        root.put("route", route);
        root.remove("rule_set");
        try {
            return JSON.writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException(500, "Sing-box 模板序列化失败：" + e.getMessage());
        }
    }

    private static void applySingboxAction(Map<String, Object> rule, String policy) {
        if (policy == null) {
            rule.put("action", "route");
            rule.put("outbound", "DIRECT");
            return;
        }
        if ("REJECT".equalsIgnoreCase(policy) || policy.contains("广告") || policy.contains("净化")) {
            rule.put("action", "reject");
            return;
        }
        rule.put("action", "route");
        rule.put("outbound", policy);
    }

    private static String requireList(Map<String, String> lists, String url) {
        String body = lists.get(url.trim());
        if (body == null) {
            throw new BusinessException(500, "缺少已拉取的规则列表：" + url);
        }
        return body;
    }

    private static String shortName(String url) {
        int slash = url.lastIndexOf('/');
        return slash >= 0 ? url.substring(slash + 1) : url;
    }

    /**
     * Replace body of {@code [sectionName]} until next {@code [} section.
     */
    static String replaceNamedSection(String body, String sectionName, String newBody) {
        if (body == null || body.isBlank()) {
            return body;
        }
        String[] lines = body.split("\\R", -1);
        String target = "[" + sectionName + "]";
        StringBuilder out = new StringBuilder();
        boolean in = false;
        boolean replaced = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                if (in) {
                    in = false;
                }
                if (trimmed.equalsIgnoreCase(target)) {
                    out.append(line).append('\n');
                    if (!newBody.isEmpty() && !newBody.endsWith("\n")) {
                        out.append(newBody).append('\n');
                    } else {
                        out.append(newBody);
                    }
                    in = true;
                    replaced = true;
                    continue;
                }
            }
            if (!in) {
                out.append(line);
                if (i < lines.length - 1) {
                    out.append('\n');
                }
            }
        }
        return replaced ? out.toString() : body;
    }

    private static String extractThroughSection(String seed, String sectionName) {
        String[] lines = seed.split("\\R", -1);
        String target = "[" + sectionName + "]";
        StringBuilder sb = new StringBuilder();
        boolean in = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                if (in) {
                    break;
                }
                if (trimmed.equalsIgnoreCase(target)) {
                    in = true;
                }
            }
            if (in) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /** 同步 ACL4SSR 后补上 🏠 回国（可选 DIRECT），并把 CN / 国内媒体指过去。 */
    static String injectReturnHome(String format, String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        return switch (format) {
            case "clash", "stash" -> injectReturnHomeClash(content);
            case "quantumultx" -> injectReturnHomeQx(content);
            case "singbox" -> injectReturnHomeSingbox(content);
            default -> injectReturnHomeSurge(content);
        };
    }

    @SuppressWarnings("unchecked")
    private static String injectReturnHomeClash(String yaml) {
        Map<String, Object> config = parseYamlMap(yaml);
        List<Map<String, Object>> groups = new ArrayList<>();
        if (config.get("proxy-groups") instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    groups.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
        }
        boolean has = groups.stream().anyMatch(g -> "🏠 回国".equals(g.get("name")));
        if (!has) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", "🏠 回国");
            row.put("type", "select");
            row.put("proxies", List.of("(?i)回国|^CN\\s|中国大陆", "DIRECT"));
            groups.add(0, row);
        }
        for (Map<String, Object> g : groups) {
            String name = String.valueOf(g.get("name"));
            if ("🎯 全球直连".equals(name) || "🌏 国内媒体".equals(name)
                    || "📺 哔哩哔哩".equals(name) || "🎶 网易音乐".equals(name)) {
                prependProxy(g, "🏠 回国");
            }
        }
        config.put("proxy-groups", groups);
        if (config.get("rules") instanceof List<?> rawRules) {
            List<Object> rules = new ArrayList<>(rawRules);
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i) instanceof String r) {
                    rules.set(i, retargetCnRule(r));
                }
            }
            config.put("rules", rules);
        }
        return dumpYaml(config);
    }

    private static void prependProxy(Map<String, Object> group, String member) {
        List<String> proxies = new ArrayList<>();
        if (group.get("proxies") instanceof List<?> raw) {
            for (Object o : raw) {
                proxies.add(String.valueOf(o));
            }
        }
        if (!proxies.contains(member)) {
            proxies.add(0, member);
        }
        group.put("proxies", proxies);
    }

    private static String retargetCnRule(String rule) {
        if (rule.startsWith("GEOIP,CN,") || rule.startsWith("GEOSITE,cn,")
                || rule.startsWith("GEOSITE,geolocation-cn,")) {
            return rule.replace("🎯 全球直连", "🏠 回国").replace(",DIRECT", ",🏠 回国");
        }
        return rule;
    }

    private static String injectReturnHomeSurge(String conf) {
        boolean inserted = conf.contains("🏠 回国 =");
        String[] lines = conf.split("\n", -1);
        List<String> out = new ArrayList<>(lines.length + 2);
        for (String line : lines) {
            if (!inserted && line.startsWith("[Proxy Group]")) {
                out.add(line);
                out.add("🏠 回国 = select, $proxy_group_cn, DIRECT");
                inserted = true;
                continue;
            }
            out.add(rewriteSurgePolicyLine(line));
        }
        return String.join("\n", out);
    }

    private static String rewriteSurgePolicyLine(String line) {
        if (line.contains("🏠 回国")) {
            return line;
        }
        if (line.startsWith("🎯 全球直连 =") || line.startsWith("🌏 国内媒体 =")
                || line.startsWith("📺 哔哩哔哩 =") || line.startsWith("🎶 网易音乐 =")) {
            int idx = line.indexOf("= select, ");
            if (idx >= 0) {
                return line.substring(0, idx) + "= select, 🏠 回国, " + line.substring(idx + "= select, ".length());
            }
        }
        if (line.startsWith("GEOIP,CN,")) {
            return line.replace("🎯 全球直连", "🏠 回国").replace(",DIRECT", ",🏠 回国");
        }
        return line;
    }

    private static String injectReturnHomeQx(String conf) {
        boolean inserted = conf.contains("static=🏠 回国");
        String[] lines = conf.split("\n", -1);
        List<String> out = new ArrayList<>(lines.length + 2);
        for (String line : lines) {
            if (!inserted && line.startsWith("[policy]")) {
                out.add(line);
                out.add("static=🏠 回国, $proxy_group_cn, direct");
                inserted = true;
                continue;
            }
            out.add(rewriteQxPolicyLine(line));
        }
        return String.join("\n", out);
    }

    private static String rewriteQxPolicyLine(String line) {
        if (line.contains("🏠 回国")) {
            return line;
        }
        if (line.startsWith("static=🎯 全球直连") || line.startsWith("static=🌏 国内媒体")
                || line.startsWith("static=📺 哔哩哔哩") || line.startsWith("static=🎶 网易音乐")) {
            int comma = line.indexOf(',');
            if (comma > 0) {
                return line.substring(0, comma) + ", 🏠 回国" + line.substring(comma);
            }
        }
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.startsWith("geoip,") && (lower.contains(", cn,") || lower.contains(",cn,"))) {
            return line.replace("🎯 全球直连", "🏠 回国")
                    .replace(", direct", ", 🏠 回国")
                    .replace(",direct", ", 🏠 回国");
        }
        return line;
    }

    @SuppressWarnings("unchecked")
    private static String injectReturnHomeSingbox(String json) {
        try {
            Map<String, Object> root = JSON.readValue(json, Map.class);
            List<Map<String, Object>> outbounds = root.get("outbounds") instanceof List<?> raw
                    ? new ArrayList<>((List<Map<String, Object>>) raw) : new ArrayList<>();
            boolean has = outbounds.stream().anyMatch(o -> "🏠 回国".equals(o.get("tag")));
            if (!has) {
                Map<String, Object> home = new LinkedHashMap<>();
                home.put("tag", "🏠 回国");
                home.put("type", "selector");
                home.put("outbounds", List.of("(?i)回国|^CN\\s|中国大陆", "DIRECT"));
                outbounds.add(1, home);
            }
            for (Map<String, Object> ob : outbounds) {
                String tag = String.valueOf(ob.get("tag"));
                if ("🎯 全球直连".equals(tag) || "🌏 国内媒体".equals(tag)
                        || "📺 哔哩哔哩".equals(tag) || "🎶 网易音乐".equals(tag)) {
                    List<String> members = new ArrayList<>();
                    if (ob.get("outbounds") instanceof List<?> raw) {
                        for (Object o : raw) {
                            members.add(String.valueOf(o));
                        }
                    }
                    if (!members.contains("🏠 回国")) {
                        members.add(0, "🏠 回国");
                    }
                    ob.put("outbounds", members);
                }
            }
            root.put("outbounds", outbounds);
            return JSON.writeValueAsString(root);
        } catch (Exception e) {
            return json;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseYamlMap(String content) {
        if (content == null || content.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object loaded = new Yaml().load(content);
            if (loaded instanceof Map<?, ?> m) {
                return new LinkedHashMap<>((Map<String, Object>) m);
            }
        } catch (Exception e) {
            throw new BusinessException(500, "Clash YAML 解析失败：" + e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    private static String dumpYaml(Map<String, Object> config) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(config);
    }
}
