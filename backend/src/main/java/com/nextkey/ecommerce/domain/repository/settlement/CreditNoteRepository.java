package com.nextkey.ecommerce.domain.repository.settlement;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.settlement.CreditNote;

@Repository
public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    List<CreditNote> findByTenantId(UUID tenantId);

    List<CreditNote> findByOriginalStatementId(UUID originalStatementId);

    boolean existsByTenantIdAndCreditNoteNumber(UUID tenantId, String creditNoteNumber);
}