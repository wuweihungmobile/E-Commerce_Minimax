package com.nextkey.ecommerce.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.promo.PromoCode;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    Optional<PromoCode> findByCodeIgnoreCaseAndTenantId(String code, UUID tenantId);

    Optional<PromoCode> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndTenantId(String code, UUID tenantId);

    /**
     * 原子佔用一次總量額度（Sprint 102，DEF-046）。
     *
     * <p>「檢查未達上限」與「遞增」寫在同一條 SQL 的 WHERE 與 SET 中，由資料庫在單一敘述內
     * 完成，取代原本 {@code isUsageLimitReached()} 讀 →{@code save()} 寫的兩段式操作——後者
     * 在兩張並行結帳請求下，可能同時讀到 {@code current_usage_count = 99}（上限 100）而雙雙通過，
     * 造成限量券超發，且兩次寫入互相覆蓋（lost update）使計數還少算一次。
     *
     * <p>本敘述同時取得該 promo 資料列的行鎖並持有至交易結束，因此同一張券的併發結帳會在此
     * 序列化——{@code max_usage_per_user} 的重查必須排在本呼叫之後才具備防護效果。
     *
     * <p>{@code max_usage_count IS NULL} 代表不限量，此時條件恆真、僅取鎖與遞增。
     *
     * @return 受影響筆數；1 表示成功佔用，0 表示已達上限（券售罄）或該 id 不存在
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE promo_codes
               SET current_usage_count = COALESCE(current_usage_count, 0) + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :id
               AND (max_usage_count IS NULL OR COALESCE(current_usage_count, 0) < max_usage_count)
            """, nativeQuery = true)
    int incrementUsageCountIfWithinLimit(@Param("id") UUID id);

    /**
     * 原子退還一次總量額度（Sprint 102，DEF-046）。
     *
     * <p>訂單取消時呼叫。同樣改為單一敘述的相對遞減，避免原本「讀出物件 → 減 1 → save」
     * 在兩筆訂單同時取消時互相覆蓋，導致額度只退還一次而永久少算。
     *
     * <p>{@code GREATEST(..., 0)} 為下限保護，語意與修復前的 {@code Math.max(0, current - 1)} 一致。
     *
     * @return 受影響筆數；0 表示該 id 不存在
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE promo_codes
               SET current_usage_count = GREATEST(COALESCE(current_usage_count, 0) - 1, 0),
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :id
            """, nativeQuery = true)
    int decrementUsageCount(@Param("id") UUID id);
}
