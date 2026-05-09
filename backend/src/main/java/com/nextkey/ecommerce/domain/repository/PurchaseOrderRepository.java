package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}