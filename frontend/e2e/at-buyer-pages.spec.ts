import { test, expect } from '@playwright/test';
import { registerAndLogin } from './helpers/auth';

/**
 * AT-BUYER-PAGES: 買家頁面 E2E（Sprint 32 US-002 / AI-1602）
 *
 * 驗證 EPIC-BUYER（Sprint 29/30）新建的買家前端頁面能於真實瀏覽器 + 真實後端
 * 正確載入、通過認證、呼叫 API 並渲染「空狀態」而不 crash——這些頁面先前僅
 * 前端 lint/build，從未瀏覽器端到端驗證（AI-1602）。
 *
 * 涵蓋（皆以「新註冊帳號 → 空資料」為基礎，零後端 seed 依賴 → 穩健不 flaky）：
 * - E2E-BUYER-01: 我的訂單列表空狀態（/orders）
 * - E2E-BUYER-02: 通知收件匣載入 + 篩選切換（/notifications）
 * - E2E-BUYER-03: 我的預訂列表空狀態（/bookings）
 *
 * Sprint 37 US-003（AI-1901 套版後版型一致性驗收）新增：
 * - E2E-BUYER-04: 買家頁套用共用賣場版型（多頁 Header/Footer 一致、唯一、不重複兩套 nav）
 * - E2E-BUYER-05: 共用 Header 帳號選單登出（登入態顯示 email/登出 → 登出後轉訪客）
 *
 * 未自動化（需 seed 訂單/商品，避免 flaky，改由手動 checklist 驗證，見 Sprint 32 Review AC-002-3）：
 * - 訂單詳情 + 內嵌物流追蹤（/orders/[id]，需已建立訂單）
 * - 商品評價提交/列表（/reviews/product/[listingId]，需真實 listing）
 * - 下單→付款→通知→物流→評價 完整資料流（需跨賣家角色 seed）
 */

test.describe('AT-BUYER-PAGES: 買家頁面瀏覽器端驗證', () => {
  test('E2E-BUYER-01: 我的訂單列表空狀態', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/orders');
    await page.waitForLoadState('domcontentloaded');

    // 新帳號無訂單 → 空狀態（若認證失敗被導回 /login，此文字不會出現 → 測試失敗）
    await expect(page.getByText('尚無訂單')).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('您目前還沒有任何訂單')).toBeVisible();
    await expect(page.getByRole('button', { name: '去逛逛' })).toBeVisible();
  });

  test('E2E-BUYER-02: 通知收件匣載入 + 篩選切換', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/notifications');
    await page.waitForLoadState('domcontentloaded');

    // 頁面標題渲染 → 頁面載入 + 認證成功
    await expect(page.getByRole('heading', { name: '通知收件匣' })).toBeVisible({ timeout: 15000 });
    // 預設 unreadOnly=false → 空狀態「尚無通知」（通知 fetch 慢時放寬 timeout，緩解既有 flaky）
    await expect(page.getByText('尚無通知')).toBeVisible({ timeout: 15000 });

    // 切換到「僅未讀」→ 空狀態變「沒有未讀通知」（exact 避免誤中其他含「未讀」文字）
    await page.getByRole('button', { name: '僅未讀', exact: true }).click();
    await expect(page.getByText('沒有未讀通知').first()).toBeVisible({ timeout: 10000 });

    // 切回「全部」（exact 避免誤中「全部標為已讀」按鈕）
    await page.getByRole('button', { name: '全部', exact: true }).click();
    await expect(page.getByText('尚無通知')).toBeVisible({ timeout: 15000 });
  });

  test('E2E-BUYER-03: 我的預訂列表空狀態', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/bookings');
    await page.waitForLoadState('domcontentloaded');

    await expect(page.getByText('尚無預訂')).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('您目前還沒有任何旅宿預訂')).toBeVisible();
    await expect(page.getByRole('button', { name: '去逛逛' })).toBeVisible();
  });
});

/**
 * AT-BUYER-LAYOUT: 買家頁共用賣場版型（Sprint 37 US-001 AI-1901 / US-003）
 *
 * 驗證 (auth) 買家頁已由「各頁手包 nav/footer」收斂到共用 route-group layout
 * （app/(auth)/layout.tsx 承載 StorefrontHeader + StorefrontFooter），且共用 Header
 * 的 auth-aware 帳號選單（email/我的訂單/登出）正常運作。零後端 seed 依賴。
 */
test.describe('AT-BUYER-LAYOUT: 買家頁共用賣場版型（S37）', () => {
  test('E2E-BUYER-04: 多頁套用共用 Header/Footer（唯一、不重複兩套 nav）', async ({ page }) => {
    await registerAndLogin(page);

    // 逐頁確認共用 Header/Footer 存在且「唯一」（若殘留自包 nav 會 >1）
    for (const path of ['/orders', '/cart', '/notifications']) {
      await page.goto(path);
      await page.waitForLoadState('domcontentloaded');

      await expect(page.getByTestId('storefront-header')).toHaveCount(1);
      await expect(page.getByTestId('storefront-header')).toBeVisible({ timeout: 15000 });
      await expect(page.getByTestId('storefront-footer')).toHaveCount(1);
      await expect(page.getByTestId('storefront-footer')).toBeVisible();
    }

    // 已登入 → 帳號區顯示（email + 登出），非訪客
    await expect(page.getByTestId('header-account')).toBeVisible();
    await expect(page.getByTestId('header-logout')).toBeVisible();
  });

  test('E2E-BUYER-05: 共用 Header 帳號選單登出 → 轉訪客', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/orders');
    await page.waitForLoadState('domcontentloaded');

    // 登入態：帳號選單有登出鈕
    await expect(page.getByTestId('header-logout')).toBeVisible({ timeout: 15000 });

    // DEF-185：登出必須實際呼叫後端 /v2/auth/logout 失效 refresh token，
    // 而非只清 localStorage（否則被竊 refresh token 於登出後 7 天內仍可用）
    const logoutRequest = page.waitForRequest(
      (req) => req.url().includes('/v2/auth/logout') && req.method() === 'POST',
      { timeout: 10000 }
    );
    await page.getByTestId('header-logout').click();
    await logoutRequest;

    // 登出後導向 /login，且 Header 即時轉為訪客（登入/註冊）
    await page.waitForURL('**/login', { timeout: 10000 });
    await expect(page.getByTestId('header-login')).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('header-account')).toHaveCount(0);
  });
});
