package com.nextkey.ecommerce.core.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.ReviewDto;
import com.nextkey.ecommerce.api.dto.ReviewSearchCriteria;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.review.Review;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ReviewRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * 評價多維度搜尋在真實 PostgreSQL 的整合測試（DEF-277）。
 *
 * <p><b>為什麼一定要真實資料庫</b>：{@code ReviewRepository.searchReviews} 的「是否有圖片」條件過去寫成
 * {@code SIZE(r.images)}，但 {@code Review.images} 是 {@code @JdbcTypeCode(SqlTypes.JSON)} 的
 * {@code List<String>}——Hibernate 視為<b>基本屬性</b>而非集合，SQL 翻譯階段拋 {@code ClassCastException}
 * （{@code BasicAttributeMapping cannot be cast to PluralAttributeMapping}）。這在啟動時的查詢驗證不會被發現，
 * 只有真的執行才爆，且不論帶什麼篩選條件<b>每次都失敗</b>（SQL 翻譯與參數值無關）——評價搜尋端點整個不可用。
 * 但 {@code ReviewServiceSearchTest} 全程 mock 了 {@code ReviewRepository}，所以一直是綠燈。
 *
 * <p>本測試不只守「查得到」，也守每個篩選條件的<b>語意</b>：資料庫內「沒有圖片」有兩種形態
 * （SQL NULL 與 JSON 空陣列 {@code []}，欄位預設值就是 {@code '[]'}），兩者都必須被視為「沒有圖片」。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-REVIEW-SEARCH: 評價搜尋在真實資料庫可用，且各篩選條件語意正確（DEF-277）")
class ReviewSearchRealDbIntegrationTest {

    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ReviewService reviewService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;

    private UUID listingId;
    private UUID otherListingId;
    private UUID buyerId;
    private UUID ownerId;

    @BeforeEach
    void seed() {
        final String stamp = String.valueOf(System.nanoTime());
        final Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Review Search Tenant")
                .slug("rvsearch-" + stamp)
                .contactEmail("rvsearch-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        final User owner = seedUser(tenant.getId(), "owner", User.UserRole.STORE_OWNER);
        ownerId = owner.getId();
        buyerId = seedUser(tenant.getId(), "buyer", User.UserRole.BUYER).getId();
        listingId = seedListing(tenant, owner, "Review Search Product");
        otherListingId = seedListing(tenant, owner, "Other Product");

        // 四則可見評價，建立時間各差一天、都在當天中午（避開時區換算造成的日界模糊）
        final UUID r1 = seedReview(listingId, 5, "Great phone", "Loved it", "[\"a.jpg\"]", "2027-01-10T12:00:00Z", true);
        seedReview(listingId, 4, "OK", "Fine quality", "[]", "2027-01-11T12:00:00Z", true);
        seedReview(listingId, 2, "Bad", "Broke quickly", null, "2027-01-12T12:00:00Z", true);
        seedReview(listingId, 3, "Meh", "GREAT packaging", "[\"b.jpg\",\"c.jpg\"]", "2027-01-13T12:00:00Z", true);
        seedReply(r1);
        // 不可見的評價（有圖片）與他人商品的評價（有圖片）——任何條件下都不該出現
        seedReview(listingId, 5, "Hidden", "Hidden with image", "[\"h.jpg\"]", "2027-01-10T12:00:00Z", false);
        seedReview(otherListingId, 5, "Other listing", "Other with image", "[\"o.jpg\"]", "2027-01-10T12:00:00Z", true);
    }

    private User seedUser(final UUID tenantId, final String tag, final User.UserRole role) {
        return userRepository.save(User.builder()
                .email("rvsearch-" + tag + "-" + System.nanoTime() + "@example.com")
                .passwordHash("dummy")
                .fullName("RvSearch " + tag)
                .role(role)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build());
    }

    private UUID seedListing(final Tenant tenant, final User owner, final String title) {
        return listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(owner)
                .listingType(Listing.ListingType.PRODUCT)
                .title(title)
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
    }

    /**
     * {@code imagesJson} 為 null 時寫入 SQL NULL；{@code is_anonymous}／{@code is_handled} 必須顯式給值
     * （測試 DB 由 {@code ddl-auto=update} 建表，沒有 Flyway 的 DEFAULT，{@code toReviewResponse} 會拆箱）。
     */
    private UUID seedReview(final UUID forListing, final int rating, final String title, final String content,
            final String imagesJson, final String createdAt, final boolean visible) {
        final UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reviews
                    (id, listing_id, user_id, review_type, rating, title, content, images,
                     helpful_votes, helpful_count, is_visible, is_anonymous, is_handled,
                     created_at, updated_at)
                VALUES (?, ?, ?, 'PRODUCT', ?, ?, ?, ?::jsonb,
                        '{}'::jsonb, 0, ?, false, false, ?, ?)
                """, id, forListing, buyerId, rating, title, content, imagesJson, visible,
                Timestamp.from(Instant.parse(createdAt)), Timestamp.from(Instant.parse(createdAt)));
        return id;
    }

    private void seedReply(final UUID reviewId) {
        jdbcTemplate.update("""
                INSERT INTO review_replies (id, review_id, replier_id, content, created_at, updated_at)
                VALUES (?, ?, ?, 'Thanks!', NOW(), NOW())
                """, UUID.randomUUID(), reviewId, ownerId);
    }

    /** 直接呼叫 Repository 查詢，回傳命中評價的標題集合。 */
    private Set<String> search(final String keyword, final Integer minRating, final Integer maxRating,
            final Instant startDate, final Instant endDate, final Boolean hasImages, final Boolean hasReply) {
        return reviewRepository.searchReviews(listingId, keyword, minRating, maxRating, startDate, endDate,
                        hasImages, hasReply, PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "createdAt")))
                .getContent().stream().map(Review::getTitle).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("無任何篩選條件：回傳該商品全部可見評價（不含不可見、不含他人商品）")
    void noFilters_returnsAllVisibleReviewsOfThisListing() {
        assertThat(search(null, null, null, null, null, null, null))
                .containsExactlyInAnyOrder("Great phone", "OK", "Bad", "Meh");
    }

    @Test
    @DisplayName("hasImages=true：只回傳有圖片者（[\"a.jpg\"]、多張），排除 [] 與 NULL")
    void hasImagesTrue_returnsOnlyReviewsWithImages() {
        assertThat(search(null, null, null, null, null, true, null))
                .containsExactlyInAnyOrder("Great phone", "Meh");
    }

    @Test
    @DisplayName("hasImages=false：回傳沒有圖片者——JSON 空陣列 [] 與 SQL NULL 兩種形態都算沒有圖片")
    void hasImagesFalse_treatsEmptyArrayAndNullAsNoImages() {
        assertThat(search(null, null, null, null, null, false, null))
                .containsExactlyInAnyOrder("OK", "Bad");
    }

    @Test
    @DisplayName("hasReply=true／false：以是否存在商家回覆切分")
    void hasReplyFilter_splitsByExistenceOfSellerReply() {
        assertThat(search(null, null, null, null, null, null, true)).containsExactlyInAnyOrder("Great phone");
        assertThat(search(null, null, null, null, null, null, false))
                .containsExactlyInAnyOrder("OK", "Bad", "Meh");
    }

    @Test
    @DisplayName("關鍵字：比對標題或內容，不分大小寫")
    void keyword_matchesTitleOrContentCaseInsensitively() {
        assertThat(search("great", null, null, null, null, null, null))
                .containsExactlyInAnyOrder("Great phone", "Meh");
    }

    @Test
    @DisplayName("評分範圍 3~4：含邊界")
    void ratingRange_isInclusive() {
        assertThat(search(null, 3, 4, null, null, null, null)).containsExactlyInAnyOrder("OK", "Meh");
    }

    @Test
    @DisplayName("日期範圍：只回傳建立時間落在區間內者")
    void dateRange_returnsOnlyReviewsCreatedWithin() {
        assertThat(search(null, null, null, Instant.parse("2027-01-11T00:00:00Z"),
                Instant.parse("2027-01-12T23:59:59Z"), null, null)).containsExactlyInAnyOrder("OK", "Bad");
    }

    @Test
    @DisplayName("組合條件：hasImages=true 且 hasReply=true → 只有同時符合者")
    void combinedFilters_applyAsConjunction() {
        assertThat(search(null, null, null, null, null, true, true)).containsExactlyInAnyOrder("Great phone");
        assertThat(search(null, null, null, null, null, true, false)).containsExactlyInAnyOrder("Meh");
    }

    @Test
    @DisplayName("端點實際走的 Service 路徑：hasImages=true 在真實資料庫回傳 2 則")
    void serviceSearch_withHasImages_worksEndToEnd() {
        final ReviewDto.ReviewListResponse response = reviewService.searchReviews(ReviewSearchCriteria.builder()
                .listingId(listingId)
                .hasImages(true)
                .page(0)
                .size(10)
                .build());

        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getReviews()).hasSize(2);
    }
}
