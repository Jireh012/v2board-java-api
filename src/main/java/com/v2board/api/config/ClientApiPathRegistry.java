package com.v2board.api.config;

import com.v2board.api.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Hot-reloadable passport/user/admin/payment-notify/public-config path prefixes for {@link ClientApiPathFilter}.
 */
@Component
public class ClientApiPathRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ClientApiPathRegistry.class);

    private final ConfigService configService;

    private volatile String passportPrefix = "";
    private volatile String userPrefix = "";
    private volatile String adminPrefix = "";
    private volatile String paymentNotifyPrefix = "";
    private volatile String publicConfigPath = "";

    public ClientApiPathRegistry(@Lazy ConfigService configService) {
        this.configService = configService;
    }

    public synchronized void refresh() {
        try {
            Map<String, String> paths = configService.ensureClientApiPaths();
            passportPrefix = paths.getOrDefault("passport_api_prefix", "");
            userPrefix = paths.getOrDefault("user_api_prefix", "");
            adminPrefix = paths.getOrDefault("admin_api_prefix", "");
            paymentNotifyPrefix = paths.getOrDefault("payment_notify_prefix", "");
            publicConfigPath = ConfigService.FIXED_PUBLIC_CONFIG_PATH;
            logger.info("Client API paths active: passport={}, user={}, admin={}, paymentNotify={}, public={}",
                    passportPrefix, userPrefix, adminPrefix, paymentNotifyPrefix, publicConfigPath);
        } catch (Exception e) {
            logger.error("Failed to refresh client API paths", e);
        }
    }

    public String getPassportPrefix() {
        return passportPrefix;
    }

    public String getUserPrefix() {
        return userPrefix;
    }

    public String getAdminPrefix() {
        return adminPrefix;
    }

    public String getPaymentNotifyPrefix() {
        return paymentNotifyPrefix;
    }

    public String getPublicConfigPath() {
        return publicConfigPath;
    }

    public boolean hasPrefixes() {
        return StringUtils.hasText(passportPrefix)
                && StringUtils.hasText(userPrefix)
                && StringUtils.hasText(adminPrefix)
                && StringUtils.hasText(paymentNotifyPrefix);
    }
}
