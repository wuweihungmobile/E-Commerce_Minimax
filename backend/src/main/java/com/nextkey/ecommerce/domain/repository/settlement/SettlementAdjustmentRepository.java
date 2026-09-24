package com.nextkey.ecommerce.domain.repository.settlement;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment;

@Repository
public interface SettlementAdjustmentRepository extends JpaRepository<SettlementAdjustment, UUID> {

    List<SettlementAdjustment> findByTenantIdAndStatus(UUID tenantId, SettlementAdjustment.AdjustmentStatus status);

    List<SettlementAdjustment> findByOriginalStatementId(UUID originalStatementId);

    /**
     * 釋放被某結算單折入的調整單，還原為 {@code PENDING}（DEF-273）：結算單被駁回（終態、資金未發生）時，
     * 它折入的退款扣除若仍標記為 {@code APPLIED}，就會隨這張死掉的結算單消失——賣家該被扣的錢沒被扣。
     * 只還原 {@code APPLIED}，讓下一張結算單重新折入。
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE adjustment_statements SET status = 'PENDING', applied_statement_id = NULL,"
            + " applied_at = NULL WHERE applied_statement_id = :statementId AND status = 'APPLIED'",
            nativeQuery = true)
    int releaseAppliedTo(@Param("statementId") UUID statementId);
}
