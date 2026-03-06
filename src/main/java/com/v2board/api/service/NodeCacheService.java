package com.v2board.api.service;

import com.v2board.api.config.V2boardRedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 专门用于节点状态 / 在线信息的缓存服务。
 * <p>
 * 所有与 SERVER_* / ALIVE_* 等节点相关的 Key 均通过此服务读写，
 * 内部统一走 cacheRedisTemplate（DB1），并根据需要自动追加 Redis 前缀，
 * 以便与 PHP 端的 Cache::get/put 行为保持一致。
 */
@Service
public class NodeCacheService {

    private static final Logger logger = LoggerFactory.getLogger(NodeCacheService.class);

    private final RedisTemplate<String, Object> cacheRedisTemplate;
    private final String prefix;

    public NodeCacheService(@Qualifier("phpCacheRedisTemplate") RedisTemplate<String, Object> phpCacheRedisTemplate,
                            V2boardRedisProperties redisProperties) {
        this.cacheRedisTemplate = phpCacheRedisTemplate;
        String p = redisProperties.getPrefix();
        this.prefix = (p != null) ? p : "";
    }

    /**
     * 生成不带前缀的 SERVER_* Key（与 PHP CacheKey::get 内部形式一致）。
     */
    public String buildServerKey(String keyPrefix, Long id) {
        if (keyPrefix == null || id == null) {
            return null;
        }
        return keyPrefix + "_" + id;
    }

    /**
     * 为原始 Key 追加 Redis 前缀。
     */
    private String applyPrefix(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        if (prefix.isEmpty()) {
            return rawKey;
        }
        return prefix + rawKey;
    }

    /**
     * 写入单个值，带可选过期时间。
     */
    public void set(String rawKey, Object value, Duration ttl) {
        if (rawKey == null) {
            return;
        }
        try {
            String redisKey = applyPrefix(rawKey);
            if (ttl != null) {
                cacheRedisTemplate.opsForValue().set(redisKey, value, ttl);
            } else {
                cacheRedisTemplate.opsForValue().set(redisKey, value);
            }
        } catch (Exception e) {
            logger.warn("Failed to set node cache key: {}", rawKey, e);
        }
    }

    /**
     * 读取单个值。
     */
    public Object get(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        try {
            String redisKey = applyPrefix(rawKey);
            return cacheRedisTemplate.opsForValue().get(redisKey);
        } catch (Exception e) {
            logger.warn("Failed to get node cache key: {}", rawKey, e);
            return null;
        }
    }

    /**
     * 批量读取多个 Key，对外仍然使用“原始 Key”（无前缀），
     * 内部自动为每个 Key 追加前缀。
     */
    public List<Object> multiGet(List<String> rawKeys) {
        if (rawKeys == null || rawKeys.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> redisKeys = rawKeys.stream()
                    .filter(Objects::nonNull)
                    .map(this::applyPrefix)
                    .collect(Collectors.toList());
            List<Object> results = cacheRedisTemplate.opsForValue().multiGet(redisKeys);
            return results != null ? results : Collections.emptyList();
        } catch (Exception e) {
            logger.warn("Failed to multiGet node cache keys: {}", rawKeys, e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除单个 Key。
     */
    public void delete(String rawKey) {
        if (rawKey == null) {
            return;
        }
        try {
            String redisKey = applyPrefix(rawKey);
            cacheRedisTemplate.delete(redisKey);
        } catch (Exception e) {
            logger.warn("Failed to delete node cache key: {}", rawKey, e);
        }
    }

    /**
     * 判断 Key 是否存在。
     */
    public boolean has(String rawKey) {
        if (rawKey == null) {
            return false;
        }
        try {
            String redisKey = applyPrefix(rawKey);
            Boolean hasKey = cacheRedisTemplate.hasKey(redisKey);
            return Boolean.TRUE.equals(hasKey);
        } catch (Exception e) {
            logger.warn("Failed to check node cache key: {}", rawKey, e);
            return false;
        }
    }

    /**
     * 读取 SERVER_*_LAST_CHECK_AT / LAST_PUSH_AT 等时间戳型 Key。
     */
    public Long getServerTimestamp(String keyPrefix, Long id) {
        String rawKey = buildServerKey(keyPrefix, id);
        if (rawKey == null) {
            return null;
        }
        try {
            Object value = get(rawKey);
            if (value instanceof Number num) {
                return num.longValue();
            }
            if (value instanceof String str && !str.isEmpty()) {
                // 兼容两种形式：
                // 1）纯数字字符串（Java 写入）
                // 2）PHP serialize 的整型：i:123456789;
                String s = str.trim();
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException ignore) {
                    if (s.startsWith("i:") && s.endsWith(";")) {
                        String numPart = s.substring(2, s.length() - 1);
                        try {
                            return Long.parseLong(numPart.trim());
                        } catch (NumberFormatException ignore2) {
                            // fall through
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error reading server timestamp for {} {}", keyPrefix, id, e);
        }
        return null;
    }
}

