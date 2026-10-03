package com.nextkey.ecommerce.domain.model.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;

/**
 * 影子欄位的讀取（Sprint 243，DEF-338）。{@code tenantId}／{@code userId}／{@code listingId}／{@code roomListingId}／{@code skuId}
 * 是 {@code insertable = false} 的唯讀影子欄位，只有「從資料庫載入」才有值；同一個持久化脈絡裡剛用關聯建立的實體它們是 null，
 * 建立訂單／訂房／合併結帳的回應（與冪等重放存下的回應）因此把 {@code tenantId}／{@code userId}／{@code listingId} 回成 null
 * （Sprint 243 以真實服務實測發現；GET 詳情是從資料庫載入的，所以是對的）。getter 在影子欄位沒有值時退回關聯的 id。
 */
@DisplayName("Order／OrderItem／Booking：影子欄位沒有值時 getter 退回關聯的 id（DEF-338）")
class ShadowFieldGetterTest {

    private static Tenant tenant(final UUID id) {
        Tenant tenant = Tenant.builder().name("店").slug("s").build();
        tenant.setId(id);
        return tenant;
    }

    private static User user(final UUID id) {
        User user = User.builder().email("u@example.com").passwordHash("x").build();
        user.setId(id);
        return user;
    }

    private static Listing listing(final UUID id) {
        Listing listing = Listing.builder().title("L").build();
        listing.setId(id);
        return listing;
    }

    @Test
    @DisplayName("Order：只有 tenant／user 關聯（剛建立）→ getTenantId／getUserId 取關聯的 id")
    void order_justCreated_readsTheAssociations() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Order order = Order.builder().tenant(tenant(tenantId)).user(user(userId)).build();

        assertThat(order.getTenantId()).isEqualTo(tenantId);
        assertThat(order.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Order：影子欄位有值（從資料庫載入）→ 用影子欄位，載入後的行為不變；兩者都沒有 → null")
    void order_loadedFromDatabase_prefersTheShadowColumn() {
        UUID shadow = UUID.randomUUID();
        Order loaded = Order.builder().tenantId(shadow).userId(shadow).build();
        assertThat(loaded.getTenantId()).isEqualTo(shadow);
        assertThat(loaded.getUserId()).isEqualTo(shadow);

        Order both = Order.builder().tenantId(shadow).tenant(tenant(UUID.randomUUID())).build();
        assertThat(both.getTenantId()).as("兩者都有時用影子欄位").isEqualTo(shadow);

        assertThat(Order.builder().build().getTenantId()).isNull();
        assertThat(Order.builder().build().getUserId()).isNull();
    }

    @Test
    @DisplayName("OrderItem：只有 listing／sku 關聯（剛建立）→ getListingId／getSkuId 取關聯的 id；沒有 sku → null")
    void orderItem_justCreated_readsTheAssociations() {
        UUID listingId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        ProductSku sku = new ProductSku();
        sku.setId(skuId);

        OrderItem withSku = OrderItem.builder().listing(listing(listingId)).sku(sku).build();
        assertThat(withSku.getListingId()).isEqualTo(listingId);
        assertThat(withSku.getSkuId()).isEqualTo(skuId);

        OrderItem withoutSku = OrderItem.builder().listing(listing(listingId)).build();
        assertThat(withoutSku.getSkuId()).isNull();
        assertThat(OrderItem.builder().listingId(listingId).build().getListingId()).isEqualTo(listingId);
    }

    @Test
    @DisplayName("Booking：只有 tenant／user／roomListing 關聯（剛建立）→ 三個 getter 取關聯的 id；影子欄位有值就用它")
    void booking_justCreated_readsTheAssociations() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Booking created = Booking.builder().tenant(tenant(tenantId)).user(user(userId)).roomListing(listing(roomId)).build();

        assertThat(created.getTenantId()).isEqualTo(tenantId);
        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getRoomListingId()).isEqualTo(roomId);

        UUID shadow = UUID.randomUUID();
        Booking loaded = Booking.builder().tenantId(shadow).userId(shadow).roomListingId(shadow).build();
        assertThat(loaded.getTenantId()).isEqualTo(shadow);
        assertThat(loaded.getUserId()).isEqualTo(shadow);
        assertThat(loaded.getRoomListingId()).isEqualTo(shadow);
        assertThat(Booking.builder().build().getRoomListingId()).isNull();
    }
}
