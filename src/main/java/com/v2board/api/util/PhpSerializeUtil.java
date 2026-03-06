package com.v2board.api.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与 PHP serialize/unserialize 兼容的序列化工具，
 * 用于 NodeCacheService 与 PHP 共用 Redis DB1 时读写同一格式。
 * <p>
 * 支持类型：整数(i:)、浮点(d:)、字符串(s:)、数组(a:)、null(N;)。
 */
public final class PhpSerializeUtil {

    private PhpSerializeUtil() {
    }

    // ---------- serialize ----------

    /**
     * 将 Java 对象序列化为 PHP serialize 字符串。
     */
    public static String serialize(Object value) {
        if (value == null) {
            return "N;";
        }
        if (value instanceof Number n) {
            if (value instanceof Float || value instanceof Double) {
                return "d:" + n.doubleValue() + ";";
            }
            return "i:" + n.longValue() + ";";
        }
        if (value instanceof String s) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            return "s:" + bytes.length + ":\"" + s + "\";";
        }
        if (value instanceof Boolean b) {
            return "b:" + (b ? "1" : "0") + ";";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            sb.append("a:").append(map.size()).append(":{");
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object k = e.getKey();
                Object v = e.getValue();
                appendKey(sb, k);
                sb.append(serialize(v));
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof Iterable<?> iter) {
            List<Object> list = new ArrayList<>();
            for (Object o : iter) {
                list.add(o);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("a:").append(list.size()).append(":{");
            for (int i = 0; i < list.size(); i++) {
                sb.append("i:").append(i).append(";");
                sb.append(serialize(list.get(i)));
            }
            sb.append("}");
            return sb.toString();
        }
        // 兜底：按字符串
        return serialize(String.valueOf(value));
    }

    private static void appendKey(StringBuilder sb, Object k) {
        if (k instanceof Integer || k instanceof Long) {
            sb.append("i:").append(((Number) k).longValue()).append(";");
        } else {
            sb.append(serialize(String.valueOf(k)));
        }
    }

    // ---------- unserialize ----------

    /**
     * 将 PHP serialize 字符串反序列化为 Java 对象。
     * 返回类型可为 Long, Double, String, Map&lt;String,Object&gt;, List&lt;Object&gt;, null。
     */
    public static Object unserialize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("N;")) {
            return null;
        }
        ParseContext ctx = new ParseContext(s);
        try {
            return parseValue(ctx);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object parseValue(ParseContext ctx) {
        if (ctx.pos >= ctx.s.length()) {
            return null;
        }
        char c = ctx.s.charAt(ctx.pos);
        if (c == 'i') {
            return parseInteger(ctx);
        }
        if (c == 'd') {
            return parseDouble(ctx);
        }
        if (c == 's') {
            return parseString(ctx);
        }
        if (c == 'a') {
            return parseArray(ctx);
        }
        if (c == 'b') {
            return parseBoolean(ctx);
        }
        if (c == 'N') {
            ctx.pos += 2; // N;
            return null;
        }
        return null;
    }

    private static long parseInteger(ParseContext ctx) {
        // i:123;
        ctx.pos += 2; // "i:"
        int start = ctx.pos;
        while (ctx.pos < ctx.s.length() && ctx.s.charAt(ctx.pos) != ';') {
            ctx.pos++;
        }
        String num = ctx.s.substring(start, ctx.pos);
        ctx.pos++; // ';'
        return Long.parseLong(num.trim());
    }

    private static double parseDouble(ParseContext ctx) {
        ctx.pos += 2;
        int start = ctx.pos;
        while (ctx.pos < ctx.s.length() && ctx.s.charAt(ctx.pos) != ';') {
            ctx.pos++;
        }
        String num = ctx.s.substring(start, ctx.pos);
        ctx.pos++;
        return Double.parseDouble(num.trim());
    }

    private static String parseString(ParseContext ctx) {
        // s:5:"hello";
        ctx.pos += 2; // "s:"
        int start = ctx.pos;
        while (ctx.pos < ctx.s.length() && Character.isDigit(ctx.s.charAt(ctx.pos))) {
            ctx.pos++;
        }
        int len = Integer.parseInt(ctx.s.substring(start, ctx.pos).trim());
        ctx.pos++; // ':'
        ctx.pos++; // '"'
        String content = ctx.s.substring(ctx.pos, ctx.pos + len);
        ctx.pos += len;
        ctx.pos += 2; // "';
        return content;
    }

    private static boolean parseBoolean(ParseContext ctx) {
        ctx.pos += 2; // "b:"
        char v = ctx.s.charAt(ctx.pos);
        ctx.pos += 2; // "1;" or "0;"
        return v == '1';
    }

    private static Object parseArray(ParseContext ctx) {
        // a:2:{i:0;s:3:"foo";i:1;i:42;} 或 a:2:{s:3:"key";s:5:"value";}
        ctx.pos += 2; // "a:"
        int start = ctx.pos;
        while (ctx.pos < ctx.s.length() && Character.isDigit(ctx.s.charAt(ctx.pos))) {
            ctx.pos++;
        }
        int count = Integer.parseInt(ctx.s.substring(start, ctx.pos).trim());
        ctx.pos++; // ':'
        ctx.pos++; // '{'

        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            Object key = parseValue(ctx);
            Object val = parseValue(ctx);
            String k = key instanceof Number ? String.valueOf(((Number) key).longValue()) : String.valueOf(key);
            map.put(k != null ? k : "", val);
        }

        if (ctx.pos < ctx.s.length() && ctx.s.charAt(ctx.pos) == '}') {
            ctx.pos++;
        }

        // 若键为 "0","1",...,"n-1" 则返回 List，便于调用方按 List 使用
        boolean isConsecutiveList = true;
        for (int i = 0; i < count; i++) {
            if (!map.containsKey(String.valueOf(i))) {
                isConsecutiveList = false;
                break;
            }
        }
        if (isConsecutiveList && count > 0) {
            List<Object> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(map.get(String.valueOf(i)));
            }
            return list;
        }
        return map;
    }

    private static class ParseContext {
        final String s;
        int pos;

        ParseContext(String s) {
            this.s = s;
            this.pos = 0;
        }
    }
}
