package com.nextkey.ecommerce.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import com.nextkey.ecommerce.infrastructure.redis.RedisConfig;

/**
 * Sprint 213：{@link RefreshTokenService#tryRotateRefreshToken} 的 Lua 腳本在真實 Redis、生產序列化設定下的行為。
 *
 * <p>為什麼不能只用 mock：腳本是拿「值序列化器序列化後的位元組」跟 Redis 內存的值比對，生產環境的值序列化器
 * 是 JSON（字串會帶引號）。若有人日後把腳本改成比對字面值 {@code 'valid'}、或換了序列化器，mock 測試完全看不出來，
 * 真實 Redis 上卻會變成「永遠比對不到 → 所有 Refresh Token 都換發失敗」。
 *
 * <p>比照 {@code AccountTokenServiceIntegrationTest}：不啟動 Spring context。需求：執行前須先 {@code make test-db-up}。
 */
@DisplayName("IT-REFRESH-ROTATE: Refresh Token 原子輪替（真 Redis，生產序列化設定）")
class RefreshTokenServiceIntegrationTest {

    private static final long THIRTY_DAYS_SECONDS = TimeUnit.DAYS.toSeconds(30);
    private static final long TTL_TOLERANCE_SECONDS = 60L;

    private static LettuceConnectionFactory connectionFactory;
    private static RedisTemplate<String, Object> redisTemplate;
    private static RefreshTokenService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration("localhost", 6379);
        redisConfig.setPassword(RedisPassword.of("redis-dev-password"));
        connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new RedisConfig().redisTemplate(connectionFactory);
        service = new RefreshTokenService(redisTemplate);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @AfterEach
    void cleanUp() {
        service.blacklistAllRefreshTokens(userId);
    }

    @Test
    @DisplayName("有效的 token 輪替成功一次：之後不再有效、且被認得是「已使用」（重放偵測所需）")
    void validTokenRotatesOnce() {
        String token = "refresh-" + UUID.randomUUID();
        service.storeRefreshToken(userId, token);

        assertThat(service.tryRotateRefreshToken(userId, token)).isTrue();

        assertThat(service.isRefreshTokenValid(userId, token)).isFalse();
        assertThat(service.isRefreshTokenReused(userId, token)).isTrue();
    }

    @Test
    @DisplayName("同一個 token 第二次輪替一律失敗（不論先後）")
    void secondRotationFails() {
        String token = "refresh-" + UUID.randomUUID();
        service.storeRefreshToken(userId, token);
        assertThat(service.tryRotateRefreshToken(userId, token)).isTrue();

        assertThat(service.tryRotateRefreshToken(userId, token)).isFalse();
    }

    @Test
    @DisplayName("已被撤銷（key 不存在）的 token 輪替失敗，且不會被腳本重新建立成「已使用」")
    void revokedTokenIsNotResurrected() {
        String token = "refresh-" + UUID.randomUUID();
        service.storeRefreshToken(userId, token);
        service.blacklistRefreshToken(userId, token);

        assertThat(service.tryRotateRefreshToken(userId, token)).isFalse();

        // 若腳本無條件寫入，撤銷過的 token 會變成「已使用」，之後再出現一次就會被當成重放而撤銷全部 session
        assertThat(service.isRefreshTokenReused(userId, token)).isFalse();
        assertThat(redisTemplate.keys("refresh_token:" + userId + ":*")).isEmpty();
    }

    @Test
    @DisplayName("輪替後保留 30 天 TTL（不因改寫而變成永不過期，也不縮短）")
    void rotationKeepsThirtyDayTtl() {
        String token = "refresh-" + UUID.randomUUID();
        service.storeRefreshToken(userId, token);
        service.tryRotateRefreshToken(userId, token);

        Set<String> keys = redisTemplate.keys("refresh_token:" + userId + ":*");
        assertThat(keys).hasSize(1);
        Long ttl = redisTemplate.getExpire(keys.iterator().next(), TimeUnit.SECONDS);
        assertThat(ttl).isBetween(THIRTY_DAYS_SECONDS - TTL_TOLERANCE_SECONDS, THIRTY_DAYS_SECONDS);
    }
}
