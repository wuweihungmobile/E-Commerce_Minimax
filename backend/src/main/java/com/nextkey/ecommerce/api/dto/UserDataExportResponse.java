package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 會員資料匯出（PRD §1.5.1 會員資料 Export 權利，Sprint 94 AI-2428）。
 *
 * <p>涵蓋使用者本人在系統中的全部資料類別，供自助下載。已知限制：聊天訊息與客服工單訊息串的
 * 完整內容不包含在本次匯出範圍（僅列出對話/工單摘要），如需查看請透過既有訊息頁面。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDataExportResponse {

    private Profile profile;
    private List<OrderSummary> orders;
    private List<BookingSummary> bookings;
    private List<ReviewSummary> productReviews;
    private List<ReviewSummary> bookingReviews;
    private List<AddressSummary> addresses;
    private List<NotificationPreferenceSummary> notificationPreferences;
    private List<NotificationSummary> notifications;
    private List<SupportTicketSummary> supportTickets;
    private List<String> oauthProviders;
    private List<TenantMembershipSummary> tenantMemberships;
    private List<String> knownLimitations;
    private Instant exportedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Profile {
        private UUID userId;
        private String email;
        private String fullName;
        private String phone;
        private String role;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSummary {
        private UUID id;
        private String status;
        private BigDecimal totalAmount;
        private String shippingRecipientName;
        private String shippingPhone;
        private String shippingAddress;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingSummary {
        private UUID id;
        private String status;
        private LocalDate checkInDate;
        private LocalDate checkOutDate;
        private BigDecimal totalAmount;
        private String guestName;
        private String guestPhone;
        private String guestEmail;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewSummary {
        private UUID id;
        private Integer rating;
        private String title;
        private String content;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressSummary {
        private UUID id;
        private String recipientName;
        private String phone;
        private String city;
        private String district;
        private String addressLine;
        private boolean isDefault;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationPreferenceSummary {
        private String notificationType;
        private String channel;
        private boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSummary {
        private UUID id;
        private String notificationType;
        private String title;
        private String content;
        private boolean isRead;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupportTicketSummary {
        private UUID id;
        private String ticketNumber;
        private String subject;
        private String status;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantMembershipSummary {
        private UUID tenantId;
        private String storeRole;
        private Instant joinedAt;
    }
}
