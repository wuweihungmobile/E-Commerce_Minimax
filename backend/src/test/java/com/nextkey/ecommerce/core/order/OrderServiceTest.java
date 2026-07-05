package com.nextkey.ecommerce.core.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.logistics.ShippingTemplateService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderStateLog;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.OrderStateLogRepository;
import com.nextkey.ecommerce.domain.repository.ProductRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * OrderService 單元測試（Sprint 69 US-001）。
 *
 * <p>背景：{@link OrderService} 是訂單狀態機核心（7 個 public 方法：createOrderFromCart、
 * createBooking、getUserOrders、getOrder、updateOrderStatus、cancelOrder、getOrderStateLogs），
 * 先前**完全沒有** {@code OrderServiceTest.java} 這類以 Mockito mock repository 的純單元測試——
 * 只有 {@code OrderControllerE2ETest}/{@code OrderPaymentControllerE2ETest}/
 * {@code BuyerOrderJourneyE2ETest}/{@code M11LogisticsOrderIntegrationTest} 這類需要完整
 * Spring Context + 真實 DB 的 Controller 層 E2E/整合測試間接涵蓋部分流程，
 * 以及獨立的 {@code OrderStateMachineTest}（純狀態機邏輯，不涉及 OrderService 本身）。
 * 本測試類別補齊 OrderService 本身的單元測試缺口，目標為 7 個方法的正常/邊界/錯誤路徑覆蓋。
 *
 * <p>過程中發現 {@code updateOrderStatus} 完全沒有訂單擁有權/租戶檢查（同檔案內
 * getOrder/cancelOrder/getOrderStateLogs 皆有 owner-or-admin 檢查），已記錄為 DEF-024，
 * 並於 Sprint 70 修復——新增 {@code checkOrderStatusUpdateAuthorization}，採「訂單擁有者本人
 * （比照 DEF-018 買家模式）or 本租戶（比照 LogisticsService.checkOrderTenant 的 DEF-019
 * 賣家/店主模式）or admin」三者其一放行，越權回 403/E_1007。詳見下方 updateOrderStatus 測試區塊。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderService 單元測試（Sprint 69）")
class OrderServiceTest {

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

    @InjectMocks
    private OrderService orderService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID LISTING_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ========== 共用 fixture ==========

    private User userFixture() {
        User user = User.builder().build();
        user.setId(USER_ID);
        return user;
    }

    private Tenant tenantFixture() {
        Tenant tenant = Tenant.builder().build();
        tenant.setId(TENANT_ID);
        return tenant;
    }

    private Listing listingFixture(final Listing.ListingType type, final Listing.ListingStatus status) {
        return Listing.builder()
                .id(LISTING_ID)
                .listingType(type)
                .status(status)
                .title("Test Listing")
                .basePrice(BigDecimal.valueOf(1000))
                .build();
    }

    private CartDto.CartItemResponse cartItem(final UUID listingId, final UUID skuId,
            final int quantity, final BigDecimal unitPrice) {
        return CartDto.CartItemResponse.builder()
                .listingId(listingId)
                .skuId(skuId)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    private CartDto.CartResponse cartOf(final List<CartDto.CartItemResponse> items) {
        return CartDto.CartResponse.builder().items(items).build();
    }

    private Order orderOf(final UUID userId, final Order.OrderStatus status) {
        Order order = Order.builder()
                .userId(userId)
                .tenantId(TENANT_ID)
                .orderType(Listing.ListingType.PRODUCT)
                .status(status)
                .totalAmount(BigDecimal.valueOf(1000))
                .currency("TWD")
                .items(new java.util.ArrayList<>())
                .build();
        order.setId(ORDER_ID);
        return order;
    }

    private OrderDto.CreateRequest productRequest() {
        return OrderDto.CreateRequest.builder().orderType("PRODUCT").build();
    }

    private void asAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ========== createOrderFromCart：PRODUCT ==========

    @Test
    @DisplayName("createOrderFromCart(PRODUCT)：購物車有效商品 → 建立訂單並清空購物車")
    void createOrderFromCart_product_happyPath() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantFixture()));

        CartDto.CartItemResponse item = cartItem(LISTING_ID, null, 2, BigDecimal.valueOf(100));
        when(cartService.getCart(USER_ID, TENANT_ID)).thenReturn(cartOf(List.of(item)));
        when(listingRepository.findById(LISTING_ID))
                .thenReturn(Optional.of(listingFixture(Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE)));
        when(shippingTemplateService.calculateFeeForTenant(TENANT_ID, BigDecimal.valueOf(200)))
                .thenReturn(BigDecimal.valueOf(60));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            return o;
        });

        OrderDto.OrderResponse response = orderService.createOrderFromCart(productRequest());

        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(260));
        assertThat(response.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(60));
        assertThat(response.getItems()).hasSize(1);
        verify(cartService).clearCart(USER_ID, TENANT_ID);
        verify(orderStateLogRepository).save(any(OrderStateLog.class));
    }

    @Test
    @DisplayName("createOrderFromCart(PRODUCT)：使用者不存在 → E_1006")
    void createOrderFromCart_userNotFound() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrderFromCart(productRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1006);
    }

    @Test
    @DisplayName("createOrderFromCart(PRODUCT)：租戶不存在 → E_2000")
    void createOrderFromCart_tenantNotFound() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrderFromCart(productRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_2000);
    }

    @Test
    @DisplayName("createOrderFromCart(PRODUCT)：購物車為空 → E_5004")
    void createOrderFromCart_emptyCart() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantFixture()));
        when(cartService.getCart(USER_ID, TENANT_ID)).thenReturn(cartOf(List.of()));

        assertThatThrownBy(() -> orderService.createOrderFromCart(productRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5004);
    }

    @Test
    @DisplayName("createOrderFromCart(PRODUCT)：購物車內刊登項目不存在 → E_3000")
    void createOrderFromCart_listingNotFound() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantFixture()));
        when(cartService.getCart(USER_ID, TENANT_ID))
                .thenReturn(cartOf(List.of(cartItem(LISTING_ID, null, 1, BigDecimal.TEN))));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrderFromCart(productRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_3000);
    }

    @Test
    @DisplayName("createOrderFromCart(PRODUCT)：刊登項目未上架 → E_3002")
    void createOrderFromCart_listingNotActive() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantFixture()));
        when(cartService.getCart(USER_ID, TENANT_ID))
                .thenReturn(cartOf(List.of(cartItem(LISTING_ID, null, 1, BigDecimal.TEN))));
        when(listingRepository.findById(LISTING_ID))
                .thenReturn(Optional.of(listingFixture(Listing.ListingType.PRODUCT, Listing.ListingStatus.INACTIVE)));

        assertThatThrownBy(() -> orderService.createOrderFromCart(productRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_3002);
    }

    @Test
    @DisplayName("createOrderFromCart(PRODUCT)：cartItem 帶 skuId 但找不到 SKU → 不拋錯，item.sku 為 null")
    void createOrderFromCart_skuNotFound_fallsBackToNullSku() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        UUID skuId = UUID.randomUUID();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantFixture()));
        when(cartService.getCart(USER_ID, TENANT_ID))
                .thenReturn(cartOf(List.of(cartItem(LISTING_ID, skuId, 1, BigDecimal.TEN))));
        when(listingRepository.findById(LISTING_ID))
                .thenReturn(Optional.of(listingFixture(Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE)));
        when(productSkuRepository.findById(skuId)).thenReturn(Optional.empty());
        when(shippingTemplateService.calculateFeeForTenant(any(), any())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto.OrderResponse response = orderService.createOrderFromCart(productRequest());

        assertThat(response.getItems().get(0).getSkuId()).isNull();
    }

    // ========== createOrderFromCart：ROOM（createRoomOrder 分支） ==========

    private OrderDto.CreateRequest roomRequest(final LocalDate checkIn, final LocalDate checkOut) {
        return OrderDto.CreateRequest.builder()
                .orderType("ROOM")
                .listingId(LISTING_ID)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .guestCount(2)
                .guestName("Alice")
                .guestPhone("0912345678")
                .guestEmail("alice@example.com")
                .specialRequests("late check-in")
                .build();
    }

    @Test
    @DisplayName("createOrderFromCart(ROOM)：合法預訂 → 依晚數 x 房價建立訂單")
    void createOrderFromCart_room_happyPath() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(listingRepository.findById(LISTING_ID))
                .thenReturn(Optional.of(listingFixture(Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE)));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantFixture()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            return o;
        });

        OrderDto.OrderResponse response = orderService.createOrderFromCart(
                roomRequest(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3)));

        // basePrice=1000 * 2 晚 = 2000
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(response.getGuestCount()).isEqualTo(2);
        assertThat(response.getGuestName()).isEqualTo("Alice");
        verify(orderStateLogRepository).save(any(OrderStateLog.class));
    }

    @Test
    @DisplayName("createOrderFromCart(ROOM)：租戶不存在 → 回退系統租戶（by id）")
    void createOrderFromCart_room_tenantFallbackToSystemTenantById() {
        // 注意：Order.tenantId 是 insertable=false/updatable=false 的影子欄位，僅由 Hibernate 讀取 DB 時回填，
        // 純 Mockito mock（無真實持久化）無法觀察到 response.getTenantId()，故改以 ArgumentCaptor 驗證
        // 實際傳入 orderRepository.save 的 Order.getTenant() 是否為回退取得的系統租戶。
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        UUID systemTenantId = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(listingRepository.findById(LISTING_ID))
                .thenReturn(Optional.of(listingFixture(Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE)));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        Tenant systemTenant = Tenant.builder().build();
        systemTenant.setId(systemTenantId);
        when(tenantRepository.findById(systemTenantId)).thenReturn(Optional.of(systemTenant));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.createOrderFromCart(roomRequest(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2)));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getTenant().getId()).isEqualTo(systemTenantId);
    }

    @Test
    @DisplayName("createOrderFromCart(ROOM)：租戶與系統租戶(by id)皆不存在 → 回退 slug=platform")
    void createOrderFromCart_room_tenantFallbackToPlatformSlug() {
        // 同上：以 ArgumentCaptor 驗證實際使用的 Tenant，避免依賴無法在純 mock 情境下回填的影子欄位。
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        UUID systemTenantId = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
        UUID platformTenantId = UUID.randomUUID();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(listingRepository.findById(LISTING_ID))
                .thenReturn(Optional.of(listingFixture(Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE)));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(systemTenantId)).thenReturn(Optional.empty());
        Tenant platformTenant = Tenant.builder().build();
        platformTenant.setId(platformTenantId);
        when(tenantRepository.findBySlug("platform")).thenReturn(Optional.of(platformTenant));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.createOrderFromCart(roomRequest(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2)));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getTenant().getId()).isEqualTo(platformTenantId);
    }

    @Test
    @DisplayName("createOrderFromCart(ROOM)：租戶與系統租戶皆不存在 → E_2000")
    void createOrderFromCart_room_noTenantAtAll() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        UUID systemTenantId = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(listingRepository.findById(LISTING_ID))
                .thenReturn(Optional.of(listingFixture(Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE)));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(systemTenantId)).thenReturn(Optional.empty());
        when(tenantRepository.findBySlug("platform")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrderFromCart(
                roomRequest(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_2000);
    }

    @Test
    @DisplayName("createOrderFromCart(ROOM)：缺少 listingId → E_9005")
    void createOrderFromCart_room_missingListingId() {
        TenantContext.setCurrentUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        OrderDto.CreateRequest req = OrderDto.CreateRequest.builder()
                .orderType("ROOM")
                .checkInDate(LocalDate.of(2026, 8, 1))
                .checkOutDate(LocalDate.of(2026, 8, 2))
                .build();

        assertThatThrownBy(() -> orderService.createOrderFromCart(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_9005);
    }

    @Test
    @DisplayName("createOrderFromCart(ROOM)：缺少入住/退房日期 → E_9005")
    void createOrderFromCart_room_missingDates() {
        TenantContext.setCurrentUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        OrderDto.CreateRequest req = OrderDto.CreateRequest.builder()
                .orderType("ROOM")
                .listingId(LISTING_ID)
                .build();

        assertThatThrownBy(() -> orderService.createOrderFromCart(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_9005);
    }

    @Test
    @DisplayName("createOrderFromCart(ROOM)：退房早於入住 → E_4003")
    void createOrderFromCart_room_checkOutBeforeCheckIn() {
        TenantContext.setCurrentUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));

        assertThatThrownBy(() -> orderService.createOrderFromCart(
                roomRequest(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_4003);
    }

    @Test
    @DisplayName("createOrderFromCart(ROOM)：入住退房同一天（0 晚）→ E_4003")
    void createOrderFromCart_room_zeroNights() {
        // resolveTenant 在 calculateNights 之前執行，須先讓租戶解析成功，才能真正測到 0 晚的 E_4003。
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(listingRepository.findById(LISTING_ID))
                .thenReturn(Optional.of(listingFixture(Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE)));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantFixture()));

        LocalDate sameDay = LocalDate.of(2026, 8, 1);
        assertThatThrownBy(() -> orderService.createOrderFromCart(roomRequest(sameDay, sameDay)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_4003);
    }

    @Test
    @DisplayName("createOrderFromCart(ROOM)：房源不存在 → E_3000")
    void createOrderFromCart_room_listingNotFound() {
        TenantContext.setCurrentUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrderFromCart(
                roomRequest(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_3000);
    }

    @Test
    @DisplayName("createOrderFromCart(ROOM)：刊登項目非 ROOM 型別 → E_9005")
    void createOrderFromCart_room_notRoomType() {
        TenantContext.setCurrentUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(listingRepository.findById(LISTING_ID))
                .thenReturn(Optional.of(listingFixture(Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE)));

        assertThatThrownBy(() -> orderService.createOrderFromCart(
                roomRequest(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_9005);
    }

    @Test
    @DisplayName("createOrderFromCart(ROOM)：房源未上架 → E_3002")
    void createOrderFromCart_room_listingNotActive() {
        TenantContext.setCurrentUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userFixture()));
        when(listingRepository.findById(LISTING_ID))
                .thenReturn(Optional.of(listingFixture(Listing.ListingType.ROOM, Listing.ListingStatus.DRAFT)));

        assertThatThrownBy(() -> orderService.createOrderFromCart(
                roomRequest(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_3002);
    }

    // ========== createBooking（民宿預訂，目前為未完成的 stub） ==========

    private OrderDto.CreateRequest bookingRequest(final LocalDate checkIn, final LocalDate checkOut) {
        return OrderDto.CreateRequest.builder()
                .orderType("ROOM")
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .build();
    }

    @Test
    @DisplayName("createBooking：缺少入住/退房日期 → E_9005")
    void createBooking_missingDates() {
        TenantContext.setCurrentUser(USER_ID);
        OrderDto.CreateRequest req = OrderDto.CreateRequest.builder().orderType("ROOM").build();

        assertThatThrownBy(() -> orderService.createBooking(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_9005);
    }

    @Test
    @DisplayName("createBooking：退房早於入住 → E_4003")
    void createBooking_checkOutBeforeCheckIn() {
        TenantContext.setCurrentUser(USER_ID);

        assertThatThrownBy(() -> orderService.createBooking(
                bookingRequest(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_4003);
    }

    @Test
    @DisplayName("createBooking：日期合法時 → 拋 UnsupportedOperationException（尚未實作 RoomBookingService，記錄現況非本 Sprint 修復範圍）")
    void createBooking_validDates_throwsUnsupported() {
        TenantContext.setCurrentUser(USER_ID);

        assertThatThrownBy(() -> orderService.createBooking(
                bookingRequest(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ========== getUserOrders ==========

    @Test
    @DisplayName("getUserOrders：依 userId 分頁查詢，size 超過上限時裁切為 100")
    void getUserOrders_capsPageSizeAt100() {
        TenantContext.setCurrentUser(USER_ID);
        Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(USER_ID),
                any(Pageable.class))).thenReturn(page);

        Page<OrderDto.OrderListResponse> result = orderService.getUserOrders(0, 500, "createdAt", "DESC");

        assertThat(result.getContent()).hasSize(1);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByUserIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(USER_ID),
                captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("getUserOrders：正常分頁大小與排序方向原樣傳遞")
    void getUserOrders_passesThroughSortAndSize() {
        TenantContext.setCurrentUser(USER_ID);
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(USER_ID),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        orderService.getUserOrders(1, 20, "createdAt", "ASC");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByUserIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(USER_ID),
                captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(org.springframework.data.domain.Sort.Direction.ASC);
    }

    // ========== getOrder ==========

    @Test
    @DisplayName("getOrder：訂單擁有者本人查詢 → 成功")
    void getOrder_owner_success() {
        TenantContext.setCurrentUser(USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.CREATED)));

        OrderDto.OrderResponse response = orderService.getOrder(ORDER_ID);

        assertThat(response.getId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("getOrder：非擁有者、非 admin 查詢他人訂單 → E_1007")
    void getOrder_otherUser_throwsE1007() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.CREATED)));

        assertThatThrownBy(() -> orderService.getOrder(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("getOrder：admin 查詢他人訂單 → 放行")
    void getOrder_admin_bypassesOwnership() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        asAdmin();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.CREATED)));

        OrderDto.OrderResponse response = orderService.getOrder(ORDER_ID);

        assertThat(response.getId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("getOrder：訂單不存在 → E_5000")
    void getOrder_notFound() {
        TenantContext.setCurrentUser(USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5000);
    }

    // ========== updateOrderStatus ==========
    //
    // DEF-024（Sprint 70 已修復）：updateOrderStatus 原本完全沒有訂單擁有權/租戶檢查——同檔案內
    // getOrder/cancelOrder/getOrderStateLogs 皆有 owner-or-admin 檢查，唯獨此方法沒有，形同任一
    // 租戶的賣家（order:update 由 SELLER/STORE_OWNER/ADMIN/SUPER_ADMIN 持有）可對「任意 orderId」
    // 執行狀態轉換（跨租戶 IDOR）。修復後改為 checkOrderStatusUpdateAuthorization：訂單擁有者本人
    // （比照 DEF-018 買家模式，對應買家透過付款流程觸發）or 本租戶（比照 LogisticsService.
    // checkOrderTenant 的 DEF-019 賣家/店主模式，對應賣家直接呼叫 PATCH 端點）or admin，三者其一
    // 放行，越權回 403/E_1007，且置於狀態機檢查之前。

    @Test
    @DisplayName("updateOrderStatus：合法轉換 CREATED→PAID → 成功並記錄狀態日誌")
    void updateOrderStatus_validTransition() {
        TenantContext.setCurrentUser(USER_ID);
        Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto.OrderResponse response = orderService.updateOrderStatus(ORDER_ID, "PAID", "buyer paid");

        assertThat(response.getStatus()).isEqualTo("PAID");
        ArgumentCaptor<OrderStateLog> logCaptor = ArgumentCaptor.forClass(OrderStateLog.class);
        verify(orderStateLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getFromStatus()).isEqualTo("CREATED");
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo("PAID");
        assertThat(logCaptor.getValue().getReason()).isEqualTo("buyer paid");
    }

    @Test
    @DisplayName("updateOrderStatus：不合法轉換（狀態機拒絕）→ E_5001")
    void updateOrderStatus_invalidTransition_throwsE5001() {
        TenantContext.setCurrentUser(USER_ID);
        Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(ORDER_ID, "DELIVERED", "skip states"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5001);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("updateOrderStatus：訂單不存在 → E_5000")
    void updateOrderStatus_orderNotFound() {
        TenantContext.setCurrentUser(USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrderStatus(ORDER_ID, "PAID", "reason"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5000);
    }

    @Test
    @DisplayName("updateOrderStatus：他租戶賣家對其他租戶訂單執行狀態轉換 → E_1007（先於狀態機檢查觸發）")
    void updateOrderStatus_otherTenantNonOwner_throwsE1007() {
        // 訂單屬 TENANT_ID、擁有者為 USER_ID；當前使用者為 OTHER_USER_ID、當前租戶為 OTHER_TENANT_ID
        // （模擬另一租戶的賣家）。狀態故意設可合法轉換的 CREATED，若得 E_1007（而非狀態機允許轉換
        // 成功）證明擁有權/租戶檢查先於狀態機檢查觸發，杜絕跨租戶 IDOR。
        TenantContext.setCurrentUser(OTHER_USER_ID);
        TenantContext.setCurrentTenant(OTHER_TENANT_ID);
        Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(ORDER_ID, "PAID", "cross-tenant attack"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("updateOrderStatus：本租戶賣家（非訂單擁有者）執行狀態轉換 → 放行（續走狀態機邏輯）")
    void updateOrderStatus_sameTenantNonOwner_passesAuthorization() {
        // 當前使用者為 OTHER_USER_ID（非訂單擁有者 USER_ID），但當前租戶與訂單租戶相同（TENANT_ID）
        // ——模擬訂單所屬租戶內的賣家/店主標記出貨等合法操作，證明修復未破壞既有業務能力。
        TenantContext.setCurrentUser(OTHER_USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        Order order = orderOf(USER_ID, Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto.OrderResponse response = orderService.updateOrderStatus(ORDER_ID, "SHIPPING", "seller ships order");

        assertThat(response.getStatus()).isEqualTo("SHIPPING");
    }

    @Test
    @DisplayName("updateOrderStatus：admin 跨租戶放行（續走狀態機邏輯）")
    void updateOrderStatus_admin_bypassesTenant() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        TenantContext.setCurrentTenant(OTHER_TENANT_ID);
        asAdmin();
        Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto.OrderResponse response = orderService.updateOrderStatus(ORDER_ID, "PAID", "admin override");

        assertThat(response.getStatus()).isEqualTo("PAID");
    }

    // ========== cancelOrder ==========

    @Test
    @DisplayName("cancelOrder：擁有者取消 CREATED 訂單 → CANCELLED，僅一筆狀態日誌")
    void cancelOrder_owner_created_cancelsWithoutRefund() {
        TenantContext.setCurrentUser(USER_ID);
        Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto.OrderResponse response = orderService.cancelOrder(ORDER_ID, "changed mind");

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        verify(orderStateLogRepository, times(1)).save(any(OrderStateLog.class));
    }

    @Test
    @DisplayName("cancelOrder：擁有者取消 PAID 訂單 → 先 CANCELLED 再自動轉 REFUNDING，兩筆狀態日誌")
    void cancelOrder_owner_paid_triggersAutoRefund() {
        TenantContext.setCurrentUser(USER_ID);
        Order order = orderOf(USER_ID, Order.OrderStatus.PAID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto.OrderResponse response = orderService.cancelOrder(ORDER_ID, "refund me");

        assertThat(response.getStatus()).isEqualTo("REFUNDING");
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(orderStateLogRepository, times(2)).save(any(OrderStateLog.class));
    }

    @Test
    @DisplayName("cancelOrder：擁有者取消 CONFIRMED 訂單 → CANCELLED（非 PAID 不觸發自動退款）")
    void cancelOrder_owner_confirmed_noAutoRefund() {
        TenantContext.setCurrentUser(USER_ID);
        Order order = orderOf(USER_ID, Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto.OrderResponse response = orderService.cancelOrder(ORDER_ID, "reason");

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("cancelOrder：非擁有者、非 admin 取消他人訂單 → E_1007")
    void cancelOrder_otherUser_throwsE1007() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.CREATED)));

        assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID, "not mine"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("cancelOrder：admin 取消他人訂單 → 放行")
    void cancelOrder_admin_bypassesOwnership() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        asAdmin();
        Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto.OrderResponse response = orderService.cancelOrder(ORDER_ID, "admin cancels");

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("cancelOrder：狀態不允許取消（如 SHIPPING）→ E_5002")
    void cancelOrder_notCancellableStatus_throwsE5002() {
        TenantContext.setCurrentUser(USER_ID);
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.SHIPPING)));

        assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID, "too late"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5002);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("cancelOrder：訂單不存在 → E_5000")
    void cancelOrder_notFound() {
        TenantContext.setCurrentUser(USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID, "reason"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5000);
    }

    // ========== getOrderStateLogs ==========

    private OrderStateLog stateLog(final Order order, final int sequence, final String from, final String to) {
        return OrderStateLog.builder()
                .order(order)
                .sequence(sequence)
                .fromStatus(from)
                .toStatus(to)
                .changedBy(USER_ID)
                .build();
    }

    @Test
    @DisplayName("getOrderStateLogs：擁有者查詢自己訂單的狀態日誌 → 成功回傳排序清單")
    void getOrderStateLogs_owner_success() {
        TenantContext.setCurrentUser(USER_ID);
        Order order = orderOf(USER_ID, Order.OrderStatus.PAID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderStateLogRepository.findByOrderIdOrderBySequenceAsc(ORDER_ID))
                .thenReturn(List.of(stateLog(order, 1, null, "CREATED"), stateLog(order, 2, "CREATED", "PAID")));

        List<OrderDto.StateLogResponse> logs = orderService.getOrderStateLogs(ORDER_ID);

        assertThat(logs).hasSize(2);
        assertThat(logs.get(1).getToStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("getOrderStateLogs：無日誌時回傳空清單")
    void getOrderStateLogs_empty() {
        TenantContext.setCurrentUser(USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.CREATED)));
        when(orderStateLogRepository.findByOrderIdOrderBySequenceAsc(ORDER_ID)).thenReturn(List.of());

        List<OrderDto.StateLogResponse> logs = orderService.getOrderStateLogs(ORDER_ID);

        assertThat(logs).isEmpty();
    }

    @Test
    @DisplayName("getOrderStateLogs：非擁有者、非 admin 查詢他人訂單日誌 → E_1007")
    void getOrderStateLogs_otherUser_throwsE1007() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.CREATED)));

        assertThatThrownBy(() -> orderService.getOrderStateLogs(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("getOrderStateLogs：admin 查詢他人訂單日誌 → 放行")
    void getOrderStateLogs_admin_bypassesOwnership() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        asAdmin();
        Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderStateLogRepository.findByOrderIdOrderBySequenceAsc(ORDER_ID))
                .thenReturn(List.of(stateLog(order, 1, null, "CREATED")));

        List<OrderDto.StateLogResponse> logs = orderService.getOrderStateLogs(ORDER_ID);

        assertThat(logs).hasSize(1);
    }

    @Test
    @DisplayName("getOrderStateLogs：訂單不存在 → E_5000")
    void getOrderStateLogs_notFound() {
        TenantContext.setCurrentUser(USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderStateLogs(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5000);
    }
}
