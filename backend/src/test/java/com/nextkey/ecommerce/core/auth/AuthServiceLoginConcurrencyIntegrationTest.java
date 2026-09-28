package com.nextkey.ecommerce.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.infrastructure.security.LoginAttemptService;
import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * Sprint 214：帳號鎖定（DEF-220）在「同時發出的猜測」下必須仍守住門檻。
 *
 * <p>{@code AuthService.login} 的順序是：先檢查是否已鎖定 → 比對密碼（bcrypt，數十到數百毫秒）→ 失敗後才把
 * 這次嘗試計入。檢查與計入之間隔著一次 bcrypt，所以同時到達的 N 個請求會全部在任何一個計入之前通過鎖定檢查，
 * 各自都拿到一次猜測機會——鎖定門檻（5 次）只擋得住「依序」的猜測，擋不住同時發出的。這正是 {@code LoginAttemptService}
 * 設計上要防的跨 IP 分散式猜測（單一 IP 的每分鐘 30 次配額不管用，因為每個 IP 都各有自己的配額）。
 *
 * <p>不啟動 Spring context：{@code IntegrationTestConfiguration} 把 Redis 換成 mock，驗證不了計數的原子性。
 * 這裡直接連 {@code make test-db-up} 起的真實 Redis，並用真實的 bcrypt（窗口大小由它決定，不靠 sleep 模擬）。
 * 需求：執行前須先 {@code make test-db-up}。
 */
@DisplayName("IT-LOGIN-LOCKOUT-RACE: 帳號鎖定在併發猜測下仍守住門檻（真 Redis + 真 bcrypt）")
class AuthServiceLoginConcurrencyIntegrationTest {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int CONCURRENT_REQUESTS = 16;
    private static final int ROUNDS = 10;
    private static final String CORRECT_PASSWORD = "correct-password";
    private static final String WRONG_PASSWORD = "wrong-password";

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static String correctPasswordHash;

    private BCryptPasswordEncoder passwordEncoder;
    private UserRepository userRepository;
    private AuthService authService;
    private final List<String> usedEmails = new ArrayList<>();

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration("localhost", 6379);
        redisConfig.setPassword(RedisPassword.of("redis-dev-password"));
        connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        correctPasswordHash = new BCryptPasswordEncoder().encode(CORRECT_PASSWORD);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        // spy 用來直接量「密碼比對實際執行了幾次」——這才是猜測機會的度量，不能只從錯誤碼推論
        passwordEncoder = spy(new BCryptPasswordEncoder());
        userRepository = mock(UserRepository.class);

        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(tenantRepository.findById(any())).thenReturn(Optional.empty());
        TenantMemberRepository tenantMemberRepository = mock(TenantMemberRepository.class);
        when(tenantMemberRepository.findByUserId(any())).thenReturn(List.of());

        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(jwtTokenService.generateAccessToken(any(), any(), any(), any())).thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken(any())).thenReturn("refresh-token");

        authService = new AuthService(userRepository, tenantRepository, tenantMemberRepository,
                passwordEncoder, jwtTokenService, mock(RefreshTokenService.class),
                new LoginAttemptService(redisTemplate), mock(AccountSecurityService.class));
    }

    @AfterEach
    void cleanUp() {
        usedEmails.forEach(email -> redisTemplate.delete("login_attempt:" + email));
    }

    @Test
    @DisplayName("16 個請求同時猜錯密碼：恰好 5 次密碼比對實際執行（E-1001），其餘一律被鎖定擋下（E-1004）（重複 10 輪）")
    void concurrentWrongPasswordGuessesAreCappedAtThreshold() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String email = newEmailWithActiveUser();
                clearInvocations(passwordEncoder);

                List<ErrorCode> outcomes = raceLogin(pool, email, WRONG_PASSWORD);

                // 猜測機會的直接度量：密碼比對實際跑了幾次。若計入發生在比對之後，即使事後仍回 E-1004，
                // 16 次比對都已經執行過了（其中任何一次猜中就會登入成功），光看錯誤碼分辨不出來。
                verify(passwordEncoder, times(MAX_FAILED_ATTEMPTS)).matches(any(), any());
                assertThat(outcomes)
                        .as("round %d：門檻是 %d 次，同時發出的猜測也不可超過（實際結果 %s）",
                                round, MAX_FAILED_ATTEMPTS, outcomes)
                        .filteredOn(code -> code == ErrorCode.E_1001)
                        .hasSize(MAX_FAILED_ATTEMPTS);
                assertThat(outcomes)
                        .as("round %d：超過門檻的請求必須是被鎖定（E-1004），不可是未分類的技術性例外", round)
                        .filteredOn(code -> code != ErrorCode.E_1001)
                        .hasSize(CONCURRENT_REQUESTS - MAX_FAILED_ATTEMPTS)
                        .containsOnly(ErrorCode.E_1004);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("對不存在的 email 同時猜測也一樣受門檻限制（不存在的 email 與存在的走同一個計數，避免帳號列舉繞過鎖定）")
    void concurrentGuessesAgainstUnknownEmailAreAlsoCapped() throws Exception {
        String email = newEmail();
        when(userRepository.findByEmailAndStatus(email, "ACTIVE")).thenReturn(Optional.empty());

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            List<ErrorCode> outcomes = raceLogin(pool, email, WRONG_PASSWORD);

            assertThat(outcomes)
                    .as("不存在的 email 也只能有 %d 次機會（實際結果 %s）", MAX_FAILED_ATTEMPTS, outcomes)
                    .filteredOn(code -> code == ErrorCode.E_1001)
                    .hasSize(MAX_FAILED_ATTEMPTS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("登入成功會清掉失敗計數：先失敗 4 次、成功 1 次後，還有完整的 5 次機會")
    void successfulLoginClearsFailureCount() {
        String email = newEmailWithActiveUser();

        for (int i = 0; i < MAX_FAILED_ATTEMPTS - 1; i++) {
            assertThat(loginOnce(email, WRONG_PASSWORD)).isEqualTo(ErrorCode.E_1001);
        }
        assertThat(loginOnce(email, CORRECT_PASSWORD)).as("成功登入").isNull();

        for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
            assertThat(loginOnce(email, WRONG_PASSWORD))
                    .as("成功後的第 %d 次猜測：計數應已歸零，仍可比對密碼", i + 1)
                    .isEqualTo(ErrorCode.E_1001);
        }
        assertThat(loginOnce(email, WRONG_PASSWORD)).as("第 6 次才被鎖定").isEqualTo(ErrorCode.E_1004);
    }

    @Test
    @DisplayName("鎖定期間即使密碼正確也拒絕（E-1004），不可讓已鎖定的帳號被猜中後登入")
    void lockedAccountRejectsEvenCorrectPassword() {
        String email = newEmailWithActiveUser();
        for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
            loginOnce(email, WRONG_PASSWORD);
        }

        assertThat(loginOnce(email, CORRECT_PASSWORD)).isEqualTo(ErrorCode.E_1004);
    }

    private String newEmail() {
        String email = "lockout-race-" + UUID.randomUUID() + "@example.com";
        usedEmails.add(email);
        return email;
    }

    private String newEmailWithActiveUser() {
        String email = newEmail();
        User user = User.builder()
                .email(email)
                .passwordHash(correctPasswordHash)
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmailAndStatus(email, "ACTIVE")).thenReturn(Optional.of(user));
        return email;
    }

    /** 成功回傳 null；失敗回傳 {@link BusinessException} 的錯誤碼。 */
    private ErrorCode loginOnce(final String email, final String password) {
        try {
            authService.login(LoginRequest.builder().email(email).password(password).build());
            return null;
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
    }

    private List<ErrorCode> raceLogin(final ExecutorService pool, final String email, final String password)
            throws Exception {
        CyclicBarrier startLine = new CyclicBarrier(CONCURRENT_REQUESTS);

        List<Future<ErrorCode>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            Callable<ErrorCode> task = () -> {
                startLine.await();
                return loginOnce(email, password);
            };
            futures.add(pool.submit(task));
        }

        List<ErrorCode> outcomes = new ArrayList<>();
        for (Future<ErrorCode> future : futures) {
            outcomes.add(future.get());
        }
        return outcomes;
    }
}
