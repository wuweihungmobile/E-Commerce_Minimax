import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';

/**
 * AT-UI-DESIGN-BASE: 全站 UI 以「意象若水 Homepage 設計稿」為 BASE（Sprint 328）
 *
 * 設計稿：docs/01_requirements/Design_UI/Homepage (standalone) _OK.html。
 * 這支規格守住兩件事，缺一不可：
 *   1. 每個區域都在共用外殼之下，而且只有「一套」頂欄（不會又長出各頁自己的頂欄）：
 *      店面／blog／買家頁／開店申請 → StorefrontHeader；賣家後台／平台管理台 → SellerHeader＋側欄。
 *   2. 外殼的顏色真的來自設計稿 token——切換 <html data-theme> 後，頂欄背景與側欄目前頁色條都跟著變。
 *      （若有人把顏色改回寫死的 Tailwind 色盤，換主題時這裡會紅；靜態面由 ESLint 的 design-tokens 規則守住。）
 *
 * 色值取自設計稿（globals.css 與設計稿逐 token 一致）：
 *   --rs-topbar-bg  blue #173a72 → rgb(23, 58, 114)   green #336a29 → rgb(51, 106, 41)
 *   --rs-primary    blue #3770bf → rgb(55, 112, 191)  green #1d6517 → rgb(29, 101, 23)
 *
 * 不依賴後端 seed 資料：只用既有固定的 admin 帳號（SUPER_ADMIN）登入；後台頁面的 API 即使回 403 也不影響外殼。
 * - E2E-UI-01: 店面首頁與 blog（匿名）
 * - E2E-UI-02: 買家頁與開店申請（已登入）
 * - E2E-UI-03: 賣家後台 /dashboard（已登入）：外殼唯一、側欄目前頁、顏色隨主題、沒有各頁自帶的舊頂欄
 * - E2E-UI-04: 平台管理台 /admin（同上）
 * - E2E-UI-05: （已移除）原本是內容管理 /cms；Sprint 332 的 DEF-466 把它併進賣家後台，編號保留不重用
 * 兩個後台區域各自一個案例（各登入一次），失敗時一眼看出是哪個區域的外殼壞了。
 * - E2E-UI-06: 目前頁在側欄下半段時，側欄會捲到讓它可見（賣家側欄有 27 項，自己會捲動）
 * - E2E-UI-07: 賣家外殼的「登出」可用（28 個後台頁各自的登出鈕已移除，改由 Header 提供；原本沒有任何 E2E 在賣家區點過登出）
 */

const ADMIN_EMAIL = 'admin@nextkey.local';
const ADMIN_PASSWORD = 'Test123!';

const TOPBAR = { blue: 'rgb(23, 58, 114)', green: 'rgb(51, 106, 41)' };
const PRIMARY = { blue: 'rgb(55, 112, 191)', green: 'rgb(29, 101, 23)' };

type HeaderKind = 'storefront' | 'seller';

/** 頂欄＝header 的第一個子 div（深色 topbar）。 */
function topbar(page: Page, kind: HeaderKind) {
  return page.locator(`[data-testid="${kind}-header"] > div`).first();
}

/**
 * 用真實的主題切換器換主題（不是直接改 DOM 屬性），並等 <html data-theme> 更新。
 *
 * 為什麼用 toPass 重試：SSR 的 <select> 在 hydration 完成前就看得到、也選得動，但那時 React 還沒接上 onChange，
 * 事件白發；之後 hydration 又把 select 的值重設回 blue，data-theme 永遠不變（S328 首次執行 UI-02 就這樣紅過）。
 * 重試到「選取後 data-theme 真的變了」才算數，不用固定等待（見記憶 e2e-domcontentloaded-wrong-signal）。
 */
async function switchTheme(page: Page, theme: 'blue' | 'green') {
  await expect(async () => {
    await page.locator('[aria-label="切換色票主題"] select').selectOption(theme);
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme, { timeout: 1000 });
  }).toPass({ timeout: 15000 });
}

/** 這一頁只有指定的那一套頂欄，且頂欄顏色隨主題 blue → green。 */
async function expectShellFollowsTheme(page: Page, kind: HeaderKind) {
  const other: HeaderKind = kind === 'storefront' ? 'seller' : 'storefront';
  await expect(page.getByTestId(`${kind}-header`)).toHaveCount(1);
  await expect(page.getByTestId(`${other}-header`), `不該同時出現另一套頂欄（${other}）`).toHaveCount(0);
  await expect(page.getByTestId('storefront-footer')).toHaveCount(1);

  await expect(page.locator('html')).toHaveAttribute('data-theme', 'blue');
  await expect(topbar(page, kind)).toHaveCSS('background-color', TOPBAR.blue);
  await switchTheme(page, 'green');
  await expect(topbar(page, kind)).toHaveCSS('background-color', TOPBAR.green);
}

test.describe('AT-UI-DESIGN-BASE: 全站共用外殼與設計稿主題', () => {
  test('E2E-UI-01: 店面首頁與 blog（匿名）套用店面外殼，頂欄顏色隨主題', async ({ page }: { page: Page }) => {
    for (const path of ['/', '/blog']) {
      await test.step(`${path} → StorefrontHeader`, async () => {
        await page.goto(path);
        await expectShellFollowsTheme(page, 'storefront');
        // 換回 blue，下一個路徑從預設主題開始（每個 step 都以 blue 起算）
        await switchTheme(page, 'blue');
      });
    }
  });

  test('E2E-UI-02: 買家頁與開店申請（已登入）套用店面外殼，頂欄顏色隨主題', async ({ page }: { page: Page }) => {
    await loginOnly(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    for (const path of ['/cart', '/tenant/apply']) {
      await test.step(`${path} → StorefrontHeader`, async () => {
        await page.goto(path);
        await expectShellFollowsTheme(page, 'storefront');
        await switchTheme(page, 'blue');
      });
    }
  });

  const sellerAreas = [
    { id: 'E2E-UI-03', name: '賣家後台', path: '/dashboard', active: '營運總覽' },
    { id: 'E2E-UI-04', name: '平台管理台', path: '/admin', active: '平台總覽' },
  ];
  for (const { id, name, path, active } of sellerAreas) {
    test(`${id}: ${name} ${path} 套用賣家外殼（頂欄＋側欄），目前頁「${active}」，顏色隨主題`, async ({ page }: { page: Page }) => {
      await loginOnly(page, ADMIN_EMAIL, ADMIN_PASSWORD);
      await page.goto(path);

      await expect(page.getByTestId('seller-header-account')).toContainText(ADMIN_EMAIL);
      await expect(page.getByTestId('seller-sidebar')).toHaveCount(1);

      const current = page.locator('[data-testid="seller-sidebar"] a[aria-current="page"]');
      await expect(current, '側欄只有一個「目前頁」').toHaveCount(1);
      await expect(current).toHaveText(active);

      await expectShellFollowsTheme(page, 'seller');
      // 側欄目前頁的左側色條是 --rs-primary：換成 green 後要跟著變，換回 blue 再變回來
      await expect(current).toHaveCSS('border-left-color', PRIMARY.green);
      await switchTheme(page, 'blue');
      await expect(current).toHaveCSS('border-left-color', PRIMARY.blue);

      // 各頁自己組的舊頂欄（麵包屑＋email＋登出）已移除，只剩外殼提供的那一套
      await expect(page.getByText('NextKey Dashboard')).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Logout' })).toHaveCount(0);
    });
  }

  test('E2E-UI-06: 目前頁在側欄下半段時，側欄會捲到讓它可見（長側欄不讓使用者找不到「我在哪」）', async ({ page }: { page: Page }) => {
    await loginOnly(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.goto('/dashboard/erp/inventory');

    const sidebar = page.getByTestId('seller-sidebar');
    const current = sidebar.locator('a[aria-current="page"]');
    await expect(current).toHaveText('庫存');

    // 前提：賣家側欄內容確實比它的可視高度長，這條路徑不捲就看不到——否則這個案例什麼都證明不了
    const size = await sidebar.evaluate((el) => ({ scrollHeight: el.scrollHeight, clientHeight: el.clientHeight }));
    expect(size.scrollHeight, `前提：側欄內容要比可視高度長（scrollHeight=${size.scrollHeight}, clientHeight=${size.clientHeight}）`).toBeGreaterThan(size.clientHeight);

    // 目前頁完整落在側欄的可視範圍內（捲動在掛載後才發生，用 toPass 重試，不用固定等待）
    await expect(async () => {
      const box = await sidebar.boundingBox();
      const cur = await current.boundingBox();
      expect(box).not.toBeNull();
      expect(cur).not.toBeNull();
      expect(cur!.y, '目前頁的上緣不能被側欄裁掉').toBeGreaterThanOrEqual(box!.y);
      expect(cur!.y + cur!.height, '目前頁的下緣不能被側欄裁掉').toBeLessThanOrEqual(box!.y + box!.height + 1);
    }).toPass({ timeout: 5000 });
  });

  test('E2E-UI-07: 賣家外殼的「登出」可用：呼叫後端登出、清掉 token、回到登入頁', async ({ page }: { page: Page }) => {
    await loginOnly(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.goto('/admin');
    await expect(page.getByTestId('seller-header-account')).toContainText(ADMIN_EMAIL);

    // 登出必須實際呼叫後端 /v2/auth/logout 失效 refresh token，而不只是清 localStorage（DEF-185，與店面 Header 同一個要求）
    const [logoutRequest] = await Promise.all([
      page.waitForRequest((r) => r.url().includes('/v2/auth/logout') && r.method() === 'POST'),
      page.getByTestId('seller-header-logout').click(),
    ]);
    expect(logoutRequest.url()).toContain('/v2/auth/logout');

    // 登出是離開：回到乾淨的 /login（不帶 ?redirect=），token 已清除
    await expect(page).toHaveURL(/\/login$/, { timeout: 15000 });
    expect(await page.evaluate(() => localStorage.getItem('accessToken')), '登出後 accessToken 應已清除').toBeNull();
  });
});
