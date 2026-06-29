package com.v2board.api.util;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读取 PHP 面板写入的 config/v2board.php（var_export 扁平数组），与 Laravel config('v2board.*') 对齐。
 */
public final class V2boardPhpConfigLoader {

    private static final Pattern SCALAR_ENTRY = Pattern.compile(
            "'([a-zA-Z0-9_]+)'\\s*=>\\s*(?:'((?:\\\\'|[^'])*)'|\"((?:\\\\\"|[^\"])*)\"|(-?\\d+(?:\\.\\d+)?))");

    private V2boardPhpConfigLoader() {
    }

    public static Map<String, Object> load(String path) {
        if (!StringUtils.hasText(path)) {
            return Map.of();
        }
        try {
            Path file = Path.of(path.trim());
            if (!Files.isRegularFile(file)) {
                return Map.of();
            }
            return parseVarExportScalars(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Map.of();
        }
    }

    static Map<String, Object> parseVarExportScalars(String content) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        String body = content.replace("\r\n", "\n");
        int returnIdx = body.indexOf("return");
        if (returnIdx >= 0) {
            body = body.substring(returnIdx + 6);
        }
        body = body.replaceFirst("^\\s*array\\s*\\(\\s*", "");
        if (body.endsWith(";")) {
            body = body.substring(0, body.length() - 1).trim();
        }
        if (body.endsWith(")")) {
            body = body.substring(0, body.length() - 1).trim();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        Matcher m = SCALAR_ENTRY.matcher(body);
        while (m.find()) {
            String key = m.group(1);
            String singleQuoted = m.group(2);
            String doubleQuoted = m.group(3);
            String number = m.group(4);
            if (singleQuoted != null) {
                out.put(key, unescapePhpString(singleQuoted));
            } else if (doubleQuoted != null) {
                out.put(key, unescapePhpString(doubleQuoted));
            } else if (number != null) {
                if (number.contains(".")) {
                    out.put(key, Double.parseDouble(number));
                } else {
                    out.put(key, Long.parseLong(number));
                }
            }
        }
        return out;
    }

    private static String unescapePhpString(String s) {
        return s.replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
