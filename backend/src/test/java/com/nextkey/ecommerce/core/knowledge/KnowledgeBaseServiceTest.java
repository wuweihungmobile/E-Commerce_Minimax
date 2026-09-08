package com.nextkey.ecommerce.core.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.nextkey.ecommerce.api.dto.knowledge.CreateKnowledgeArticleRequest;
import com.nextkey.ecommerce.api.dto.knowledge.CreateKnowledgeCategoryRequest;
import com.nextkey.ecommerce.api.dto.knowledge.KnowledgeArticleDto;
import com.nextkey.ecommerce.api.dto.knowledge.KnowledgeCategoryDto;
import com.nextkey.ecommerce.api.dto.knowledge.UpdateKnowledgeArticleRequest;
import com.nextkey.ecommerce.api.dto.knowledge.UpdateKnowledgeCategoryRequest;
import com.nextkey.ecommerce.domain.model.knowledge.ArticleVersion;
import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeArticle;
import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeArticle.ArticleStatus;
import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeCategory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.knowledge.ArticleVersionRepository;
import com.nextkey.ecommerce.domain.repository.knowledge.KnowledgeArticleRepository;
import com.nextkey.ecommerce.domain.repository.knowledge.KnowledgeCategoryRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * KnowledgeBaseService 單元測試（Sprint 62 US-001）。
 *
 * <p>背景：KnowledgeBaseService（17 個 public 方法，含文章版本控制）先前完全零測試覆蓋
 * ——唯一相關的 M18KnowledgePhase2IntegrationTest 對其使用 @MockBean 繞過，未驗證真實邏輯。
 * 本測試以 Mockito mock repository 涵蓋分類 CRUD、文章 CRUD/查詢、版本控制（快照/清單/還原/排程發布）。
 */
@DisplayName("KnowledgeBaseService 單元測試")
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeArticleRepository articleRepository;
    @Mock
    private KnowledgeCategoryRepository categoryRepository;
    @Mock
    private ArticleVersionRepository articleVersionRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KnowledgeBaseService knowledgeBaseService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CURRENT_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUser(CURRENT_USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- helpers ----

    private Tenant buildTenant() {
        return Tenant.builder().id(TENANT_ID).name("Test Tenant").slug("test-tenant").build();
    }

    private User buildAuthor(UUID id) {
        return User.builder().id(id).fullName("Author Name").build();
    }

    private KnowledgeCategory buildCategory(UUID id, String name, String slug) {
        return KnowledgeCategory.builder()
                .id(id).tenantId(TENANT_ID).name(name).slug(slug).sortOrder(0).build();
    }

    private KnowledgeArticle buildArticle(UUID id, KnowledgeCategory category, User author, String title, String slug) {
        return KnowledgeArticle.builder()
                .id(id).tenantId(TENANT_ID).category(category).author(author)
                .title(title).slug(slug).content("Content").status(ArticleStatus.PUBLISHED)
                .isPinned(false).sortOrder(0).build();
    }

    // ── Category Operations ──────────────────────────────────────────

    @Test
    @DisplayName("getCategories：依 sortOrder 回傳全部分類")
    void getCategories_returnsAllCategories() {
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        when(categoryRepository.findByTenantIdOrderBySortOrderAsc(TENANT_ID)).thenReturn(List.of(category));

        List<KnowledgeCategoryDto> result = knowledgeBaseService.getCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSlug()).isEqualTo("general");
    }

    @Test
    @DisplayName("getCategory：找到 → 回傳 DTO")
    void getCategory_found_returnsDto() {
        UUID categoryId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(categoryId, "General", "general");
        when(categoryRepository.findByIdAndTenantId(categoryId, TENANT_ID)).thenReturn(Optional.of(category));

        KnowledgeCategoryDto result = knowledgeBaseService.getCategory(categoryId);

        assertThat(result.getId()).isEqualTo(categoryId);
    }

    @Test
    @DisplayName("getCategory：找不到 → E_4000")
    void getCategory_notFound_throws() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findByIdAndTenantId(categoryId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> knowledgeBaseService.getCategory(categoryId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_4000);
    }

    @Test
    @DisplayName("getCategoryBySlug：找到 → 回傳 DTO")
    void getCategoryBySlug_found_returnsDto() {
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        when(categoryRepository.findByTenantIdAndSlug(TENANT_ID, "general")).thenReturn(Optional.of(category));

        KnowledgeCategoryDto result = knowledgeBaseService.getCategoryBySlug("general");

        assertThat(result.getSlug()).isEqualTo("general");
    }

    @Test
    @DisplayName("createCategory：slug 已存在 → E_3001")
    void createCategory_duplicateSlug_throws() {
        when(categoryRepository.existsByTenantIdAndSlug(TENANT_ID, "general")).thenReturn(true);
        CreateKnowledgeCategoryRequest req = CreateKnowledgeCategoryRequest.builder()
                .name("General").slug("general").build();

        assertThatThrownBy(() -> knowledgeBaseService.createCategory(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3001);
    }

    @Test
    @DisplayName("createCategory：成功建立")
    void createCategory_success() {
        when(categoryRepository.existsByTenantIdAndSlug(TENANT_ID, "general")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateKnowledgeCategoryRequest req = CreateKnowledgeCategoryRequest.builder()
                .name("General").slug("general").build();

        KnowledgeCategoryDto result = knowledgeBaseService.createCategory(req);

        assertThat(result.getSlug()).isEqualTo("general");
    }

    @Test
    @DisplayName("updateCategory：更新名稱")
    void updateCategory_updatesFields() {
        UUID categoryId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(categoryId, "Old Name", "general");
        when(categoryRepository.findByIdAndTenantId(categoryId, TENANT_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateKnowledgeCategoryRequest req = UpdateKnowledgeCategoryRequest.builder().name("New Name").build();

        KnowledgeCategoryDto result = knowledgeBaseService.updateCategory(categoryId, req);

        assertThat(result.getName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("createCategory：併發 TOCTOU 撞上 DB 唯一約束（DEF-154）→ 轉譯為 E_3001，而非原始 500")
    void createCategory_concurrentDuplicateSlug_translatesToE3001() {
        when(categoryRepository.existsByTenantIdAndSlug(TENANT_ID, "general")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        CreateKnowledgeCategoryRequest req = CreateKnowledgeCategoryRequest.builder()
                .name("General").slug("general").build();

        assertThatThrownBy(() -> knowledgeBaseService.createCategory(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3001);
    }

    @Test
    @DisplayName("deleteCategory：分類底下仍有文章 → E_3001（不可刪）")
    void deleteCategory_withArticles_throws() {
        UUID categoryId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(categoryId, "General", "general");
        when(categoryRepository.findByIdAndTenantId(categoryId, TENANT_ID)).thenReturn(Optional.of(category));
        Page<KnowledgeArticle> nonEmpty = new PageImpl<>(List.of(buildArticle(
                UUID.randomUUID(), category, buildAuthor(UUID.randomUUID()), "Q", "q")));
        when(articleRepository.findByCategoryId(eq(categoryId), any())).thenReturn(nonEmpty);

        assertThatThrownBy(() -> knowledgeBaseService.deleteCategory(categoryId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3001);
    }

    @Test
    @DisplayName("deleteCategory：無文章 → 刪除成功")
    void deleteCategory_noArticles_deletesSuccessfully() {
        UUID categoryId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(categoryId, "General", "general");
        when(categoryRepository.findByIdAndTenantId(categoryId, TENANT_ID)).thenReturn(Optional.of(category));
        when(articleRepository.findByCategoryId(eq(categoryId), any())).thenReturn(new PageImpl<>(List.of()));

        knowledgeBaseService.deleteCategory(categoryId);

        verify(categoryRepository).delete(category);
    }

    @Test
    @DisplayName("deleteCategory：檢查通過後併發 createArticle 掛上新文章（DEF-155）"
            + " → FK RESTRICT 擋下並轉譯為 E_3001，而非原始 500")
    void deleteCategory_concurrentArticleCreated_translatesToE3001() {
        UUID categoryId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(categoryId, "General", "general");
        when(categoryRepository.findByIdAndTenantId(categoryId, TENANT_ID)).thenReturn(Optional.of(category));
        when(articleRepository.findByCategoryId(eq(categoryId), any())).thenReturn(new PageImpl<>(List.of()));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk violation"))
                .when(categoryRepository).flush();

        assertThatThrownBy(() -> knowledgeBaseService.deleteCategory(categoryId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3001);
    }

    // ── Article Operations ───────────────────────────────────────────

    @Test
    @DisplayName("getArticles：無篩選條件 → 回傳已發布文章")
    void getArticles_noFilters_returnsPublished() {
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = buildArticle(UUID.randomUUID(), category, buildAuthor(UUID.randomUUID()), "Title", "slug1");
        when(articleRepository.findByTenantIdAndStatus(eq(TENANT_ID), eq(ArticleStatus.PUBLISHED), any()))
                .thenReturn(new PageImpl<>(List.of(article)));

        Page<KnowledgeArticleDto> result = knowledgeBaseService.getArticles(0, 10, null, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSlug()).isEqualTo("slug1");
    }

    @Test
    @DisplayName("getArticle：找到 → 回傳 DTO")
    void getArticle_found_returnsDto() {
        UUID articleId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = buildArticle(articleId, category, buildAuthor(UUID.randomUUID()), "Title", "slug1");
        when(articleRepository.findByIdAndTenantId(articleId, TENANT_ID)).thenReturn(Optional.of(article));

        KnowledgeArticleDto result = knowledgeBaseService.getArticle(articleId);

        assertThat(result.getId()).isEqualTo(articleId);
    }

    @Test
    @DisplayName("getArticleBySlug：找到已發布文章 → 回傳 DTO")
    void getArticleBySlug_found_returnsDto() {
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = buildArticle(UUID.randomUUID(), category, buildAuthor(UUID.randomUUID()), "Title", "slug1");
        when(articleRepository.findPublishedBySlug("slug1")).thenReturn(Optional.of(article));

        KnowledgeArticleDto result = knowledgeBaseService.getArticleBySlug("slug1");

        assertThat(result.getSlug()).isEqualTo("slug1");
    }

    @Test
    @DisplayName("createArticle：成功建立（草稿狀態）")
    void createArticle_success() {
        UUID categoryId = UUID.randomUUID();
        Tenant tenant = buildTenant();
        KnowledgeCategory category = buildCategory(categoryId, "General", "general");
        User author = buildAuthor(CURRENT_USER_ID);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(userRepository.findById(CURRENT_USER_ID)).thenReturn(Optional.of(author));
        when(articleRepository.findByTenantIdAndSlug(TENANT_ID, "new-article")).thenReturn(Optional.empty());
        when(articleRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateKnowledgeArticleRequest req = CreateKnowledgeArticleRequest.builder()
                .categoryId(categoryId)
                .title("New Article").slug("new-article").content("Content").build();

        KnowledgeArticleDto result = knowledgeBaseService.createArticle(req);

        assertThat(result.getSlug()).isEqualTo("new-article");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("createArticle：tags 應被儲存並在 DTO 中讀回（DEF-083）")
    void createArticle_savesAndReturnsTags() {
        UUID categoryId = UUID.randomUUID();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(buildCategory(categoryId, "General", "general")));
        when(userRepository.findById(CURRENT_USER_ID)).thenReturn(Optional.of(buildAuthor(CURRENT_USER_ID)));
        when(articleRepository.findByTenantIdAndSlug(TENANT_ID, "tagged-article")).thenReturn(Optional.empty());
        when(articleRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateKnowledgeArticleRequest req = CreateKnowledgeArticleRequest.builder()
                .categoryId(categoryId)
                .title("Tagged Article").slug("tagged-article").content("Content")
                .tags(List.of("faq", "billing"))
                .build();

        KnowledgeArticleDto result = knowledgeBaseService.createArticle(req);

        assertThat(result.getTags()).containsExactly("faq", "billing");
    }

    @Test
    @DisplayName("createArticle：slug 已存在 → E_3001")
    void createArticle_duplicateSlug_throws() {
        UUID categoryId = UUID.randomUUID();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(buildCategory(categoryId, "General", "general")));
        when(userRepository.findById(CURRENT_USER_ID)).thenReturn(Optional.of(buildAuthor(CURRENT_USER_ID)));
        when(articleRepository.findByTenantIdAndSlug(TENANT_ID, "dup")).thenReturn(
                Optional.of(buildArticle(UUID.randomUUID(), buildCategory(categoryId, "General", "general"), buildAuthor(CURRENT_USER_ID), "X", "dup")));

        CreateKnowledgeArticleRequest req = CreateKnowledgeArticleRequest.builder()
                .categoryId(categoryId).title("X").slug("dup").content("C").build();

        assertThatThrownBy(() -> knowledgeBaseService.createArticle(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3001);
    }

    @Test
    @DisplayName("createArticle：併發 TOCTOU 撞上 DB 唯一約束（DEF-153）→ 轉譯為 E_3001，而非原始 500")
    void createArticle_concurrentDuplicateSlug_translatesToE3001() {
        UUID categoryId = UUID.randomUUID();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(buildCategory(categoryId, "General", "general")));
        when(userRepository.findById(CURRENT_USER_ID)).thenReturn(Optional.of(buildAuthor(CURRENT_USER_ID)));
        when(articleRepository.findByTenantIdAndSlug(TENANT_ID, "race-slug")).thenReturn(Optional.empty());
        when(articleRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        CreateKnowledgeArticleRequest req = CreateKnowledgeArticleRequest.builder()
                .categoryId(categoryId).title("X").slug("race-slug").content("C").build();

        assertThatThrownBy(() -> knowledgeBaseService.createArticle(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3001);
    }

    @Test
    @DisplayName("updateArticle：更新標題與狀態為 PUBLISHED")
    void updateArticle_updatesFieldsAndPublishes() {
        UUID articleId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = KnowledgeArticle.builder()
                .id(articleId).tenantId(TENANT_ID).category(category).author(buildAuthor(UUID.randomUUID()))
                .title("Old Title").slug("slug1").content("Content").status(ArticleStatus.DRAFT)
                .isPinned(false).sortOrder(0).build();
        when(articleRepository.findByIdAndTenantId(articleId, TENANT_ID)).thenReturn(Optional.of(article));
        when(articleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateKnowledgeArticleRequest req = UpdateKnowledgeArticleRequest.builder()
                .title("New Title").status("PUBLISHED").build();

        KnowledgeArticleDto result = knowledgeBaseService.updateArticle(articleId, req);

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateArticle：更新 tags 應被儲存並在 DTO 中讀回（DEF-083）")
    void updateArticle_updatesTags() {
        UUID articleId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = KnowledgeArticle.builder()
                .id(articleId).tenantId(TENANT_ID).category(category).author(buildAuthor(UUID.randomUUID()))
                .title("Title").slug("slug1").content("Content").status(ArticleStatus.DRAFT)
                .isPinned(false).sortOrder(0).build();
        when(articleRepository.findByIdAndTenantId(articleId, TENANT_ID)).thenReturn(Optional.of(article));
        when(articleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateKnowledgeArticleRequest req = UpdateKnowledgeArticleRequest.builder()
                .tags(List.of("updated-tag")).build();

        KnowledgeArticleDto result = knowledgeBaseService.updateArticle(articleId, req);

        assertThat(result.getTags()).containsExactly("updated-tag");
    }

    @Test
    @DisplayName("deleteArticle：刪除成功")
    void deleteArticle_deletesSuccessfully() {
        UUID articleId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = buildArticle(articleId, category, buildAuthor(UUID.randomUUID()), "Title", "slug1");
        when(articleRepository.findByIdAndTenantId(articleId, TENANT_ID)).thenReturn(Optional.of(article));

        knowledgeBaseService.deleteArticle(articleId);

        verify(articleRepository).delete(article);
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
        when(articleRepository.incrementViewCount(articleId, TENANT_ID)).thenReturn(1);

        knowledgeBaseService.incrementViewCount(articleId);

        verify(articleRepository).incrementViewCount(articleId, TENANT_ID);
        // 守衛：一旦有人改回「載入 → 記憶體 +1 → save()」，這兩行會立刻失敗
        verify(articleRepository, org.mockito.Mockito.never()).save(any());
        verify(articleRepository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    @DisplayName("incrementViewCount：更新 0 筆（查無文章或跨租戶）→ E_4000")
    void incrementViewCount_notFound_throws() {
        UUID articleId = UUID.randomUUID();
        when(articleRepository.incrementViewCount(articleId, TENANT_ID)).thenReturn(0);

        assertThatThrownBy(() -> knowledgeBaseService.incrementViewCount(articleId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_4000);
    }

    // ── Article Version Control ──────────────────────────────────────

    @Test
    @DisplayName("createVersionSnapshot：首次快照版本號為 1")
    void createVersionSnapshot_firstVersion_isOne() {
        UUID articleId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = buildArticle(articleId, category, buildAuthor(UUID.randomUUID()), "Title", "slug1");
        when(articleRepository.findByIdAndTenantIdForUpdate(articleId, TENANT_ID)).thenReturn(Optional.of(article));
        when(articleVersionRepository.findMaxVersionNumberByArticleId(articleId)).thenReturn(null);

        knowledgeBaseService.createVersionSnapshot(articleId);

        org.mockito.ArgumentCaptor<ArticleVersion> captor = org.mockito.ArgumentCaptor.forClass(ArticleVersion.class);
        verify(articleVersionRepository).save(captor.capture());
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(captor.getValue().getTitle()).isEqualTo("Title");
    }

    @Test
    @DisplayName("createVersionSnapshot：已有版本時遞增版本號")
    void createVersionSnapshot_incrementsFromExisting() {
        UUID articleId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = buildArticle(articleId, category, buildAuthor(UUID.randomUUID()), "Title", "slug1");
        when(articleRepository.findByIdAndTenantIdForUpdate(articleId, TENANT_ID)).thenReturn(Optional.of(article));
        when(articleVersionRepository.findMaxVersionNumberByArticleId(articleId)).thenReturn(3);

        knowledgeBaseService.createVersionSnapshot(articleId);

        org.mockito.ArgumentCaptor<ArticleVersion> captor = org.mockito.ArgumentCaptor.forClass(ArticleVersion.class);
        verify(articleVersionRepository).save(captor.capture());
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(4);
    }

    /**
     * DEF-120：{@code createVersionSnapshot} 必須用悲觀鎖版本的查詢載入文章，序列化
     * 「讀最大版本號 → +1 → INSERT」，否則兩個併發呼叫可能算出相同版本號各自成功 INSERT
     * （{@code article_versions} 沒有 unique 約束兜底）。守衛：一旦有人改回不上鎖的
     * {@code findByIdAndTenantId}，這個測試會立刻失敗。
     */
    @Test
    @DisplayName("createVersionSnapshot：必須用悲觀鎖查詢載入文章，不得退回不上鎖的查詢")
    void createVersionSnapshot_usesLockedLookup() {
        UUID articleId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = buildArticle(articleId, category, buildAuthor(UUID.randomUUID()), "Title", "slug1");
        when(articleRepository.findByIdAndTenantIdForUpdate(articleId, TENANT_ID)).thenReturn(Optional.of(article));
        when(articleVersionRepository.findMaxVersionNumberByArticleId(articleId)).thenReturn(null);

        knowledgeBaseService.createVersionSnapshot(articleId);

        verify(articleRepository).findByIdAndTenantIdForUpdate(articleId, TENANT_ID);
        verify(articleRepository, org.mockito.Mockito.never()).findByIdAndTenantId(any(), any());
    }

    @Test
    @DisplayName("getArticleVersions：文章不存在 → E_4000")
    void getArticleVersions_articleNotFound_throws() {
        UUID articleId = UUID.randomUUID();
        when(articleRepository.findByIdAndTenantId(articleId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> knowledgeBaseService.getArticleVersions(articleId, 0, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_4000);
    }

    @Test
    @DisplayName("getArticleVersions：回傳分頁版本清單")
    void getArticleVersions_returnsPage() {
        UUID articleId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = buildArticle(articleId, category, buildAuthor(UUID.randomUUID()), "Title", "slug1");
        when(articleRepository.findByIdAndTenantId(articleId, TENANT_ID)).thenReturn(Optional.of(article));
        ArticleVersion version = ArticleVersion.builder()
                .id(UUID.randomUUID()).article(article).versionNumber(1).title("Title").content("Content").build();
        when(articleVersionRepository.findByArticleIdAndTenantId(eq(articleId), eq(TENANT_ID), any()))
                .thenReturn(new PageImpl<>(List.of(version)));

        Page<ArticleVersion> result = knowledgeBaseService.getArticleVersions(articleId, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getVersionNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("getArticleVersion：找不到指定版本 → E_4000")
    void getArticleVersion_notFound_throws() {
        UUID articleId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = buildArticle(articleId, category, buildAuthor(UUID.randomUUID()), "Title", "slug1");
        when(articleRepository.findByIdAndTenantId(articleId, TENANT_ID)).thenReturn(Optional.of(article));
        when(articleVersionRepository.findByArticleIdAndVersionNumber(articleId, 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> knowledgeBaseService.getArticleVersion(articleId, 99))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_4000);
    }

    @Test
    @DisplayName("restoreVersion：還原指定版本內容並先建立目前版本快照")
    void restoreVersion_restoresContentAndSnapshotsCurrent() {
        UUID articleId = UUID.randomUUID();
        KnowledgeCategory originalCategory = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = buildArticle(articleId, originalCategory, buildAuthor(UUID.randomUUID()), "Current Title", "slug1");
        when(articleRepository.findByIdAndTenantId(articleId, TENANT_ID)).thenReturn(Optional.of(article));
        when(articleRepository.findByIdAndTenantIdForUpdate(articleId, TENANT_ID)).thenReturn(Optional.of(article));
        when(articleVersionRepository.findMaxVersionNumberByArticleId(articleId)).thenReturn(1);
        when(articleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeCategory versionCategory = buildCategory(UUID.randomUUID(), "Old Cat", "old-cat");
        ArticleVersion version = ArticleVersion.builder()
                .id(UUID.randomUUID()).versionNumber(1)
                .title("Old Title").content("Old Content").category(versionCategory)
                .isPinned(true).sortOrder(5).build();
        when(articleVersionRepository.findByArticleIdAndVersionNumber(articleId, 1)).thenReturn(Optional.of(version));

        KnowledgeArticleDto result = knowledgeBaseService.restoreVersion(articleId, 1);

        assertThat(result.getTitle()).isEqualTo("Old Title");
        verify(articleVersionRepository).save(any(ArticleVersion.class)); // 還原前先快照目前版本
    }

    @Test
    @DisplayName("schedulePublish：設定排程發布時間")
    void schedulePublish_setsScheduledTime() {
        UUID articleId = UUID.randomUUID();
        KnowledgeCategory category = buildCategory(UUID.randomUUID(), "General", "general");
        KnowledgeArticle article = buildArticle(articleId, category, buildAuthor(UUID.randomUUID()), "Title", "slug1");
        when(articleRepository.findByIdAndTenantId(articleId, TENANT_ID)).thenReturn(Optional.of(article));
        when(articleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant scheduledTime = Instant.parse("2028-01-01T00:00:00Z");
        KnowledgeArticleDto result = knowledgeBaseService.schedulePublish(articleId, scheduledTime);

        assertThat(result.getSlug()).isEqualTo("slug1");
        assertThat(article.getScheduledPublishAt()).isEqualTo(scheduledTime);
    }
}
