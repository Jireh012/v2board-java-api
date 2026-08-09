package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.config.ClientApiPathRegistry;
import com.v2board.api.config.NodeApiRouteRegistrar;
import com.v2board.api.config.PublicConfigRouteRegistrar;
import com.v2board.api.config.SubscribeRouteRegistrar;
import com.v2board.api.mapper.SystemConfigMapper;
import com.v2board.api.model.SystemConfig;
import com.v2board.api.util.V2boardPhpConfigLoader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 系统配置服务，对齐 PHP 版 ConfigController fetch/save。
 * 默认值来自 application.yml，可被数据库中的 v2_system_config 覆盖。
 */
@Service
public class ConfigService {

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    @Lazy
    private SubscribeRouteRegistrar subscribeRouteRegistrar;

    @Autowired(required = false)
    @Lazy
    private NodeApiRouteRegistrar nodeApiRouteRegistrar;

    @Autowired(required = false)
    @Lazy
    private ClientApiPathRegistry clientApiPathRegistry;

    @Autowired(required = false)
    @Lazy
    private PublicConfigRouteRegistrar publicConfigRouteRegistrar;

    @Value("${v2board.app-name:V2Board}")
    private String appName;
    @Value("${v2board.php-config-path:}")
    private String phpConfigPath;
    @Value("${v2board.app-url:}")
    private String appUrl;
    @Value("${v2board.subscribe-url:}")
    private String subscribeUrl;
    @Value("${v2board.subscribe-path:/api/v1/client/subscribe}")
    private String subscribePath;
    @Value("${v2board.show-subscribe-method:0}")
    private Integer showSubscribeMethod;
    @Value("${v2board.show-subscribe-expire:5}")
    private Integer showSubscribeExpire;
    @Value("${v2board.allow-new-period:0}")
    private Integer allowNewPeriod;
    @Value("${v2board.reset-traffic-method:0}")
    private Integer resetTrafficMethod;
    @Value("${v2board.show-info-to-server-enable:false}")
    private Boolean showInfoToServerEnable;
    @Value("${v2board.invite-commission:10}")
    private Integer inviteCommission;
    @Value("${v2board.invite-gen-limit:5}")
    private Integer inviteGenLimit;
    @Value("${v2board.ticket-status:0}")
    private Integer ticketStatus;
    @Value("${v2board.withdraw-close-enable:0}")
    private Integer withdrawCloseEnable;
    @Value("${v2board.commission-withdraw-limit:100}")
    private Integer commissionWithdrawLimit;
    @Value("${v2board.commission-distribution-enable:0}")
    private Integer commissionDistributionEnable;
    @Value("${v2board.commission-distribution-l1:100}")
    private Double commissionDistributionL1;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /**
     * Fixed unauthenticated bootstrap path for public site config (SM4 envelope).
     * Not configurable; frontend always GETs this path.
     */
    public static final String FIXED_PUBLIC_CONFIG_PATH = "/config";

    /** UI route segments that must not be used as custom admin path. */
    private static final Set<String> SECURE_PATH_RESERVED = Set.of(
            "login", "register", "forget", "dashboard", "plan", "order", "server",
            "invite", "ticket", "traffic", "knowledge", "profile", "api", "config"
    );

    /** HTTP prefixes that must not collide with a custom subscribe path. */
    private static final List<String> SUBSCRIBE_PATH_RESERVED_PREFIXES = List.of(
            "/api/v1/user",
            "/api/v1/admin",
            "/api/v1/passport",
            "/api/v1/guest",
            "/api/v1/server",
            FIXED_PUBLIC_CONFIG_PATH
    );

    private static final int SUBSCRIBE_PATH_MAX_LEN = 128;
    private static final int SERVER_API_PREFIX_MAX_LEN = 64;
    private static final int SERVER_API_PREFIX_RANDOM_LEN = 12;
    private static final String SERVER_API_PREFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom SERVER_API_PREFIX_RANDOM = new SecureRandom();

    /** HTTP prefixes that must not collide with a custom node API prefix. */
    private static final List<String> SERVER_API_PREFIX_RESERVED = List.of(
            "/api/v1",
            "/api/v2",
            "/api/v1/server",
            "/api/v2/server",
            FIXED_PUBLIC_CONFIG_PATH
    );

    /** Shared reserved bases for client/node path prefixes. */
    private static final List<String> CLIENT_API_PREFIX_RESERVED = List.of(
            "/api/v1",
            "/api/v2",
            "/api/v1/user",
            "/api/v1/admin",
            "/api/v1/passport",
            "/api/v1/guest",
            "/api/v1/server",
            "/api/v2/server",
            FIXED_PUBLIC_CONFIG_PATH
    );

    /**
     * 获取完整配置或按 key 返回某一分组。与 PHP GET /config/fetch 一致。
     * Empty {@code server.server_api_prefix} is auto-generated and persisted.
     */
    public Map<String, Object> fetch(String key) throws Exception {
        Map<String, Object> full = getFullConfig();
        boolean changed = ensureServerApiPrefixInPlace(full);
        changed = ensureClientApiPathsInPlace(full) || changed;
        if (changed) {
            persistFullConfig(full);
            if (nodeApiRouteRegistrar != null) {
                nodeApiRouteRegistrar.refresh();
            }
            refreshClientApiRoutes();
        }
        if (StringUtils.hasText(key) && full.containsKey(key)) {
            return Map.of(key, full.get(key));
        }
        return full;
    }

    /**
     * 返回完整配置（含默认值与数据库覆盖）。
     */
    public Map<String, Object> getFullConfig() throws Exception {
        Map<String, Object> defaults = buildDefaults();
        mergePhpFlatConfig(defaults, V2boardPhpConfigLoader.load(phpConfigPath));
        SystemConfig row = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getName, SystemConfig.NAME_V2BOARD));
        if (row != null && StringUtils.hasText(row.getValue())) {
            Map<String, Object> stored = objectMapper.readValue(row.getValue(), MAP_TYPE);
            deepMerge(defaults, stored);
        }
        return defaults;
    }

    /** 对齐 PHP config('v2board.app_name')，供订阅模板 $app_name 替换。 */
    public String getAppName() {
        try {
            Map<String, Object> full = getFullConfig();
            if (full.get("site") instanceof Map<?, ?> site) {
                Object name = site.get("app_name");
                if (name != null && StringUtils.hasText(String.valueOf(name))) {
                    return String.valueOf(name).trim();
                }
            }
        } catch (Exception ignored) {
        }
        return StringUtils.hasText(appName) ? appName.trim() : "V2Board";
    }

    public String getAppUrl() {
        try {
            Map<String, Object> full = getFullConfig();
            if (full.get("site") instanceof Map<?, ?> site && site.get("app_url") != null) {
                return String.valueOf(site.get("app_url")).trim();
            }
        } catch (Exception ignored) {
        }
        return appUrl != null ? appUrl : "";
    }

    /**
     * 站点订阅 URL 基址（可逗号分隔多个）。
     * 优先 DB site.subscribe_url → site.app_url → yml；
     * 全部为空时回退到当前 HTTP 请求的 origin（localhost / 局域网 IP / 反代 Host 均可）。
     */
    public String getSubscribeUrlBase() {
        String configured = getConfiguredSubscribeUrlBase();
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return resolveCurrentRequestOrigin();
    }

    /** 仅配置层基址，不含当前请求回退。 */
    public String getConfiguredSubscribeUrlBase() {
        try {
            Map<String, Object> full = getFullConfig();
            if (full.get("site") instanceof Map<?, ?> site) {
                String fromDb = str(site.get("subscribe_url"));
                if (StringUtils.hasText(fromDb)) {
                    return normalizeSubscribeBases(fromDb);
                }
                String fromApp = str(site.get("app_url"));
                if (StringUtils.hasText(fromApp)) {
                    return normalizeSubscribeBases(fromApp);
                }
            }
        } catch (Exception ignored) {
        }
        if (StringUtils.hasText(subscribeUrl)) {
            return normalizeSubscribeBases(subscribeUrl.trim());
        }
        if (StringUtils.hasText(appUrl)) {
            return normalizeSubscribeBases(appUrl.trim());
        }
        return "";
    }

    /**
     * 从当前请求解析对外可访问的 origin，例如 http://192.168.1.10:8080。
     * 识别 X-Forwarded-Proto / X-Forwarded-Host / Host；开启 force_https 时强制 https。
     */
    public String resolveCurrentRequestOrigin() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null || attrs.getRequest() == null) {
                return "";
            }
            return buildOriginFromRequest(attrs.getRequest());
        } catch (Exception ignored) {
            return "";
        }
    }

    private String buildOriginFromRequest(HttpServletRequest request) {
        String scheme = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));
        if (!StringUtils.hasText(scheme)) {
            scheme = request.getScheme();
        }
        if (isForceHttps()) {
            scheme = "https";
        }
        if (!StringUtils.hasText(scheme)) {
            scheme = "http";
        }
        scheme = scheme.toLowerCase();

        String host = firstForwardedValue(request.getHeader("X-Forwarded-Host"));
        if (!StringUtils.hasText(host)) {
            host = request.getHeader("Host");
        }
        if (StringUtils.hasText(host)) {
            return normalizeSubscribeBases(scheme + "://" + host.trim());
        }

        String serverName = request.getServerName();
        if (!StringUtils.hasText(serverName)) {
            return "";
        }
        int port = request.getServerPort();
        String forwardedPort = firstForwardedValue(request.getHeader("X-Forwarded-Port"));
        if (StringUtils.hasText(forwardedPort)) {
            try {
                port = Integer.parseInt(forwardedPort.trim());
            } catch (NumberFormatException ignored) {
                // keep serverPort
            }
        }

        StringBuilder origin = new StringBuilder();
        origin.append(scheme).append("://").append(serverName);
        boolean defaultHttp = "http".equals(scheme) && port == 80;
        boolean defaultHttps = "https".equals(scheme) && port == 443;
        if (port > 0 && !defaultHttp && !defaultHttps) {
            origin.append(':').append(port);
        }
        return origin.toString();
    }

    private boolean isForceHttps() {
        Integer v = intFromGroup("site", "force_https");
        return v != null && v == 1;
    }

    private static String firstForwardedValue(String header) {
        if (!StringUtils.hasText(header)) {
            return "";
        }
        String first = header.split(",")[0].trim();
        return first;
    }

    /** 订阅路径，优先 DB site.subscribe_path。 */
    public String getSubscribePath() {
        try {
            Map<String, Object> full = getFullConfig();
            if (full.get("site") instanceof Map<?, ?> site) {
                String path = str(site.get("subscribe_path"));
                if (StringUtils.hasText(path)) {
                    return path.startsWith("/") ? path : "/" + path;
                }
            }
        } catch (Exception ignored) {
        }
        if (StringUtils.hasText(subscribePath)) {
            return subscribePath.startsWith("/") ? subscribePath : "/" + subscribePath;
        }
        return "/api/v1/client/subscribe";
    }

    /**
     * Node API path prefix ({@code server.server_api_prefix}). Empty when unset (caller may ensure).
     */
    public String getServerApiPrefix() {
        try {
            Map<String, Object> full = getFullConfig();
            if (full.get("server") instanceof Map<?, ?> server) {
                String path = str(server.get("server_api_prefix"));
                if (StringUtils.hasText(path)) {
                    return normalizeServerApiPrefix(path);
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Ensure {@code server.server_api_prefix} is non-empty (auto-gen + persist). Returns the active prefix.
     */
    public String ensureServerApiPrefix() throws Exception {
        Map<String, Object> full = getFullConfig();
        if (ensureServerApiPrefixInPlace(full)) {
            persistFullConfig(full);
        }
        return getServerApiPrefixFromMap(full);
    }

    /** {@code site.passport_api_prefix}; empty when unset. */
    public String getPassportApiPrefix() {
        return normalizeServerApiPrefix(getStringFromGroup("site", "passport_api_prefix"));
    }

    /** {@code site.user_api_prefix}; empty when unset. */
    public String getUserApiPrefix() {
        return normalizeServerApiPrefix(getStringFromGroup("site", "user_api_prefix"));
    }

    /** {@code site.admin_api_prefix}; empty when unset. */
    public String getAdminApiPrefix() {
        return normalizeServerApiPrefix(getStringFromGroup("site", "admin_api_prefix"));
    }

    /** {@code site.payment_notify_prefix}; empty when unset. */
    public String getPaymentNotifyPrefix() {
        return normalizeServerApiPrefix(getStringFromGroup("site", "payment_notify_prefix"));
    }

    /** Fixed public config bootstrap path {@link #FIXED_PUBLIC_CONFIG_PATH}. */
    public String getPublicConfigPath() {
        return FIXED_PUBLIC_CONFIG_PATH;
    }

    /**
     * Ensure client path prefixes exist (auto-gen + persist). Does not refresh filters/routes —
     * callers that need hot reload should invoke {@link #refreshClientApiRoutes()} separately.
     */
    public Map<String, String> ensureClientApiPaths() throws Exception {
        Map<String, Object> full = getFullConfig();
        if (ensureClientApiPathsInPlace(full)) {
            persistFullConfig(full);
        }
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("passport_api_prefix", getSitePathFromMap(full, "passport_api_prefix"));
        paths.put("user_api_prefix", getSitePathFromMap(full, "user_api_prefix"));
        paths.put("admin_api_prefix", getSitePathFromMap(full, "admin_api_prefix"));
        paths.put("payment_notify_prefix", getSitePathFromMap(full, "payment_notify_prefix"));
        paths.put("public_config_path", FIXED_PUBLIC_CONFIG_PATH);
        return paths;
    }

    public String ensurePaymentNotifyPrefix() throws Exception {
        return ensureClientApiPaths().get("payment_notify_prefix");
    }

    /** Relative notify path {@code {payment_notify_prefix}/{method}/{uuid}}. */
    public String buildPaymentNotifyPath(String method, String uuid) throws Exception {
        String prefix = ensurePaymentNotifyPrefix();
        return prefix + "/" + method + "/" + uuid;
    }

    public String ensurePassportApiPrefix() throws Exception {
        return ensureClientApiPaths().get("passport_api_prefix");
    }

    public String ensureUserApiPrefix() throws Exception {
        return ensureClientApiPaths().get("user_api_prefix");
    }

    public String ensureAdminApiPrefix() throws Exception {
        return ensureClientApiPaths().get("admin_api_prefix");
    }

    public String ensurePublicConfigPath() {
        return FIXED_PUBLIC_CONFIG_PATH;
    }

    void refreshClientApiRoutes() {
        if (clientApiPathRegistry != null) {
            clientApiPathRegistry.refresh();
        }
        if (publicConfigRouteRegistrar != null) {
            publicConfigRouteRegistrar.refresh();
        }
    }

    /**
     * 是否在订阅输出中注入信息节点。DB {@code subscribe.show_info_to_server_enable} 优先，1/true 为开。
     */
    public boolean getShowInfoToServerEnable() {
        Integer v = intFromGroup("subscribe", "show_info_to_server_enable");
        if (v != null) {
            return v == 1;
        }
        return Boolean.TRUE.equals(showInfoToServerEnable);
    }

    /**
     * 订阅信息节点展示方式：0=流量+重置天+到期，1=仅到期，2=仅流量。不参与 subscribe URL 生成。
     */
    public int getShowSubscribeMethod() {
        Integer v = intFromGroup("subscribe", "show_subscribe_method");
        if (v != null) {
            return v;
        }
        return showSubscribeMethod != null ? showSubscribeMethod : 0;
    }

    /**
     * 用户端「即将到期」徽章提前天数。不参与 TOTP / subscribe URL。
     */
    public int getShowSubscribeExpire() {
        Integer v = intFromGroup("subscribe", "show_subscribe_expire");
        if (v != null) {
            return v;
        }
        return showSubscribeExpire != null ? showSubscribeExpire : 5;
    }

    public int getAllowNewPeriod() {
        Integer v = intFromGroup("subscribe", "allow_new_period");
        if (v != null) {
            return v;
        }
        return allowNewPeriod != null ? allowNewPeriod : 0;
    }

    /**
     * 工单开单策略：0 全部可用；1 仅有已完成/折抵订单用户；2 禁止开单。
     * 读 DB {@code ticket.ticket_status}，缺省回落 yml {@code v2board.ticket-status}。
     */
    public int getTicketStatus() {
        Integer v = intFromGroup("ticket", "ticket_status");
        if (v != null) {
            return v;
        }
        return ticketStatus != null ? ticketStatus : 0;
    }

    /** 1 = users may change to a different plan while current sub is active. */
    public int getPlanChangeEnable() {
        Integer v = intFromGroup("subscribe", "plan_change_enable");
        return v != null ? v : 1;
    }

    /** 1 = apply surplus credit when changing plan. */
    public int getSurplusEnable() {
        Integer v = intFromGroup("subscribe", "surplus_enable");
        return v != null ? v : 1;
    }

    /** PHP openEvent for type=1 new purchase; only 1 clears used traffic (u/d). */
    public int getNewOrderEventId() {
        Integer v = intFromGroup("subscribe", "new_order_event_id");
        return v != null ? v : 0;
    }

    /** PHP openEvent for type=2 renew; only 1 clears used traffic (u/d). */
    public int getRenewOrderEventId() {
        Integer v = intFromGroup("subscribe", "renew_order_event_id");
        return v != null ? v : 0;
    }

    /** PHP openEvent for type=3 plan change; only 1 clears used traffic (u/d). */
    public int getChangeOrderEventId() {
        Integer v = intFromGroup("subscribe", "change_order_event_id");
        return v != null ? v : 0;
    }

    /**
     * Global default traffic reset method when plan.reset_traffic_method is null.
     * 0 month-1, 1 expire-day, 2 none, 3 year-1, 4 year-expire-day.
     */
    public int getResetTrafficMethod() {
        Integer v = intFromGroup("subscribe", "reset_traffic_method");
        if (v != null) {
            return v;
        }
        return resetTrafficMethod != null ? resetTrafficMethod : 0;
    }

    /** 1 = registration closed (admin site.stop_register). */
    public int getStopRegister() {
        Integer v = intFromGroup("site", "stop_register");
        return v != null ? v : 0;
    }

    /** 1 = invite code required on register. */
    public int getInviteForce() {
        Integer v = intFromGroup("invite", "invite_force");
        return v != null ? v : 0;
    }

    /** Max unused invite codes a user may generate. DB invite.invite_gen_limit → yml. */
    public int getInviteGenLimit() {
        Integer v = intFromGroup("invite", "invite_gen_limit");
        if (v != null) {
            return v;
        }
        return inviteGenLimit != null ? inviteGenLimit : 5;
    }

    /** Default commission rate percent. DB invite.invite_commission → yml. */
    public int getInviteCommission() {
        Integer v = intFromGroup("invite", "invite_commission");
        if (v != null) {
            return v;
        }
        return inviteCommission != null ? inviteCommission : 10;
    }

    /** 1 = only first paid order yields commission (system default type). */
    public int getCommissionFirstTimeEnable() {
        Integer v = intFromGroup("invite", "commission_first_time_enable");
        return v != null ? v : 1;
    }

    /** 1 = schedule auto-approves commission_status 0→1 after 3 days. */
    public int getCommissionAutoCheckEnable() {
        Integer v = intFromGroup("invite", "commission_auto_check_enable");
        return v != null ? v : 1;
    }

    /** Minimum withdrawable commission balance in cents. */
    public int getCommissionWithdrawLimit() {
        Integer v = intFromGroup("invite", "commission_withdraw_limit");
        if (v != null) {
            return v;
        }
        return commissionWithdrawLimit != null ? commissionWithdrawLimit : 100;
    }

    /** Comma-separated withdraw methods (e.g. alipay,wechat). */
    public String getCommissionWithdrawMethod() {
        String v = getStringFromGroup("invite", "commission_withdraw_method");
        return StringUtils.hasText(v) ? v : "alipay,wechat";
    }

    /** 1 = withdraw tickets disabled; commission credited to balance on payout. */
    public int getWithdrawCloseEnable() {
        Integer v = intFromGroup("invite", "withdraw_close_enable");
        if (v != null) {
            return v;
        }
        return withdrawCloseEnable != null ? withdrawCloseEnable : 0;
    }

    /** 1 = three-level distribution enabled. */
    public int getCommissionDistributionEnable() {
        Integer v = intFromGroup("invite", "commission_distribution_enable");
        if (v != null) {
            return v;
        }
        return commissionDistributionEnable != null ? commissionDistributionEnable : 0;
    }

    /** L1 share percent when distribution enabled. */
    public int getCommissionDistributionL1() {
        Integer v = intFromGroup("invite", "commission_distribution_l1");
        if (v != null) {
            return v;
        }
        return commissionDistributionL1 != null ? commissionDistributionL1.intValue() : 100;
    }

    /** L2 share percent when distribution enabled. */
    public int getCommissionDistributionL2() {
        Integer v = intFromGroup("invite", "commission_distribution_l2");
        return v != null ? v : 0;
    }

    /** L3 share percent when distribution enabled. */
    public int getCommissionDistributionL3() {
        Integer v = intFromGroup("invite", "commission_distribution_l3");
        return v != null ? v : 0;
    }

    /** 1 = email verification required on register. */
    public int getEmailVerify() {
        Integer v = intFromGroup("safe", "email_verify");
        return v != null ? v : 0;
    }

    /** 1 = user UI requires login except login/register/forget. */
    public int getSafeModeEnable() {
        Integer v = intFromGroup("safe", "safe_mode_enable");
        return v != null ? v : 0;
    }

    /**
     * Admin UI path segment (no slashes). Empty/invalid stored value → {@code admin}.
     */
    public String getSecurePath() {
        String raw = getStringFromGroup("safe", "secure_path");
        if (!StringUtils.hasText(raw)) {
            return "admin";
        }
        return isValidSecurePath(raw) ? raw : "admin";
    }

    /** 1 = reCAPTCHA required on user login/register. */
    public int getRecaptchaEnable() {
        Integer v = intFromGroup("safe", "recaptcha_enable");
        return v != null ? v : 0;
    }

    /** Public site key for reCAPTCHA v2 widget (never secret). */
    public String getRecaptchaSiteKey() {
        return getStringFromGroup("safe", "recaptcha_site_key");
    }

    /** User UI sidebar style: {@code light} or {@code dark}. */
    public String getFrontendThemeSidebar() {
        String v = getStringFromGroup("frontend", "frontend_theme_sidebar");
        return StringUtils.hasText(v) ? v : "light";
    }

    /** User UI header style: {@code light} or {@code dark}. */
    public String getFrontendThemeHeader() {
        String v = getStringFromGroup("frontend", "frontend_theme_header");
        return StringUtils.hasText(v) ? v : "dark";
    }

    /** User UI theme color key: default / darkblue / black / green. */
    public String getFrontendThemeColor() {
        String v = getStringFromGroup("frontend", "frontend_theme_color");
        return StringUtils.hasText(v) ? v : "default";
    }

    /** Optional user UI background image URL (empty allowed). */
    public String getFrontendBackgroundUrl() {
        return getStringFromGroup("frontend", "frontend_background_url");
    }

    /** Telegram 群组讨论链接（公开配置可下发；空字符串允许）。 */
    public String getTelegramDiscussLink() {
        return getStringFromGroup("telegram", "telegram_discuss_link");
    }

    /**
     * Telegram Bot 开关：0/1。兼容 Number / Boolean / String（与 {@link #intFromGroup} 一致）。
     */
    public int getTelegramBotEnable() {
        Integer v = intFromGroup("telegram", "telegram_bot_enable");
        return v != null ? v : 0;
    }

    /** Server-only Google reCAPTCHA secret. */
    public String getRecaptchaSecret() {
        return getStringFromGroup("safe", "recaptcha_key");
    }

    /** 1 = lock user login after too many wrong passwords (by email). */
    public int getPasswordLimitEnable() {
        Integer v = intFromGroup("safe", "password_limit_enable");
        return v != null ? v : 1;
    }

    public int getPasswordLimitCount() {
        Integer v = intFromGroup("safe", "password_limit_count");
        return v != null && v > 0 ? v : 5;
    }

    public int getPasswordLimitExpireMinutes() {
        Integer v = intFromGroup("safe", "password_limit_expire");
        return v != null && v > 0 ? v : 60;
    }

    static boolean isValidSecurePath(String path) {
        if (path == null || !path.matches("^[A-Za-z0-9]{8,}$")) {
            return false;
        }
        return !SECURE_PATH_RESERVED.contains(path.toLowerCase(Locale.ROOT));
    }

    /**
     * Normalize subscribe path for save: trim, ensure leading {@code /}, strip trailing {@code /}
     * (except root). Empty/blank → empty string (runtime falls back to default).
     */
    static String normalizeSubscribePathInput(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "";
        }
        if (!t.startsWith("/")) {
            t = "/" + t;
        }
        while (t.length() > 1 && t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    /**
     * Validate a normalized non-empty subscribe path (charset, length, {@code ..}, reserved prefixes).
     * {@code securePathSegment} is the current admin UI path (no leading slash); may be null/blank.
     */
    static boolean isValidSubscribePath(String normalized, String securePathSegment) {
        if (normalized == null || normalized.isEmpty()) {
            return true;
        }
        if ("/".equals(normalized)) {
            return false;
        }
        if (normalized.length() > SUBSCRIBE_PATH_MAX_LEN) {
            return false;
        }
        if (normalized.contains("..")) {
            return false;
        }
        if (!normalized.matches("^/[A-Za-z0-9._~/-]+$")) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String prefix : SUBSCRIBE_PATH_RESERVED_PREFIXES) {
            if (lower.equals(prefix) || lower.startsWith(prefix + "/")) {
                return false;
            }
        }
        if (StringUtils.hasText(securePathSegment)) {
            String seg = securePathSegment.trim().toLowerCase(Locale.ROOT);
            if (!seg.isEmpty()) {
                String securePrefix = "/" + seg;
                if (lower.equals(securePrefix) || lower.startsWith(securePrefix + "/")) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Read a string from a nested config group (e.g. email.email_host).
     * Empty / missing → empty string (never null).
     */
    public String getStringFromGroup(String group, String key) {
        try {
            Map<String, Object> full = getFullConfig();
            if (full.get(group) instanceof Map<?, ?> map && map.get(key) != null) {
                return String.valueOf(map.get(key)).trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * 按当前系统配置生成用户订阅完整链接（DB 动态配置优先于 yml）。
     * 始终使用直连 token（subMethod=0）；展示方式 / 提前天数不参与 URL 形态。
     */
    public String buildSubscribeUrl(String token, Long userId) {
        return com.v2board.api.util.Helper.getSubscribeUrl(
                token,
                userId,
                0,
                getSubscribePath(),
                getSubscribeUrlBase(),
                null
        );
    }

    private Integer intFromGroup(String group, String key) {
        try {
            Map<String, Object> full = getFullConfig();
            if (full.get(group) instanceof Map<?, ?> map && map.get(key) != null) {
                Object raw = map.get(key);
                if (raw instanceof Number n) {
                    return n.intValue();
                }
                if (raw instanceof Boolean b) {
                    return b ? 1 : 0;
                }
                String s = String.valueOf(raw).trim();
                if (s.isEmpty()) {
                    return null;
                }
                return Integer.parseInt(s);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 去掉各基址尾部斜杠，避免与 path 拼接成 // */
    private static String normalizeSubscribeBases(String bases) {
        if (!StringUtils.hasText(bases)) {
            return "";
        }
        // Accept comma and/or newline separated bases from admin UI.
        String[] parts = bases.split("[,\\n\\r]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String t = part.trim();
            while (t.endsWith("/")) {
                t = t.substring(0, t.length() - 1);
            }
            if (t.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t);
        }
        return sb.toString();
    }

    /**
     * 保存配置。请求体为与 fetch 相同的嵌套结构，会与现有配置合并后写入。
     */
    public void save(Map<String, Object> body) throws Exception {
        validateSecurePathInSaveBody(body);
        validateSubscribePathInSaveBody(body);
        validateServerInSaveBody(body);
        validateClientApiPathsInSaveBody(body);
        Map<String, Object> current = getFullConfig();
        deepMerge(current, body);
        // Always ensure node API prefix after merge (empty → auto-gen).
        boolean prefixCreated = ensureServerApiPrefixInPlace(current);
        boolean clientPathsChanged = ensureClientApiPathsInPlace(current);
        persistFullConfig(current);
        // Hot-reload subscribe HTTP route when site.subscribe_path changes (no restart).
        if (subscribeRouteRegistrar != null && body != null && body.containsKey("site")) {
            subscribeRouteRegistrar.refresh();
        }
        if (nodeApiRouteRegistrar != null
                && (prefixCreated || (body != null && body.containsKey("server")))) {
            nodeApiRouteRegistrar.refresh();
        }
        if (clientPathsChanged || (body != null && body.containsKey("site"))) {
            refreshClientApiRoutes();
        }
    }

    private void persistFullConfig(Map<String, Object> full) throws Exception {
        String json = objectMapper.writeValueAsString(full);
        long now = System.currentTimeMillis();
        SystemConfig row = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getName, SystemConfig.NAME_V2BOARD));
        if (row == null) {
            row = new SystemConfig();
            row.setName(SystemConfig.NAME_V2BOARD);
            row.setValue(json);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            systemConfigMapper.insert(row);
        } else {
            row.setValue(json);
            row.setUpdatedAt(now);
            systemConfigMapper.updateById(row);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateSecurePathInSaveBody(Map<String, Object> body) {
        if (body == null || !(body.get("safe") instanceof Map<?, ?> safe)) {
            return;
        }
        if (!safe.containsKey("secure_path")) {
            return;
        }
        Object raw = safe.get("secure_path");
        String path = raw == null ? "" : String.valueOf(raw).trim();
        if (!StringUtils.hasText(path)) {
            return;
        }
        if (!isValidSecurePath(path)) {
            throw new BusinessException(500, "后台路径不合法：至少8位字母或数字，且不能为保留路径");
        }
    }

    /**
     * Validate and normalize {@code site.subscribe_path} in save body when the key is present.
     * Empty/blank is allowed (runtime default). Illegal values → {@link BusinessException}.
     */
    @SuppressWarnings("unchecked")
    private void validateSubscribePathInSaveBody(Map<String, Object> body) {
        if (body == null || !(body.get("site") instanceof Map<?, ?> siteRaw)) {
            return;
        }
        if (!siteRaw.containsKey("subscribe_path")) {
            return;
        }
        Map<String, Object> site;
        if (siteRaw instanceof HashMap || siteRaw instanceof LinkedHashMap) {
            site = (Map<String, Object>) siteRaw;
        } else {
            site = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : siteRaw.entrySet()) {
                site.put(String.valueOf(e.getKey()), e.getValue());
            }
            body.put("site", site);
        }
        Object raw = site.get("subscribe_path");
        String normalized = normalizeSubscribePathInput(raw == null ? "" : String.valueOf(raw));
        if (!StringUtils.hasText(normalized)) {
            site.put("subscribe_path", "");
            return;
        }
        String secureSegment = resolveSecurePathSegmentForSubscribeValidation(body);
        if (!isValidSubscribePath(normalized, secureSegment)) {
            throw new BusinessException(500,
                    "订阅路径不合法：须以 / 开头，仅含字母数字与 ._~/ -，长度≤128，且不能与保留 API 前缀或后台路径冲突");
        }
        site.put("subscribe_path", normalized);
    }

    /** Prefer secure_path from the same save body when present and valid; else current config. */
    private String resolveSecurePathSegmentForSubscribeValidation(Map<String, Object> body) {
        if (body.get("safe") instanceof Map<?, ?> safe && safe.containsKey("secure_path")) {
            Object raw = safe.get("secure_path");
            String candidate = raw == null ? "" : String.valueOf(raw).trim();
            if (StringUtils.hasText(candidate) && isValidSecurePath(candidate)) {
                return candidate;
            }
        }
        return getSecurePath();
    }

    /**
     * Validate {@code server.*} fields when present in save body.
     * Token must be ≥16 after trim (normalized in-place); pull/push intervals ≥1;
     * min-traffic ≥0; device_limit_mode ∈ {0,1}.
     */
    @SuppressWarnings("unchecked")
    private static void validateServerInSaveBody(Map<String, Object> body) {
        if (body == null || !(body.get("server") instanceof Map<?, ?> serverRaw)) {
            return;
        }
        Map<String, Object> server;
        if (serverRaw instanceof HashMap || serverRaw instanceof LinkedHashMap) {
            server = (Map<String, Object>) serverRaw;
        } else {
            server = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : serverRaw.entrySet()) {
                server.put(String.valueOf(e.getKey()), e.getValue());
            }
            body.put("server", server);
        }

        if (server.containsKey("server_token")) {
            Object raw = server.get("server_token");
            String token = raw == null ? "" : String.valueOf(raw).trim();
            if (token.length() < 16) {
                throw new BusinessException(500, "通讯密钥至少 16 位");
            }
            server.put("server_token", token);
        }

        requireIntAtLeast(server, "server_pull_interval", 1, "节点拉取间隔至少为 1");
        requireIntAtLeast(server, "server_push_interval", 1, "节点推送间隔至少为 1");
        requireIntAtLeast(server, "server_node_report_min_traffic", 0, "最低上报流量不能为负");
        requireIntAtLeast(server, "server_device_online_min_traffic", 0, "在线判定最低流量不能为负");

        if (server.containsKey("device_limit_mode")) {
            Integer mode = parseIntOrNull(server.get("device_limit_mode"));
            if (mode == null || (mode != 0 && mode != 1)) {
                throw new BusinessException(500, "设备限制模式只能为 0 或 1");
            }
        }

        if (server.containsKey("server_api_prefix")) {
            Object raw = server.get("server_api_prefix");
            String normalized = normalizeServerApiPrefix(raw == null ? "" : String.valueOf(raw));
            if (!StringUtils.hasText(normalized)) {
                // Empty → auto-gen after merge via ensureServerApiPrefixInPlace.
                server.put("server_api_prefix", "");
            } else if (!isValidServerApiPrefix(normalized)) {
                throw new BusinessException(500,
                        "节点 API 前缀不合法：须以 / 开头，仅含字母数字与 ._~/ -，长度≤64，且不能与保留 API 前缀冲突");
            } else {
                server.put("server_api_prefix", normalized);
            }
        }
    }

    /**
     * If {@code server.server_api_prefix} is blank, generate {@code /n/}+12 alnum and write into map.
     *
     * @return true if a new prefix was generated
     */
    @SuppressWarnings("unchecked")
    static boolean ensureServerApiPrefixInPlace(Map<String, Object> full) {
        if (full == null) {
            return false;
        }
        Object serverObj = full.get("server");
        Map<String, Object> server;
        if (serverObj instanceof HashMap || serverObj instanceof LinkedHashMap) {
            server = (Map<String, Object>) serverObj;
        } else if (serverObj instanceof Map<?, ?> raw) {
            server = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                server.put(String.valueOf(e.getKey()), e.getValue());
            }
            full.put("server", server);
        } else {
            server = new LinkedHashMap<>();
            full.put("server", server);
        }
        String current = normalizeServerApiPrefix(str(server.get("server_api_prefix")));
        if (StringUtils.hasText(current)) {
            server.put("server_api_prefix", current);
            return false;
        }
        String generated = generateServerApiPrefix();
        server.put("server_api_prefix", generated);
        return true;
    }

    static String getServerApiPrefixFromMap(Map<String, Object> full) {
        if (full != null && full.get("server") instanceof Map<?, ?> server) {
            return normalizeServerApiPrefix(str(server.get("server_api_prefix")));
        }
        return "";
    }

    /** Normalize: trim, ensure leading {@code /}, strip trailing {@code /}. */
    public static String normalizeServerApiPrefix(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "";
        }
        if (!t.startsWith("/")) {
            t = "/" + t;
        }
        while (t.length() > 1 && t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    static boolean isValidServerApiPrefix(String normalized) {
        if (normalized == null || normalized.isEmpty() || "/".equals(normalized)) {
            return false;
        }
        if (normalized.length() > SERVER_API_PREFIX_MAX_LEN) {
            return false;
        }
        if (normalized.contains("..")) {
            return false;
        }
        if (!normalized.matches("^/[A-Za-z0-9._~/-]+$")) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String prefix : SERVER_API_PREFIX_RESERVED) {
            if (lower.equals(prefix) || lower.startsWith(prefix + "/") || prefix.startsWith(lower + "/")) {
                return false;
            }
        }
        return true;
    }

    /** Auto-gen {@code /n/} + 12 lowercase alphanumeric chars. */
    static String generateServerApiPrefix() {
        return generatePrefixedPath("/n/", SERVER_API_PREFIX_RANDOM_LEN);
    }

    static String generatePassportApiPrefix() {
        return generatePrefixedPath("/p/", SERVER_API_PREFIX_RANDOM_LEN);
    }

    static String generateUserApiPrefix() {
        return generatePrefixedPath("/u/", SERVER_API_PREFIX_RANDOM_LEN);
    }

    static String generateAdminApiPrefix() {
        return generatePrefixedPath("/a/", SERVER_API_PREFIX_RANDOM_LEN);
    }

    static String generatePaymentNotifyPrefix() {
        return generatePrefixedPath("/g/", SERVER_API_PREFIX_RANDOM_LEN);
    }

    private static String generatePrefixedPath(String prefix, int randomLen) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < randomLen; i++) {
            int idx = SERVER_API_PREFIX_RANDOM.nextInt(SERVER_API_PREFIX_ALPHABET.length());
            sb.append(SERVER_API_PREFIX_ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    /**
     * Validate {@code site.passport_api_prefix} / {@code user_api_prefix} / {@code admin_api_prefix}
     * when present in save body. Empty → auto-gen after merge. Ignores legacy {@code public_config_path}.
     */
    @SuppressWarnings("unchecked")
    private void validateClientApiPathsInSaveBody(Map<String, Object> body) {
        if (body == null || !(body.get("site") instanceof Map<?, ?> siteRaw)) {
            return;
        }
        Map<String, Object> site = mutableSiteMap(body, siteRaw);
        site.remove("public_config_path");
        normalizeClientPathKeyInPlace(site, "passport_api_prefix", "用户 Passport API 前缀");
        normalizeClientPathKeyInPlace(site, "user_api_prefix", "用户 API 前缀");
        normalizeClientPathKeyInPlace(site, "admin_api_prefix", "管理 API 前缀");
        normalizeClientPathKeyInPlace(site, "payment_notify_prefix", "支付回调前缀");

        String passport = normalizeServerApiPrefix(str(site.get("passport_api_prefix")));
        String user = normalizeServerApiPrefix(str(site.get("user_api_prefix")));
        String admin = normalizeServerApiPrefix(str(site.get("admin_api_prefix")));
        String paymentNotify = normalizeServerApiPrefix(str(site.get("payment_notify_prefix")));
        assertClientPathsNoMutualConflict(passport, user, admin, paymentNotify);

        String subscribe = "";
        if (site.containsKey("subscribe_path")) {
            subscribe = normalizeSubscribePathInput(str(site.get("subscribe_path")));
        } else {
            subscribe = normalizeSubscribePathInput(getSubscribePath());
        }
        String serverPrefix = "";
        if (body.get("server") instanceof Map<?, ?> server && server.containsKey("server_api_prefix")) {
            serverPrefix = normalizeServerApiPrefix(str(server.get("server_api_prefix")));
        } else {
            serverPrefix = getServerApiPrefix();
        }
        assertNoConflictWithExisting(passport, "Passport API 前缀", subscribe, serverPrefix);
        assertNoConflictWithExisting(user, "用户 API 前缀", subscribe, serverPrefix);
        assertNoConflictWithExisting(admin, "管理 API 前缀", subscribe, serverPrefix);
        assertNoConflictWithExisting(paymentNotify, "支付回调前缀", subscribe, serverPrefix);
    }

    private static void assertClientPathsNoMutualConflict(String passport, String user, String admin, String paymentNotify) {
        if (StringUtils.hasText(passport) && StringUtils.hasText(user) && pathsConflict(passport, user)) {
            throw new BusinessException(500, "Passport API 前缀与用户 API 前缀不能冲突");
        }
        if (StringUtils.hasText(passport) && StringUtils.hasText(admin) && pathsConflict(passport, admin)) {
            throw new BusinessException(500, "Passport API 前缀与管理 API 前缀不能冲突");
        }
        if (StringUtils.hasText(user) && StringUtils.hasText(admin) && pathsConflict(user, admin)) {
            throw new BusinessException(500, "用户 API 前缀与管理 API 前缀不能冲突");
        }
        if (StringUtils.hasText(passport) && StringUtils.hasText(paymentNotify) && pathsConflict(passport, paymentNotify)) {
            throw new BusinessException(500, "Passport API 前缀与支付回调前缀不能冲突");
        }
        if (StringUtils.hasText(user) && StringUtils.hasText(paymentNotify) && pathsConflict(user, paymentNotify)) {
            throw new BusinessException(500, "用户 API 前缀与支付回调前缀不能冲突");
        }
        if (StringUtils.hasText(admin) && StringUtils.hasText(paymentNotify) && pathsConflict(admin, paymentNotify)) {
            throw new BusinessException(500, "管理 API 前缀与支付回调前缀不能冲突");
        }
        if (StringUtils.hasText(passport) && pathsConflict(passport, FIXED_PUBLIC_CONFIG_PATH)) {
            throw new BusinessException(500, "Passport API 前缀不能与固定公开配置路径 /config 冲突");
        }
        if (StringUtils.hasText(user) && pathsConflict(user, FIXED_PUBLIC_CONFIG_PATH)) {
            throw new BusinessException(500, "用户 API 前缀不能与固定公开配置路径 /config 冲突");
        }
        if (StringUtils.hasText(admin) && pathsConflict(admin, FIXED_PUBLIC_CONFIG_PATH)) {
            throw new BusinessException(500, "管理 API 前缀不能与固定公开配置路径 /config 冲突");
        }
        if (StringUtils.hasText(paymentNotify) && pathsConflict(paymentNotify, FIXED_PUBLIC_CONFIG_PATH)) {
            throw new BusinessException(500, "支付回调前缀不能与固定公开配置路径 /config 冲突");
        }
    }

    private void assertNoConflictWithExisting(String candidate, String label, String subscribe, String serverPrefix) {
        if (!StringUtils.hasText(candidate)) {
            return;
        }
        if (StringUtils.hasText(subscribe) && pathsConflict(candidate, subscribe)) {
            throw new BusinessException(500, label + "不能与订阅路径冲突");
        }
        if (StringUtils.hasText(serverPrefix) && pathsConflict(candidate, serverPrefix)) {
            throw new BusinessException(500, label + "不能与节点 API 前缀冲突");
        }
        String secure = getSecurePath();
        if (StringUtils.hasText(secure)) {
            String securePath = "/" + secure.trim();
            if (pathsConflict(candidate, securePath)) {
                throw new BusinessException(500, label + "不能与后台路径冲突");
            }
        }
    }

    private static void normalizeClientPathKeyInPlace(Map<String, Object> site, String key, String label) {
        if (!site.containsKey(key)) {
            return;
        }
        Object raw = site.get(key);
        String normalized = normalizeServerApiPrefix(raw == null ? "" : String.valueOf(raw));
        if (!StringUtils.hasText(normalized)) {
            site.put(key, "");
            return;
        }
        if (!isValidClientApiPrefix(normalized)) {
            throw new BusinessException(500,
                    label + "不合法：须以 / 开头，仅含字母数字与 ._~/ -，长度≤64，且不能与保留 API 前缀冲突");
        }
        site.put(key, normalized);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableSiteMap(Map<String, Object> body, Map<?, ?> siteRaw) {
        if (siteRaw instanceof HashMap || siteRaw instanceof LinkedHashMap) {
            return (Map<String, Object>) siteRaw;
        }
        Map<String, Object> site = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : siteRaw.entrySet()) {
            site.put(String.valueOf(e.getKey()), e.getValue());
        }
        try {
            body.put("site", site);
        } catch (UnsupportedOperationException ignored) {
            // Immutable save body (e.g. Map.of in unit tests): validate against copy only.
        }
        return site;
    }

    /**
     * Ensure blank {@code site.passport_api_prefix} / {@code user_api_prefix} /
     * {@code admin_api_prefix}. Strips legacy {@code public_config_path} from site map.
     *
     * @return true if any value was generated or normalized into the map
     */
    @SuppressWarnings("unchecked")
    static boolean ensureClientApiPathsInPlace(Map<String, Object> full) {
        if (full == null) {
            return false;
        }
        Object siteObj = full.get("site");
        Map<String, Object> site;
        if (siteObj instanceof HashMap || siteObj instanceof LinkedHashMap) {
            site = (Map<String, Object>) siteObj;
        } else if (siteObj instanceof Map<?, ?> raw) {
            site = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                site.put(String.valueOf(e.getKey()), e.getValue());
            }
            full.put("site", site);
        } else {
            site = new LinkedHashMap<>();
            full.put("site", site);
        }
        boolean changed = false;
        if (site.containsKey("public_config_path")) {
            site.remove("public_config_path");
            changed = true;
        }
        changed = ensureSitePathKey(site, "passport_api_prefix", ConfigService::generatePassportApiPrefix) || changed;
        changed = ensureSitePathKey(site, "user_api_prefix", ConfigService::generateUserApiPrefix) || changed;
        changed = ensureSitePathKey(site, "admin_api_prefix", ConfigService::generateAdminApiPrefix) || changed;
        changed = ensureSitePathKey(site, "payment_notify_prefix", ConfigService::generatePaymentNotifyPrefix) || changed;

        String passport = normalizeServerApiPrefix(str(site.get("passport_api_prefix")));
        String user = normalizeServerApiPrefix(str(site.get("user_api_prefix")));
        String admin = normalizeServerApiPrefix(str(site.get("admin_api_prefix")));
        String paymentNotify = normalizeServerApiPrefix(str(site.get("payment_notify_prefix")));
        // Avoid rare auto-gen collisions (incl. fixed /config).
        if (clientPathsHaveConflict(passport, user, admin, paymentNotify)) {
            site.put("passport_api_prefix", generatePassportApiPrefix());
            site.put("user_api_prefix", generateUserApiPrefix());
            site.put("admin_api_prefix", generateAdminApiPrefix());
            site.put("payment_notify_prefix", generatePaymentNotifyPrefix());
            changed = true;
        }
        return changed;
    }

    private static boolean clientPathsHaveConflict(String passport, String user, String admin, String paymentNotify) {
        return pathsConflict(passport, user)
                || pathsConflict(passport, admin)
                || pathsConflict(user, admin)
                || pathsConflict(passport, paymentNotify)
                || pathsConflict(user, paymentNotify)
                || pathsConflict(admin, paymentNotify)
                || pathsConflict(passport, FIXED_PUBLIC_CONFIG_PATH)
                || pathsConflict(user, FIXED_PUBLIC_CONFIG_PATH)
                || pathsConflict(admin, FIXED_PUBLIC_CONFIG_PATH)
                || pathsConflict(paymentNotify, FIXED_PUBLIC_CONFIG_PATH);
    }

    private static boolean ensureSitePathKey(Map<String, Object> site, String key,
                                            java.util.function.Supplier<String> generator) {
        String current = normalizeServerApiPrefix(str(site.get(key)));
        if (StringUtils.hasText(current)) {
            if (!current.equals(str(site.get(key)))) {
                site.put(key, current);
                return true;
            }
            return false;
        }
        site.put(key, generator.get());
        return true;
    }

    static String getSitePathFromMap(Map<String, Object> full, String key) {
        if (full != null && full.get("site") instanceof Map<?, ?> site) {
            return normalizeServerApiPrefix(str(site.get(key)));
        }
        return "";
    }

    static boolean isValidClientApiPrefix(String normalized) {
        if (normalized == null || normalized.isEmpty() || "/".equals(normalized)) {
            return false;
        }
        if (normalized.length() > SERVER_API_PREFIX_MAX_LEN) {
            return false;
        }
        if (normalized.contains("..")) {
            return false;
        }
        if (!normalized.matches("^/[A-Za-z0-9._~/-]+$")) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String prefix : CLIENT_API_PREFIX_RESERVED) {
            if (lower.equals(prefix) || lower.startsWith(prefix + "/") || prefix.startsWith(lower + "/")) {
                return false;
            }
        }
        return true;
    }

    static boolean pathsConflict(String a, String b) {
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return false;
        }
        String la = a.toLowerCase(Locale.ROOT);
        String lb = b.toLowerCase(Locale.ROOT);
        return la.equals(lb) || la.startsWith(lb + "/") || lb.startsWith(la + "/");
    }

    private static void requireIntAtLeast(Map<?, ?> server, String key, int minInclusive, String message) {
        if (!server.containsKey(key)) {
            return;
        }
        Integer value = parseIntOrNull(server.get(key));
        if (value == null || value < minInclusive) {
            throw new BusinessException(500, message);
        }
    }

    private static Integer parseIntOrNull(Object raw) {
        if (raw instanceof Number num) {
            return num.intValue();
        }
        if (raw instanceof String str && !str.isBlank()) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> e : source.entrySet()) {
            Object srcVal = e.getValue();
            Object tgtVal = target.get(e.getKey());
            if (srcVal instanceof Map<?, ?> srcMap && tgtVal instanceof Map<?, ?> tgtMap) {
                Map<String, Object> child;
                // Map.of / Collections.unmodifiableMap break put during merge — copy first.
                if (tgtMap instanceof HashMap || tgtMap instanceof LinkedHashMap) {
                    child = (Map<String, Object>) tgtMap;
                } else {
                    child = new HashMap<>();
                    for (Map.Entry<?, ?> te : tgtMap.entrySet()) {
                        child.put(String.valueOf(te.getKey()), te.getValue());
                    }
                    target.put(e.getKey(), child);
                }
                deepMerge(child, (Map<String, Object>) srcMap);
            } else {
                target.put(e.getKey(), srcVal);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void mergePhpFlatConfig(Map<String, Object> defaults, Map<String, Object> flat) {
        if (flat == null || flat.isEmpty()) {
            return;
        }
        putPhpSection(defaults, "ticket", flat, "ticket_status");
        putPhpSection(defaults, "deposit", flat, "deposit_bounus");
        putPhpSection(defaults, "invite", flat,
                "invite_force", "invite_commission", "invite_gen_limit", "invite_never_expire",
                "commission_first_time_enable", "commission_auto_check_enable",
                "commission_withdraw_limit", "commission_withdraw_method", "withdraw_close_enable",
                "commission_distribution_enable", "commission_distribution_l1",
                "commission_distribution_l2", "commission_distribution_l3");
        putPhpSection(defaults, "site", flat,
                "logo", "force_https", "stop_register", "app_name", "app_description", "app_url",
                "subscribe_url", "subscribe_path", "passport_api_prefix", "user_api_prefix",
                "admin_api_prefix", "payment_notify_prefix", "try_out_plan_id", "try_out_hour", "tos_url",
                "currency", "currency_symbol");
        putPhpSection(defaults, "subscribe", flat,
                "plan_change_enable", "reset_traffic_method", "surplus_enable", "allow_new_period",
                "new_order_event_id", "renew_order_event_id", "change_order_event_id",
                "show_info_to_server_enable", "show_subscribe_method", "show_subscribe_expire");
        putPhpSection(defaults, "frontend", flat,
                "frontend_theme", "frontend_theme_sidebar", "frontend_theme_header",
                "frontend_theme_color", "frontend_background_url");
        putPhpSection(defaults, "server", flat,
                "server_api_url", "server_api_prefix", "server_token", "server_pull_interval", "server_push_interval",
                "server_node_report_min_traffic", "server_device_online_min_traffic", "device_limit_mode");
        putPhpSection(defaults, "email", flat,
                "email_template", "email_host", "email_port", "email_username", "email_password",
                "email_encryption", "email_from_address");
        putPhpSection(defaults, "telegram", flat,
                "telegram_bot_enable", "telegram_bot_token", "telegram_discuss_link");
        putPhpSection(defaults, "app", flat,
                "windows_version", "windows_download_url", "macos_version", "macos_download_url",
                "android_version", "android_download_url");
        putPhpSection(defaults, "safe", flat,
                "email_verify", "safe_mode_enable", "secure_path", "email_whitelist_enable",
                "email_whitelist_suffix", "email_gmail_limit_enable", "recaptcha_enable",
                "recaptcha_key", "recaptcha_site_key", "register_limit_by_ip_enable",
                "register_limit_count", "register_limit_expire", "password_limit_enable",
                "password_limit_count", "password_limit_expire");
    }

    @SuppressWarnings("unchecked")
    private static void putPhpSection(Map<String, Object> defaults, String section, Map<String, Object> flat,
                                      String... keys) {
        Object sec = defaults.get(section);
        if (!(sec instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> target = (Map<String, Object>) sec;
        for (String key : keys) {
            if (flat.containsKey(key)) {
                target.put(key, flat.get(key));
            }
        }
    }

    private Map<String, Object> buildDefaults() {
        Map<String, Object> data = new LinkedHashMap<>();
        // All nested sections must be mutable: save()/fetch deepMerge into these maps.
        data.put("ticket", mutableMap("ticket_status", ticketStatus != null ? ticketStatus : 0));
        data.put("deposit", mutableMap("deposit_bounus", new Object[0]));
        Map<String, Object> invite = new HashMap<>();
        invite.put("invite_force", 0);
        invite.put("invite_commission", inviteCommission != null ? inviteCommission : 10);
        invite.put("invite_gen_limit", inviteGenLimit != null ? inviteGenLimit : 5);
        invite.put("invite_never_expire", 0);
        invite.put("commission_first_time_enable", 1);
        invite.put("commission_auto_check_enable", 1);
        invite.put("commission_withdraw_limit", commissionWithdrawLimit != null ? commissionWithdrawLimit : 100);
        invite.put("commission_withdraw_method", "alipay,wechat");
        invite.put("withdraw_close_enable", withdrawCloseEnable != null ? withdrawCloseEnable : 0);
        invite.put("commission_distribution_enable", commissionDistributionEnable != null ? commissionDistributionEnable : 0);
        invite.put("commission_distribution_l1", commissionDistributionL1 != null ? commissionDistributionL1 : 100.0);
        invite.put("commission_distribution_l2", 0.0);
        invite.put("commission_distribution_l3", 0.0);
        data.put("invite", invite);
        Map<String, Object> site = new HashMap<>();
        site.put("logo", "");
        site.put("force_https", 0);
        site.put("stop_register", 0);
        site.put("app_name", appName != null ? appName : "V2Board");
        site.put("app_description", "V2Board is best!");
        site.put("app_url", appUrl != null ? appUrl : "");
        site.put("subscribe_url", subscribeUrl != null ? subscribeUrl : "");
        site.put("subscribe_path", subscribePath != null ? subscribePath : "/api/v1/client/subscribe");
        site.put("passport_api_prefix", "");
        site.put("user_api_prefix", "");
        site.put("admin_api_prefix", "");
        site.put("payment_notify_prefix", "");
        site.put("try_out_plan_id", 0);
        site.put("try_out_hour", 1);
        site.put("tos_url", "");
        site.put("currency", "CNY");
        site.put("currency_symbol", "¥");
        data.put("site", site);
        Map<String, Object> subscribe = new HashMap<>();
        subscribe.put("plan_change_enable", 1);
        subscribe.put("reset_traffic_method", resetTrafficMethod != null ? resetTrafficMethod : 0);
        subscribe.put("surplus_enable", 1);
        subscribe.put("allow_new_period", allowNewPeriod != null ? allowNewPeriod : 0);
        subscribe.put("new_order_event_id", 0);
        subscribe.put("renew_order_event_id", 0);
        subscribe.put("change_order_event_id", 0);
        subscribe.put("show_info_to_server_enable", Boolean.TRUE.equals(showInfoToServerEnable) ? 1 : 0);
        subscribe.put("show_subscribe_method", showSubscribeMethod != null ? showSubscribeMethod : 0);
        subscribe.put("show_subscribe_expire", showSubscribeExpire != null ? showSubscribeExpire : 5);
        data.put("subscribe", subscribe);
        data.put("frontend", mutableMap(
                "frontend_theme", "v2board",
                "frontend_theme_sidebar", "light",
                "frontend_theme_header", "dark",
                "frontend_theme_color", "default",
                "frontend_background_url", ""
        ));
        data.put("server", mutableMap(
                "server_api_url", "",
                "server_api_prefix", "",
                "server_token", "",
                "server_pull_interval", 60,
                "server_push_interval", 60,
                "server_node_report_min_traffic", 0,
                "server_device_online_min_traffic", 0,
                "device_limit_mode", 0
        ));
        data.put("email", mutableMap(
                "email_template", "default",
                "email_host", "",
                "email_port", "",
                "email_username", "",
                "email_password", "",
                "email_encryption", "",
                "email_from_address", ""
        ));
        data.put("telegram", mutableMap(
                "telegram_bot_enable", 0,
                "telegram_bot_token", "",
                "telegram_discuss_link", ""
        ));
        data.put("app", mutableMap(
                "windows_version", "",
                "windows_download_url", "",
                "macos_version", "",
                "macos_download_url", "",
                "android_version", "",
                "android_download_url", ""
        ));
        Map<String, Object> safe = new HashMap<>();
        safe.put("email_verify", 0);
        safe.put("safe_mode_enable", 0);
        safe.put("secure_path", "");
        safe.put("email_whitelist_enable", 0);
        safe.put("email_whitelist_suffix", "");
        safe.put("email_gmail_limit_enable", 0);
        safe.put("recaptcha_enable", 0);
        safe.put("recaptcha_key", "");
        safe.put("recaptcha_site_key", "");
        safe.put("register_limit_by_ip_enable", 0);
        safe.put("register_limit_count", 3);
        safe.put("register_limit_expire", 60);
        safe.put("password_limit_enable", 1);
        safe.put("password_limit_count", 5);
        safe.put("password_limit_expire", 60);
        data.put("safe", safe);
        return data;
    }

    /** Mutable section map for deepMerge (Map.of is immutable and breaks save/fetch). */
    private static Map<String, Object> mutableMap(Object... kvs) {
        Map<String, Object> map = new HashMap<>();
        if (kvs == null) {
            return map;
        }
        if (kvs.length % 2 != 0) {
            throw new IllegalArgumentException("mutableMap requires even number of arguments");
        }
        for (int i = 0; i < kvs.length; i += 2) {
            map.put(String.valueOf(kvs[i]), kvs[i + 1]);
        }
        return map;
    }
}
