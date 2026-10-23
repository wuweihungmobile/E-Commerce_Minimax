package com.nextkey.ecommerce.core.product;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.ProductDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.Product;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.util.PageableUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    // DEF-344：sortBy 白名單（公開給 mapSortField 映射前驗證用）。亂填先前直接原樣傳給 JPA 排序，
    // 落入全域例外處理器的 catch-all 變 500；比照 M05/M06（DEF-339）改用 PageableUtils.sortOf。
    private static final Set<String> SORTABLE_FIELDS = Set.of("basePrice", "createdAt", "updatedAt");

    private final ProductRepository productRepository;
    private final ListingRepository listingRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final FeatureToggleService featureToggleService;

    @Transactional(readOnly = true)
    public Page<ProductDto.ListResponse> getProducts(
            String category,
            String brand,
            String keyword,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        // DEF-344：先驗證（白名單＋方向），再映射成 Product-Listing 關聯的實際欄位路徑。
        Sort validatedSort = PageableUtils.sortOf(sortBy, sortDir, SORTABLE_FIELDS);
        String sortField = mapSortField(sortBy);
        Sort sort = Sort.by(validatedSort.getOrderFor(sortBy).getDirection(), sortField);
        PageRequest pageRequest = PageableUtils.of(page, size, 100, sort);

        UUID tenantId = TenantContext.getCurrentTenant();
        Page<Product> products;

        if (keyword != null && !keyword.isBlank()) {
            // Search by keyword in listing title/description（Sprint 66 修正：先前呼叫與 else 分支
            // 完全相同的方法，keyword 從未被實際使用，等同搜尋永遠失效、直接回傳全部上架商品）
            products = productRepository.searchByTenantIdAndKeyword(tenantId, keyword.trim(), pageRequest);
        } else if (category != null && brand != null && !brand.isBlank()) {
            products = productRepository.findByTenantIdAndListingStatusAndCategoryAndBrand(
                    tenantId, category, brand, pageRequest);
        } else if (category != null && !category.isBlank()) {
            products = productRepository.findByTenantIdAndListingStatusAndCategory(tenantId, category, pageRequest);
        } else if (brand != null && !brand.isBlank()) {
            products = productRepository.findByTenantIdAndListingStatusAndBrand(tenantId, brand, pageRequest);
        } else {
            products = productRepository.findByListingTenantIdAndListingStatus(
                    tenantId, Listing.ListingStatus.ACTIVE, pageRequest);
        }

        return products.map(this::toListResponse);
    }

    @Transactional(readOnly = true)
    public ProductDto.Response getProduct(UUID listingId) {
        Product product = findProductByListingId(listingId);
        return toResponse(product);
    }

    @Transactional
    public ProductDto.Response createProduct(ProductDto.CreateRequest request, final boolean isSuperAdmin) {
        UUID tenantId = TenantContext.getCurrentTenant();
        checkCanCreateListing(tenantId, isSuperAdmin);
        // 檢查 RETAIL_ENABLED feature toggle - T-DEF-001-02
        featureToggleService.checkFeatureEnabled("RETAIL_ENABLED");

        // Sprint 147：MAX_PRODUCTS 數量配額強制執行（PRD §4.4）
        featureToggleService.checkQuotaNotExceeded(AppConstants.QUOTA_MAX_PRODUCTS,
                listingRepository.countByTenantIdAndListingTypeAndStatus(
                        tenantId, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE));

        Tenant tenant = fetchTenant(tenantId);
        User owner = fetchOwner();

        // Create Listing first
        Listing listing = Listing.builder()
                .tenant(tenant)
                .tenantId(tenantId)
                .owner(owner)
                .listingType(Listing.ListingType.PRODUCT)
                .title(request.getTitle())
                .description(request.getDescription())
                .coverImageUrl(request.getCoverImageUrl())
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(request.getBasePrice())
                .currency("TWD")
                .tags(request.getTags())
                .isFeatured(Boolean.TRUE.equals(request.getIsFeatured()))
                .build();

        listing = listingRepository.save(listing);

        // Create Product
        Product product = Product.builder()
                .listing(listing)
                .category(request.getCategory())
                .brand(request.getBrand())
                .weightGrams(request.getWeightGrams())
                .dimensionsCm(request.getDimensionsCm())
                .build();

        product = productRepository.save(product);
        log.info("Created product with listingId: {}", listing.getId());

        return toResponse(product);
    }

    /**
     * 從 Dashboard 建立 Product (使用 CreateListingRequest)
     * T-DEF-001-01
     */
    @Transactional
    public ProductDto.Response createProductFromDashboard(
            com.nextkey.ecommerce.api.dto.CreateListingRequest request, final boolean isSuperAdmin) {
        UUID tenantId = TenantContext.getCurrentTenant();
        checkCanCreateListing(tenantId, isSuperAdmin);
        // Sprint 148（DEF-184）：RETAIL_ENABLED 檢查從 DashboardListingController 搬進 Service 層，
        // 遵循 PRD §4.4「Feature Toggle 驗證...不得在 Controller 層執行」的分層規範
        featureToggleService.checkFeatureEnabled("RETAIL_ENABLED");
        // Sprint 147：MAX_PRODUCTS 數量配額強制執行（PRD §4.4）
        featureToggleService.checkQuotaNotExceeded(AppConstants.QUOTA_MAX_PRODUCTS,
                listingRepository.countByTenantIdAndListingTypeAndStatus(
                        tenantId, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE));

        Tenant tenant = fetchTenant(tenantId);
        User owner = fetchOwner();

        // Create Listing first
        Listing listing = Listing.builder()
                .tenant(tenant)
                .tenantId(tenantId)
                .owner(owner)
                .listingType(Listing.ListingType.PRODUCT)
                .title(request.getName())
                .description(request.getDescription())
                .coverImageUrl(request.getCoverImageUrl())
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(request.getPrice())
                .currency("TWD")
                .tags(request.getTags())
                .build();

        listing = listingRepository.save(listing);

        // Create Product
        Product product = Product.builder()
                .listing(listing)
                .category(request.getCategory())
                .brand(request.getBrand())
                .weightGrams(request.getWeightGrams())
                .dimensionsCm(request.getDimensionsCm())
                .build();

        product = productRepository.save(product);
        log.info("Created product from dashboard with listingId: {}", listing.getId());

        return toResponse(product);
    }

    @Transactional
    public ProductDto.Response updateProduct(
            UUID listingId, ProductDto.UpdateRequest request, final boolean isSuperAdmin) {
        Product product = findProductByListingId(listingId);
        Listing listing = product.getListing();
        checkListingTenantOwnership(listing, isSuperAdmin);

        // Sprint 147：MAX_PRODUCTS 數量配額強制執行（PRD §4.4）——僅在「由非 ACTIVE 轉入 ACTIVE」
        // 時檢查，避免既有 ACTIVE 商品因其他欄位更新被誤擋，也堵住「先建滿額→下架一筆→建立新的→
        // 再把舊的重新上架」這個繞過建立時檢查的路徑
        if (isActivatingListing(listing.getStatus(), request.getStatus())) {
            featureToggleService.checkQuotaNotExceeded(AppConstants.QUOTA_MAX_PRODUCTS,
                    listingRepository.countByTenantIdAndListingTypeAndStatus(
                            listing.getTenantId(), Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE));
        }

        updateListingFromRequest(listing, request);
        listingRepository.save(listing);

        updateProductFromRequest(product, request);
        product = productRepository.save(product);
        log.info("Updated product with listingId: {}", listingId);

        return toResponse(product);
    }

    private boolean isActivatingListing(final Listing.ListingStatus currentStatus, final String requestedStatus) {
        if (requestedStatus == null || currentStatus == Listing.ListingStatus.ACTIVE) {
            return false;
        }
        try {
            return Listing.ListingStatus.ACTIVE == Listing.ListingStatus.valueOf(requestedStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 非法 status 字串不視為「正在啟用」，交由 updateListingFromRequest 的 try/catch 轉為 E_9000
            return false;
        }
    }

    private void updateListingFromRequest(Listing listing, ProductDto.UpdateRequest request) {
        if (request.getTitle() != null) {
            listing.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            listing.setDescription(request.getDescription());
        }
        if (request.getCoverImageUrl() != null) {
            listing.setCoverImageUrl(request.getCoverImageUrl());
        }
        if (request.getBasePrice() != null) {
            listing.setBasePrice(request.getBasePrice());
        }
        if (request.getTags() != null) {
            listing.setTags(request.getTags());
        }
        if (request.getStatus() != null) {
            try {
                listing.setStatus(Listing.ListingStatus.valueOf(request.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.E_9000, "Invalid status: " + request.getStatus());
            }
        }
        if (request.getIsFeatured() != null) {
            listing.setIsFeatured(request.getIsFeatured());
        }
    }

    private void updateProductFromRequest(Product product, ProductDto.UpdateRequest request) {
        if (request.getCategory() != null) {
            product.setCategory(request.getCategory());
        }
        if (request.getBrand() != null) {
            product.setBrand(request.getBrand());
        }
        if (request.getWeightGrams() != null) {
            product.setWeightGrams(request.getWeightGrams());
        }
        if (request.getDimensionsCm() != null) {
            product.setDimensionsCm(request.getDimensionsCm());
        }
    }

    @Transactional
    public void deleteProduct(final UUID listingId, final boolean isSuperAdmin) {
        Product product = findProductByListingId(listingId);
        Listing listing = product.getListing();
        checkListingTenantOwnership(listing, isSuperAdmin);

        // Soft delete: set status to DELETED
        listing.setStatus(Listing.ListingStatus.DELETED);
        listingRepository.save(listing);

        log.info("Deleted product with listingId: {}", listingId);
    }

    /**
     * Map sort field names to proper JPA field paths.
     * basePrice and createdAt are on Listing, not Product.
     */
    private String mapSortField(final String sortBy) {
        return switch ( sortBy) {
            case "basePrice" -> "listing.basePrice";
            case "createdAt" -> "listing.createdAt";
            case "updatedAt" -> "listing.updatedAt";
            default -> sortBy;
        };
    }

    private Product findProductByListingId(final UUID listingId) {
        return productRepository.findByListingId(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3000));
    }

    // DEF-041 根因修復（Sprint 84）：Listing.tenantId/ownerId 是 insertable=false 的唯讀影子欄位，
    // 建立時必須實際設定 .tenant(...)/.owner(...) 關聯物件，否則資料庫 tenant_id/owner_id 永遠不會被寫入。
    private Tenant fetchTenant(final UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));
    }

    private User fetchOwner() {
        UUID userId = TenantContext.getCurrentUser();
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));
    }

    // DEF-041（Sprint 84）：比照 BookingService.checkListingTenantOwnership 既有模式，
    // 非 SUPER_ADMIN 限自己租戶，SUPER_ADMIN 可跨租戶操作。
    // DEF-415（Sprint 304）：「自己租戶」必須是真正的店鋪（TenantContext.isStoreTenant）。沒有店鋪者——含沒有店鋪的
    // ADMIN（租戶內角色）——的租戶是系統租戶佔位值、全體共用，平台自營商品也蓋成同一個值，單看相等會把他們當成
    // 擁有者；這類商品只有 SUPER_ADMIN 能管。
    private void checkListingTenantOwnership(final Listing listing, final boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return;
        }
        UUID callerTenantId = TenantContext.getCurrentTenant();
        if (!TenantContext.isStoreTenant(callerTenantId) || !listing.getTenantId().equals(callerTenantId)) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to manage this product listing");
        }
    }

    // DEF-415（Sprint 304）：建立端的同一道守門。沒有店鋪者建出來的商品會蓋成系統租戶（平台自營資料），
    // 只有 SUPER_ADMIN 能建立；放在功能開關與配額檢查（都要查資料庫）之前，先判斷再查詢。
    private void checkCanCreateListing(final UUID callerTenantId, final boolean isSuperAdmin) {
        if (!isSuperAdmin && !TenantContext.isStoreTenant(callerTenantId)) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to create product listings");
        }
    }

    private ProductDto.Response toResponse(Product product) {
        Listing listing = product.getListing();
        return ProductDto.Response.builder()
                .listingId(listing.getId())
                .tenantId(listing.getTenantId())
                .title(listing.getTitle())
                .description(listing.getDescription())
                .category(product.getCategory())
                .brand(product.getBrand())
                .basePrice(listing.getBasePrice())
                .currency(listing.getCurrency())
                .coverImageUrl(listing.getCoverImageUrl())
                .status(listing.getStatus().name())
                .tags(listing.getTags())
                .weightGrams(product.getWeightGrams())
                .dimensionsCm(product.getDimensionsCm())
                .isFeatured(listing.getIsFeatured())
                .createdAt(listing.getCreatedAt())
                .updatedAt(listing.getUpdatedAt())
                .build();
    }

    private ProductDto.ListResponse toListResponse(Product product) {
        Listing listing = product.getListing();
        return ProductDto.ListResponse.builder()
                .listingId(listing.getId())
                .title(listing.getTitle())
                .category(product.getCategory())
                .brand(product.getBrand())
                .basePrice(listing.getBasePrice())
                .currency(listing.getCurrency())
                .coverImageUrl(listing.getCoverImageUrl())
                .status(listing.getStatus().name())
                .isFeatured(listing.getIsFeatured())
                .createdAt(listing.getCreatedAt())
                .build();
    }
}