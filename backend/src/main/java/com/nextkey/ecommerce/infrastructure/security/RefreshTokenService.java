package com.nextkey.ecommerce.infrastructure.security;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    // TTL 直接寫進腳本而不當參數傳：參數會被值序列化器加上 JSON 引號，Redis 的 EX 不接受
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then\n"
            + "  redis.call('SET', KEYS[1], ARGV[2], 'EX', " + DEFAULT_TTL.toSeconds() + ")\n"
            + "  return 1\n"
            + "end\n"
            + "return 0", Long.class);

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
     * 原子地把 Refresh Token 由「有效」轉為「已使用」（rotation）。與 {@link #blacklistRefreshToken} 不同，
     * 此方法保留 key 並標記狀態，而非直接刪除，讓同一個 token 之後若再被使用可被
     * {@link #isRefreshTokenReused} 偵測為重放攻擊。
     *
     * <p><b>「比對狀態」與「改寫狀態」必須是同一個 Redis 指令</b>（Sprint 213）：先 {@link #isRefreshTokenValid}
     * 再單獨寫入「已使用」的兩步做法，讓同時到達的多個請求全部通過檢查、各自換發出新的 Refresh Token
     * （實測 16 個併發請求 16 個成功），輪替與重放偵測整個被繞過。這裡用 Lua 腳本讓 Redis 單執行緒保證
     * 只有一個呼叫者看得到「有效」。
     *
     * @param userId 用戶 ID
     * @param refreshToken 準備用來換發新 token 的舊 Refresh Token 字串
     * @return {@code true} 表示由這次呼叫完成輪替（呼叫端可以換發）；{@code false} 表示 token 在這之前已被
     *         別的請求輪替或撤銷，呼叫端不可換發
     */
    public boolean tryRotateRefreshToken(final UUID userId, final String refreshToken) {
        // 參數交給 RedisTemplate 的值序列化器處理，才會與 storeRefreshToken 寫入的位元組完全一致
        // （生產環境以 JSON 序列化，字串會帶引號，直接在腳本裡寫字面值比對不到）
        Long rotated = redisTemplate.execute(ROTATE_SCRIPT,
                Collections.singletonList(buildKey(userId, refreshToken)), STATUS_VALID, STATUS_USED);
        boolean won = Long.valueOf(1L).equals(rotated);
        log.debug("Rotate refresh token for user: {}, won: {}", userId, won);
        return won;
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
