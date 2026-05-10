package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.product.ProductSku;

@Repository
public interface ProductSkuRepository extends JpaRepository<ProductSku, UUID> {

    List<ProductSku> findByProductListingId(UUID productListingId);

    Optional<ProductSku> findBySkuCode(String skuCode);

    boolean existsBySkuCode(String skuCode);
}