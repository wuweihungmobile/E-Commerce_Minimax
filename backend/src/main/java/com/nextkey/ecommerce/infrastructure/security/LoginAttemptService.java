package com.nextkey.ecommerce.infrastructure.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 登入失敗次數追蹤與帳號暫時鎖定（Sprint 168，DEF-220）。
 *
 * <p>背景：{@code AuthService.login()} 先前對任一 email 的密碼嘗試次數完全沒有限制，
 * 攻擊者可對已知帳號做無限次密碼猜測（brute force / credential stuffing）。此服務以
 * Redis 計數器（比照 {@link RefreshTokenService} 的 key-per-email 慣例）追蹤連續失敗
 * 次數，達門檻後在時間窗內拒絕該帳號的任何登入嘗試，不論密碼是否正確。
 *
 * <p>用 {@link StringRedisTemplate}（而非既有 {@code RedisTemplate<String, Object>}）
 * 是因為 {@code RedisConfig} 的 value serializer 是 {@code GenericJackson2JsonRedisSerializer}
 * （帶 default typing），會把數字包成 JSON 字串存入 Redis，原生 {@code INCR} 指令要求的是
 * 純整數字串，兩者不相容；{@code StringRedisTemplate} 才能讓 {@code increment()} 對應到
 * Redis 原生 {@code INCR}（單一計數器自增本身即為原子操作，不需要像
 * {@link com.nextkey.ecommerce.api.filter.RateLimitFilter} 那樣的多步驟 Lua script）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "login_attempt:";
    private static final long MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);

    /**
     * 記錄一次登入失敗（帳號不存在或密碼錯誤皆計入，避免攻擊者用不存在的 email
     * 繞過計數，也避免因回應差異洩漏帳號是否存在）。
     * @param email 登入請求中使用的 email（與 {@code UserRepository.findByEmailAndStatus} 同一個值，未正規化大小寫）
     */
    public void recordFailedAttempt(final String email) {
        String key = buildKey(email);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, LOCKOUT_WINDOW);
        }
        log.warn("Failed login attempt recorded for email: {}, count: {}", email, count);
    }

    /**
     * 檢查該 email 是否因連續失敗次數達門檻而暫時鎖定。
     * @param email 登入請求中使用的 email
     * @return true 表示已鎖定，應拒絕本次登入嘗試（不應繼續驗證密碼）
     */
    public boolean isLocked(final String email) {
        String value = redisTemplate.opsForValue().get(buildKey(email));
        if (value == null) {
            return false;
        }
        return Long.parseLong(value) >= MAX_FAILED_ATTEMPTS;
    }

    /**
     * 登入成功後重設失敗計數。
     * @param email 登入請求中使用的 email
     */
    public void resetAttempts(final String email) {
        redisTemplate.delete(buildKey(email));
    }

    private String buildKey(final String email) {
        return KEY_PREFIX + email;
    }
}
