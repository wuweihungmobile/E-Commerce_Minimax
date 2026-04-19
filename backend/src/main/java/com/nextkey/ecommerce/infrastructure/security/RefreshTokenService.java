package com.nextkey.ecommerce.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Refresh Token 管理服務
 * 處理 Refresh Token 的儲存和驗證（用於 logout 和 refresh）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final Duration DEFAULT_TTL = Duration.ofDays(30); // 預設 30 天，與 Refresh Token 有效期相同

    /**
     * 儲存 Refresh Token
     * @param userId 用戶 ID
     * @param refreshToken Refresh Token 字串
     */
    public void storeRefreshToken(UUID userId, String refreshToken) {
        String key = buildKey(userId, refreshToken);
        redisTemplate.opsForValue().set(key, "valid", DEFAULT_TTL);
        log.debug("Stored refresh token for user: {}", userId);
    }

    /**
     * 驗證 Refresh Token 是否有效
     * @param userId 用戶 ID
     * @param refreshToken Refresh Token 字串
     * @return true if valid, false otherwise
     */
    public boolean isRefreshTokenValid(UUID userId, String refreshToken) {
        String key = buildKey(userId, refreshToken);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 將 Refresh Token 加入黑名單（logout）
     * @param userId 用戶 ID
     * @param refreshToken Refresh Token 字串
     */
    public void blacklistRefreshToken(UUID userId, String refreshToken) {
        String key = buildKey(userId, refreshToken);
        // 刪除 key 等同於將 token 失效
        Boolean deleted = redisTemplate.delete(key);
        log.info("Blacklisted refresh token for user: {}, deleted: {}", userId, deleted);
    }

    /**
     * 將所有 Refresh Token 加入黑名單（logout all devices）
     * @param userId 用戶 ID
     */
    public void blacklistAllRefreshTokens(UUID userId) {
        String pattern = REFRESH_TOKEN_PREFIX + userId + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Blacklisted {} refresh tokens for user: {}", keys.size(), userId);
        }
    }

    /**
     * 從 Refresh Token 中提取 userId
     * 注意：這個方法依賴 JwtTokenService 來解析 JWT
     * @param refreshToken JWT token
     * @return userId if valid, null otherwise
     */
    public UUID extractUserIdFromToken(String refreshToken) {
        try {
            // 這裡需要調用 JwtTokenService，但為了避免循環依賴，我們在 AuthService 中處理
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract userId from refresh token", e);
            return null;
        }
    }

    private String buildKey(UUID userId, String refreshToken) {
        return REFRESH_TOKEN_PREFIX + userId + ":" + extractTokenId(refreshToken);
    }

    /**
     * 從 JWT 中提取一個簡短的 ID（使用 hash 或 JTI claim）
     * 這裡我們使用 token 的最後 8 個字符作為識別符
     */
    private String extractTokenId(String refreshToken) {
        // JWT 格式: header.payload.signature
        // 我們可以使用 signature 的最後部分作為 ID
        if (refreshToken == null || refreshToken.length() < 10) {
            return refreshToken;
        }
        // 使用 token 的 hash 作為 key 的一部分
        return String.valueOf(refreshToken.hashCode());
    }
}
