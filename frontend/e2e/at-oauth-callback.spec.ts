import { test, expect } from '@playwright/test';

/**
 * AT-OAUTH-CALLBACK: OAuth callback 頁錯誤處理 E2E 測試（Sprint 153，item 13）。
 *
 * 本測試環境未設定 NEXT_PUBLIC_OAUTH_GOOGLE_CLIENT_ID/NEXT_PUBLIC_OAUTH_GITHUB_CLIENT_ID
 * （真實憑證需使用者於部署環境另行設定，見 SPRINT_153_PLAN.md），因此 /login、/account
 * 的「使用 Google/GitHub 登入／連結」按鈕不會渲染，也無法端對端測試完整的登入成功路徑
 * （需要真實 provider 授權頁與真實 code）。本測試改為驗證 callback 頁在缺少
 * code/state（例如使用者手動輸入網址、或 provider 回傳未帶預期參數）時的錯誤處理——
 * 這段邏輯不依賴真實 provider，可獨立驗證。
 */
test.describe('AT-OAUTH-CALLBACK: OAuth callback 頁錯誤處理', () => {
  test('缺少 code/state 直接造訪 callback 頁 → 顯示錯誤訊息，不崩潰', async ({ page }) => {
    await page.goto('/oauth/callback/google');

    await expect(page.getByText('登入失敗')).toBeVisible();
    await expect(page.getByText('登入驗證失敗，請重新嘗試')).toBeVisible();
  });

  test('provider 回傳 error 參數（使用者取消授權）→ 顯示對應錯誤訊息', async ({ page }) => {
    await page.goto('/oauth/callback/google?error=access_denied');

    await expect(page.getByText('登入失敗')).toBeVisible();
    await expect(page.getByText('已取消或拒絕授權')).toBeVisible();
  });

  test('不支援的 provider 路徑（如 /oauth/callback/facebook）→ 顯示錯誤，不崩潰', async ({ page }) => {
    await page.goto('/oauth/callback/facebook?code=abc&state=xyz');

    await expect(page.getByText('登入失敗')).toBeVisible();
    await expect(page.getByText('不支援的登入服務')).toBeVisible();
  });
});

/**
 * 登入成功後的落地頁（Sprint 301，DEF-409）：OAuth 登入不經過本站登入頁，與密碼登入一樣依角色分流
 * （BUYER→/、店家類→/dashboard、管理員→/admin）。provider 授權頁雖然走不完，但「成功之後去哪」不依賴 provider：
 * 預先在 sessionStorage 種好 state，並以 page.route 假造後端的 OAuth 登入回應即可驅動。
 * 假 token 之後打任何 API 都會 401 並被踢回登入頁，所以不斷言「最後」停在哪，只看離開 callback 頁後的第一個網址。
 */
test.describe('AT-OAUTH-CALLBACK: 登入成功後依角色落地（DEF-409）', () => {
  const landings: Array<[string, string]> = [
    ['BUYER', '/'],
    ['STORE_OWNER', '/dashboard'],
    ['ADMIN', '/admin'],
  ];
  for (const [role, landing] of landings) {
    test(`E2E-OAUTH-LANDING: 角色 ${role} 以 OAuth 登入成功 → 離開 callback 頁後的第一個網址是 ${landing}`, async ({ page }) => {
      await page.addInitScript(() => {
        sessionStorage.setItem('oauth_state', 'e2e-state');
        sessionStorage.setItem('oauth_intent', 'login');
      });
      await page.route('**/v2/auth/oauth/login', (route) =>
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            data: {
              accessToken: 'fake-access-token',
              refreshToken: 'fake-refresh-token',
              user: { id: '00000000-0000-0000-0000-000000000001', email: 'oauth-e2e@example.com', fullName: 'OAuth 測試', role },
            },
          }),
        }),
      );
      const visited: string[] = [];
      page.on('framenavigated', (frame) => {
        if (frame === page.mainFrame()) visited.push(new URL(frame.url()).pathname);
      });

      await page.goto('/oauth/callback/google?code=e2e-code&state=e2e-state');

      // 修復前：一律 /dashboard（買家與管理員都走錯）
      await expect
        .poll(() => visited.find((p) => !p.startsWith('/oauth/callback')), { timeout: 20000 })
        .toBe(landing);
    });
  }
});
