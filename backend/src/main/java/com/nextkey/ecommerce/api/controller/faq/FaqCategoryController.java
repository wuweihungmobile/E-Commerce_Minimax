package com.nextkey.ecommerce.api.controller.faq;

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
import com.nextkey.ecommerce.api.dto.faq.CreateFaqCategoryRequest;
import com.nextkey.ecommerce.api.dto.faq.FaqCategoryDto;
import com.nextkey.ecommerce.api.dto.faq.UpdateFaqCategoryRequest;
import com.nextkey.ecommerce.core.faq.FaqService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v2/faqs/categories")
@RequiredArgsConstructor
public class FaqCategoryController {

    private final FaqService faqService;

    @GetMapping
    @PreAuthorize("hasAuthority('faq:read')")
    public ResponseEntity<ApiResponse<List<FaqCategoryDto>>> getCategories() {
        List<FaqCategoryDto> categories = faqService.getCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('faq:read')")
    public ResponseEntity<ApiResponse<FaqCategoryDto>> getCategory(@PathVariable UUID categoryId) {
        FaqCategoryDto category = faqService.getCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    @GetMapping("/slug/{slug}")
    @PreAuthorize("hasAuthority('faq:read')")
    public ResponseEntity<ApiResponse<FaqCategoryDto>> getCategoryBySlug(@PathVariable String slug) {
        FaqCategoryDto category = faqService.getCategoryBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('faq:create')")
    public ResponseEntity<ApiResponse<FaqCategoryDto>> createCategory(
            @Valid @RequestBody CreateFaqCategoryRequest request) {
        FaqCategoryDto category = faqService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", category));
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('faq:update')")
    public ResponseEntity<ApiResponse<FaqCategoryDto>> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateFaqCategoryRequest request) {
        FaqCategoryDto category = faqService.updateCategory(categoryId, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", category));
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('faq:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID categoryId) {
        faqService.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully", null));
    }
}