package com.nextkey.ecommerce.infrastructure.security;

import com.nextkey.ecommerce.api.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

/**
 * UT-M03-001 ~ UT-M03-005: 密碼加密邏輯單元測試
 *
 * 測試範圍：
 * - UT-M03-001: BCrypt密碼加密-密碼不可逆
 * - UT-M03-002: BCrypt密碼加密-鹽值不同
 * - UT-M03-003: BCrypt密碼加密-相同密碼驗證成功
 * - UT-M03-004: BCrypt密碼加密-不同密碼驗證失敗
 * - UT-M03-005: BCrypt密碼加密-cost factor設定
 */
@DisplayName("UT-M03-001 ~ UT-M03-005: 密碼加密邏輯")
class PasswordEncoderTest {

    // FRD NFR-SEC-002（P0）：bcrypt salt round ≥ 12；SRD 密碼儲存：cost factor = 12。
    // Sprint 233（DEF-323）：本測試原本用自己 new 出來的 cost 10 編碼器、還把 10 寫成「標準」，完全沒碰正式的
    // PasswordEncoder bean——所以正式環境用預設強度 10、不符規格的狀態，這裡永遠抓不到（無法失敗的測試）。
    // 現在直接取 SecurityConfig 的正式 bean：它的強度一旦被改掉，下面的格式斷言就會失敗。
    private static final int EXPECTED_COST_FACTOR = 12;
    private static final String EXPECTED_PREFIX = "$2a$" + EXPECTED_COST_FACTOR + "$";
    private static final String TEST_PASSWORD = "SecurePass123";
    private static final String DIFFERENT_PASSWORD = "Pass456";

    private final PasswordEncoder passwordEncoder = new SecurityConfig(null, null, null, null).passwordEncoder();

    // ── UT-M03-001: 密碼不可逆 ─────────────────────────────────────────

    @Test
    @DisplayName("UT-M03-001: BCrypt密碼加密-密碼不可逆")
    void encode_passwordCannotBeDecrypted() {
        // Arrange
        String rawPassword = TEST_PASSWORD;

        // Act
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // Assert
        assertThat(encodedPassword).isNotNull();
        assertThat(encodedPassword).isNotEqualTo(rawPassword);
        // BCrypt 加密結果格式: $2a$12$...
        assertThat(encodedPassword).startsWith(EXPECTED_PREFIX);

        // 驗證無法從加密結果反推原始密碼
        // 嘗試 "解碼" - BCrypt 沒有 decode 方法，只能通過 matches 驗證
        assertThat(encodedPassword).isNotEqualTo(passwordEncoder.encode("DecryptedPassword"));
        assertThat(encodedPassword).doesNotContain("SecurePass");
    }

    // ── UT-M03-002: 鹽值不同 ─────────────────────────────────────────

    @Test
    @DisplayName("UT-M03-002: BCrypt密碼加密-鹽值不同（相同密碼加密兩次結果不同）")
    void encode_samePasswordTwice_producesDifferentResults() {
        // Arrange
        String rawPassword = TEST_PASSWORD;

        // Act
        String encodedPassword1 = passwordEncoder.encode(rawPassword);
        String encodedPassword2 = passwordEncoder.encode(rawPassword);

        // Assert
        assertThat(encodedPassword1).isNotEqualTo(encodedPassword2);
        // 兩次加密結果都應該能驗證成功
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword1)).isTrue();
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword2)).isTrue();
    }

    // ── UT-M03-003: 相同密碼驗證成功 ──────────────────────────────────

    @Test
    @DisplayName("UT-M03-003: BCrypt密碼加密-相同密碼驗證成功")
    void matches_samePassword_returnsTrue() {
        // Arrange
        String rawPassword = TEST_PASSWORD;
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // Act & Assert
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();
    }

    @Test
    @DisplayName("UT-M03-003: BCrypt密碼加密-驗證相同密碼（不同編碼輪次）")
    void matches_samePasswordEncodedMultipleTimes_returnsTrue() {
        // Arrange
        String rawPassword = TEST_PASSWORD;
        String encodedPassword1 = passwordEncoder.encode(rawPassword);
        String encodedPassword2 = passwordEncoder.encode(rawPassword);

        // Act & Assert - 兩個不同的編碼都應該能驗證
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword1)).isTrue();
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword2)).isTrue();
    }

    // ── UT-M03-004: 不同密碼驗證失敗 ──────────────────────────────────

    @Test
    @DisplayName("UT-M03-004: BCrypt密碼加密-不同密碼驗證失敗")
    void matches_differentPassword_returnsFalse() {
        // Arrange
        String rawPasswordCorrect = TEST_PASSWORD;
        String rawPasswordWrong = DIFFERENT_PASSWORD;
        String encodedPassword = passwordEncoder.encode(rawPasswordCorrect);

        // Act & Assert
        assertThat(passwordEncoder.matches(rawPasswordWrong, encodedPassword)).isFalse();
    }

    @Test
    @DisplayName("UT-M03-004: BCrypt密碼加密-空密碼驗證失敗")
    void matches_emptyPassword_returnsFalse() {
        // Arrange
        String rawPasswordCorrect = TEST_PASSWORD;
        String encodedPassword = passwordEncoder.encode(rawPasswordCorrect);

        // Act & Assert
        assertThat(passwordEncoder.matches("", encodedPassword)).isFalse();
    }

    // ── UT-M03-005: Cost Factor 設定 ─────────────────────────────────

    @Test
    @DisplayName("UT-M03-005: BCrypt密碼加密-正式 bean 的 cost factor 為 12（FRD NFR-SEC-002 P0）")
    void encode_verifyCostFactor() {
        // Act
        String encodedPassword = passwordEncoder.encode(TEST_PASSWORD);

        // Assert
        // BCrypt 格式: $2a$[cost]$[22字符鹽][53字符hash]；cost factor 12 = $2a$12$
        assertThat(encodedPassword).startsWith(EXPECTED_PREFIX);
        // 不測加密耗時：時間不是意圖，在慢的 CI 機器上也會讓測試不穩；成本直接編在雜湊字串裡，檢查格式就夠了。
    }

    @Test
    @DisplayName("Sprint 233：升級成本後，既有 cost 10 的雜湊仍可驗證（不需遷移既有使用者）")
    void matches_legacyCost10Hash_stillVerifiesWithProductionEncoder() {
        String legacyHash = new BCryptPasswordEncoder(10).encode(TEST_PASSWORD);
        assertThat(legacyHash).startsWith("$2a$10$");

        assertThat(passwordEncoder.matches(TEST_PASSWORD, legacyHash)).isTrue();
        assertThat(passwordEncoder.matches(DIFFERENT_PASSWORD, legacyHash)).isFalse();
    }

    @Test
    @DisplayName("UT-M03-005: BCrypt密碼加密-不同cost factor產生不同格式")
    void encode_differentCostFactors_produceDifferentPrefix() {
        // Arrange
        PasswordEncoder encoderCost10 = new BCryptPasswordEncoder(10);
        PasswordEncoder encoderCost12 = new BCryptPasswordEncoder(12);

        // Act
        String encodedCost10 = encoderCost10.encode(TEST_PASSWORD);
        String encodedCost12 = encoderCost12.encode(TEST_PASSWORD);

        // Assert
        assertThat(encodedCost10).startsWith("$2a$10$");
        assertThat(encodedCost12).startsWith("$2a$12$");
        assertThat(encodedCost10).isNotEqualTo(encodedCost12);

        // 兩個都能驗證原始密碼
        assertThat(encoderCost10.matches(TEST_PASSWORD, encodedCost10)).isTrue();
        assertThat(encoderCost12.matches(TEST_PASSWORD, encodedCost12)).isTrue();
    }
}
