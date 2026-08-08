package com.v2board.api.service;

import com.v2board.api.common.BusinessException;
import com.v2board.api.util.CacheKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * PHP AuthController login password_limit_* parity (keyed by email).
 */
@Service
public class LoginPasswordLimitService {

    @Autowired
    private ConfigService configService;

    @Autowired
    private NodeCacheService nodeCacheService;

    public void assertNotLocked(String email) {
        if (configService.getPasswordLimitEnable() != 1) {
            return;
        }
        String key = cacheKey(email);
        if (key == null) {
            return;
        }
        int count = toInt(nodeCacheService.get(key));
        int limit = configService.getPasswordLimitCount();
        if (count >= limit) {
            int minutes = configService.getPasswordLimitExpireMinutes();
            throw new BusinessException(500,
                    "There are too many password errors, please try again after " + minutes + " minutes.");
        }
    }

    public void recordFailure(String email) {
        if (configService.getPasswordLimitEnable() != 1) {
            return;
        }
        String key = cacheKey(email);
        if (key == null) {
            return;
        }
        int count = toInt(nodeCacheService.get(key));
        int expireMin = configService.getPasswordLimitExpireMinutes();
        nodeCacheService.set(key, count + 1, Duration.ofMinutes(expireMin));
    }

    private static String cacheKey(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return CacheKeyUtil.get("PASSWORD_ERROR_LIMIT", email.trim());
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o));
            } catch (NumberFormatException ignore) {
            }
        }
        return 0;
    }
}
