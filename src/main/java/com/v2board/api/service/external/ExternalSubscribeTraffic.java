package com.v2board.api.service.external;

import com.v2board.api.model.ExternalSubscribeSource;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects third-party subscribe quota from HTTP {@code subscription-userinfo}
 * and upstream info-node names (e.g. {@code 剩余流量：0 GB}).
 */
public final class ExternalSubscribeTraffic {

    private static final Pattern HEADER_PAIR = Pattern.compile(
            "(?i)(upload|download|total|expire)\\s*=\\s*(-?\\d+)");

    private static final Pattern REMAINING_ZERO_WORDS = Pattern.compile(
            "(?i)(剩余\\s*流量|traffic\\s*(?:left|remain(?:ing)?))"
                    + ".*(已用完|用尽|耗尽|用完|exhausted|none|no\\s*traffic)");

    private static final Pattern REMAINING_AMOUNT = Pattern.compile(
            "(?i)(?:剩余\\s*流量|traffic\\s*(?:left|remain(?:ing)?))"
                    + "[^0-9-]*(-?[0-9]+(?:\\.[0-9]+)?)\\s*(B|KB|MB|GB|TB|KiB|MiB|GiB)?");

    private ExternalSubscribeTraffic() {
    }

    public record Snapshot(Long upload, Long download, Long total, Long expire, boolean exhausted) {
        public static Snapshot unknown() {
            return new Snapshot(null, null, null, null, false);
        }
    }

    /**
     * Enabled sources with {@code traffic_exhausted=1} must not enter user subscribe
     * or be used as pre-proxy candidates.
     */
    public static boolean isDeliverable(ExternalSubscribeSource source) {
        if (source == null || source.getId() == null) {
            return false;
        }
        Integer exhausted = source.getTrafficExhausted();
        return exhausted == null || exhausted != 1;
    }

    public static Snapshot resolve(String subscriptionUserinfo, List<CanonicalExternalNode> nodes) {
        return resolveFrom(subscriptionUserinfo, namesOf(nodes));
    }

    static Snapshot resolveFrom(String subscriptionUserinfo, List<String> names) {
        Header header = parseHeader(subscriptionUserinfo);
        Long remaining = remainingBytesFromNames(names);
        boolean exhausted = false;
        if (header != null && header.total != null && header.total > 0) {
            exhausted = usedAtLeast(header.upload, header.download, header.total);
        }
        if (remaining != null && remaining <= 0) {
            exhausted = true;
        }
        if (header == null && remaining == null) {
            return Snapshot.unknown();
        }
        if (header != null) {
            Long upload = header.upload;
            Long download = header.download;
            Long total = header.total;
            if ((total == null || total <= 0) && remaining != null && remaining > 0) {
                upload = 0L;
                download = 0L;
                total = remaining;
            }
            return new Snapshot(upload, download, total, header.expire, exhausted);
        }
        if (remaining > 0) {
            return new Snapshot(0L, 0L, remaining, null, false);
        }
        return new Snapshot(null, null, null, null, true);
    }

    public static Header parseHeader(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Long upload = null;
        Long download = null;
        Long total = null;
        Long expire = null;
        Matcher m = HEADER_PAIR.matcher(raw);
        while (m.find()) {
            String key = m.group(1).toLowerCase();
            long value = Long.parseLong(m.group(2));
            switch (key) {
                case "upload" -> upload = value;
                case "download" -> download = value;
                case "total" -> total = value;
                case "expire" -> expire = value;
                default -> {
                }
            }
        }
        if (upload == null && download == null && total == null && expire == null) {
            return null;
        }
        return new Header(nz(upload), nz(download), total, expire);
    }

    static Long remainingBytesFromNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        Long min = null;
        for (String name : names) {
            Long value = remainingBytesFromName(name);
            if (value == null) {
                continue;
            }
            min = min == null ? value : Math.min(min, value);
        }
        return min;
    }

    static Long remainingBytesFromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = normalizeRemainingName(name);
        if (REMAINING_ZERO_WORDS.matcher(n).find()) {
            return 0L;
        }
        Matcher m = REMAINING_AMOUNT.matcher(n);
        if (!m.find()) {
            return null;
        }
        double amount = Double.parseDouble(m.group(1));
        String unit = m.group(2);
        return Math.round(amount * unitMultiplier(unit));
    }

    /** ASCII minus, fullwidth minus, unicode minus, and 负 → so -1.5 GB is not parsed as 1.5. */
    private static String normalizeRemainingName(String name) {
        return name.trim()
                .replaceFirst("^[🔒⚠️\\s]+", "")
                .replace('\uFF0D', '-')
                .replace('\u2212', '-')
                .replace("负", "-");
    }

    private static List<String> namesOf(List<CanonicalExternalNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        return nodes.stream()
                .filter(n -> n != null && n.getName() != null)
                .map(CanonicalExternalNode::getName)
                .toList();
    }

    private static boolean usedAtLeast(long upload, long download, long total) {
        long used;
        try {
            used = Math.addExact(upload, download);
        } catch (ArithmeticException e) {
            used = Long.MAX_VALUE;
        }
        return used >= total;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static double unitMultiplier(String unit) {
        if (unit == null || unit.isBlank()) {
            return 1073741824d;
        }
        return switch (unit.toUpperCase()) {
            case "B" -> 1d;
            case "KB", "KIB" -> 1024d;
            case "MB", "MIB" -> 1048576d;
            case "GB", "GIB" -> 1073741824d;
            case "TB" -> 1099511627776d;
            default -> 1073741824d;
        };
    }

    public record Header(long upload, long download, Long total, Long expire) {
    }
}
