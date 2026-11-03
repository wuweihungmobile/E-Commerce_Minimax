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

    /**
     * 某租戶名下、編號以 {@code prefix} 開頭的信用票據張數（Sprint 324，DEF-448）：當日流水號的來源，
     * 做法同 {@code SupportTicketRepository.countByTicketNumberStartingWith}。
     */
    long countByTenantIdAndCreditNoteNumberStartingWith(UUID tenantId, String prefix);
}