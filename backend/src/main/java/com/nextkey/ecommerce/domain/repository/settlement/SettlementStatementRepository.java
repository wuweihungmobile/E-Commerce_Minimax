package com.nextkey.ecommerce.domain.repository.settlement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;

@Repository
public interface SettlementStatementRepository extends JpaRepository<SettlementStatement, UUID> {

    Page<SettlementStatement> findByTenantIdOrderByPeriodStartDesc(UUID tenantId, Pageable pageable);

    @Query("""
            SELECT s FROM SettlementStatement s
            WHERE s.tenantId = :tenantId
            AND s.periodStart >= :startDate
            AND s.periodEnd <= :endDate
            ORDER BY s.periodStart DESC
            """)
    List<SettlementStatement> findByTenantIdAndPeriodStartBetween(
            @Param("tenantId") UUID tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    Optional<SettlementStatement> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SettlementStatement> findByStatus(SettlementStatement.SettlementStatus status);

    @Query("SELECT s FROM SettlementStatement s WHERE s.status = :status ORDER BY s.generatedAt DESC")
    Page<SettlementStatement> findByStatusOrderByGeneratedAtDesc(
            @Param("status") SettlementStatement.SettlementStatus status,
            Pageable pageable);

    @Query("""
            SELECT s FROM SettlementStatement s
            WHERE s.tenantId = :tenantId AND s.status = :status
            ORDER BY s.generatedAt DESC
            """)
    Page<SettlementStatement> findByTenantIdAndStatusOrderByGeneratedAtDesc(
            @Param("tenantId") UUID tenantId,
            @Param("status") SettlementStatement.SettlementStatus status,
            Pageable pageable);

    boolean existsByTenantIdAndStatementNumber(UUID tenantId, String statementNumber);
}