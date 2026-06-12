package com.nextkey.ecommerce.infrastructure.mq;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Stream MQ 配置
 * Phase 2 使用 Redis Stream 作為訊息佇列
 *
 * 注意：RedisConnectionFactory 由 Spring Boot 自動配置，此處不重複建立
 */
@Configuration
public class RedisStreamConfig {

    public static final String NOTIFICATION_STREAM = "notification:stream";
    public static final String NOTIFICATION_CONSUMER_GROUP = "notification-group";
    public static final String NOTIFICATION_CONSUMER = "notification-consumer";

    /**
     * RedisTemplate for Redis Stream operations
     * 使用 Spring Boot 自動配置的 RedisConnectionFactory
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}