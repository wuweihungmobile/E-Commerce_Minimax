package com.nextkey.ecommerce.integration;

import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * 整合測試配置
 * 使用 Mock Redis 來避免需要真實的 Redis 連接
 */
@TestConfiguration
public class IntegrationTestConfiguration {

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @SuppressWarnings("unchecked")
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate() {
        return (RedisTemplate<String, Object>) Mockito.mock(RedisTemplate.class);
    }

    @Bean
    @Primary
    public RefreshTokenService refreshTokenService() {
        RefreshTokenService mockService = Mockito.mock(RefreshTokenService.class);

        // Mock 所有的方法，讓它們不回報錯誤
        doNothing().when(mockService).storeRefreshToken(any(), anyString());
        when(mockService.isRefreshTokenValid(any(), anyString())).thenReturn(true);
        doNothing().when(mockService).blacklistRefreshToken(any(), anyString());
        doNothing().when(mockService).blacklistAllRefreshTokens(any());

        return mockService;
    }
}
