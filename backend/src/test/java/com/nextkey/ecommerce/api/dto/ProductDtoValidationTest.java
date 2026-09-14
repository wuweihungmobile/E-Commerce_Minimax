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
 * DEF-196 回歸測試（Sprint 156）：{@code ProductDto.CreateRequest}/{@code UpdateRequest} 的
 * {@code coverImageUrl} 協定白名單驗證。
 *
 * <p>背景：與 {@code DEF-103}（Sprint 154，{@code CreateListingRequest.coverImageUrl}）同型缺口——
 * {@code POST /v2/products}/{@code PUT /v2/products/{id}} 是另一條寫入同一個
 * {@code Listing.coverImageUrl} 欄位的獨立路徑（見 {@code ProductService}），先前的修復未覆蓋此
 * 路徑。此欄位經 {@code cart}/{@code checkout}/{@code orders} 等頁面以 {@code <img src=...>} 綁定，
 * 與 DEF-103 相同結論：現行主流瀏覽器對 {@code <img src>} 不會執行 {@code javascript:}/
 * {@code data:text/html} 協定，非可利用 XSS，但仍是輸入驗證缺口，比照同一防禦性修復。
 */
class ProductDtoValidationTest {

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

    private ProductDto.CreateRequest.CreateRequestBuilder validCreateRequestBuilder() {
        return ProductDto.CreateRequest.builder()
                .title("測試商品")
                .category("electronics")
                .basePrice(new BigDecimal("100.00"));
    }

    private Set<ConstraintViolation<ProductDto.CreateRequest>> createViolationsOnCoverImageUrl(String coverImageUrl) {
        ProductDto.CreateRequest request = validCreateRequestBuilder().coverImageUrl(coverImageUrl).build();
        return validator.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals("coverImageUrl"))
                .collect(java.util.stream.Collectors.toSet());
    }

    private Set<ConstraintViolation<ProductDto.UpdateRequest>> updateViolationsOnCoverImageUrl(String coverImageUrl) {
        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder().coverImageUrl(coverImageUrl).build();
        return validator.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals("coverImageUrl"))
                .collect(java.util.stream.Collectors.toSet());
    }

    @ParameterizedTest(name = "合法網址應通過驗證：{0}")
    @ValueSource(strings = {
            "https://example.com/cover.png",
            "/uploads/cover.png"
    })
    @DisplayName("DEF-196：合法 http(s) 網址或相對路徑應通過驗證")
    void legitimateCoverImageUrl_passesValidation(String coverImageUrl) {
        assertThat(createViolationsOnCoverImageUrl(coverImageUrl)).isEmpty();
        assertThat(updateViolationsOnCoverImageUrl(coverImageUrl)).isEmpty();
    }

    @Test
    @DisplayName("DEF-196：coverImageUrl 為 null（選填欄位）應通過驗證")
    void nullCoverImageUrl_passesValidation() {
        assertThat(createViolationsOnCoverImageUrl(null)).isEmpty();
        assertThat(updateViolationsOnCoverImageUrl(null)).isEmpty();
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
    @DisplayName("DEF-196：javascript/data/vbscript/file 協定（含大小寫與前導空白繞過）應被拒絕")
    void dangerousProtocol_failsValidation(String coverImageUrl) {
        assertThat(createViolationsOnCoverImageUrl(coverImageUrl)).isNotEmpty();
        assertThat(updateViolationsOnCoverImageUrl(coverImageUrl)).isNotEmpty();
    }
}
