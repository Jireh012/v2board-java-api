package com.v2board.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 获取在线设备数
     * PHP: Cache::get('ALIVE_IP_USER_' . $userId)
     * 
     * @param userId 用户ID
     * @return 在线设备数，如果不存在返回0
     */
    @SuppressWarnings("unchecked")
    public Integer getAliveIpCount(Long userId) {
        if (userId == null) {
            return 0;
        }
        
        try {
            String key = "ALIVE_IP_USER_" + userId;
            Object data = redisTemplate.opsForValue().get(key);
            
            if (data == null) {
                return 0;
            }
            
            // 如果data是Map类型，尝试获取alive_ip字段
            if (data instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) data;
                Object aliveIp = map.get("alive_ip");
                if (aliveIp instanceof Number) {
                    return ((Number) aliveIp).intValue();
                }
            }
            
            // 如果data是Number类型，直接返回
            if (data instanceof Number) {
                return ((Number) data).intValue();
            }
            
            return 0;
        } catch (Exception e) {
            logger.error("Error getting alive IP count for user {}", userId, e);
            return 0;
        }
    }
    
    /**
     * 设置缓存值
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            logger.error("Error setting cache key: {}", key, e);
        }
    }
    
    /**
     * 获取缓存值
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
     * 删除缓存
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("Error deleting cache key: {}", key, e);
        }
    }
    
    /**
     * 检查缓存是否存在
     */
    public Boolean has(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            logger.error("Error checking cache key: {}", key, e);
            return false;
        }
    }
}

