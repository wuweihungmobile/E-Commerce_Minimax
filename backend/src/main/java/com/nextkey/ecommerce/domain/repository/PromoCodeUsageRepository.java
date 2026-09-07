package com.nextkey.ecommerce.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.promo.PromoCodeUsage;
import com.nextkey.ecommerce.domain.model.promo.PromoCodeUsage.UsageStatus;

/**
 * 用券紀錄 Repository（Sprint 100）：支撐每人限用次數檢查與取消訂單時的額度退還。
 */
@Repository
public interface PromoCodeUsageRepository extends JpaRepository<PromoCodeUsage, UUID> {

    /** 該使用者對該促銷碼目前佔用中的用券次數（僅計 {@code ACTIVE}）。 */
    long countByPromoCodeIdAndUserIdAndStatus(UUID promoCodeId, UUID userId, UsageStatus status);

    /** 取消訂單時反查該訂單佔用中的用券紀錄，用於退還額度。 */
    List<PromoCodeUsage> findByOrderIdAndStatus(UUID orderId, UsageStatus status);

    /** 取消預訂時反查該預訂佔用中的用券紀錄，用於退還額度（Sprint 124，DEF-047）。 */
    List<PromoCodeUsage> findByBookingIdAndStatus(UUID bookingId, UsageStatus status);

    /**
     * 併發防護（DEF-136）：條件式狀態轉換（{@code WHERE status = expectedStatus}），取代
     * PromoService.revokeAndReleaseQuota 原本「無條件 setStatus(REVOKED)→save」的作法。
     * 回傳受影響列數：1 代表本次是真正把此筆用券紀錄從 ACTIVE 轉為 REVOKED 的呼叫者，才可以
     * 繼續執行 releaseUsageQuota；0 代表已被另一併發呼叫搶先撤銷，不可再退還一次額度
     * （否則同一張促銷碼的已用額度會被多退一次）。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE PromoCodeUsage u SET u.status = :newStatus, u.revokedAt = :revokedAt "
            + "WHERE u.id = :id AND u.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") UUID id, @Param("expectedStatus") UsageStatus expectedStatus,
            @Param("newStatus") UsageStatus newStatus, @Param("revokedAt") Instant revokedAt);
}
