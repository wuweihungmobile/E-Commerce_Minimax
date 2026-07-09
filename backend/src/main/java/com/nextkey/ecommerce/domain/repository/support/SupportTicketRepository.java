package com.nextkey.ecommerce.domain.repository.support;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.support.SupportTicket;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    Page<SupportTicket> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Optional<SupportTicket> findByIdAndCustomerId(UUID id, UUID customerId);

    Page<SupportTicket> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<SupportTicket> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTicketNumberStartingWith(String prefix);
}
