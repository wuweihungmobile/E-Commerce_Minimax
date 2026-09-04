package com.nextkey.ecommerce.domain.model.promo;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用券紀錄（Sprint 100）。
 *
 * <p>支撐 {@link PromoCode#getMaxUsagePerUser()}（每人限用次數）——該欄位自 V20 建表起即存在，
 * 但在 Sprint 100 之前全庫沒有任何程式碼讀取它。
 *
 * <p>取消訂單退還額度時採「軟撤銷」（{@code status} 轉 {@code REVOKED}）而非實體刪除，
 * 比照 Sprint 98 {@code tenant_members.status} 的既有決策，保留可追溯的用券歷史。
 * 關聯欄位一律以 UUID 直接持有（比照 {@code SupportMessage.ticketId} 慣例），不建 {@code ManyToOne}。
 */
@Entity
@Table(name = "promo_code_usages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCodeUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "promo_code_id", nullable = false)
    private UUID promoCodeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 商品訂單來源。單一類型結帳時與 {@link #bookingId} 恰好一個非 null；
     * 合併結帳（Sprint 126，DEF-048 擴大範圍）時兩者皆非 null（見 V77 CHECK 約束放寬）。
     */
    @Column(name = "order_id")
    private UUID orderId;

    /**
     * 訂房來源（Sprint 124，DEF-047）。{@code order_id} 有 {@code REFERENCES orders(id)} FK，
     * 塞入 booking id 會違反約束，故另開一欄而非沿用同一欄位存兩種語意不同的 ID。
     */
    @Column(name = "booking_id")
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UsageStatus status = UsageStatus.ACTIVE;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * 合併結帳中 Order 側已取消的時間點（Sprint 126，V77）。僅 {@link #orderId}／
     * {@link #bookingId} 皆非 null 的合併結帳用券紀錄會用到；單一類型用券紀錄恆為 null，
     * 其取消行為維持既有的「立即 REVOKED＋釋放額度」，不受此欄位影響。
     */
    @Column(name = "order_released_at")
    private Instant orderReleasedAt;

    /** 合併結帳中 Booking 側已取消的時間點（Sprint 126，V77）。語意對稱 {@link #orderReleasedAt}。 */
    @Column(name = "booking_released_at")
    private Instant bookingReleasedAt;

    @PrePersist
    protected void onCreate() {
        if (usedAt == null) {
            usedAt = Instant.now();
        }
        if (status == null) {
            status = UsageStatus.ACTIVE;
        }
    }

    /**
     * {@code ACTIVE}=佔用額度；{@code REVOKED}=訂單取消後已退還額度
     * （PRD §2630「優惠券：若已使用促銷碼，則退還」），每人限用計數時僅計 {@code ACTIVE}。
     */
    public enum UsageStatus {
        ACTIVE,
        REVOKED
    }
}
