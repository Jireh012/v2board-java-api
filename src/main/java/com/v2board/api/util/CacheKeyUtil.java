package com.v2board.api.util;

import com.v2board.api.common.BusinessException;

import java.util.Set;

/**
 * 对齐 PHP App\Utils\CacheKey
 */
public final class CacheKeyUtil {

    private static final Set<String> ALLOWED = Set.of(
            "EMAIL_VERIFY_CODE",
            "LAST_SEND_EMAIL_VERIFY_TIMESTAMP",
            "REGISTER_IP_RATE_LIMIT",
            "PASSWORD_ERROR_LIMIT",
            "FORGET_REQUEST_LIMIT",
            "TEMP_TOKEN"
    );

    private CacheKeyUtil() {
    }

    public static String get(String key, String uniqueValue) {
        if (!ALLOWED.contains(key)) {
            throw new BusinessException(500, "key is not in cache key list");
        }
        return key + "_" + uniqueValue;
    }
}
