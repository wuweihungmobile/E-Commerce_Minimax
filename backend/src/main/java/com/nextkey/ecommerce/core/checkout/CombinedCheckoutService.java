package com.nextkey.ecommerce.core.checkout;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.CheckoutDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.booking.BookingService;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.logistics.ShippingTemplateService;
import com.nextkey.ecommerce.core.order.OrderService;
import com.nextkey.ecommerce.core.promo.PromoService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.promo.PromoCodeUsage;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeUsageRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 合併結帳服務（Sprint 126，DEF-048 擴大範圍）。
 *
 * <p>背景：購物車混合 PRODUCT+ROOM 時，原本使用者只能分兩次結帳（先結 PRODUCT 走
 * {@code OrderService.createOrderFromCart}，再結 ROOM 走 {@code BookingService.createBooking}），
 * 兩條路徑各自獨立驗證/套用/佔用促銷碼，購物車顯示的折扣（整車混算）因此與實收（分兩筆各自結帳）
 * 不一致。本服務讓使用者一次動作同時結清兩者，並讓單一張優惠券的折扣合理分攤到兩側。
 *
 * <p>Order 與 Booking 雖各自獨立 Service，但同屬一個 PostgreSQL 資料庫：本方法本身包在單一
 * {@code @Transactional}（預設傳播模式 REQUIRED）內依序呼叫兩邊既有建立邏輯（
 * {@link OrderService#buildProductOrder}／{@link BookingService#buildBookingCore}），Spring 讓
 * 兩者共用同一個 DB 交易，任一邊失敗兩邊自動回滾——不需要 saga／補償交易。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CombinedCheckoutService {

    private final RedisCartService cartService;
    private final OrderService orderService;
    private final BookingService bookingService;
    private final PromoService promoService;
    private final PromoCodeUsageRepository promoCodeUsageRepository;
    private final ListingRepository listingRepository;
    private final ShippingTemplateService shippingTemplateService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    /**
     * 合併結帳：購物車須同時有 PRODUCT 與 ROOM 項目（否則應走既有的 {@code POST /v2/orders}／
     * {@code POST /v2/bookings} 單一類型結帳）。合併結帳一次只結第一筆 ROOM 項目——與既有購物車
     * 「訂房一次只訂一間」的既定行為一致（見 {@code checkout/page.tsx} 既有邏輯，非本方法獨創）。
     */
    @Transactional
    public CheckoutDto.MixedCheckoutResponse checkoutMixedCart(final CheckoutDto.MixedCheckoutRequest request) {
        UUID userId = TenantContext.getCurrentUser();
        UUID tenantId = TenantContext.getCurrentTenant();

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        CartDto.CartResponse cart = cartService.getCart(userId, tenantId);
        List<CartDto.CartItemResponse> productItems = cart.getItems().stream()
                .filter(i -> "PRODUCT".equals(i.getListingType()))
                .collect(Collectors.toList());
        List<CartDto.CartItemResponse> roomItems = cart.getItems().stream()
                .filter(i -> "ROOM".equals(i.getListingType()))
                .collect(Collectors.toList());
        if (productItems.isEmpty() || roomItems.isEmpty()) {
            throw new BusinessException(ErrorCode.E_5004,
                    "Mixed checkout requires both PRODUCT and ROOM items in cart");
        }
        CartDto.CartItemResponse roomItem = roomItems.get(0);

        BookingDto.CreateRequest bookingRequest = BookingDto.CreateRequest.builder()
                .roomListingId(roomItem.getListingId())
                .checkInDate(roomItem.getStartDate())
                .checkOutDate(roomItem.getEndDate())
                .guestCount(request.getGuestCount())
                .guestName(request.getGuestName())
                .guestPhone(request.getGuestPhone())
                .guestEmail(request.getGuestEmail())
                .specialRequests(request.getSpecialRequests())
                .promoCode(request.getPromoCode())
                .build();
        Listing roomListing = listingRepository.findById(roomItem.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Room not found"));
        Room room = bookingService.resolveBookableRoom(roomListing, bookingRequest);

        // 促銷碼只解析一次，兩側共用同一張券——不使用 OrderService/BookingService 各自的解析路徑
        // （前者讀購物車 Redis 已套用的碼、後者讀 request 明確帶入的碼），合併結帳頁面只有一個
        // 看得見的促銷碼輸入框，行為對使用者透明。
        PromoCode promo = promoService.resolveValidPromoForCheckout(request.getPromoCode(), tenantId, userId);

        BigDecimal productSubtotal = productItems.stream()
                .map(CartDto.CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingFee = shippingTemplateService.calculateFeeForTenant(tenantId, productSubtotal);
        BigDecimal roomGrossAmount = bookingService.calculateTotalAmount(
                roomListing.getId(), bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate());

        // 合併後的總額／總折扣：直接重用 PromoService.computeCappedDiscount，不重新發明折扣規則
        // （minPurchaseAmount／maxDiscountAmount 因此天然是「合併後」的門檻與天花板）。
        BigDecimal combinedItemsTotal = productSubtotal.add(roomGrossAmount);
        BigDecimal totalDiscount = promoService.computeCappedDiscount(promo, combinedItemsTotal, shippingFee);
        PromoService.DiscountAllocation allocation =
                promoService.allocateDiscount(promo, totalDiscount, productSubtotal, roomGrossAmount);

        OrderDto.CreateRequest orderRequest = OrderDto.CreateRequest.builder()
                .orderType("PRODUCT")
                .addressId(request.getAddressId())
                .shippingAddress(request.getShippingAddress())
                .shippingRecipientName(request.getShippingRecipientName())
                .shippingPhone(request.getShippingPhone())
                .notes(request.getNotes())
                .build();

        OrderDto.OrderResponse orderResponse = orderService.buildProductOrder(
                tenant, user, orderRequest, productItems, promo, allocation.productDiscount(), shippingFee, userId);

        BookingDto.BookingResponse bookingResponse = bookingService.buildBookingCore(
                roomListing, room, bookingRequest, userId, tenantId,
                roomGrossAmount, promo, allocation.roomDiscount());

        // 兩側都成功建立後才佔用一次額度（使用者拍板：合併結帳用一張券算 1 次），寫入一筆同時
        // 關聯 order_id 與 booking_id 的用券紀錄（V77 CHECK 約束已放寬允許兩者皆非 null）。
        commitCombinedPromoUsage(promo, orderResponse.getId(), bookingResponse.getId(), userId, tenantId);

        // 全部成功才清購物車已結清的項目（Redis 操作非 JPA 交易的一部分，必須放在最後才做，
        // 避免任何前面步驟失敗回滾時，購物車項目已被誤刪）。
        for (CartDto.CartItemResponse item : productItems) {
            cartService.removeItem(userId, tenantId, item.getCartItemKey());
        }
        cartService.removeItem(userId, tenantId, roomItem.getCartItemKey());

        log.info("Mixed checkout completed: orderId={}, bookingId={}, userId={}, promoCode={}, "
                        + "productDiscount={}, roomDiscount={}",
                orderResponse.getId(), bookingResponse.getId(), userId,
                promo != null ? promo.getCode() : null, allocation.productDiscount(), allocation.roomDiscount());

        return CheckoutDto.MixedCheckoutResponse.builder()
                .order(orderResponse)
                .booking(bookingResponse)
                .promoCode(promo != null ? promo.getCode() : null)
                .totalDiscountAmount(allocation.productDiscount().add(allocation.roomDiscount()))
                .build();
    }

    /**
     * 合併結帳專用的額度佔用：與 {@code OrderService.commitPromoUsage}／
     * {@code BookingService.commitPromoUsage} 佔用邏輯相同（原子 UPDATE 取得總量額度 → 鎖內重查
     * 每人限用），差異在於寫入的 {@code PromoCodeUsage} 同時關聯 {@code orderId} 與
     * {@code bookingId}——這筆「一次用券」之後取消其中一側時，{@code PromoService.releaseOrderSide}／
     * {@code releaseBookingSide} 會依使用者拍板「兩側都取消才退還」的規則各自評估。
     */
    private void commitCombinedPromoUsage(final PromoCode promo, final UUID orderId, final UUID bookingId,
            final UUID userId, final UUID tenantId) {
        if (promo == null) {
            return;
        }
        if (!promoService.tryConsumeUsageQuota(promo)) {
            throw new BusinessException(ErrorCode.E_5009,
                    "Promo code sold out during checkout: " + promo.getCode());
        }
        if (promoService.perUserLimitReached(promo, userId)) {
            throw new BusinessException(ErrorCode.E_5009,
                    "Promo code per-user usage limit reached: " + promo.getCode());
        }
        promoCodeUsageRepository.save(PromoCodeUsage.builder()
                .promoCodeId(promo.getId())
                .userId(userId)
                .orderId(orderId)
                .bookingId(bookingId)
                .build());
        cartService.removePromoCode(userId, tenantId);
    }
}
