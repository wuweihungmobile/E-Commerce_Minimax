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
import com.nextkey.ecommerce.api.dto.faq.FaqCategoryDto;
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
                faqService.searchArticlesWithHighlight(0, 10, "reset");

        FaqArticleDto dto = result.getContent().get(0);
        assertThat(dto.getHighlightedQuestion()).contains("<mark>reset</mark>");   // 小寫命中
        assertThat(dto.getHighlightedAnswer()).contains("<mark>Reset</mark>");     // 大寫也命中（case-insensitive）
    }

    @Test
    @DisplayName("searchArticlesWithHighlight（DEF-102 安全修復）：question/answer 內含 HTML/JS 會被跳脫，不會原樣輸出可執行標籤")
    void searchWithHighlight_escapesHtmlInStoredText() {
        FaqArticle article = org.mockito.Mockito.mock(FaqArticle.class);
        when(article.getQuestion()).thenReturn("<script>alert(1)</script> how to reset password");
        when(article.getAnswer()).thenReturn("<img src=x onerror=alert(1)> click reset link");
        Page<FaqArticle> page = new PageImpl<>(List.of(article));
        when(articleRepository.searchByTenantIdAndKeyword(any(), eq("reset"), any())).thenReturn(page);

        Page<FaqArticleDto> result =
                faqService.searchArticlesWithHighlight(0, 10, "reset");

        FaqArticleDto dto = result.getContent().get(0);
        // 原始標籤不可原樣出現（否則前端 dangerouslySetInnerHTML 會直接執行）
        assertThat(dto.getHighlightedQuestion()).doesNotContain("<script>");
        assertThat(dto.getHighlightedAnswer()).doesNotContain("<img src=x onerror=alert(1)>");
        // 但內容需被跳脫保留（非靜默丟資料）
        assertThat(dto.getHighlightedQuestion()).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(dto.getHighlightedAnswer()).contains("&lt;img src=x onerror=alert(1)&gt;");
        // 高亮標籤本身（伺服器端固定產生的 <mark>）仍要正常運作
        assertThat(dto.getHighlightedQuestion()).contains("<mark>reset</mark>");
        assertThat(dto.getHighlightedAnswer()).contains("<mark>reset</mark>");
    }

    @Test
    @DisplayName("searchArticlesWithHighlight（邊界）：空白關鍵字 → 回已發布清單、不做高亮")
    void searchWithHighlight_blankKeyword_returnsPublishedNoHighlight() {
        FaqArticle article = org.mockito.Mockito.mock(FaqArticle.class);
        Page<FaqArticle> page = new PageImpl<>(List.of(article));
        when(articleRepository.findByTenantIdAndIsPublishedTrue(any(), any())).thenReturn(page);

        Page<FaqArticleDto> result =
                faqService.searchArticlesWithHighlight(0, 10, "   ");

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

    // ── Sprint 62 US-002：補齊零覆蓋方法的 happy-path 測試 ─────────────────

    private FaqCategory buildCategory(UUID id, String name, String slug) {
        return FaqCategory.builder()
                .id(id).tenantId(TENANT).name(name).slug(slug).sortOrder(0).build();
    }

    private FaqArticle buildArticle(UUID id, FaqCategory category, String question, String slug) {
        return FaqArticle.builder()
                .id(id).tenantId(TENANT).category(category)
                .question(question).answer("Answer").slug(slug)
                .sortOrder(0).isPinned(false).isPublished(true).build();
    }

    @Test
    @DisplayName("getCategories：依 sortOrder 回傳全部分類")
    void getCategories_returnsAllCategories() {
        FaqCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        when(categoryRepository.findByTenantIdOrderBySortOrderAsc(TENANT)).thenReturn(List.of(category));

        List<FaqCategoryDto> result = faqService.getCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSlug()).isEqualTo("general");
    }

    @Test
    @DisplayName("getCategory：找到 → 回傳 DTO")
    void getCategory_found_returnsDto() {
        UUID categoryId = UUID.randomUUID();
        FaqCategory category = buildCategory(categoryId, "General", "general");
        when(categoryRepository.findByIdAndTenantId(categoryId, TENANT)).thenReturn(Optional.of(category));

        FaqCategoryDto result = faqService.getCategory(categoryId);

        assertThat(result.getId()).isEqualTo(categoryId);
    }

    @Test
    @DisplayName("getCategory：找不到 → E_4000")
    void getCategory_notFound_throws() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findByIdAndTenantId(categoryId, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> faqService.getCategory(categoryId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_4000);
    }

    @Test
    @DisplayName("getCategoryBySlug：找到 → 回傳 DTO")
    void getCategoryBySlug_found_returnsDto() {
        FaqCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        when(categoryRepository.findByTenantIdAndSlug(TENANT, "general")).thenReturn(Optional.of(category));

        FaqCategoryDto result = faqService.getCategoryBySlug("general");

        assertThat(result.getSlug()).isEqualTo("general");
    }

    @Test
    @DisplayName("updateCategory：更新名稱與描述")
    void updateCategory_updatesFields() {
        UUID categoryId = UUID.randomUUID();
        FaqCategory category = buildCategory(categoryId, "Old Name", "general");
        when(categoryRepository.findByIdAndTenantId(categoryId, TENANT)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        com.nextkey.ecommerce.api.dto.faq.UpdateFaqCategoryRequest req =
                com.nextkey.ecommerce.api.dto.faq.UpdateFaqCategoryRequest.builder()
                        .name("New Name").build();

        FaqCategoryDto result = faqService.updateCategory(categoryId, req);

        assertThat(result.getName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("getArticles：無篩選條件 → 回傳已發布文章")
    void getArticles_noFilters_returnsPublished() {
        FaqCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        FaqArticle article = buildArticle(UUID.randomUUID(), category, "Q1", "q1");
        Page<FaqArticle> page = new PageImpl<>(List.of(article));
        when(articleRepository.findByTenantIdAndIsPublishedTrue(eq(TENANT), any())).thenReturn(page);

        Page<FaqArticleDto> result = faqService.getArticles(0, 10, null, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSlug()).isEqualTo("q1");
    }

    @Test
    @DisplayName("getPinnedArticles：回傳依 sortOrder 排序的置頂文章")
    void getPinnedArticles_returnsPinned() {
        FaqCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        FaqArticle article = buildArticle(UUID.randomUUID(), category, "Pinned Q", "pinned-q");
        when(articleRepository.findByTenantIdAndIsPinnedTrueOrderBySortOrderAsc(TENANT))
                .thenReturn(List.of(article));

        List<FaqArticleDto> result = faqService.getPinnedArticles();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSlug()).isEqualTo("pinned-q");
    }

    @Test
    @DisplayName("getArticle：找到 → 回傳 DTO")
    void getArticle_found_returnsDto() {
        UUID articleId = UUID.randomUUID();
        FaqCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        FaqArticle article = buildArticle(articleId, category, "Q1", "q1");
        when(articleRepository.findByIdAndTenantId(articleId, TENANT)).thenReturn(Optional.of(article));

        FaqArticleDto result = faqService.getArticle(articleId);

        assertThat(result.getId()).isEqualTo(articleId);
    }

    @Test
    @DisplayName("getArticleBySlug：找到 → 回傳 DTO")
    void getArticleBySlug_found_returnsDto() {
        FaqCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        FaqArticle article = buildArticle(UUID.randomUUID(), category, "Q1", "q1");
        when(articleRepository.findByTenantIdAndSlug(TENANT, "q1")).thenReturn(Optional.of(article));

        FaqArticleDto result = faqService.getArticleBySlug("q1");

        assertThat(result.getSlug()).isEqualTo("q1");
    }

    @Test
    @DisplayName("updateArticle：更新問題內容")
    void updateArticle_updatesFields() {
        UUID articleId = UUID.randomUUID();
        FaqCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        FaqArticle article = buildArticle(articleId, category, "Old Question", "q1");
        when(articleRepository.findByIdAndTenantId(articleId, TENANT)).thenReturn(Optional.of(article));
        when(articleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        com.nextkey.ecommerce.api.dto.faq.UpdateFaqArticleRequest req =
                com.nextkey.ecommerce.api.dto.faq.UpdateFaqArticleRequest.builder()
                        .question("New Question").build();

        FaqArticleDto result = faqService.updateArticle(articleId, req);

        assertThat(result.getQuestion()).isEqualTo("New Question");
    }

    @Test
    @DisplayName("deleteArticle：刪除成功")
    void deleteArticle_deletesSuccessfully() {
        UUID articleId = UUID.randomUUID();
        FaqCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        FaqArticle article = buildArticle(articleId, category, "Q1", "q1");
        when(articleRepository.findByIdAndTenantId(articleId, TENANT)).thenReturn(Optional.of(article));

        faqService.deleteArticle(articleId);

        org.mockito.Mockito.verify(articleRepository).delete(article);
    }

    /**
     * Sprint 106 / DEF-055：本測試原本斷言「記憶體物件上的數字 +1 並 save()」，
     * 那正是**在讀後寫競態存在時照樣全綠**的斷言（repository 已被 mock，
     * 真正的丟失更新發生在 DB 層）。改為斷言「有委派給原子敘述、且不再走 save()」，
     * 併發正確性交由 {@code ViewCountConcurrencyIntegrationTest} 以真實 DB 驗證。
     */
    @Test
    @DisplayName("incrementViewCount：委派給 DB 原子遞增，不得退回讀後寫")
    void incrementViewCount_delegatesToAtomicUpdate() {
        UUID articleId = UUID.randomUUID();
        when(articleRepository.incrementViewCount(articleId, TENANT)).thenReturn(1);

        faqService.incrementViewCount(articleId);

        org.mockito.Mockito.verify(articleRepository).incrementViewCount(articleId, TENANT);
        // 守衛：一旦有人改回「載入 → 記憶體 +1 → save()」，這兩行會立刻失敗
        org.mockito.Mockito.verify(articleRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(articleRepository, org.mockito.Mockito.never())
                .findByIdAndTenantId(any(), any());
    }

    @Test
    @DisplayName("incrementViewCount：更新 0 筆（查無文章或跨租戶）→ E_4000")
    void incrementViewCount_notFound_throws() {
        UUID articleId = UUID.randomUUID();
        when(articleRepository.incrementViewCount(articleId, TENANT)).thenReturn(0);

        assertThatThrownBy(() -> faqService.incrementViewCount(articleId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_4000);
    }
}
