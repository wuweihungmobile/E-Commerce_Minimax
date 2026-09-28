package com.nextkey.ecommerce.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * LoginAttemptService 單元測試（Sprint 168，DEF-220；Sprint 214，DEF-293 改為原子計入）。
 *
 * <p>背景：{@code AuthService.login()} 先前對任一 email 的密碼嘗試次數完全沒有限制。
 * 本服務以 Redis 計數器追蹤嘗試次數，超過門檻（5 次）後在 15 分鐘窗口內鎖定該帳號。
 * Lua script 本身的原子性與 TTL 行為需要真實 Redis 才驗證得了，見
 * {@code LoginAttemptServiceIntegrationTest}；這裡只釘住門檻邊界與呼叫參數。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginAttemptService 單元測試（Sprint 168，DEF-220；Sprint 214，DEF-293）")
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private LoginAttemptService loginAttemptService;

    private static final String EMAIL = "buyer@example.com";
    private static final String KEY = "login_attempt:" + EMAIL;
    private static final String WINDOW_SECONDS = "900";

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(redisTemplate);
    }

    private void stubCountReturns(final Long count) {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(KEY)), eq(WINDOW_SECONDS)))
                .thenReturn(count);
    }

    @Test
    @DisplayName("countAttemptAndCheckLocked：第 1 次嘗試不鎖定")
    void countAttempt_firstAttempt_notLocked() {
        stubCountReturns(1L);

        assertThat(loginAttemptService.countAttemptAndCheckLocked(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("countAttemptAndCheckLocked：第 5 次（門檻本身）仍可驗證密碼，不鎖定——5 次機會都要給")
    void countAttempt_atThreshold_notLocked() {
        stubCountReturns(5L);

        assertThat(loginAttemptService.countAttemptAndCheckLocked(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("countAttemptAndCheckLocked：第 6 次起鎖定，不得再驗證密碼")
    void countAttempt_aboveThreshold_locked() {
        stubCountReturns(6L);

        assertThat(loginAttemptService.countAttemptAndCheckLocked(EMAIL)).isTrue();
    }

    @Test
    @DisplayName("countAttemptAndCheckLocked：Redis 沒有回傳計數（null）時視為未鎖定，不擋登入")
    void countAttempt_nullResult_notLocked() {
        stubCountReturns(null);

        assertThat(loginAttemptService.countAttemptAndCheckLocked(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("countAttemptAndCheckLocked：以 email 為 key、15 分鐘（900 秒）為 TTL 參數執行 script")
    void countAttempt_usesEmailKeyAndFifteenMinuteWindow() {
        stubCountReturns(1L);

        loginAttemptService.countAttemptAndCheckLocked(EMAIL);

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)), eq(WINDOW_SECONDS));
    }

    @Test
    @DisplayName("resetAttempts：刪除嘗試計數 key")
    void resetAttempts_deletesKey() {
        loginAttemptService.resetAttempts(EMAIL);

        verify(redisTemplate).delete(KEY);
    }
}
