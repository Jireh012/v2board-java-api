package com.v2board.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "v2board.redis")
public class V2boardRedisProperties {

    /**
     * 用于节点状态 / 缓存的 Redis 逻辑库索引
     * 对齐 PHP 项目的 REDIS_CACHE_DB，默认使用 1
     */
    private int cacheDatabase = 1;

    /**
     * 可选的 Redis Key 前缀，用于与 Laravel 的 REDIS_PREFIX 对齐
     */
    private String prefix = "";

    public int getCacheDatabase() {
        return cacheDatabase;
    }

    public void setCacheDatabase(int cacheDatabase) {
        this.cacheDatabase = cacheDatabase;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}

