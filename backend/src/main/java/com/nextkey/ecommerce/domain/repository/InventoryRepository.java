package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.inventory.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findBySkuId(UUID skuId);

    List<Inventory> findByTenantId(UUID tenantId);

    Page<Inventory> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT i FROM Inventory i WHERE i.availableQty <= i.safetyStock")
    List<Inventory> findLowStockItems();

    @Query("SELECT i FROM Inventory i WHERE i.availableQty <= i.reorderPoint")
    List<Inventory> findItemsNeedingReorder();

    @Query("SELECT i FROM Inventory i WHERE i.tenantId = :tenantId AND i.availableQty <= i.safetyStock")
    List<Inventory> findLowStockItemsByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT i FROM Inventory i WHERE i.skuId IN " +
           "(SELECT ps.id FROM ProductSku ps WHERE ps.productListingId = :listingId)")
    List<Inventory> findByListingId(@Param("listingId") UUID listingId);
}
