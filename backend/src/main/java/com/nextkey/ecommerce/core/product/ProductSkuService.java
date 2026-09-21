package com.nextkey.ecommerce.core.product;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.SkuDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 商品規格（SKU）管理（Sprint 178）。
 *
 * <p>建立 SKU 時一併建立一筆 {@code total_qty=0} 的 {@link ProductInventory}
 * 列——這是唯一一處以 JPA {@code save()} 直接寫入 product_inventory 的地方，
 * 純粹是「新列插入」而非數量異動，不違反 {@link ProductInventoryRepository}
 * 頂端 Javadoc 所述「所有數量寫入都走原子 UPDATE」的原則（那條原則管的是既有列的
 * 讀後寫競態，新建列當下不存在併發覆蓋的對象）。之後所有數量變化一律經由既有的
 * {@link ProductInventoryRepository} 原子 UPDATE 方法（進貨、盤點、訂單扣帳等），
 * 本檔不重複那一段邏輯。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSkuService {

    private final ProductSkuRepository productSkuRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final ListingRepository listingRepository;

    @Transactional
    public SkuDto.Response createSku(final UUID listingId, final SkuDto.CreateRequest request,
            final boolean isSuperAdmin) {
        Listing listing = findProductListing(listingId);
        checkListingTenantOwnership(listing, isSuperAdmin);

        if (productSkuRepository.existsBySkuCode(request.getSkuCode())) {
            throw new BusinessException(ErrorCode.E_3005, "SKU code already exists: " + request.getSkuCode());
        }

        ProductSku sku = ProductSku.builder()
                .listing(listing)
                .skuCode(request.getSkuCode())
                .specName(request.getSpecName())
                .priceOverride(request.getPriceOverride())
                .status("ACTIVE")
                .build();
        sku = productSkuRepository.save(sku);

        // ProductInventory.id 透過 @MapsId 衍生自 sku 關聯物件本身（見該實體），僅設定
        // skuId 純量欄位不足以讓 Hibernate 產生 ID，會拋 IdentifierGenerationException。
        //
        // version 刻意明確覆寫為 null（該欄位 @Builder.Default 預設是 0L）：Spring Data JPA 對帶
        // @Version 的非原生型別欄位，isNew() 判斷式是「version == null」而非「id == null」——
        // 若沿用預設值 0L，save() 會誤判此為既有列而呼叫 entityManager.merge() 而非 persist()，
        // merge 在 @MapsId 關聯的識別子尚未真正持久化時會直接拋
        // org.hibernate.AssertionFailure: null identifier。
        ProductInventory inventory = ProductInventory.builder()
                .sku(sku)
                .skuId(sku.getId())
                .totalQty(0)
                .reservedQty(0)
                .version(null)
                .build();
        productInventoryRepository.save(inventory);

        log.info("Created SKU: id={}, listingId={}, skuCode={}", sku.getId(), listingId, sku.getSkuCode());
        // 比照 DEF-041：sku.getProductListingId() 是 insertable=false 的唯讀影子欄位，
        // 同一交易內剛 save() 的實體不會自動回填，這裡直接用呼叫端已知的 listingId 參數。
        return toResponse(sku, inventory, listingId);
    }

    @Transactional(readOnly = true)
    public List<SkuDto.Response> listSkus(final UUID listingId) {
        // 讀取端不做租戶擁有權檢查：SKU 列表比照商品詳情本身即為公開資訊（賣場需要顯示規格選項）
        findProductListing(listingId);
        List<ProductSku> skus = productSkuRepository.findByProductListingId(listingId);
        return skus.stream().map(sku -> toResponseWithInventoryLookup(sku, listingId)).toList();
    }

    @Transactional
    public SkuDto.Response updateSku(final UUID listingId, final UUID skuId, final SkuDto.UpdateRequest request,
            final boolean isSuperAdmin) {
        Listing listing = findProductListing(listingId);
        checkListingTenantOwnership(listing, isSuperAdmin);

        ProductSku sku = productSkuRepository.findById(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3003, "SKU not found: " + skuId));
        if (!sku.getProductListingId().equals(listingId)) {
            throw new BusinessException(ErrorCode.E_3003, "SKU not found: " + skuId);
        }

        if (request.getSpecName() != null) {
            sku.setSpecName(request.getSpecName());
        }
        if (request.getPriceOverride() != null) {
            sku.setPriceOverride(request.getPriceOverride());
        }
        if (request.getStatus() != null) {
            sku.setStatus(request.getStatus());
        }
        sku = productSkuRepository.save(sku);

        log.info("Updated SKU: id={}, listingId={}", skuId, listingId);
        return toResponseWithInventoryLookup(sku, listingId);
    }

    private Listing findProductListing(final UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3000));
        if (listing.getListingType() != Listing.ListingType.PRODUCT) {
            throw new BusinessException(ErrorCode.E_3001, "SKU management only applies to PRODUCT listings");
        }
        return listing;
    }

    // 比照 ProductService.checkListingTenantOwnership（DEF-041 既有模式）
    private void checkListingTenantOwnership(final Listing listing, final boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return;
        }
        UUID callerTenantId = TenantContext.getCurrentTenant();
        if (!listing.getTenantId().equals(callerTenantId)) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to manage this product's SKUs");
        }
    }

    private SkuDto.Response toResponseWithInventoryLookup(final ProductSku sku, final UUID listingId) {
        ProductInventory inventory = productInventoryRepository.findById(sku.getId()).orElse(null);
        return toResponse(sku, inventory, listingId);
    }

    private SkuDto.Response toResponse(final ProductSku sku, final ProductInventory inventory, final UUID listingId) {
        return SkuDto.Response.builder()
                .id(sku.getId())
                .listingId(listingId)
                .skuCode(sku.getSkuCode())
                .specName(sku.getSpecName())
                .priceOverride(sku.getPriceOverride())
                .status(sku.getStatus())
                .totalQty(inventory != null ? inventory.getTotalQty() : 0)
                .reservedQty(inventory != null ? inventory.getReservedQty() : 0)
                .availableQty(inventory != null
                        ? inventory.getTotalQty() - inventory.getReservedQty()
                        : 0)
                .createdAt(sku.getCreatedAt())
                .updatedAt(sku.getUpdatedAt())
                .build();
    }
}
