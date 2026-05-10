package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.ProductDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.Product;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M01 商品管理 Backend API 整合測試 (T-M01-03)
 *
 * 測試範圍：
 * - IT-M01-001: 商品上架-從草稿發布
 * - IT-M01-002: 商品上架-缺少必填欄位
 * - IT-M01-003: 商品上架-basePrice必須大於0
 * - IT-M01-004: 商品編輯-更新標題和價格
 * - IT-M01-005: 商品下架-改為INACTIVE
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-M01: M01 商品管理 Backend API 整合測試")
@WithMockUser(username = "test-user", authorities = {"product:create", "product:read", "product:update", "product:delete"})
class M01ProductIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private ListingRepository listingRepository;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final String BASE_URL = "/v2/products";
    private static final String TEST_TENANT_ID = "550e8400-e29b-41d4-a716-446655440001";
    @SuppressWarnings("unused")
    private static final UUID TEST_USER_ID = UUID.randomUUID();

    // 測試資料工廠方法
    private ProductDto.CreateRequest buildValidCreateRequest() {
        return ProductDto.CreateRequest.builder()
                .title("Test Product")
                .description("Test Description")
                .category("Electronics")
                .brand("TestBrand")
                .basePrice(BigDecimal.valueOf(999.00))
                .coverImageUrl("https://example.com/image.jpg")
                .tags(List.of("tag1", "tag2"))
                .weightGrams(500)
                .dimensionsCm("10x10x10")
                .build();
    }

    private Listing buildMockListing(UUID listingId, Listing.ListingStatus status) {
        Listing listing = Listing.builder()
                .tenantId(UUID.fromString(TEST_TENANT_ID))
                .listingType(Listing.ListingType.PRODUCT)
                .title("Test Product")
                .description("Test Description")
                .status(status)
                .basePrice(BigDecimal.valueOf(999.00))
                .currency("TWD")
                .build();
        listing.setId(listingId);
        return listing;
    }

    private Product buildMockProduct(Listing listing) {
        Product product = Product.builder()
                .listing(listing)
                .category("Electronics")
                .brand("TestBrand")
                .weightGrams(500)
                .dimensionsCm("10x10x10")
                .build();
        return product;
    }

    // ── IT-M01-001: 商品上架-從草稿發布 (P0) ──────────────────────

    @Test
    @DisplayName("IT-M01-001: 商品上架-從草稿發布，成功建立商品")
    void createProduct_success_returns201() throws Exception {
        ProductDto.CreateRequest request = buildValidCreateRequest();

        UUID savedListingId = UUID.randomUUID();
        Listing savedListing = buildMockListing(savedListingId, Listing.ListingStatus.ACTIVE);
        Product savedProduct = buildMockProduct(savedListing);

        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);
        when(listingRepository.save(any(Listing.class))).thenReturn(savedListing);
        when(productRepository.findByListingId(any(UUID.class))).thenReturn(java.util.Optional.of(savedProduct));

        mockMvc.perform(post(BASE_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product created successfully"))
                .andExpect(jsonPath("$.data.listingId").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value("Test Product"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    // ── IT-M01-002: 商品上架-缺少必填欄位 (P0) ───────────────────

    @Test
    @DisplayName("IT-M01-002: 商品上架-缺少必填欄位，返回 400")
    void createProduct_missingRequiredFields_returns400() throws Exception {
        // 缺少 title 和 category
        ProductDto.CreateRequest request = ProductDto.CreateRequest.builder()
                .description("Test Description")
                .basePrice(BigDecimal.valueOf(999.00))
                .build();

        mockMvc.perform(post(BASE_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── IT-M01-003: 商品上架-basePrice必須大於0 (P1) ──────────────

    @Test
    @DisplayName("IT-M01-003: 商品上架-basePrice必須大於0，低於0返回 400")
    void createProduct_basePriceNotPositive_returns400() throws Exception {
        ProductDto.CreateRequest request = ProductDto.CreateRequest.builder()
                .title("Test Product")
                .description("Test Description")
                .category("Electronics")
                .brand("TestBrand")
                .basePrice(BigDecimal.valueOf(-1.00)) // 無效價格
                .build();

        mockMvc.perform(post(BASE_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("IT-M01-003: 商品上架-basePrice必須大於0，等於0返回 400")
    void createProduct_basePriceZero_returns400() throws Exception {
        ProductDto.CreateRequest request = ProductDto.CreateRequest.builder()
                .title("Test Product")
                .description("Test Description")
                .category("Electronics")
                .brand("TestBrand")
                .basePrice(BigDecimal.valueOf(0.00)) // 無效價格
                .build();

        mockMvc.perform(post(BASE_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── IT-M01-004: 商品編輯-更新標題和價格 (P1) ───────────────────

    @Test
    @DisplayName("IT-M01-004: 商品編輯-更新標題和價格，成功更新")
    void updateProduct_success_returns200() throws Exception {
        UUID listingId = UUID.randomUUID();
        Listing existingListing = buildMockListing(listingId, Listing.ListingStatus.ACTIVE);
        Product existingProduct = buildMockProduct(existingListing);

        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder()
                .title("Updated Product Title")
                .basePrice(BigDecimal.valueOf(1499.00))
                .build();

        when(productRepository.findByListingId(listingId)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenReturn(existingProduct);
        when(listingRepository.save(any(Listing.class))).thenReturn(existingListing);

        mockMvc.perform(put(BASE_URL + "/{listingId}", listingId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product updated successfully"));
    }

    // ── IT-M01-005: 商品下架-改為INACTIVE (P1) ────────────────────

    @Test
    @DisplayName("IT-M01-005: 商品下架-改為INACTIVE，成功刪除")
    void deleteProduct_success_returns200() throws Exception {
        UUID listingId = UUID.randomUUID();
        Listing existingListing = buildMockListing(listingId, Listing.ListingStatus.ACTIVE);
        Product existingProduct = buildMockProduct(existingListing);

        when(productRepository.findByListingId(listingId)).thenReturn(Optional.of(existingProduct));
        when(listingRepository.save(any(Listing.class))).thenReturn(existingListing);

        mockMvc.perform(delete(BASE_URL + "/{listingId}", listingId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product deleted successfully"));
    }
}