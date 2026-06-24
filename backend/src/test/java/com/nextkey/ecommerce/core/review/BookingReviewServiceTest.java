package com.nextkey.ecommerce.core.review;

import com.nextkey.ecommerce.api.dto.BookingReviewDto;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.review.BookingReview;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BookingReviewService 單元測試
 *
 * 測試範圍（Sprint 16 US-001）：
 * - replyToBookingReview: 商家回覆預訂評價（核心 Sprint 15 功能）
 *   - 成功場景：房東回覆
 *   - 失敗場景：評價不存在、非房東嘗試回覆
 * - getBookingReviewById: 取得評價詳情
 *   - 成功場景：找到評價
 *   - 失敗場景：評價不存在
 * - 邊界測試：null reply、空字串 reply、超長 reply
 *
 * 設計重點：
 * - 使用 @ExtendWith(MockitoExtension.class) 純 Service 層測試
 * - 透過 Mockito.mockStatic 處理 TenantContext 靜態方法
 * - 覆蓋率目標：>= 80%
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("BookingReviewService 單元測試 (Sprint 16 US-001)")
class BookingReviewServiceTest {

    @Mock
    private BookingReviewRepository bookingReviewRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookingReviewService bookingReviewService;

    private static final UUID HOST_USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final String SAMPLE_REPLY = "感謝您的入住，期待您再次蒞臨！";

    @AfterEach
    void cleanup() {
        // 確保 ThreadLocal 被清理，避免測試間污染
        TenantContext.clear();
    }

    // ========== Helper Methods ==========

    private Booking createBooking(UUID hostId) {
        return Booking.builder()
                .id(BOOKING_ID)
                .userId(hostId)
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
    private BookingReview createReview(UUID hostId, boolean isAnonymous) {
        Booking booking = createBooking(hostId);
        BookingReview review = BookingReview.builder()
                .booking(booking)
                .userId(OTHER_USER_ID)
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

    // ========== replyToBookingReview 測試 ==========

    @Test
    @Order(1)
    @DisplayName("replyToBookingReview: 房東成功回覆評價")
    void replyToBookingReview_Success() {
        // Given
        TenantContext.setCurrentUser(HOST_USER_ID);
        BookingReview existingReview = createReview(HOST_USER_ID, true);
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
    @Order(2)
    @DisplayName("replyToBookingReview: 評價不存在時拋出 BusinessException")
    void replyToBookingReview_ReviewNotFound() {
        // Given
        TenantContext.setCurrentUser(HOST_USER_ID);
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
    @Order(3)
    @DisplayName("replyToBookingReview: 非房東嘗試回覆拋出 BusinessException")
    void replyToBookingReview_NotHost() {
        // Given: 評價的房東是 HOST_USER_ID，但當前用戶是 OTHER_USER_ID
        TenantContext.setCurrentUser(OTHER_USER_ID);
        BookingReview existingReview = createReview(HOST_USER_ID, true);
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
    @Order(4)
    @DisplayName("replyToBookingReview: 重複回覆（既有 hostReply）會被覆寫")
    void replyToBookingReview_OverwriteExistingReply() {
        // Given: 已有回覆
        TenantContext.setCurrentUser(HOST_USER_ID);
        BookingReview existingReview = createReview(HOST_USER_ID, true);
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
    @Order(5)
    @DisplayName("getBookingReviewById: 成功取得評價詳情")
    void getBookingReviewById_Success() {
        // Given
        BookingReview existingReview = createReview(HOST_USER_ID, true);
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
    @Order(6)
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
    @Order(7)
    @DisplayName("replyToBookingReview: null reply 仍可儲存（業務邏輯允許）")
    void replyToBookingReview_NullReply() {
        // Given
        TenantContext.setCurrentUser(HOST_USER_ID);
        BookingReview existingReview = createReview(HOST_USER_ID, true);
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
    @Order(8)
    @DisplayName("replyToBookingReview: 空字串 reply 可儲存")
    void replyToBookingReview_EmptyReply() {
        // Given
        TenantContext.setCurrentUser(HOST_USER_ID);
        BookingReview existingReview = createReview(HOST_USER_ID, true);
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
    @Order(9)
    @DisplayName("replyToBookingReview: 超長 reply (5000 字元) 可儲存")
    void replyToBookingReview_LongReply() {
        // Given
        TenantContext.setCurrentUser(HOST_USER_ID);
        BookingReview existingReview = createReview(HOST_USER_ID, true);
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
    @Order(10)
    @DisplayName("getBookingReviewById: 匿名評價的 userFullName 為 Anonymous")
    void getBookingReviewById_AnonymousUser() {
        // Given
        BookingReview existingReview = createReview(HOST_USER_ID, true);
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
