package com.nextkey.ecommerce.domain.model.payment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 已處理 Stripe webhook 事件（Sprint 51 AI-2411，Phase B）。
 * event_id 為主鍵，用於 webhook 事件去重（防 Stripe 重送造成重複處理副作用）。
 */
@Entity
@Table(name = "processed_stripe_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedStripeEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }
}
