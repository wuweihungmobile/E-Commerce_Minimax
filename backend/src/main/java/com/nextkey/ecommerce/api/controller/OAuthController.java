package com.nextkey.ecommerce.api.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.AuthResponse;
import com.nextkey.ecommerce.api.dto.OAuthDto;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.oauth.OAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@Slf4j
@RestController
@RequestMapping("/v2/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;

    /**
     * OAuth 登入/註冊
     * 交換授權碼並返回 JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> oauthLogin(
            @Valid @RequestBody OAuthDto.AuthRequest request) {
        log.info("OAuth login request for provider: {}", request.getProvider());
        AuthResponse response = oAuthService.handleOAuthLogin(request);
        return ResponseEntity.ok(ApiResponse.success("OAuth login successful", response));
    }

    /**
     * 連結 OAuth 帳戶到現有用戶
     * 需要已登入的用戶
     */
    @PostMapping("/link")
    public ResponseEntity<ApiResponse<Void>> linkOAuthAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OAuthDto.LinkRequest request) {
        log.info("Link OAuth account request for user: {} provider: {}", principal.getUserId(), request.getProvider());
        oAuthService.linkOAuthAccount(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("OAuth account linked successfully", null));
    }
}