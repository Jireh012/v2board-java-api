package com.v2board.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    @Autowired
    private NodeCacheService nodeCacheService;

    /**
     * 使用 DB0 的默认 Redis，避免与 PHP 共用 DB1 的 Cache。
     * PHP 的 Cache（USER_SESSIONS_* 等）在 DB1 且为 serialize 格式；
     * Java 的会话/通用缓存放在 DB0、JSON 格式，互不干扰。
     */
    @Autowired
    @Qualifier("redisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取在线设备数
     * PHP: Cache::get('ALIVE_IP_USER_' . $userId)
     *
     * @param userId 用户ID
     * @return 在线设备数，如果不存在返回0
     */
    public Integer getAliveIpCount(Long userId) {
        if (userId == null) {
            return 0;
        }

        try {
            String key = "ALIVE_IP_USER_" + userId;
            Object data = nodeCacheService.get(key);

            if (data == null) {
                return 0;
            }

            // 1）Number 形式（兜底）
            if (data instanceof Number) {
                return ((Number) data).intValue();
            }

            // 2）字符串形式（例如 PHP serialize 写入）
            if (data instanceof String str && !str.isEmpty()) {
                // 典型形式：a:2:{s:6:"vless2";a:2:{...}s:8:"alive_ip";i:1;}
                String s = str;
                int idx = s.indexOf("s:8:\"alive_ip\"");
                if (idx >= 0) {
                    int iPos = s.indexOf("i:", idx);
                    int semi = s.indexOf(';', iPos + 2);
                    if (iPos > 0 && semi > iPos + 2) {
                        String numPart = s.substring(iPos + 2, semi).trim();
                        try {
                            return Integer.parseInt(numPart);
                        } catch (NumberFormatException ignore) {
                            // fall through
                        }
                    }
                }
            }

            return 0;
        } catch (Exception e) {
            logger.error("Error getting alive IP count for user {}", userId, e);
            return 0;
        }
    }

    /**
     * 设置缓存值（Java 端专用，存 DB0，与 PHP DB1 隔离）
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            if (unit != null && timeout > 0) {
                redisTemplate.opsForValue().set(key, value, timeout, unit);
            } else {
                redisTemplate.opsForValue().set(key, value);
            }
        } catch (Exception e) {
            logger.error("Error setting cache key: {}", key, e);
        }
    }

    /**
     * 获取缓存值（Java 端专用，读 DB0）
     */
    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            logger.error("Error getting cache key: {}", key, e);
            return null;
        }
    }

    /**
     * 删除缓存（Java 端专用，删 DB0）
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("Error deleting cache key: {}", key, e);
        }
    }

    /**
     * 检查缓存是否存在（Java 端专用，查 DB0）
     */
    public Boolean has(String key) {
        try {
            Boolean hasKey = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(hasKey);
        } catch (Exception e) {
            logger.error("Error checking cache key: {}", key, e);
            return false;
        }
    }
}

