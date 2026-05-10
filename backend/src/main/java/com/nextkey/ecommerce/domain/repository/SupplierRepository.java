package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.erp.Supplier;

/**
 * 供應商 Repository
 * PRD §9.15
 */
@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    /**
     * 依 tenant 取得供應商列表
     */
    List<Supplier> findByTenantId(UUID tenantId);

    /**
     * 依 tenant 和狀態取得供應商列表
     */
    List<Supplier> findByTenantIdAndStatus(UUID tenantId, Supplier.SupplierStatus status);

    /**
     * 依 tenant 和 ID 取得供應商
     */
    Optional<Supplier> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * 檢查供應商是否存在於 tenant 下
     */
    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * 依 tenant 和名稱搜尋供應商（模糊比對）
     */
    @Query("SELECT s FROM Supplier s WHERE s.tenantId = :tenantId AND LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Supplier> searchByName(@Param("tenantId") UUID tenantId, @Param("keyword") String keyword);
}