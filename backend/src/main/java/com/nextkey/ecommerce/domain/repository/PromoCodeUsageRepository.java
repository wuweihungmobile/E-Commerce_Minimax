package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
