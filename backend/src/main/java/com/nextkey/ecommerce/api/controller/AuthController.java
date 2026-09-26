package com.nextkey.ecommerce.api.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.AuthResponse;
import com.nextkey.ecommerce.api.dto.ForgotPasswordRequest;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.LogoutRequest;
import com.nextkey.ecommerce.api.dto.LogoutResponse;
import com.nextkey.ecommerce.api.dto.RefreshTokenRequest;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.api.dto.RegisterResponse;
import com.nextkey.ecommerce.api.dto.ResetPasswordRequest;
import com.nextkey.ecommerce.api.dto.UserDataExportResponse;
import com.nextkey.ecommerce.api.dto.UserInfoResponse;
import com.nextkey.ecommerce.api.dto.VerifyEmailRequest;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.auth.AccountSecurityService;
import com.nextkey.ecommerce.core.auth.AuthService;
import com.nextkey.ecommerce.core.user.UserPrivacyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v2/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountSecurityService accountSecurityService;
    private final UserPrivacyService userPrivacyService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        log.info("Register request for email: {}", request.getEmail());
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        log.info("Refresh token request");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    /**
     * 申請密碼重設連結（Sprint 204，FRD US-M03-006）。<b>回應與 Email 是否存在、是否冷卻中、寄信是否成功完全無關</b>，
     * 且刻意不把 Email 寫進日誌：這個端點的行為不能成為帳號列舉的管道。
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        accountSecurityService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(
                "If the email is registered, a password reset link has been sent", null));
    }

    /** 以重設連結設定新密碼（Sprint 204，FRD US-M03-006）。 */
    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        accountSecurityService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Password has been reset", null));
    }

    /** 以驗證連結完成 Email 驗證（Sprint 204，FRD US-M03-007）。不需登入：連結常在另一個瀏覽器開啟。 */
    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        accountSecurityService.verifyEmail(request.getToken());
        return ResponseEntity.ok(ApiResponse.success("Email verified", null));
    }

    /** 重寄驗證信給目前登入的會員（Sprint 204，FRD US-M03-007）。已驗證或冷卻中一律回成功、不寄信。 */
    @PostMapping("/email/verify/send")
    public ResponseEntity<ApiResponse<Void>> resendEmailVerification(
            @AuthenticationPrincipal UserPrincipal principal) {
        accountSecurityService.sendEmailVerification(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Verification email requested", null));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) LogoutRequest request) {
        log.info("Logout request for user: {}", principal.getUserId());

        if (request == null) {
            request = new LogoutRequest();
        }

        authService.logout(principal.getUserId(), request);

        return ResponseEntity.ok(ApiResponse.success("Logout successful",
                LogoutResponse.builder()
                        .success(true)
                        .message("Logout successful")
                        .build()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Get current user request for: {}", principal.getUserId());
        UserInfoResponse response = authService.getCurrentUser(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Success", response));
    }

    /**
     * 會員資料匯出（PRD §1.5.1，Sprint 94 AI-2428）
     */
    @GetMapping("/me/data-export")
    public ResponseEntity<ApiResponse<UserDataExportResponse>> exportMyData(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Data export request for user: {}", principal.getUserId());
        UserDataExportResponse response = userPrivacyService.exportMyData();
        return ResponseEntity.ok(ApiResponse.success("Success", response));
    }

    /**
     * 會員自助帳戶刪除／被遺忘權（PRD §1.5.1，Sprint 94 AI-2428）
     */
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMyAccount(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Account deletion request for user: {}", principal.getUserId());
        userPrivacyService.deleteMyAccount();
        return ResponseEntity.ok(ApiResponse.success("Account deleted", null));
    }
}