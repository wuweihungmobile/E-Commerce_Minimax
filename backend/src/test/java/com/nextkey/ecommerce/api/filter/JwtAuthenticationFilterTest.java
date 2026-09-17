package com.nextkey.ecommerce.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.domain.model.user.RolePermissionMapping;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

/**
 * JwtAuthenticationFilter 單元測試（DEF-222）。
 *
 * <p>{@code JwtTokenService.generateRefreshToken} 只設定 subject/issuedAt/expiration，
 * 不含 email/role/tenantId claim（與 {@code generateAccessToken} 不同）。refresh token
 * 用同一把密鑰簽章，{@code validateToken} 只驗證簽章與效期，無法區分 access/refresh token。
 * 若把 refresh token 當 Bearer token 送出，{@code getRole(jwt)} 回傳 {@code null}，
 * {@code User.UserRole.valueOf(null)} 拋出 {@code NullPointerException}——{@code doFilterInternal}
 * 的 catch 子句只涵蓋 {@code IllegalArgumentException | ClassCastException | JwtException}，
 * 未攔截 {@code NullPointerException}，此 filter 執行於 DispatcherServlet 之前，
 * {@code GlobalExceptionHandler} 攔不到，直接以未攔截例外的形式往外拋。
 */
@DisplayName("DEF-222: JwtAuthenticationFilter 缺 claim 的 token 未攔截例外")
class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "testSecretKeyForJwtTokenGenerationThatIsAtLeast256BitsLong";
    private static final long ACCESS_TOKEN_EXPIRATION = 900_000L;
    private static final long REFRESH_TOKEN_EXPIRATION = 604_800_000L;

    private JwtTokenService jwtTokenService;
    private JwtAuthenticationFilter filter;
    private final FilterChain noopChain = (request, response) -> { };

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(TEST_SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
        filter = new JwtAuthenticationFilter(jwtTokenService, new RolePermissionMapping());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("refresh token（無 role claim）當 Bearer token 送出時，不得拋出未攔截例外，且不得建立驗證身分")
    void doFilter_refreshTokenAsBearerToken_doesNotThrowAndLeavesUnauthenticated() {
        UUID userId = UUID.randomUUID();
        String refreshToken = jwtTokenService.generateRefreshToken(userId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
        request.addHeader("Authorization", "Bearer " + refreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatCode(() -> filter.doFilter(request, response, noopChain))
                .as("缺 role claim 的合法簽章 token 不應讓過濾器拋出未攔截例外")
                .doesNotThrowAnyException();

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("role claim 缺失時不應建立任何驗證身分")
                .isNull();
    }

    @Test
    @DisplayName("正常 access token（含完整 claim）成功建立驗證身分")
    void doFilter_validAccessToken_authenticatesSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        String accessToken = jwtTokenService.generateAccessToken(
                userId, "buyer@example.com", "BUYER", UUID.randomUUID().toString());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
        request.addHeader("Authorization", "Bearer " + accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, noopChain);

        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(userId);
        assertThat(principal.getRole()).isEqualTo("BUYER");
    }
}
