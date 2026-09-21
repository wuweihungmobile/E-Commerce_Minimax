package com.nextkey.ecommerce.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.product.ProductSku;

@Repository
public interface ProductSkuRepository extends JpaRepository<ProductSku, UUID> {

    List<ProductSku> findByProductListingId(UUID productListingId);

    Optional<ProductSku> findBySkuCode(String skuCode);

    boolean existsBySkuCode(String skuCode);

    boolean existsByIdAndProductListingId(UUID id, UUID productListingId);

    /**
     * SKU 編號／規格／品名的顯示用投影（Sprint 120，DEF-069）。
     *
     * <p>只給只需要顯示用途的場景（如退貨品項列表）批次查詢，避免對每一列各自 lazy load
     * {@code ProductSku}／{@code Listing}。LEFT JOIN 比照 DEF-064：SKU 或 listing 若異常缺漏，
     * 顯示資訊留白也好過整列消失。
     */
    interface SkuDisplayInfo {
        UUID getId();

        String getSkuCode();

        String getSpecName();

        String getProductName();
    }

    @Query("SELECT s.id AS id, s.skuCode AS skuCode, s.specName AS specName, l.title AS productName "
            + "FROM ProductSku s LEFT JOIN s.listing l WHERE s.id IN :skuIds")
    List<SkuDisplayInfo> findDisplayInfoByIdIn(@Param("skuIds") Collection<UUID> skuIds);
}