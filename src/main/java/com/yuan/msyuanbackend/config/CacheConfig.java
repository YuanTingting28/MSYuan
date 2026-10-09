package com.yuan.msyuanbackend.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存配置 用Redis做Spring cache 的底层实现
 * 为什么不用默认的？ 默认是JDK序列化，存到Redis里是一串二进制乱码
 * 用redis-cli根本看不懂，换成JSON序列化，key是纯字符串，value是JSON
 * 排查问题的时候就肉眼可读
 */
@Configuration
public class CacheConfig {
    private static final String CACHE_HOME_QUESTION_BANK = "questionBank";
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory)
    {
        GenericJacksonJsonRedisSerializer valueSerializer =
                GenericJacksonJsonRedisSerializer.builder().build();

        //默认配置：key用String value用JSON TTL 5分钟
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .computePrefixWith(cacheName -> "msyuan:cache:"+cacheName+":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer))
                .disableCachingNullValues();
        Map<String,RedisCacheConfiguration> configMap = new HashMap<>();
        configMap.put(CACHE_HOME_QUESTION_BANK,defaultConfig.entryTtl(Duration.ofMinutes(30)));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configMap)
                .build();
    }

}
