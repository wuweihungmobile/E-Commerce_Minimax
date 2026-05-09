package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 產品庫存 Repository
 */
@Repository
public interface ProductInventoryRepository extends JpaRepository<ProductInventory, UUID> {

    List<ProductInventory> findBySkuIdIn(List<UUID> skuIds);
}