package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
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
 * DEF-103 回歸測試（Sprint 154）：{@code CreateListingRequest.coverImageUrl} 協定白名單驗證。
 *
 * <p>背景：此欄位原本無任何協定驗證，任何賣家可填入任意字串；雖經查證現行 {@code <img src>}
 * 消費端（{@code frontend/src/app/(auth)/cart/page.tsx} 等）不會執行 {@code javascript:} 協定，
 * 非可利用的 XSS，但仍屬輸入驗證品質缺口，S135 已記錄為低優先級技術債。
 *
 * <p>驗證意圖：確保 {@code javascript:}/{@code data:}/{@code vbscript:}/{@code file:} 等危險協定
 * （含大小寫混合、前導空白等繞過手法）一律被拒絕，同時不影響合法的 http(s) 絕對網址、相對路徑
 * 或選填（null/空字串）情境。
 */
class CreateListingRequestValidationTest {

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

    private CreateListingRequest.CreateListingRequestBuilder validRequestBuilder() {
        return CreateListingRequest.builder()
                .listingType("PRODUCT")
                .name("測試商品")
                .price(BigDecimal.valueOf(100));
    }

    private Set<ConstraintViolation<CreateListingRequest>> violationsOnCoverImageUrl(String coverImageUrl) {
        CreateListingRequest request = validRequestBuilder().coverImageUrl(coverImageUrl).build();
        Set<ConstraintViolation<CreateListingRequest>> violations = validator.validate(request);
        return violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("coverImageUrl"))
                .collect(java.util.stream.Collectors.toSet());
    }

    @ParameterizedTest(name = "合法值不應觸發驗證錯誤：{0}")
    @ValueSource(strings = {
            "https://example.com/cover.png",
            "http://cdn.example.com/img.jpg",
            "/uploads/covers/abc.png"
    })
    @DisplayName("DEF-103：合法的 http(s) 網址或相對路徑應通過驗證")
    void legitimateCoverImageUrl_passesValidation(String coverImageUrl) {
        assertThat(violationsOnCoverImageUrl(coverImageUrl)).isEmpty();
    }

    @Test
    @DisplayName("DEF-103：coverImageUrl 為 null（選填欄位）應通過驗證")
    void nullCoverImageUrl_passesValidation() {
        assertThat(violationsOnCoverImageUrl(null)).isEmpty();
    }

    @Test
    @DisplayName("DEF-103：coverImageUrl 為空字串應通過驗證")
    void emptyCoverImageUrl_passesValidation() {
        assertThat(violationsOnCoverImageUrl("")).isEmpty();
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
    @DisplayName("DEF-103：javascript/data/vbscript/file 協定（含大小寫與前導空白繞過）應被拒絕")
    void dangerousProtocol_failsValidation(String coverImageUrl) {
        assertThat(violationsOnCoverImageUrl(coverImageUrl)).isNotEmpty();
    }
}
