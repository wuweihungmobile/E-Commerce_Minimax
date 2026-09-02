import { test, expect, Page } from '@playwright/test';
import { loginOnly, registerAndLogin } from './helpers/auth';

/**
 * AT-M17-002: Admin 審核開店申請 E2E 測試
 *
 * 測試步驟：
 * 1. 註冊一位買家，並以買家身分送出一件開店申請（本測試自己建立前提）
 * 2. Admin 登入，前往 /admin/tenants
 * 3. 切到「審核中」分頁，找到剛才那件申請
 * 4. 核准（或駁回）
 * 5. 驗證後端回 200，且該件申請從待審核清單消失
 *
 * ── 本 spec 的歷史（DEF-058 / DEF-060 / DEF-061，Sprint 107→109）────────────────
 * 這支測試長期在 **skip／pass／fail 三態擺盪**，經三輪才收斂：
 *
 * - S107 修掉三個真缺陷（`:has-text("核准")` 子字串比對誤中列表頁的「已核准 (N)」tab、
 *   `waitForLoadState('domcontentloaded')` 在 client-side 導航下不保證任何事、
 *   `expect(x || true)` 恆真斷言），但把根因誤記為「前提無法由測試建立」。
 * - S108 更正：前提其實是 Flyway `V7__M17_Test_Data_Init.sql` 播的**固定一筆** `PENDING_REVIEW`
 *   租戶，版本化遷移只跑一次，測試核准掉之後不會復原 → 單次性固件被自己消耗。
 * - S109（本輪）找到真正的上游根因：**admin 審核台查錯了實體**（DEF-060——申請在
 *   `tenant_applications`，頁面卻查 `tenants?status=PENDING_REVIEW`，生產環境永遠是空的），
 *   而且**開店申請根本送不出去**（DEF-061——`TenantController` 的 class mapping 多了一層
 *   `/api`，疊上 context-path 後端點實際落在 `/api/api/v2/**`，前端打不到）。
 *
 * 兩者修好後，前提終於可以由測試自己建立：買家送申請走的就是前端走的那條路。
 * **因此本 spec 不再有任何 `test.skip()`**——每次執行都真的在測，前提不成立就該紅燈。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

/**
 * 以「新註冊的買家」身分送出一件開店申請，回傳店名供後續在畫面上定位。
 *
 * 刻意用真實 API 而非 UI 表單建立前提：本測試要驗的是 admin 審核台，
 * 申請表單自身由 at-m17-001 負責。用的是**前端實際會呼叫的同一個路徑**
 * （DEF-061 修復後 `/api/v2/tenants/apply`），所以這條前提不會與正式流程脫節。
 *
 * 必須以「已登入的買家」而非 Guest 送出：Guest 申請的 `userId` 為 null，
 * 後端核准時會拋 E_2008（無法授予 StoreOwner），前提就不成立了。
 */
async function submitApplicationAsNewBuyer(page: Page): Promise<string> {
  await registerAndLogin(page);

  const token = await page.evaluate(() => localStorage.getItem('accessToken'));
  expect(token, '買家註冊登入後應取得 accessToken').toBeTruthy();

  const storeName = `E2E 審核測試店鋪 ${Date.now()}`;
  const response = await page.request.post(`${API_BASE}/v2/tenants/apply`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      storeName,
      storeDescription: 'AT-M17-002 自建前提',
      businessType: 'RETAIL_ONLY',
      contactEmail: `store-${Date.now()}@example.com`,
      contactPhone: '0912345678',
    },
  });
  expect(
    response.status(),
    '開店申請應建立成功（若為 401 表示 DEF-061 的路徑問題重現）'
  ).toBe(201);

  return storeName;
}

/** 切換到 admin 帳號（先清掉買家的 token，否則 /login 可能直接被導離）。 */
async function loginAsAdmin(page: Page): Promise<void> {
  await page.evaluate(() => localStorage.clear());
  await loginOnly(page, 'admin@nextkey.local', 'Test123!');
}

/** 前往店鋪管理頁並切到「審核中」分頁，回傳等待中的申請列表回應。 */
async function openPendingTab(page: Page): Promise<void> {
  await page.goto('/admin/tenants');
  const pendingRequest = page.waitForResponse(
    (r) => r.url().includes('/v2/admin/tenant-applications') && r.status() === 200,
    { timeout: 20000 }
  );
  await page.getByTestId('tab-pending').click();
  await pendingRequest;
}

test.describe('AT-M17-002: Admin 審核開店申請', () => {
  test('Admin 核准開店申請 → 建立店鋪並從待審核清單移除', async ({ page }) => {
    const storeName = await submitApplicationAsNewBuyer(page);
    await loginAsAdmin(page);
    await openPendingTab(page);

    // 定位「我們自己建立的那一件」，而不是 .first()——併行執行時清單裡會有別的申請。
    const card = page.getByTestId('application-card').filter({ hasText: storeName });
    await expect(card, '剛送出的申請應出現在待審核清單中').toBeVisible({ timeout: 15000 });

    const approveResponse = page.waitForResponse(
      (r) =>
        r.url().includes('/v2/admin/tenant-applications/') &&
        r.url().endsWith('/approve') &&
        r.request().method() === 'POST',
      { timeout: 20000 }
    );
    await card.getByTestId('approve-application').click();
    expect((await approveResponse).status()).toBe(200);

    // 核准後：畫面出現成功訊息，且該申請已不在待審核清單中（後端已將其轉為 APPROVED）。
    await expect(page.getByTestId('action-message')).toContainText(storeName);
    await expect(card).toHaveCount(0, { timeout: 15000 });
  });

  test('Admin 駁回開店申請 → 需填原因，駁回後從待審核清單移除', async ({ page }) => {
    const storeName = await submitApplicationAsNewBuyer(page);
    await loginAsAdmin(page);
    await openPendingTab(page);

    const card = page.getByTestId('application-card').filter({ hasText: storeName });
    await expect(card, '剛送出的申請應出現在待審核清單中').toBeVisible({ timeout: 15000 });

    await card.getByTestId('reject-application').click();
    await card.getByTestId('reject-reason').fill('資料不全');

    const rejectResponse = page.waitForResponse(
      (r) =>
        r.url().includes('/v2/admin/tenant-applications/') &&
        r.url().endsWith('/reject') &&
        r.request().method() === 'POST',
      { timeout: 20000 }
    );
    await card.getByTestId('confirm-reject').click();
    expect((await rejectResponse).status()).toBe(200);

    await expect(page.getByTestId('action-message')).toContainText(storeName);
    await expect(card).toHaveCount(0, { timeout: 15000 });
  });
});
