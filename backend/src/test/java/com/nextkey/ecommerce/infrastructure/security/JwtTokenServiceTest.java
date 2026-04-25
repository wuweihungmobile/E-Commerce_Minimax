package com.nextkey.ecommerce.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * UT-M03-006 ~ UT-M03-012: JWT Token 產生/驗證單元測試
 *
 * 測試範圍：
 * - UT-M03-006: JWT Token產生-payload正確
 * - UT-M03-007: JWT Token產生-有效期限30分鐘
 * - UT-M03-008: JWT Token驗證-有效Token通過
 * - UT-M03-009: JWT Token驗證-過期Token失敗
 * - UT-M03-010: JWT Token驗證-篡改Token失敗
 * - UT-M03-011: JWT Refresh Token產生-payload正確
 * - UT-M03-012: JWT Refresh Token有效期限30天
 */
@DisplayName("UT-M03-006 ~ UT-M03-012: JWT Token 產生/驗證")
class JwtTokenServiceTest {

    // JWT Secret - 測試用（生產環境應使用更長的金鑰）
    private static final String TEST_SECRET = "testSecretKeyForJwtTokenGenerationThatIsAtLeast256BitsLong";
    private static final long ACCESS_TOKEN_EXPIRATION = 1800_000L; // 30 分鐘 in milliseconds
    private static final long REFRESH_TOKEN_EXPIRATION = 30L * 24 * 60 * 60 * 1000L; // 30 天 in milliseconds

    private JwtTokenService jwtTokenService;

    // 測試資料
    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_EMAIL = "user@example.com";
    private static final String TEST_ROLE = "BUYER";
    private static final String TEST_TENANT_ID = "tenant-uuid-1";

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(TEST_SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
    }

    // ── UT-M03-006: JWT Token產生-payload正確 ──────────────────────────

    @Test
    @DisplayName("UT-M03-006: JWT Access Token產生-payload包含正確欄位")
    void generateAccessToken_payloadContainsCorrectFields() {
        // Act
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // Assert
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();

        // 解析 payload
        Claims claims = jwtTokenService.getClaims(token);

        assertThat(claims.getSubject()).isEqualTo(TEST_USER_ID.toString());
        assertThat(claims.get("email", String.class)).isEqualTo(TEST_EMAIL);
        assertThat(claims.get("role", String.class)).isEqualTo(TEST_ROLE);
        assertThat(claims.get("tenantId", String.class)).isEqualTo(TEST_TENANT_ID);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    @DisplayName("UT-M03-006: JWT Access Token產生-payload包含所有必要資訊")
    void generateAccessToken_payloadContainsAllNecessaryInfo() {
        // Act
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, null);

        // Assert
        Claims claims = jwtTokenService.getClaims(token);

        assertThat(claims.getSubject()).isEqualTo(TEST_USER_ID.toString());
        assertThat(claims.get("email", String.class)).isEqualTo(TEST_EMAIL);
        assertThat(claims.get("role", String.class)).isEqualTo(TEST_ROLE);
        // tenantId 為 null 時不應該出現在 token 中
        assertThat(claims.get("tenantId", String.class)).isNull();
    }

    // ── UT-M03-007: JWT Token產生-有效期限30分鐘 ─────────────────────

    @Test
    @DisplayName("UT-M03-007: JWT Access Token產生-有效期限為30分鐘")
    void generateAccessToken_expirationIs30Minutes() {
        // Arrange
        long expectedExpirationMs = 30 * 60 * 1000L; // 30 分鐘 in milliseconds

        // Act
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);
        Claims claims = jwtTokenService.getClaims(token);

        // Assert
        long actualExpirationMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(actualExpirationMs).isEqualTo(expectedExpirationMs);
        // getAccessTokenExpiration() 返回毫秒，不是秒
        assertThat(jwtTokenService.getAccessTokenExpiration()).isEqualTo(1800_000L); // 1800000 ms = 30 分鐘
    }

    @Test
    @DisplayName("UT-M03-007: JWT Access Token產生-過期時間合理")
    void generateAccessToken_expirationTimeIsReasonable() {
        // Act
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);
        Claims claims = jwtTokenService.getClaims(token);

        // Assert
        Date now = new Date();
        Date expiration = claims.getExpiration();

        // 過期時間應該在未來
        assertThat(expiration).isAfter(now);

        // 過期時間應該在 30 分鐘後（允許一些小誤差）
        long diffMs = expiration.getTime() - now.getTime();
        assertThat(diffMs).isBetween(29 * 60 * 1000L, 31 * 60 * 1000L);
    }

    // ── UT-M03-008: JWT Token驗證-有效Token通過 ───────────────────────

    @Test
    @DisplayName("UT-M03-008: JWT Token驗證-有效Token通過驗證")
    void validateToken_validToken_returnsTrue() {
        // Arrange
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // Act
        boolean isValid = jwtTokenService.validateToken(token);

        // Assert
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("UT-M03-008: JWT Token驗證-有效Token解析成功")
    void getClaims_validToken_returnsCorrectClaims() {
        // Arrange
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // Act
        Claims claims = jwtTokenService.getClaims(token);

        // Assert
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(TEST_USER_ID.toString());
    }

    // ── UT-M03-009: JWT Token驗證-過期Token失敗 ─────────────────────

    @Test
    @DisplayName("UT-M03-009: JWT Token驗證-過期Token失敗")
    void validateToken_expiredToken_returnsFalse() {
        // Arrange - 建立一個過期時間為 -1 秒的 service（即刻過期）
        JwtTokenService expiredTokenService = new JwtTokenService(TEST_SECRET, -1000L, REFRESH_TOKEN_EXPIRATION);
        String expiredToken = expiredTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // Act
        boolean isExpired = jwtTokenService.isTokenExpired(expiredToken);
        boolean isValid = jwtTokenService.validateToken(expiredToken);

        // Assert
        assertThat(isExpired).isTrue();
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("UT-M03-009: JWT Token驗證-過期Token拋出ExpiredException")
    void getClaims_expiredToken_throwsExpiredJwtException() {
        // Arrange - 建立一個已經過期的 token
        // 使用一個非常短的有效期，然後等待它過期
        // 或者直接測試 isTokenExpired 方法
        JwtTokenService expiredTokenService = new JwtTokenService(TEST_SECRET, -1000L, REFRESH_TOKEN_EXPIRATION);
        String expiredToken = expiredTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // Act & Assert
        assertThatThrownBy(() -> jwtTokenService.getClaims(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    // ── UT-M03-010: JWT Token驗證-篡改Token失敗 ─────────────────────

    @Test
    @DisplayName("UT-M03-010: JWT Token驗證-篡改Token失敗")
    void validateToken_tamperedToken_returnsFalse() {
        // Arrange
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);
        // 篡改 payload 部分（不改 signature），這樣 signature 就會與 payload 不匹配
        // JWT 結構: header.payload.signature，篡改 payload 會導致 signature 驗證失敗
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        // Act
        boolean isValid = jwtTokenService.validateToken(tamperedToken);

        // Assert
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("UT-M03-010: JWT Token驗證-篡改Token拋出SignatureException")
    void getClaims_tamperedToken_throwsSignatureException() {
        // Arrange
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);
        // 篡改 payload 部分（不改 signature）
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        // Act & Assert
        assertThatThrownBy(() -> jwtTokenService.getClaims(tamperedToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("UT-M03-010: JWT Token驗證-完全無效格式Token失敗")
    void validateToken_invalidFormatToken_returnsFalse() {
        // Arrange
        String invalidToken = "invalid.token.format";

        // Act
        boolean isValid = jwtTokenService.validateToken(invalidToken);

        // Assert
        assertThat(isValid).isFalse();
    }

    // ── UT-M03-011: JWT Refresh Token產生-payload正確 ────────────────

    @Test
    @DisplayName("UT-M03-011: JWT Refresh Token產生-payload包含正確欄位")
    void generateRefreshToken_payloadContainsCorrectFields() {
        // Act
        String token = jwtTokenService.generateRefreshToken(TEST_USER_ID);

        // Assert
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();

        Claims claims = jwtTokenService.getClaims(token);

        assertThat(claims.getSubject()).isEqualTo(TEST_USER_ID.toString());
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    @DisplayName("UT-M03-011: JWT Refresh Token不包含額外claim")
    void generateRefreshToken_containsMinimalPayload() {
        // Act
        String token = jwtTokenService.generateRefreshToken(TEST_USER_ID);
        Claims claims = jwtTokenService.getClaims(token);

        // Assert - Refresh Token 應該只有基本欄位，不應該有 email, role 等
        assertThat(claims.getSubject()).isEqualTo(TEST_USER_ID.toString());
        assertThat(claims.get("email", String.class)).isNull();
        assertThat(claims.get("role", String.class)).isNull();
        assertThat(claims.get("tenantId", String.class)).isNull();
    }

    // ── UT-M03-012: JWT Refresh Token有效期限30天 ───────────────────

    @Test
    @DisplayName("UT-M03-012: JWT Refresh Token產生-有效期限為30天")
    void generateRefreshToken_expirationIs30Days() {
        // Arrange
        long expectedExpirationMs = 30L * 24 * 60 * 60 * 1000L; // 30 天

        // Act
        String token = jwtTokenService.generateRefreshToken(TEST_USER_ID);
        Claims claims = jwtTokenService.getClaims(token);

        // Assert
        long actualExpirationMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(actualExpirationMs).isEqualTo(expectedExpirationMs);
    }

    @Test
    @DisplayName("UT-M03-012: JWT Refresh Token產生-過期時間在未來30天")
    void generateRefreshToken_expirationIsIn30Days() {
        // Act
        String token = jwtTokenService.generateRefreshToken(TEST_USER_ID);
        Claims claims = jwtTokenService.getClaims(token);

        // Assert
        Date now = new Date();
        Date expiration = claims.getExpiration();

        // 過期時間應該在未來 30 天左右
        assertThat(expiration).isAfter(now);

        long diffMs = expiration.getTime() - now.getTime();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000L;
        // 允許 1 小時的誤差
        assertThat(diffMs).isBetween(thirtyDaysMs - 60 * 60 * 1000L, thirtyDaysMs + 60 * 60 * 1000L);
    }

    // ── 輔助方法測試 ─────────────────────────────────────────────────

    @Test
    @DisplayName("getUserId: 從有效Token解析userId正確")
    void getUserId_validToken_returnsCorrectUserId() {
        // Arrange
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // Act
        UUID userId = jwtTokenService.getUserId(token);

        // Assert
        assertThat(userId).isEqualTo(TEST_USER_ID);
    }

    @Test
    @DisplayName("getEmail: 從有效Token解析email正確")
    void getEmail_validToken_returnsCorrectEmail() {
        // Arrange
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // Act
        String email = jwtTokenService.getEmail(token);

        // Assert
        assertThat(email).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("getRole: 從有效Token解析role正確")
    void getRole_validToken_returnsCorrectRole() {
        // Arrange
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // Act
        String role = jwtTokenService.getRole(token);

        // Assert
        assertThat(role).isEqualTo(TEST_ROLE);
    }

    @Test
    @DisplayName("getTenantId: 從有效Token解析tenantId正確")
    void getTenantId_validToken_returnsCorrectTenantId() {
        // Arrange
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // Act
        String tenantId = jwtTokenService.getTenantId(token);

        // Assert
        assertThat(tenantId).isEqualTo(TEST_TENANT_ID);
    }

    @Test
    @DisplayName("isTokenExpired: 未過期Token返回false")
    void isTokenExpired_validToken_returnsFalse() {
        // Arrange
        String token = jwtTokenService.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // Act
        boolean isExpired = jwtTokenService.isTokenExpired(token);

        // Assert
        assertThat(isExpired).isFalse();
    }
}
