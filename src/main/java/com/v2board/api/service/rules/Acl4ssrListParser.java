package com.v2board.api.service.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parse ACL4SSR Clash classical .list payloads into typed entries.
 */
public final class Acl4ssrListParser {

    private Acl4ssrListParser() {
    }

    /**
     * @param kind domain-suffix | domain | domain-keyword | ip-cidr | ip-cidr6 | user-agent | process-name
     */
    public record Entry(String kind, String value) {
    }

    public static List<Entry> parse(String text) {
        List<Entry> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String raw : text.split("\\R", -1)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//") || line.startsWith(";")) {
                continue;
            }
            // YAML payload: wrapper
            if (line.startsWith("payload:") || line.equals("-")) {
                continue;
            }
            if (line.startsWith("- ")) {
                line = line.substring(2).trim();
                if ((line.startsWith("'") && line.endsWith("'")) || (line.startsWith("\"") && line.endsWith("\""))) {
                    line = line.substring(1, line.length() - 1).trim();
                }
            }
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("DOMAIN-SUFFIX,")) {
                out.add(new Entry("domain-suffix", firstField(line)));
            } else if (upper.startsWith("DOMAIN-KEYWORD,")) {
                out.add(new Entry("domain-keyword", firstField(line)));
            } else if (upper.startsWith("DOMAIN,")) {
                out.add(new Entry("domain", firstField(line)));
            } else if (upper.startsWith("IP-CIDR6,")) {
                out.add(new Entry("ip-cidr6", firstField(line)));
            } else if (upper.startsWith("IP-CIDR,")) {
                out.add(new Entry("ip-cidr", firstField(line)));
            } else if (upper.startsWith("USER-AGENT,")) {
                out.add(new Entry("user-agent", firstField(line)));
            } else if (upper.startsWith("PROCESS-NAME,")) {
                out.add(new Entry("process-name", firstField(line)));
            } else if (line.startsWith(".")) {
                out.add(new Entry("domain-suffix", stripLeadingDots(line)));
            } else if (looksLikeCidr(line)) {
                String cidr = line.split(",", 2)[0].trim();
                out.add(new Entry(cidr.contains(":") ? "ip-cidr6" : "ip-cidr", cidr));
            } else {
                String host = line.split(",", 2)[0].trim();
                if (!host.isEmpty() && !host.contains(" ") && !host.contains("=")) {
                    out.add(new Entry("domain-suffix", host.startsWith(".") ? host.substring(1) : host));
                }
            }
        }
        return out;
    }

    private static String firstField(String line) {
        String[] parts = line.split(",", 3);
        return parts.length >= 2 ? parts[1].trim() : "";
    }

    private static boolean looksLikeCidr(String line) {
        String head = line.split(",", 2)[0].trim();
        int slash = head.indexOf('/');
        if (slash <= 0) {
            return false;
        }
        String ip = head.substring(0, slash);
        return ip.chars().anyMatch(Character::isDigit);
    }

    private static String stripLeadingDots(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '.') {
            i++;
        }
        return s.substring(i);
    }
}
