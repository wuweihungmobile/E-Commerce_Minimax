import { test, expect, Page } from '@playwright/test';

/**
 * AT-DASHBOARD-REVENUE: 店主營收報表（/dashboard/revenue）的預設日期區間 —— Sprint 300（DEF-405）
 *
 * 為什麼需要：這個頁面在這之前沒有任何 E2E。它的預設起訖日原本用 date.toISOString().slice(0, 10)（UTC 日期）
 * 計算，台灣時間每天 00:00～07:59 時 UTC 還在前一天，預設結束日就變成「昨天」，當天的訂單全被排除在報表之外
 * ——Sprint 300 以真實資料掃描時剛好是台灣時間 00:48，報表顯示總營收 $0／訂單 0 筆，API 其實有 2600／3 筆。
 *
 * 以固定瀏覽器時間＋指定時區重現：台灣 2030-03-11 01:30＝UTC 2030-03-10 17:30，兩地日期差一天，是這個缺陷
 * 唯一會現形的時段；一般白天（或 CI 的 UTC 時區）根本測不出來，所以不能靠真實時鐘。
 * 以 page.route mock 營收 API（頁面沒有登入守門，不需真實登入），只斷言「頁面送出的日期區間」與畫面預設值。
 */

test.use({ timezoneId: 'Asia/Taipei' });

function revenueStats() {
  return {
    totalRevenue: 2600,
    totalOrders: 2,
    averageOrderValue: 1300,
    totalRefunds: 0,
    netRevenue: 2600,
    currency: 'TWD',
    dailyRevenue: { startDate: '2030-02-09', endDate: '2030-03-11', data: [{ date: '2030-03-11', revenue: 2600, orderCount: 2 }] },
    categoryRevenue: { data: [] },
  };
}

test.describe('AT-DASHBOARD-REVENUE: 營收報表預設日期區間（DEF-405）', () => {
  test('E2E-REVENUE-01: 台灣凌晨 01:30（UTC 仍是前一天）開啟 → 預設結束日是台灣的「今天」，起日是 30 天前', async ({ page }: { page: Page }) => {
    await page.clock.setFixedTime(new Date('2030-03-10T17:30:00Z'));
    const requested: URL[] = [];
    await page.route('**/v2/dashboard/revenue**', async (route) => {
      requested.push(new URL(route.request().url()));
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: revenueStats() }) });
    });

    await page.goto('/dashboard/revenue');
    await expect(page.getByRole('heading', { name: '營收報表' })).toBeVisible({ timeout: 20000 });
    await expect.poll(() => requested.length, { message: '頁面載入後應自動查詢預設區間' }).toBeGreaterThan(0);

    // 修復前：endDate=2030-03-10（UTC 的昨天），當天的訂單被排除在報表之外
    const query = requested[0].searchParams;
    expect(query.get('endDate'), '結束日應為台灣的今天').toBe('2030-03-11');
    expect(query.get('startDate'), '起日應為 30 天前（台灣日曆日）').toBe('2030-02-09');

    // 畫面上的日期欄位是同一組預設值，且有顯示營收
    const dates = page.locator('input[type="date"]');
    await expect(dates.nth(0)).toHaveValue('2030-02-09');
    await expect(dates.nth(1)).toHaveValue('2030-03-11');
    await expect(page.getByText('$2,600').first()).toBeVisible({ timeout: 10000 });
  });
});
