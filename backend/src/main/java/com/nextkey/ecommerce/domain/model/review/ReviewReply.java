package com.nextkey.ecommerce.domain.model.review;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 評價回覆 Entity
 *
 * 拆分原因（Sprint 16 US-001 / Retro AI-001）：
 * - 原本商家回覆的 reply 內容直接存放在 Review.sellerReply 欄位
 * - 為了支援多商家回覆、覆寫歷史、稽核軌跡，抽離為獨立 Entity
 * - 一對一關係：每個 Review 最多一個 Reply（unique constraint）
 *
 * 設計：
 * - Unique constraint on review_id 防止重複回覆
 * - 關聯 Review（ManyToOne 懶載入）
 * - 不儲存 replier 完整資訊，只存 replierId（與現有 userId 欄位設計一致）
 */
@Entity
@Table(name = "review_replies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_review_replies_review_id", columnNames = "review_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewReply {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(name = "review_id", insertable = false, updatable = false)
    private UUID reviewId;

    @Column(name = "replier_id", nullable = false)
    private UUID replierId;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
