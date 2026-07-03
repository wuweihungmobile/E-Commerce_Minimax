package com.nextkey.ecommerce.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.payment.ProcessedStripeEvent;

/**
 * 已處理 Stripe webhook 事件 repository（Sprint 51 AI-2411，Phase B 事件去重）。
 * existsById(eventId) 用於去重判斷。
 */
@Repository
public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, String> {
}
