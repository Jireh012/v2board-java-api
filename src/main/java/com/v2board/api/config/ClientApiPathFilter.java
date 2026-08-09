package com.v2board.api.config;

import com.v2board.api.util.PanelSm4Support;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Hard-cutover classic {@code /api/v1/user|passport|admin} → 404; rewrite configured prefixes
 * + action aliases to internal classic mappings. Marks panel SM4 zone for {@link PanelSm4Filter}.
 */
public class ClientApiPathFilter extends OncePerRequestFilter {

    private final ClientApiPathRegistry registry;
    private final PanelApiActionAliases actionAliases;

    public ClientApiPathFilter(ClientApiPathRegistry registry, PanelApiActionAliases actionAliases) {
        this.registry = registry;
        this.actionAliases = actionAliases;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!registry.hasPrefixes()) {
            registry.refresh();
        }
        String path = servletPath(request);

        if (isClassicPanelApi(path) || isClassicGuestPayment(path)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String publicConfigPath = registry.getPublicConfigPath();
        if (StringUtils.hasText(publicConfigPath) && pathEquals(path, publicConfigPath)) {
            request.setAttribute(PanelSm4Support.ATTR_PANEL_SM4, Boolean.TRUE);
            filterChain.doFilter(request, response);
            return;
        }

        if (tryHandlePaymentNotify(request, response, filterChain, path)) {
            return;
        }

        if (tryHandleZone(request, response, filterChain, path, registry.getUserPrefix(), "user", "/api/v1/user")) {
            return;
        }
        if (tryHandleZone(request, response, filterChain, path, registry.getPassportPrefix(), "passport", "/api/v1/passport")) {
            return;
        }
        if (tryHandleZone(request, response, filterChain, path, registry.getAdminPrefix(), "admin", "/api/v1/admin")) {
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Plaintext payment callback: {@code {payment_notify_prefix}/{method}/{uuid}} →
     * {@code /api/v1/guest/payment/notify/{method}/{uuid}}. No Panel SM4.
     */
    private boolean tryHandlePaymentNotify(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            String path
    ) throws IOException, ServletException {
        String prefix = registry.getPaymentNotifyPrefix();
        if (!matchesPrefix(path, prefix)) {
            return false;
        }
        String rewritten = rewritePaymentNotify(path, prefix);
        if (rewritten == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }
        filterChain.doFilter(new RewrittenRequest(request, rewritten), response);
        return true;
    }

    private boolean tryHandleZone(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            String path,
            String prefix,
            String zone,
            String classicBase
    ) throws IOException, ServletException {
        if (!matchesPrefix(path, prefix)) {
            return false;
        }
        String rewritten = rewriteAliased(path, prefix, zone, classicBase, actionAliases);
        if (rewritten == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }
        markEncryptedZone(request);
        filterChain.doFilter(new RewrittenRequest(request, rewritten), response);
        return true;
    }

    private static void markEncryptedZone(HttpServletRequest request) {
        request.setAttribute(PanelSm4Support.ATTR_PANEL_SM4, Boolean.TRUE);
        request.setAttribute(PanelSm4Support.ATTR_REJECT_CLASSIC_AUTH, Boolean.TRUE);
    }

    static boolean matchesPrefix(String path, String prefix) {
        if (!StringUtils.hasText(prefix) || !StringUtils.hasText(path)) {
            return false;
        }
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    /**
     * Rewrite {@code {prefix}/{alias}} → {@code classicBase}/{classicRel}.
     * Returns null when remainder is missing, multi-segment, or unknown alias.
     */
    static String rewriteAliased(
            String path,
            String prefix,
            String zone,
            String classicBase,
            PanelApiActionAliases aliases
    ) {
        if (!matchesPrefix(path, prefix)) {
            return null;
        }
        if (path.equals(prefix)) {
            return null;
        }
        String remainder = path.substring(prefix.length() + 1);
        if (!StringUtils.hasText(remainder) || remainder.contains("/")) {
            return null;
        }
        String classicRel = aliases.resolveClassicRel(zone, remainder);
        if (!StringUtils.hasText(classicRel)) {
            return null;
        }
        return classicBase + "/" + classicRel;
    }

    static boolean isClassicPanelApi(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.equals("/api/v1/user")
                || lower.startsWith("/api/v1/user/")
                || lower.equals("/api/v1/passport")
                || lower.startsWith("/api/v1/passport/")
                || lower.equals("/api/v1/admin")
                || lower.startsWith("/api/v1/admin/");
    }

    /** Classic V2Board payment notify fingerprint — hard cutover. */
    static boolean isClassicGuestPayment(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.equals("/api/v1/guest/payment")
                || lower.startsWith("/api/v1/guest/payment/");
    }

    /**
     * {@code {prefix}/{method}/{uuid}} → classic notify path. Null if shape invalid.
     */
    static String rewritePaymentNotify(String path, String prefix) {
        if (!matchesPrefix(path, prefix) || path.equals(prefix)) {
            return null;
        }
        String remainder = path.substring(prefix.length() + 1);
        if (!StringUtils.hasText(remainder)) {
            return null;
        }
        String[] parts = remainder.split("/");
        if (parts.length != 2
                || !StringUtils.hasText(parts[0])
                || !StringUtils.hasText(parts[1])) {
            return null;
        }
        return "/api/v1/guest/payment/notify/" + parts[0] + "/" + parts[1];
    }

    private static boolean pathEquals(String path, String expected) {
        if (!StringUtils.hasText(path) || !StringUtils.hasText(expected)) {
            return false;
        }
        return path.equals(expected);
    }

    private static String servletPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (StringUtils.hasText(context) && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (!StringUtils.hasText(uri)) {
            return "/";
        }
        int q = uri.indexOf('?');
        return q >= 0 ? uri.substring(0, q) : uri;
    }

    private static final class RewrittenRequest extends HttpServletRequestWrapper {
        private final String path;

        RewrittenRequest(HttpServletRequest request, String path) {
            super(request);
            this.path = path;
        }

        @Override
        public String getRequestURI() {
            String context = getContextPath();
            return (context == null ? "" : context) + path;
        }

        @Override
        public String getServletPath() {
            return path;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer();
            String scheme = getScheme();
            int port = getServerPort();
            url.append(scheme).append("://").append(getServerName());
            if (("http".equals(scheme) && port != 80) || ("https".equals(scheme) && port != 443)) {
                url.append(':').append(port);
            }
            url.append(getRequestURI());
            return url;
        }
    }

    @Configuration
    static class ClientApiPathFilterConfig {
        @Bean
        ClientApiPathFilter clientApiPathFilter(ClientApiPathRegistry registry, PanelApiActionAliases actionAliases) {
            return new ClientApiPathFilter(registry, actionAliases);
        }

        @Bean
        FilterRegistrationBean<ClientApiPathFilter> clientApiPathFilterRegistration(ClientApiPathFilter filter) {
            FilterRegistrationBean<ClientApiPathFilter> reg = new FilterRegistrationBean<>();
            reg.setFilter(filter);
            reg.addUrlPatterns("/*");
            reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
            reg.setName("clientApiPathFilter");
            return reg;
        }
    }
}
