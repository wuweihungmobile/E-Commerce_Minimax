package com.nextkey.ecommerce.infrastructure.security;

import java.time.Duration;
import java.util.Collections;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
 * 純整數字串，兩者不相容。
 *
 * <p><b>DEF-293（Sprint 214）</b>：原本的「先 {@code isLocked} 檢查、驗完密碼才 {@code recordFailedAttempt}
 * 計入」中間隔著一次 bcrypt，同時到達的 N 個請求會全部在任何一個計入之前通過檢查，各得一次猜測機會，門檻只擋得住
 * 依序的猜測。改為 {@link #countAttemptAndCheckLocked}：在 Redis 內以單支 Lua script 一步完成「計入本次嘗試 +
 * 判斷是否超過門檻」，呼叫端<b>先計入、再驗密碼</b>，登入成功才 {@link #resetAttempts}。script 同時把「無 TTL 才補
 * {@code EXPIRE}」放在同一步，避免 {@code INCR} 成功但 {@code EXPIRE} 沒執行到而留下永不過期的計數（等於永久鎖定）。
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
     * TTL 以「目前沒有 TTL」（{@code TTL < 0}）而非「count == 1」判斷是否設定：同樣涵蓋第一次計入，
     * 並讓先前（舊版兩指令寫法）遺留、沒有 TTL 的 key 在下一次計入時自行補上 TTL。
     * 秒數以 ARGV 傳入（{@code StringRedisTemplate} 的參數序列化器是純字串，沒有 JSON 引號問題）。
     */
    private static final DefaultRedisScript<Long> COUNT_ATTEMPT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1])\n"
            + "if redis.call('TTL', KEYS[1]) < 0 then\n"
            + "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))\n"
            + "end\n"
            + "return count", Long.class);

    /**
     * 把本次登入嘗試計入該 email 的計數，並回傳計入後是否已超過門檻（原子操作，DEF-293）。
     *
     * <p>帳號不存在或密碼錯誤皆計入（避免攻擊者用不存在的 email 繞過計數，也避免因回應差異洩漏帳號是否存在）；
     * 因為是先計入再驗密碼，呼叫端不需要（也不應該）在驗證失敗後再記錄一次。
     * @param email 登入請求中使用的 email（與 {@code UserRepository.findByEmailAndStatus} 同一個值，未正規化大小寫）
     * @return true 表示計入後已超過門檻而鎖定，應拒絕本次登入嘗試（不應繼續驗證密碼）；
     *         Redis 沒有回傳計數時視為未鎖定
     */
    public boolean countAttemptAndCheckLocked(final String email) {
        Long count = redisTemplate.execute(COUNT_ATTEMPT_SCRIPT,
                Collections.singletonList(buildKey(email)),
                String.valueOf(LOCKOUT_WINDOW.toSeconds()));
        if (count == null) {
            return false;
        }
        log.debug("Login attempt counted for email: {}, count: {}", email, count);
        return count > MAX_FAILED_ATTEMPTS;
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
