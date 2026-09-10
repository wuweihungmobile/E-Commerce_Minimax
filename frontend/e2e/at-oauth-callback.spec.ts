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
