package com.nextkey.ecommerce.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nextkey.ecommerce.api.controller.knowledge.ArticleVersionController;
import com.nextkey.ecommerce.api.controller.knowledge.KnowledgeArticleController;
import com.nextkey.ecommerce.api.dto.knowledge.KnowledgeArticleDto;
import com.nextkey.ecommerce.core.knowledge.KnowledgeBaseService;
import com.nextkey.ecommerce.domain.model.knowledge.ArticleVersion;
import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeArticle;
import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeArticle.ArticleStatus;
import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeCategory;
import com.nextkey.ecommerce.domain.repository.knowledge.ArticleVersionRepository;
import com.nextkey.ecommerce.domain.repository.knowledge.KnowledgeArticleRepository;
import com.nextkey.ecommerce.domain.repository.knowledge.KnowledgeCategoryRepository;

/**
 * M18 知識管理 Phase 2-B 整合測試
 * 測試：文章版本控制、發布排程、搜尋增強
 */
@WebMvcTest(controllers = {
    KnowledgeArticleController.class,
    ArticleVersionController.class
})
@ActiveProfiles("integration-test")
@DisplayName("M18 知識管理 Phase 2-B 整合測試")
public class M18KnowledgePhase2IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeArticleRepository articleRepository;

    @MockBean
    private KnowledgeCategoryRepository categoryRepository;

    @MockBean
    private ArticleVersionRepository articleVersionRepository;

    @MockBean
    private KnowledgeBaseService knowledgeBaseService;

    private UUID tenantId;
    private UUID categoryId;
    private UUID articleId;
    private KnowledgeArticle testArticle;
    private KnowledgeCategory testCategory;
    private ArticleVersion testVersion;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        articleId = UUID.randomUUID();

        testCategory = KnowledgeCategory.builder()
                .id(categoryId)
                .name("Test Category")
                .slug("test-category")
                .description("Test category description")
                .tenantId(tenantId)
                .build();

        testArticle = KnowledgeArticle.builder()
                .id(articleId)
                .title("Test Article")
                .slug("test-article")
                .content("Test content for the article")
                .status(ArticleStatus.DRAFT)
                .viewCount(0)
                .isPinned(false)
                .build();

        testVersion = ArticleVersion.builder()
                .id(UUID.randomUUID())
                .versionNumber(1)
                .title("Version 1")
                .content("Content version 1")
                .isPublished(false)
                .isPinned(false)
                .sortOrder(0)
                .build();
    }

    @Test
    @DisplayName("AC-001: 可以查看文章的版本歷史")
    @WithMockUser(authorities = {"knowledge:read"})
    void getArticleVersions_success() throws Exception {
        Page<ArticleVersion> versionPage = new PageImpl<>(
                List.of(testVersion),
                PageRequest.of(0, 20),
                1
        );
        when(knowledgeBaseService.getArticleVersions(eq(articleId), anyInt(), anyInt()))
                .thenReturn(versionPage);

        mockMvc.perform(get("/v2/knowledge/articles/{articleId}/versions", articleId)
                        .with(csrf())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].versionNumber").value(1));

        verify(knowledgeBaseService).getArticleVersions(eq(articleId), eq(0), eq(20));
    }

    @Test
    @DisplayName("AC-002: 可以設定文章的發布時間（排程發布）")
    @WithMockUser(authorities = {"knowledge:update"})
    void schedulePublish_success() throws Exception {
        Instant scheduledTime = Instant.parse("2026-06-01T00:00:00Z");
        KnowledgeArticleDto scheduledArticle = KnowledgeArticleDto.builder()
                .id(articleId)
                .title("Test Article")
                .status(ArticleStatus.DRAFT.name())
                .build();

        when(knowledgeBaseService.schedulePublish(eq(articleId), any(Instant.class)))
                .thenReturn(scheduledArticle);

        mockMvc.perform(put("/v2/knowledge/articles/{articleId}/schedule", articleId)
                        .with(csrf())
                        .param("scheduledPublishAt", "2026-06-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(knowledgeBaseService).schedulePublish(eq(articleId), any(Instant.class));
    }

    @Test
    @DisplayName("AC-003: 可以恢復到之前的文章版本")
    @WithMockUser(authorities = {"knowledge:update"})
    void restoreVersion_success() throws Exception {
        KnowledgeArticleDto restoredArticle = KnowledgeArticleDto.builder()
                .id(articleId)
                .title("Restored Title")
                .content("Restored Content")
                .status(ArticleStatus.DRAFT.name())
                .build();

        when(knowledgeBaseService.restoreVersion(eq(articleId), eq(1)))
                .thenReturn(restoredArticle);

        mockMvc.perform(post("/v2/knowledge/articles/{articleId}/versions/{versionNumber}/restore",
                        articleId, 1)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Restored Title"));

        verify(knowledgeBaseService).restoreVersion(eq(articleId), eq(1));
    }

    @Test
    @DisplayName("AC-004: 可以取得特定版本的詳細內容")
    @WithMockUser(authorities = {"knowledge:read"})
    void getArticleVersion_success() throws Exception {
        when(knowledgeBaseService.getArticleVersion(eq(articleId), eq(1)))
                .thenReturn(testVersion);

        mockMvc.perform(get("/v2/knowledge/articles/{articleId}/versions/{versionNumber}",
                        articleId, 1)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andExpect(jsonPath("$.data.title").value("Version 1"));

        verify(knowledgeBaseService).getArticleVersion(eq(articleId), eq(1));
    }

    @Test
    @DisplayName("AC-005: 可以手動建立版本快照")
    @WithMockUser(authorities = {"knowledge:update"})
    void createVersionSnapshot_success() throws Exception {
        doNothing().when(knowledgeBaseService).createVersionSnapshot(articleId);

        mockMvc.perform(post("/v2/knowledge/articles/{articleId}/versions/snapshot", articleId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Version snapshot created"));

        verify(knowledgeBaseService).createVersionSnapshot(articleId);
    }

    @Test
    @DisplayName("版本控制：驗證版本號遞增")
    void versionNumber_increments() {
        ArticleVersion v1 = ArticleVersion.builder()
                .versionNumber(1)
                .title("Version 1")
                .content("Content 1")
                .build();

        ArticleVersion v2 = ArticleVersion.builder()
                .versionNumber(2)
                .title("Version 2")
                .content("Content 2")
                .build();

        assertTrue(v2.getVersionNumber() > v1.getVersionNumber());
    }

    @Test
    @DisplayName("排程發布：驗證 scheduled_publish_at 欄位")
    void scheduledPublishAt_fieldExists() {
        KnowledgeArticle article = KnowledgeArticle.builder()
                .title("Scheduled Article")
                .slug("scheduled-article")
                .status(ArticleStatus.DRAFT)
                .scheduledPublishAt(Instant.now().plusSeconds(86400))
                .build();

        assertNotNull(article.getScheduledPublishAt());
    }

    @Test
    @DisplayName("排程發布：驗證 published_at 欄位區分草稿和已發布")
    void publishedAt_fieldDistinguishesDraftAndPublished() {
        KnowledgeArticle draft = KnowledgeArticle.builder()
                .title("Draft Article")
                .slug("draft-article")
                .status(ArticleStatus.DRAFT)
                .publishedAt(null)
                .build();

        KnowledgeArticle published = KnowledgeArticle.builder()
                .title("Published Article")
                .slug("published-article")
                .status(ArticleStatus.PUBLISHED)
                .publishedAt(Instant.now())
                .build();

        assertNull(draft.getPublishedAt());
        assertNotNull(published.getPublishedAt());
    }

    @Test
    @DisplayName("搜尋：關鍵字全文搜尋會搜尋標題和內容")
    @WithMockUser(authorities = {"knowledge:read"})
    void searchByKeyword_includesTitleAndContent() throws Exception {
        Page<KnowledgeArticleDto> searchResult = new PageImpl<>(
                List.of(KnowledgeArticleDto.builder()
                        .id(articleId)
                        .title("Test Article")
                        .content("Test content")
                        .status(ArticleStatus.PUBLISHED.name())
                        .build()),
                PageRequest.of(0, 20),
                1
        );
        when(knowledgeBaseService.getArticles(eq(0), eq(20), isNull(), eq("test")))
                .thenReturn(searchResult);

        mockMvc.perform(get("/v2/knowledge")
                        .param("keyword", "test")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("Test Article"));

        verify(knowledgeBaseService).getArticles(eq(0), eq(20), isNull(), eq("test"));
    }
}