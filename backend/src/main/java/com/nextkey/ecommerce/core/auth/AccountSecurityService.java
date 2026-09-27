package com.nextkey.ecommerce.core.auth;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 忘記密碼、重設密碼、Email 驗證（Sprint 204，DEF-252／253；FRD US-M03-006／007、PRD §7.4.2）。
 *
 * <p>與 {@link AuthService} 分開：這裡的流程都由「一封信裡的一次性連結」驅動，性質與登入／註冊不同，
 * 且不依賴 {@code AuthService} 的任何內部狀態。
 *
 * <p><b>時序側通道防護（Sprint 211，DEF-290）</b>：申請重設連結時，「帳號存在」的路徑本會比「不存在」
 * 多做一次寄信；Sprint 209 接上真實 SMTP（阻塞式網路呼叫）後，這個差異會被放大成可觀測的時序側通道，
 * 洩漏 Email 是否已註冊。故此處把實際寄信動作丟給 {@link #passwordResetMailExecutor} 背景執行，
 * {@link #requestPasswordReset} 回應前不等待寄信完成，兩種路徑的回應時間不再有差異。回應內容本身
 * 兩種情況完全相同。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSecurityService {

    private static final String ACTIVE = "ACTIVE";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountTokenService tokenService;
    private final EmailSender emailSender;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;

    /**
     * 只用來把「重設密碼」的實際寄信動作移出請求執行緒（見上方時序側通道說明）。虛擬執行緒成本極低，
     * 這裡是唯一用途，不需要納入 Spring 生命週期管理——最差情況等同目前寄信失敗即放棄的既有行為。
     */
    private final Executor passwordResetMailExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    /**
     * 申請密碼重設連結。<b>回應不可因 Email 是否存在、是否冷卻中、寄信是否成功而不同</b>——
     * 呼叫端一律回同樣的成功訊息，這裡也就不回傳任何結果。
     */
    public void requestPasswordReset(final String email) {
        userRepository.findByEmailAndStatus(email, ACTIVE)
                // 僅以 OAuth 登入、沒有密碼的帳號沒有可重設的東西
                .filter(user -> user.getPasswordHash() != null)
                .ifPresentOrElse(this::sendPasswordResetMail,
                        () -> log.info("Password reset requested for an unknown or non-password account"));
    }

    private void sendPasswordResetMail(final User user) {
        if (!tokenService.tryAcquireSendSlot(Purpose.PASSWORD_RESET, user.getId())) {
            log.info("Password reset mail suppressed by cooldown: user={}", user.getId());
            return;
        }
        String token = tokenService.issue(Purpose.PASSWORD_RESET, user.getId());
        String body = "您好，我們收到了重設密碼的申請。\n\n"
                + "請於 30 分鐘內點擊下列連結設定新密碼（連結只能使用一次）：\n"
                + linkTo("/reset-password", token) + "\n\n"
                + "如果這不是您本人的操作，請忽略這封信，您的密碼不會改變。";
        // 實際寄信丟到背景執行緒：呼叫端不可因為這裡阻塞而讓回應時間洩漏帳號是否存在。
        passwordResetMailExecutor.execute(() -> {
            try {
                emailSender.send(user.getEmail(), "【E-Commerce】重設您的密碼", body);
            } catch (EmailDeliveryException e) {
                // 不可讓寄信失敗改變回應（否則失敗與否成為帳號是否存在的訊號）
                log.error("Password reset mail delivery failed: user={}", user.getId(), e);
            }
        });
    }

    /**
     * 以重設連結設定新密碼。新密碼的格式驗證（8～128 字元、含大小寫與數字）由 DTO 負責，
     * 在進到這裡之前就已完成——格式不合時連結不會被消耗。
     *
     * @throws BusinessException {@code E-1011} 連結無效、已使用、已過期、被新連結取代，或帳號已不可用
     */
    @Transactional
    public void resetPassword(final String token, final String newPassword) {
        UUID userId = tokenService.consume(Purpose.PASSWORD_RESET, token)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1011));
        User user = userRepository.findById(userId)
                .filter(found -> ACTIVE.equals(found.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1011));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // 密碼被重設，代表舊密碼可能已外洩：所有裝置的 Refresh Token 一併失效，並解除因輸錯造成的登入鎖定。
        // 已簽發的 Access Token 無法撤銷，最長仍可使用到其自然到期（FRD BR-M03-003）。
        refreshTokenService.blacklistAllRefreshTokens(userId);
        loginAttemptService.resetAttempts(user.getEmail());
        log.info("Password reset completed: user={}", userId);
    }

    /**
     * 寄出（或重寄）Email 驗證信給已登入會員。已驗證者、冷卻中都靜默略過。
     *
     * @throws BusinessException {@code E-9905} 寄信服務暫時無法使用
     */
    public void sendEmailVerification(final UUID userId) {
        try {
            dispatchEmailVerification(userId);
        } catch (EmailDeliveryException e) {
            log.error("Email verification mail delivery failed: user={}", userId, e);
            throw new BusinessException(ErrorCode.E_9905);
        }
    }

    /**
     * 註冊成功後寄驗證信。<b>寄信或 Redis 出問題都不可讓註冊失敗</b>（FRD AC-M03-007-1），
     * 使用者之後仍可自行要求重寄。
     */
    public void sendEmailVerificationQuietly(final UUID userId) {
        try {
            dispatchEmailVerification(userId);
        } catch (EmailDeliveryException | DataAccessException e) {
            log.warn("Email verification mail not sent after registration: user={}", userId, e);
        }
    }

    private void dispatchEmailVerification(final UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }
        if (!tokenService.tryAcquireSendSlot(Purpose.EMAIL_VERIFY, userId)) {
            log.info("Email verification mail suppressed by cooldown: user={}", userId);
            return;
        }
        String token = tokenService.issue(Purpose.EMAIL_VERIFY, userId);
        emailSender.send(user.getEmail(), "【E-Commerce】請驗證您的電子郵件",
                "您好，歡迎加入！\n\n"
                + "請於 24 小時內點擊下列連結完成電子郵件驗證（連結只能使用一次）：\n"
                + linkTo("/verify-email", token) + "\n\n"
                + "如果這不是您本人的操作，請忽略這封信。");
    }

    /**
     * 以驗證連結完成 Email 驗證。不需要登入：連結常在另一個瀏覽器、甚至另一台裝置上開啟。
     *
     * @throws BusinessException {@code E-1011} 連結無效、已使用、已過期或被新連結取代
     */
    @Transactional
    public void verifyEmail(final String token) {
        UUID userId = tokenService.consume(Purpose.EMAIL_VERIFY, token)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1011));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1011));
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(true);
            userRepository.save(user);
            log.info("Email verified: user={}", userId);
        }
    }

    /**
     * 開店申請的前置條件（PRD §7.4.2）：已登入的申請者必須已驗證 Email。
     *
     * <p><b>寄信服務無法真正寄出信時不檢查</b>（{@link EmailSender#canDeliver()} 為 {@code false}）：
     * 沒有信可寄，申請者就無從驗證，強制檢查只會把所有人鎖在門外。接上真實寄信服務後自動生效。
     *
     * @throws BusinessException {@code E-1012} 尚未驗證
     */
    @Transactional(readOnly = true)
    public void requireVerifiedEmailForStoreApplication(final UUID userId) {
        if (!emailSender.canDeliver()) {
            return;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BusinessException(ErrorCode.E_1012);
        }
    }

    private String linkTo(final String path, final String token) {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
        return base + path + "?token=" + token;
    }
}
