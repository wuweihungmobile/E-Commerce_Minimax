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
 * DEF-104 回歸測試（Sprint 154）：{@code CmsDto.CreateBannerRequest}/{@code UpdateBannerRequest}
 * 的 {@code linkUrl} 協定白名單驗證。
 *
 * <p>背景：{@code linkUrl} 原本無任何協定驗證，且 {@code GET /cms/banners/active} 為公開端點；
 * 查證時全代碼庫沒有任何前端消費端讀取此欄位（零 sink），非可利用漏洞，S135 記錄為低優先級、
 * 待未來真正串接該 API 時一併處理。本輪僅補上 DTO 層驗證，不影響 {@code linkType} 為
 * LISTING/PAGE/CATEGORY 時 {@code linkUrl} 可能存放 ID/相對路徑而非完整網址的既有彈性。
 */
class CmsDtoValidationTest {

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

    private CmsDto.CreateBannerRequest.CreateBannerRequestBuilder validCreateRequestBuilder() {
        return CmsDto.CreateBannerRequest.builder()
                .title("首頁橫幅")
                .imageUrl("https://example.com/banner.png")
                .bannerType(CmsDto.BannerType.HERO)
                .position(CmsDto.BannerPosition.HOME_TOP);
    }

    private Set<ConstraintViolation<CmsDto.CreateBannerRequest>> createViolationsOnLinkUrl(String linkUrl) {
        CmsDto.CreateBannerRequest request = validCreateRequestBuilder().linkUrl(linkUrl).build();
        return validator.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals("linkUrl"))
                .collect(java.util.stream.Collectors.toSet());
    }

    private Set<ConstraintViolation<CmsDto.UpdateBannerRequest>> updateViolationsOnLinkUrl(String linkUrl) {
        CmsDto.UpdateBannerRequest request = CmsDto.UpdateBannerRequest.builder().linkUrl(linkUrl).build();
        return validator.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals("linkUrl"))
                .collect(java.util.stream.Collectors.toSet());
    }

    @ParameterizedTest(name = "合法值不應觸發驗證錯誤：{0}")
    @ValueSource(strings = {
            "https://example.com/promo",
            "/listings/3fa85f64-5717-4562-b3fc-2c963f66afa6",
            "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    })
    @DisplayName("DEF-104：合法網址、相對路徑或 ID（LISTING/PAGE/CATEGORY 情境）應通過驗證")
    void legitimateLinkUrl_passesValidation(String linkUrl) {
        assertThat(createViolationsOnLinkUrl(linkUrl)).isEmpty();
        assertThat(updateViolationsOnLinkUrl(linkUrl)).isEmpty();
    }

    @Test
    @DisplayName("DEF-104：linkUrl 為 null（選填欄位）應通過驗證")
    void nullLinkUrl_passesValidation() {
        assertThat(createViolationsOnLinkUrl(null)).isEmpty();
        assertThat(updateViolationsOnLinkUrl(null)).isEmpty();
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
    @DisplayName("DEF-104：javascript/data/vbscript/file 協定（含大小寫與前導空白繞過）應被拒絕")
    void dangerousProtocol_failsValidation(String linkUrl) {
        assertThat(createViolationsOnLinkUrl(linkUrl)).isNotEmpty();
        assertThat(updateViolationsOnLinkUrl(linkUrl)).isNotEmpty();
    }
}
