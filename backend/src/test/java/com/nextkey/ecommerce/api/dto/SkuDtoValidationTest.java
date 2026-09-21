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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SkuDto Bean Validation 測試（Sprint 178）。
 *
 * <p>{@code skuCode}/{@code specName} 的 {@code @Size} 上限對齊 V1 schema
 * {@code product_skus.sku_code VARCHAR(50)}/{@code spec_name VARCHAR(100)}——沒有這層驗證，
 * 超長輸入會在資料庫層以未經處理的「value too long for type character varying」拋出，
 * 經 {@code GlobalExceptionHandler} 落入 500 而非語意明確的 400。
 */
class SkuDtoValidationTest {

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

    @Test
    @DisplayName("CreateRequest：合法輸入無違規")
    void createRequest_validInput_noViolations() {
        SkuDto.CreateRequest request = SkuDto.CreateRequest.builder()
                .skuCode("SKU-001")
                .specName("紅色 / L")
                .priceOverride(new BigDecimal("199.00"))
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("CreateRequest：skuCode 空白時違規")
    void createRequest_blankSkuCode_violates() {
        SkuDto.CreateRequest request = SkuDto.CreateRequest.builder().skuCode("  ").build();

        Set<ConstraintViolation<SkuDto.CreateRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("skuCode"));
    }

    @Test
    @DisplayName("CreateRequest：skuCode 超過 50 字元時違規（對齊 VARCHAR(50)）")
    void createRequest_skuCodeTooLong_violates() {
        SkuDto.CreateRequest request = SkuDto.CreateRequest.builder().skuCode("A".repeat(51)).build();

        Set<ConstraintViolation<SkuDto.CreateRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("skuCode"));
    }

    @Test
    @DisplayName("CreateRequest：skuCode 恰好 50 字元時通過")
    void createRequest_skuCodeExactly50Chars_noViolation() {
        SkuDto.CreateRequest request = SkuDto.CreateRequest.builder().skuCode("A".repeat(50)).build();

        Set<ConstraintViolation<SkuDto.CreateRequest>> violations = validator.validate(request);

        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("skuCode"));
    }

    @Test
    @DisplayName("CreateRequest：specName 超過 100 字元時違規（對齊 VARCHAR(100)）")
    void createRequest_specNameTooLong_violates() {
        SkuDto.CreateRequest request = SkuDto.CreateRequest.builder()
                .skuCode("SKU-001")
                .specName("A".repeat(101))
                .build();

        Set<ConstraintViolation<SkuDto.CreateRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("specName"));
    }

    @Test
    @DisplayName("CreateRequest：priceOverride 為 0 或負數時違規")
    void createRequest_nonPositivePriceOverride_violates() {
        SkuDto.CreateRequest request = SkuDto.CreateRequest.builder()
                .skuCode("SKU-001")
                .priceOverride(BigDecimal.ZERO)
                .build();

        Set<ConstraintViolation<SkuDto.CreateRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("priceOverride"));
    }

    @Test
    @DisplayName("UpdateRequest：全部欄位皆選填，空物件無違規（partial update 語意）")
    void updateRequest_emptyRequest_noViolations() {
        SkuDto.UpdateRequest request = SkuDto.UpdateRequest.builder().build();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("UpdateRequest：specName 超過 100 字元時違規")
    void updateRequest_specNameTooLong_violates() {
        SkuDto.UpdateRequest request = SkuDto.UpdateRequest.builder().specName("A".repeat(101)).build();

        Set<ConstraintViolation<SkuDto.UpdateRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("specName"));
    }
}
