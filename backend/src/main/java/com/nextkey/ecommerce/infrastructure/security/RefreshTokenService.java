package com.nextkey.ecommerce.infrastructure.security;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    // Token 狀態值：VALID 為尚未被換發過的現行 token；USED 為已被拿去換發過新 token 的舊 token
    // （保留 USED 標記而非直接刪除 key，是為了讓 rotation 後的重放攻擊能被 isRefreshTokenReused 偵測到）
    private static final String STATUS_VALID = "valid";
    private static final String STATUS_USED = "used";

    // Token ID extraction
    private static final int TOKEN_ID_LENGTH = 16;
    private static final int TOKEN_SIGNATURE_LENGTH = 43;

    /**
     * 儲存 Refresh Token
     * @param userId 用戶 ID
     * @param refreshToken Refresh Token 字串
     */
    public void storeRefreshToken(final UUID userId, final String refreshToken) {
        String key = buildKey(userId, refreshToken);
        redisTemplate.opsForValue().set(key, STATUS_VALID, DEFAULT_TTL);
        log.debug("Stored refresh token for user: {}", userId);
    }

    /**
     * 驗證 Refresh Token 是否有效（尚未被換發過）
     * @param userId 用戶 ID
     * @param refreshToken Refresh Token 字串
     * @return true if valid, false otherwise
     */
    public boolean isRefreshTokenValid(final UUID userId, final String refreshToken) {
        String key = buildKey(userId, refreshToken);
        return STATUS_VALID.equals(redisTemplate.opsForValue().get(key));
    }

    /**
     * 檢查 Refresh Token 是否為「已被換發過」卻又再次被拿來使用（重放攻擊訊號）
     * @param userId 用戶 ID
     * @param refreshToken Refresh Token 字串
     * @return true 表示偵測到重放
     */
    public boolean isRefreshTokenReused(final UUID userId, final String refreshToken) {
        String key = buildKey(userId, refreshToken);
        return STATUS_USED.equals(redisTemplate.opsForValue().get(key));
    }

    /**
     * 將 Refresh Token 標記為已使用（rotation）。與 {@link #blacklistRefreshToken} 不同，
     * 此方法保留 key 並標記狀態，而非直接刪除，讓同一個 token 之後若再被使用可被
     * {@link #isRefreshTokenReused} 偵測為重放攻擊。
     * @param userId 用戶 ID
     * @param refreshToken 剛被用來換發新 token 的舊 Refresh Token 字串
     */
    public void rotateRefreshToken(final UUID userId, final String refreshToken) {
        String key = buildKey(userId, refreshToken);
        redisTemplate.opsForValue().set(key, STATUS_USED, DEFAULT_TTL);
        log.debug("Rotated (marked used) refresh token for user: {}", userId);
    }

    /**
     * 將 Refresh Token 加入黑名單（logout）
     * @param userId 用戶 ID
     * @param refreshToken Refresh Token 字串
     */
    public void blacklistRefreshToken(final UUID userId, final String refreshToken) {
        String key = buildKey(userId, refreshToken);
        // 刪除 key 等同於將 token 失效
        Boolean deleted = redisTemplate.delete(key);
        log.info("Blacklisted refresh token for user: {}, deleted: {}", userId, deleted);
    }

    /**
     * 將所有 Refresh Token 加入黑名單（logout all devices）
     * @param userId 用戶 ID
     */
    public void blacklistAllRefreshTokens(final UUID userId) {
        String pattern = REFRESH_TOKEN_PREFIX + userId + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Blacklisted {} refresh tokens for user: {}", keys.size(), userId);
        }
    }

    private String buildKey(final UUID userId, final String refreshToken) {
        return REFRESH_TOKEN_PREFIX + userId + ":" + extractTokenId(refreshToken);
    }

    /**
     * 從 JWT 中提取一個簡短的 ID（使用 SHA-256 hash）
     * 使用 SHA-256 確保安全性，避免 hashCode() 的衝突問題
     */
    private String extractTokenId(final String refreshToken) {
        if (refreshToken == null || refreshToken.length() < 10) {
            return refreshToken;
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // 使用 Base64 編碼並取前 16 個字符作為 token ID
            String base64Hash = java.util.Base64.getEncoder().encodeToString(hash);
            return base64Hash.substring(0, Math.min(TOKEN_ID_LENGTH, base64Hash.length()));
        } catch (java.security.NoSuchAlgorithmException e) {
            log.warn("SHA-256 algorithm not available, falling back to substring", e);
            // Fallback: 使用 token 的最後 43 個字符（JWT signature 部分的大約長度）
            return refreshToken.substring(refreshToken.length() - TOKEN_SIGNATURE_LENGTH);
        }
    }
}
