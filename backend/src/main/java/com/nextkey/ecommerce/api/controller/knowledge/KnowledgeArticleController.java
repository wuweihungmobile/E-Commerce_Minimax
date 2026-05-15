package com.nextkey.ecommerce.api.controller.knowledge;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.knowledge.CreateKnowledgeArticleRequest;
import com.nextkey.ecommerce.api.dto.knowledge.KnowledgeArticleDto;
import com.nextkey.ecommerce.api.dto.knowledge.UpdateKnowledgeArticleRequest;
import com.nextkey.ecommerce.core.knowledge.KnowledgeBaseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知識庫文章 REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2/knowledge")
@RequiredArgsConstructor
public class KnowledgeArticleController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 取得文章列表（分頁/篩選）
     */
    @GetMapping
    @PreAuthorize("hasAuthority('knowledge:read')")
    public ResponseEntity<ApiResponse<Page<KnowledgeArticleDto>>> getArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String keyword) {
        Page<KnowledgeArticleDto> articles = knowledgeBaseService.getArticles(page, size, categoryId, keyword);
        return ResponseEntity.ok(ApiResponse.success(articles));
    }

    /**
     * 取得文章詳情
     */
    @GetMapping("/{articleId}")
    @PreAuthorize("hasAuthority('knowledge:read')")
    public ResponseEntity<ApiResponse<KnowledgeArticleDto>> getArticle(
            @PathVariable UUID articleId) {
        KnowledgeArticleDto article = knowledgeBaseService.getArticle(articleId);
        return ResponseEntity.ok(ApiResponse.success(article));
    }

    /**
     * 取得文章詳情（依 slug）
     */
    @GetMapping("/slug/{slug}")
    @PreAuthorize("hasAuthority('knowledge:read')")
    public ResponseEntity<ApiResponse<KnowledgeArticleDto>> getArticleBySlug(
            @PathVariable String slug) {
        KnowledgeArticleDto article = knowledgeBaseService.getArticleBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(article));
    }

    /**
     * 建立文章
     */
    @PostMapping
    @PreAuthorize("hasAuthority('knowledge:create')")
    public ResponseEntity<ApiResponse<KnowledgeArticleDto>> createArticle(
            @Valid @RequestBody CreateKnowledgeArticleRequest request) {
        KnowledgeArticleDto article = knowledgeBaseService.createArticle(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Article created successfully", article));
    }

    /**
     * 更新文章
     */
    @PutMapping("/{articleId}")
    @PreAuthorize("hasAuthority('knowledge:update')")
    public ResponseEntity<ApiResponse<KnowledgeArticleDto>> updateArticle(
            @PathVariable UUID articleId,
            @Valid @RequestBody UpdateKnowledgeArticleRequest request) {
        KnowledgeArticleDto article = knowledgeBaseService.updateArticle(articleId, request);
        return ResponseEntity.ok(ApiResponse.success("Article updated successfully", article));
    }

    /**
     * 刪除文章
     */
    @DeleteMapping("/{articleId}")
    @PreAuthorize("hasAuthority('knowledge:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteArticle(
            @PathVariable UUID articleId) {
        knowledgeBaseService.deleteArticle(articleId);
        return ResponseEntity.ok(ApiResponse.success("Article deleted successfully", null));
    }

    /**
     * 增加文章瀏覽次數
     */
    @PostMapping("/{articleId}/view")
    @PreAuthorize("hasAuthority('knowledge:read')")
    public ResponseEntity<ApiResponse<Void>> incrementViewCount(
            @PathVariable UUID articleId) {
        knowledgeBaseService.incrementViewCount(articleId);
        return ResponseEntity.ok(ApiResponse.success("View count incremented", null));
    }
}