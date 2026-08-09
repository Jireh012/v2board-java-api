package com.v2board.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.util.PanelSm4Support;
import com.v2board.api.util.Sm4Util;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Panel SM4 for user/passport/admin (and public config): decrypt JSON request envelope,
 * encrypt entire JSON response. Fail closed when {@code SM4_KEY} missing.
 */
public class PanelSm4Filter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(PanelSm4Filter.class);

    private final ObjectMapper objectMapper;
    private final String sm4Key;

    public PanelSm4Filter(ObjectMapper objectMapper, String sm4Key) {
        this.objectMapper = objectMapper;
        this.sm4Key = sm4Key;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!PanelSm4Support.isPanelSm4(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] key;
        try {
            if (!StringUtils.hasText(sm4Key)) {
                writePlainError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SM4 key not configured");
                return;
            }
            key = Sm4Util.parseKey(sm4Key);
        } catch (IllegalArgumentException e) {
            writePlainError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SM4 key invalid: " + e.getMessage());
            return;
        }

        HttpServletRequest effectiveRequest = request;
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            try {
                effectiveRequest = decryptBodyIfPresent(request, key);
            } catch (IllegalArgumentException e) {
                writePlainError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
                return;
            } catch (Exception e) {
                logger.warn("Panel SM4 request decrypt failed: {}", e.getMessage());
                writePlainError(response, HttpServletResponse.SC_BAD_REQUEST, "请求体解密失败");
                return;
            }
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(effectiveRequest, wrapped);

        byte[] raw = wrapped.getContentAsByteArray();
        if (raw.length == 0) {
            wrapped.copyBodyToResponse();
            return;
        }
        String contentType = wrapped.getContentType();
        if (contentType != null && !contentType.toLowerCase().contains("json")) {
            wrapped.copyBodyToResponse();
            return;
        }
        try {
            String plaintext = new String(raw, StandardCharsets.UTF_8);
            Map<String, String> envelope = Sm4Util.encryptToEnvelope(plaintext, key);
            byte[] out = objectMapper.writeValueAsBytes(envelope);
            response.resetBuffer();
            response.setStatus(wrapped.getStatus());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setContentLength(out.length);
            response.getOutputStream().write(out);
        } catch (Exception e) {
            logger.error("Panel SM4 response encrypt failed", e);
            writePlainError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "响应加密失败");
        }
    }

    private HttpServletRequest decryptBodyIfPresent(HttpServletRequest request, byte[] key) throws Exception {
        byte[] body = request.getInputStream().readAllBytes();
        if (body.length == 0) {
            return request;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/x-www-form-urlencoded")) {
            throw new IllegalArgumentException("加密区仅接受 JSON 信封请求体");
        }
        JsonNode node = objectMapper.readTree(body);
        if (!node.isObject() || !node.hasNonNull("iv") || !node.hasNonNull("payload")) {
            throw new IllegalArgumentException("请求体必须为 SM4 信封 {iv,payload}");
        }
        String plain = Sm4Util.decryptFromEnvelope(node.get("iv").asText(), node.get("payload").asText(), key);
        byte[] plainBytes = plain.getBytes(StandardCharsets.UTF_8);
        return new DecryptedBodyRequest(request, plainBytes);
    }

    private void writePlainError(HttpServletResponse response, int status, String message) throws IOException {
        response.resetBuffer();
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
                "code", status >= 400 && status < 600 ? status : 500,
                "message", message
        );
        response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
    }

    private static final class DecryptedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        DecryptedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return bais.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public String getContentType() {
            return MediaType.APPLICATION_JSON_VALUE;
        }

        @Override
        public String getHeader(String name) {
            if ("Content-Type".equalsIgnoreCase(name)) {
                return MediaType.APPLICATION_JSON_VALUE;
            }
            return super.getHeader(name);
        }
    }

    @Configuration
    static class PanelSm4FilterConfig {
        @Bean
        PanelSm4Filter panelSm4Filter(ObjectMapper objectMapper,
                                      @Value("${v2board.sm4-key:}") String sm4Key) {
            return new PanelSm4Filter(objectMapper, sm4Key);
        }

        @Bean
        FilterRegistrationBean<PanelSm4Filter> panelSm4FilterRegistration(PanelSm4Filter filter) {
            FilterRegistrationBean<PanelSm4Filter> reg = new FilterRegistrationBean<>();
            reg.setFilter(filter);
            reg.addUrlPatterns("/*");
            reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
            reg.setName("panelSm4Filter");
            return reg;
        }
    }
}
