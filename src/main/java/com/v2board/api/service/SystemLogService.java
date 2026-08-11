package com.v2board.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.mapper.SystemLogMapper;
import com.v2board.api.model.SystemLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persist ERROR+ events to PHP-compatible {@code v2_log}.
 * Write failures go only to logger {@value #FALLBACK_LOGGER} (no Db appender).
 */
@Service
public class SystemLogService {

    public static final String FALLBACK_LOGGER = "system-log-fallback";

    static final int TITLE_MAX = 2000;
    static final int CONTEXT_MAX = 8000;
    static final int DATA_MAX = 4000;

    private static final Logger fallbackLog = LoggerFactory.getLogger(FALLBACK_LOGGER);

    private static final ThreadLocal<Boolean> REENTRY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(password|passwd|token|authorization|secret|sm4|app_key|apikey|api[_-]?key).*");

    private static final Pattern QUERY_SENSITIVE = Pattern.compile(
            "(?i)([?&])(password|passwd|token|authorization|secret|sm4_key|sm4|app_key|apikey|api[_-]?key)=([^&]*)");

    @Autowired
    private SystemLogMapper systemLogMapper;

    @Autowired
    private ObjectMapper objectMapper;

    public static boolean isReentry() {
        return Boolean.TRUE.equals(REENTRY.get());
    }

    public SystemLog buildRow(String level,
                              String loggerName,
                              String message,
                              String throwableClass,
                              String throwableMessage,
                              String stackTrace,
                              RequestMeta meta) {
        long now = System.currentTimeMillis() / 1000;
        SystemLog row = new SystemLog();
        row.setTitle(truncate(message != null ? message : "", TITLE_MAX));
        row.setLevel(level != null ? truncate(level, 11) : "ERROR");
        row.setHost(truncate(resolveHost(meta), 255));
        if (meta != null && StringUtils.hasText(meta.uri())) {
            row.setUri(truncate(meta.uri(), 255));
            row.setMethod(truncate(StringUtils.hasText(meta.method()) ? meta.method() : "GET", 11));
            row.setIp(truncate(meta.ip() != null ? meta.ip() : "", 128));
            row.setData(truncate(redactQuery(meta.queryString()), DATA_MAX));
        } else {
            row.setUri("-");
            row.setMethod("SCHEDULE");
            row.setIp("");
            row.setData(null);
        }
        row.setContext(truncate(buildContextJson(loggerName, throwableClass, throwableMessage, stackTrace), CONTEXT_MAX));
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    @Async("systemLogExecutor")
    public void persistAsync(SystemLog row) {
        if (row == null || isReentry()) {
            return;
        }
        REENTRY.set(Boolean.TRUE);
        try {
            systemLogMapper.insert(row);
        } catch (Exception e) {
            fallbackLog.error("persist v2_log failed: {}", e.getMessage());
        } finally {
            REENTRY.remove();
        }
    }

    String redactQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        String q = query.startsWith("?") ? query : "?" + query;
        Matcher m = QUERY_SENSITIVE.matcher(q);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + m.group(2) + "=***"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    String redactJsonLike(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            return objectMapper.writeValueAsString(redactValue(parsed));
        } catch (Exception ignored) {
            return redactQuery(raw);
        }
    }

    Object redactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (SENSITIVE_KEY.matcher(key).matches()) {
                    out.put(key, "***");
                } else {
                    out.put(key, redactValue(e.getValue()));
                }
            }
            return out;
        }
        if (value instanceof Iterable<?> it) {
            java.util.List<Object> list = new java.util.ArrayList<>();
            for (Object o : it) {
                list.add(redactValue(o));
            }
            return list;
        }
        return value;
    }

    static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private String buildContextJson(String loggerName,
                                    String throwableClass,
                                    String throwableMessage,
                                    String stackTrace) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("logger", loggerName);
        if (StringUtils.hasText(throwableClass)) {
            ctx.put("exception", throwableClass);
        }
        if (StringUtils.hasText(throwableMessage)) {
            ctx.put("exception_message", redactPlain(throwableMessage));
        }
        if (StringUtils.hasText(stackTrace)) {
            ctx.put("stack", truncate(stackTrace, CONTEXT_MAX - 512));
        }
        try {
            return objectMapper.writeValueAsString(redactValue(ctx));
        } catch (JsonProcessingException e) {
            return "{\"logger\":\"" + (loggerName != null ? loggerName : "") + "\"}";
        }
    }

    private static String redactPlain(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceAll("(?i)(password|token|authorization|sm4[_-]?key|app[_-]?key)\\s*[=:]\\s*\\S+", "$1=***");
    }

    private static String resolveHost(RequestMeta meta) {
        if (meta != null && StringUtils.hasText(meta.host())) {
            return meta.host();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }

    public record RequestMeta(String host, String uri, String method, String ip, String queryString) {
        public static RequestMeta nonHttp() {
            return null;
        }
    }
}
