package com.v2board.api.service;

import com.v2board.api.common.BusinessException;
import com.v2board.api.config.V2boardRedisProperties;
import com.v2board.api.mapper.SubscribeRuleTemplateMapper;
import com.v2board.api.model.SubscribeRuleTemplate;
import com.v2board.api.service.external.ExternalSubscribeFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 订阅规则模板：Redis → DB → classpath；写路径消毒并失效缓存。
 */
@Service
public class RuleTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(RuleTemplateService.class);
    private static final long CACHE_TTL_HOURS = 24;
    private static final Set<String> SUPPORTED = Set.of(
            "clash", "stash", "surge", "surfboard", "singbox", "quantumultx", "loon");
    public static final String PROFILE_FULL = "full";
    private static final Set<String> PROFILES = Set.of("full", "simple", "nodes");
    private static final ThreadLocal<String> REQUEST_PROFILE = new ThreadLocal<>();

    private final SubscribeRuleTemplateMapper mapper;
    private final CacheService cacheService;
    private final ExternalSubscribeFetcher fetcher;
    private final String redisPrefix;

    public RuleTemplateService(SubscribeRuleTemplateMapper mapper,
                               CacheService cacheService,
                               ExternalSubscribeFetcher fetcher,
                               V2boardRedisProperties redisProperties) {
        this.mapper = mapper;
        this.cacheService = cacheService;
        this.fetcher = fetcher;
        String p = redisProperties != null ? redisProperties.getPrefix() : "";
        this.redisPrefix = p != null ? p : "";
    }

    /** Bind rule profile for current subscribe request ({@code full|simple|nodes}). */
    public static void bindRequestProfile(String profile) {
        REQUEST_PROFILE.set(normalizeProfile(profile));
    }

    public static void clearRequestProfile() {
        REQUEST_PROFILE.remove();
    }

    public static String currentRequestProfile() {
        String p = REQUEST_PROFILE.get();
        return p != null ? p : PROFILE_FULL;
    }

    public static String normalizeProfile(String profile) {
        if (!StringUtils.hasText(profile)) {
            return PROFILE_FULL;
        }
        String p = profile.trim().toLowerCase(Locale.ROOT);
        if ("node".equals(p)) {
            p = "nodes";
        }
        if (!PROFILES.contains(p)) {
            return PROFILE_FULL;
        }
        return p;
    }

    public String resolve(String format) {
        String fmt = normalizeFormat(format);
        String profile = currentRequestProfile();
        if (!PROFILE_FULL.equals(profile)) {
            return resolveProfileSeed(fmt, profile);
        }
        if ("stash".equals(fmt)) {
            return resolveStash();
        }
        return resolveDirect(fmt);
    }

    /**
     * simple / nodes：仅 classpath 种子（管理端自定义只覆盖 full）。
     */
    private String resolveProfileSeed(String fmt, String profile) {
        String path = profileClasspathPath(profile, fmt);
        String content = readClasspath(path);
        if ((content == null || content.isBlank()) && "stash".equals(fmt)) {
            content = readClasspath(profileClasspathPath(profile, "clash"));
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(500, "未找到规则档位 " + profile + " 的默认模板：" + fmt);
        }
        return content;
    }

    public Map<String, Object> fetch(String format) {
        String fmt = normalizeFormat(format);
        SubscribeRuleTemplate row = loadRow(fmt);
        // stash 无独立行时展示 clash 解析结果，但标注回退
        String content = resolve(fmt);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("format", fmt);
        data.put("content", content);
        if (row != null) {
            data.put("source_url", row.getSourceUrl());
            data.put("update_source", row.getUpdateSource());
            data.put("updated_at", row.getUpdatedAt());
            data.put("created_at", row.getCreatedAt());
            data.put("is_default", false);
        } else if ("stash".equals(fmt) && loadRow("clash") != null) {
            SubscribeRuleTemplate clash = loadRow("clash");
            data.put("source_url", clash.getSourceUrl());
            data.put("update_source", clash.getUpdateSource());
            data.put("updated_at", clash.getUpdatedAt());
            data.put("created_at", clash.getCreatedAt());
            data.put("is_default", false);
            data.put("fallback_format", "clash");
        } else {
            data.put("source_url", null);
            data.put("update_source", "default");
            data.put("updated_at", null);
            data.put("created_at", null);
            data.put("is_default", true);
        }
        return data;
    }

    public Map<String, Object> save(String format, String content, String sourceUrl, String updateSource) {
        String fmt = normalizeFormat(format);
        String seed = loadClasspathSeed(fmt);
        RuleTemplateSanitizer.Result sanitized = RuleTemplateSanitizer.sanitize(fmt, content, seed);
        persist(fmt, sanitized.content(), sourceUrl, updateSource != null ? updateSource : "manual");
        Map<String, Object> data = fetch(fmt);
        applySanitizeMeta(data, sanitized);
        return data;
    }

    public Map<String, Object> restore(String format) {
        String fmt = normalizeFormat(format);
        mapper.deleteById(fmt);
        invalidateCache(fmt);
        Map<String, Object> data = fetch(fmt);
        data.put("update_source", "default");
        return data;
    }

    public Map<String, Object> sync(String format, String url) {
        String fmt = normalizeFormat(format);
        if (!StringUtils.hasText(url)) {
            // 允许沿用已存 source_url
            SubscribeRuleTemplate existing = loadRow(fmt);
            if (existing != null && StringUtils.hasText(existing.getSourceUrl())) {
                url = existing.getSourceUrl();
            }
        }
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(500, "同步 URL 不能为空");
        }
        String raw;
        try {
            raw = fetcher.fetch(url.trim());
        } catch (Exception e) {
            logger.warn("Subscribe rule sync fetch failed format={} url={}: {}", fmt, url, e.getMessage());
            throw new BusinessException(500, "拉取上游规则失败：" + e.getMessage());
        }
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(500, "上游规则内容为空");
        }
        String seed = loadClasspathSeed(fmt);
        RuleTemplateSanitizer.Result sanitized;
        try {
            sanitized = RuleTemplateSanitizer.sanitize(fmt, raw, seed);
        } catch (BusinessException e) {
            throw e;
        }
        persist(fmt, sanitized.content(), url.trim(), "sync");
        Map<String, Object> data = fetch(fmt);
        applySanitizeMeta(data, sanitized);
        data.put("sync_hint",
                "同步目标须为「已本地化」完整模板；带 rule-providers / 远程 RULE-SET 的 Online Full 会被剥离或回落默认种子");
        return data;
    }

    private static void applySanitizeMeta(Map<String, Object> data, RuleTemplateSanitizer.Result sanitized) {
        if (sanitized.warning() != null) {
            data.put("warning", sanitized.warning());
        }
        data.put("stripped_remote", sanitized.strippedRemote());
        data.put("used_seed_fallback", sanitized.usedSeedFallback());
    }

    String cacheKey(String format) {
        return redisPrefix + "subscribe:rule:" + format;
    }

    void invalidateCache(String format) {
        cacheService.delete(cacheKey(format));
    }

    private String resolveStash() {
        Object cached = cacheService.get(cacheKey("stash"));
        if (cached instanceof String s && !s.isBlank()) {
            return s;
        }
        SubscribeRuleTemplate row = loadRow("stash");
        if (row != null && StringUtils.hasText(row.getContent())) {
            cacheService.set(cacheKey("stash"), row.getContent(), CACHE_TTL_HOURS, TimeUnit.HOURS);
            return row.getContent();
        }
        String stashSeed = readClasspath("rules/default.stash.yaml");
        if (stashSeed != null) {
            // classpath 种子不写 Redis，避免发版后仍命中旧默认缓存
            return stashSeed;
        }
        return resolveDirect("clash");
    }

    private String resolveDirect(String fmt) {
        Object cached = cacheService.get(cacheKey(fmt));
        if (cached instanceof String s && !s.isBlank()) {
            return s;
        }
        SubscribeRuleTemplate row = loadRow(fmt);
        if (row != null && StringUtils.hasText(row.getContent())) {
            cacheService.set(cacheKey(fmt), row.getContent(), CACHE_TTL_HOURS, TimeUnit.HOURS);
            return row.getContent();
        }
        String seed = loadClasspathSeed(fmt);
        if (seed == null || seed.isBlank()) {
            throw new BusinessException(500, "未找到格式 " + fmt + " 的默认规则模板");
        }
        // 仅缓存 DB 自定义模板；默认种子随 jar 更新，勿缓存 24h
        return seed;
    }

    private void persist(String fmt, String content, String sourceUrl, String updateSource) {
        long now = System.currentTimeMillis() / 1000;
        SubscribeRuleTemplate existing = loadRow(fmt);
        if (existing == null) {
            SubscribeRuleTemplate row = new SubscribeRuleTemplate();
            row.setFormat(fmt);
            row.setContent(content);
            row.setSourceUrl(sourceUrl);
            row.setUpdateSource(updateSource);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            if (mapper.insert(row) <= 0) {
                throw new BusinessException(500, "保存规则模板失败");
            }
        } else {
            existing.setContent(content);
            if (sourceUrl != null) {
                existing.setSourceUrl(sourceUrl);
            }
            existing.setUpdateSource(updateSource);
            existing.setUpdatedAt(now);
            if (mapper.updateById(existing) <= 0) {
                throw new BusinessException(500, "保存规则模板失败");
            }
        }
        invalidateCache(fmt);
        // 写入后立即回填缓存，避免并发读到旧 classpath
        cacheService.set(cacheKey(fmt), content, CACHE_TTL_HOURS, TimeUnit.HOURS);
    }

    private SubscribeRuleTemplate loadRow(String fmt) {
        try {
            return mapper.selectById(fmt);
        } catch (Exception e) {
            logger.warn("Load subscribe rule template failed format={}: {}", fmt, e.getMessage());
            return null;
        }
    }

    String loadClasspathSeed(String format) {
        String path = classpathPath(format);
        if (path == null) {
            return null;
        }
        String content = readClasspath(path);
        if (content == null && "stash".equals(format)) {
            return readClasspath(classpathPath("clash"));
        }
        return content;
    }

    static String classpathPath(String format) {
        return switch (format) {
            case "clash" -> "rules/default.clash.yaml";
            case "stash" -> "rules/default.stash.yaml";
            case "surge" -> "rules/default.surge.conf";
            case "surfboard" -> "rules/default.surfboard.conf";
            case "singbox" -> "rules/default.sing-box.json";
            case "quantumultx" -> "rules/default.quantumultx.conf";
            case "loon" -> "rules/default.loon.conf";
            default -> null;
        };
    }

    static String profileClasspathPath(String profile, String format) {
        String p = normalizeProfile(profile);
        if (PROFILE_FULL.equals(p)) {
            return classpathPath(format);
        }
        return switch (format) {
            case "clash", "stash" -> "rules/" + p + ".clash.yaml";
            case "surge" -> "rules/" + p + ".surge.conf";
            case "surfboard" -> "rules/" + p + ".surfboard.conf";
            case "singbox" -> "rules/" + p + ".sing-box.json";
            case "quantumultx" -> "rules/" + p + ".quantumultx.conf";
            case "loon" -> "rules/" + p + ".loon.conf";
            default -> null;
        };
    }

    private String readClasspath(String path) {
        if (path == null) {
            return null;
        }
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                return null;
            }
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.warn("Read classpath rule seed failed path={}: {}", path, e.getMessage());
            return null;
        }
    }

    String normalizeFormat(String format) {
        if (!StringUtils.hasText(format)) {
            throw new BusinessException(500, "format 不能为空");
        }
        String fmt = format.trim().toLowerCase(Locale.ROOT);
        if ("meta".equals(fmt) || "verge".equals(fmt) || "nyanpasu".equals(fmt)) {
            fmt = "clash";
        }
        if ("sing-box".equals(fmt)) {
            fmt = "singbox";
        }
        if (!SUPPORTED.contains(fmt)) {
            throw new BusinessException(500, "不支持的规则格式：" + format);
        }
        return fmt;
    }
}
