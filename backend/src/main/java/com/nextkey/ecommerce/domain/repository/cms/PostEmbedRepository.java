package com.nextkey.ecommerce.domain.repository.cms;

import com.nextkey.ecommerce.domain.model.cms.post.PostEmbed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * M15 CMS PostEmbed Repository
 */
@Repository
public interface PostEmbedRepository extends JpaRepository<PostEmbed, UUID> {

    /**
     * 依 Post 查詢所有嵌入
     */
    List<PostEmbed> findByPostId(UUID postId);

    /**
     * 依 Post 查詢（按順序排序）
     */
    List<PostEmbed> findByPostIdOrderByEmbedOrderAsc(UUID postId);

    /**
     * 檢查 Post 是否已有特定 Listing 嵌入
     */
    boolean existsByPostIdAndListingId(UUID postId, UUID listingId);

    /**
     * 刪除 Post 的所有嵌入
     */
    void deleteByPostId(UUID postId);

    /**
     * 統計 Post 的嵌入數量
     */
    long countByPostId(UUID postId);

    /**
     * 檢查 Listing 是否被任何貼文引用
     */
    @Query("SELECT COUNT(pe) > 0 FROM PostEmbed pe WHERE pe.listingId = :listingId")
    boolean isListingUsedByAnyPost(@Param("listingId") UUID listingId);
}