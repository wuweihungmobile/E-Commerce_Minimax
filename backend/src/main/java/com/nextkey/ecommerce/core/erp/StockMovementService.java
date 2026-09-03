package com.nextkey.ecommerce.core.erp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.erp.StockMovementDto;
import com.nextkey.ecommerce.api.dto.erp.StockMovementRequest;
import com.nextkey.ecommerce.domain.model.inventory.StockMovement;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.StockMovementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * 庫存異動 Service
 * PRD §6.7, §9.15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final ListingRepository listingRepository;

    /**
     * 手動庫存異動
     * @param request 異動請求
     * @param userId 操作者 ID
     * @return 異動記錄 DTO
     */
    @Transactional
    public StockMovementDto createManualMovement(final StockMovementRequest request, final UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID skuId = request.getSkuId();

        // 取得當前庫存
        ProductInventory inventory = productInventoryRepository.findById(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3003,
                        String.format("SKU not found: skuId=%s", skuId)));

        // 驗證租戶擁有此 SKU 所屬 listing（租戶隔離，DEF-017 修復；比照 NotificationTemplateService 慣例）
        UUID productListingId = inventory.getSku().getProductListingId();
        Listing listing = listingRepository.findById(productListingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3003,
                        String.format("Listing not found for SKU: skuId=%s", skuId)));
        // null-safe：listing 租戶為空（資料異常）或不符當前租戶 → 拒絕（避免 NPE→500）
        if (!tenantId.equals(listing.getTenantId())) {
            throw new BusinessException(ErrorCode.E_1007, "SKU does not belong to current tenant");
        }

        // 解析異動類型
        StockMovement.MovementType movementType;
        try {
            movementType = StockMovement.MovementType.valueOf(request.getMovementType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E_7005,
                    String.format("Invalid movement type: %s", request.getMovementType()));
        }

        // 驗證不允許的手動類型：這四型必須由採購單／訂單流程產生，手動建立會讓流水帳與來源單據脫鉤
        if (movementType == StockMovement.MovementType.INBOUND
                || movementType == StockMovement.MovementType.OUTBOUND
                || movementType == StockMovement.MovementType.RESERVE
                || movementType == StockMovement.MovementType.RELEASE) {
            throw new BusinessException(ErrorCode.E_7005,
                    "Manual movement cannot use INBOUND/OUTBOUND/RESERVE/RELEASE types");
        }

        // RETURN 的庫存語意 PRD §6.7.4 未定義（屬 DEF-044 業務決策）。Sprint 114 之前它可以被解析、
        // 會寫下一筆 isInbound() 為真的流水帳，但 switch 落到 default 使庫存文風不動——帳面上退了貨、
        // 庫存卻沒回補，且沒有任何錯誤。在該項拍板前明確拒絕，不留這條靜默 no-op。
        if (movementType == StockMovement.MovementType.RETURN) {
            throw new BusinessException(ErrorCode.E_7005,
                    "RETURN movement is not available: its inventory semantics are undefined "
                            + "until the restock-on-refund decision (DEF-044) is made");
        }

        int quantity = request.getQuantity();

        // 根據異動類型以原子 UPDATE 調整庫存（DEF-051）；回傳本次對 total_qty 的帶號變化量。
        // 🔴 此行之後不得再碰 inventory 的任何 setter：它仍在本交易的持久化上下文中，
        // 一旦被弄髒，Hibernate 會在提交時把過期快照整列寫回、覆蓋掉原生 UPDATE 的結果。
        int delta = applyMovementType(skuId, movementType, quantity);

        // 前後數量一律回讀 DB：原子 UPDATE 之後 inventory 的快照已過期，拿它填流水帳會記錯數字。
        // 回讀在同一交易內、且該列的行鎖尚未釋放，故 after 必為本次結果，before 由 after 反推必然自洽。
        int afterTotalQty = currentTotalQty(skuId);
        int beforeTotalQty = afterTotalQty - delta;

        // 建立異動記錄
        StockMovement movement = StockMovement.builder()
                .tenantId(tenantId)
                .skuId(skuId)
                .movementType(movementType)
                .quantity(quantity)
                .beforeTotalQty(beforeTotalQty)
                .afterTotalQty(afterTotalQty)
                .balanceAfter(afterTotalQty)
                .referenceType(StockMovement.ReferenceType.MANUAL)
                // Sprint 117（DEF-064）：店家自填的參考單號。修復前 StockMovementRequest 根本沒有這個
                // 欄位，前端表單那格輸入被 Jackson 靜默忽略——打了字、送出了、什麼也沒發生。
                .referenceNumber(request.getReferenceNumber())
                .notes(request.getNotes())
                .createdBy(userId)
                .build();

        StockMovement saved = stockMovementRepository.save(movement);
        log.info("Created manual stock movement: id={}, tenantId={}, type={}, qty={}",
                saved.getId(), tenantId, movementType, quantity);

        return toCreatedDto(saved, inventory, listing);
    }

    /**
     * 依異動類型執行庫存調整，回傳本次對 {@code total_qty} 的帶號變化量（未動庫存的類型為 0）。
     *
     * <p>Sprint 113（DEF-051）改為原子 UPDATE。修復前是「先讀出總量比大小 → 再改實體 → save()」，
     * 併發下那個比大小形同虛設：實測庫存 3 遇上 10 筆併發扣減，10 條執行緒全數通過充足性檢查，
     * 最後是靠 {@code @Version} 樂觀鎖擋掉 8 筆——擋住了沒錯，但操作員收到的是 500 而不是「庫存不足」，
     * 而且盤盈方向連擋都不必擋，8 筆合法異動就這樣整筆消失。
     */
    private int applyMovementType(final UUID skuId, final StockMovement.MovementType movementType,
            final int quantity) {
        switch (movementType) {
            case ADJUST_PLUS:
            case TRANSFER_IN:
                productInventoryRepository.increaseTotalQty(skuId, quantity);
                return quantity;
            case ADJUST_MINUS:
            case TRANSFER_OUT:
            case SCRAP:
                // 庫存列的存在性已在上方 findById 確認過，故 0 筆只可能是總量不足
                if (productInventoryRepository.decreaseTotalQtyIfSufficient(skuId, quantity) == 0) {
                    throw new BusinessException(ErrorCode.E_7004,
                            String.format("Insufficient stock: available=%d, requested=%d",
                                    currentTotalQty(skuId), quantity));
                }
                return -quantity;
            default:
                return 0;
        }
    }

    /** 回讀庫存列的當前總量（原子 UPDATE 後實體快照已過期，不可改用 getter）。 */
    private int currentTotalQty(final UUID skuId) {
        return productInventoryRepository.findTotalQtyBySkuId(skuId);
    }

    /**
     * 依 SKU 取得異動記錄
     */
    @Transactional(readOnly = true)
    public List<StockMovementDto> getMovementsBySku(final UUID skuId) {
        UUID tenantId = TenantContext.getCurrentTenant();

        return stockMovementRepository.findMovementRowsBySkuAndTenant(skuId, tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 分頁取得所有異動記錄
     */
    @Transactional(readOnly = true)
    public Page<StockMovementDto> getMovements(final Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenant();

        return stockMovementRepository.findMovementRowsByTenant(tenantId, pageable).map(this::toDto);
    }

    /**
     * 依時間範圍取得異動記錄
     */
    @Transactional(readOnly = true)
    public List<StockMovementDto> getMovementsByDateRange(final Instant start, final Instant end) {
        UUID tenantId = TenantContext.getCurrentTenant();

        return stockMovementRepository.findMovementRowsByTenantAndDateRange(tenantId, start, end).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 轉換為 DTO（投影版；Sprint 117／DEF-064）。
     *
     * <p>修復前的版本吃 {@link StockMovement} 實體，而實體上根本沒有 SKU 編號與品名
     * ——那兩個 DTO 欄位因此**從來沒被填過值**，前端列表的「SKU／品名」兩欄永遠是「-」。
     * 改吃投影後由 SQL 一次 JOIN 齊，不會有 N+1。
     */
    private StockMovementDto toDto(final StockMovementRepository.StockMovementRow row) {
        return StockMovementDto.builder()
                .id(row.getId())
                .tenantId(row.getTenantId())
                .skuId(row.getSkuId())
                .skuCode(row.getSkuCode())
                .productName(row.getProductName())
                .movementType(row.getMovementType())
                .quantity(row.getQuantity())
                .beforeTotalQty(row.getBeforeTotalQty())
                .afterTotalQty(row.getAfterTotalQty())
                .referenceType(row.getReferenceType())
                .referenceId(row.getReferenceId())
                .referenceNumber(row.getReferenceNumber())
                .sourceDocument(row.getSourceDocument())
                .notes(row.getNotes())
                .createdBy(row.getCreatedBy())
                .createdAt(row.getCreatedAt())
                .build();
    }

    /**
     * 建立後的回傳值轉換（Sprint 117／DEF-064）。
     *
     * <p>剛建立的異動同樣要帶 SKU 編號與品名，否則「建立後回傳的那筆」與「列表查到的同一筆」
     * 欄位不一致。這兩個值**不必回查資料庫**：SKU 來自方法開頭已載入的庫存列、品名來自
     * 租戶檢查時已載入的 listing——兩者都在手上了。
     *
     * <p>手動異動的 {@code sourceDocument} 恆為 null：它是系統產生之異動的來源單據，
     * 手動建立的沒有來源可指（店家自填的單號放在 {@code referenceNumber}，是另一回事）。
     */
    private StockMovementDto toCreatedDto(final StockMovement saved, final ProductInventory inventory,
            final Listing listing) {
        return StockMovementDto.builder()
                .id(saved.getId())
                .tenantId(saved.getTenantId())
                .skuId(saved.getSkuId())
                .skuCode(inventory.getSku() != null ? inventory.getSku().getSkuCode() : null)
                .productName(listing.getTitle())
                .movementType(saved.getMovementType() != null ? saved.getMovementType().name() : null)
                .quantity(saved.getQuantity())
                .beforeTotalQty(saved.getBeforeTotalQty())
                .afterTotalQty(saved.getAfterTotalQty())
                .referenceType(saved.getReferenceType() != null ? saved.getReferenceType().name() : null)
                .referenceId(saved.getReferenceId())
                .referenceNumber(saved.getReferenceNumber())
                .notes(saved.getNotes())
                .createdBy(saved.getCreatedBy())
                .createdAt(saved.getCreatedAt())
                .build();
    }
}