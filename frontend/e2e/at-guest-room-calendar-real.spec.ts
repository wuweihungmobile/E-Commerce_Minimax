import { test, expect } from '@playwright/test';
import { registerAndLogin } from './helpers/auth';
import { seedStore } from './helpers/store';

/**
 * AT-GUEST-ROOM-CALENDAR-REAL: 房源頁的日曆與可訂性查詢對訪客／登入者的行為（真實後端，完全不 mock）—— Sprint 299（DEF-402）
 *
 * 為什麼需要：`GET /v2/bookings/calendar` 與 `GET /v2/bookings/availability` 需要登入（`booking:read`），匿名呼叫回 401。
 * 房源頁（公開路由）原本對訪客照打這兩支，結果每個房源頁都顯示「日曆載入失敗，仍可手動輸入日期」，按「查詢可用性」也只看到
 * 「查詢可用性失敗，請稍後再試」——把「需要登入」說成「失敗」。PRD RTM-US-G1 原寫「Guest 可瀏覽房源日曆、定價」，與實作不符
 * （Sprint 298 發現，DEF-402）。使用者裁決**維持需登入**，改 UI 與 PRD：訪客在日曆區看到「登入後可查看即時空房」且不再發出
 * 必然 401 的請求；按「查詢可用性」得到「請先登入以查詢可用性」。
 *
 * E2E-GUESTCAL-03 是對照案例：已登入買家仍要看到真實日曆、沒有登入提示——防止修法把登入者也擋掉。
 *
 * 資料全由真實流程建立（見 helpers/store.ts）：店主註冊→驗證 Email→申請開店→管理員核准→開啟 BOOKING_ENABLED→建立房源。
 */

const WEB_BASE = 'http://localhost:3000';
const CALENDAR_API = '/v2/bookings/calendar';
const LOGIN_HINT = '登入後可查看即時空房';

function isoDate(daysFromNow: number): string {
  const d = new Date();
  d.setDate(d.getDate() + daysFromNow);
  return d.toISOString().slice(0, 10);
}

test.describe('AT-GUEST-ROOM-CALENDAR-REAL: 房源頁日曆（真實後端）', () => {
  // 開店流程只需要做一次
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let roomId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      ({ roomId } = (await seedStore(page, {
        businessType: 'BOOKING_ONLY',
        storeLabel: 'E2E 訪客日曆店',
        room: { name: 'E2E 訪客日曆房', price: 2800 },
      })) as { roomId: string });
    } finally {
      await page.close();
    }
  });

  test('E2E-GUESTCAL-01: 訪客看到「登入後可查看即時空房」，沒有錯誤訊息，也不發出必然 401 的日曆請求', async ({ page }) => {
    const calendarRequests: string[] = [];
    page.on('request', (req) => {
      if (req.url().includes(CALENDAR_API)) calendarRequests.push(req.url());
    });

    await page.goto(`/listings/${roomId}`);
    await expect(page.getByTestId('listing-calendar')).toBeVisible();
    await expect(page.getByTestId('calendar-login-hint')).toContainText(LOGIN_HINT);

    // 先驗「原因」：切到下個月——舊行為在這一步會再發一次日曆請求，新行為不發。
    // 否定斷言放在「提示已出現＝流程確實走到這一步」之後
    await page.getByTestId('calendar-next').click();
    await page.waitForTimeout(500);
    expect(calendarRequests, '訪客不應呼叫需要登入的日曆端點').toEqual([]);

    // 再驗「症狀」：也不得顯示錯誤訊息（請求若照發、照 401，這條會紅；順序放在請求斷言之後，兩條才各自有鑑別力）
    await expect(page.getByText('日曆載入失敗')).toHaveCount(0);
  });

  test('E2E-GUESTCAL-02: 訪客按「查詢可用性」看到「請先登入」，而不是「查詢可用性失敗」', async ({ page }) => {
    await page.goto(`/listings/${roomId}`);
    await page.getByTestId('listing-checkin').fill(isoDate(10));
    await page.getByTestId('listing-checkout').fill(isoDate(12));
    await page.getByRole('button', { name: '查詢可用性' }).click();

    await expect(page.getByText('請先登入以查詢可用性')).toBeVisible();
    await expect(page.getByText('查詢可用性失敗')).toHaveCount(0);
  });

  test('E2E-GUESTCAL-03（對照）: 已登入買家看得到真實日曆，沒有登入提示', async ({ page }) => {
    await registerAndLogin(page);

    // 必須在導向之前掛上監聽，否則快速的回應可能先到而漏接
    const calendarResponse = page.waitForResponse(
      (res) => res.url().includes(CALENDAR_API) && res.request().method() === 'GET'
    );
    await page.goto(`/listings/${roomId}`);

    expect((await calendarResponse).status(), '登入買家的日曆請求應成功').toBe(200);
    await expect(page.getByTestId('listing-calendar')).toBeVisible();
    await expect(page.getByTestId('calendar-login-hint')).toHaveCount(0);
    await expect(page.getByText('日曆載入失敗')).toHaveCount(0);
  });
});
