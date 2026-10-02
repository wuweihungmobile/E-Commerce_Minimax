package com.nextkey.ecommerce.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.nextkey.ecommerce.api.dto.AuthResponse;
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
 * Sprint 230（DEF-315）：登入後<b>立刻</b>換發（同一秒內），舊 Refresh Token 仍然只能用一次。
 *
 * <p>Sprint 223 用打包 JAR 實測：{@code JwtTokenService.generateRefreshToken} 產生的 JWT 沒有 {@code jti}、{@code iat} 只到秒，
 * 同一秒內簽發的兩顆 token 位元組完全相同。換發出的新 token 若與舊 token 在同一秒簽發，兩者在 Redis 的 key 相同——
 * 輪替把 key 標成 {@code used} 之後，換發新 token 的 {@code storeRefreshToken} 又把同一個 key 寫回 {@code valid}，
 * 重放偵測與 DEF-291 的原子輪替都被繞過（同一秒內 login→refresh→以舊 token 重放回 200；16 個併發 refresh 成功 2～5 個）。
 *
 * <p>為什麼 DEF-291 的併發測試（{@link AuthServiceRefreshConcurrencyIntegrationTest}）沒抓到：它把 {@link JwtTokenService}
 * <b>mock 掉</b>，{@code generateRefreshToken} 每次回傳 {@code "refresh-new-" + UUID}，永遠不會產生「與舊 token 相同」的新 token。
 * 這裡用<b>真實的</b> {@link JwtTokenService} 與真實 Redis（序列化沿用生產的 {@link RedisConfig}）；
 * 不啟動 Spring context（{@code IntegrationTestConfiguration} 把 Redis 與 {@link RefreshTokenService} 換成 mock）。
 * 需求：執行前須先 {@code make test-db-up}。
 */
@DisplayName("IT-REFRESH-SAME-SECOND: 登入後立刻換發與重放（真 JwtTokenService＋真 Redis）")
class AuthServiceRefreshSameSecondIntegrationTest {

    private static final String JWT_SECRET = "same-second-it-secret-key-that-is-at-least-256-bits-long-0123456789";
    private static final long ACCESS_TOKEN_EXPIRATION = 900_000L;
    private static final long REFRESH_TOKEN_EXPIRATION = 7L * 24 * 60 * 60 * 1000L;
    private static final int CONCURRENT_REQUESTS = 16;
    /** 每一輪都是「登入→立刻換發」，同一秒內發生的機率極高；多輪讓修復前必然出現同一秒的情況，不靠運氣。 */
    private static final int ROUNDS = 20;

    private static LettuceConnectionFactory connectionFactory;
    private static RedisTemplate<String, Object> redisTemplate;

    private final UUID userId = UUID.randomUUID();
    private User user;
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
        user = User.builder()
                .email("refresh-same-second-" + userId + "@example.com")
                .passwordHash("$2a$10$encodedHash")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(tenantRepository.findById(any())).thenReturn(Optional.empty());
        TenantMemberRepository tenantMemberRepository = mock(TenantMemberRepository.class);
        when(tenantMemberRepository.findByUserIdAndStatus(any(), any())).thenReturn(List.of());

        authService = new AuthService(userRepository, tenantRepository, tenantMemberRepository,
                mock(PasswordEncoder.class), new JwtTokenService(JWT_SECRET, ACCESS_TOKEN_EXPIRATION,
                        REFRESH_TOKEN_EXPIRATION), refreshTokenService, mock(LoginAttemptService.class),
                mock(AccountSecurityService.class));
    }

    @AfterEach
    void cleanUp() {
        refreshTokenService.blacklistAllRefreshTokens(userId);
    }

    @Test
    @DisplayName("登入後立刻換發：新 token 與舊 token 不同；以舊 token 重放 → E-1003，並撤銷該使用者所有 session（重複 20 輪）")
    void immediateRefreshThenReplay_isRejectedAndRevokesEverySession() {
        for (int round = 0; round < ROUNDS; round++) {
            AuthResponse login = authService.completeLogin(user);
            String first = login.getRefreshToken();

            AuthResponse refreshed = authService.refreshToken(request(first));
            String second = refreshed.getRefreshToken();

            assertThat(second)
                    .as("round %d：換發出的新 token 不可與舊 token 相同——相同就會共用 Redis key，「已使用」被寫回「有效」", round)
                    .isNotEqualTo(first);
            assertThatThrownBy(() -> authService.refreshToken(request(first)))
                    .as("round %d：舊 token 只能用一次，重放必須被拒絕", round)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.E_1003);
            assertThatThrownBy(() -> authService.refreshToken(request(second)))
                    .as("round %d：偵測到重放，換發出的新 token 也已被撤銷（強制全裝置重新登入）", round)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.E_1003);
        }
    }

    @Test
    @DisplayName("登入後立刻有 16 個請求同時用同一個 token 換發 → 恰好 1 個成功，其餘一律 E-1003（重複 20 輪）")
    void concurrentRefreshRightAfterLogin_succeedsExactlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String token = authService.completeLogin(user).getRefreshToken();

                List<ErrorCode> failures = new ArrayList<>();
                int successes = race(pool, token, failures);

                assertThat(successes)
                        .as("round %d：同一個 token 只能換發出一組新 token（成功 %d 個、失敗 %s）", round, successes, failures)
                        .isEqualTo(1);
                assertThat(failures)
                        .as("round %d：落敗者必須是被明確拒絕（E-1003）", round)
                        .containsOnly(ErrorCode.E_1003);
                refreshTokenService.blacklistAllRefreshTokens(userId);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private int race(final ExecutorService pool, final String token, final List<ErrorCode> failures) throws Exception {
        CyclicBarrier startLine = new CyclicBarrier(CONCURRENT_REQUESTS);
        RefreshTokenRequest request = request(token);
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
        for (Future<ErrorCode> future : futures) {
            ErrorCode code = future.get();
            if (code == null) {
                successes++;
            } else {
                failures.add(code);
            }
        }
        return successes;
    }

    private static RefreshTokenRequest request(final String token) {
        return RefreshTokenRequest.builder().refreshToken(token).build();
    }
}
