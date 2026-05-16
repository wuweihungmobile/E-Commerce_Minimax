package com.nextkey.ecommerce.api.controller.faq;

import java.util.List;
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
import com.nextkey.ecommerce.api.dto.faq.CreateFaqArticleRequest;
import com.nextkey.ecommerce.api.dto.faq.FaqArticleDto;
import com.nextkey.ecommerce.api.dto.faq.UpdateFaqArticleRequest;
import com.nextkey.ecommerce.core.faq.FaqService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v2/faqs")
@RequiredArgsConstructor
public class FaqArticleController {

    private final FaqService faqService;

    @GetMapping
    @PreAuthorize("hasAuthority('faq:read')")
    public ResponseEntity<ApiResponse<Page<FaqArticleDto>>> getArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String keyword) {
        Page<FaqArticleDto> articles = faqService.getArticles(page, size, categoryId, keyword);
        return ResponseEntity.ok(ApiResponse.success(articles));
    }

    @GetMapping("/{articleId}")
    @PreAuthorize("hasAuthority('faq:read')")
    public ResponseEntity<ApiResponse<FaqArticleDto>> getArticle(@PathVariable UUID articleId) {
        FaqArticleDto article = faqService.getArticle(articleId);
        return ResponseEntity.ok(ApiResponse.success(article));
    }

    @GetMapping("/slug/{slug}")
    @PreAuthorize("hasAuthority('faq:read')")
    public ResponseEntity<ApiResponse<FaqArticleDto>> getArticleBySlug(@PathVariable String slug) {
        FaqArticleDto article = faqService.getArticleBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(article));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('faq:create')")
    public ResponseEntity<ApiResponse<FaqArticleDto>> createArticle(
            @Valid @RequestBody CreateFaqArticleRequest request) {
        FaqArticleDto article = faqService.createArticle(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Article created successfully", article));
    }

    @PutMapping("/{articleId}")
    @PreAuthorize("hasAuthority('faq:update')")
    public ResponseEntity<ApiResponse<FaqArticleDto>> updateArticle(
            @PathVariable UUID articleId,
            @Valid @RequestBody UpdateFaqArticleRequest request) {
        FaqArticleDto article = faqService.updateArticle(articleId, request);
        return ResponseEntity.ok(ApiResponse.success("Article updated successfully", article));
    }

    @DeleteMapping("/{articleId}")
    @PreAuthorize("hasAuthority('faq:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteArticle(@PathVariable UUID articleId) {
        faqService.deleteArticle(articleId);
        return ResponseEntity.ok(ApiResponse.success("Article deleted successfully", null));
    }

    @PostMapping("/{articleId}/view")
    @PreAuthorize("hasAuthority('faq:read')")
    public ResponseEntity<ApiResponse<Void>> incrementViewCount(@PathVariable UUID articleId) {
        faqService.incrementViewCount(articleId);
        return ResponseEntity.ok(ApiResponse.success("View count incremented", null));
    }

    // ========== Phase 2-C: FAQ 進階功能 ==========

    /**
     * 取得置頂文章列表
     * AC-001: 支援 FAQ 文章置頂排序
     */
    @GetMapping("/pinned")
    @PreAuthorize("hasAuthority('faq:read')")
    public ResponseEntity<ApiResponse<List<FaqArticleDto>>> getPinnedArticles() {
        List<FaqArticleDto> articles = faqService.getPinnedArticles();
        return ResponseEntity.ok(ApiResponse.success(articles));
    }

    /**
     * 搜尋文章並高亮關鍵字
     * AC-002: 支援搜尋關鍵字高亮顯示
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('faq:read')")
    public ResponseEntity<ApiResponse<Page<FaqArticleDto>>> searchArticlesWithHighlight(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String highlightPrefix,
            @RequestParam(required = false) String highlightSuffix) {
        Page<FaqArticleDto> articles = faqService.searchArticlesWithHighlight(
                page, size, keyword, highlightPrefix, highlightSuffix);
        return ResponseEntity.ok(ApiResponse.success(articles));
    }
}