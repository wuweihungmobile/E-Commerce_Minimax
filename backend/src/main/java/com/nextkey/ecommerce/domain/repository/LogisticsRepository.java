package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.logistics.Logistics;

@Repository
public interface LogisticsRepository extends JpaRepository<Logistics, UUID> {

    List<Logistics> findByOrderId(UUID orderId);

    Optional<Logistics> findByTrackingNumber(String trackingNumber);

    List<Logistics> findByStatus(Logistics.LogisticsStatus status);

    /**
     * 併發防護（DEF-158，LogisticsService.cancelLogistics）：條件式原子 UPDATE，取代
     * 「讀 status != DELIVERED 檢查 → setStatus(RETURNED) → save()」。兩個併發請求
     * （updateLogisticsStatus 推進到 DELIVERED、cancelLogistics 取消）可能都通過各自交易
     * 開始時讀到的舊快照檢查；本方法把「目前是否仍非 DELIVERED」的判斷下沉到 UPDATE 的
     * WHERE 子句，回傳 0 代表已被併發推進為 DELIVERED，呼叫端應拒絕本次取消。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Logistics l SET l.status = :newStatus WHERE l.id = :id AND l.status <> :blockedStatus")
    int cancelIfNotStatus(@Param("id") UUID id, @Param("blockedStatus") Logistics.LogisticsStatus blockedStatus,
            @Param("newStatus") Logistics.LogisticsStatus newStatus);
}
