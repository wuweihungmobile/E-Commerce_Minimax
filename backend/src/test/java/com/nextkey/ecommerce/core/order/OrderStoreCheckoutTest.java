package com.nextkey.ecommerce.core.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.logistics.ShippingTemplateService;
import com.nextkey.ecommerce.core.product.ProductInventoryService;
import com.nextkey.ecommerce.core.promo.PromoService;
import com.nextkey.ecommerce.core.user.AddressService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.OrderStateLogRepository;
import com.nextkey.ecommerce.domain.repository.ProductRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeUsageRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * Sprint 237（DEF-319 訂單側，同店結帳）：訂單歸屬「商品所屬的店鋪」，不是下單者的租戶。
 *
 * <p>為什麼要有這個檔案：沒有店鋪的一般消費者，租戶脈絡是系統租戶佔位值（不是 null、全體共用）。原本訂單、促銷碼、
 * 運費模板全以這個租戶解析——店主讀不到也處理不了客人的訂單，店鋪設定的運費與優惠券也從未對真實消費者生效
 * （Sprint 232 以真實全棧實測）。既有的訂單測試把買家與店鋪放在同一個租戶，等於用固件繞過了這個情境；
 * 這裡讓購物車擁有者是系統租戶，店鋪是另一個租戶，才分得出程式碼用了哪一個。
 *
 * <p>PRD US-008／PC-005：單筆訂單限同一商家。購物車含多家店鋪的商品時必須指定要結哪一家，其餘留在購物車分開結帳。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderService 同店結帳（Sprint 237，DEF-319）")
class OrderStoreCheckoutTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStateLogRepository orderStateLogRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RedisCartService cartService;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private ShippingTemplateService shippingTemplateService;
    @Mock private AddressService addressService;
    @Mock private ProductInventoryService productInventoryService;
    @Mock private PromoService promoService;
    @Mock private PromoCodeUsageRepository promoCodeUsageRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private OrderService orderService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    /** 購物車的擁有範圍：沒有店鋪的真實消費者的租戶（系統租戶佔位值）。 */
    private static final UUID CART_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    private static final UUID STORE_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID STORE_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID LISTING_A = UUID.fromString("a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1");
    private static final UUID LISTING_B = UUID.fromString("b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1");
    private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final BigDecimal FEE_A = BigDecimal.valueOf(60);
    private static final BigDecimal FEE_B = BigDecimal.valueOf(100);

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(CART_TENANT_ID);

        User user = User.builder().build();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(STORE_A)).thenReturn(Optional.of(store(STORE_A)));
        when(tenantRepository.findById(STORE_B)).thenReturn(Optional.of(store(STORE_B)));
        when(listingRepository.findById(LISTING_A)).thenReturn(Optional.of(productListing(LISTING_A, STORE_A)));
        when(listingRepository.findById(LISTING_B)).thenReturn(Optional.of(productListing(LISTING_B, STORE_B)));
        when(shippingTemplateService.calculateFeeForTenant(eq(STORE_A), any())).thenReturn(FEE_A);
        when(shippingTemplateService.calculateFeeForTenant(eq(STORE_B), any())).thenReturn(FEE_B);
        when(promoService.computeCappedDiscount(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            return o;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Tenant store(final UUID id) {
        Tenant tenant = Tenant.builder().build();
        tenant.setId(id);
        return tenant;
    }

    private Listing productListing(final UUID id, final UUID storeId) {
        return Listing.builder().id(id).tenantId(storeId)
                .listingType(Listing.ListingType.PRODUCT).status(Listing.ListingStatus.ACTIVE)
                .title("商品 " + id).basePrice(BigDecimal.valueOf(100)).build();
    }

    private CartDto.CartItemResponse item(final UUID listingId, final UUID storeId) {
        return CartDto.CartItemResponse.builder()
                .cartItemKey("key-" + listingId).listingId(listingId).quantity(2)
                .unitPrice(BigDecimal.valueOf(100)).subtotal(BigDecimal.valueOf(200))
                .listingType("PRODUCT").storeId(storeId).build();
    }

    private void givenCart(final CartDto.CartItemResponse... items) {
        when(cartService.getCart(USER_ID, CART_TENANT_ID)).thenReturn(CartDto.CartResponse.builder()
                .items(List.of(items)).totalAmount(BigDecimal.valueOf(200L * items.length)).build());
    }

    private OrderDto.CreateRequest request(final UUID storeId) {
        return OrderDto.CreateRequest.builder().orderType("PRODUCT").storeId(storeId).build();
    }

    @Test
    @DisplayName("沒有店鋪的消費者結帳 → 訂單蓋成商品所屬的店鋪（不是系統租戶），運費用該店鋪的運費模板")
    void tenantlessBuyer_orderIsStampedWithTheProductsStore() {
        givenCart(item(LISTING_A, STORE_A));

        orderService.createOrderFromCart(request(null));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getTenant().getId())
                .as("訂單必須歸屬商品所屬的店鋪，店主才看得到、處理得了").isEqualTo(STORE_A);
        assertThat(saved.getValue().getShippingFee()).as("運費走該店鋪自己的模板").isEqualByComparingTo(FEE_A);
        verify(shippingTemplateService, never()).calculateFeeForTenant(eq(CART_TENANT_ID), any());
        verify(tenantRepository, never()).findById(CART_TENANT_ID);
    }

    @Test
    @DisplayName("促銷碼：用購物車在該店鋪已套用的券碼，並在該店鋪重新驗證（不是買家的租戶）")
    void promo_isResolvedInTheStore() {
        givenCart(item(LISTING_A, STORE_A));
        when(cartService.getAppliedPromoCode(USER_ID, CART_TENANT_ID, STORE_A)).thenReturn("SAVE100");

        orderService.createOrderFromCart(request(null));

        verify(promoService).resolveValidPromoForCheckout("SAVE100", STORE_A, USER_ID);
        verify(promoService, never()).resolveValidPromoForCheckout(any(), eq(CART_TENANT_ID), any());
    }

    @Test
    @DisplayName("購物車含兩家店鋪的商品、沒指定店鋪 → E-5020，不建立訂單、不動購物車")
    void multipleStores_withoutStore_isRejected() {
        givenCart(item(LISTING_A, STORE_A), item(LISTING_B, STORE_B));

        assertThatThrownBy(() -> orderService.createOrderFromCart(request(null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5020));

        verify(orderRepository, never()).save(any());
        verify(cartService, never()).removeItem(any(), any(), any(String.class));
    }

    @Test
    @DisplayName("購物車含兩家店鋪、指定其中一家 → 只結那一家的商品，另一家的商品留在購物車")
    void multipleStores_withStore_ordersOnlyThatStoresItems() {
        givenCart(item(LISTING_A, STORE_A), item(LISTING_B, STORE_B));

        orderService.createOrderFromCart(request(STORE_B));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getTenant().getId()).isEqualTo(STORE_B);
        assertThat(saved.getValue().getItems()).hasSize(1);
        assertThat(saved.getValue().getItems().get(0).getListing().getId()).isEqualTo(LISTING_B);
        assertThat(saved.getValue().getShippingFee()).isEqualByComparingTo(FEE_B);
        verify(cartService).removeItem(USER_ID, CART_TENANT_ID, "key-" + LISTING_B);
        verify(cartService, never()).removeItem(USER_ID, CART_TENANT_ID, "key-" + LISTING_A);
    }

    @Test
    @DisplayName("指定了購物車裡沒有商品的店鋪 → E-5004，不建立訂單")
    void storeWithoutItems_isRejected() {
        givenCart(item(LISTING_A, STORE_A));

        assertThatThrownBy(() -> orderService.createOrderFromCart(request(STORE_B)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5004));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("縱深防線：就算購物車項目宣稱的店鋪與資料庫裡商品實際所屬的店鋪不同，也不可能建出跨店鋪的訂單")
    void buildProductOrder_rejectsAListingOfAnotherStore() {
        // 項目宣稱屬於 STORE_A，但商品實際屬於 STORE_B（例如商品在加入購物車之後被搬移、或有人繞過篩選直接呼叫）
        CartDto.CartItemResponse liar = item(LISTING_B, STORE_A);
        User user = User.builder().build();
        user.setId(USER_ID);

        assertThatThrownBy(() -> orderService.buildProductOrder(store(STORE_A), user, request(null),
                List.of(liar), null, BigDecimal.ZERO, BigDecimal.ZERO, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5020));
        verify(orderRepository, never()).save(any());
    }
}
