package com.nextkey.ecommerce.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.email.EmailDeliveryException;
import com.nextkey.ecommerce.infrastructure.email.EmailSender;
import com.nextkey.ecommerce.infrastructure.security.AccountTokenService;
import com.nextkey.ecommerce.infrastructure.security.AccountTokenService.Purpose;
import com.nextkey.ecommerce.infrastructure.security.LoginAttemptService;
import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * Sprint 204：忘記密碼、重設密碼、Email 驗證（FRD US-M03-006／007，PRD §7.4.2）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Sprint 204: AccountSecurityService")
class AccountSecurityServiceTest {

    private static final String EMAIL = "member@example.com";
    private static final String TOKEN = "tok-abc";
    private static final String NEW_PASSWORD = "NewPass123";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccountTokenService tokenService;
    @Mock
    private EmailSender emailSender;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AccountSecurityService service;

    private final UUID userId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://app.test/");
        user = User.builder()
                .id(userId)
                .email(EMAIL)
                .passwordHash("$hash")
                .status("ACTIVE")
                .emailVerified(false)
                .build();
    }

    @Nested
    @DisplayName("US-M03-006 申請重設連結")
    class RequestPasswordReset {

        @Test
        @DisplayName("AC-M03-006-1: 有效帳號 → 簽發 token 並寄出含連結的信（基底網址結尾斜線不會變成雙斜線）")
        void sendsLinkToActivePasswordAccount() {
            when(userRepository.findByEmailAndStatus(EMAIL, "ACTIVE")).thenReturn(Optional.of(user));
            when(tokenService.tryAcquireSendSlot(Purpose.PASSWORD_RESET, userId)).thenReturn(true);
            when(tokenService.issue(Purpose.PASSWORD_RESET, userId)).thenReturn(TOKEN);

            service.requestPasswordReset(EMAIL);

            ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
            verify(emailSender).send(eq(EMAIL), anyString(), body.capture());
            assertThat(body.getValue()).contains("http://app.test/reset-password?token=" + TOKEN);
        }

        @Test
        @DisplayName("Email 不存在 → 不簽發、不寄信、不拋例外（回應與成功相同）")
        void unknownEmail_doesNothing() {
            when(userRepository.findByEmailAndStatus(EMAIL, "ACTIVE")).thenReturn(Optional.empty());

            assertThatCode(() -> service.requestPasswordReset(EMAIL)).doesNotThrowAnyException();

            verifyNoInteractions(tokenService, emailSender);
        }

        @Test
        @DisplayName("僅以 OAuth 登入、沒有密碼的帳號 → 不寄信")
        void oauthOnlyAccount_doesNothing() {
            user.setPasswordHash(null);
            when(userRepository.findByEmailAndStatus(EMAIL, "ACTIVE")).thenReturn(Optional.of(user));

            service.requestPasswordReset(EMAIL);

            verifyNoInteractions(tokenService, emailSender);
        }

        @Test
        @DisplayName("AC-M03-006-4: 冷卻中 → 不簽發、不寄信、不拋例外")
        void cooldown_suppressesMail() {
            when(userRepository.findByEmailAndStatus(EMAIL, "ACTIVE")).thenReturn(Optional.of(user));
            when(tokenService.tryAcquireSendSlot(Purpose.PASSWORD_RESET, userId)).thenReturn(false);

            assertThatCode(() -> service.requestPasswordReset(EMAIL)).doesNotThrowAnyException();

            verify(tokenService, never()).issue(any(), any());
            verifyNoInteractions(emailSender);
        }

        @Test
        @DisplayName("寄信失敗 → 吞掉（不可讓失敗與否成為帳號是否存在的訊號）")
        void deliveryFailure_isSwallowed() {
            when(userRepository.findByEmailAndStatus(EMAIL, "ACTIVE")).thenReturn(Optional.of(user));
            when(tokenService.tryAcquireSendSlot(Purpose.PASSWORD_RESET, userId)).thenReturn(true);
            when(tokenService.issue(Purpose.PASSWORD_RESET, userId)).thenReturn(TOKEN);
            doThrow(new EmailDeliveryException("smtp down", null)).when(emailSender).send(any(), any(), any());

            assertThatCode(() -> service.requestPasswordReset(EMAIL)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("US-M03-006 重設密碼")
    class ResetPassword {

        @Test
        @DisplayName("AC-M03-006-2: 有效連結 → 密碼改為新雜湊、所有 Refresh Token 失效、登入鎖定解除")
        void resetsPasswordAndRevokesSessions() {
            when(tokenService.consume(Purpose.PASSWORD_RESET, TOKEN)).thenReturn(Optional.of(userId));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("$newhash");

            service.resetPassword(TOKEN, NEW_PASSWORD);

            assertThat(user.getPasswordHash()).isEqualTo("$newhash");
            verify(userRepository).save(user);
            verify(refreshTokenService).blacklistAllRefreshTokens(userId);
            verify(loginAttemptService).resetAttempts(EMAIL);
        }

        @Test
        @DisplayName("AC-M03-006-3: 連結無效／已用／過期 → E-1011，完全不動任何資料")
        void invalidToken_changesNothing() {
            when(tokenService.consume(Purpose.PASSWORD_RESET, TOKEN)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resetPassword(TOKEN, NEW_PASSWORD))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_1011));

            verifyNoInteractions(userRepository, passwordEncoder, refreshTokenService, loginAttemptService);
        }

        @Test
        @DisplayName("連結有效但帳號已停用 → E-1011，不改密碼")
        void inactiveAccount_isRejected() {
            user.setStatus("SUSPENDED");
            when(tokenService.consume(Purpose.PASSWORD_RESET, TOKEN)).thenReturn(Optional.of(userId));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.resetPassword(TOKEN, NEW_PASSWORD))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_1011));

            verifyNoInteractions(passwordEncoder, refreshTokenService);
            assertThat(user.getPasswordHash()).isEqualTo("$hash");
        }
    }

    @Nested
    @DisplayName("US-M03-007 寄送驗證信")
    class SendEmailVerification {

        @Test
        @DisplayName("尚未驗證 → 簽發 token 並寄出含驗證連結的信")
        void sendsLinkToUnverifiedUser() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(tokenService.tryAcquireSendSlot(Purpose.EMAIL_VERIFY, userId)).thenReturn(true);
            when(tokenService.issue(Purpose.EMAIL_VERIFY, userId)).thenReturn(TOKEN);

            service.sendEmailVerification(userId);

            ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
            verify(emailSender).send(eq(EMAIL), anyString(), body.capture());
            assertThat(body.getValue()).contains("http://app.test/verify-email?token=" + TOKEN);
        }

        @Test
        @DisplayName("AC-M03-007-3: 已驗證 → 不寄信；冷卻中 → 不寄信")
        void alreadyVerifiedOrCoolingDown_sendsNothing() {
            user.setEmailVerified(true);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            service.sendEmailVerification(userId);
            verifyNoInteractions(tokenService, emailSender);

            user.setEmailVerified(false);
            when(tokenService.tryAcquireSendSlot(Purpose.EMAIL_VERIFY, userId)).thenReturn(false);

            service.sendEmailVerification(userId);
            verifyNoInteractions(emailSender);
        }

        @Test
        @DisplayName("使用者要求重寄時寄信失敗 → E-9905（服務暫時無法使用），讓使用者知道沒寄出")
        void deliveryFailure_surfacesToUser() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(tokenService.tryAcquireSendSlot(Purpose.EMAIL_VERIFY, userId)).thenReturn(true);
            when(tokenService.issue(Purpose.EMAIL_VERIFY, userId)).thenReturn(TOKEN);
            doThrow(new EmailDeliveryException("smtp down", null)).when(emailSender).send(any(), any(), any());

            assertThatThrownBy(() -> service.sendEmailVerification(userId))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_9905));
        }

        @Test
        @DisplayName("AC-M03-007-1: 註冊後寄信失敗或 Redis 故障 → 吞掉，不影響註冊")
        void quietVariant_neverThrowsOnInfrastructureFailure() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(tokenService.tryAcquireSendSlot(Purpose.EMAIL_VERIFY, userId))
                    .thenThrow(new QueryTimeoutException("redis down"));

            assertThatCode(() -> service.sendEmailVerificationQuietly(userId)).doesNotThrowAnyException();

            // 已被設定為丟例外的方法，重新設定回傳值必須用 doReturn：when(...) 會先呼叫一次而觸發例外
            doReturn(true).when(tokenService).tryAcquireSendSlot(Purpose.EMAIL_VERIFY, userId);
            when(tokenService.issue(Purpose.EMAIL_VERIFY, userId)).thenReturn(TOKEN);
            doThrow(new EmailDeliveryException("smtp down", null)).when(emailSender).send(any(), any(), any());

            assertThatCode(() -> service.sendEmailVerificationQuietly(userId)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("US-M03-007 完成驗證")
    class VerifyEmail {

        @Test
        @DisplayName("AC-M03-007-2: 有效連結 → emailVerified 變 true 並儲存")
        void marksEmailVerified() {
            when(tokenService.consume(Purpose.EMAIL_VERIFY, TOKEN)).thenReturn(Optional.of(userId));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            service.verifyEmail(TOKEN);

            assertThat(user.getEmailVerified()).isTrue();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("已驗證的會員再開一次連結 → 成功但不重複儲存")
        void alreadyVerified_isIdempotent() {
            user.setEmailVerified(true);
            when(tokenService.consume(Purpose.EMAIL_VERIFY, TOKEN)).thenReturn(Optional.of(userId));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            service.verifyEmail(TOKEN);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("連結無效 → E-1011")
        void invalidToken_isRejected() {
            when(tokenService.consume(Purpose.EMAIL_VERIFY, TOKEN)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifyEmail(TOKEN))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_1011));

            verifyNoInteractions(userRepository);
        }
    }

    @Nested
    @DisplayName("US-M17-001／US-M03-007 開店申請前置條件")
    class StoreApplicationPrecondition {

        @Test
        @DisplayName("寄信服務無法真正寄出信 → 前置條件不生效，連使用者都不查（否則會把所有申請者鎖在門外）")
        void notEnforcedWhenMailCannotBeDelivered() {
            when(emailSender.canDeliver()).thenReturn(false);

            assertThatCode(() -> service.requireVerifiedEmailForStoreApplication(userId))
                    .doesNotThrowAnyException();

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("AC-M03-007-4: 寄信可用且尚未驗證 → E-1012")
        void unverifiedUserIsBlocked() {
            when(emailSender.canDeliver()).thenReturn(true);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.requireVerifiedEmailForStoreApplication(userId))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_1012));
        }

        @Test
        @DisplayName("寄信可用且已驗證 → 放行")
        void verifiedUserPasses() {
            user.setEmailVerified(true);
            when(emailSender.canDeliver()).thenReturn(true);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatCode(() -> service.requireVerifiedEmailForStoreApplication(userId))
                    .doesNotThrowAnyException();
            verifyNoMoreInteractions(tokenService);
        }
    }
}
