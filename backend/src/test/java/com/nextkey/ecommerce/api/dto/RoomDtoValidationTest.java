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
 * DEF-196 回歸測試（Sprint 156）：{@code RoomDto.CreateRequest}/{@code UpdateRequest} 的
 * {@code coverImageUrl} 協定白名單驗證。
 *
 * <p>背景：與 {@code ProductDtoValidationTest} 同一個 {@code DEF-196}——{@code POST /v2/rooms}/
 * {@code PUT /v2/rooms/{id}} 同樣是另一條寫入 {@code Listing.coverImageUrl} 的獨立路徑（見
 * {@code RoomService}），先前 {@code DEF-103} 的修復未覆蓋此路徑。
 */
class RoomDtoValidationTest {

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

    private RoomDto.CreateRequest.CreateRequestBuilder validCreateRequestBuilder() {
        return RoomDto.CreateRequest.builder()
                .title("測試房源")
                .location("台北市")
                .basePrice(new BigDecimal("1000.00"));
    }

    private Set<ConstraintViolation<RoomDto.CreateRequest>> createViolationsOnCoverImageUrl(String coverImageUrl) {
        RoomDto.CreateRequest request = validCreateRequestBuilder().coverImageUrl(coverImageUrl).build();
        return validator.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals("coverImageUrl"))
                .collect(java.util.stream.Collectors.toSet());
    }

    private Set<ConstraintViolation<RoomDto.UpdateRequest>> updateViolationsOnCoverImageUrl(String coverImageUrl) {
        RoomDto.UpdateRequest request = RoomDto.UpdateRequest.builder().coverImageUrl(coverImageUrl).build();
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
