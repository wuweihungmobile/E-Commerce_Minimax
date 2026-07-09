package com.nextkey.ecommerce.domain.repository.settlement;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment;

@Repository
public interface SettlementAdjustmentRepository extends JpaRepository<SettlementAdjustment, UUID> {

    List<SettlementAdjustment> findByTenantIdAndStatus(UUID tenantId, SettlementAdjustment.AdjustmentStatus status);

    List<SettlementAdjustment> findByOriginalStatementId(UUID originalStatementId);
}
