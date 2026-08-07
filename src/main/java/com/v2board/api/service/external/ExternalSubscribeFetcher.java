package com.v2board.api.service.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
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

    public String fetch(String url) throws Exception {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("订阅地址为空");
        }
        String current = url.trim();
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection conn = (HttpURLConnection) URI.create(current).toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "v2board-java-api/external-subscribe");
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
                conn.disconnect();
                throw new IllegalStateException("拉取失败: HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                byte[] body = readLimited(in, MAX_BYTES);
                String contentType = conn.getContentType();
                logger.debug("Fetched subscribe content, type={}, bytes={}", contentType, body.length);
                return new String(body, StandardCharsets.UTF_8);
            } finally {
                conn.disconnect();
            }
        }
        throw new IllegalStateException("重定向次数过多");
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
