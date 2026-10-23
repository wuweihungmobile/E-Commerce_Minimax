import { test, expect, Page } from '@playwright/test';
import { registerAndLogin, loginOnly } from './helpers/auth';
import { Account, authHeaders, seedStore } from './helpers/store';

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

/**
 * 營收口徑（真實後端）—— Sprint 301（DEF-407）
 *
 * Sprint 300 以真實資料掃描時發現：同一間店鋪，首頁「今日營收」3100 與營收報表「總營收」2600 對不上——差的正是一張**未付款**
 * 的訂單。後端有三種互相矛盾的口徑：報表總營收只加已付款類狀態，首頁與每日趨勢卻把未付款（CREATED）與等待退款（REFUNDING）
 * 也算進去，平均客單價的分母又是區間內全部訂單。使用者決定：營收＝已付款、未取消、未退款的訂單；平均客單價分母同一組。
 *
 * 資料：一張已付款訂單（500）＋一張**只建立、不付款**的訂單（500）。修復前首頁今日營收＝1000，報表平均客單價＝250。
 * 瀏覽器時區固定為台灣（檔案頂端的 test.use），預設報表區間的「今天」才會與伺服器的營運日（UTC+8）一致。
 */
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';

test.describe('AT-DASHBOARD-REVENUE: 營收口徑一致（真實後端，DEF-407）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(240_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const store = await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: 'E2E 營收店',
        product: { name: 'E2E 營收商品', price: 500 },
      });
      owner = store.owner;

      await page.evaluate(() => localStorage.clear());
      await registerAndLogin(page);
      const headers = await authHeaders(page);
      const placeOrder = async (): Promise<string> => {
        const add = await page.request.post(`${API_BASE}/v2/cart/items`, {
          headers,
          data: { listingId: store.productId, quantity: 1 },
        });
        expect(add.status(), await add.text()).toBeLessThan(300);
        const created = await page.request.post(`${API_BASE}/v2/orders`, {
          headers,
          data: {
            orderType: 'PRODUCT',
            shippingAddress: '台北市信義區信義路五段 7 號',
            shippingRecipientName: 'E2E 收件人',
            shippingPhone: '0987654321',
          },
        });
        expect(created.status(), await created.text()).toBe(201);
        return (await created.json()).data.id as string;
      };
      const paidOrderId = await placeOrder();
      const pay = await page.request.post(`${API_BASE}/v2/orders/${paidOrderId}/pay`, { headers });
      expect(pay.status(), await pay.text()).toBe(200);
      await placeOrder(); // 只建立、不付款（CREATED）
    } finally {
      await page.close();
    }
  });

  /** 同一張卡片裡，標籤下面那行就是數值（首頁與報表頁的 StatCard 結構相同）。 */
  const statValue = (page: Page, label: string) =>
    page.getByText(label, { exact: true }).locator('xpath=following-sibling::p[1]');

  test('E2E-REVENUE-02: 店主首頁的今日／本月／本年營收只計已付款的訂單（未付款的不算）', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard');

    // 修復前：今日營收 $1,000（把未付款的 500 也算進去）
    await expect(statValue(page, '今日營收')).toHaveText('$500', { timeout: 20000 });
    await expect(statValue(page, '本月營收')).toHaveText('$500');
    await expect(statValue(page, '本年營收')).toHaveText('$500');
  });

  test('E2E-REVENUE-03: 營收報表的總營收與平均客單價同一口徑（平均客單價以入帳訂單數平均）', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/revenue');

    await expect(statValue(page, '總營收')).toHaveText('$500', { timeout: 20000 });
    // 修復前：500 ÷ 2（分母含未付款的那張）＝ $250
    await expect(statValue(page, '平均客單價')).toHaveText('$500');
    // 「訂單總數」維持區間內全部訂單（含未付款）
    await expect(page.getByText('訂單總數 2 筆')).toBeVisible();
  });
});
