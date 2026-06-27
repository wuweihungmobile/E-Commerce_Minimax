package com.nextkey.ecommerce.domain.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.order.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Order> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.status = :status")
    Page<Order> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") Order.OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.tenant.id = :tenantId AND o.status = :status")
    Page<Order> findByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") Order.OrderStatus status, Pageable pageable);

    Optional<Order> findByIdAndUserId(UUID orderId, UUID userId);

    Optional<Order> findByIdAndTenantId(UUID orderId, UUID tenantId);

    @Query("SELECT o FROM Order o WHERE o.tenant.id = :tenantId")
    List<Order> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT o FROM Order o WHERE o.tenant.id = :tenantId AND o.createdAt >= :start AND o.createdAt <= :end")
    List<Order> findByTenantIdAndCreatedAtBetween(
            @Param("tenantId") UUID tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.tenant.id = :tenantId AND o.createdAt >= :start AND o.createdAt <= :end")
    int countByTenantIdAndCreatedAtBetween(
            @Param("tenantId") UUID tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.tenant.id = :tenantId AND o.status = :status")
    int countByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") Order.OrderStatus status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.tenant.id = :tenantId AND o.createdAt >= :after")
    long countByTenantIdAndCreatedAtAfter(@Param("tenantId") UUID tenantId, @Param("after") Instant after);

    @Query("SELECT o FROM Order o WHERE o.tenant.id = :tenantId ORDER BY o.createdAt DESC")
    List<Order> findTopByTenantIdOrderByCreatedAtDesc(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o " +
            "WHERE o.tenant.id = :tenantId AND o.status = :status AND o.createdAt >= :after")
    BigDecimal sumTotalAmountByTenantIdAndStatusAndCreatedAtAfter(
            @Param("tenantId") UUID tenantId,
            @Param("status") Order.OrderStatus status,
            @Param("after") Instant after);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.tenant.id = :tenantId AND o.status IN :statuses")
    long countByTenantIdAndStatusIn(
            @Param("tenantId") UUID tenantId,
            @Param("statuses") List<Order.OrderStatus> statuses);
}