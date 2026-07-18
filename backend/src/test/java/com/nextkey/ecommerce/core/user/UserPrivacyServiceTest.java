package com.nextkey.ecommerce.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nextkey.ecommerce.api.dto.UserDataExportResponse;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.AddressRepository;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.NotificationRepository;
import com.nextkey.ecommerce.domain.repository.OAuthAccountRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.ReviewRepository;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.UserNotificationPreferenceRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.review.BookingReviewRepository;
import com.nextkey.ecommerce.domain.repository.support.SupportTicketRepository;
import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * UserPrivacyService 單元測試（Sprint 94，PRD §1.5.1 會員資料 Export + 帳戶刪除）。
 *
 * <p>業務決策（使用者已拍板）：僅 BUYER 可自助刪除；有未結案訂單/訂房時封鎖刪除。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserPrivacyService 單元測試（Sprint 94）")
class UserPrivacyServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BookingReviewRepository bookingReviewRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private UserNotificationPreferenceRepository notificationPreferenceRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private SupportTicketRepository supportTicketRepository;
    @Mock
    private OAuthAccountRepository oAuthAccountRepository;
    @Mock
    private TenantMemberRepository tenantMemberRepository;
    @Mock
    private RefreshTokenService refreshTokenService;

    private UserPrivacyService userPrivacyService;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userPrivacyService = new UserPrivacyService(
                userRepository, orderRepository, bookingRepository, reviewRepository, bookingReviewRepository,
                addressRepository, notificationPreferenceRepository, notificationRepository, supportTicketRepository,
                oAuthAccountRepository, tenantMemberRepository, refreshTokenService);
        TenantContext.setCurrentUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private User buyerOf(final UUID id) {
        return User.builder()
                .id(id)
                .email("buyer@example.com")
                .fullName("王小明")
                .phone("0912345678")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();
    }

    // ========== exportMyData ==========

    @Test
    @DisplayName("exportMyData：彙整個人資料各分類並回傳，含已知限制說明")
    void exportMyData_aggregatesAllCategories() {
        User user = buyerOf(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(bookingRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(bookingReviewRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(addressRepository.findByUserIdOrderByIsDefaultDescUpdatedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());
        when(notificationPreferenceRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(supportTicketRepository.findByCustomerIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(oAuthAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(tenantMemberRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());

        UserDataExportResponse response = userPrivacyService.exportMyData();

        assertThat(response.getProfile().getEmail()).isEqualTo("buyer@example.com");
        assertThat(response.getProfile().getRole()).isEqualTo("BUYER");
        assertThat(response.getKnownLimitations()).isNotEmpty();
        assertThat(response.getExportedAt()).isNotNull();
    }

    // ========== deleteMyAccount ==========

    @Test
    @DisplayName("deleteMyAccount：BUYER 且無未結案交易時，成功匿名化並清除關聯資料")
    void deleteMyAccount_eligibleBuyer_anonymizesAndCleansUp() {
        User user = buyerOf(USER_ID);
        String originalEmail = user.getEmail();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(orderRepository.existsByUserIdAndStatusNotIn(eq(USER_ID), anyList())).thenReturn(false);
        when(bookingRepository.existsByUserIdAndStatusNotIn(eq(USER_ID), anyList())).thenReturn(false);

        userPrivacyService.deleteMyAccount();

        assertThat(user.getEmail()).isNotEqualTo(originalEmail).endsWith("@anonymized.local");
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getFullName()).isEqualTo("已刪除的使用者");
        assertThat(user.getPhone()).isNull();
        assertThat(user.getStatus()).isEqualTo("DELETED");
        verify(userRepository).save(user);
        verify(addressRepository).deleteByUserId(USER_ID);
        verify(oAuthAccountRepository).deleteByUserId(USER_ID);
        verify(refreshTokenService).blacklistAllRefreshTokens(USER_ID);
    }

    @Test
    @DisplayName("deleteMyAccount：非 BUYER 角色（如 STORE_OWNER）拒絕自助刪除")
    void deleteMyAccount_nonBuyerRole_throwsE1009() {
        User storeOwner = buyerOf(USER_ID);
        storeOwner.setRole(User.UserRole.STORE_OWNER);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(storeOwner));

        assertThatThrownBy(() -> userPrivacyService.deleteMyAccount())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1009);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteMyAccount：尚有未結案訂單時拒絕刪除")
    void deleteMyAccount_hasActiveOrder_throwsE1010() {
        User user = buyerOf(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(orderRepository.existsByUserIdAndStatusNotIn(eq(USER_ID), anyList())).thenReturn(true);

        assertThatThrownBy(() -> userPrivacyService.deleteMyAccount())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1010);

        verify(userRepository, never()).save(any());
        verify(addressRepository, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("deleteMyAccount：尚有未結案訂房時拒絕刪除")
    void deleteMyAccount_hasActiveBooking_throwsE1010() {
        User user = buyerOf(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(orderRepository.existsByUserIdAndStatusNotIn(eq(USER_ID), anyList())).thenReturn(false);
        when(bookingRepository.existsByUserIdAndStatusNotIn(eq(USER_ID), anyList())).thenReturn(true);

        assertThatThrownBy(() -> userPrivacyService.deleteMyAccount())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1010);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteMyAccount：找不到使用者時拋 E_1006")
    void deleteMyAccount_userNotFound_throwsE1006() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userPrivacyService.deleteMyAccount())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1006);
    }
}
