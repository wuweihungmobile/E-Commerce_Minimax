package com.nextkey.ecommerce.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

/**
 * SecurityConfig CORS 允許來源單元測試（Sprint 191，DEF-265）。
 *
 * <p>缺陷：{@code application.yml}（以及 test / integration-test profile）都宣告了
 * {@code app.cors.allowed-origins}，但 {@code SecurityConfig.corsConfigurationSource()} 把來源硬編碼為
 * {@code localhost:3000/8080}，該設定值沒有任何程式碼讀取。前端 {@code NEXT_PUBLIC_API_URL} 於 build 時可設為
 * 正式後端網域，瀏覽器從正式前端網域跨源直連後端時，維運人員即使照設定檔設好允許來源也完全無效，
 * 所有 API 呼叫都會被瀏覽器的 CORS 檢查封鎖（伺服器端不報錯，故長期未被察覺）。
 *
 * <p>本測試驗證 Spring 判定來源用的 {@link CorsConfiguration#checkOrigin(String)}——命中回傳該來源，
 * 未命中回傳 {@code null}——斷言「白名單內容確實由設定值決定」，而非只斷言某個來源可通過。
 */
@DisplayName("SecurityConfig CORS 允許來源（DEF-265：app.cors.allowed-origins 必須生效）")
class SecurityConfigCorsOriginsTest {

    private static final String PROD_ORIGIN = "https://shop.example.com";

    private CorsConfiguration configFor(String rawAllowedOrigins) {
        SecurityConfig securityConfig = new SecurityConfig(null, null, null, null);
        ReflectionTestUtils.setField(securityConfig, "corsAllowedOriginsRaw", rawAllowedOrigins);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v2/products");
        CorsConfiguration configuration = securityConfig.corsConfigurationSource().getCorsConfiguration(request);
        assertThat(configuration).isNotNull();
        return configuration;
    }

    @Test
    @DisplayName("設定的正式網域必須被允許（修復前被硬編碼的 localhost 名單擋下）")
    void configuredOrigin_isAllowed() {
        assertThat(configFor(PROD_ORIGIN).checkOrigin(PROD_ORIGIN)).isEqualTo(PROD_ORIGIN);
    }

    @Test
    @DisplayName("設定值不含 localhost 時，localhost 必須被拒——證明名單完全由設定決定而非硬編碼併集")
    void localhost_rejectedWhenNotConfigured() {
        assertThat(configFor(PROD_ORIGIN).checkOrigin("http://localhost:3000")).isNull();
    }

    @Test
    @DisplayName("逗號分隔多個來源，且容忍前後空白")
    void multipleOrigins_commaSeparatedWithWhitespace() {
        CorsConfiguration configuration = configFor(" http://localhost:3000 , " + PROD_ORIGIN + " ");

        assertThat(configuration.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
        assertThat(configuration.checkOrigin(PROD_ORIGIN)).isEqualTo(PROD_ORIGIN);
    }

    @Test
    @DisplayName("未列入名單的來源仍被拒（避免修法退化成全開）")
    void unlistedOrigin_rejected() {
        assertThat(configFor(PROD_ORIGIN).checkOrigin("https://evil.example.org")).isNull();
    }

    @Test
    @DisplayName("設定為空字串時不允許任何來源（失敗即關閉，而非退回預設或全開）")
    void blankConfig_allowsNoOrigin() {
        CorsConfiguration configuration = configFor("");

        assertThat(configuration.checkOrigin(PROD_ORIGIN)).isNull();
        assertThat(configuration.checkOrigin("http://localhost:3000")).isNull();
    }

    @Test
    @DisplayName("allowCredentials=true 時不得以萬用字元 * 全開來源（大聲失敗，不靜默放行）")
    void wildcardWithCredentials_failsLoudly() {
        assertThatThrownBy(() -> configFor("*").checkOrigin(PROD_ORIGIN))
                .as("帶憑證的 CORS 搭配 * 是最危險的誤設定，必須拋例外而非放行任意網域")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowCredentials");
    }
}
