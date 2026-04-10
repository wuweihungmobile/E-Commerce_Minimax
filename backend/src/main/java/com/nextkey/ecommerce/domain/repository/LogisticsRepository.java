package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.logistics.Logistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LogisticsRepository extends JpaRepository<Logistics, UUID> {

    List<Logistics> findByOrderId(UUID orderId);

    Optional<Logistics> findByTrackingNumber(String trackingNumber);

    List<Logistics> findByStatus(Logistics.LogisticsStatus status);
}
