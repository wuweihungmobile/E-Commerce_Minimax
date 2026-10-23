import { Page } from '@playwright/test';

/**
 * 以「已登入買家」的身分開需要登入的買家頁面，給「用 page.route 假造 API、不走真實登入」的規格用。
 *
 * 為什麼需要：買家頁面共用的版面層有登入守門（DEF-413，Sprint 302）——沒有 accessToken 會被導去 /login?redirect=…，
 * 這些規格開的訂單、預訂、通知、結帳頁因此需要一個 token 才進得去。
 *
 * 只種 accessToken，刻意不種 refreshToken 與 user（沿用 at-room-booking 的做法）：
 * - 沒有 refreshToken：沒被 mock 到的請求打到真實後端得到 401 時，axios 攔截器不會進入刷新流程、也就不會整頁導去登入頁；
 * - 沒有 user：共用頁首只有在同時有 user 時才會去取購物車數量，不會多發沒被 mock 的請求。
 * 需要真實登入的案例請改用 helpers/auth.ts 的 registerAndLogin／loginOnly。
 */
export async function asMockLoggedInBuyer(page: Page): Promise<void> {
  await page.addInitScript(() => localStorage.setItem('accessToken', 'e2e-mock-access-token'));
}
