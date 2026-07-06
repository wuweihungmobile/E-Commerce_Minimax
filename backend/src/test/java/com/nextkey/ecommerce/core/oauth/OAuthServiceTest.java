package com.nextkey.ecommerce.core.oauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import com.nextkey.ecommerce.api.dto.OAuthDto;
import com.nextkey.ecommerce.domain.model.user.OAuthProvider;
import com.nextkey.ecommerce.domain.repository.OAuthAccountRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

/**
 * OAuthService 單元測試（Sprint 78 US-001）。
 *
 * <p>背景：{@link OAuthService} 是「多 Sprint 測試強化計劃」剩餘模組之一，先前完全沒有單元測試。
 * 探查後發現：{@code exchangeCodeForUserInfo}（兩個 public 方法 handleOAuthLogin /
 * linkOAuthAccount 的第一步）目前是**未完成的 stub**，無條件拋出
 * {@link UnsupportedOperationException}（見程式碼註解「模擬實現」，此現況已記錄於
 * docs/06_quality/TECHNICAL_DEBT_TODO_SCAN.md 與 docs/04_planning/PRODUCT_BACKLOG.md
 * 的 P3 技術債項目「OAuth2 / KYC 實名」，非本 Sprint 新發現）。
 *
 * <p>因此 {@code findOrCreateOAuthUser}（含帳號連結/擁有權相關邏輯：既有 OAuth 帳戶查找、
 * email 既有帳號自動連結、新用戶建立與 tenant 指派）與 {@code linkOAuthAccount} 內的
 * provider_user_id 衝突檢查，在目前的正式接線下都是**無法從 public API 觸及的死碼**——
 * 呼叫者永遠會在觸及這些邏輯前就收到 {@link UnsupportedOperationException}。
 * 本測試類別的目標因此聚焦於兩點：
 * (1) 確認兩個 public 方法在目前狀態下的「fail-closed」行為——不論輸入為何，皆立即失敗且
 *     不會對任何 Repository 產生副作用（不會意外建立/連結帳號）；
 * (2) 確認 {@code linkOAuthAccount} 的擁有權設計本身正確：userId 由呼叫端
 *     （{@code OAuthController}）從已認證的 {@code UserPrincipal} 取得，並非取自 request body，
 *     不存在「代他人連結 OAuth 帳號」的 IDOR 風險（此為 Controller 層設計，Service 層僅單純信任
 *     呼叫端傳入的 userId，此處以測試驗證 Service 對外顯示的行為不會因不同 userId 而有差異，
 *     不隱含地信任 request 中不存在的欄位）。
 *
 * <p>結論：未發現需要在本 Sprint 修復的擁有權/租戶檢查缺口。OAuth 特有的安全考量
 * （state/CSRF 防護、redirect_uri 白名單驗證、token 交換後的租戶/使用者綁定）目前皆
 * 因整合尚未實作而無法評估——待未來實際串接 OAuth Provider API 時，須重新針對這些項目
 * 進行安全審查（尤其 {@code OAuthDto.AuthRequest}/{@code LinkRequest} 目前完全沒有
 * {@code state} 欄位，屆時需一併補上 CSRF 防護）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthService 單元測試（Sprint 78）")
class OAuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OAuthAccountRepository oAuthAccountRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private RestTemplate restTemplate;

    @InjectMocks
    private OAuthService oAuthService;

    // ========== handleOAuthLogin ==========

    @Test
    @DisplayName("handleOAuthLogin：GOOGLE provider 合法輸入 → 拋 UnsupportedOperationException（尚未實作真實 OAuth 交換，記錄現況非本 Sprint 修復範圍），且不觸碰任何 Repository")
    void handleOAuthLogin_google_throwsUnsupported_noSideEffects() {
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GOOGLE)
                .code("valid-auth-code")
                .redirectUri("https://app.example.com/oauth/callback")
                .build();

        assertThatThrownBy(() -> oAuthService.handleOAuthLogin(request))
                .isInstanceOf(UnsupportedOperationException.class);

        verifyNoInteractions(userRepository, oAuthAccountRepository, tenantRepository, jwtTokenService);
    }

    @Test
    @DisplayName("handleOAuthLogin：GITHUB provider 合法輸入 → 同樣拋 UnsupportedOperationException，且不觸碰任何 Repository（確認行為不因 provider 而異）")
    void handleOAuthLogin_github_throwsUnsupported_noSideEffects() {
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GITHUB)
                .code("valid-auth-code")
                .redirectUri("https://app.example.com/oauth/callback")
                .build();

        assertThatThrownBy(() -> oAuthService.handleOAuthLogin(request))
                .isInstanceOf(UnsupportedOperationException.class);

        verifyNoInteractions(userRepository, oAuthAccountRepository, tenantRepository, jwtTokenService);
    }

    // ========== linkOAuthAccount ==========

    @Test
    @DisplayName("linkOAuthAccount：合法輸入 → 拋 UnsupportedOperationException，且不會在檢查 provider_user_id 衝突或寫入 OAuth 帳戶前就產生任何 Repository 副作用（fail-closed，不會有帳號被誤連結）")
    void linkOAuthAccount_throwsUnsupported_beforeAnyRepositoryWrite() {
        UUID userId = UUID.randomUUID();
        OAuthDto.LinkRequest request = OAuthDto.LinkRequest.builder()
                .provider(OAuthProvider.GOOGLE)
                .code("valid-auth-code")
                .redirectUri("https://app.example.com/oauth/callback")
                .build();

        assertThatThrownBy(() -> oAuthService.linkOAuthAccount(userId, request))
                .isInstanceOf(UnsupportedOperationException.class);

        verifyNoInteractions(userRepository, oAuthAccountRepository, tenantRepository, jwtTokenService);
    }

    @Test
    @DisplayName("linkOAuthAccount：不同 userId 的行為一致（皆 fail-closed），確認 Service 對 userId 沒有做出未預期的分支判斷")
    void linkOAuthAccount_differentUserId_stillThrowsUnsupported() {
        UUID anotherUserId = UUID.randomUUID();
        OAuthDto.LinkRequest request = OAuthDto.LinkRequest.builder()
                .provider(OAuthProvider.GITHUB)
                .code("another-auth-code")
                .redirectUri(null)
                .build();

        assertThatThrownBy(() -> oAuthService.linkOAuthAccount(anotherUserId, request))
                .isInstanceOf(UnsupportedOperationException.class);

        verifyNoInteractions(userRepository, oAuthAccountRepository, tenantRepository, jwtTokenService);
    }
}
