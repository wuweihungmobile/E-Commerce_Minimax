package com.nextkey.ecommerce.core.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.pricing.PricingService;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * BookingService.getBooking / updateBooking / cancelBooking 擁有權隔離
 * （DEF-023：booking 讀寫/取消擁有權隔離，IDOR）。
 *
 * <p>驗證：他人不可查詢/更新/取消買家預訂（E_1007，經 GlobalExceptionHandler 映射 HTTP 403）；
 * 本人通過擁有權檢查（getBooking 續走成功回應；updateBooking/cancelBooking 續走狀態檢查）；
 * admin 放行。以「狀態檢查」證明擁有權**先於**狀態觸發（updateBooking/cancelBooking 案例），
 * 比照 PaymentServiceOwnershipTest/LogisticsServiceOwnershipTest 風格。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BookingService.getBooking/updateBooking/cancelBooking 擁有權隔離（DEF-023）")
class BookingServiceOwnershipTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomCalendarRepository roomCalendarRepository;

    @Mock
    private RoomCalendarService roomCalendarService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PricingService pricingService;

    @Mock
    private FeatureToggleService featureToggleService;

    @InjectMocks
    private BookingService bookingService;

    private final UUID buyerA = UUID.randomUUID();
    private final UUID buyerB = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Booking bookingOfUser(final UUID userId, final Booking.BookingStatus status) {
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .roomListingId(UUID.randomUUID())
                .checkInDate(LocalDate.of(2026, 8, 1))
                .checkOutDate(LocalDate.of(2026, 8, 3))
                .guestCount(2)
                .status(status)
                .totalAmount(BigDecimal.valueOf(2000))
                .build();
        return booking;
    }

    // ========== getBooking ==========

    @Test
    @DisplayName("他人查詢買家預訂 → E_1007")
    void getBooking_otherUser_throwsE1007() {
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.CREATED)));
        TenantContext.setCurrentUser(buyerB);

        assertThatThrownBy(() -> bookingService.getBooking(bookingId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("本人查詢自己預訂 → 通過擁有權檢查，正常回傳")
    void getBooking_sameUser_passesOwnership() {
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.CREATED)));
        when(listingRepository.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        TenantContext.setCurrentUser(buyerA);

        BookingDto.BookingResponse response = bookingService.getBooking(bookingId);

        assertThat(response.getId()).isEqualTo(bookingId);
    }

    @Test
    @DisplayName("admin 查詢他人預訂放行")
    void getBooking_admin_bypassesOwnership() {
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.CREATED)));
        when(listingRepository.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        TenantContext.setCurrentUser(buyerB); // 非本人
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        BookingDto.BookingResponse response = bookingService.getBooking(bookingId);

        assertThat(response.getId()).isEqualTo(bookingId);
    }

    // ========== updateBooking ==========

    private BookingDto.UpdateRequest updateReq() {
        return BookingDto.UpdateRequest.builder().guestCount(3).build();
    }

    @Test
    @DisplayName("他人更新買家預訂 → E_1007（擁有權先於狀態檢查）")
    void updateBooking_otherUser_throwsE1007() {
        // booking 狀態故意設 CANCELLED（不可更新狀態）；當前使用者非本人。
        // 若得 E_1007（而非狀態錯誤 E_5010），證明擁有權檢查「先於」狀態檢查觸發。
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.CANCELLED)));
        TenantContext.setCurrentUser(buyerB);

        assertThatThrownBy(() -> bookingService.updateBooking(bookingId, updateReq()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("本人通過擁有權檢查（續走狀態檢查 → E_5010，非 E_1007）")
    void updateBooking_sameUser_passesOwnership() {
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.CANCELLED)));
        TenantContext.setCurrentUser(buyerA);

        assertThatThrownBy(() -> bookingService.updateBooking(bookingId, updateReq()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_5010);
    }

    @Test
    @DisplayName("admin 更新他人預訂放行（擁有權通過，續走狀態檢查 → E_5010）")
    void updateBooking_admin_bypassesOwnership() {
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.CANCELLED)));
        TenantContext.setCurrentUser(buyerB); // 非本人
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThatThrownBy(() -> bookingService.updateBooking(bookingId, updateReq()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_5010);
    }

    // ========== cancelBooking ==========

    @Test
    @DisplayName("他人取消買家預訂 → E_1007（擁有權先於狀態檢查）")
    void cancelBooking_otherUser_throwsE1007() {
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.CREATED)));
        TenantContext.setCurrentUser(buyerB);

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId, "test"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("本人可正常取消自己的預訂")
    void cancelBooking_sameUser_passesOwnership() {
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.CREATED)));
        TenantContext.setCurrentUser(buyerA);

        bookingService.cancelBooking(bookingId, "test");

        org.mockito.Mockito.verify(bookingRepository).save(org.mockito.ArgumentMatchers.argThat(
                b -> b.getStatus() == Booking.BookingStatus.CANCELLED));
    }

    @Test
    @DisplayName("本人取消不可取消狀態的預訂（擁有權通過，續走狀態檢查 → E_4007，非 E_1007）")
    void cancelBooking_sameUser_invalidStatus_throwsE4007() {
        // COMPLETED 為終態，OrderStateMachine.canCancel 回 false；若得 E_4007（而非 E_1007），
        // 證明擁有權檢查先於狀態檢查通過。
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.COMPLETED)));
        TenantContext.setCurrentUser(buyerA);

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId, "test"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4007);
    }

    @Test
    @DisplayName("admin 可取消他人的預訂")
    void cancelBooking_admin_bypassesOwnership() {
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(bookingOfUser(buyerA, Booking.BookingStatus.CREATED)));
        TenantContext.setCurrentUser(buyerB); // 非本人
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        bookingService.cancelBooking(bookingId, "test");

        org.mockito.Mockito.verify(bookingRepository).save(org.mockito.ArgumentMatchers.argThat(
                b -> b.getStatus() == Booking.BookingStatus.CANCELLED));
    }
}
