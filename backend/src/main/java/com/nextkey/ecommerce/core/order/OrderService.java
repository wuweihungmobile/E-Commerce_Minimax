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
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeUsageRepository;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.order.OrderStateLog;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.core.user.AddressService;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.Address;
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
    private final PromoCodeRepository promoCodeRepository;
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
                .orderType(orderType)
                .status(Order.OrderStatus.CREATED)
                .shippingAddress(shippingAddress)
                .shippingRecipientName(shippingRecipientName)
                .shippingPhone(shippingPhone)
                .notes(request.getNotes())
                .currency("TWD")
                .items(new ArrayList<>())
                .build();

        // 計算總金額並建立訂單項目
        BigDecimal totalAmount = BigDecimal.ZERO;
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
            totalAmount = totalAmount.add(cartItem.getSubtotal());
        }

        // 計算運費並加入總金額
        BigDecimal shippingFee = shippingTemplateService.calculateFeeForTenant(tenantId, totalAmount);
        order.setShippingFee(shippingFee);

        // Sprint 100（PRD §9.5.1）：在校驗 totalAmount 之後、寫入訂單之前重新驗證促銷碼並套用折扣。
        // 促銷碼於加入購物車時已驗過一次，但購物車存活於 Redis TTL 期間，期間可能過期/停用/售罄，
        // 故此處必須重驗，不可沿用加入購物車當下的結果。
        // 只取券碼、不採信購物車算好的折扣：後者是 fallback-tolerant 的顯示用計算
        // （券失效時靜默回退原價），不可作為收款依據。
        PromoCode promo = applyPromoDiscount(order, cartService.getAppliedPromoCode(userId, tenantId),
                totalAmount, shippingFee, tenantId, userId);

        // Sprint 88（AI-2422）：建單前檢查並預扣庫存，避免超賣；庫存不足拋例外交易回滾，不留部分建立的訂單
        productInventoryService.reserveForOrder(order);

        order = orderRepository.save(order);

        commitPromoUsage(promo, order, userId, tenantId);

        // 記錄狀態日誌
        recordStateLog(order, null, Order.OrderStatus.CREATED.name(), userId, "Order created from cart");

        // 僅移除已處理的 PRODUCT 項目，保留購物車中其餘（如 ROOM）項目供另外結帳
        for (CartDto.CartItemResponse cartItem : productItems) {
            cartService.removeItem(userId, tenantId, cartItem.getCartItemKey());
        }

        log.info("Order created: orderId={}, userId={}, totalAmount={}, shippingFee={}, promoCode={}, discount={}",
                order.getId(), userId, order.getTotalAmount(), shippingFee,
                order.getPromoCode(), order.getDiscountAmount());
        return toOrderResponse(order);
    }

    /**
     * 套用促銷碼折扣並設定訂單金額欄位（PRD §9.5.1 步驟 4-5）。
     *
     * <p>抽為獨立方法而非內嵌於 {@code createOrderFromCart}：後者的 NPath 分支複雜度已逼近
     * checkstyle 上限，內嵌會使其超標。
     *
     * @return 通過驗證的促銷碼；{@code null} 表示未套用
     */
    private PromoCode applyPromoDiscount(final Order order, final String appliedPromoCode,
            final BigDecimal itemsTotal, final BigDecimal shippingFee,
            final UUID tenantId, final UUID userId) {
        PromoCode promo = resolveValidPromoForCheckout(appliedPromoCode, tenantId, userId);
        BigDecimal grossAmount = itemsTotal.add(shippingFee);
        BigDecimal discount = BigDecimal.ZERO;
        if (promo != null) {
            // PERCENTAGE / FIXED_AMOUNT 的折扣基數為商品小計，FREE_SHIPPING 則折抵運費（Sprint 101）；
            // 兩個基數都傳入，由 PromoService 依券別決定，與購物車顯示的折扣走同一套算法
            discount = promoService.computeDiscount(promo, itemsTotal, shippingFee);
            // PRD §9.5.1 步驟 5：折扣後金額不得為負
            if (discount.compareTo(grossAmount) > 0) {
                discount = grossAmount;
            }
            order.setPromoCode(promo.getCode());
        }
        order.setDiscountAmount(discount);
        order.setTotalAmount(grossAmount.subtract(discount));
        return promo;
    }

    /**
     * 訂單成立後佔用優惠券額度（總量 + 每人限用），並清除購物車上的促銷碼，
     * 避免同一張券被下一張訂單重複沿用。
     */
    private void commitPromoUsage(final PromoCode promo, final Order order,
            final UUID userId, final UUID tenantId) {
        if (promo == null) {
            return;
        }
        promoService.incrementUsageCount(promo);
        promoCodeUsageRepository.save(PromoCodeUsage.builder()
                .promoCodeId(promo.getId())
                .userId(userId)
                .orderId(order.getId())
                .build());
        cartService.removePromoCode(userId, tenantId);
    }

    /**
     * 訂單建立時的促銷碼驗證（PRD §9.5.1）。
     *
     * <p>依 PRD 明訂順序驗證：1. 存在且 ACTIVE → 2. 有效時間範圍 → 3. 使用上限，
     * 前三項沿用 {@link PromoService#validatePromoCode}（與購物車套用時同一套規則）；
     * 另加驗 {@code max_usage_per_user} 每人限用次數——該欄位自 V20 建表即存在，
     * 但在 Sprint 100 之前全庫沒有任何程式碼讀取它。
     *
     * <p>驗證失敗一律拒絕下單而非靜默改以原價成立：買家在購物車看到的是折扣後金額，
     * 若此處靜默回退原價，等同在買家不知情下多收款。
     *
     * @return 通過驗證的促銷碼；{@code null} 表示購物車未套用促銷碼
     */
    private PromoCode resolveValidPromoForCheckout(
            final String appliedPromoCode, final UUID tenantId, final UUID userId) {
        if (appliedPromoCode == null || appliedPromoCode.isBlank()) {
            return null;
        }
        String normalized = appliedPromoCode.trim().toUpperCase(java.util.Locale.ROOT);

        PromoCode promo = promoCodeRepository.findByCodeIgnoreCaseAndTenantId(normalized, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5007,
                        "Promo code no longer available at checkout: " + normalized));

        // 步驟 1-3：ACTIVE / 有效時間範圍 / 總量使用上限
        if (!promo.getIsActive()) {
            throw new BusinessException(ErrorCode.E_5007, "Promo code is inactive: " + normalized);
        }
        if (promo.isNotYetActive()) {
            throw new BusinessException(ErrorCode.E_5007, "Promo code is not yet active: " + normalized);
        }
        if (promo.isExpired()) {
            throw new BusinessException(ErrorCode.E_5008, "Promo code expired before checkout: " + normalized);
        }
        if (promo.isUsageLimitReached()) {
            throw new BusinessException(ErrorCode.E_5009, "Promo code usage limit reached: " + normalized);
        }

        // 每人限用次數（僅計 ACTIVE 的用券紀錄；訂單取消退還後的 REVOKED 不佔額度）
        if (promo.getMaxUsagePerUser() != null) {
            long usedByUser = promoCodeUsageRepository.countByPromoCodeIdAndUserIdAndStatus(
                    promo.getId(), userId, PromoCodeUsage.UsageStatus.ACTIVE);
            if (usedByUser >= promo.getMaxUsagePerUser()) {
                throw new BusinessException(ErrorCode.E_5009,
                        "Promo code per-user usage limit reached: " + normalized);
            }
        }

        return promo;
    }

    /**
     * 訂單取消時退還優惠券額度（PRD §2630「優惠券：若已使用促銷碼，則退還」）。
     *
     * <p>回補 {@code promo_codes.current_usage_count} 並將該訂單的用券紀錄標記為 REVOKED，
     * 使總量額度與每人限用額度雙雙釋放。若不做此退還，取消訂單會永久吃掉券的額度——
     * 那等於在修復本缺口的同時親手做出下一個同類缺口。
     */
    private void refundPromoUsage(final Order order) {
        if (order.getPromoCode() == null || order.getPromoCode().isBlank()) {
            return;
        }
        List<PromoCodeUsage> usages = promoCodeUsageRepository.findByOrderIdAndStatus(
                order.getId(), PromoCodeUsage.UsageStatus.ACTIVE);
        for (PromoCodeUsage usage : usages) {
            usage.setStatus(PromoCodeUsage.UsageStatus.REVOKED);
            usage.setRevokedAt(java.time.Instant.now());
            promoCodeUsageRepository.save(usage);

            promoCodeRepository.findById(usage.getPromoCodeId()).ifPresent(promo -> {
                int current = promo.getCurrentUsageCount() == null ? 0 : promo.getCurrentUsageCount();
                promo.setCurrentUsageCount(Math.max(0, current - 1));
                promoCodeRepository.save(promo);
            });
        }
        log.info("Promo usage refunded on cancellation: orderId={}, promoCode={}, revokedCount={}",
                order.getId(), order.getPromoCode(), usages.size());
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
        order.setStatus(newStatus);
        order = orderRepository.save(order);

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