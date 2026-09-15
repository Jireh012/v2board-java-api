package com.v2board.api.protocol;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * sing-box 客户端版本：从 flag / User-Agent 解析，并在订阅请求内绑定，供 DNS 1.14 改写使用。
 */
public final class SingboxVersion {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private static final Pattern SING_BOX = Pattern.compile("sing-box[\\s/]+([0-9.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGBOX = Pattern.compile("singbox[\\s/]+([0-9.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SFA_FAMILY = Pattern.compile(
            "\\b(?:sfa|sfi|sfm|sft)/([0-9.]+)", Pattern.CASE_INSENSITIVE);

    private SingboxVersion() {
    }

    /** flag 或 UA 是否应下发 sing-box JSON（含 SFA/SFI/SFM/SFT）。 */
    public static boolean wantsJson(String flagOrUa) {
        if (flagOrUa == null || flagOrUa.isBlank()) {
            return false;
        }
        String f = flagOrUa.toLowerCase(Locale.ROOT);
        if (f.contains("sing")) {
            return true;
        }
        return f.contains("sfa/") || f.contains("sfi/") || f.contains("sfm/") || f.contains("sft/");
    }

    public static String extract(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher singBox = SING_BOX.matcher(text);
        if (singBox.find()) {
            return singBox.group(1);
        }
        Matcher family = SFA_FAMILY.matcher(text);
        if (family.find()) {
            return family.group(1);
        }
        Matcher singbox = SINGBOX.matcher(text);
        if (singbox.find()) {
            return singbox.group(1);
        }
        return null;
    }

    /** 显式 &lt; 1.12 才用旧模板；无版本号默认走 ≥1.12。 */
    public static boolean useLegacyTemplate(String version) {
        return version != null && !atLeast(version, 1, 12);
    }

    public static boolean needsDnsResponseMatch(String version) {
        return atLeast(version, 1, 14);
    }

    public static boolean atLeast(String version, int major, int minor) {
        if (version == null || version.isBlank()) {
            return false;
        }
        String[] parts = version.trim().split("\\.");
        try {
            int vMajor = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            int vMinor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (vMajor != major) {
                return vMajor > major;
            }
            return vMinor >= minor;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void bind(String version) {
        CURRENT.set(version);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static String current() {
        return CURRENT.get();
    }
}
