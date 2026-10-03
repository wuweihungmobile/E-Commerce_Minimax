package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.CheckoutDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.checkout.CombinedCheckoutService;
import com.nextkey.ecommerce.core.order.OrderService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.logistics.ShippingTemplate;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.ShippingTemplateRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.exception.PromoCodeInvalidException;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 同店結帳整合測試（Sprint 237，DEF-319 訂單側；真實 PostgreSQL + Redis）。
 *
 * <p>情境就是 Sprint 232 實測出問題的真實情境：兩家店鋪、一位<strong>沒有店鋪的真實消費者</strong>
 * （租戶脈絡是系統租戶佔位值），消費者的購物車同時放兩家店鋪的商品。修復前訂單全部蓋成系統租戶，店主讀不到也處理不了，
 * 店鋪設定的運費模板與優惠券也從未對這位消費者生效；既有的結帳整合測試都把買家放進店鋪的租戶，等於用固件繞過了這個情境。
 *
 * <p>為什麼要真實資料庫與 Redis：要驗證的是訂單資料列實際蓋的 {@code tenant_id}、購物車裡哪些項目被移除，以及店主端查詢
 * 實際看得到什麼——mock 掉 Repository 與購物車只看得到「有沒有呼叫」。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-STORE-CHECKOUT: 同店結帳（Sprint 237，DEF-319）")
class StoreScopedCheckoutIntegrationTest {

    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    private static final BigDecimal FEE_A = new BigDecimal("60.00");
    private static final BigDecimal FEE_B = new BigDecimal("30.00");

    @Autowired private OrderService orderService;
    @Autowired private CombinedCheckoutService combinedCheckoutService;
    @Autowired private com.nextkey.ecommerce.core.booking.BookingService bookingService;
    @Autowired private RedisCartService cartService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;
    @Autowired private ShippingTemplateRepository shippingTemplateRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private Tenant storeA;
    private Tenant storeB;
    private UUID consumerId;
    private UUID ownerAId;
    private UUID ownerBId;
    private UUID productA;
    private UUID productB;
    private UUID roomA;
    private PromoCode promoA;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        long stamp = System.nanoTime();
        storeA = newStore("A", stamp);
        storeB = newStore("B", stamp);
        User ownerA = newUser("owner-a", stamp, User.UserRole.STORE_OWNER, storeA.getId());
        User ownerB = newUser("owner-b", stamp, User.UserRole.STORE_OWNER, storeB.getId());
        ownerAId = ownerA.getId();
        ownerBId = ownerB.getId();
        // 沒有店鋪的真實消費者：users.tenant_id 為 null，租戶脈絡是系統租戶佔位值
        consumerId = newUser("consumer", stamp, User.UserRole.BUYER, null).getId();

        productA = newListing(storeA, ownerA, Listing.ListingType.PRODUCT, "商品A", "100.00");
        productB = newListing(storeB, ownerB, Listing.ListingType.PRODUCT, "商品B", "50.00");
        roomA = newListing(storeA, ownerA, Listing.ListingType.ROOM, "房間A", "1000.00");
        jdbcTemplate.update("INSERT INTO rooms (listing_id, max_guests, room_count) VALUES (?, ?, ?)", roomA, 4, 1);

        newShippingTemplate(storeA, FEE_A);
        newShippingTemplate(storeB, FEE_B);
        promoA = promoCodeRepository.save(PromoCode.builder()
                .tenant(storeA)
                .code("STOREA" + (stamp % 100000))
                .discountType(PromoCode.DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("20.00"))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .maxUsageCount(5)
                .currentUsageCount(0)
                .maxUsagePerUser(1)
                .isActive(true)
                .build());

        asConsumer();
        cartService.clearCart(consumerId, SYSTEM_TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── 固件 ────────────────────────────────────────────────

    private Tenant newStore(final String label, final long stamp) {
        return tenantRepository.save(Tenant.builder()
                .name("同店結帳店鋪" + label).slug("store-checkout-" + label.toLowerCase() + "-" + stamp)
                .contactEmail("store-checkout-" + label.toLowerCase() + "-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789").status(Tenant.TenantStatus.ACTIVE).build());
    }

    private User newUser(final String label, final long stamp, final User.UserRole role, final UUID tenantId) {
        return userRepository.save(User.builder()
                .email("store-checkout-" + label + "-" + stamp + "@example.com")
                .passwordHash("dummy").fullName(label).role(role).status("ACTIVE").tenantId(tenantId).build());
    }

    /** 房源要同時設關聯（寫入資料庫的是 tenant／owner 關聯，tenantId／ownerId 只是唯讀影子欄位）。 */
    private UUID newListing(final Tenant store, final User owner, final Listing.ListingType type,
                            final String title, final String price) {
        return listingRepository.save(Listing.builder()
                .tenant(store).owner(owner).listingType(type).title(title)
                .basePrice(new BigDecimal(price)).currency("TWD").status(Listing.ListingStatus.ACTIVE).build()).getId();
    }

    private void newShippingTemplate(final Tenant store, final BigDecimal fee) {
        shippingTemplateRepository.save(ShippingTemplate.builder()
                .tenant(store).name("固定運費").feeType(ShippingTemplate.FeeType.FIXED).fixedAmount(fee).build());
    }

    private void asConsumer() {
        TenantContext.setCurrentUser(consumerId);
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);
    }

    /** 換成該店鋪的店主：使用者與租戶都要換——只換租戶的話，目前的使用者仍是訂單的擁有者（消費者），授權會因「是本人」而放行。 */
    private void asStoreOwner(final Tenant store) {
        TenantContext.setCurrentUser(store.getId().equals(storeA.getId()) ? ownerAId : ownerBId);
        TenantContext.setCurrentTenant(store.getId());
    }

    private void addProduct(final UUID listingId, final int quantity) {
        cartService.addItem(consumerId, SYSTEM_TENANT_ID,
                CartDto.AddItemRequest.builder().listingId(listingId).quantity(quantity).build());
    }

    private void addRoom() {
        LocalDate checkIn = LocalDate.now().plusDays(40);
        cartService.addItem(consumerId, SYSTEM_TENANT_ID, CartDto.AddItemRequest.builder()
                .listingId(roomA).quantity(1).startDate(checkIn).endDate(checkIn.plusDays(2)).build());
    }

    private OrderDto.CreateRequest productRequest(final UUID storeId) {
        OrderDto.CreateRequest request = new OrderDto.CreateRequest();
        request.setOrderType("PRODUCT");
        request.setShippingAddress("台北市信義區測試路 1 號");
        request.setShippingRecipientName("測試收件人");
        request.setShippingPhone("0912345678");
        request.setStoreId(storeId);
        return request;
    }

    private CheckoutDto.MixedCheckoutRequest mixedRequest(final UUID storeId) {
        return mixedRequest(storeId, null);
    }

    private CheckoutDto.MixedCheckoutRequest mixedRequest(final UUID storeId, final String promoCode) {
        return CheckoutDto.MixedCheckoutRequest.builder()
                .shippingAddress("台北市信義區測試路 1 號").shippingRecipientName("測試收件人").shippingPhone("0912345678")
                .guestCount(2).guestName("測試住客").storeId(storeId).promoCode(promoCode).build();
    }

    private UUID orderTenant(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT tenant_id FROM orders WHERE id = ?", UUID.class, orderId);
    }

    private int ordersOfConsumer() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, consumerId);
    }

    private int cartItemCount() {
        return cartService.getCart(consumerId, SYSTEM_TENANT_ID).getItems().size();
    }

    // ── 測試 ────────────────────────────────────────────────

    @Test
    @DisplayName("單一店鋪的購物車：不必指定店鋪，訂單蓋成該店鋪、運費用該店鋪的模板；店主讀得到、處理得了")
    void singleStoreCart_orderBelongsToTheStore_andTheStoreOwnerCanSeeIt() {
        addProduct(productA, 2);

        UUID orderId = orderService.createOrderFromCart(productRequest(null)).getId();

        assertThat(orderTenant(orderId)).as("訂單必須歸屬商品所屬的店鋪，不是系統租戶").isEqualTo(storeA.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT shipping_fee FROM orders WHERE id = ?", BigDecimal.class, orderId))
                .as("運費走店鋪自己的運費模板").isEqualByComparingTo(FEE_A);

        asStoreOwner(storeA);
        assertThat(orderService.getTenantOrders(0, 20, "createdAt", "DESC", null).getContent())
                .as("店主的賣家訂單列表看得到消費者的訂單").extracting(OrderDto.OrderListResponse::getId).contains(orderId);
        assertThat(orderService.getOrder(orderId).getId()).as("店主讀得到這筆訂單（同租戶授權）").isEqualTo(orderId);

        asStoreOwner(storeB);
        assertThat(orderService.getTenantOrders(0, 20, "createdAt", "DESC", null).getContent())
                .as("另一家店鋪看不到").extracting(OrderDto.OrderListResponse::getId).doesNotContain(orderId);
        assertThatThrownBy(() -> orderService.getOrder(orderId)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_1007));

        asConsumer();
        assertThat(orderService.getOrder(orderId).getId()).as("消費者自己仍讀得到自己的訂單").isEqualTo(orderId);
    }

    @Test
    @DisplayName("購物車含兩家店鋪的商品、沒指定店鋪 → E-5020，不建立任何訂單、購物車原封不動")
    void multiStoreCart_withoutStore_isRejected_andNothingChanges() {
        addProduct(productA, 1);
        addProduct(productB, 1);

        assertThatThrownBy(() -> orderService.createOrderFromCart(productRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5020));

        assertThat(ordersOfConsumer()).isZero();
        assertThat(cartItemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("兩家店鋪分開結帳：各自的訂單歸各自的店鋪、各用各的運費模板，另一家的商品留在購物車直到它自己結帳")
    void multiStoreCart_checkedOutSeparately() {
        addProduct(productA, 1);
        addProduct(productB, 2);

        UUID orderA = orderService.createOrderFromCart(productRequest(storeA.getId())).getId();

        assertThat(orderTenant(orderA)).isEqualTo(storeA.getId());
        assertThat(cartItemCount()).as("店鋪 B 的商品還在購物車").isEqualTo(1);
        assertThat(cartService.getCart(consumerId, SYSTEM_TENANT_ID).getItems().get(0).getListingId()).isEqualTo(productB);

        UUID orderB = orderService.createOrderFromCart(productRequest(storeB.getId())).getId();

        assertThat(orderTenant(orderB)).isEqualTo(storeB.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT shipping_fee FROM orders WHERE id = ?", BigDecimal.class, orderB))
                .isEqualByComparingTo(FEE_B);
        assertThat(cartItemCount()).isZero();
        assertThat(ordersOfConsumer()).isEqualTo(2);
    }

    @Test
    @DisplayName("促銷碼屬於店鋪：店鋪 A 的券能套用在店鋪 A、折扣落在 A 的訂單；套用在店鋪 B 會失敗；B 的結帳不受影響")
    void promoIsStoreScoped() {
        addProduct(productA, 1);
        addProduct(productB, 1);

        assertThatThrownBy(() -> cartService.applyPromoCode(consumerId, SYSTEM_TENANT_ID, storeB.getId(), promoA.getCode()))
                .as("店鋪 A 的券在店鋪 B 不存在").isInstanceOf(PromoCodeInvalidException.class);
        assertThatThrownBy(() -> cartService.applyPromoCode(consumerId, SYSTEM_TENANT_ID, null, promoA.getCode()))
                .as("兩家店鋪的購物車必須指定店鋪").isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5020));

        CartDto.ApplyPromoResponse applied =
                cartService.applyPromoCode(consumerId, SYSTEM_TENANT_ID, storeA.getId(), promoA.getCode());
        assertThat(applied.getStoreId()).isEqualTo(storeA.getId());
        assertThat(applied.getDiscountAmount()).isEqualByComparingTo("20.00");

        CartDto.CartResponse cart = cartService.getCartWithPromo(consumerId, SYSTEM_TENANT_ID);
        assertThat(cart.getStores()).hasSize(2);
        assertThat(cart.getStores()).filteredOn(s -> s.getStoreId().equals(storeA.getId()))
                .singleElement().satisfies(s -> {
                    assertThat(s.getAppliedPromoCode()).isEqualToIgnoringCase(promoA.getCode());
                    assertThat(s.getDiscountAmount()).isEqualByComparingTo("20.00");
                    assertThat(s.getShippingFee()).isEqualByComparingTo(FEE_A);
                });
        assertThat(cart.getStores()).filteredOn(s -> s.getStoreId().equals(storeB.getId()))
                .singleElement().satisfies(s -> {
                    assertThat(s.getAppliedPromoCode()).isNull();
                    assertThat(s.getDiscountAmount()).isEqualByComparingTo("0");
                    assertThat(s.getShippingFee()).isEqualByComparingTo(FEE_B);
                });

        // 店鋪 B 先結帳：沒有套用任何促銷碼，不受店鋪 A 的券影響
        OrderDto.OrderResponse orderB = orderService.createOrderFromCart(productRequest(storeB.getId()));
        assertThat(orderB.getPromoCode()).isNull();
        assertThat(orderB.getDiscountAmount()).isEqualByComparingTo("0");

        OrderDto.OrderResponse orderA = orderService.createOrderFromCart(productRequest(storeA.getId()));
        assertThat(orderA.getPromoCode()).isEqualToIgnoringCase(promoA.getCode());
        assertThat(orderA.getDiscountAmount()).isEqualByComparingTo("20.00");
        // 小計 100 + 運費 60 − 折扣 20
        assertThat(orderA.getTotalAmount()).isEqualByComparingTo("140.00");
        assertThat(promoCodeRepository.findById(promoA.getId()).orElseThrow().getCurrentUsageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("合併結帳限同一家店鋪：沒指定店鋪 → E-5020；指定店鋪 A → Order 與 Booking 都歸店鋪 A，店鋪 B 的商品留在購物車")
    void mixedCheckout_isLimitedToOneStore() {
        addProduct(productA, 1);
        addProduct(productB, 1);
        addRoom();

        assertThatThrownBy(() -> combinedCheckoutService.checkoutMixedCart(mixedRequest(null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5020));
        assertThat(ordersOfConsumer()).isZero();

        CheckoutDto.MixedCheckoutResponse response =
                combinedCheckoutService.checkoutMixedCart(mixedRequest(storeA.getId(), promoA.getCode()));

        assertThat(orderTenant(response.getOrder().getId())).isEqualTo(storeA.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT shipping_fee FROM orders WHERE id = ?", BigDecimal.class,
                response.getOrder().getId())).as("合併結帳的運費也走店鋪 A 自己的模板").isEqualByComparingTo(FEE_A);
        assertThat(response.getPromoCode()).as("店鋪 A 的促銷碼在店鋪 A 解析、可以用在合併結帳").isEqualToIgnoringCase(promoA.getCode());
        assertThat(response.getTotalDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(jdbcTemplate.queryForObject("SELECT tenant_id FROM bookings WHERE id = ?", UUID.class,
                response.getBooking().getId())).as("訂房也歸屬店鋪 A").isEqualTo(storeA.getId());
        assertThat(cartItemCount()).as("只剩店鋪 B 的商品").isEqualTo(1);
        assertThat(cartService.getCart(consumerId, SYSTEM_TENANT_ID).getItems().get(0).getListingId()).isEqualTo(productB);
    }

    @Test
    @DisplayName("合併結帳指定的店鋪缺少商品或房間（店鋪 B 只有商品）→ E-5004，不建立任何東西")
    void mixedCheckout_storeWithoutBothKinds_isRejected() {
        addProduct(productA, 1);
        addProduct(productB, 1);
        addRoom();

        assertThatThrownBy(() -> combinedCheckoutService.checkoutMixedCart(mixedRequest(storeB.getId())))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5004));

        assertThat(ordersOfConsumer()).isZero();
        assertThat(cartItemCount()).isEqualTo(3);
    }

    // ── Sprint 239：暫停營業的店鋪不能被下單（使用者拍板）──────────────────────────

    private void suspend(final Tenant store) {
        jdbcTemplate.update("UPDATE tenants SET status = 'SUSPENDED' WHERE id = ?", store.getId());
    }

    @Test
    @DisplayName("店鋪停權後：購物車標示該店鋪暫停營業；結帳回 E-2010、沒有訂單、購物車原封不動；另一家營業中的店鋪照常結帳")
    void suspendedStore_cannotBeOrdered_andTheCartSaysSo() {
        addProduct(productA, 1);
        addProduct(productB, 1);
        suspend(storeA);

        CartDto.CartResponse cart = cartService.getCartWithPromo(consumerId, SYSTEM_TENANT_ID);
        assertThat(cart.getStores()).filteredOn(s -> s.getStoreId().equals(storeA.getId()))
                .singleElement().satisfies(s -> assertThat(s.getStoreActive()).as("停權的店鋪").isFalse());
        assertThat(cart.getStores()).filteredOn(s -> s.getStoreId().equals(storeB.getId()))
                .singleElement().satisfies(s -> assertThat(s.getStoreActive()).as("營業中的店鋪").isTrue());

        assertThatThrownBy(() -> orderService.createOrderFromCart(productRequest(storeA.getId())))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_2010));
        assertThat(ordersOfConsumer()).isZero();
        assertThat(cartItemCount()).isEqualTo(2);

        UUID orderB = orderService.createOrderFromCart(productRequest(storeB.getId())).getId();
        assertThat(orderTenant(orderB)).isEqualTo(storeB.getId());
    }

    @Test
    @DisplayName("店鋪停權後：合併結帳與單獨訂房都回 E-2010，不留下任何訂單或訂房")
    void suspendedStore_cannotBeBooked_norMixedCheckedOut() {
        addProduct(productA, 1);
        addRoom();
        suspend(storeA);

        assertThatThrownBy(() -> combinedCheckoutService.checkoutMixedCart(mixedRequest(storeA.getId())))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_2010));
        assertThat(ordersOfConsumer()).isZero();

        LocalDate checkIn = LocalDate.now().plusDays(60);
        assertThatThrownBy(() -> bookingService.createBooking(
                com.nextkey.ecommerce.api.dto.BookingDto.CreateRequest.builder().roomListingId(roomA)
                        .checkInDate(checkIn).checkOutDate(checkIn.plusDays(1)).guestCount(1).guestName("測試住客").build(),
                "idem-" + UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_2010));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bookings WHERE user_id = ?", Integer.class, consumerId)).isZero();
    }

    @Test
    @DisplayName("只擋「建立」：訂單成立後店鋪才停權，消費者仍能取消自己的訂單（停權店鋪的消費者要能取消與退款）")
    void suspendedAfterOrdering_theConsumerCanStillCancel() {
        addProduct(productA, 1);
        UUID orderId = orderService.createOrderFromCart(productRequest(null)).getId();
        suspend(storeA);

        orderService.cancelOrder(orderId, "店鋪停權後消費者取消");

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("CANCELLED");
    }
}
