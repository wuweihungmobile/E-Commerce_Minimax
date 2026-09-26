package com.nextkey.ecommerce.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 一次性連結 token（重設密碼、Email 驗證）的簽發與消耗（Sprint 204，FRD BR-M03-003）。
 *
 * <p>為什麼放 Redis 而不是資料表：token 天生短命（30 分鐘／24 小時）、要能原子地「取出並作廢」、
 * 過期就該消失——這正是 Redis 的 TTL＋{@code GETDEL} 擅長的，不需要新的 Flyway 遷移與實體。
 * Redis 被清空的代價只是使用者要重新申請一次連結。
 *
 * <p>安全性：
 * <ul>
 *   <li>token 是 32 位元組 {@link SecureRandom}，Base64URL 編碼；Redis 只存它的 SHA-256，
 *       <b>不存原文</b>——即使 Redis 內容外洩也拿不到可用連結。</li>
 *   <li>消耗用 {@code GETDEL}：取出與作廢是同一個原子操作，兩個併發請求只有一個會成功。</li>
 *   <li>每個（用途, 會員）同時只有一個有效 token：簽發新的會作廢舊的。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AccountTokenService {

    /** 用途與其有效期（FRD BR-M03-003）。 */
    public enum Purpose {
        PASSWORD_RESET(Duration.ofMinutes(30)),
        EMAIL_VERIFY(Duration.ofHours(24));

        private final Duration ttl;

        Purpose(final Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getTtl() {
            return ttl;
        }
    }

    /** 同一（用途, 會員）兩次寄信之間的最短間隔，避免被用來灌爆他人信箱。 */
    public static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);

    private static final String TOKEN_PREFIX = "account_token:";
    private static final String LATEST_PREFIX = "account_token_latest:";
    private static final String COOLDOWN_PREFIX = "account_token_cooldown:";
    private static final int TOKEN_BYTES = 32;
    /** Base64URL 編碼 32 位元組為 43 字元；超過此長度的輸入必不是我們簽發的，直接拒絕，不進 Redis。 */
    private static final int MAX_TOKEN_LENGTH = 128;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 為（用途, 會員）簽發新 token，並使先前簽發的作廢。
     *
     * @return token 原文；只在此刻存在於記憶體，之後只能靠使用者手上的連結
     */
    public String issue(final Purpose purpose, final UUID userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = sha256Hex(token);

        // 先寫新 token 再換指標、最後刪舊的：任何時刻使用者手上最新那封信的連結都是有效的
        redisTemplate.opsForValue().set(tokenKey(purpose, tokenHash), userId.toString(), purpose.getTtl());
        String previousHash = redisTemplate.opsForValue().getAndSet(latestKey(purpose, userId), tokenHash);
        redisTemplate.expire(latestKey(purpose, userId), purpose.getTtl());
        if (previousHash != null && !previousHash.equals(tokenHash)) {
            redisTemplate.delete(tokenKey(purpose, previousHash));
        }
        return token;
    }

    /**
     * 消耗 token：原子取出並作廢。
     *
     * @return 該 token 所屬的會員；無效、過期、已使用、被新 token 取代、或格式不對，一律回空（不區分原因，
     *         不讓呼叫端從回應推測 token 曾經存在）
     */
    public Optional<UUID> consume(final Purpose purpose, final String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        String userId = redisTemplate.opsForValue().getAndDelete(tokenKey(purpose, sha256Hex(token)));
        if (userId == null) {
            return Optional.empty();
        }
        UUID id = UUID.fromString(userId);
        redisTemplate.delete(latestKey(purpose, id));
        return Optional.of(id);
    }

    /**
     * 嘗試取得「這次可以寄信」的名額：同一（用途, 會員）在 {@link #SEND_COOLDOWN} 內只有第一次會成功。
     *
     * @return {@code true} 可以寄；{@code false} 冷卻中，呼叫端應靜默略過（回應不可因此與可寄時不同）
     */
    public boolean tryAcquireSendSlot(final Purpose purpose, final UUID userId) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_PREFIX + purpose + ":" + userId, "1", SEND_COOLDOWN);
        return Boolean.TRUE.equals(acquired);
    }

    /** 使該會員該用途目前有效的 token 作廢（例如密碼已重設，其他尚未使用的重設連結不該再能用）。 */
    public void invalidate(final Purpose purpose, final UUID userId) {
        String hash = redisTemplate.opsForValue().getAndDelete(latestKey(purpose, userId));
        if (hash != null) {
            redisTemplate.delete(tokenKey(purpose, hash));
        }
    }

    private static String tokenKey(final Purpose purpose, final String tokenHash) {
        return TOKEN_PREFIX + purpose + ":" + tokenHash;
    }

    private static String latestKey(final Purpose purpose, final UUID userId) {
        return LATEST_PREFIX + purpose + ":" + userId;
    }

    private static String sha256Hex(final String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // 每個 Java 平台都保證提供 SHA-256（JCA 規範要求），走到這裡代表執行環境本身壞了
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
