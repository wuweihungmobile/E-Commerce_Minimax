package com.nextkey.ecommerce.infrastructure.redis;

import java.time.Duration;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Redis 配置：RedisTemplate + dashboardStats cache TTL（5 分鐘）
 */
@Configuration
public class RedisConfig {

    static final Duration DASHBOARD_STATS_TTL = Duration.ofMinutes(5);
    static final Duration RATING_STATS_TTL = Duration.ofMinutes(5);

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(buildObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 為 dashboardStats／ratingStats cache 設定獨立 TTL（5 分鐘）與 Jackson JSON 序列化。
     * 僅在 spring.cache.type=redis（或 auto-detect Redis）時生效；
     * spring.cache.type=simple 時，Spring Boot 不會建立 RedisCacheManager，此 customizer 不被呼叫。
     *
     * 🔴 Sprint 285（DEF-390）：ratingStats 原本沒有在這裡註冊，會落回 Spring Boot 預設的
     * JdkSerializationRedisSerializer；ReviewDto.RatingStats 沒有 implements Serializable，
     * 導致 ReviewService.getRatingStats 的 @Cacheable 每次寫入快取都拋
     * SerializationException，端點對任何商品／房源一律 500（E-9900）。真實瀏覽器＋真實
     * Redis 走查才發現——IntegrationTestConfiguration 把 Redis 換成 mock，純讀碼也看不出來。
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> {
            GenericJackson2JsonRedisSerializer jsonSerializer =
                    new GenericJackson2JsonRedisSerializer(buildObjectMapper());

            RedisCacheConfiguration jsonConfig = RedisCacheConfiguration.defaultCacheConfig()
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(jsonSerializer))
                    .disableCachingNullValues();

            builder.withCacheConfiguration("dashboardStats", jsonConfig.entryTtl(DASHBOARD_STATS_TTL));
            builder.withCacheConfiguration("ratingStats", jsonConfig.entryTtl(RATING_STATS_TTL));
        };
    }

    private ObjectMapper buildObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return objectMapper;
    }
}
