package com.nextkey.ecommerce.api.dto.erp;

import java.util.Set;
import java.util.stream.Collectors;

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
 * DEF-228 回歸測試（Sprint 176）：{@code SupplierUpdateRequest} 的 {@code email} 欄位
 * 缺少與 {@code SupplierCreateRequest} 相同的 {@code @Email} 格式驗證。
 *
 * <p>背景：延伸 Sprint 173~175（{@code DEF-225}/{@code DEF-226}/{@code DEF-227}）
 * 「create 有驗證、update 沒有」的掃描角度，全庫比對 22 組 Create/Update DTO 配對後，
 * 發現 {@code SupplierCreateRequest.email} 有 {@code @Email(message = "Invalid email format")}，
 * 但 {@code SupplierUpdateRequest.email} 完全沒有對應註解，且
 * {@code SupplierService.updateSupplier} 也沒有手動格式檢查即直接 {@code setEmail}。
 * 兩個端點皆掛 {@code @Valid}，故格式不符的 email 在建立時會被拒絕，更新時卻會靜默寫入。
 */
class SupplierDtoValidationTest {

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

    private Set<ConstraintViolation<SupplierUpdateRequest>> violationsOn(String property, SupplierUpdateRequest request) {
        return validator.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals(property))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("DEF-228：所有欄位皆為 null（未觸碰任何欄位）應通過驗證，維持 partial update 語意")
    void allFieldsNull_passesValidation() {
        SupplierUpdateRequest request = SupplierUpdateRequest.builder().build();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("DEF-228：email 格式不符應被拒絕")
    void emailInvalidFormat_failsValidation() {
        SupplierUpdateRequest request = SupplierUpdateRequest.builder().email("not-an-email").build();
        assertThat(violationsOn("email", request)).isNotEmpty();
    }

    @Test
    @DisplayName("DEF-228：email 格式合法應通過驗證")
    void emailValidFormat_passesValidation() {
        SupplierUpdateRequest request = SupplierUpdateRequest.builder().email("supplier@example.com").build();
        assertThat(violationsOn("email", request)).isEmpty();
    }

    @Test
    @DisplayName("DEF-228：CreateRequest 的 email 格式不符應被拒絕（既有行為，確認對照基準）")
    void createRequestEmailInvalidFormat_failsValidation() {
        SupplierCreateRequest request = SupplierCreateRequest.builder()
                .name("Test Supplier")
                .email("not-an-email")
                .build();
        Set<ConstraintViolation<SupplierCreateRequest>> violations = validator.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals("email"))
                .collect(Collectors.toSet());
        assertThat(violations).isNotEmpty();
    }
}
