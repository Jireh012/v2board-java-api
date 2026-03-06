package com.v2board.api.config;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableConfigurationProperties(V2boardRedisProperties.class)
public class RedisConfig {

    /**
     * 默认 RedisTemplate，使用 spring.data.redis.database（通常为 DB0），
     * 保持与现有逻辑兼容（如流量统计 Hash 等）。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用String序列化器作为key的序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // 使用JSON序列化器作为value的序列化器
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 专用于缓存 / 节点状态的 Redis 连接工厂，指向 REDIS_CACHE_DB（通常为 DB1）。
     */
    @Bean
    public RedisConnectionFactory cacheRedisConnectionFactory(RedisProperties redisProperties,
                                                              V2boardRedisProperties v2boardRedisProperties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProperties.getHost());
        config.setPort(redisProperties.getPort());
        if (redisProperties.getPassword() != null) {
            config.setPassword(redisProperties.getPassword());
        }
        if (redisProperties.getUsername() != null) {
            config.setUsername(redisProperties.getUsername());
        }
        config.setDatabase(v2boardRedisProperties.getCacheDatabase());

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientBuilder =
                LettuceClientConfiguration.builder();
        if (redisProperties.getTimeout() != null) {
            clientBuilder.commandTimeout(redisProperties.getTimeout());
        }
        if (redisProperties.getSsl().isEnabled()) {
            clientBuilder.useSsl();
        }

        LettuceClientConfiguration clientConfiguration = clientBuilder.build();
        return new LettuceConnectionFactory(config, clientConfiguration);
    }

    /**
     * 使用缓存库（DB1）的 RedisTemplate，供 Java 端通用缓存使用
     * （例如 USER_SESSIONS_* 等结构化数据），采用 JSON 序列化。
     */
    @Bean
    public RedisTemplate<String, Object> cacheRedisTemplate(RedisConnectionFactory cacheRedisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(cacheRedisConnectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 专门用于兼容 PHP Cache（DB1 + 字符串），
     * 存放 SERVER_* / ALIVE_* / 其他 PHP serialize 写入的键。
     */
    @Bean
    public RedisTemplate<String, Object> phpCacheRedisTemplate(RedisConnectionFactory cacheRedisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(cacheRedisConnectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}
