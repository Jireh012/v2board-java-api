package com.v2board.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Derives opaque action aliases from panel {@code SM4_KEY} and resolves them back to classicRel.
 */
@Component
public class PanelApiActionAliases {

    private final String sm4Key;
    /** zone → (alias → classicRel) */
    private final Map<String, Map<String, String>> byZone;

    public PanelApiActionAliases(@Value("${v2board.sm4-key:}") String sm4Key) {
        this.sm4Key = sm4Key == null ? "" : sm4Key;
        this.byZone = buildMaps(this.sm4Key);
    }

    /** Test helper with an explicit key. */
    static PanelApiActionAliases withKey(String sm4Key) {
        return new PanelApiActionAliases(sm4Key);
    }

    public static String normalizeClassicRel(String path) {
        if (path == null) {
            return "";
        }
        String t = path.trim();
        while (t.startsWith("/")) {
            t = t.substring(1);
        }
        while (t.length() > 1 && t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    public static String deriveAlias(String sm4Key, String zone, String classicRel) {
        String key = sm4Key == null ? "" : sm4Key;
        String z = zone == null ? "" : zone;
        String rel = normalizeClassicRel(classicRel);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(key.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(z.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(rel.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 6; i++) {
                sb.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String aliasFor(String zone, String classicRel) {
        return deriveAlias(sm4Key, zone, classicRel);
    }

    /**
     * @return classicRel or null if alias unknown for zone
     */
    public String resolveClassicRel(String zone, String aliasSegment) {
        if (!StringUtils.hasText(zone) || !StringUtils.hasText(aliasSegment)) {
            return null;
        }
        Map<String, String> map = byZone.get(zone);
        if (map == null) {
            return null;
        }
        return map.get(aliasSegment.toLowerCase(Locale.ROOT));
    }

    private static Map<String, Map<String, String>> buildMaps(String sm4Key) {
        Map<String, Map<String, String>> zones = new HashMap<>();
        for (PanelApiActionCatalog.ZonePath zp : PanelApiActionCatalog.all()) {
            String alias = deriveAlias(sm4Key, zp.zone(), zp.classicRel());
            Map<String, String> map = zones.computeIfAbsent(zp.zone(), k -> new HashMap<>());
            String prev = map.put(alias, zp.classicRel());
            if (prev != null && !prev.equals(zp.classicRel())) {
                throw new IllegalStateException(
                        "Panel action alias collision for zone=" + zp.zone()
                                + " alias=" + alias + " a=" + prev + " b=" + zp.classicRel());
            }
        }
        Map<String, Map<String, String>> frozen = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> e : zones.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableMap(e.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }
}
