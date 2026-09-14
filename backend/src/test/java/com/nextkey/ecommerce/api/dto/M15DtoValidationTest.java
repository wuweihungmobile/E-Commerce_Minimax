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
 * DEF-197 回歸測試（Sprint 156）：{@code M15Dto.CreatePostRequest}/{@code UpdatePostRequest} 的
 * {@code featuredImageUrl} 協定白名單驗證。
 *
 * <p>背景：與 {@code DEF-103}/{@code DEF-196} 同型缺口——{@code featuredImageUrl} 先前無任何協定
 * 驗證，經 {@code frontend/src/app/blog/[slug]/page.tsx}、{@code frontend/src/app/blog/page.tsx}
 * 以 {@code <img src={post.featuredImageUrl}>} 直接綁定於公開部落格頁面。與既有 DEF-103/104/196
 * 結論一致：{@code <img src>} 不會執行 {@code javascript:}/{@code data:text/html} 協定，非可利用
 * XSS，但仍是輸入驗證缺口，比照同一防禦性修復。{@code title}/{@code content} 的儲存型 XSS 已於
 * {@code DEF-105}（Sprint 154）以偵測即拒絕方式修復，本項僅補上結構不同的 URL 欄位協定驗證。
 */
class M15DtoValidationTest {

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

    private Set<ConstraintViolation<M15Dto.CreatePostRequest>> createViolationsOnFeaturedImageUrl(String featuredImageUrl) {
        M15Dto.CreatePostRequest request = M15Dto.CreatePostRequest.builder()
                .title("測試文章")
                .content("內容")
                .featuredImageUrl(featuredImageUrl)
                .build();
        return validator.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals("featuredImageUrl"))
                .collect(java.util.stream.Collectors.toSet());
    }

    private Set<ConstraintViolation<M15Dto.UpdatePostRequest>> updateViolationsOnFeaturedImageUrl(String featuredImageUrl) {
        M15Dto.UpdatePostRequest request = M15Dto.UpdatePostRequest.builder()
                .featuredImageUrl(featuredImageUrl)
                .build();
        return validator.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals("featuredImageUrl"))
                .collect(java.util.stream.Collectors.toSet());
    }

    @ParameterizedTest(name = "合法網址應通過驗證：{0}")
    @ValueSource(strings = {
            "https://example.com/cover.png",
            "/uploads/cover.png"
    })
    @DisplayName("DEF-197：合法 http(s) 網址或相對路徑應通過驗證")
    void legitimateFeaturedImageUrl_passesValidation(String featuredImageUrl) {
        assertThat(createViolationsOnFeaturedImageUrl(featuredImageUrl)).isEmpty();
        assertThat(updateViolationsOnFeaturedImageUrl(featuredImageUrl)).isEmpty();
    }

    @Test
    @DisplayName("DEF-197：featuredImageUrl 為 null（選填欄位）應通過驗證")
    void nullFeaturedImageUrl_passesValidation() {
        assertThat(createViolationsOnFeaturedImageUrl(null)).isEmpty();
        assertThat(updateViolationsOnFeaturedImageUrl(null)).isEmpty();
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
    @DisplayName("DEF-197：javascript/data/vbscript/file 協定（含大小寫與前導空白繞過）應被拒絕")
    void dangerousProtocol_failsValidation(String featuredImageUrl) {
        assertThat(createViolationsOnFeaturedImageUrl(featuredImageUrl)).isNotEmpty();
        assertThat(updateViolationsOnFeaturedImageUrl(featuredImageUrl)).isNotEmpty();
    }
}
