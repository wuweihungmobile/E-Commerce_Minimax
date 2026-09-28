package com.nextkey.ecommerce.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nextkey.ecommerce.api.dto.RefreshTokenRequest;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.redis.RedisConfig;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.infrastructure.security.LoginAttemptService;
import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * Sprint 213：同一個 Refresh Token 被「同時」拿來換發時，只能有一個請求成功（DEF-219 輪替機制的併發缺口）。
 *
 * <p>DEF-219 的重放偵測只擋「先後」兩次使用（第一次換發完成、標記為已使用之後，第二次才會被認出）。
 * 但檢查「是否有效」與標記「已使用」是兩個獨立的 Redis 指令，兩個請求若同時通過檢查，就會各自換發出一組新的
 * Refresh Token——一個被竊取的 token 與其真正的擁有者搶在同一瞬間使用，兩邊都拿得到新的有效 token，
 * 輪替與重放偵測整個被繞過。
 *
 * <p>不啟動 Spring context：{@code IntegrationTestConfiguration} 把 Redis 與 {@link RefreshTokenService}
 * 整個換成 mock，用它驗證不了 Redis 的原子性。這裡直接連 {@code make test-db-up} 起的真實 Redis，
 * 序列化設定沿用生產的 {@link RedisConfig}（值以 JSON 序列化，會影響任何比對原始字串的寫法）。
 * 需求：執行前須先 {@code make test-db-up}。
 */
@DisplayName("IT-REFRESH-RACE: 同一 Refresh Token 併發換發只能成功一次（真 Redis）")
class AuthServiceRefreshConcurrencyIntegrationTest {

    private static final int CONCURRENT_REQUESTS = 16;
    /** 單次競爭的窗口只有幾個 Redis 往返，重複多輪才能讓「修復前必然出現雙重成功」不靠運氣。 */
    private static final int ROUNDS = 20;

    private static LettuceConnectionFactory connectionFactory;
    private static RedisTemplate<String, Object> redisTemplate;

    private final UUID userId = UUID.randomUUID();
    private RefreshTokenService refreshTokenService;
    private AuthService authService;

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration("localhost", 6379);
        redisConfig.setPassword(RedisPassword.of("redis-dev-password"));
        connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new RedisConfig().redisTemplate(connectionFactory);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(redisTemplate);

        UserRepository userRepository = mock(UserRepository.class);
        User user = User.builder()
                .email("refresh-race-" + userId + "@example.com")
                .passwordHash("$2a$10$encodedHash")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(tenantRepository.findById(any())).thenReturn(Optional.empty());
        TenantMemberRepository tenantMemberRepository = mock(TenantMemberRepository.class);
        when(tenantMemberRepository.findByUserId(any())).thenReturn(List.of());

        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(jwtTokenService.validateToken(any())).thenReturn(true);
        when(jwtTokenService.isTokenExpired(any())).thenReturn(false);
        when(jwtTokenService.getUserId(any())).thenReturn(userId);
        when(jwtTokenService.generateAccessToken(any(), any(), any(), any())).thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken(any()))
                .thenAnswer(invocation -> "refresh-new-" + UUID.randomUUID());

        authService = new AuthService(userRepository, tenantRepository, tenantMemberRepository,
                mock(PasswordEncoder.class), jwtTokenService, refreshTokenService,
                mock(LoginAttemptService.class), mock(AccountSecurityService.class));
    }

    @AfterEach
    void cleanUp() {
        refreshTokenService.blacklistAllRefreshTokens(userId);
    }

    @Test
    @DisplayName("16 個請求同時用同一個 Refresh Token 換發，恰好 1 個成功，其餘一律 E-1003（重複 20 輪）")
    void concurrentRefreshWithSameTokenSucceedsExactlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String token = "refresh-old-" + UUID.randomUUID();
                refreshTokenService.storeRefreshToken(userId, token);

                RaceOutcome outcome = raceRefresh(pool, token);

                assertThat(outcome.successes())
                        .as("round %d：同一個 token 只能換發出一組新 token（成功 %d 個、失敗 %s）",
                                round, outcome.successes(), outcome.failureCodes())
                        .isEqualTo(1);
                assertThat(outcome.failureCodes())
                        .as("round %d：落敗者必須是被明確拒絕（E-1003），不可是未分類的技術性例外", round)
                        .containsOnly(ErrorCode.E_1003);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private RaceOutcome raceRefresh(final ExecutorService pool, final String token) throws Exception {
        CyclicBarrier startLine = new CyclicBarrier(CONCURRENT_REQUESTS);
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken(token).build();

        List<Future<ErrorCode>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            Callable<ErrorCode> task = () -> {
                startLine.await();
                try {
                    authService.refreshToken(request);
                    return null;
                } catch (BusinessException e) {
                    return e.getErrorCode();
                }
            };
            futures.add(pool.submit(task));
        }

        int successes = 0;
        List<ErrorCode> failures = new ArrayList<>();
        for (Future<ErrorCode> future : futures) {
            ErrorCode code = future.get();
            if (code == null) {
                successes++;
            } else {
                failures.add(code);
            }
        }
        return new RaceOutcome(successes, failures);
    }

    private record RaceOutcome(int successes, List<ErrorCode> failureCodes) {
    }
}
