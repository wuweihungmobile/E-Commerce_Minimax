package com.nextkey.ecommerce.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

/**
 * SecurityConfig CORS 設定單元測試（Sprint 128，DEF-072）。
 *
 * <p>缺陷：{@code Idempotency-Key} 未列入 {@code setAllowedHeaders}，導致前端訂房結帳
 * （{@code POST /v2/bookings}）與合併結帳（{@code POST /v2/checkout/mixed}）帶此自訂標頭時，
 * 瀏覽器 preflight 回應的 {@code Access-Control-Allow-Headers} 不含該標頭，真正的 POST 從未送出。
 *
 * <p>本測試直接驗證 Spring 判定 preflight 用的 {@link CorsConfiguration#checkHeaders(List)}——
 * 該方法回傳的是「被允許的標頭子集」，瀏覽器據此決定是否放行，故斷言必須是「請求的標頭全部都在
 * 回傳集合內」，而非僅斷言回傳非 null（非 null 只代表至少一個標頭命中，正是本缺陷難以察覺的原因）。
 */
@DisplayName("SecurityConfig CORS（DEF-072：Idempotency-Key preflight）")
class SecurityConfigCorsTest {

    /** corsConfigurationSource() 不使用任何 filter 相依，可安全以 null 建構。 */
    private final SecurityConfig securityConfig = new SecurityConfig(null, null, null);

    private CorsConfiguration configFor(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        CorsConfiguration configuration = securityConfig.corsConfigurationSource().getCorsConfiguration(request);
        assertThat(configuration).as("CORS 設定必須套用於 %s", path).isNotNull();
        return configuration;
    }

    @Test
    @DisplayName("訂房結帳的 preflight：Idempotency-Key 必須被允許")
    void bookingPreflight_allowsIdempotencyKey() {
        List<String> requested = List.of("authorization", "content-type", "idempotency-key");

        List<String> allowed = configFor("/v2/bookings").checkHeaders(requested);

        assertThat(allowed)
                .as("瀏覽器要求的標頭必須全部出現在 Access-Control-Allow-Headers，否則封鎖真正的 POST")
                .containsAll(requested);
    }

    @Test
    @DisplayName("合併結帳的 preflight：Idempotency-Key 必須被允許（Sprint 126 新流程同樣受影響）")
    void mixedCheckoutPreflight_allowsIdempotencyKey() {
        List<String> requested = List.of("authorization", "content-type", "idempotency-key");

        List<String> allowed = configFor("/v2/checkout/mixed").checkHeaders(requested);

        assertThat(allowed).containsAll(requested);
    }

    @Test
    @DisplayName("既有標頭不受影響（最小爆炸半徑）")
    void existingAllowedHeaders_stillAllowed() {
        CorsConfiguration configuration = configFor("/v2/bookings");

        assertThat(configuration.checkHeaders(List.of("authorization"))).containsExactly("authorization");
        assertThat(configuration.checkHeaders(List.of("content-type"))).containsExactly("content-type");
        assertThat(configuration.checkHeaders(List.of("x-tenant-id"))).containsExactly("x-tenant-id");
    }

    @Test
    @DisplayName("未列入白名單的任意標頭仍應被拒（避免修法退化成全開 *）")
    void unknownHeader_stillRejected() {
        // Spring 的 checkHeaders 在完全無命中時回傳 null（實作為 result.isEmpty() ? null : result），
        // 而非空集合，故此處用 isNullOrEmpty 涵蓋兩種語意。
        assertThat(configFor("/v2/bookings").checkHeaders(List.of("x-not-allowed-header"))).isNullOrEmpty();
    }
}
