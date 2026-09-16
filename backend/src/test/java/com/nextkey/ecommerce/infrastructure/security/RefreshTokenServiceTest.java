package com.nextkey.ecommerce.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * RefreshTokenService 單元測試（Sprint 167，DEF-219）。
 *
 * <p>背景：{@code refreshToken()} 先前換發新 token 後從未讓舊 token 失效，同一個
 * refresh token 可在 7 天效期內被重複使用無限次。本輪新增 rotation（換發後立即
 * 標記舊 token 為已使用）+ 重放偵測（{@link #isRefreshTokenReused}）機制，這裡直接
 * 針對 Redis 互動驗證狀態轉換是否正確。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService 單元測試（Sprint 167，DEF-219）")
class RefreshTokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private RefreshTokenService refreshTokenService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TOKEN = "some-refresh-token-value";

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(redisTemplate);
    }

    @Test
    @DisplayName("storeRefreshToken：以 valid 狀態寫入 Redis")
    void storeRefreshToken_writesValidStatus() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        refreshTokenService.storeRefreshToken(USER_ID, TOKEN);

        verify(valueOperations).set(anyString(), eq("valid"), eq(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("isRefreshTokenValid：值為 valid 時回傳 true")
    void isRefreshTokenValid_whenValid_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("valid");

        assertThat(refreshTokenService.isRefreshTokenValid(USER_ID, TOKEN)).isTrue();
    }

    @Test
    @DisplayName("isRefreshTokenValid：值為 used（已被換發過）時回傳 false")
    void isRefreshTokenValid_whenUsed_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("used");

        assertThat(refreshTokenService.isRefreshTokenValid(USER_ID, TOKEN)).isFalse();
    }

    @Test
    @DisplayName("isRefreshTokenValid：key 不存在時回傳 false")
    void isRefreshTokenValid_whenAbsent_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThat(refreshTokenService.isRefreshTokenValid(USER_ID, TOKEN)).isFalse();
    }

    @Test
    @DisplayName("isRefreshTokenReused：值為 used 時回傳 true（DEF-219 重放偵測）")
    void isRefreshTokenReused_whenUsed_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("used");

        assertThat(refreshTokenService.isRefreshTokenReused(USER_ID, TOKEN)).isTrue();
    }

    @Test
    @DisplayName("isRefreshTokenReused：值為 valid 時回傳 false")
    void isRefreshTokenReused_whenValid_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("valid");

        assertThat(refreshTokenService.isRefreshTokenReused(USER_ID, TOKEN)).isFalse();
    }

    @Test
    @DisplayName("isRefreshTokenReused：key 不存在時回傳 false")
    void isRefreshTokenReused_whenAbsent_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThat(refreshTokenService.isRefreshTokenReused(USER_ID, TOKEN)).isFalse();
    }

    @Test
    @DisplayName("rotateRefreshToken：將舊 token 狀態改為 used（DEF-219 rotation）")
    void rotateRefreshToken_marksTokenUsed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        refreshTokenService.rotateRefreshToken(USER_ID, TOKEN);

        verify(valueOperations).set(anyString(), eq("used"), eq(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("blacklistRefreshToken：直接刪除 key（非 rotation，用於 logout）")
    void blacklistRefreshToken_deletesKey() {
        String key = "refresh_token:" + USER_ID + ":" + expectedTokenId();
        when(redisTemplate.delete(key)).thenReturn(true);

        refreshTokenService.blacklistRefreshToken(USER_ID, TOKEN);

        verify(redisTemplate).delete(key);
    }

    private String expectedTokenId() {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(TOKEN.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String base64Hash = java.util.Base64.getEncoder().encodeToString(hash);
            return base64Hash.substring(0, Math.min(16, base64Hash.length()));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
