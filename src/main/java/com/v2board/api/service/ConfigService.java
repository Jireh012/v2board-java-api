package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.mapper.SystemConfigMapper;
import com.v2board.api.model.SystemConfig;
import com.v2board.api.util.V2boardPhpConfigLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
     * 保存配置。请求体为与 fetch 相同的嵌套结构，会与现有配置合并后写入。
     */
    public void save(Map<String, Object> body) throws Exception {
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
    }

    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> e : source.entrySet()) {
            Object srcVal = e.getValue();
            Object tgtVal = target.get(e.getKey());
            if (srcVal instanceof Map && tgtVal instanceof Map) {
                deepMerge((Map<String, Object>) tgtVal, (Map<String, Object>) srcVal);
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
        data.put("ticket", Map.of("ticket_status", ticketStatus != null ? ticketStatus : 0));
        data.put("deposit", Map.of("deposit_bounus", new Object[0]));
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
        data.put("frontend", Map.of(
                "frontend_theme", "v2board",
                "frontend_theme_sidebar", "light",
                "frontend_theme_header", "dark",
                "frontend_theme_color", "default",
                "frontend_background_url", ""
        ));
        data.put("server", Map.of(
                "server_api_url", "",
                "server_token", "",
                "server_pull_interval", 60,
                "server_push_interval", 60,
                "server_node_report_min_traffic", 0,
                "server_device_online_min_traffic", 0,
                "device_limit_mode", 0
        ));
        data.put("email", Map.of(
                "email_template", "default",
                "email_host", "",
                "email_port", "",
                "email_username", "",
                "email_password", "",
                "email_encryption", "",
                "email_from_address", ""
        ));
        data.put("telegram", Map.of(
                "telegram_bot_enable", 0,
                "telegram_bot_token", "",
                "telegram_discuss_link", ""
        ));
        data.put("app", Map.of(
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
}
