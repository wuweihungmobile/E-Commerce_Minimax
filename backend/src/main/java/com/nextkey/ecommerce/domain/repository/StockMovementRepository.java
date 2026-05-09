package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.inventory.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 庫存異動 Repository
 * PRD §6.7, §9.15
 */
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    /**
     * 依 tenant 取得異動記錄（分頁）
     */
    Page<StockMovement> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * 依 SKU 取得異動記錄
     */
    List<StockMovement> findBySkuIdOrderByCreatedAtDesc(UUID skuId);

    /**
     * 依 SKU 和 tenant 取得異動記錄
     */
    List<StockMovement> findBySkuIdAndTenantIdOrderByCreatedAtDesc(UUID skuId, UUID tenantId);

    /**
     * 依異動類型取得記錄
     */
    List<StockMovement> findByMovementType(StockMovement.MovementType movementType);

    /**
     * 依時間範圍取得異動記錄
     */
    @Query("SELECT sm FROM StockMovement sm WHERE sm.tenantId = :tenantId AND sm.createdAt BETWEEN :start AND :end ORDER BY sm.createdAt DESC")
    List<StockMovement> findByTenantIdAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * 依參考資料取得異動記錄
     */
    List<StockMovement> findByReferenceTypeAndReferenceId(StockMovement.ReferenceType referenceType, UUID referenceId);
}