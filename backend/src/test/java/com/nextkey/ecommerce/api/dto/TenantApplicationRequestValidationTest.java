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
 * DEF-195 回歸測試（Sprint 157）：{@code TenantApplicationRequest.businessLicenseUrl} 協定白名單驗證。
 *
 * <p>背景：Sprint 155 查證 DEF-193/194 時順帶發現此欄位缺口，但刻意不擴大該輪修復範圍
 * （見 {@code DEFERRED_ITEMS_TRACKER.md} DEF-195），僅登記待後續處理。本輪查證確認：
 * {@code TenantService}（第 114 行）將 {@code request.getBusinessLicenseUrl()} 寫入並持久化至
 * {@code TenantApplication.businessLicenseUrl}，且送出此欄位的端點 {@code POST /tenant/apply}
 * 本身是前端 {@code TenantApplyForm.tsx} 實際會呼叫的**活流程**（不同於 DEF-199~201 那種整個功能
 * 前端零 UI 入口的死路徑）——只是目前這個官方表單本身沒有暴露此欄位的輸入框。由於後端 API
 * 對任何直接送出的 JSON payload 一視同仁（不因官方前端表單沒有對應輸入框就拒收該欄位），
 * 任何能呼叫此端點的使用者仍可繞過前端表單、直接以 API 帶入任意字串，故寫入路徑本身缺協定
 * 驗證仍是真實的輸入驗證缺口，比照 DEF-104/194/198 同型防禦性修復。
 *
 * <p>驗證意圖：確保 {@code javascript:}/{@code data:}/{@code vbscript:}/{@code file:} 等危險協定
 * （含大小寫混合、前導空白等繞過手法）一律被拒絕，同時不影響合法的 http(s) 絕對網址、相對路徑
 * 或選填（null/空字串）情境。
 */
class TenantApplicationRequestValidationTest {

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

    private Set<ConstraintViolation<TenantApplicationRequest>> violationsOnBusinessLicenseUrl(String businessLicenseUrl) {
        TenantApplicationRequest request = TenantApplicationRequest.builder()
                .storeName("Valid Store")
                .businessType("RETAIL_ONLY")
                .contactEmail("owner@example.com")
                .businessLicenseUrl(businessLicenseUrl)
                .build();
        Set<ConstraintViolation<TenantApplicationRequest>> violations = validator.validate(request);
        return violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("businessLicenseUrl"))
                .collect(java.util.stream.Collectors.toSet());
    }

    @ParameterizedTest(name = "合法值不應觸發驗證錯誤：{0}")
    @ValueSource(strings = {
            "https://example.com/license.pdf",
            "http://cdn.example.com/docs/license.jpg",
            "/uploads/licenses/abc.pdf"
    })
    @DisplayName("DEF-195：合法的 http(s) 網址或相對路徑應通過驗證")
    void legitimateBusinessLicenseUrl_passesValidation(String businessLicenseUrl) {
        assertThat(violationsOnBusinessLicenseUrl(businessLicenseUrl)).isEmpty();
    }

    @Test
    @DisplayName("DEF-195：businessLicenseUrl 為 null（選填欄位）應通過驗證")
    void nullBusinessLicenseUrl_passesValidation() {
        assertThat(violationsOnBusinessLicenseUrl(null)).isEmpty();
    }

    @Test
    @DisplayName("DEF-195：businessLicenseUrl 為空字串應通過驗證")
    void emptyBusinessLicenseUrl_passesValidation() {
        assertThat(violationsOnBusinessLicenseUrl("")).isEmpty();
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
    @DisplayName("DEF-195：javascript/data/vbscript/file 協定（含大小寫與前導空白繞過）應被拒絕")
    void dangerousProtocol_failsValidation(String businessLicenseUrl) {
        assertThat(violationsOnBusinessLicenseUrl(businessLicenseUrl)).isNotEmpty();
    }
}
