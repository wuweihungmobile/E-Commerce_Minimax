import { test, expect, Page } from '@playwright/test';

/**
 * AT-HOMEPAGE: 賣場首頁版型 E2E（Sprint 35 US-005）
 *
 * 驗證「意象若水 RUOSHUI」賣場首頁版型（TOP/Tools/Bottom 共用 + Content 分頁）於真實
 * 瀏覽器正確載入、四區塊呈現、主題切換與搜尋互動可用，且內容區於任一資料狀態
 * （商品網格 / 空狀態 / 未登入引導）皆能渲染而不 crash。
 *
 * 遵循既有 E2E 哲學：不依賴後端 seed 資料 → 穩健不 flaky。
 * - E2E-HOME-01: 首頁載入 + 四區塊（TOP header / Tools 排序頁籤 / Content / Bottom footer）
 * - E2E-HOME-02: 色票主題切換（<html data-theme> 變更 + localStorage 記憶）
 * - E2E-HOME-03: 搜尋列互動不 crash
 * - E2E-HOME-04: 登入後內容區解析（商品網格或空狀態，不 crash）
 *
 * 未自動化（需 seed 商品，避免 flaky）：商品網格「有資料」渲染、分頁翻頁 → 手動 checklist。
 */

async function registerAndLogin(page: Page, testEmail?: string) {
  const timestamp = Date.now();
  const email = testEmail || `e2e-home-${timestamp}@example.com`;
  const password = 'Test123!';

  await page.goto('/login');
  await page.waitForLoadState('domcontentloaded');

  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');
  await page.waitForTimeout(3000);

  if (page.url().includes('/login')) {
    const registerLink = page
      .locator('a:has-text("create a new account"), a:has-text("註冊")')
      .first();
    if (await registerLink.isVisible()) {
      await registerLink.click();
      await page.waitForTimeout(2000);
    }

    await page.fill('input[name="fullName"]', 'E2E Home User');
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', password);
    await page.fill('input[name="confirmPassword"]', password);
    await page.click('button[type="submit"]');
    await page.waitForTimeout(3000);

    if (page.url().includes('/login')) {
      await page.fill('input[name="email"]', email);
      await page.fill('input[name="password"]', password);
      await page.click('button[type="submit"]');
      await page.waitForTimeout(3000);
    }
  }

  return { email, password };
}

// 內容區三種合法狀態任一出現即代表資料流完成、未 crash
function contentResolved(page: Page) {
  return page
    .getByTestId('product-grid')
    .or(page.getByTestId('home-empty'))
    .or(page.getByTestId('home-auth-empty'));
}

test.describe('AT-HOMEPAGE: 賣場首頁版型瀏覽器端驗證', () => {
  test('E2E-HOME-01: 首頁載入 + 四區塊呈現', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    // TOP：品牌 logo（頁首）
    await expect(page.getByRole('link', { name: /意象若水/ }).first()).toBeVisible({
      timeout: 15000,
    });
    // TOP：搜尋列
    await expect(page.getByRole('search')).toBeVisible();
    // Tools：排序頁籤
    await expect(page.getByRole('button', { name: '最新' })).toBeVisible();
    // Bottom：頁尾版權
    await expect(page.getByText('生活減法，無負擔的購物體驗')).toBeVisible();
    // Content：內容區已解析（任一合法狀態）
    await expect(contentResolved(page)).toBeVisible({ timeout: 15000 });
  });

  test('E2E-HOME-02: 色票主題切換', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    const html = page.locator('html');
    // 預設主題 blue
    await expect(html).toHaveAttribute('data-theme', 'blue', { timeout: 15000 });

    // 透過主題切換器切到 green
    const themeSelect = page.locator('header select').first();
    await themeSelect.selectOption('green');
    await expect(html).toHaveAttribute('data-theme', 'green');

    // 重新載入後仍記憶 green（localStorage）
    await page.reload();
    await page.waitForLoadState('domcontentloaded');
    await expect(html).toHaveAttribute('data-theme', 'green', { timeout: 15000 });
  });

  test('E2E-HOME-03: 搜尋列互動不 crash', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    const search = page.getByRole('search').locator('input[type="search"]');
    await search.fill('質感');
    await search.press('Enter');

    // 送出後版型仍在、內容區重新解析
    await expect(page.getByRole('search')).toBeVisible();
    await expect(contentResolved(page)).toBeVisible({ timeout: 15000 });
  });

  test('E2E-HOME-04: 登入後內容區解析', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    // 版型呈現 + 內容區解析（商品網格或空狀態，不 crash）
    await expect(page.getByRole('link', { name: /意象若水/ }).first()).toBeVisible({
      timeout: 15000,
    });
    await expect(contentResolved(page)).toBeVisible({ timeout: 15000 });
  });
});
