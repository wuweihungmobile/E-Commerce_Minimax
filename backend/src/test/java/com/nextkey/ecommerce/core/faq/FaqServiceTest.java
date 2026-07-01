package com.nextkey.ecommerce.core.faq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.nextkey.ecommerce.api.dto.faq.CreateFaqArticleRequest;
import com.nextkey.ecommerce.api.dto.faq.CreateFaqCategoryRequest;
import com.nextkey.ecommerce.api.dto.faq.FaqArticleDto;
import com.nextkey.ecommerce.domain.model.faq.FaqArticle;
import com.nextkey.ecommerce.domain.model.faq.FaqCategory;
import com.nextkey.ecommerce.domain.repository.faq.FaqArticleRepository;
import com.nextkey.ecommerce.domain.repository.faq.FaqCategoryRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * FaqService 單元測試（Sprint 28 US-001 / AI-1202）。
 *
 * <p>背景：FaqService 先前 0 測試。本測試聚焦其業務規則與獨有邏輯：
 * slug 唯一性守門、含文章分類不可刪、建立文章需分類存在、關鍵字高亮（大小寫不敏感 + 預設 mark）、
 * 分類統計聚合。以 Mockito mock repository 與 entity、設置 TenantContext。
 */
@DisplayName("FaqService 單元測試（業務規則 + 關鍵字高亮）")
@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock
    private FaqArticleRepository articleRepository;
    @Mock
    private FaqCategoryRepository categoryRepository;

    @InjectMocks
    private FaqService faqService;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("createCategory：slug 已存在 → E_3001")
    void createCategory_duplicateSlug_throws() {
        when(categoryRepository.existsByTenantIdAndSlug(any(), eq("faq"))).thenReturn(true);
        CreateFaqCategoryRequest req = CreateFaqCategoryRequest.builder()
                .name("FAQ").slug("faq").build();

        assertThatThrownBy(() -> faqService.createCategory(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3001);
    }

    @Test
    @DisplayName("deleteCategory：分類底下仍有文章 → E_3001（不可刪）")
    void deleteCategory_withArticles_throws() {
        UUID categoryId = UUID.randomUUID();
        FaqCategory category = org.mockito.Mockito.mock(FaqCategory.class);
        when(categoryRepository.findByIdAndTenantId(eq(categoryId), any())).thenReturn(Optional.of(category));
        Page<FaqArticle> nonEmpty = new PageImpl<>(List.of(org.mockito.Mockito.mock(FaqArticle.class)));
        when(articleRepository.findByCategoryId(eq(categoryId), any())).thenReturn(nonEmpty);

        assertThatThrownBy(() -> faqService.deleteCategory(categoryId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3001);
    }

    @Test
    @DisplayName("createArticle：分類不存在 → E_4000")
    void createArticle_categoryNotFound_throws() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findByIdAndTenantId(eq(categoryId), any())).thenReturn(Optional.empty());
        CreateFaqArticleRequest req = CreateFaqArticleRequest.builder()
                .categoryId(categoryId).question("Q").answer("A").slug("q").build();

        assertThatThrownBy(() -> faqService.createArticle(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_4000);
    }

    @Test
    @DisplayName("searchArticlesWithHighlight：關鍵字高亮，大小寫不敏感、預設 <mark>")
    void searchWithHighlight_highlightsKeyword_caseInsensitive() {
        FaqArticle article = org.mockito.Mockito.mock(FaqArticle.class);
        when(article.getQuestion()).thenReturn("How to reset your password");
        when(article.getAnswer()).thenReturn("Click the Reset link");
        Page<FaqArticle> page = new PageImpl<>(List.of(article));
        when(articleRepository.searchByTenantIdAndKeyword(any(), eq("reset"), any())).thenReturn(page);

        Page<FaqArticleDto> result =
                faqService.searchArticlesWithHighlight(0, 10, "reset", null, null);

        FaqArticleDto dto = result.getContent().get(0);
        assertThat(dto.getHighlightedQuestion()).contains("<mark>reset</mark>");   // 小寫命中
        assertThat(dto.getHighlightedAnswer()).contains("<mark>Reset</mark>");     // 大寫也命中（case-insensitive）
    }

    @Test
    @DisplayName("searchArticlesWithHighlight（邊界）：空白關鍵字 → 回已發布清單、不做高亮")
    void searchWithHighlight_blankKeyword_returnsPublishedNoHighlight() {
        FaqArticle article = org.mockito.Mockito.mock(FaqArticle.class);
        Page<FaqArticle> page = new PageImpl<>(List.of(article));
        when(articleRepository.findByTenantIdAndIsPublishedTrue(any(), any())).thenReturn(page);

        Page<FaqArticleDto> result =
                faqService.searchArticlesWithHighlight(0, 10, "   ", null, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getHighlightedQuestion()).isNull(); // 未套高亮
    }

    @Test
    @DisplayName("getCategoryStats：聚合每分類的文章總數與已發布數")
    void getCategoryStats_mapsCounts() {
        UUID catId = UUID.randomUUID();
        FaqCategory category = org.mockito.Mockito.mock(FaqCategory.class);
        when(category.getId()).thenReturn(catId);
        when(category.getName()).thenReturn("General");
        when(category.getSlug()).thenReturn("general");
        when(categoryRepository.findByTenantIdOrderBySortOrderAsc(any())).thenReturn(List.of(category));
        when(articleRepository.countByTenantIdAndCategoryId(any(), eq(catId))).thenReturn(5L);
        when(articleRepository.countByTenantIdAndCategoryIdAndIsPublishedTrue(any(), eq(catId))).thenReturn(3L);

        List<FaqService.CategoryStatsDto> stats = faqService.getCategoryStats();

        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).categoryName()).isEqualTo("General");
        assertThat(stats.get(0).totalArticles()).isEqualTo(5L);
        assertThat(stats.get(0).publishedArticles()).isEqualTo(3L);
    }
}
