package com.v2board.api.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.v2board.api.model.SystemLog;
import com.v2board.api.service.SystemLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Logback appender: ERROR+ → {@link SystemLogService#persistAsync}.
 */
public class DbErrorAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private final SystemLogService systemLogService;

    public DbErrorAppender(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    @Override
    protected void append(ILoggingEvent event) {
        // Never let persistence failures disrupt the original logging / request path.
        try {
            if (event == null || systemLogService == null) {
                return;
            }
            if (SystemLogService.isReentry()) {
                return;
            }
            String loggerName = event.getLoggerName();
            if (SystemLogService.FALLBACK_LOGGER.equals(loggerName)) {
                return;
            }
            if (loggerName != null && loggerName.startsWith(DbErrorAppender.class.getName())) {
                return;
            }

            IThrowableProxy tp = event.getThrowableProxy();
            String exClass = tp != null ? tp.getClassName() : null;
            String exMsg = tp != null ? tp.getMessage() : null;
            String stack = tp != null ? renderStack(tp) : null;

            SystemLogService.RequestMeta meta = extractRequestMeta();
            SystemLog row = systemLogService.buildRow(
                    event.getLevel() != null ? event.getLevel().toString() : "ERROR",
                    loggerName,
                    event.getFormattedMessage(),
                    exClass,
                    exMsg,
                    stack,
                    meta);
            systemLogService.persistAsync(row);
        } catch (Exception ignored) {
            // Swallow: fallback path is inside SystemLogService; avoid Logback status storms.
        }
    }

    private static SystemLogService.RequestMeta extractRequestMeta() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes sra)) {
                return null;
            }
            HttpServletRequest request = sra.getRequest();
            if (request == null) {
                return null;
            }
            String uri = request.getRequestURI();
            String method = request.getMethod();
            String host = request.getHeader("Host");
            if (!StringUtils.hasText(host)) {
                host = request.getServerName();
            }
            String ip = resolveClientIp(request);
            String query = request.getQueryString();
            return new SystemLogService.RequestMeta(host, uri, method, ip, query);
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            String first = xff.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first.length() > 128 ? first.substring(0, 128) : first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            String v = realIp.trim();
            return v.length() > 128 ? v.substring(0, 128) : v;
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "";
    }

    private static String renderStack(IThrowableProxy tp) {
        StringBuilder sb = new StringBuilder();
        appendProxy(sb, tp, 0);
        return sb.toString();
    }

    private static void appendProxy(StringBuilder sb, IThrowableProxy tp, int depth) {
        if (tp == null || depth > 5) {
            return;
        }
        sb.append(tp.getClassName()).append(": ").append(tp.getMessage()).append('\n');
        StackTraceElementProxy[] arr = tp.getStackTraceElementProxyArray();
        if (arr != null) {
            int limit = Math.min(arr.length, 80);
            for (int i = 0; i < limit; i++) {
                sb.append("\tat ").append(arr[i].getSTEAsString()).append('\n');
            }
            if (arr.length > limit) {
                sb.append("\t... ").append(arr.length - limit).append(" more\n");
            }
        }
        if (tp.getCause() != null) {
            sb.append("Caused by: ");
            appendProxy(sb, tp.getCause(), depth + 1);
        }
    }
}
