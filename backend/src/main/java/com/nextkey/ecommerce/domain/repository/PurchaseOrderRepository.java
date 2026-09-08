package com.nextkey.ecommerce.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrder;

/**
 * 採購單 Repository
 * PRD §9.15
 */
@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    /**
     * 依 tenant 取得採購單列表
     */
    List<PurchaseOrder> findByTenantId(UUID tenantId);

    /**
     * 依 tenant 取得採購單列表（分頁）
     */
    Page<PurchaseOrder> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * 依 tenant 和狀態取得採購單列表
     */
    List<PurchaseOrder> findByTenantIdAndStatus(UUID tenantId, PurchaseOrder.POStatus status);

    /**
     * 依 tenant 和狀態取得採購單列表（分頁）
     */
    Page<PurchaseOrder> findByTenantIdAndStatus(UUID tenantId, PurchaseOrder.POStatus status, Pageable pageable);

    /**
     * 依 tenant 和 ID 取得採購單
     */
    Optional<PurchaseOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * 併發防護（DEF-126/127/128/129）：以 {@code SELECT ... FOR UPDATE} 鎖住採購單列，
     * 序列化 update/submit/receive/cancel 四個寫入方法對同一張採購單的「讀狀態→驗證→改欄位→
     * save()」複合操作（比照既有 {@code UserRepository.findByIdForUpdate}/
     * {@code KnowledgeArticleRepository.findByIdAndTenantIdForUpdate} 模式）。
     * {@code receivePurchaseOrder} 額外涉及 {@code PurchaseOrderItem.receivedQuantity} 的
     * 讀後寫，僅靠 DB 約束無法防止；悲觀鎖可一次同時解決狀態欄位全欄位覆寫與品項收貨數量
     * 讀後寫兩類問題。與 {@code AdminService} 的 {@link #reviewIfStatus} 原生條件式 UPDATE
     * 互不衝突：Postgres 的列鎖會讓兩者對同一列的寫入自然序列化。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT po FROM PurchaseOrder po WHERE po.id = :id AND po.tenantId = :tenantId")
    Optional<PurchaseOrder> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /**
     * 依 tenant 和 PO Number 取得採購單
     */
    Optional<PurchaseOrder> findByTenantIdAndPoNumber(UUID tenantId, String poNumber);

    /**
     * 依 supplier 取得採購單列表
     */
    List<PurchaseOrder> findBySupplierId(UUID supplierId);

    /**
     * 檢查 PO Number 是否存在
     */
    boolean existsByPoNumber(String poNumber);

    /**
     * 依 tenant 取得最近的採購單
     */
    @Query("SELECT po FROM PurchaseOrder po WHERE po.tenantId = :tenantId ORDER BY po.createdAt DESC")
    List<PurchaseOrder> findRecentByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * 依狀態跨租戶取得採購單列表（分頁）。供 SUPER_ADMIN 審批機制使用（Sprint 85），
     * 呼叫端須確保僅 SUPER_ADMIN 可觸達，此方法本身不做租戶篩選。
     */
    Page<PurchaseOrder> findByStatus(PurchaseOrder.POStatus status, Pageable pageable);

    /**
     * 併發防護：以條件式 UPDATE（{@code WHERE status = expectedStatus}）取代
     * 「讀 canReview()→setStatus→save」，避免 approve 與 reject（或兩個併發 approve/reject）
     * 都通過同一份舊快照的狀態檢查、各自覆寫審批結果。回傳受影響列數：1 代表本次成功轉換，
     * 0 代表狀態已被另一併發呼叫搶先轉換，呼叫端應拒絕本次請求。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE PurchaseOrder po SET po.status = :newStatus, po.reviewedBy = :reviewedBy, "
            + "po.reviewedAt = :reviewedAt, po.rejectionReason = :rejectionReason "
            + "WHERE po.id = :id AND po.status = :expectedStatus")
    int reviewIfStatus(@Param("id") UUID id, @Param("expectedStatus") PurchaseOrder.POStatus expectedStatus,
            @Param("newStatus") PurchaseOrder.POStatus newStatus, @Param("reviewedBy") UUID reviewedBy,
            @Param("reviewedAt") Instant reviewedAt, @Param("rejectionReason") String rejectionReason);
}