package com.nextkey.ecommerce.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * LoginAttemptService 單元測試（Sprint 168，DEF-220）。
 *
 * <p>背景：{@code AuthService.login()} 先前對任一 email 的密碼嘗試次數完全沒有限制。
 * 本服務以 Redis 計數器追蹤連續失敗次數，達門檻（5 次）後在 15 分鐘窗口內鎖定該帳號。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginAttemptService 單元測試（Sprint 168，DEF-220）")
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginAttemptService loginAttemptService;

    private static final String EMAIL = "buyer@example.com";
    private static final String KEY = "login_attempt:" + EMAIL;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(redisTemplate);
    }

    @Test
    @DisplayName("recordFailedAttempt：第一次失敗時遞增並設定 15 分鐘 TTL")
    void recordFailedAttempt_firstFailure_incrementsAndSetsExpiry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(1L);

        loginAttemptService.recordFailedAttempt(EMAIL);

        verify(redisTemplate).expire(KEY, Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("recordFailedAttempt：非第一次失敗時不重設 TTL（維持原本的鎖定窗口）")
    void recordFailedAttempt_subsequentFailure_doesNotResetExpiry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(2L);

        loginAttemptService.recordFailedAttempt(EMAIL);

        verify(redisTemplate, never()).expire(eq(KEY), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("isLocked：失敗次數達門檻（5 次）時回傳 true")
    void isLocked_atThreshold_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("5");

        assertThat(loginAttemptService.isLocked(EMAIL)).isTrue();
    }

    @Test
    @DisplayName("isLocked：失敗次數超過門檻時仍回傳 true")
    void isLocked_aboveThreshold_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("9");

        assertThat(loginAttemptService.isLocked(EMAIL)).isTrue();
    }

    @Test
    @DisplayName("isLocked：失敗次數未達門檻時回傳 false")
    void isLocked_belowThreshold_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("4");

        assertThat(loginAttemptService.isLocked(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("isLocked：key 不存在（尚未失敗過或已過期）時回傳 false")
    void isLocked_keyAbsent_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThat(loginAttemptService.isLocked(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("resetAttempts：刪除失敗計數 key")
    void resetAttempts_deletesKey() {
        loginAttemptService.resetAttempts(EMAIL);

        verify(redisTemplate).delete(KEY);
    }
}
