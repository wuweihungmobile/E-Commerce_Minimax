package com.nextkey.ecommerce.core.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.UserDataExportResponse;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.AddressRepository;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.NotificationRepository;
import com.nextkey.ecommerce.domain.repository.OAuthAccountRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.UserNotificationPreferenceRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.review.BookingReviewRepository;
import com.nextkey.ecommerce.domain.repository.support.SupportTicketRepository;
import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 會員自助資料權利（PRD §1.5.1，Sprint 94 AI-2428）：資料 Export + 帳戶刪除（被遺忘權）。
 *
 * <p>業務決策（使用者已拍板，詳見 SPRINT_94_PLAN.md）：
 * <ul>
 *   <li>帳戶刪除僅開放 {@link User.UserRole#BUYER}；StoreOwner/Staff/Admin/SUPER_ADMIN/CFO
 *       需另案處理（StoreOwner 被移除會讓商店孤兒化，且系統既有邏輯本就禁止移除 StoreOwner；
 *       管理員角色也無防止刪除最後一位管理者的機制）。</li>
 *   <li>有未結案（非終態）訂單或訂房時封鎖刪除請求，避免留下無法追蹤的孤兒交易。</li>
 *   <li>訂單/訂房內「下單當下」的收件人姓名電話快照（{@code Order.shippingRecipientName}/
 *       {@code shippingPhone}、{@code Booking.guestName}/{@code guestPhone}/{@code guestEmail}）
 *       保留不動，視為交易歷史記錄的一部分，不在匿名化範圍內（已知限制）。</li>
 * </ul>
 *
 * <p>匿名化沿用既有的 {@code User.status} 慣例（{@link com.nextkey.ecommerce.core.auth.AuthService}
 * 的登入/refresh token 檢查已依賴 {@code "ACTIVE"} 字串，改為 {@code "DELETED"} 後會自動被擋下，
 * 不需額外修改認證邏輯）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPrivacyService {

    private static final String DELETED_STATUS = "DELETED";
    private static final String ANONYMIZED_EMAIL_DOMAIN = "@anonymized.local";
    private static final String ANONYMIZED_FULL_NAME = "已刪除的使用者";

    private static final List<Order.OrderStatus> ORDER_TERMINAL_STATUSES = List.of(
            Order.OrderStatus.COMPLETED, Order.OrderStatus.CANCELLED, Order.OrderStatus.REFUNDED);

    private static final List<Booking.BookingStatus> BOOKING_TERMINAL_STATUSES = List.of(
            Booking.BookingStatus.COMPLETED, Booking.BookingStatus.CANCELLED);

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final com.nextkey.ecommerce.domain.repository.ReviewRepository reviewRepository;
    private final BookingReviewRepository bookingReviewRepository;
    private final AddressRepository addressRepository;
    private final UserNotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationRepository notificationRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public UserDataExportResponse exportMyData() {
        UUID userId = TenantContext.getCurrentUser();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));

        return UserDataExportResponse.builder()
                .profile(UserDataExportResponse.Profile.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .phone(user.getPhone())
                        .role(user.getRole().name())
                        .createdAt(user.getCreatedAt())
                        .build())
                .orders(orderRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged())
                        .getContent().stream().map(this::toOrderSummary).collect(Collectors.toList()))
                .bookings(bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged())
                        .getContent().stream().map(this::toBookingSummary).collect(Collectors.toList()))
                .productReviews(reviewRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged())
                        .getContent().stream()
                        .map(r -> new UserDataExportResponse.ReviewSummary(
                                r.getId(), r.getRating(), r.getTitle(), r.getContent(), r.getCreatedAt()))
                        .collect(Collectors.toList()))
                .bookingReviews(bookingReviewRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged())
                        .getContent().stream()
                        .map(r -> new UserDataExportResponse.ReviewSummary(
                                r.getId(), r.getRating(), r.getTitle(), r.getContent(), r.getCreatedAt()))
                        .collect(Collectors.toList()))
                .addresses(addressRepository.findByUserIdOrderByIsDefaultDescUpdatedAtDesc(userId).stream()
                        .map(a -> new UserDataExportResponse.AddressSummary(
                                a.getId(), a.getRecipientName(), a.getPhone(), a.getCity(),
                                a.getDistrict(), a.getAddressLine(), a.isDefault()))
                        .collect(Collectors.toList()))
                .notificationPreferences(notificationPreferenceRepository.findByUserId(userId).stream()
                        .map(p -> new UserDataExportResponse.NotificationPreferenceSummary(
                                p.getNotificationType().name(), p.getChannel().name(), p.isEnabled()))
                        .collect(Collectors.toList()))
                .notifications(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged())
                        .getContent().stream()
                        .map(n -> new UserDataExportResponse.NotificationSummary(
                                n.getId(), n.getNotificationType().name(), n.getTitle(), n.getContent(),
                                Boolean.TRUE.equals(n.getIsRead()), n.getCreatedAt()))
                        .collect(Collectors.toList()))
                .supportTickets(supportTicketRepository.findByCustomerIdOrderByCreatedAtDesc(userId, Pageable.unpaged())
                        .getContent().stream()
                        .map(t -> new UserDataExportResponse.SupportTicketSummary(
                                t.getId(), t.getTicketNumber(), t.getSubject(), t.getStatus().name(), t.getCreatedAt()))
                        .collect(Collectors.toList()))
                .oauthProviders(oAuthAccountRepository.findByUserId(userId).stream()
                        .map(com.nextkey.ecommerce.domain.model.user.OAuthAccount::getProvider)
                        .collect(Collectors.toList()))
                .tenantMemberships(tenantMemberRepository.findByUserId(userId).stream()
                        .map(m -> new UserDataExportResponse.TenantMembershipSummary(
                                m.getTenantId(), m.getStoreRole().name(), m.getJoinedAt()))
                        .collect(Collectors.toList()))
                .knownLimitations(List.of(
                        "聊天訊息與客服工單訊息串的完整內容未包含在本次匯出範圍，僅列出對話/工單摘要，"
                                + "如需查看請透過既有訊息頁面"))
                .exportedAt(Instant.now())
                .build();
    }

    @Transactional
    public void deleteMyAccount() {
        UUID userId = TenantContext.getCurrentUser();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));

        if (user.getRole() != User.UserRole.BUYER) {
            throw new BusinessException(ErrorCode.E_1009);
        }

        boolean hasActiveOrders = orderRepository.existsByUserIdAndStatusNotIn(userId, ORDER_TERMINAL_STATUSES);
        boolean hasActiveBookings = bookingRepository.existsByUserIdAndStatusNotIn(userId, BOOKING_TERMINAL_STATUSES);
        if (hasActiveOrders || hasActiveBookings) {
            throw new BusinessException(ErrorCode.E_1010);
        }

        anonymize(user);
        userRepository.save(user);

        addressRepository.deleteByUserId(userId);
        oAuthAccountRepository.deleteByUserId(userId);
        refreshTokenService.blacklistAllRefreshTokens(userId);

        log.info("Account anonymized (right to be forgotten): {}", userId);
    }

    private void anonymize(final User user) {
        user.setEmail("deleted-" + UUID.randomUUID() + ANONYMIZED_EMAIL_DOMAIN);
        user.setPasswordHash(null);
        user.setFullName(ANONYMIZED_FULL_NAME);
        user.setPhone(null);
        user.setAvatarUrl(null);
        user.setKycIdNumberEncrypted(null);
        user.setKycIdCardFrontUrl(null);
        user.setKycIdCardBackUrl(null);
        user.setMetadata(null);
        user.setStatus(DELETED_STATUS);
    }

    private UserDataExportResponse.OrderSummary toOrderSummary(final Order order) {
        return UserDataExportResponse.OrderSummary.builder()
                .id(order.getId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .shippingRecipientName(order.getShippingRecipientName())
                .shippingPhone(order.getShippingPhone())
                .shippingAddress(order.getShippingAddress())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private UserDataExportResponse.BookingSummary toBookingSummary(final Booking booking) {
        return UserDataExportResponse.BookingSummary.builder()
                .id(booking.getId())
                .status(booking.getStatus().name())
                .checkInDate(booking.getCheckInDate())
                .checkOutDate(booking.getCheckOutDate())
                .totalAmount(booking.getTotalAmount())
                .guestName(booking.getGuestName())
                .guestPhone(booking.getGuestPhone())
                .guestEmail(booking.getGuestEmail())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
