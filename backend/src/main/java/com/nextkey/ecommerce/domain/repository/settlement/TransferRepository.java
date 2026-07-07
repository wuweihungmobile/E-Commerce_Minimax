package com.nextkey.ecommerce.domain.repository.settlement;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.settlement.Transfer;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findBySettlementStatementId(UUID settlementStatementId);

    Page<Transfer> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<Transfer> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Transfer> findByStripeTransferId(String stripeTransferId);
}
