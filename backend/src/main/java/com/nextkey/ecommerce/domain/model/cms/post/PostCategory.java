package com.nextkey.ecommerce.domain.model.cms.post;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * M15 CMS PostCategory Entity
 * 貼文分類實體
 */
@Entity
@Table(name = "post_categories",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "slug"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 110)
    private String slug;

    @Column(length = 500)
    private String description;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}