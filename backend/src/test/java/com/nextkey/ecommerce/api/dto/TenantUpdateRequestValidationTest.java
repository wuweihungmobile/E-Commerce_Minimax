package com.nextkey.ecommerce.api.dto;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEF-194 回歸測試（Sprint 155）：{@code TenantUpdateRequest.logoUrl} 協定白名單驗證。
 *
 * <p>背景：查證 Sprint 154 §4/§6 記錄的 {@code TenantUpdateRequest.coverImageUrl} 是否有同型
 * 協定驗證缺口時發現，其手足欄位 {@code logoUrl} 才是與 DEF-104（{@code CmsDto} Banner
 * {@code linkUrl}）結構完全同型的缺口：有寫入路徑（{@code TenantService.applyTenantUpdates}）、
 * 有持久化（{@code Tenant.logoUrl}），且經公開端點 {@code GET /v2/tenants/{id}} 回傳
 * （{@code TenantDetailsResponse.logoUrl}），但目前全代碼庫查證零前端消費端（{@code TenantEditForm}/
 * {@code TenantDetail} 皆未讀取此欄位），sink 尚不存在，非可利用漏洞，比照 DEF-104 同一批
 * 防禦性修復。
 *
 * <p>驗證意圖：確保 {@code javascript:}/{@code data:}/{@code vbscript:}/{@code file:} 等危險協定
 * （含大小寫混合、前導空白等繞過手法）一律被拒絕，同時不影響合法的 http(s) 絕對網址、相對路徑
 * 或選填（null/空字串）情境。
 *
 * <p>{@code coverImageUrl} 刻意不在此測試涵蓋範圍：該欄位查證確認是完全死欄位（從未被
 * {@code applyTenantUpdates} 讀取、{@code Tenant} 實體無對應欄位、{@code getTenantDetails}
 * 恆回傳 null），加協定驗證在會被丟棄的值上沒有意義，已記錄為 DEF-193（不排入排程）。
 */
class TenantUpdateRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        validatorFactory.close();
    }

    private Set<ConstraintViolation<TenantUpdateRequest>> violationsOnLogoUrl(String logoUrl) {
        TenantUpdateRequest request = TenantUpdateRequest.builder().logoUrl(logoUrl).build();
        Set<ConstraintViolation<TenantUpdateRequest>> violations = validator.validate(request);
        return violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("logoUrl"))
                .collect(java.util.stream.Collectors.toSet());
    }

    @ParameterizedTest(name = "合法值不應觸發驗證錯誤：{0}")
    @ValueSource(strings = {
            "https://example.com/logo.png",
            "http://cdn.example.com/logo.jpg",
            "/uploads/logos/abc.png"
    })
    @DisplayName("DEF-194：合法的 http(s) 網址或相對路徑應通過驗證")
    void legitimateLogoUrl_passesValidation(String logoUrl) {
        assertThat(violationsOnLogoUrl(logoUrl)).isEmpty();
    }

    @Test
    @DisplayName("DEF-194：logoUrl 為 null（選填欄位）應通過驗證")
    void nullLogoUrl_passesValidation() {
        assertThat(violationsOnLogoUrl(null)).isEmpty();
    }

    @Test
    @DisplayName("DEF-194：logoUrl 為空字串應通過驗證")
    void emptyLogoUrl_passesValidation() {
        assertThat(violationsOnLogoUrl("")).isEmpty();
    }

    @ParameterizedTest(name = "危險協定應被拒絕：{0}")
    @ValueSource(strings = {
            "javascript:alert(document.cookie)",
            "JavaScript:alert(1)",
            "  javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox(1)",
            "file:///etc/passwd"
    })
    @DisplayName("DEF-194：javascript/data/vbscript/file 協定（含大小寫與前導空白繞過）應被拒絕")
    void dangerousProtocol_failsValidation(String logoUrl) {
        assertThat(violationsOnLogoUrl(logoUrl)).isNotEmpty();
    }
}
