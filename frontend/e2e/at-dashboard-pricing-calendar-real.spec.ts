import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { Account, seedStore } from './helpers/store';

/**
 * AT-DASHBOARD-PRICING-CALENDAR-REAL: 定價日曆預覽獨立頁面（DEF-321(e)，Sprint 253，真實後端）
 *
 * PRD §10.1.2 列為獨立路由 /dashboard/pricing/calendar；先前 <PricingCalendarPreview /> 只內嵌於
 * /dashboard/pricing/rules 篩選房源 ID 之後，沒有符合 PRD 路由表的獨立入口。本規格驗證新頁面能
 * 正確帶出既有元件並載入真實後端的日曆資料。
 */

const WEB_BASE = 'http://localhost:3000';

test.describe('AT-DASHBOARD-PRICING-CALENDAR-REAL: 定價日曆預覽頁（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let roomId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'BOOKING_ONLY',
        storeLabel: 'E2E 定價日曆店',
        room: { name: '定價日曆測試房型', price: 2500 },
      });
      owner = seeded.owner;
      roomId = seeded.roomId!;
    } finally {
      await page.close();
    }
  });

  test('E2E-PRICING-CAL-01: 輸入房源 ID 後載入定價日曆，無錯誤訊息', async ({ page }: { page: Page }) => {
    // DEF-361（已修復）：此頁與既有 /dashboard/pricing/rules 共用的導覽列曾直接在渲染中呼叫
    // AuthService.getCurrentUser()?.email（讀 localStorage），SSR 輸出與客戶端 hydration 當下
    // 不一致，觸發 React Hydration Error #418。修復後改用 useCurrentUserEmail()
    // （useSyncExternalStore），此處正面斷言不再出現該錯誤，而非僅放寬逾時容忍它。
    const pageErrors: string[] = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/pricing/calendar');

    await page.getByTestId('pricing-calendar-room-input').fill(roomId);

    const preview = page.getByTestId('pricing-calendar-preview');
    await expect(preview).toBeVisible();
    await expect(preview.getByText('定價日曆預覽')).toBeVisible();

    expect(pageErrors).toEqual([]);

    // DEF-362（Sprint 254 已修復）：<PricingCalendarPreview/> 原用 toISOString()（UTC）當「今天」
    // 送給後端，當地時區 UTC+8 每天 00:00~07:59 會算成前一天，使後端 LocalDate.now() 誤判為過去
    // 日期回 400，日曆區塊顯示「載入定價日曆失敗」。修復後改用本地日期，等待載入完成後正面斷言
    // 不論執行時間都不會出現該錯誤——不再迴避斷言，避免已修復的缺陷又被測試靜默繞過。
    await expect(preview.getByText('載入中...')).not.toBeVisible({ timeout: 15_000 });
    await expect(preview.getByText('載入定價日曆失敗')).not.toBeVisible();
  });

  test('E2E-PRICING-CAL-02: 定價規則頁有連結可導向定價日曆頁', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/pricing/rules');

    await page.getByRole('link', { name: '定價日曆預覽' }).click();
    await expect(page).toHaveURL(/\/dashboard\/pricing\/calendar$/);
  });
});
