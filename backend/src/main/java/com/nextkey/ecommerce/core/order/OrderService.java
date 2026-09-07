package com.nextkey.ecommerce.core.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.logistics.ShippingTemplateService;
import com.nextkey.ecommerce.core.product.ProductInventoryService;
import com.nextkey.ecommerce.core.promo.PromoService;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.promo.PromoCodeUsage;
import com.nextkey.ecommerce.domain.repository.PromoCodeUsageRepository;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.order.OrderStateLog;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.core.user.AddressService;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.Address;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.OrderStateLogRepository;
import com.nextkey.ecommerce.domain.repository.ProductRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 訂單服務
 * 處理訂單建立、狀態更新、取消等操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStateLogRepository orderStateLogRepository;
    private final ListingRepository listingRepository;
    @SuppressWarnings("unused")
    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    @SuppressWarnings("unused")
    private final RoomRepository roomRepository;
    private final RedisCartService cartService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ShippingTemplateService shippingTemplateService;
    private final AddressService addressService;
    private final ProductInventoryService productInventoryService;
    private final PromoService promoService;
    private final PromoCodeUsageRepository promoCodeUsageRepository;

    /**
     * 建立訂單（從購物車或直接預訂）
     * ROOM 類型訂單可以直接傳入預訂資料，不需要購物車
     * PRODUCT 類型訂單需要購物車中有商品
     */
    @CacheEvict(value = "dashboardStats", allEntries = true)
    @Transactional
    public OrderDto.OrderResponse createOrderFromCart(OrderDto.CreateRequest request) {
        UUID userId = TenantContext.getCurrentUser();
        UUID tenantId = TenantContext.getCurrentTenant();

        // 取得用戶
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));

        Listing.ListingType orderType = Listing.ListingType.valueOf(request.getOrderType());

        // ROOM 類型訂單：直接建立預訂訂單，不需要 tenant lookup
        if (orderType == Listing.ListingType.ROOM) {
            return createRoomOrder(request, user);
        }

        // PRODUCT 類型訂單：需要租戶和購物車
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        CartDto.CartResponse cart = cartService.getCart(userId, tenantId);
        if (cart.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.E_5004, "Cart is empty");
        }

        // Sprint 88：購物車為 ROOM/PRODUCT 共用，PRODUCT 訂單僅取用 PRODUCT 項目，
        // ROOM 項目留在購物車給訂房流程另外結帳（避免誤併入商品訂單或被整個清空）
        List<CartDto.CartItemResponse> productItems = cart.getItems().stream()
                .filter(i -> "PRODUCT".equals(i.getListingType()))
                .collect(Collectors.toList());
        if (productItems.isEmpty()) {
            throw new BusinessException(ErrorCode.E_5004, "Cart has no product items");
        }

        // Sprint 100（PRD §9.5.1）：在建立訂單之前重新驗證促銷碼並計算折扣。促銷碼於加入購物車時
        // 已驗過一次，但購物車存活於 Redis TTL 期間，期間可能過期/停用/售罄，故此處必須重驗，
        // 不可沿用加入購物車當下的結果。只取券碼、不採信購物車算好的折扣：後者是 fallback-tolerant
        // 的顯示用計算（券失效時靜默回退原價），不可作為收款依據。
        PromoCode promo = promoService.resolveValidPromoForCheckout(
                cartService.getAppliedPromoCode(userId, tenantId), tenantId, userId);
        BigDecimal itemsTotal = productItems.stream()
                .map(CartDto.CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingFee = shippingTemplateService.calculateFeeForTenant(tenantId, itemsTotal);
        BigDecimal discount = promoService.computeCappedDiscount(promo, itemsTotal, shippingFee);

        OrderDto.OrderResponse response =
                buildProductOrder(tenant, user, request, productItems, promo, discount, shippingFee, userId);

        commitPromoUsage(promo, response.getId(), userId, tenantId);

        // 僅移除已處理的 PRODUCT 項目，保留購物車中其餘（如 ROOM）項目供另外結帳
        for (CartDto.CartItemResponse cartItem : productItems) {
            cartService.removeItem(userId, tenantId, cartItem.getCartItemKey());
        }

        log.info("Order created: orderId={}, userId={}, totalAmount={}, shippingFee={}, promoCode={}, discount={}",
                response.getId(), userId, response.getTotalAmount(), shippingFee,
                response.getPromoCode(), response.getDiscountAmount());
        return response;
    }

    /**
     * 建立 PRODUCT 訂單核心邏輯（購物車項目 → Order/OrderItem → 套用給定折扣 → 庫存預扣）。
     *
     * <p>Sprint 126（DEF-048 擴大範圍）抽出並改為 package-private 以上可見度：促銷碼「已由
     * 呼叫端解析、折扣已由呼叫端算好」（見 {@link PromoService#resolveValidPromoForCheckout}／
     * {@link PromoService#computeCappedDiscount}），本方法只負責把給定的 {@code promo}／
     * {@code discountAmount} 套用到訂單金額欄位，不再自行解析促銷碼、不佔用額度
     * （{@code commitPromoUsage}）、不動購物車 Redis 狀態——這三件事留給呼叫端在恰當時機各自處理。
     * 單一類型結帳（{@link #createOrderFromCart}）與合併結帳（{@code CombinedCheckoutService}，
     * 不同套件故本方法為 {@code public}）共用此方法：後者傳入分攤後的折扣金額，且不會接著呼叫
     * {@code commitPromoUsage}（額度改由合併結帳呼叫端在兩側都成功後統一佔用一次）。
     *
     * <p>回傳型別為 {@code OrderDto.OrderResponse}（而非 {@code Order} 實體）：呼叫端只需要
     * 已建立訂單的 id／金額欄位，比照 {@code BookingService.buildBookingCore} 同一慣例。
     */
    public OrderDto.OrderResponse buildProductOrder(final Tenant tenant, final User user,
            final OrderDto.CreateRequest request, final List<CartDto.CartItemResponse> productItems,
            final PromoCode promo, final BigDecimal discountAmount, final BigDecimal shippingFee,
            final UUID userId) {
        // Sprint 87：若提供 addressId，改以地址簿內容覆蓋手動輸入的收件欄位
        // （下單當下複製一份 snapshot，日後編輯/刪除地址簿項目不影響已建立訂單）
        String shippingAddress = request.getShippingAddress();
        String shippingRecipientName = request.getShippingRecipientName();
        String shippingPhone = request.getShippingPhone();
        if (request.getAddressId() != null) {
            Address address = addressService.getOwnedAddress(request.getAddressId(), userId);
            shippingAddress = formatAddressLine(address);
            shippingRecipientName = address.getRecipientName();
            shippingPhone = address.getPhone();
        }

        // 建立訂單
        Order order = Order.builder()
                .tenant(tenant)
                .user(user)
                .orderType(Listing.ListingType.PRODUCT)
                .status(Order.OrderStatus.CREATED)
                .shippingAddress(shippingAddress)
                .shippingRecipientName(shippingRecipientName)
                .shippingPhone(shippingPhone)
                .notes(request.getNotes())
                .currency("TWD")
                .items(new ArrayList<>())
                .build();

        // 計算總金額並建立訂單項目
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (CartDto.CartItemResponse cartItem : productItems) {
            // 檢查商品是否有效
            Listing listing = listingRepository.findById(cartItem.getListingId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_3000, "Listing not found: " + cartItem.getListingId()));

            if (!"ACTIVE".equals(listing.getStatus().name())) {
                throw new BusinessException(ErrorCode.E_3002, "Listing not active: " + cartItem.getListingId());
            }

            ProductSku sku = null;
            if (cartItem.getSkuId() != null) {
                sku = productSkuRepository.findById(cartItem.getSkuId()).orElse(null);
            }

            OrderItem item = OrderItem.builder()
                    .listing(listing)
                    .sku(sku)
                    .quantity(cartItem.getQuantity())
                    .unitPrice(cartItem.getUnitPrice())
                    .subtotal(cartItem.getSubtotal())
                    .build();

            order.addItem(item);
            itemsTotal = itemsTotal.add(cartItem.getSubtotal());
        }

        order.setShippingFee(shippingFee);
        if (promo != null) {
            order.setPromoCode(promo.getCode());
        }
        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(itemsTotal.add(shippingFee).subtract(discountAmount));

        order = orderRepository.save(order);

        // Sprint 88（AI-2422）：檢查並預扣庫存，避免超賣；庫存不足拋例外，整個 @Transactional
        // 方法回滾，不留部分建立的訂單。
        // Sprint 115（DEF-065）：刻意排在 save() 之後。預扣要同時寫下一筆 RESERVE 流水帳，
        // 而流水帳的 reference_id／order_item_id 取自 Order 與 OrderItem 的 id——兩者皆為
        // @GeneratedValue，save() 之前都是 null，在那個時點寫入只會得到一批查不到來源的孤兒列。
        // 移到 save() 之後不影響超賣防護：兩者同屬一個交易，庫存不足時訂單一樣不會留下。
        productInventoryService.reserveForOrder(order);

        recordStateLog(order, null, Order.OrderStatus.CREATED.name(), userId, "Order created from cart");

        return toOrderResponse(order);
    }

    /**
     * 訂單成立後佔用優惠券額度（總量 + 每人限用），並清除購物車上的促銷碼，
     * 避免同一張券被下一張訂單重複沿用。
     *
     * <p>Sprint 102（DEF-046）起，總量額度改由 {@code PromoService.tryConsumeUsageQuota}
     * 以條件式 UPDATE 原子取得。{@code resolveValidPromoForCheckout} 的前置檢查僅用於
     * 提早給出精確錯誤訊息，**不是**額度的把關者——真正的把關在這裡：檢查與遞增之間
     * 若有任何間隙，併發結帳就能雙雙通過而超發限量券。
     *
     * <p>佔用失敗時拋例外讓整筆交易回滾，而非靜默改以原價成立訂單：買家在購物車看到的是
     * 折扣後金額，靜默回退等同在買家不知情下多收款（沿用 Sprint 100 已確立的處置原則）。
     *
     * <p>Sprint 126（DEF-048 擴大範圍）簽章改接受 {@code orderId} 而非整個 {@code Order}
     * 實體——{@link #buildProductOrder} 現在回傳 {@code OrderDto.OrderResponse}（供合併結帳的
     * 呼叫端共用），呼叫端只需該筆訂單的 id。
     */
    private void commitPromoUsage(final PromoCode promo, final UUID orderId,
            final UUID userId, final UUID tenantId) {
        if (promo == null) {
            return;
        }
        if (!promoService.tryConsumeUsageQuota(promo)) {
            throw new BusinessException(ErrorCode.E_5009,
                    "Promo code sold out during checkout: " + promo.getCode());
        }
        // 上一行的條件式 UPDATE 已取得該 promo 資料列的行鎖並持有至交易結束，
        // 同一張券的併發結帳到此已序列化，此時重查每人限用才擋得住
        // 「同一買家同時送出兩筆訂單」——只靠 resolveValidPromoForCheckout 的前置檢查，
        // 兩筆請求會在任何一筆寫入用券紀錄之前都讀到 0，雙雙放行。
        if (promoService.perUserLimitReached(promo, userId)) {
            throw new BusinessException(ErrorCode.E_5009,
                    "Promo code per-user usage limit reached: " + promo.getCode());
        }
        promoCodeUsageRepository.save(PromoCodeUsage.builder()
                .promoCodeId(promo.getId())
                .userId(userId)
                .orderId(orderId)
                .build());
        cartService.removePromoCode(userId, tenantId);
    }

    /**
     * 訂單取消時評估是否退還優惠券額度（PRD §2630「優惠券：若已使用促銷碼，則退還」）。
     *
     * <p>Sprint 126（DEF-048 擴大範圍）改呼叫 {@link PromoService#releaseOrderSide}：單一類型
     * 用券紀錄行為不變（立即 REVOKED＋釋放額度）；合併結帳（PRODUCT+ROOM 一次結清）用券紀錄
     * 則依使用者拍板「兩邊都取消才退還」，只有 Booking 側也已取消才會真正釋放。若不做此退還，
     * 取消訂單會永久吃掉券的額度——那等於在修復本缺口的同時親手做出下一個同類缺口。
     */
    private void refundPromoUsage(final Order order) {
        if (order.getPromoCode() == null || order.getPromoCode().isBlank()) {
            return;
        }
        List<PromoCodeUsage> usages = promoCodeUsageRepository.findByOrderIdAndStatus(
                order.getId(), PromoCodeUsage.UsageStatus.ACTIVE);
        int revokedCount = 0;
        for (PromoCodeUsage usage : usages) {
            if (promoService.releaseOrderSide(usage)) {
                revokedCount++;
            }
        }
        log.info("Promo usage refund evaluated on order cancellation: orderId={}, promoCode={}, "
                        + "usageCount={}, revokedCount={}",
                order.getId(), order.getPromoCode(), usages.size(), revokedCount);
    }

    /**
     * 建立 ROOM 預訂訂單
     */
    private OrderDto.OrderResponse createRoomOrder(OrderDto.CreateRequest request,
                                                   com.nextkey.ecommerce.domain.model.user.User user) {
        UUID userId = user.getId();
        UUID tenantId = TenantContext.getCurrentTenant();

        // 驗證必填欄位
        validateRoomOrderRequest(request);

        // 取得 Room Listing 及其價格資訊
        Listing roomListing = getActiveRoomListing(request.getListingId());

        // 嘗試取得租戶，若不存在則使用系統預設租戶
        var tenant = resolveTenant(tenantId);

        // 計算入住晚數
        long nights = calculateNights(request.getCheckInDate(), request.getCheckOutDate());

        // 根據 basePrice 計算總金額
        BigDecimal pricePerNight = roomListing.getBasePrice();
        BigDecimal totalAmount = pricePerNight.multiply(BigDecimal.valueOf(nights));

        log.info("ROOM order price calculation: listingId={}, checkIn={}, checkOut={}, nights={}, pricePerNight={}, totalAmount={}",
                request.getListingId(), request.getCheckInDate(), request.getCheckOutDate(), nights, pricePerNight, totalAmount);

        // 建立 Room 訂單的 metadata，包含 guestCount 等資訊
        Map<String, Object> orderMetadata = buildRoomOrderMetadata(request);

        // 建立 Room 訂單
        Order order = Order.builder()
                .tenant(tenant)
                .user(user)
                .orderType(Listing.ListingType.ROOM)
                .status(Order.OrderStatus.CREATED)
                .notes(buildRoomNotes(request))
                .currency("TWD")
                .totalAmount(totalAmount)
                .guestCount(request.getGuestCount())
                .items(new ArrayList<>())
                .metadata(orderMetadata)
                .build();

        order = orderRepository.save(order);

        // 記錄狀態日誌
        recordStateLog(order, null, Order.OrderStatus.CREATED.name(), userId, "ROOM booking created");

        log.info("ROOM order created: orderId={}, userId={}", order.getId(), userId);
        return toOrderResponse(order);
    }

    private void validateRoomOrderRequest(OrderDto.CreateRequest request) {
        if (request.getListingId() == null) {
            throw new BusinessException(ErrorCode.E_9005, "Listing ID is required for ROOM orders");
        }
        if (request.getCheckInDate() == null || request.getCheckOutDate() == null) {
            throw new BusinessException(ErrorCode.E_9005, "Check-in and check-out dates are required for ROOM orders");
        }
        if (request.getCheckOutDate().isBefore(request.getCheckInDate())) {
            throw new BusinessException(ErrorCode.E_4003, "Check-out must be after check-in");
        }
    }

    private Listing getActiveRoomListing(UUID listingId) {
        Listing roomListing = listingRepository.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3000, "Room listing not found: " + listingId));

        if (roomListing.getListingType() != Listing.ListingType.ROOM) {
            throw new BusinessException(ErrorCode.E_9005, "Listing is not a ROOM type");
        }
        if (!"ACTIVE".equals(roomListing.getStatus().name())) {
            throw new BusinessException(ErrorCode.E_3002, "Room listing not active: " + listingId);
        }
        return roomListing;
    }

    private Tenant resolveTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseGet(() -> findSystemTenant());
    }

    private Tenant findSystemTenant() {
        var systemTenantId = java.util.UUID.fromString(
                com.nextkey.ecommerce.shared.constants.AppConstants.SYSTEM_TENANT_ID);
        var systemTenant = tenantRepository.findById(systemTenantId);
        if (systemTenant.isPresent()) {
            return systemTenant.get();
        }
        return tenantRepository.findBySlug("platform")
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "System tenant not found"));
    }

    private String formatAddressLine(final Address address) {
        StringBuilder sb = new StringBuilder();
        if (address.getPostalCode() != null && !address.getPostalCode().isBlank()) {
            sb.append(address.getPostalCode()).append(' ');
        }
        sb.append(address.getCity());
        if (address.getDistrict() != null && !address.getDistrict().isBlank()) {
            sb.append(address.getDistrict());
        }
        sb.append(address.getAddressLine());
        return sb.toString();
    }

    private long calculateNights(java.time.LocalDate checkIn, java.time.LocalDate checkOut) {
        long nights = java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights <= 0) {
            throw new BusinessException(ErrorCode.E_4003, "Check-out must be after check-in");
        }
        return nights;
    }

    private Map<String, Object> buildRoomOrderMetadata(OrderDto.CreateRequest request) {
        Map<String, Object> orderMetadata = new HashMap<>();
        if (request.getGuestCount() != null) {
            orderMetadata.put("guestCount", request.getGuestCount());
        }
        if (request.getGuestName() != null) {
            orderMetadata.put("guestName", request.getGuestName());
        }
        if (request.getGuestPhone() != null) {
            orderMetadata.put("guestPhone", request.getGuestPhone());
        }
        if (request.getGuestEmail() != null) {
            orderMetadata.put("guestEmail", request.getGuestEmail());
        }
        if (request.getSpecialRequests() != null) {
            orderMetadata.put("specialRequests", request.getSpecialRequests());
        }
        return orderMetadata;
    }

    private String buildRoomNotes(OrderDto.CreateRequest request) {
        StringBuilder notes = new StringBuilder();
        notes.append("Check-in: ").append(request.getCheckInDate()).append("\n");
        notes.append("Check-out: ").append(request.getCheckOutDate()).append("\n");
        if (request.getGuestCount() != null) {
            notes.append("Guests: ").append(request.getGuestCount()).append("\n");
        }
        if (request.getGuestName() != null) {
            notes.append("Guest Name: ").append(request.getGuestName()).append("\n");
        }
        if (request.getGuestPhone() != null) {
            notes.append("Guest Phone: ").append(request.getGuestPhone()).append("\n");
        }
        if (request.getGuestEmail() != null) {
            notes.append("Guest Email: ").append(request.getGuestEmail()).append("\n");
        }
        if (request.getSpecialRequests() != null) {
            notes.append("Special Requests: ").append(request.getSpecialRequests());
        }
        return notes.toString();
    }

    /**
     * 建立民宿預訂
     */
    @Transactional
    public OrderDto.BookingResponse createBooking(OrderDto.CreateRequest request) {
        UUID userId = TenantContext.getCurrentUser();

        // 檢查必填欄位
        if (request.getCheckInDate() == null || request.getCheckOutDate() == null) {
            throw new BusinessException(ErrorCode.E_9005, "Check-in and check-out dates are required");
        }

        if (request.getCheckOutDate().isBefore(request.getCheckInDate())) {
            throw new BusinessException(ErrorCode.E_4003, "Check-out must be after check-in");
        }

        // 這裡需要實現預訂邏輯（使用 Redis 分散式鎖檢查日期衝突）
        // 略過詳細實現，假設已存在 RoomBookingService

        log.info("Booking created for user: {}, room listing: {}", userId, request);
        throw new UnsupportedOperationException("Booking creation requires RoomBookingService implementation");
    }

    /**
     * 取得用戶訂單列表
     */
    @Transactional(readOnly = true)
    public Page<OrderDto.OrderListResponse> getUserOrders(int page, int size, String sortBy, String sortDir) {
        UUID userId = TenantContext.getCurrentUser();
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), sort);

        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
        return orders.map(this::toOrderListResponse);
    }

    /**
     * 取得訂單詳情
     */
    @Transactional(readOnly = true)
    public OrderDto.OrderResponse getOrder(UUID orderId) {
        UUID userId = TenantContext.getCurrentUser();
        Order order = findOrderById(orderId);

        // 獲取使用者角色用於權限判斷
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && (
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ||
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
        );

        // 如果不是 ADMIN，則必須是訂單擁有者（DEF-018：修復 getOrder IDOR）
        if (!isAdmin && !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to view this order");
        }

        return toOrderResponse(order);
    }

    /**
     * 更新訂單狀態
     */
    @CacheEvict(value = "dashboardStats", allEntries = true)
    @Transactional
    public OrderDto.OrderResponse updateOrderStatus(UUID orderId, String targetStatus, String reason) {
        UUID userId = TenantContext.getCurrentUser();
        Order order = findOrderById(orderId);

        // DEF-024：修復跨租戶 IDOR——訂單擁有者本人（買家自助付款流程）或本租戶
        // （賣家/店主管理自己租戶訂單，比照 LogisticsService.checkOrderTenant）或 admin 放行，
        // 置於狀態機檢查之前避免向未授權者洩漏訂單狀態
        checkOrderStatusUpdateAuthorization(order);

        String currentStatus = order.getStatus().name();
        OrderStateMachine.TransitionResult result = OrderStateMachine.canTransition(currentStatus, targetStatus);

        if (!result.isAllowed()) {
            throw new BusinessException(ErrorCode.E_5001, result.getReason());
        }

        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(targetStatus);

        // 🔴 併發防護：兩個併發請求都可能通過上面的 OrderStateMachine.canTransition 檢查（讀到同一份
        // 舊快照的 currentStatus，例如都以 PAID 為起點分別要求轉 CONFIRMED 與 REFUNDING），改用條件式
        // 原子 UPDATE 確保只有一邊真的轉換成功，另一邊拒絕，而非用舊快照互相覆寫對方剛寫入的新狀態。
        int updated = orderRepository.updateStatusIfCurrent(orderId, order.getStatus(), newStatus);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.E_5001,
                    "Order status changed concurrently, please retry: " + orderId);
        }
        order.setStatus(newStatus);

        // 記錄狀態日誌
        recordStateLog(order, currentStatus, targetStatus, userId, reason);

        log.info("Order status updated: orderId={}, {} -> {}", orderId, currentStatus, targetStatus);
        return toOrderResponse(order);
    }

    /**
     * 訂單狀態更新擁有權/租戶檢查（DEF-024：修復 updateOrderStatus 完全無檢查的跨租戶 IDOR）。
     *
     * <p>本方法有兩種合法呼叫情境：(1) 買家透過付款流程觸發（CREATED→PAID），呼叫端已由
     * {@code PaymentService.checkOrderPaymentOwnership} 限定本人；(2) 賣家/店主透過
     * {@code PATCH /v2/orders/{orderId}/status} 直接呼叫（Controller 層以 order:update 權限把關，
     * 僅 SELLER/STORE_OWNER/ADMIN/SUPER_ADMIN 持有），用於出貨等租戶內訂單管理。
     *
     * <p>因此採「訂單擁有者本人（比照 getOrder/cancelOrder/getOrderStateLogs 的 DEF-018 模式）
     * or 本租戶（比照 LogisticsService.checkOrderTenant 的 DEF-019 模式）or admin」三者其一放行，
     * 越權回 403/E_1007。
     */
    private void checkOrderStatusUpdateAuthorization(final Order order) {
        UUID userId = TenantContext.getCurrentUser();
        UUID tenantId = TenantContext.getCurrentTenant();
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && (
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ||
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
        );
        boolean isOwner = userId != null && userId.equals(order.getUserId());
        boolean isSameTenant = tenantId != null && tenantId.equals(order.getTenantId());

        if (!isAdmin && !isOwner && !isSameTenant) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to update this order's status");
        }
    }

    /**
     * 取消訂單
     * 注意：前端應确保使用者具有適當權限。此方法允許訂單擁有者取消自己的訂單
     */
    @CacheEvict(value = "dashboardStats", allEntries = true)
    @Transactional
    public OrderDto.OrderResponse cancelOrder(UUID orderId, String reason) {
        UUID userId = TenantContext.getCurrentUser();
        Order order = findOrderById(orderId);

        // 獲取使用者角色用於權限判斷
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && (
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ||
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
        );

        // 如果不是 ADMIN，則必須是訂單擁有者
        if (!isAdmin) {
            if (!userId.equals(order.getUserId())) {
                throw new BusinessException(ErrorCode.E_1007, "Not authorized to cancel this order");
            }
        }

        if (!OrderStateMachine.canCancel(order.getStatus().name())) {
            throw new BusinessException(ErrorCode.E_5002, "Order cannot be cancelled in current status");
        }

        String currentStatus = order.getStatus().name();
        order.setStatus(Order.OrderStatus.CANCELLED);
        order = orderRepository.save(order);

        // 記錄狀態日誌
        recordStateLog(order, currentStatus, Order.OrderStatus.CANCELLED.name(), userId, reason);

        // Sprint 88（AI-2422）：僅當取消前尚未付款（CREATED）才釋放預扣庫存；
        // 已付款（PAID）的庫存已由 deductForOrder 正式扣帳，本次不做退款回補（範圍外）
        if ("CREATED".equals(currentStatus)) {
            try {
                productInventoryService.releaseForOrder(order);
            } catch (RuntimeException e) {
                log.error("Failed to release reserved stock after order cancellation: orderId={}, error={}",
                        orderId, e.getMessage(), e);
            }
        }

        // Sprint 100（PRD §2630）：退還優惠券額度。與庫存釋放不同，此處不限 CREATED——
        // 訂單無論在付款前或付款後取消，該次用券都不應繼續佔用總量/每人限用額度。
        try {
            refundPromoUsage(order);
        } catch (RuntimeException e) {
            log.error("Failed to refund promo usage after order cancellation: orderId={}, error={}",
                    orderId, e.getMessage(), e);
        }

        // 如果已付款，需要退款流程
        if ("PAID".equals(currentStatus)) {
            // 創建退款记录，跳轉到 REFUNDING 狀態
            order.setStatus(Order.OrderStatus.REFUNDING);
            order = orderRepository.save(order);
            recordStateLog(order, Order.OrderStatus.CANCELLED.name(), Order.OrderStatus.REFUNDING.name(), userId, "Automatic refund triggered");
        }

        log.info("Order cancelled: orderId={}", orderId);
        return toOrderResponse(order);
    }

    /**
     * 取得訂單狀態日誌
     */
    @Transactional(readOnly = true)
    public List<OrderDto.StateLogResponse> getOrderStateLogs(UUID orderId) {
        UUID userId = TenantContext.getCurrentUser();
        Order order = findOrderById(orderId);

        // 獲取使用者角色用於權限判斷
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && (
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ||
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
        );

        // 如果不是 ADMIN，則必須是訂單擁有者
        if (!isAdmin && !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to view order logs");
        }

        List<OrderStateLog> logs = orderStateLogRepository.findByOrderIdOrderBySequenceAsc(orderId);
        return logs.stream()
                .map(this::toStateLogResponse)
                .collect(Collectors.toList());
    }

    // ========== Helper Methods ==========

    private Order findOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000));
    }

    private void recordStateLog(Order order, String fromStatus, String toStatus, UUID changedBy, String reason) {
        Integer maxSequence = orderStateLogRepository.findMaxSequenceByOrderId(order.getId());
        int nextSequence = maxSequence != null ? maxSequence + 1 : 1;

        OrderStateLog log = OrderStateLog.builder()
                .order(order)
                .sequence(nextSequence)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .reason(reason)
                .build();

        orderStateLogRepository.save(log);
    }

    private OrderDto.OrderResponse toOrderResponse(Order order) {
        List<OrderDto.OrderItemResponse> items = order.getItems().stream()
                .map(this::toOrderItemResponse)
                .collect(Collectors.toList());

        // 從 metadata 中取出 ROOM 類型訂單的資訊（向後相容）
        String guestName = null;
        String guestPhone = null;
        String guestEmail = null;
        if (order.getMetadata() != null) {
            guestName = (String) order.getMetadata().get("guestName");
            guestPhone = (String) order.getMetadata().get("guestPhone");
            guestEmail = (String) order.getMetadata().get("guestEmail");
        }

        // 使用 Order.guestCount 欄位（主要），若為空則回退到 metadata（向後相容）
        Integer guestCount = order.getGuestCount();
        if (guestCount == null && order.getMetadata() != null) {
            Object gc = order.getMetadata().get("guestCount");
            if (gc instanceof Integer) {
                guestCount = (Integer) gc;
            } else if (gc instanceof Number) {
                guestCount = ((Number) gc).intValue();
            }
        }

        return OrderDto.OrderResponse.builder()
                .id(order.getId())
                .tenantId(order.getTenantId())
                .userId(order.getUserId())
                .orderType(order.getOrderType().name())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .promoCode(order.getPromoCode())
                .discountAmount(order.getDiscountAmount())
                .currency(order.getCurrency())
                .shippingAddress(order.getShippingAddress())
                .shippingRecipientName(order.getShippingRecipientName())
                .shippingPhone(order.getShippingPhone())
                .notes(order.getNotes())
                .guestCount(guestCount)
                .guestName(guestName)
                .guestPhone(guestPhone)
                .guestEmail(guestEmail)
                .items(items)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderDto.OrderItemResponse toOrderItemResponse(OrderItem item) {
        Listing listing = item.getListing();
        String title = listing != null ? listing.getTitle() : "Unknown";

        return OrderDto.OrderItemResponse.builder()
                .id(item.getId())
                .listingId(item.getListingId())
                .listingTitle(title)
                .coverImageUrl(listing != null ? listing.getCoverImageUrl() : null)
                .skuId(item.getSkuId())
                .skuCode(item.getSku() != null ? item.getSku().getSkuCode() : null)
                .specName(item.getSku() != null ? item.getSku().getSpecName() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }

    private OrderDto.OrderListResponse toOrderListResponse(Order order) {
        return OrderDto.OrderListResponse.builder()
                .id(order.getId())
                .orderType(order.getOrderType().name())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .itemCount(order.getItems().size())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderDto.StateLogResponse toStateLogResponse(OrderStateLog log) {
        return OrderDto.StateLogResponse.builder()
                .id(log.getId())
                .orderId(log.getOrder().getId())
                .sequence(log.getSequence())
                .fromStatus(log.getFromStatus())
                .toStatus(log.getToStatus())
                .changedBy(log.getChangedBy())
                .reason(log.getReason())
                .createdAt(log.getCreatedAt())
                .build();
    }
}