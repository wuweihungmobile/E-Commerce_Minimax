package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.order.OrderStateLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderStateLogRepository extends JpaRepository<OrderStateLog, UUID> {

    List<OrderStateLog> findByOrderIdOrderBySequenceAsc(UUID orderId);

    @Query("SELECT MAX(s.sequence) FROM OrderStateLog s WHERE s.order.id = :orderId")
    Integer findMaxSequenceByOrderId(@Param("orderId") UUID orderId);
}