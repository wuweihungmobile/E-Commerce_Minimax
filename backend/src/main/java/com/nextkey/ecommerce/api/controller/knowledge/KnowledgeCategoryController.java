package com.nextkey.ecommerce.api.controller.knowledge;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

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
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.knowledge.CreateKnowledgeCategoryRequest;
import com.nextkey.ecommerce.api.dto.knowledge.KnowledgeCategoryDto;
import com.nextkey.ecommerce.api.dto.knowledge.UpdateKnowledgeCategoryRequest;
import com.nextkey.ecommerce.core.knowledge.KnowledgeBaseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知識庫分類 REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2/knowledge/categories")
@RequiredArgsConstructor
public class KnowledgeCategoryController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 取得分類列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('knowledge:read')")
    public ResponseEntity<ApiResponse<List<KnowledgeCategoryDto>>> getCategories() {
        List<KnowledgeCategoryDto> categories = knowledgeBaseService.getCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    /**
     * 取得分類詳情
     */
    @GetMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('knowledge:read')")
    public ResponseEntity<ApiResponse<KnowledgeCategoryDto>> getCategory(
            @PathVariable UUID categoryId) {
        KnowledgeCategoryDto category = knowledgeBaseService.getCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    /**
     * 取得分類詳情（依 slug）
     */
    @GetMapping("/slug/{slug}")
    @PreAuthorize("hasAuthority('knowledge:read')")
    public ResponseEntity<ApiResponse<KnowledgeCategoryDto>> getCategoryBySlug(
            @PathVariable String slug) {
        KnowledgeCategoryDto category = knowledgeBaseService.getCategoryBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    /**
     * 建立分類
     */
    @PostMapping
    @PreAuthorize("hasAuthority('knowledge:create')")
    public ResponseEntity<ApiResponse<KnowledgeCategoryDto>> createCategory(
            @Valid @RequestBody CreateKnowledgeCategoryRequest request) {
        KnowledgeCategoryDto category = knowledgeBaseService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", category));
    }

    /**
     * 更新分類
     */
    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('knowledge:update')")
    public ResponseEntity<ApiResponse<KnowledgeCategoryDto>> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateKnowledgeCategoryRequest request) {
        KnowledgeCategoryDto category = knowledgeBaseService.updateCategory(categoryId, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", category));
    }

    /**
     * 刪除分類
     */
    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('knowledge:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable UUID categoryId) {
        knowledgeBaseService.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully", null));
    }
}