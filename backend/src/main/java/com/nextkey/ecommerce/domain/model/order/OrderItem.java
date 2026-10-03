package com.nextkey.ecommerce.domain.model.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductSku;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    private static final int DECIMAL_PRECISION = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "order_id", insertable = false, updatable = false)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "listing_id", insertable = false, updatable = false)
    private UUID listingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id")
    private ProductSku sku;

    @Column(name = "sku_id", insertable = false, updatable = false)
    private UUID skuId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = DECIMAL_PRECISION, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = DECIMAL_PRECISION, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (subtotal == null && unitPrice != null && quantity != null) {
            subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    // ===== 影子欄位的讀取（Sprint 243，DEF-338）=====
    // 下面的 listingId／skuId 是 insertable = false 的唯讀影子欄位：只有「從資料庫載入」時才有值，同一個持久化脈絡裡剛用
    // .listing／sku(...) 建立的實體它是 null（Sprint 115 DEF-065 起已知的 JPA 陷阱：同交易內剛建立的實體，影子欄位是 null）。回應轉換直接讀它，
    // 建立訂單／訂房／合併結帳的回應（與冪等重放存下的回應）就把 listingId 回成 null。這裡的 getter 在影子欄位沒有值時退回
    // 關聯的 id，載入自資料庫的實體行為不變（影子欄位有值就用它）。

    public UUID getListingId() {
        return listingId != null ? listingId : (listing != null ? listing.getId() : null);
    }

    public UUID getSkuId() {
        return skuId != null ? skuId : (sku != null ? sku.getId() : null);
    }
}
