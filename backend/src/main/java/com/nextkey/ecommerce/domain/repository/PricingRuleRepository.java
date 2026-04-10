package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.room.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    List<PricingRule> findByTenantIdAndIsActiveTrue(UUID tenantId);

    List<PricingRule> findByRoomListingIdAndIsActiveTrue(UUID roomListingId);

    @Query("SELECT p FROM PricingRule p WHERE p.roomListingId = :roomListingId " +
            "AND p.isActive = true " +
            "AND p.validFrom <= :date " +
            "AND p.validTo >= :date")
    List<PricingRule> findActiveRulesForDate(
            @Param("roomListingId") UUID roomListingId,
            @Param("date") LocalDate date);

    @Query("SELECT p FROM PricingRule p WHERE p.roomListingId = :roomListingId " +
            "AND p.isActive = true " +
            "AND p.validFrom <= :startDate " +
            "AND p.validTo >= :endDate")
    List<PricingRule> findActiveRulesForDateRange(
            @Param("roomListingId") UUID roomListingId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    List<PricingRule> findByTenantId(UUID tenantId);
}
