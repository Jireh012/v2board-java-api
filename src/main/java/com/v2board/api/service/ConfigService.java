package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
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

    /** UI route segments that must not be used as custom admin path. */
    private static final Set<String> SECURE_PATH_RESERVED = Set.of(
            "login", "register", "forget", "dashboard", "plan", "order", "server",
            "invite", "ticket", "traffic", "knowledge", "profile", "api"
    );

    /** HTTP prefixes that must not collide with a custom subscribe path. */
    private static final List<String> SUBSCRIBE_PATH_RESERVED_PREFIXES = List.of(
            "/api/v1/user",
            "/api/v1/admin",
            "/api/v1/passport",
            "/api/v1/guest",
            "/api/v1/server"
    );

    private static final int SUBSCRIBE_PATH_MAX_LEN = 128;

    /**
     * 获取完整配置或按 key 返回某一分组。与 PHP GET /config/fetch 一致。
     */
    public Map<String, Object> fetch(String key) throws Exception {
        Map<String, Object> full = getFullConfig();
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
        Map<String, Object> current = getFullConfig();
        deepMerge(current, body);
        String json = objectMapper.writeValueAsString(current);
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
        // Hot-reload subscribe HTTP route when site.subscribe_path changes (no restart).
        if (subscribeRouteRegistrar != null && body != null && body.containsKey("site")) {
            subscribeRouteRegistrar.refresh();
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
                "subscribe_url", "subscribe_path", "try_out_plan_id", "try_out_hour", "tos_url",
                "currency", "currency_symbol");
        putPhpSection(defaults, "subscribe", flat,
                "plan_change_enable", "reset_traffic_method", "surplus_enable", "allow_new_period",
                "new_order_event_id", "renew_order_event_id", "change_order_event_id",
                "show_info_to_server_enable", "show_subscribe_method", "show_subscribe_expire");
        putPhpSection(defaults, "frontend", flat,
                "frontend_theme", "frontend_theme_sidebar", "frontend_theme_header",
                "frontend_theme_color", "frontend_background_url");
        putPhpSection(defaults, "server", flat,
                "server_api_url", "server_token", "server_pull_interval", "server_push_interval",
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
