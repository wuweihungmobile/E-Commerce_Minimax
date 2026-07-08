package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.nextkey.ecommerce.api.dto.ProductDto;
import com.nextkey.ecommerce.api.dto.ReviewDto;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.product.ProductService;
import com.nextkey.ecommerce.core.review.ReviewService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@Slf4j
@RestController
@RequestMapping("/v2/products")
@RequiredArgsConstructor
public class ProductController {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final ProductService productService;
    private final ReviewService reviewService;

    @GetMapping
    @PreAuthorize("hasAuthority('product:read')")
    public ResponseEntity<ApiResponse<Page<ProductDto.ListResponse>>> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Page<ProductDto.ListResponse> products = productService.getProducts(
                category, brand, keyword, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/{listingId}")
    @PreAuthorize("hasAuthority('product:read')")
    public ResponseEntity<ApiResponse<ProductDto.Response>> getProduct(
            @PathVariable UUID listingId) {
        ProductDto.Response product = productService.getProduct(listingId);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:create')")
    public ResponseEntity<ApiResponse<ProductDto.Response>> createProduct(
            @Valid @RequestBody ProductDto.CreateRequest request) {
        ProductDto.Response product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", product));
    }

    @PutMapping("/{listingId}")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<ProductDto.Response>> updateProduct(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID listingId,
            @Valid @RequestBody ProductDto.UpdateRequest request) {
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        ProductDto.Response product = productService.updateProduct(listingId, request, isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", product));
    }

    @DeleteMapping("/{listingId}")
    @PreAuthorize("hasAuthority('product:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID listingId) {
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        productService.deleteProduct(listingId, isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }

    /**
     * 取得商品評分統計（Sprint 20 US-006）
     * productId 對應 listingId（Platform 層統一路徑）
     */
    @GetMapping("/{productId}/reviews/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewDto.RatingStats>> getProductReviewStats(
            @PathVariable UUID productId) {
        log.info("Get review stats: productId={}", productId);
        ReviewDto.RatingStats stats = reviewService.getRatingStats(productId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}