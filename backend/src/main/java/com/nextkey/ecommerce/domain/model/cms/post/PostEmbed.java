package com.nextkey.ecommerce.domain.model.cms.post;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * M15 CMS PostEmbed Entity
 * 貼文嵌入卡片，關聯 Post 和 Listing
 */
@Entity
@Table(name = "post_embeds",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "listing_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostEmbed {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "listing_type")
    private String listingType;

    @Column(name = "embed_order")
    @Builder.Default
    private Integer embedOrder = 0;

    @Builder.Default
    private Instant createdAt = Instant.now();
}