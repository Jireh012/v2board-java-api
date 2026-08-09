package com.v2board.api.service.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component
public class ExternalSubscribeFetcher {

    private static final Logger logger = LoggerFactory.getLogger(ExternalSubscribeFetcher.class);
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_REDIRECTS = 5;
    /**
     * 部分面板会拦截非客户端 UA（如自定义 java-api 标识 → HTTP 403）。
     * 优先使用常见订阅客户端 UA，403 时再回退尝试。
     */
    private static final String[] USER_AGENTS = {
            "clash-verge/v1.7.7",
            "ClashMetaForAndroid/2.11.0",
            "v2rayN/6.45"
    };

    public String fetch(String url) throws Exception {
        return fetch(url, null);
    }

    /**
     * @param proxy optional HTTP proxy (e.g. local sing-box mixed); null = direct
     */
    public String fetch(String url, Proxy proxy) throws Exception {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("订阅地址为空");
        }
        Exception last = null;
        for (String ua : USER_AGENTS) {
            try {
                return fetchWithUserAgent(url.trim(), ua, proxy);
            } catch (IllegalStateException e) {
                last = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("HTTP 403") || msg.contains("User-Agent")) {
                    logger.info("Subscribe fetch blocked for UA={}, retry next: {}", ua, msg);
                    continue;
                }
                throw e;
            }
        }
        throw last != null ? last : new IllegalStateException("拉取失败");
    }

    private String fetchWithUserAgent(String url, String userAgent, Proxy proxy) throws Exception {
        String current = url;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            URL target = URI.create(current).toURL();
            HttpURLConnection conn = (HttpURLConnection) (proxy != null
                    ? target.openConnection(proxy)
                    : target.openConnection());
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", userAgent);
            conn.setRequestProperty("Accept", "*/*");
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isBlank()) {
                    throw new IllegalStateException("重定向缺少 Location: HTTP " + code);
                }
                current = resolveRedirect(current, location);
                continue;
            }
            if (code < 200 || code >= 300) {
                String errBody = readErrorBody(conn);
                conn.disconnect();
                if (code == 403 && errBody != null && errBody.toLowerCase().contains("user-agent")) {
                    throw new IllegalStateException("拉取失败: HTTP 403 (User-Agent 被拦截)");
                }
                throw new IllegalStateException("拉取失败: HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                byte[] body = readLimited(in, MAX_BYTES);
                String contentType = conn.getContentType();
                logger.debug("Fetched subscribe content, ua={}, proxy={}, type={}, bytes={}",
                        userAgent, proxy != null, contentType, body.length);
                return new String(body, StandardCharsets.UTF_8);
            } finally {
                conn.disconnect();
            }
        }
        throw new IllegalStateException("重定向次数过多");
    }

    private static String readErrorBody(HttpURLConnection conn) {
        try (InputStream err = conn.getErrorStream()) {
            if (err == null) {
                return null;
            }
            byte[] body = readLimited(err, 4096);
            return new String(body, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolveRedirect(String current, String location) {
        try {
            URL base = URI.create(current).toURL();
            return new URL(base, location).toString();
        } catch (Exception e) {
            return location;
        }
    }

    private static byte[] readLimited(InputStream in, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > max) {
                throw new IllegalStateException("订阅内容超过大小限制");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
