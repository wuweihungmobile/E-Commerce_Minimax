import { test, expect } from '@playwright/test';

/**
 * AT-LEGAL-PAGES: 頁尾的隱私權政策／服務條款連結（Sprint 299，DEF-403）
 *
 * 為什麼需要：StorefrontFooter 連到 /privacy 與 /terms，但這兩個頁面原本不存在，點下去是 404
 * （Sprint 298 的全路由掃描在登入／註冊頁的 console 抓到 `404 /privacy?_rsc=…`、`/terms?_rsc=…`）。
 * 頁面的正式內容屬法務／業務決定、尚未提供，使用者選擇先建「即將公告」佔位頁：只說明內容即將公告，不含任何法律文字。
 * 本規格守住「連結不是死的」。有了正式內容後，把佔位文字換掉，並同步更新這裡的斷言。
 */

const PAGES = [
  { path: '/privacy', title: '隱私權政策' },
  { path: '/terms', title: '服務條款' },
];

test.describe('AT-LEGAL-PAGES: 隱私權政策／服務條款佔位頁', () => {
  for (const { path, title } of PAGES) {
    test(`E2E-LEGAL-01 ${path}: 直接開啟回 200，顯示標題與「即將公告」`, async ({ page }) => {
      const response = await page.goto(path);
      expect(response?.status(), `${path} 不得是 404`).toBe(200);
      await expect(page.getByRole('heading', { name: title, level: 1 })).toBeVisible();
      await expect(page.getByText('內容即將公告')).toBeVisible();
    });

    test(`E2E-LEGAL-02 ${path}: 從登入頁頁尾的「${title}」連結點進去不是 404`, async ({ page }) => {
      await page.goto('/login');
      await page.getByTestId('storefront-footer').getByRole('link', { name: title }).click();
      await expect(page).toHaveURL(new RegExp(`${path}$`));
      await expect(page.getByRole('heading', { name: title, level: 1 })).toBeVisible();
    });
  }
});
