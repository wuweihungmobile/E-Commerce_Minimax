package com.nextkey.ecommerce.core.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.dto.ReviewDto;
import com.nextkey.ecommerce.core.media.MediaService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.review.Review;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ReviewRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * ReviewService 單元測試（Sprint 73）
 *
 * 涵蓋探查階段確認的 7 個先前完全零覆蓋的方法：
 * - updateReview / deleteReview / markHelpful（US-001：一般正常/錯誤路徑）
 * - markAsHandled / markAsUnhandled（US-002：DEF-028 跨租戶寫入修復驗證）
 * - getReviewsByHandlingStatus（US-003：DEF-029 跨租戶讀取修復驗證）
 * - getUserReviews（US-004：DEF-030 匿名保護繞過修復驗證）
 *
 * 其餘 7 個方法（createReview/getReviewsByListingId/getRatingStats/searchReviews/
 * addImage/removeImage/reorderImages）已由既有 M08ReviewIntegrationTest /
 * M08ReviewImageIntegrationTest / M08ReviewStatsIntegrationTest /
 * ReviewServiceSearchTest / ReviewServiceCacheIntegrationTest 涵蓋，
 * 依 Rule 3「精準改動」不重複造測試。
 *
 * 標記為「US-00X：...（修復前為紅燈，修復後轉綠）」的測試，於本 Sprint 修復生產程式碼前
 * 實際執行過並確認失敗，證明對應漏洞存在；詳細過程記錄於 DEFERRED_ITEMS_TRACKER.md。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService 單元測試 (Sprint 73)")
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MediaService mediaService;

    @InjectMocks
    private ReviewService reviewService;

    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID LISTING_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ========== Helper Methods ==========

    private void asAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private Listing listingOf(final UUID tenantId) {
        return Listing.builder()
                .id(LISTING_ID)
                .tenantId(tenantId)
                .title("Test Listing")
                .listingType(Listing.ListingType.PRODUCT)
                .build();
    }

    private User userOf(final UUID userId) {
        User user = User.builder().build();
        user.setId(userId);
        return user;
    }

    private Review reviewOf(final UUID ownerId, final Listing listing) {
        Review review = Review.builder()
                .listing(listing)
                .user(userOf(ownerId))
                .reviewType(Review.ReviewType.PRODUCT)
                .rating(5)
                .title("Great")
                .content("Great product")
                .isAnonymous(false)
                .isVisible(true)
                .build();
        review.setId(REVIEW_ID);
        return review;
    }

    // ========== updateReview ==========

    @Test
    @DisplayName("updateReview：本人可更新自己的評價")
    void updateReview_owner_success() {
        TenantContext.setCurrentUser(REVIEWER_ID);
        Review review = reviewOf(REVIEWER_ID, listingOf(TENANT_A));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewDto.UpdateRequest request = ReviewDto.UpdateRequest.builder()
                .rating(4).title("Updated").content("Updated content").build();

        ReviewDto.ReviewResponse response = reviewService.updateReview(REVIEW_ID, request);

        assertThat(response.getRating()).isEqualTo(4);
        assertThat(response.getTitle()).isEqualTo("Updated");
        verify(reviewRepository).save(review);
    }

    @Test
    @DisplayName("updateReview：評價不存在拋出 E_1087")
    void updateReview_notFound_throwsE1087() {
        TenantContext.setCurrentUser(REVIEWER_ID);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.updateReview(REVIEW_ID, ReviewDto.UpdateRequest.builder().build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1087);
    }

    @Test
    @DisplayName("updateReview：非本人更新拋出 E_1007")
    void updateReview_notOwner_throwsE1007() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        Review review = reviewOf(REVIEWER_ID, listingOf(TENANT_A));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.updateReview(REVIEW_ID,
                ReviewDto.UpdateRequest.builder().rating(1).build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
        verify(reviewRepository, never()).save(any());
    }

    // ========== deleteReview ==========

    @Test
    @DisplayName("deleteReview：本人可軟刪除自己的評價")
    void deleteReview_owner_success() {
        TenantContext.setCurrentUser(REVIEWER_ID);
        Review review = reviewOf(REVIEWER_ID, listingOf(TENANT_A));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        reviewService.deleteReview(REVIEW_ID);

        assertThat(review.getIsVisible()).isFalse();
        verify(reviewRepository).save(review);
    }

    @Test
    @DisplayName("deleteReview：評價不存在拋出 E_1087")
    void deleteReview_notFound_throwsE1087() {
        TenantContext.setCurrentUser(REVIEWER_ID);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.deleteReview(REVIEW_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1087);
    }

    @Test
    @DisplayName("deleteReview：非本人刪除拋出 E_1007")
    void deleteReview_notOwner_throwsE1007() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        Review review = reviewOf(REVIEWER_ID, listingOf(TENANT_A));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.deleteReview(REVIEW_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
        verify(reviewRepository, never()).save(any());
    }

    // ========== markHelpful ==========

    @Test
    @DisplayName("markHelpful：投票委派給原子敘述登記，不再讀出整份 map 改完寫回")
    void markHelpful_delegatesToAtomicStatement() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        Review review = reviewOf(REVIEWER_ID, listingOf(TENANT_A));
        review.setHelpfulCount(1);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(reviewRepository.registerHelpfulVote(REVIEW_ID, OTHER_USER_ID.toString())).thenReturn(1);

        ReviewDto.ReviewResponse response = reviewService.markHelpful(REVIEW_ID);

        // Sprint 105（DEF-054）：這裡只能驗證「有委派、且不再走 save() 讀後寫」。
        // 真正的兩個性質——同一人重複投票冪等、相異使用者併發投票不互相覆蓋——
        // 無法用 mock 掉 repository 的單執行緒測試證明（修復前這個測試照樣全綠），
        // 由 M08ReviewHelpfulVotingIntegrationTest 壓真實 DB 負責。
        verify(reviewRepository).registerHelpfulVote(REVIEW_ID, OTHER_USER_ID.toString());
        verify(reviewRepository, never()).save(any(Review.class));
        assertThat(response.getHelpfulCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("markHelpful：評價不存在拋出 E_1087")
    void markHelpful_notFound_throwsE1087() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.markHelpful(REVIEW_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1087);
    }

    // ========== markAsHandled / markAsUnhandled（US-002, DEF-028）==========

    @Test
    @DisplayName("US-002：markAsHandled 對他租戶的評價不得放行（修復前為紅燈，修復後轉綠）")
    void markAsHandled_crossTenantReview_mustBeRejected() {
        // 當前使用者所屬租戶為 TENANT_A，但評價所屬 listing 為 TENANT_B——模擬他租戶賣家
        // 呼叫 PUT /v2/reviews/{reviewId}/handle 竄改別家商店的評價處理狀態。
        TenantContext.setCurrentUser(OTHER_USER_ID);
        TenantContext.setCurrentTenant(TENANT_A);
        Review review = reviewOf(REVIEWER_ID, listingOf(TENANT_B));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.markAsHandled(REVIEW_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("markAsHandled：本租戶賣家可放行（不得矯枉過正）")
    void markAsHandled_sameTenant_passes() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        TenantContext.setCurrentTenant(TENANT_A);
        Review review = reviewOf(REVIEWER_ID, listingOf(TENANT_A));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewDto.ReviewResponse response = reviewService.markAsHandled(REVIEW_ID);

        assertThat(response.getIsHandled()).isTrue();
    }

    @Test
    @DisplayName("markAsHandled：admin 跨租戶放行")
    void markAsHandled_admin_bypassesTenant() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        TenantContext.setCurrentTenant(TENANT_A);
        asAdmin();
        Review review = reviewOf(REVIEWER_ID, listingOf(TENANT_B));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewDto.ReviewResponse response = reviewService.markAsHandled(REVIEW_ID);

        assertThat(response.getIsHandled()).isTrue();
    }

    @Test
    @DisplayName("US-002：markAsUnhandled 對他租戶的評價不得放行（修復前為紅燈，修復後轉綠）")
    void markAsUnhandled_crossTenantReview_mustBeRejected() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        TenantContext.setCurrentTenant(TENANT_A);
        Review review = reviewOf(REVIEWER_ID, listingOf(TENANT_B));
        review.setIsHandled(true);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.markAsUnhandled(REVIEW_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("markAsUnhandled：本租戶賣家可放行（不得矯枉過正）")
    void markAsUnhandled_sameTenant_passes() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        TenantContext.setCurrentTenant(TENANT_A);
        Review review = reviewOf(REVIEWER_ID, listingOf(TENANT_A));
        review.setIsHandled(true);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewDto.ReviewResponse response = reviewService.markAsUnhandled(REVIEW_ID);

        assertThat(response.getIsHandled()).isFalse();
    }

    // ========== getReviewsByHandlingStatus（US-003, DEF-029）==========

    @Test
    @DisplayName("US-003：getReviewsByHandlingStatus 不得洩漏他租戶的評價（修復前為紅燈，修復後轉綠）")
    void getReviewsByHandlingStatus_mustNotLeakOtherTenantReviews() {
        TenantContext.setCurrentTenant(TENANT_A);
        Review ownReview = reviewOf(REVIEWER_ID, listingOf(TENANT_A));
        Review otherTenantReview = reviewOf(OTHER_USER_ID, listingOf(TENANT_B));
        otherTenantReview.setId(UUID.randomUUID());

        // 非 admin 呼叫租戶過濾方法，僅回傳本租戶資料（otherTenantReview 不得出現在結果中）。
        when(reviewRepository.findByIsHandledAndTenantId(eq(false), eq(TENANT_A), any()))
                .thenReturn(new PageImpl<>(List.of(ownReview)));

        ReviewDto.ReviewListResponse response = reviewService.getReviewsByHandlingStatus(false, 0, 20);

        assertThat(response.getReviews())
                .as("不得洩漏他租戶的評價")
                .extracting(ReviewDto.ReviewResponse::getReviewId)
                .containsExactly(ownReview.getId());
    }

    @Test
    @DisplayName("getReviewsByHandlingStatus：admin 可看到所有租戶的評價")
    void getReviewsByHandlingStatus_admin_seesAllTenants() {
        TenantContext.setCurrentTenant(TENANT_A);
        asAdmin();
        Review ownReview = reviewOf(REVIEWER_ID, listingOf(TENANT_A));
        Review otherTenantReview = reviewOf(OTHER_USER_ID, listingOf(TENANT_B));
        otherTenantReview.setId(UUID.randomUUID());
        when(reviewRepository.findByIsHandled(eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(ownReview, otherTenantReview)));

        ReviewDto.ReviewListResponse response = reviewService.getReviewsByHandlingStatus(false, 0, 20);

        assertThat(response.getReviews()).hasSize(2);
    }

    // ========== getUserReviews（US-004, DEF-030）==========

    @Test
    @DisplayName("US-004：getUserReviews 不得讓非本人查詢他人評價列表（修復前為紅燈，修復後轉綠）")
    void getUserReviews_otherUser_mustBeRejected() {
        TenantContext.setCurrentUser(OTHER_USER_ID);

        assertThatThrownBy(() -> reviewService.getUserReviews(REVIEWER_ID, 0, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("getUserReviews：可查詢自己的評價列表")
    void getUserReviews_self_passes() {
        TenantContext.setCurrentUser(REVIEWER_ID);
        Review review = reviewOf(REVIEWER_ID, listingOf(TENANT_A));
        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(eq(REVIEWER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(review)));

        ReviewDto.ReviewListResponse response = reviewService.getUserReviews(REVIEWER_ID, 0, 10);

        assertThat(response.getReviews()).hasSize(1);
    }

    @Test
    @DisplayName("getUserReviews：admin 可查詢任何人的評價列表")
    void getUserReviews_admin_canQueryOthers() {
        TenantContext.setCurrentUser(OTHER_USER_ID);
        asAdmin();
        Review review = reviewOf(REVIEWER_ID, listingOf(TENANT_A));
        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(eq(REVIEWER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(review)));

        ReviewDto.ReviewListResponse response = reviewService.getUserReviews(REVIEWER_ID, 0, 10);

        assertThat(response.getReviews()).hasSize(1);
    }
}
