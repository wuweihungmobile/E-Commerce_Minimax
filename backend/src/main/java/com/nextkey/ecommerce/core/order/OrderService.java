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
import com.nextkey.ecommerce.core.audit.AuditService;
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
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.util.PageableUtils;

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
    private final AuditService auditService;

    /**
     * 「沒有真正租戶」的預設佔位租戶 ID（見 {@code TenantContextFilter.resolveEffectiveTenantId}）。
     * {@link #checkOrderTenantAuthorization} 的 same-tenant 分支需明確排除它，見該方法 Javadoc。
     */
    private static final UUID SYSTEM_TENANT_UUID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

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

        Listing.ListingType orderType;
        try {
            orderType = Listing.ListingType.valueOf(request.getOrderType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E_3001, "Invalid order type: " + request.getOrderType());
        }

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

            // DEF-237 縱深防禦：即使 RedisCartService.addItem 已在寫入購物車前驗證 skuId 屬於
            // 同一個 listingId，Redis 購物車項目 TTL 長達 30 天，修復前已寫入的舊資料（或未來任何
            // 繞過購物車直接建構 CartItemResponse 的呼叫路徑）仍可能帶著不屬於此 listing 的 skuId
            // 流到這裡。比照既有「skuId 找不到」的容錯語意，不屬於此 listing 的 SKU 一律視為未選規格。
            ProductSku sku = null;
            if (cartItem.getSkuId() != null) {
                ProductSku candidate = productSkuRepository.findById(cartItem.getSkuId()).orElse(null);
                if (candidate != null && listing.getId().equals(candidate.getProductListingId())) {
                    sku = candidate;
                }
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
        auditService.record("ORDER_CREATED", "ORDER", order.getId(), tenant.getId(),
                null, "totalAmount=" + order.getTotalAmount(), null, userId);

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
        PageRequest pageRequest = PageableUtils.of(page, size, 100, sort);

        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
        return orders.map(this::toOrderListResponse);
    }

    /**
     * 取得訂單詳情
     */
    @Transactional(readOnly = true)
    public OrderDto.OrderResponse getOrder(UUID orderId) {
        Order order = findOrderById(orderId);

        // DEF-018：owner-or-admin 基礎防護；Sprint 151（DEF-188）補上 same-tenant 分支，
        // 比照 checkOrderTenantAuthorization 既有的三選一放行條件，消除「賣家能透過
        // PATCH .../status 寫入、卻無法用 GET 讀取同一筆訂單」的讀寫授權不對稱。
        checkOrderTenantAuthorization(order);

        return toOrderResponse(order);
    }

    /**
     * 取得當前租戶（賣家/店主）收到的訂單列表（Sprint 151，DEF-188）。
     *
     * <p>比照 {@link #getUserOrders} 的分頁/排序處理，額外支援選填的狀態篩選——
     * 有 status 時走 {@code findByTenantIdAndStatus}，否則走既有的
     * {@code findByTenantIdOrderByCreatedAtDesc}（兩者皆為既有 repository 方法，未新增查詢）。
     * 無租戶內容（例如買家帳號呼叫本方法）時 tenantId 為 null，查詢結果自然為空頁，不特別拋錯。
     */
    @Transactional(readOnly = true)
    public Page<OrderDto.OrderListResponse> getTenantOrders(int page, int size, String sortBy, String sortDir,
            String status) {
        UUID tenantId = TenantContext.getCurrentTenant();
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        PageRequest pageRequest = PageableUtils.of(page, size, 100, sort);

        Page<Order> orders;
        if (status != null && !status.isBlank()) {
            Order.OrderStatus orderStatus;
            try {
                orderStatus = Order.OrderStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.E_5001, "Invalid order status: " + status);
            }
            orders = orderRepository.findByTenantIdAndStatus(tenantId, orderStatus, pageRequest);
        } else {
            orders = orderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageRequest);
        }
        return orders.map(this::toOrderListResponse);
    }

    /**
     * DEF-245：{@code PAID}/{@code REFUNDED} 屬於「聲稱金流已實際發生」的狀態，本方法是
     * {@code PATCH /v2/orders/{orderId}/status} 的唯一實作，任何持有 {@code order:update}
     * 權限者（SELLER/STORE_OWNER/ADMIN，見 RolePermissionMapping）皆可呼叫——先前無任何限制，
     * 可在完全沒有真實付款/退款紀錄的情況下，直接偽造訂單已付款或已退款，用於灌水業績/結算，
     * 或向買家謊報退款已完成。修法：一律封鎖這兩個狀態透過本端點直接指定（不對任何角色例外，
     * 含 ADMIN/SUPER_ADMIN——經使用者拍板採最嚴格方案），僅保留給受信任的內部付款子系統
     * （{@link com.nextkey.ecommerce.core.payment.PaymentService}）以 {@code systemTriggered=true}
     * 呼叫下方 4 參數版本。
     */
    private static final java.util.Set<String> PAYMENT_SYSTEM_ONLY_STATUSES = java.util.Set.of("PAID", "REFUNDED");

    /**
     * 更新訂單狀態（外部呼叫入口，例如 {@code PATCH /v2/orders/{orderId}/status}）。
     *
     * <p>此重載方法與下方 4 參數版本各自標註 {@code @CacheEvict}/{@code @Transactional}
     * （而非只標在其中一個、內部靠 {@code this.} 呼叫另一個）：Spring AOP 代理只會攔截「從物件
     * 外部進來」的呼叫，同一物件內部以 {@code this.} 互呼會繞過代理，讓被呼叫方法自身的註解失效
     * （self-invocation 陷阱）。兩個簽章都是會被外部 bean 直接呼叫的真實入口
     * （{@link com.nextkey.ecommerce.api.controller.OrderController} 呼叫 3 參數版本、
     * {@link com.nextkey.ecommerce.core.payment.PaymentService} 呼叫 4 參數版本），因此都必須
     * 各自完整標註，才能確保兩條路徑的快取清除/交易邊界都真的生效（`RedisCacheConfigTest`
     * 以反射驗證 3 參數版本的 `@CacheEvict` 存在，即為此陷阱的既有防護測試）。
     */
    @CacheEvict(value = "dashboardStats", allEntries = true)
    @Transactional
    public OrderDto.OrderResponse updateOrderStatus(UUID orderId, String targetStatus, String reason) {
        return updateOrderStatus(orderId, targetStatus, reason, false);
    }

    /**
     * 更新訂單狀態。{@code systemTriggered=true} 僅供
     * {@link com.nextkey.ecommerce.core.payment.PaymentService} 等受信任的內部服務在完成真實
     * 付款/退款後呼叫，繞過 DEF-245 對 {@code PAID}/{@code REFUNDED} 的直接指定限制。
     */
    @CacheEvict(value = "dashboardStats", allEntries = true)
    @Transactional
    public OrderDto.OrderResponse updateOrderStatus(
            UUID orderId, String targetStatus, String reason, boolean systemTriggered) {
        UUID userId = TenantContext.getCurrentUser();
        Order order = findOrderById(orderId);

        // DEF-024：修復跨租戶 IDOR——訂單擁有者本人（買家自助付款流程）或本租戶
        // （賣家/店主管理自己租戶訂單，比照 LogisticsService.checkOrderTenant）或 admin 放行，
        // 置於狀態機檢查之前避免向未授權者洩漏訂單狀態
        checkOrderTenantAuthorization(order);

        // DEF-245：見上方 PAYMENT_SYSTEM_ONLY_STATUSES 說明。
        if (!systemTriggered && PAYMENT_SYSTEM_ONLY_STATUSES.contains(targetStatus)) {
            throw new BusinessException(ErrorCode.E_5001,
                    targetStatus + " status can only be set by the payment system, not directly via this endpoint");
        }

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
        auditService.record("ORDER_STATUS_UPDATED", "ORDER", order.getId(), order.getTenantId(),
                currentStatus, targetStatus, reason, userId);
        return toOrderResponse(order);
    }

    /**
     * 訂單讀取／狀態更新的擁有權/租戶檢查（DEF-024：修復 updateOrderStatus 完全無檢查的跨租戶
     * IDOR；Sprint 151／DEF-188：getOrder 比照補上 same-tenant 分支，消除「賣家能寫入自己讀不到
     * 的資料」的讀寫授權不對稱——此前 getOrder 只有 owner-or-admin，賣家能透過 PATCH .../status
     * 操作同租戶訂單，卻無法用 GET 讀取同一筆訂單）。
     *
     * <p>本方法有三種合法呼叫情境：(1) 買家透過付款流程觸發（CREATED→PAID），呼叫端已由
     * {@code PaymentService.checkOrderPaymentOwnership} 限定本人；(2) 賣家/店主透過
     * {@code PATCH /v2/orders/{orderId}/status} 或 {@code GET /v2/orders/{orderId}} 直接呼叫
     * （Controller 層分別以 order:update／order:read 權限把關，皆為 SELLER/STORE_OWNER/
     * ADMIN/SUPER_ADMIN 持有），用於出貨等租戶內訂單管理；(3) 買家查看自己的訂單詳情
     * （比照 cancelOrder/getOrderStateLogs 的 DEF-018 模式）。
     *
     * <p>因此採「訂單擁有者本人 or 本租戶（比照 LogisticsService.checkOrderTenant 的 DEF-019
     * 模式）or admin」三者其一放行，越權回 403/E_1007。
     *
     * <p>🔴 Sprint 151（DEF-188 修復過程中查證發現的既有漏洞，隨本次改動一併修復）：
     * {@code TenantContextFilter.resolveEffectiveTenantId} 對「使用者未歸屬任何實際租戶」
     * （一般 BUYER 帳號、或尚未通過審核的 SELLER）一律 fallback 到同一個常數
     * {@link AppConstants#SYSTEM_TENANT_ID}——也就是說任兩個未加入店鋪的一般使用者，
     * {@code TenantContext.getCurrentTenant()} 會是**同一個值**。若讓 same-tenant 分支對這個
     * 系統租戶也放行，等於任一買家都能讀取/操作任一其他買家掛在系統租戶下的訂單（例如未指定
     * 店鋪的 ROOM 訂單），形成本方法原本要防堵的同一種跨使用者 IDOR。此漏洞自 DEF-024（Sprint 70）
     * 起即存在於 {@code updateOrderStatus}，因先前沒有測試以「兩個一般買家帳號」的組合驗證
     * PATCH 路徑而未被發現；本次因 {@code getOrder} 開始共用此方法，觸發既有的
     * {@code BuyerOrderJourneyE2ETest.otherBuyerCannotGetOrder}（DEF-018）紅燈而被揭露。
     * 修法：same-tenant 分支明確排除系統租戶——系統租戶是「沒有真正租戶」的預設佔位值，
     * 不是有效的多租戶隔離邊界。
     */
    private void checkOrderTenantAuthorization(final Order order) {
        UUID userId = TenantContext.getCurrentUser();
        UUID tenantId = TenantContext.getCurrentTenant();
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && (
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ||
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
        );
        boolean isOwner = userId != null && userId.equals(order.getUserId());
        boolean isSameTenant = tenantId != null && tenantId.equals(order.getTenantId())
                && !tenantId.equals(SYSTEM_TENANT_UUID);

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
        // 併發防護（DEF-124）：先原子搶占「目前狀態→CANCELLED」這個轉換，只有搶到的一方才
        // 繼續往下釋放預扣庫存／退還優惠券額度。這兩個下游操作本身雖是原子的相對量增減
        // （releaseReservation 原生 UPDATE、優惠券額度遞增），但若 cancelOrder 本身被併發
        // 呼叫兩次都通過上面的舊快照狀態檢查，仍會各自呼叫一次，造成庫存被重複釋放（幻影
        // 庫存）、優惠券額度被重複退還（超發）。
        if (orderRepository.updateStatusIfCurrent(order.getId(), order.getStatus(), Order.OrderStatus.CANCELLED) == 0) {
            throw new BusinessException(ErrorCode.E_5002, "Order cannot be cancelled in current status");
        }
        order.setStatus(Order.OrderStatus.CANCELLED);

        // 記錄狀態日誌
        recordStateLog(order, currentStatus, Order.OrderStatus.CANCELLED.name(), userId, reason);
        auditService.record("ORDER_CANCELLED", "ORDER", order.getId(), order.getTenantId(),
                currentStatus, Order.OrderStatus.CANCELLED.name(), reason, userId);

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
     *
     * <p>Sprint 153（Sprint 151 §6 #9 追加項目）：比照 {@link #getOrder} 在 Sprint 151（DEF-188）
     * 補上 same-tenant 分支的理由，本方法原本只有 owner-or-admin，改用
     * {@link #checkOrderTenantAuthorization} 統一三選一放行條件——賣家/店主既然已能透過
     * {@code GET /v2/orders/{orderId}} 讀取同租戶訂單詳情、透過 {@code PATCH .../status} 寫入狀態，
     * 沒有理由唯獨看不到同一筆訂單的狀態變更時間軸，此前的差異純屬 Sprint 151 刻意劃定的範圍邊界
     * （見 SPRINT_151_PLAN.md §4），非授權設計上的刻意限制。
     */
    @Transactional(readOnly = true)
    public List<OrderDto.StateLogResponse> getOrderStateLogs(UUID orderId) {
        Order order = findOrderById(orderId);

        checkOrderTenantAuthorization(order);

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
                .shippingRecipientName(order.getShippingRecipientName())
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