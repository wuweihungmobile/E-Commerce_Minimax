package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.payment.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByBookingId(UUID bookingId);

    boolean existsByOrderIdAndStatus(UUID orderId, Payment.PaymentStatus status);

    boolean existsByBookingIdAndStatus(UUID bookingId, Payment.PaymentStatus status);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByOrderIdAndStatus(UUID orderId, Payment.PaymentStatus status);

    List<Payment> findByOrderIdInAndStatus(List<UUID> orderIds, Payment.PaymentStatus status);

    Optional<Payment> findByTransactionId(String transactionId);
}