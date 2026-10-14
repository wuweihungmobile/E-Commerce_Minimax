package com.nextkey.ecommerce.core.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.BookingReviewDto;
import com.nextkey.ecommerce.api.dto.ReviewDto;
import com.nextkey.ecommerce.api.dto.ReviewReplyDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 評價/預訂評價授權缺口在真實 PostgreSQL 的整合測試（Sprint 251 DEF-331a/b/c；Sprint 264 補 DEF-373/374）。
 *
 * <p>Sprint 251 三個缺口皆由讀碼確認：
 * (a) {@code BookingReviewService.createBookingReview} 未驗證 bookingId 歸屬呼叫者本人；
 * (b) {@code ReviewService.createReview} 未驗證任何購買/預訂證明；
 * (c) {@code BookingReviewService.replyToBookingReview} 誤將訂房客人當作房東比對依據。
 *
 * <p>Sprint 264 新增兩項（撰寫 API_M08_Review.md 逐行讀碼核對授權時發現，見
 * DEFERRED_ITEMS_TRACKER.md DEF-373/374）：
 * (d) {@code ReviewReplyService.createReply} 原僅認定確切 listing.ownerId，統一為
 *     same-tenant-or-admin（比照既有 (c) 的模式）；
 * (e) {@code ReviewService.deleteReview}／{@code BookingReviewService.deleteBookingReview}
 *     註解寫「本人或管理員」但實作缺少管理員例外分支，已補上。
 *
 * <p>之所以用真實資料庫而非 Mockito：這些修復都讀取 Order/Booking/Listing/BookingReview 的
 * insertable=false 影子欄位（tenantId/userId/listingId 等），這些欄位只有「從資料庫載入」時才
 * 正確回填（DEF-065/DEF-332 既有教訓），純記憶體建構的假物件無法如實重現。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-REVIEW-AUTH: 評價/預訂評價授權缺口已修復（DEF-331a/b/c, DEF-373/374）")
class ReviewAuthorizationRealDbIntegrationTest {

    @Autowired private ReviewService reviewService;
    @Autowired private ReviewReplyService reviewReplyService;
    @Autowired private BookingReviewService bookingReviewService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private BookingRepository bookingRepository;

    private Tenant hostTenant;
    private Tenant otherTenant;
    private User buyer;
    private User otherUser;
    private UUID productListingId;
    private UUID roomListingId;

    @BeforeEach
    void seed() {
        final String stamp = String.valueOf(System.nanoTime());
        hostTenant = tenantRepository.save(Tenant.builder()
                .name("Review Auth Host Tenant").slug("rvauth-host-" + stamp)
                .contactEmail("rvauth-host-" + stamp + "@tenant.com").contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE).build());
        otherTenant = tenantRepository.save(Tenant.builder()
                .name("Review Auth Other Tenant").slug("rvauth-other-" + stamp)
                .contactEmail("rvauth-other-" + stamp + "@tenant.com").contactPhone("+886-987654321")
                .status(Tenant.TenantStatus.ACTIVE).build());

        User owner = seedUser(hostTenant.getId(), "owner", User.UserRole.STORE_OWNER);
        buyer = seedUser(null, "buyer", User.UserRole.BUYER);
        otherUser = seedUser(null, "other", User.UserRole.BUYER);

        productListingId = seedListing(hostTenant, owner, Listing.ListingType.PRODUCT, "Auth Test Product");
        roomListingId = seedListing(hostTenant, owner, Listing.ListingType.ROOM, "Auth Test Room");
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ---------- Helpers ----------

    private User seedUser(final UUID tenantId, final String tag, final User.UserRole role) {
        return userRepository.save(User.builder()
                .email("rvauth-" + tag + "-" + System.nanoTime() + "@example.com")
                .passwordHash("dummy")
                .fullName("RvAuth " + tag)
                .role(role)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build());
    }

    private UUID seedListing(final Tenant tenant, final User owner, final Listing.ListingType type,
            final String title) {
        return listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(owner)
                .listingType(type)
                .title(title)
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
    }

    private Order seedOrder(final User owner, final UUID listingId) {
        Listing listing = listingRepository.findById(listingId).orElseThrow();
        Order order = Order.builder()
                .tenant(hostTenant).user(owner).orderType(Listing.ListingType.PRODUCT)
                .status(Order.OrderStatus.COMPLETED).totalAmount(new BigDecimal("100.00")).currency("TWD")
                .items(new ArrayList<>()).build();
        order.addItem(OrderItem.builder().listing(listing).quantity(1)
                .unitPrice(new BigDecimal("100.00")).subtotal(new BigDecimal("100.00")).build());
        return orderRepository.saveAndFlush(order);
    }

    private Booking seedBooking(final User guest, final UUID listingId) {
        Listing listing = listingRepository.findById(listingId).orElseThrow();
        return bookingRepository.saveAndFlush(Booking.builder()
                .tenant(hostTenant).user(guest).roomListing(listing)
                .checkInDate(LocalDate.now().plusDays(10)).checkOutDate(LocalDate.now().plusDays(12))
                .guestCount(2).status(Booking.BookingStatus.COMPLETED).totalAmount(new BigDecimal("3000.00"))
                .build());
    }

    private ReviewDto.CreateRequest.CreateRequestBuilder reviewRequest(final UUID listingId) {
        return ReviewDto.CreateRequest.builder().listingId(listingId).rating(5).content("Great!");
    }

    private void asAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ========== DEF-331(b): ReviewService.createReview 購買驗證 ==========

    @Test
    @DisplayName("createReview: 本人訂單可對同一 listing 留評價")
    void createReview_ownOrder_succeeds() {
        Order order = seedOrder(buyer, productListingId);
        TenantContext.setCurrentUser(buyer.getId());

        ReviewDto.ReviewResponse response = reviewService.createReview(
                reviewRequest(productListingId).orderId(order.getId()).build());

        assertThat(response.getReviewId()).isNotNull();
    }

    @Test
    @DisplayName("createReview: 冒用他人訂單留評價拋出 E_1007（DEF-331b）")
    void createReview_someoneElsesOrder_throwsE1007() {
        Order order = seedOrder(buyer, productListingId); // 訂單屬於 buyer
        TenantContext.setCurrentUser(otherUser.getId());   // 呼叫者是 otherUser

        assertThatThrownBy(() -> reviewService.createReview(
                reviewRequest(productListingId).orderId(order.getId()).build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("createReview: 訂單存在但不含此 listing 時拋出 E_1007（DEF-331b）")
    void createReview_orderForDifferentListing_throwsE1007() {
        Order order = seedOrder(buyer, productListingId); // 訂單只含 productListingId
        TenantContext.setCurrentUser(buyer.getId());

        assertThatThrownBy(() -> reviewService.createReview(
                reviewRequest(roomListingId).orderId(order.getId()).build())) // 卻對 roomListingId 留評價
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("createReview: 本人訂房可留評價")
    void createReview_ownBooking_succeeds() {
        Booking booking = seedBooking(buyer, roomListingId);
        TenantContext.setCurrentUser(buyer.getId());

        ReviewDto.ReviewResponse response = reviewService.createReview(
                reviewRequest(roomListingId).bookingId(booking.getId()).build());

        assertThat(response.getReviewId()).isNotNull();
    }

    @Test
    @DisplayName("createReview: 冒用他人訂房留評價拋出 E_1007（DEF-331b）")
    void createReview_someoneElsesBooking_throwsE1007() {
        Booking booking = seedBooking(buyer, roomListingId);
        TenantContext.setCurrentUser(otherUser.getId());

        assertThatThrownBy(() -> reviewService.createReview(
                reviewRequest(roomListingId).bookingId(booking.getId()).build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("createReview: 不附任何購買/預訂證明時拋出 E_1007（DEF-331b）")
    void createReview_noProofOfPurchase_throwsE1007() {
        TenantContext.setCurrentUser(buyer.getId());

        assertThatThrownBy(() -> reviewService.createReview(reviewRequest(productListingId).build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    // ========== DEF-331(a): BookingReviewService.createBookingReview 擁有權 ==========

    @Test
    @DisplayName("createBookingReview: 本人訂房可建立評價")
    void createBookingReview_ownBooking_succeeds() {
        Booking booking = seedBooking(buyer, roomListingId);
        TenantContext.setCurrentUser(buyer.getId());

        BookingReviewDto.BookingReviewResponse response = bookingReviewService.createBookingReview(
                booking.getId(), 5, "Nice", "Great stay", null, false);

        assertThat(response.getReviewId()).isNotNull();
    }

    @Test
    @DisplayName("createBookingReview: 冒用他人訂房建立評價拋出 E_1007（DEF-331a）")
    void createBookingReview_someoneElsesBooking_throwsE1007() {
        Booking booking = seedBooking(buyer, roomListingId);
        TenantContext.setCurrentUser(otherUser.getId());

        assertThatThrownBy(() -> bookingReviewService.createBookingReview(
                booking.getId(), 5, "Nice", "Great stay", null, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    // ========== DEF-331(c): BookingReviewService.replyToBookingReview 房東比對 ==========

    @Test
    @DisplayName("replyToBookingReview: 房源所屬租戶成功回覆")
    void replyToBookingReview_byHostTenant_succeeds() {
        Booking booking = seedBooking(buyer, roomListingId);
        TenantContext.setCurrentUser(buyer.getId());
        BookingReviewDto.BookingReviewResponse created = bookingReviewService.createBookingReview(
                booking.getId(), 5, "Nice", "Great stay", null, false);

        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(hostTenant.getId());
        BookingReviewDto.BookingReviewResponse reply =
                bookingReviewService.replyToBookingReview(created.getReviewId(), "Thanks for staying!");

        assertThat(reply.getHostReply()).isEqualTo("Thanks for staying!");
    }

    @Test
    @DisplayName("replyToBookingReview: 訂房的客人本人回覆拋出 E_1007（DEF-331c 修復前會誤判為房東）")
    void replyToBookingReview_byOriginalGuest_throwsE1007() {
        Booking booking = seedBooking(buyer, roomListingId);
        TenantContext.setCurrentUser(buyer.getId());
        BookingReviewDto.BookingReviewResponse created = bookingReviewService.createBookingReview(
                booking.getId(), 5, "Nice", "Great stay", null, false);

        // 客人本人嘗試回覆（DEF-331c 修復前，production 會誤判這是房東）
        TenantContext.setCurrentTenant(null);
        assertThatThrownBy(() -> bookingReviewService.replyToBookingReview(created.getReviewId(), "Reply"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("replyToBookingReview: 不同租戶回覆拋出 E_1007")
    void replyToBookingReview_differentTenant_throwsE1007() {
        Booking booking = seedBooking(buyer, roomListingId);
        TenantContext.setCurrentUser(buyer.getId());
        BookingReviewDto.BookingReviewResponse created = bookingReviewService.createBookingReview(
                booking.getId(), 5, "Nice", "Great stay", null, false);

        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(otherTenant.getId());
        assertThatThrownBy(() -> bookingReviewService.replyToBookingReview(created.getReviewId(), "Reply"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("replyToBookingReview: 平台管理員可跨租戶回覆")
    void replyToBookingReview_byPlatformAdmin_succeeds() {
        Booking booking = seedBooking(buyer, roomListingId);
        TenantContext.setCurrentUser(buyer.getId());
        BookingReviewDto.BookingReviewResponse created = bookingReviewService.createBookingReview(
                booking.getId(), 5, "Nice", "Great stay", null, false);

        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(otherTenant.getId());
        asAdmin();
        BookingReviewDto.BookingReviewResponse reply =
                bookingReviewService.replyToBookingReview(created.getReviewId(), "Admin reply");

        assertThat(reply.getHostReply()).isEqualTo("Admin reply");
    }

    // ========== DEF-373: ReviewReplyService.createReply 統一為 same-tenant-or-admin ==========

    @Test
    @DisplayName("createReply: 同租戶的其他共同擁有人（非確切 listing.ownerId）可回覆")
    void createReply_sameTenantNonOwner_succeeds() {
        Order order = seedOrder(buyer, productListingId);
        TenantContext.setCurrentUser(buyer.getId());
        ReviewDto.ReviewResponse review = reviewService.createReview(
                reviewRequest(productListingId).orderId(order.getId()).build());

        // 另一位同租戶的操作者，但不是這個 listing 記錄上的確切 ownerId
        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(hostTenant.getId());
        ReviewReplyDto.ReplyResponse reply = reviewReplyService.createReply(
                review.getReviewId(),
                ReviewReplyDto.CreateReplyRequest.builder().content("Thanks!").build());

        assertThat(reply.getContent()).isEqualTo("Thanks!");
    }

    @Test
    @DisplayName("createReply: 不同租戶回覆拋出 E_1007")
    void createReply_differentTenant_throwsE1007() {
        Order order = seedOrder(buyer, productListingId);
        TenantContext.setCurrentUser(buyer.getId());
        ReviewDto.ReviewResponse review = reviewService.createReview(
                reviewRequest(productListingId).orderId(order.getId()).build());

        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(otherTenant.getId());
        assertThatThrownBy(() -> reviewReplyService.createReply(
                review.getReviewId(),
                ReviewReplyDto.CreateReplyRequest.builder().content("Thanks!").build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("createReply: 平台管理員可跨租戶回覆")
    void createReply_admin_succeeds() {
        Order order = seedOrder(buyer, productListingId);
        TenantContext.setCurrentUser(buyer.getId());
        ReviewDto.ReviewResponse review = reviewService.createReview(
                reviewRequest(productListingId).orderId(order.getId()).build());

        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(otherTenant.getId());
        asAdmin();
        ReviewReplyDto.ReplyResponse reply = reviewReplyService.createReply(
                review.getReviewId(),
                ReviewReplyDto.CreateReplyRequest.builder().content("Admin reply").build());

        assertThat(reply.getContent()).isEqualTo("Admin reply");
    }

    // ========== DEF-374: deleteReview／deleteBookingReview 管理員例外 ==========

    @Test
    @DisplayName("deleteReview: 管理員可軟刪除他人評價")
    void deleteReview_admin_succeeds() {
        Order order = seedOrder(buyer, productListingId);
        TenantContext.setCurrentUser(buyer.getId());
        ReviewDto.ReviewResponse review = reviewService.createReview(
                reviewRequest(productListingId).orderId(order.getId()).build());

        TenantContext.setCurrentUser(UUID.randomUUID());
        asAdmin();
        reviewService.deleteReview(review.getReviewId());

        ReviewDto.ReviewListResponse remaining =
                reviewService.getReviewsByListingId(productListingId, 0, 10, null);
        assertThat(remaining.getReviews()).isEmpty();
    }

    @Test
    @DisplayName("deleteBookingReview: 管理員可軟刪除他人訂房評價")
    void deleteBookingReview_admin_succeeds() {
        Booking booking = seedBooking(buyer, roomListingId);
        TenantContext.setCurrentUser(buyer.getId());
        BookingReviewDto.BookingReviewResponse created = bookingReviewService.createBookingReview(
                booking.getId(), 5, "Nice", "Great stay", null, false);

        TenantContext.setCurrentUser(UUID.randomUUID());
        asAdmin();
        bookingReviewService.deleteBookingReview(created.getReviewId());

        assertThat(bookingReviewService.getBookingReviews(booking.getId(), 0, 10).getContent()).isEmpty();
    }
}
