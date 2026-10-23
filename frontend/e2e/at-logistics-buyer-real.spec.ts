import { test, expect, Page } from '@playwright/test';
import { registerAndLogin, loginOnly } from './helpers/auth';
import { Account, authHeaders, seedStore } from './helpers/store';

/**
 * AT-LOGISTICS-BUYER-REAL: 買家看得到「自己訂單」的物流追蹤（真實後端，完全不 mock）—— Sprint 301（DEF-408）
 *
 * 為什麼需要：PRD §10.1.1 路由表寫買家的 /orders/[id] 是「訂單詳情（含物流追蹤）」，但後端物流的四個讀取端點只認「該訂單的
 * 店鋪租戶」與 admin，買家（沒有店鋪的買家落在系統租戶佔位值）一律 403；前端又以 `.catch(() => [])` 靜默吞掉，物流追蹤卡片
 * 連已送達的訂單都寫「尚未出貨」。Sprint 300 以真實資料掃描時發現；使用者決定「只開放讀取端點給該訂單的買家本人」。
 *
 * 資料全由真實流程建立，**包含真實的出貨路徑**：店主把訂單確認後呼叫 POST /v2/logistics（建立物流單即出貨，訂單轉 SHIPPING）。
 * Sprint 300 的播種是直接 PATCH 訂單狀態、沒有建立物流單，所以當時只觀察到 403、沒觀察到「物流單存在卻被藏起來」。
 *
 * 守門的另一半：另一位買家讀別人訂單的物流仍須 403（跨使用者 IDOR，DEF-190）；買家本人不得寫入（建立／更新／取消）。
 * 登入密度：全檔約 7 次登入（含開店前提的 4 次）。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const PRODUCT_NAME = 'E2E 物流商品';

test.describe('AT-LOGISTICS-BUYER-REAL: 買家讀取自己訂單的物流', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let buyer: Account;
  let buyerHeaders: { Authorization: string };
  let otherBuyerHeaders: { Authorization: string };
  let orderId: string;
  let logisticsId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(240_000);
    const page: Page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const store = await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: 'E2E 物流店',
        product: { name: PRODUCT_NAME, price: 450 },
      });
      const ownerHeaders = await authHeaders(page);

      await page.evaluate(() => localStorage.clear());
      buyer = await registerAndLogin(page);
      buyerHeaders = await authHeaders(page);
      const add = await page.request.post(`${API_BASE}/v2/cart/items`, {
        headers: buyerHeaders,
        data: { listingId: store.productId, quantity: 1 },
      });
      expect(add.status(), await add.text()).toBeLessThan(300);
      const created = await page.request.post(`${API_BASE}/v2/orders`, {
        headers: buyerHeaders,
        data: {
          orderType: 'PRODUCT',
          shippingAddress: '台北市信義區信義路五段 7 號',
          shippingRecipientName: 'E2E 收件人',
          shippingPhone: '0987654321',
        },
      });
      expect(created.status(), await created.text()).toBe(201);
      orderId = (await created.json()).data.id as string;
      const pay = await page.request.post(`${API_BASE}/v2/orders/${orderId}/pay`, { headers: buyerHeaders });
      expect(pay.status(), await pay.text()).toBe(200);

      // 店主確認訂單後建立物流單＝出貨（訂單轉 SHIPPING）
      const confirm = await page.request.patch(`${API_BASE}/v2/orders/${orderId}/status`, {
        headers: ownerHeaders,
        data: { targetStatus: 'CONFIRMED' },
      });
      expect(confirm.status(), await confirm.text()).toBe(200);
      const shipment = await page.request.post(`${API_BASE}/v2/logistics`, {
        headers: ownerHeaders,
        data: {
          orderId,
          logisticsProvider: 'HCT',
          receiverName: 'E2E 收件人',
          receiverPhone: '0987654321',
          shippingAddress: '台北市信義區信義路五段 7 號',
        },
      });
      expect(shipment.status(), await shipment.text()).toBe(200);
      logisticsId = (await shipment.json()).data.logisticsId as string;

      // 另一位沒有店鋪的買家（同樣落在系統租戶佔位值）
      await page.evaluate(() => localStorage.clear());
      await registerAndLogin(page);
      otherBuyerHeaders = await authHeaders(page);
    } finally {
      await page.close();
    }
  });

  test('E2E-LOGISTICS-01: 買家開自己的訂單詳情 → 物流追蹤卡片顯示物流商與單號，不再寫「尚未出貨」', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, buyer.email, buyer.password);

    await page.goto(`/orders/${orderId}`);
    // 修復前：讀取物流回 403，前端吞掉，卡片顯示「尚未出貨，物流資訊將於出貨後顯示。」
    await expect(page.getByText('物流商：黑貓宅急便')).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/物流單號：\S+/)).toBeVisible();
    await expect(page.getByText('尚未出貨，物流資訊將於出貨後顯示。')).toHaveCount(0);
  });

  test('E2E-LOGISTICS-02: 買家本人讀取自己訂單的物流（四個讀取端點）→ 200', async ({ request }) => {
    const list = await request.get(`${API_BASE}/v2/logistics/order/${orderId}`, { headers: buyerHeaders });
    expect(list.status(), await list.text()).toBe(200);
    const shipments = (await list.json()).data as Array<{ logisticsId: string; orderId: string }>;
    expect(shipments).toHaveLength(1);
    expect(shipments[0].logisticsId).toBe(logisticsId);

    const detail = await request.get(`${API_BASE}/v2/logistics/${logisticsId}`, { headers: buyerHeaders });
    expect(detail.status(), await detail.text()).toBe(200);
    const track = await request.get(`${API_BASE}/v2/logistics/${logisticsId}/track`, { headers: buyerHeaders });
    expect(track.status(), await track.text()).toBe(200);
    const trackingDetail = await request.get(`${API_BASE}/v2/logistics/${logisticsId}/tracking-detail`, { headers: buyerHeaders });
    expect(trackingDetail.status(), await trackingDetail.text()).toBe(200);
    expect(((await trackingDetail.json()).data.events as unknown[]).length).toBeGreaterThan(0);
  });

  test('E2E-LOGISTICS-03: 🔴 另一位買家讀別人訂單的物流 → 四個讀取端點都仍然 403（跨使用者 IDOR 守門）', async ({ request }) => {
    for (const path of [
      `/v2/logistics/order/${orderId}`,
      `/v2/logistics/${logisticsId}`,
      `/v2/logistics/${logisticsId}/track`,
      `/v2/logistics/${logisticsId}/tracking-detail`,
    ]) {
      const response = await request.get(`${API_BASE}${path}`, { headers: otherBuyerHeaders });
      expect(response.status(), `另一位買家不得讀取：GET ${path} → ${await response.text()}`).toBe(403);
    }
  });

  test('E2E-LOGISTICS-04: 🔴 讀取放寬不得外溢到寫入：買家本人不能更新狀態、取消、建立物流單 → 403', async ({ request }) => {
    const update = await request.put(`${API_BASE}/v2/logistics/${logisticsId}/status?status=DELIVERED`, { headers: buyerHeaders });
    expect(update.status(), `買家不得更新物流狀態：${await update.text()}`).toBe(403);

    const cancel = await request.post(`${API_BASE}/v2/logistics/cancel`, {
      headers: buyerHeaders,
      data: { logisticsId, reason: 'E2E 買家不得取消' },
    });
    expect(cancel.status(), `買家不得取消物流單：${await cancel.text()}`).toBe(403);

    const create = await request.post(`${API_BASE}/v2/logistics`, {
      headers: buyerHeaders,
      data: {
        orderId,
        logisticsProvider: 'HCT',
        receiverName: '買家',
        receiverPhone: '0912345678',
        shippingAddress: '台北市',
      },
    });
    expect(create.status(), `買家不得建立物流單：${await create.text()}`).toBe(403);
  });
});
