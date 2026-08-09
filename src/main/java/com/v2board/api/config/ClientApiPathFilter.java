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
 * to internal classic mappings. Marks panel SM4 zone for {@link PanelSm4Filter}.
 */
public class ClientApiPathFilter extends OncePerRequestFilter {

    private final ClientApiPathRegistry registry;

    public ClientApiPathFilter(ClientApiPathRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!registry.hasPrefixes()) {
            registry.refresh();
        }
        String path = servletPath(request);

        if (isClassicPanelApi(path)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String publicConfigPath = registry.getPublicConfigPath();
        if (StringUtils.hasText(publicConfigPath) && pathEquals(path, publicConfigPath)) {
            request.setAttribute(PanelSm4Support.ATTR_PANEL_SM4, Boolean.TRUE);
            filterChain.doFilter(request, response);
            return;
        }

        String rewritten = tryRewrite(path, registry.getUserPrefix(), "/api/v1/user");
        if (rewritten != null) {
            markEncryptedZone(request);
            filterChain.doFilter(new RewrittenRequest(request, rewritten), response);
            return;
        }

        rewritten = tryRewrite(path, registry.getPassportPrefix(), "/api/v1/passport");
        if (rewritten != null) {
            markEncryptedZone(request);
            filterChain.doFilter(new RewrittenRequest(request, rewritten), response);
            return;
        }

        rewritten = tryRewrite(path, registry.getAdminPrefix(), "/api/v1/admin");
        if (rewritten != null) {
            markEncryptedZone(request);
            filterChain.doFilter(new RewrittenRequest(request, rewritten), response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static void markEncryptedZone(HttpServletRequest request) {
        request.setAttribute(PanelSm4Support.ATTR_PANEL_SM4, Boolean.TRUE);
        request.setAttribute(PanelSm4Support.ATTR_REJECT_CLASSIC_AUTH, Boolean.TRUE);
    }

    /** Package-visible for unit tests. */
    static String tryRewrite(String path, String prefix, String classicBase) {
        if (!StringUtils.hasText(prefix) || !StringUtils.hasText(path)) {
            return null;
        }
        if (path.equals(prefix)) {
            return classicBase;
        }
        if (path.startsWith(prefix + "/")) {
            return classicBase + path.substring(prefix.length());
        }
        return null;
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
        ClientApiPathFilter clientApiPathFilter(ClientApiPathRegistry registry) {
            return new ClientApiPathFilter(registry);
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
