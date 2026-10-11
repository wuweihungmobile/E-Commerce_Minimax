package com.nextkey.ecommerce.core.review;

import com.nextkey.ecommerce.api.dto.BookingReviewDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.review.BookingReview;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.review.BookingReviewRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BookingReviewService 單元測試
 *
 * 測試範圍（Sprint 16 US-001；Sprint 251 DEF-331a/c 修復後重寫房東回覆相關 fixture）：
 * - createBookingReview: 建立預訂評價
 *   - 成功場景：本人訂房
 *   - 失敗場景：冒用他人訂房（DEF-331a）
 * - replyToBookingReview: 商家回覆預訂評價（核心 Sprint 15 功能）
 *   - 成功場景：房源所屬租戶回覆、平台管理員跨租戶回覆
 *   - 失敗場景：評價不存在、不同租戶回覆、訂房客人本人回覆（DEF-331c：先前誤判為「房東」）
 * - getBookingReviewById: 取得評價詳情
 *   - 成功場景：找到評價
 *   - 失敗場景：評價不存在
 * - 邊界測試：null reply、空字串 reply、超長 reply
 *
 * 設計重點：
 * - 使用 @ExtendWith(MockitoExtension.class) 純 Service 層測試
 * - TenantContext 為單純 ThreadLocal 工具類別，直接呼叫其靜態方法設定/清除即可
 * - 覆蓋率目標：>= 80%
 *
 * DEF-331(a)(c) 修復前，本檔案的 fixture 將訂房客人的 userId 誤命名並當作「房東」比對依據
 * （production 的 {@code replyToBookingReview} 原本也真的這樣誤判，見 DEFERRED_ITEMS_TRACKER.md）。
 * 修復後，「房東」改由房源所屬租戶（{@code TenantContext.getCurrentTenant()} 比對
 * {@code booking.getRoomListing().getTenantId()}）決定，與訂房客人的 userId 無關。
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("BookingReviewService 單元測試 (Sprint 16 US-001; Sprint 251 DEF-331a/c)")
class BookingReviewServiceTest {

    @Mock
    private BookingReviewRepository bookingReviewRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookingReviewService bookingReviewService;

    private static final UUID GUEST_USER_ID = UUID.randomUUID();    // 訂房的客人（評價作者）
    private static final UUID OTHER_USER_ID = UUID.randomUUID();    // 與此訂房無關的第三人
    private static final UUID HOST_TENANT_ID = UUID.randomUUID();   // 房源所屬租戶
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();  // 另一個租戶
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final UUID LISTING_ID = UUID.randomUUID();
    private static final String SAMPLE_REPLY = "感謝您的入住，期待您再次蒞臨！";

    @AfterEach
    void cleanup() {
        // 確保 ThreadLocal 被清理，避免測試間污染
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ========== Helper Methods ==========

    private Listing listingOf(UUID tenantId) {
        return Listing.builder().id(LISTING_ID).tenantId(tenantId).build();
    }

    private Booking createBooking(UUID guestUserId, UUID listingTenantId) {
        return Booking.builder()
                .id(BOOKING_ID)
                .userId(guestUserId)
                .roomListing(listingOf(listingTenantId))
                .build();
    }

    /**
     * 建立測試用 BookingReview。
     *
     * 注意：實體使用 Lombok @Builder + JPA @Id @GeneratedValue。
     * 為避免 builder 與 generated value 的互動問題，id 與 bookingId
     * 在 build() 後使用 setter 明確設定（與 M08ReviewIntegrationTest
     * 的既有測試模式一致）。
     */
    private BookingReview createReview(UUID guestUserId, UUID listingTenantId, boolean isAnonymous) {
        Booking booking = createBooking(guestUserId, listingTenantId);
        BookingReview review = BookingReview.builder()
                .booking(booking)
                .userId(guestUserId)
                .rating(5)
                .title("很棒")
                .content("房間很乾淨，主人很熱情")
                .isAnonymous(isAnonymous)
                .isVisible(true)
                .build();
        review.setId(REVIEW_ID);
        review.setBookingId(booking.getId());
        return review;
    }

    private void asAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ========== createBookingReview 測試（DEF-331a） ==========

    @Test
    @Order(1)
    @DisplayName("createBookingReview: 本人訂房可建立評價")
    void createBookingReview_ownBooking_succeeds() {
        // Given
        TenantContext.setCurrentUser(GUEST_USER_ID);
        Booking booking = createBooking(GUEST_USER_ID, HOST_TENANT_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingReviewRepository.findByBookingIdAndUserId(BOOKING_ID, GUEST_USER_ID))
                .thenReturn(Optional.empty());
        when(userRepository.findById(GUEST_USER_ID)).thenReturn(Optional.of(User.builder().build()));
        when(bookingReviewRepository.save(any(BookingReview.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        BookingReviewDto.BookingReviewResponse response = bookingReviewService.createBookingReview(
                BOOKING_ID, 5, "很棒", "住宿體驗很好", null, false);

        // Then
        assertNotNull(response);
        assertEquals(5, response.getRating());
        verify(bookingReviewRepository).save(any(BookingReview.class));
    }

    @Test
    @Order(2)
    @DisplayName("createBookingReview: 冒用他人訂房建立評價拋出 BusinessException（DEF-331a）")
    void createBookingReview_someoneElsesBooking_throwsE1007() {
        // Given: 訂房的客人是 GUEST_USER_ID，但呼叫者是毫無關係的 OTHER_USER_ID
        TenantContext.setCurrentUser(OTHER_USER_ID);
        Booking booking = createBooking(GUEST_USER_ID, HOST_TENANT_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> bookingReviewService.createBookingReview(
                        BOOKING_ID, 5, "很棒", "住宿體驗很好", null, false));

        assertEquals(ErrorCode.E_1007, exception.getErrorCode());
        verify(bookingReviewRepository, never()).save(any());
    }

    @Test
    @Order(3)
    @DisplayName("createBookingReview: 訂房不存在時拋出 BusinessException")
    void createBookingReview_bookingNotFound_throwsE3000() {
        TenantContext.setCurrentUser(GUEST_USER_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> bookingReviewService.createBookingReview(
                        BOOKING_ID, 5, "很棒", "住宿體驗很好", null, false));

        assertEquals(ErrorCode.E_3000, exception.getErrorCode());
        verify(bookingReviewRepository, never()).save(any());
    }

    // ========== replyToBookingReview 測試（DEF-331c） ==========

    @Test
    @Order(4)
    @DisplayName("replyToBookingReview: 房源所屬租戶成功回覆評價")
    void replyToBookingReview_byHostTenant_succeeds() {
        // Given
        TenantContext.setCurrentUser(UUID.randomUUID()); // 房東端操作者，與訂房客人無關
        TenantContext.setCurrentTenant(HOST_TENANT_ID);
        BookingReview existingReview = createReview(GUEST_USER_ID, HOST_TENANT_ID, true);
        when(bookingReviewRepository.findById(REVIEW_ID))
                .thenReturn(Optional.of(existingReview));
        when(bookingReviewRepository.save(any(BookingReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        BookingReviewDto.BookingReviewResponse response =
                bookingReviewService.replyToBookingReview(REVIEW_ID, SAMPLE_REPLY);

        // Then
        assertNotNull(response);
        assertEquals(SAMPLE_REPLY, response.getHostReply());
        assertNotNull(response.getHostRepliedAt());
        assertEquals("Anonymous", response.getUserFullName()); // isAnonymous=true

        // 驗證 repository 互動
        verify(bookingReviewRepository, times(1)).findById(REVIEW_ID);
        verify(bookingReviewRepository, times(1)).save(existingReview);
    }

    @Test
    @Order(5)
    @DisplayName("replyToBookingReview: 評價不存在時拋出 BusinessException")
    void replyToBookingReview_ReviewNotFound() {
        // Given
        TenantContext.setCurrentUser(UUID.randomUUID());
        when(bookingReviewRepository.findById(REVIEW_ID))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bookingReviewService.replyToBookingReview(REVIEW_ID, SAMPLE_REPLY)
        );

        assertEquals(ErrorCode.E_1092, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Booking review not found"));

        // 驗證沒有呼叫 save
        verify(bookingReviewRepository, never()).save(any());
    }

    @Test
    @Order(6)
    @DisplayName("replyToBookingReview: 不同租戶嘗試回覆拋出 BusinessException")
    void replyToBookingReview_differentTenant_throwsE1007() {
        // Given: 評價的房源屬於 HOST_TENANT_ID，但呼叫者當下租戶是 OTHER_TENANT_ID
        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(OTHER_TENANT_ID);
        BookingReview existingReview = createReview(GUEST_USER_ID, HOST_TENANT_ID, true);
        when(bookingReviewRepository.findById(REVIEW_ID))
                .thenReturn(Optional.of(existingReview));

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bookingReviewService.replyToBookingReview(REVIEW_ID, SAMPLE_REPLY)
        );

        assertEquals(ErrorCode.E_1007, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Only the host can reply"));

        // 驗證沒有修改或儲存
        assertNull(existingReview.getHostReply());
        assertNull(existingReview.getHostRepliedAt());
        verify(bookingReviewRepository, never()).save(any());
    }

    @Test
    @Order(7)
    @DisplayName("replyToBookingReview: 訂房客人本人回覆（非房東）仍拋出 BusinessException（DEF-331c 修復前會誤判為房東）")
    void replyToBookingReview_byOriginalGuest_rejected() {
        // Given: 呼叫者恰好是當初訂房的客人本人，但客人不是房源所屬租戶
        TenantContext.setCurrentUser(GUEST_USER_ID);
        TenantContext.setCurrentTenant(null); // 一般買家無租戶（系統租戶以外沒有店鋪）
        BookingReview existingReview = createReview(GUEST_USER_ID, HOST_TENANT_ID, true);
        when(bookingReviewRepository.findById(REVIEW_ID))
                .thenReturn(Optional.of(existingReview));

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bookingReviewService.replyToBookingReview(REVIEW_ID, SAMPLE_REPLY)
        );

        assertEquals(ErrorCode.E_1007, exception.getErrorCode());
        verify(bookingReviewRepository, never()).save(any());
    }

    @Test
    @Order(8)
    @DisplayName("replyToBookingReview: 平台管理員可跨租戶回覆")
    void replyToBookingReview_byPlatformAdmin_succeeds() {
        // Given
        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(OTHER_TENANT_ID); // 管理員當下租戶與房源不同
        asAdmin();
        BookingReview existingReview = createReview(GUEST_USER_ID, HOST_TENANT_ID, true);
        when(bookingReviewRepository.findById(REVIEW_ID))
                .thenReturn(Optional.of(existingReview));
        when(bookingReviewRepository.save(any(BookingReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        BookingReviewDto.BookingReviewResponse response =
                bookingReviewService.replyToBookingReview(REVIEW_ID, SAMPLE_REPLY);

        // Then
        assertEquals(SAMPLE_REPLY, response.getHostReply());
        verify(bookingReviewRepository, times(1)).save(existingReview);
    }

    @Test
    @Order(9)
    @DisplayName("replyToBookingReview: 重複回覆（既有 hostReply）會被覆寫")
    void replyToBookingReview_OverwriteExistingReply() {
        // Given: 已有回覆
        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(HOST_TENANT_ID);
        BookingReview existingReview = createReview(GUEST_USER_ID, HOST_TENANT_ID, true);
        existingReview.setHostReply("舊的回覆");
        existingReview.setHostRepliedAt(Instant.now().minusSeconds(3600));
        when(bookingReviewRepository.findById(REVIEW_ID))
                .thenReturn(Optional.of(existingReview));
        when(bookingReviewRepository.save(any(BookingReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        BookingReviewDto.BookingReviewResponse response =
                bookingReviewService.replyToBookingReview(REVIEW_ID, "新的回覆");

        // Then: 回覆被覆寫
        assertEquals("新的回覆", response.getHostReply());
        assertNotNull(response.getHostRepliedAt());
        verify(bookingReviewRepository, times(1)).save(existingReview);
    }

    // ========== getBookingReviewById 測試 ==========

    @Test
    @Order(10)
    @DisplayName("getBookingReviewById: 成功取得評價詳情")
    void getBookingReviewById_Success() {
        // Given
        BookingReview existingReview = createReview(GUEST_USER_ID, HOST_TENANT_ID, true);
        when(bookingReviewRepository.findById(REVIEW_ID))
                .thenReturn(Optional.of(existingReview));

        // When
        BookingReviewDto.BookingReviewResponse response =
                bookingReviewService.getBookingReviewById(REVIEW_ID);

        // Then
        assertNotNull(response);
        assertEquals(REVIEW_ID, response.getReviewId());
        assertEquals(BOOKING_ID, response.getBookingId());
        assertEquals(5, response.getRating());
        assertEquals("很棒", response.getTitle());
        verify(bookingReviewRepository, times(1)).findById(REVIEW_ID);
    }

    @Test
    @Order(11)
    @DisplayName("getBookingReviewById: 評價不存在拋出 BusinessException")
    void getBookingReviewById_NotFound() {
        // Given
        when(bookingReviewRepository.findById(REVIEW_ID))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> bookingReviewService.getBookingReviewById(REVIEW_ID)
        );

        assertEquals(ErrorCode.E_1092, exception.getErrorCode());
    }

    // ========== 邊界測試 ==========

    @Test
    @Order(12)
    @DisplayName("replyToBookingReview: null reply 仍可儲存（業務邏輯允許）")
    void replyToBookingReview_NullReply() {
        // Given
        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(HOST_TENANT_ID);
        BookingReview existingReview = createReview(GUEST_USER_ID, HOST_TENANT_ID, true);
        when(bookingReviewRepository.findById(REVIEW_ID))
                .thenReturn(Optional.of(existingReview));
        when(bookingReviewRepository.save(any(BookingReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        BookingReviewDto.BookingReviewResponse response =
                bookingReviewService.replyToBookingReview(REVIEW_ID, null);

        // Then: 業務邏輯未阻擋（但 hostReply 為 null）
        assertNull(response.getHostReply());
        assertNotNull(response.getHostRepliedAt());
        verify(bookingReviewRepository, times(1)).save(existingReview);
    }

    @Test
    @Order(13)
    @DisplayName("replyToBookingReview: 空字串 reply 可儲存")
    void replyToBookingReview_EmptyReply() {
        // Given
        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(HOST_TENANT_ID);
        BookingReview existingReview = createReview(GUEST_USER_ID, HOST_TENANT_ID, true);
        when(bookingReviewRepository.findById(REVIEW_ID))
                .thenReturn(Optional.of(existingReview));
        when(bookingReviewRepository.save(any(BookingReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        BookingReviewDto.BookingReviewResponse response =
                bookingReviewService.replyToBookingReview(REVIEW_ID, "");

        // Then
        assertEquals("", response.getHostReply());
        verify(bookingReviewRepository, times(1)).save(existingReview);
    }

    @Test
    @Order(14)
    @DisplayName("replyToBookingReview: 超長 reply (5000 字元) 可儲存")
    void replyToBookingReview_LongReply() {
        // Given
        TenantContext.setCurrentUser(UUID.randomUUID());
        TenantContext.setCurrentTenant(HOST_TENANT_ID);
        BookingReview existingReview = createReview(GUEST_USER_ID, HOST_TENANT_ID, true);
        String longReply = "謝謝".repeat(2500); // 5000 字元
        assertEquals(5000, longReply.length());
        when(bookingReviewRepository.findById(REVIEW_ID))
                .thenReturn(Optional.of(existingReview));
        when(bookingReviewRepository.save(any(BookingReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        BookingReviewDto.BookingReviewResponse response =
                bookingReviewService.replyToBookingReview(REVIEW_ID, longReply);

        // Then
        assertEquals(5000, response.getHostReply().length());
        verify(bookingReviewRepository, times(1)).save(existingReview);
    }

    @Test
    @Order(15)
    @DisplayName("getBookingReviewById: 匿名評價的 userFullName 為 Anonymous")
    void getBookingReviewById_AnonymousUser() {
        // Given
        BookingReview existingReview = createReview(GUEST_USER_ID, HOST_TENANT_ID, true);
        when(bookingReviewRepository.findById(REVIEW_ID))
                .thenReturn(Optional.of(existingReview));

        // When
        BookingReviewDto.BookingReviewResponse response =
                bookingReviewService.getBookingReviewById(REVIEW_ID);

        // Then: 匿名用戶的 userFullName 應為 Anonymous
        assertEquals("Anonymous", response.getUserFullName());
        assertTrue(response.getIsAnonymous());
    }
}
