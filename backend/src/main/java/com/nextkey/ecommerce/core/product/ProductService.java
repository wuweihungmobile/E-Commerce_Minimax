package com.nextkey.ecommerce.core.product;

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
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ListingRepository listingRepository;
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

        // Map sortBy to proper field path for Product-Listing relationship
        String sortField = mapSortField(sortBy);
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortField);
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), sort);

        UUID tenantId = TenantContext.getCurrentTenant();
        Page<Product> products;

        if (keyword != null && !keyword.isBlank()) {
            // Search by keyword in listing title/description
            products = productRepository.findByListingTenantIdAndListingStatus(
                    tenantId, Listing.ListingStatus.ACTIVE, pageRequest);
        } else if (category != null && brand != null && !brand.isBlank()) {
            products = productRepository.findByCategoryAndBrand(category, brand, pageRequest);
        } else if (category != null && !category.isBlank()) {
            products = productRepository.findByCategory(category, pageRequest);
        } else if (brand != null && !brand.isBlank()) {
            products = productRepository.findByBrand(brand, pageRequest);
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
    public ProductDto.Response createProduct(ProductDto.CreateRequest request) {
        // 檢查 RETAIL_ENABLED feature toggle - T-DEF-001-02
        featureToggleService.checkFeatureEnabled("RETAIL_ENABLED");

        UUID tenantId = TenantContext.getCurrentTenant();

        // Create Listing first
        Listing listing = Listing.builder()
                .tenantId(tenantId)
                .listingType(Listing.ListingType.PRODUCT)
                .title(request.getTitle())
                .description(request.getDescription())
                .coverImageUrl(request.getCoverImageUrl())
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(request.getBasePrice())
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
        log.info("Created product with listingId: {}", listing.getId());

        return toResponse(product);
    }

    /**
     * 從 Dashboard 建立 Product (使用 CreateListingRequest)
     * T-DEF-001-01
     */
    @Transactional
    public ProductDto.Response createProductFromDashboard(com.nextkey.ecommerce.api.dto.CreateListingRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();

        // Create Listing first
        Listing listing = Listing.builder()
                .tenantId(tenantId)
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
    public ProductDto.Response updateProduct(UUID listingId, ProductDto.UpdateRequest request) {
        Product product = findProductByListingId(listingId);
        Listing listing = product.getListing();

        updateListingFromRequest(listing, request);
        listingRepository.save(listing);

        updateProductFromRequest(product, request);
        product = productRepository.save(product);
        log.info("Updated product with listingId: {}", listingId);

        return toResponse(product);
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
            listing.setStatus(Listing.ListingStatus.valueOf(request.getStatus().toUpperCase()));
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
    public void deleteProduct(final UUID listingId) {
        Product product = findProductByListingId(listingId);
        Listing listing = product.getListing();

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
                .createdAt(listing.getCreatedAt())
                .build();
    }
}