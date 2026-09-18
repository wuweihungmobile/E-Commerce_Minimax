package com.nextkey.ecommerce.api.dto;

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
 * DEF-227 回歸測試（Sprint 175）：{@code BookingDto.UpdateRequest} 缺少與
 * {@code CreateRequest} 相同的欄位驗證。
 *
 * <p>背景：延伸 Sprint 173/174（{@code DEF-225}/{@code DEF-226}）「create 有驗證、update 沒有」
 * 的掃描角度，從日期範圍擴大到 DTO 欄位層級的 Bean Validation 註解，發現
 * {@code CreateRequest.guestCount}/{@code guestName}/{@code guestPhone}/{@code guestEmail}/
 * {@code specialRequests} 皆有對應驗證註解，但 {@code UpdateRequest} 同名欄位完全沒有——與
 * {@code RoomDto.UpdateRequest.maxGuests}（{@code @Min} 不搭配 {@code @NotNull}）等既有 partial
 * update 慣例不一致。
 */
class BookingDtoValidationTest {

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

    private Set<ConstraintViolation<BookingDto.UpdateRequest>> violationsOn(String property, BookingDto.UpdateRequest request) {
        return validator.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals(property))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("DEF-227：所有欄位皆為 null（未觸碰任何欄位）應通過驗證，維持 partial update 語意")
    void allFieldsNull_passesValidation() {
        BookingDto.UpdateRequest request = BookingDto.UpdateRequest.builder().build();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("DEF-227：guestCount 為 0 或負數應被拒絕")
    void guestCountLessThanOne_failsValidation() {
        BookingDto.UpdateRequest zero = BookingDto.UpdateRequest.builder().guestCount(0).build();
        BookingDto.UpdateRequest negative = BookingDto.UpdateRequest.builder().guestCount(-1).build();

        assertThat(violationsOn("guestCount", zero)).isNotEmpty();
        assertThat(violationsOn("guestCount", negative)).isNotEmpty();
    }

    @Test
    @DisplayName("DEF-227：guestCount 為正數應通過驗證")
    void guestCountPositive_passesValidation() {
        BookingDto.UpdateRequest request = BookingDto.UpdateRequest.builder().guestCount(2).build();
        assertThat(violationsOn("guestCount", request)).isEmpty();
    }

    @Test
    @DisplayName("DEF-227：guestPhone 格式不符應被拒絕，合法格式應通過")
    void guestPhone_formatValidation() {
        BookingDto.UpdateRequest invalid = BookingDto.UpdateRequest.builder().guestPhone("abc").build();
        BookingDto.UpdateRequest valid = BookingDto.UpdateRequest.builder().guestPhone("0912345678").build();

        assertThat(violationsOn("guestPhone", invalid)).isNotEmpty();
        assertThat(violationsOn("guestPhone", valid)).isEmpty();
    }

    @Test
    @DisplayName("DEF-227：guestEmail 格式不符應被拒絕，合法格式應通過")
    void guestEmail_formatValidation() {
        BookingDto.UpdateRequest invalid = BookingDto.UpdateRequest.builder().guestEmail("not-an-email").build();
        BookingDto.UpdateRequest valid = BookingDto.UpdateRequest.builder().guestEmail("guest@example.com").build();

        assertThat(violationsOn("guestEmail", invalid)).isNotEmpty();
        assertThat(violationsOn("guestEmail", valid)).isEmpty();
    }

    @Test
    @DisplayName("DEF-227：guestName 超過長度上限應被拒絕")
    void guestNameTooLong_failsValidation() {
        BookingDto.UpdateRequest request = BookingDto.UpdateRequest.builder()
                .guestName("a".repeat(201))
                .build();
        assertThat(violationsOn("guestName", request)).isNotEmpty();
    }

    @Test
    @DisplayName("DEF-227：specialRequests 超過長度上限應被拒絕")
    void specialRequestsTooLong_failsValidation() {
        BookingDto.UpdateRequest request = BookingDto.UpdateRequest.builder()
                .specialRequests("a".repeat(1001))
                .build();
        assertThat(violationsOn("specialRequests", request)).isNotEmpty();
    }
}
