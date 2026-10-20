import { test, expect, Page } from '@playwright/test';
import { registerAndLogin } from './helpers/auth';
import { seedStore } from './helpers/store';

/**
 * AT-REVIEW-REPLY-REAL: 賣家回覆評價與評價管理（真實後端）— Sprint 287（DEF-392）
 *
 * 為什麼需要：Sprint 285 真實跨角色實測發現 `ReviewReplyService`／`ReviewService.markAsHandled`／
 * `getReviewsByHandlingStatus` 後端功能完整，但前端完全零串接——`ReviewList.tsx` 不顯示回覆，
 * `dashboard/` 底下沒有任何評價管理頁面（DEF-392）。本輪補上 `dashboard/reviews` 管理頁與買家端
 * 回覆顯示後，用真實後端＋兩個真實角色（賣家／買家）走一遍完整路徑：下單付款出貨送達 → 買家評價 →
 * 賣家在真實瀏覽器回覆與標記已處理 → 買家端看得到回覆。
 *
 * 登入次數：登入端點有 IP 限流（每個來源 IP、每個路徑每分鐘 30 次，整個 E2E 套件共用），本規格只在 beforeAll
 * 登入（店主、買家各一次，加上開店流程內的管理員），之後各案例還原存下的 localStorage，不再打登入端點。
 * 原先每個案例都重新登入（共 8 次），Sprint 287 實測會連帶讓不相干的 at-store-member-revocation-real 拿不到 token。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const PRODUCT_PRICE = 350;
const RECIPIENT_NAME = 'E2E 評價收件人';
const REVIEW_TITLE = 'E2E 評價標題：包裝完好';
const REVIEW_CONTENT = 'E2E 評價內容：商品品質符合預期，出貨速度也很快。';
const REPLY_CONTENT = '感謝您的評價，祝您使用愉快！（E2E 自動回覆）';

type Session = Record<string, string>;

function readSession(page: Page): Promise<Session> {
  return page.evaluate(() => Object.fromEntries(Object.entries(localStorage)));
}

function bearer(session: Session): { Authorization: string } {
  return { Authorization: `Bearer ${session.accessToken}` };
}

/**
 * 還原登入狀態（beforeAll 登入一次後存下的 localStorage）。每個案例都是全新的瀏覽器環境，所以要在載入頁面前先設好；
 * token 有效 15 分鐘，本規格約 1 分鐘跑完。作法比照 at-store-suspended-payment-real.spec.ts。
 */
async function restoreSession(page: Page, session: Session): Promise<void> {
  await page.addInitScript((entries: Record<string, string>) => {
    for (const [key, value] of Object.entries(entries)) localStorage.setItem(key, value);
  }, session);
}

test.describe('AT-REVIEW-REPLY-REAL: 賣家回覆評價與評價管理（真實後端）', () => {
  // 後面案例依賴前面建立的商品、訂單與評價，且開店流程只需要做一次
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let productId: string;
  let ownerSession: Session;
  let customerSession: Session;
  let orderId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(150_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: 'E2E 評價回覆測試店',
        product: { name: 'E2E 評價測試商品', price: PRODUCT_PRICE },
      });
      productId = seeded.productId as string;
      // seedStore 結束時頁面是店主的登入狀態（JWT 已帶新店鋪租戶）
      ownerSession = await readSession(page);
      expect(ownerSession.accessToken, '店主應已登入').toBeTruthy();

      // registerAndLogin 先到 /login，已登入會被導走而跳過註冊，所以先清掉
      await page.evaluate(() => localStorage.clear());
      await registerAndLogin(page);
      customerSession = await readSession(page);
      expect(customerSession.accessToken, '買家應已登入').toBeTruthy();
    } finally {
      await page.close();
    }
  });

  test('E2E-RVREPLY-01: 消費者下單付款，店主確認出貨並送達（建立前提）', async ({ page }: { page: Page }) => {
    const buyerHeaders = bearer(customerSession);

    const add = await page.request.post(`${API_BASE}/v2/cart/items`, {
      headers: buyerHeaders,
      data: { listingId: productId, quantity: 1 },
    });
    expect(add.status(), await add.text()).toBeLessThan(300);

    const created = await page.request.post(`${API_BASE}/v2/orders`, {
      headers: buyerHeaders,
      data: {
        orderType: 'PRODUCT',
        shippingAddress: '台北市測試路一段 1 號',
        shippingRecipientName: RECIPIENT_NAME,
        shippingPhone: '0911111111',
      },
    });
    expect(created.status(), await created.text()).toBe(201);
    orderId = (await created.json()).data.id as string;

    const pay = await page.request.post(`${API_BASE}/v2/orders/${orderId}/pay`, { headers: buyerHeaders });
    expect(pay.status(), await pay.text()).toBe(200);

    const ownerHeaders = bearer(ownerSession);

    const confirm = await page.request.patch(`${API_BASE}/v2/orders/${orderId}/status`, {
      headers: ownerHeaders,
      data: { targetStatus: 'CONFIRMED' },
    });
    expect(confirm.status(), await confirm.text()).toBe(200);

    const ship = await page.request.post(`${API_BASE}/v2/logistics`, {
      headers: ownerHeaders,
      data: { orderId, logisticsProvider: 'HCT' },
    });
    expect(ship.status(), await ship.text()).toBe(200);
    const logisticsId = (await ship.json()).data.logisticsId as string;

    const deliver = await page.request.put(
      `${API_BASE}/v2/logistics/${logisticsId}/status?status=DELIVERED`,
      { headers: ownerHeaders }
    );
    expect(deliver.status(), await deliver.text()).toBe(200);
  });

  test('E2E-RVREPLY-02: 買家對已送達商品撰寫評價（建立前提）', async ({ page }: { page: Page }) => {
    const created = await page.request.post(`${API_BASE}/v2/reviews`, {
      headers: bearer(customerSession),
      data: {
        listingId: productId,
        orderId,
        rating: 4,
        title: REVIEW_TITLE,
        content: REVIEW_CONTENT,
      },
    });
    expect(created.status(), await created.text()).toBe(200);
  });

  test('E2E-RVREPLY-03: 店主在 /dashboard/reviews 看到待處理評價、送出回覆後立即顯示', async ({ page }: { page: Page }) => {
    await restoreSession(page, ownerSession);

    await page.goto('/dashboard/reviews');
    await expect(page.getByText(REVIEW_TITLE)).toBeVisible();
    await expect(page.getByText('未處理').first()).toBeVisible();

    await page.locator('textarea[placeholder="回覆這則評價..."]').fill(REPLY_CONTENT);
    await page.getByRole('button', { name: '送出回覆' }).click();

    await expect(page.getByText(REPLY_CONTENT)).toBeVisible();
    // 已有回覆後，回覆表單應被回覆內容取代，不再顯示送出按鈕
    await expect(page.getByRole('button', { name: '送出回覆' })).not.toBeVisible();
  });

  test('E2E-RVREPLY-04: 店主標記為已處理後，這筆從待處理列表移到已處理列表（畫面與 /v2/reviews/managed 一致）', async ({ page }: { page: Page }) => {
    // 每個 test() 都是全新的 page（即使 describe 設為 serial 也不共用瀏覽器狀態），
    // 還原登入並導覽回管理頁才能看到 test 03 留下的（伺服器端持久化的）回覆狀態
    await restoreSession(page, ownerSession);
    await page.goto('/dashboard/reviews');
    await expect(page.getByText(REVIEW_TITLE)).toBeVisible();

    // 同步訊號用 PUT /handle 的回應，而不是畫面文字：按鈕本身就叫「標記為已處理」，按下後頁面又立刻重新整理列表、
    // 按鈕隨整筆評價消失，用「畫面上出現『已處理』」當訊號，早一點評估恆為真、晚一點找不到（Sprint 287 實測兩種結果都出現過），
    // 之後立刻查 API 又會搶在伺服器提交之前
    const [handled] = await Promise.all([
      page.waitForResponse((r) => r.request().method() === 'PUT' && r.url().includes('/handle')),
      page.getByRole('button', { name: '標記為已處理' }).click(),
    ]);
    expect(handled.status(), await handled.text()).toBe(200);
    const reviewId = (await handled.json()).data.reviewId as string;

    // 預設篩選是「未處理」：這筆離開後列表為空
    await expect(page.getByText('目前沒有待處理的評價')).toBeVisible();

    // 切到「已處理」篩選：這筆出現在這裡，且按鈕變成「標記為未處理」
    await page.getByRole('combobox').click();
    await page.getByRole('option', { name: '已處理' }).click();
    await expect(page.getByText(REVIEW_TITLE)).toBeVisible();
    await expect(page.getByRole('button', { name: '標記為未處理' })).toBeVisible();

    // 後端查詢與畫面一致（PUT 已回應，伺服器端已提交）
    const headers = bearer(ownerSession);
    const handledList = await page.request.get(`${API_BASE}/v2/reviews/managed?isHandled=true&size=50`, { headers });
    expect(handledList.status(), await handledList.text()).toBe(200);
    const handledIds = (await handledList.json()).data.reviews.map((r: { reviewId: string }) => r.reviewId);

    const unhandledList = await page.request.get(`${API_BASE}/v2/reviews/managed?isHandled=false&size=50`, { headers });
    expect(unhandledList.status(), await unhandledList.text()).toBe(200);
    const unhandledIds = (await unhandledList.json()).data.reviews.map((r: { reviewId: string }) => r.reviewId);

    expect(handledIds, '標記後的這筆應出現在已處理列表').toContain(reviewId);
    expect(unhandledIds, '標記後的這筆不應再出現在未處理列表').not.toContain(reviewId);
  });

  test('E2E-RVREPLY-05: 買家在商品評價頁看得到店家回覆', async ({ page }: { page: Page }) => {
    // GET /v2/reviews/listing/{id} 要求登入（@PreAuthorize("isAuthenticated()")），匿名訪客會得到 401 而顯示「無法載入評價」
    await restoreSession(page, customerSession);
    await page.goto(`/reviews/product/${productId}`);
    await expect(page.getByText(REVIEW_TITLE)).toBeVisible();
    await expect(page.getByText('店家回覆')).toBeVisible();
    await expect(page.getByText(REPLY_CONTENT)).toBeVisible();
  });
});
