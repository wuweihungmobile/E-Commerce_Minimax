package com.nextkey.ecommerce.api.controller.knowledge;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.knowledge.ArticleVersionDto;
import com.nextkey.ecommerce.api.dto.knowledge.KnowledgeArticleDto;
import com.nextkey.ecommerce.core.knowledge.KnowledgeBaseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知識庫文章版本控制 REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2/knowledge/articles")
@RequiredArgsConstructor
public class ArticleVersionController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 取得文章的版本歷史
     */
    @GetMapping("/{articleId}/versions")
    @PreAuthorize("hasAuthority('knowledge:read')")
    public ResponseEntity<ApiResponse<Page<ArticleVersionDto>>> getArticleVersions(
            @PathVariable UUID articleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ArticleVersionDto> versions = knowledgeBaseService.getArticleVersions(articleId, page, size);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    /**
     * 取得文章的特定版本
     */
    @GetMapping("/{articleId}/versions/{versionNumber}")
    @PreAuthorize("hasAuthority('knowledge:read')")
    public ResponseEntity<ApiResponse<ArticleVersionDto>> getArticleVersion(
            @PathVariable UUID articleId,
            @PathVariable Integer versionNumber) {
        ArticleVersionDto version = knowledgeBaseService.getArticleVersion(articleId, versionNumber);
        return ResponseEntity.ok(ApiResponse.success(version));
    }

    /**
     * 恢復到指定版本
     */
    @PostMapping("/{articleId}/versions/{versionNumber}/restore")
    @PreAuthorize("hasAuthority('knowledge:update')")
    public ResponseEntity<ApiResponse<KnowledgeArticleDto>> restoreVersion(
            @PathVariable UUID articleId,
            @PathVariable Integer versionNumber) {
        KnowledgeArticleDto article = knowledgeBaseService.restoreVersion(articleId, versionNumber);
        return ResponseEntity.ok(ApiResponse.success("Article restored to version " + versionNumber, article));
    }

    /**
     * 手動建立版本快照
     */
    @PostMapping("/{articleId}/versions/snapshot")
    @PreAuthorize("hasAuthority('knowledge:update')")
    public ResponseEntity<ApiResponse<Void>> createVersionSnapshot(
            @PathVariable UUID articleId) {
        knowledgeBaseService.createVersionSnapshot(articleId);
        return ResponseEntity.ok(ApiResponse.success("Version snapshot created", null));
    }
}