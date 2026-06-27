package com.nextkey.ecommerce.core.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.logistics.ShippingTemplateService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.order.OrderStateLog;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
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

    /**
     * 建立訂單（從購物車或直接預訂）
     * ROOM 類型訂單可以直接傳入預訂資料，不需要購物車
     * PRODUCT 類型訂單需要購物車中有商品
     */
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

        // 建立訂單
        Order order = Order.builder()
                .tenant(tenant)
                .user(user)
                .orderType(orderType)
                .status(Order.OrderStatus.CREATED)
                .shippingAddress(request.getShippingAddress())
                .shippingRecipientName(request.getShippingRecipientName())
                .shippingPhone(request.getShippingPhone())
                .notes(request.getNotes())
                .currency("TWD")
                .items(new ArrayList<>())
                .build();

        // 計算總金額並建立訂單項目
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartDto.CartItemResponse cartItem : cart.getItems()) {
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
        order.setTotalAmount(totalAmount.add(shippingFee));

        order = orderRepository.save(order);

        // 記錄狀態日誌
        recordStateLog(order, null, Order.OrderStatus.CREATED.name(), userId, "Order created from cart");

        // 清除購物車
        cartService.clearCart(userId, tenantId);

        log.info("Order created: orderId={}, userId={}, totalAmount={}, shippingFee={}",
                order.getId(), userId, order.getTotalAmount(), shippingFee);
        return toOrderResponse(order);
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
        Order order = findOrderById(orderId);
        return toOrderResponse(order);
    }

    /**
     * 更新訂單狀態
     */
    @Transactional
    public OrderDto.OrderResponse updateOrderStatus(UUID orderId, String targetStatus, String reason) {
        UUID userId = TenantContext.getCurrentUser();
        Order order = findOrderById(orderId);

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
     * 取消訂單
     * 注意：前端應确保使用者具有適當權限。此方法允許訂單擁有者取消自己的訂單
     */
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