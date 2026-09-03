package com.nextkey.ecommerce.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.inventory.StockMovement;

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

    /**
     * 庫存異動列表的一列（Sprint 117，DEF-064）。
     *
     * <p>{@code stock_movements} 只有 {@code sku_id}，SKU 編號在 {@code product_skus}、品名在
     * {@code listings}；來源單據則要看 {@code reference_type} 決定去哪張表取。用投影一次撈齊，
     * 避免列表每一列都往回查一次商品與單據。
     */
    interface StockMovementRow {
        UUID getId();

        UUID getTenantId();

        UUID getSkuId();

        String getSkuCode();

        String getProductName();

        String getMovementType();

        Integer getQuantity();

        Integer getBeforeTotalQty();

        Integer getAfterTotalQty();

        String getReferenceType();

        UUID getReferenceId();

        /** 店家自填的參考單號。 */
        String getReferenceNumber();

        /** 系統推導的來源單據：採購單號、訂單 id 前八碼，手動異動為 null。 */
        String getSourceDocument();

        String getNotes();

        UUID getCreatedBy();

        Instant getCreatedAt();
    }

    /**
     * 列表查詢的共用 SELECT（Sprint 117，DEF-064）。
     *
     * <p>來源單據刻意用 {@code CAST(... AS text)} 而非 PostgreSQL 的 {@code ::text}：
     * 原生查詢裡的 {@code ::} 會讓 Hibernate 把後面的字當成具名參數。
     *
     * <p>SKU 與 listing 一律用 LEFT JOIN：異動記錄不該因為商品被下架或資料異常而整列消失
     * ——台帳的職責是把發生過的事留著。
     */
    String MOVEMENT_SELECT = """
            SELECT m.id                 AS id,
                   m.tenant_id          AS tenantId,
                   m.sku_id             AS skuId,
                   s.sku_code           AS skuCode,
                   l.title              AS productName,
                   m.movement_type      AS movementType,
                   m.quantity           AS quantity,
                   m.before_total_qty   AS beforeTotalQty,
                   m.after_total_qty    AS afterTotalQty,
                   m.reference_type     AS referenceType,
                   m.reference_id       AS referenceId,
                   m.reference_number   AS referenceNumber,
                   CASE
                       WHEN m.reference_type = 'PURCHASE_ORDER' THEN po.po_number
                       WHEN m.reference_type = 'ORDER' THEN LEFT(CAST(m.reference_id AS text), 8)
                       ELSE NULL
                   END                  AS sourceDocument,
                   m.notes              AS notes,
                   m.created_by         AS createdBy,
                   m.created_at         AS createdAt
              FROM stock_movements m
              LEFT JOIN product_skus s   ON s.id = m.sku_id
              LEFT JOIN listings l       ON l.id = s.product_listing_id
              LEFT JOIN purchase_orders po ON po.id = m.reference_id
                                          AND m.reference_type = 'PURCHASE_ORDER'
            """;

    /** 租戶的異動記錄（分頁，時間倒序）。 */
    @Query(value = MOVEMENT_SELECT + " WHERE m.tenant_id = :tenantId ORDER BY m.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM stock_movements m WHERE m.tenant_id = :tenantId",
            nativeQuery = true)
    Page<StockMovementRow> findMovementRowsByTenant(@Param("tenantId") UUID tenantId, Pageable pageable);

    /** 單筆異動記錄（供建立後回傳一致的 DTO）。 */
    @Query(value = MOVEMENT_SELECT + " WHERE m.tenant_id = :tenantId AND m.id = :id", nativeQuery = true)
    Optional<StockMovementRow> findMovementRowById(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /** 單一 SKU 的異動記錄（時間倒序）。 */
    @Query(value = MOVEMENT_SELECT
            + " WHERE m.tenant_id = :tenantId AND m.sku_id = :skuId ORDER BY m.created_at DESC",
            nativeQuery = true)
    List<StockMovementRow> findMovementRowsBySkuAndTenant(@Param("skuId") UUID skuId,
            @Param("tenantId") UUID tenantId);

    /** 時間範圍內的異動記錄（時間倒序）。 */
    @Query(value = MOVEMENT_SELECT
            + " WHERE m.tenant_id = :tenantId AND m.created_at BETWEEN :start AND :end"
            + " ORDER BY m.created_at DESC",
            nativeQuery = true)
    List<StockMovementRow> findMovementRowsByTenantAndDateRange(@Param("tenantId") UUID tenantId,
            @Param("start") Instant start, @Param("end") Instant end);
}
