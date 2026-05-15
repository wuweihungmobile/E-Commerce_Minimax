package com.nextkey.ecommerce.api.controller;

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
import com.nextkey.ecommerce.api.dto.media.CreateMediaCategoryRequest;
import com.nextkey.ecommerce.api.dto.media.MediaCategoryDto;
import com.nextkey.ecommerce.api.dto.media.UpdateMediaCategoryRequest;
import com.nextkey.ecommerce.core.media.MediaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 媒體分類 REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2/media/categories")
@RequiredArgsConstructor
public class MediaCategoryController {

    private final MediaService mediaService;

    /**
     * 取得分類列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('media:read')")
    public ResponseEntity<ApiResponse<List<MediaCategoryDto>>> getCategories() {
        List<MediaCategoryDto> categories = mediaService.getCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    /**
     * 取得根分類列表
     */
    @GetMapping("/root")
    @PreAuthorize("hasAuthority('media:read')")
    public ResponseEntity<ApiResponse<List<MediaCategoryDto>>> getRootCategories() {
        List<MediaCategoryDto> categories = mediaService.getRootCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    /**
     * 取得分類詳情
     */
    @GetMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('media:read')")
    public ResponseEntity<ApiResponse<MediaCategoryDto>> getCategory(
            @PathVariable UUID categoryId) {
        MediaCategoryDto category = mediaService.getCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    /**
     * 建立分類
     */
    @PostMapping
    @PreAuthorize("hasAuthority('media:create')")
    public ResponseEntity<ApiResponse<MediaCategoryDto>> createCategory(
            @Valid @RequestBody CreateMediaCategoryRequest request) {
        MediaCategoryDto category = mediaService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", category));
    }

    /**
     * 更新分類
     */
    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('media:update')")
    public ResponseEntity<ApiResponse<MediaCategoryDto>> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateMediaCategoryRequest request) {
        MediaCategoryDto category = mediaService.updateCategory(categoryId, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", category));
    }

    /**
     * 刪除分類
     */
    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('media:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable UUID categoryId) {
        mediaService.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully", null));
    }
}