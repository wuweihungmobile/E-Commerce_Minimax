import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { authHeaders, seedStore } from './helpers/store';

/**
 * AT-M17-003: Feature Toggle 更新 E2E 測試（真實後端，FRD US-M17-006）
 *
 * 測試步驟：
 * 1. StoreOwner 登入系統
 * 2. 前往店鋪功能開關頁 (/dashboard/tenants/:id/features)
 * 3. 查看 Feature Toggle 列表
 * 4. 變更某個 Toggle 的狀態
 * 5. 驗證變更成功（畫面、後端、重新整理後都一致）
 *
 * ── 本 spec 的歷史（DEF-425，Sprint 308 發現、Sprint 310 修復）────────────────────────
 * 原本兩個案例在守門裡**每次都跳過**，UI 層實際上什麼都沒測：
 * - 用 `registerAndLogin` 新註冊的帳號（沒有店鋪）進列表頁，找不到店鋪連結就在 `beforeEach` 裡 `test.skip()`；
 * - 就算進得去，選擇器也已過期：開關是 `<button role="switch">`（`components/ui/switch.tsx`），不是
 *   `input[type="checkbox"]`；
 * - 「Feature Toggle 數值更新」找的「編輯」按鈕與數值輸入框，在店主端根本不存在——數量配額（MAX_PRODUCTS 等）在
 *   功能頁是唯讀的用量條，店主端 API 也拒絕更新（`PUT /v2/dashboard/tenants/features/{數量配額}` 回 `E-9000`）。
 *   平台管理員雖有 `POST /v2/admin/feature-toggles` 可寫 `config`，但那是 SUPER_ADMIN 的 API、沒有任何畫面呼叫它，
 *   也不是 AT-M17-003（StoreOwner）的情境。**店主端沒有對應的產品功能可測，這個案例移除**，
 *   改測 FRD AC-M17-006-1／AC-M17-006-2 的另一半：需審核的功能點開後不會直接生效。
 *
 * 現在用 `seedStore` 保證前提（有店鋪的店主），**不再有任何 `test.skip()`**——前提不成立就該紅燈（比照 at-m17-002）。
 * 本 spec 與 at-dashboard-tenant-features-real 直接用網址前往功能開關頁；通往它的導覽入口（原本沒有，DEF-430，Sprint 314 補上）
 * 由 at-dashboard-tenant-nav-real 負責。
 * Admin 核准待審核功能由 at-admin-tenant-features-real 負責，API 層的覆蓋在 `TenantControllerE2ETest`。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

interface FeatureInfo {
  featureKey: string;
  isEnabled: boolean;
  status: 'ACTIVE' | 'PENDING' | 'INACTIVE';
  requestedAt: string | null;
}

/** 以店主身分讀回某功能目前的狀態（GET /v2/dashboard/tenants/features）：驗證「後端真的存了」，不只相信畫面。 */
async function readFeature(page: Page, featureKey: string): Promise<FeatureInfo> {
  const response = await page.request.get(`${API_BASE}/v2/dashboard/tenants/features`, {
    headers: await authHeaders(page),
  });
  expect(response.status(), await response.text()).toBe(200);
  const feature = ((await response.json()).data.features as FeatureInfo[]).find((f) => f.featureKey === featureKey);
  expect(feature, `功能清單應含 ${featureKey}`).toBeTruthy();
  return feature as FeatureInfo;
}

/** 該功能那一列：Switch 本身只有 id，要往上找 justify-between 的列，才能把斷言限定在這一列（頁尾說明文字也有同樣字樣）。 */
function featureRow(page: Page, featureKey: string) {
  return page.locator(`xpath=//*[@id="${featureKey}"]/ancestor::div[contains(@class,"justify-between")][1]`);
}

/** 點該功能的開關並等 PUT 回應。 */
async function clickToggle(page: Page, featureKey: string) {
  const response = page.waitForResponse(
    (r) => r.url().includes(`/v2/dashboard/tenants/features/${featureKey}`) && r.request().method() === 'PUT',
    { timeout: 20_000 }
  );
  await page.locator(`#${featureKey}`).click();
  return response;
}

test.describe('AT-M17-003: Feature Toggle 更新（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let tenantId: string;
  let ownerEmail: string;
  let ownerPassword: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: 'E2E 功能開關更新店',
      });
      tenantId = seeded.tenantId;
      ownerEmail = seeded.owner.email;
      ownerPassword = seeded.owner.password;
    } finally {
      await page.close();
    }
  });

  test('E2E-M17-003-01: 免審核功能（CMS）關閉再開啟 → 畫面即時反映、後端已儲存、重新整理後仍保持', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, ownerEmail, ownerPassword);
    await page.goto(`/dashboard/tenants/${tenantId}/features`);
    const toggle = page.locator('#CMS_ENABLED');
    const row = featureRow(page, 'CMS_ENABLED');
    await expect(toggle).toBeVisible({ timeout: 15_000 });

    // 前提：CMS_ENABLED 免審核、預設開啟
    await expect(toggle).toHaveAttribute('aria-checked', 'true');
    expect(await readFeature(page, 'CMS_ENABLED')).toMatchObject({ isEnabled: true, status: 'ACTIVE' });

    // 關閉：立即生效（成功訊息 3 秒後自動消失，不是規格訂的契約，所以只斷言持久的狀態）
    expect((await clickToggle(page, 'CMS_ENABLED')).status()).toBe(200);
    await expect(toggle).toHaveAttribute('aria-checked', 'false');
    await expect(row.getByText('已停用', { exact: true })).toBeVisible();
    expect(await readFeature(page, 'CMS_ENABLED')).toMatchObject({ isEnabled: false, status: 'INACTIVE' });

    // 重新整理後仍是關閉：不是只有畫面上的樂觀更新
    await page.reload();
    await expect(page.locator('#CMS_ENABLED')).toHaveAttribute('aria-checked', 'false');

    // 再開啟：免審核的功能直接生效，不會變成待審核（AC-M17-006-2）
    expect((await clickToggle(page, 'CMS_ENABLED')).status()).toBe(200);
    await expect(page.locator('#CMS_ENABLED')).toHaveAttribute('aria-checked', 'true');
    await expect(featureRow(page, 'CMS_ENABLED').getByText('已啟用', { exact: true })).toBeVisible();
    expect(await readFeature(page, 'CMS_ENABLED')).toMatchObject({ isEnabled: true, status: 'ACTIVE' });
  });

  test('E2E-M17-003-02: 需審核的功能（促銷）→ 沒申請過是「已停用」；點開送出申請後是「待審核」、開關仍是關的、功能沒有生效 → 重新整理後一致', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, ownerEmail, ownerPassword);
    await page.goto(`/dashboard/tenants/${tenantId}/features`);
    const toggle = page.locator('#PROMO_ENABLED');
    const row = featureRow(page, 'PROMO_ENABLED');
    await expect(toggle).toBeVisible({ timeout: 15_000 });

    // 前提（DEF-429）：核准開店時系統替需審核的功能建了預設資料列，但店主沒申請過 → 是「已停用」，不是「待審核」，
    // 後端也沒有申請時間。（修復前這三項在沒人申請時就顯示「待審核」，下面「點了才變待審核」的斷言因此沒有鑑別力。）
    await expect(toggle).toHaveAttribute('aria-checked', 'false');
    await expect(row.getByText('已停用', { exact: true })).toBeVisible();
    await expect(row.getByText('待審核', { exact: true })).toHaveCount(0);
    expect(await readFeature(page, 'PROMO_ENABLED')).toMatchObject({
      isEnabled: false,
      status: 'INACTIVE',
      requestedAt: null,
    });

    const response = await clickToggle(page, 'PROMO_ENABLED');
    expect(response.status()).toBe(200);
    // AC-M17-006-1：系統記錄申請、等待 Admin 審核——功能本身沒有啟用（DEF-247 的回歸防線）
    expect((await response.json()).data).toMatchObject({
      featureKey: 'PROMO_ENABLED',
      previousState: false,
      newState: false,
      status: 'PENDING_APPROVAL',
    });

    // DEF-428：點擊當下的畫面就要和後端一致——開關仍是關的、徽章是「待審核」、提示不是「已啟用」
    // （修復前畫面把開關翻成開、提示「功能「PROMO_ENABLED」已啟用」，重新整理後又變回關閉）
    await expect(toggle).toHaveAttribute('aria-checked', 'false');
    await expect(row.getByText('待審核', { exact: true })).toBeVisible();
    await expect(page.getByText('功能「PROMO_ENABLED」已啟用')).toHaveCount(0);

    // 後端：仍未啟用，但記下了申請（待審核）
    const pending = await readFeature(page, 'PROMO_ENABLED');
    expect(pending).toMatchObject({ isEnabled: false, status: 'PENDING' });
    expect(pending.requestedAt, '送出申請要記下申請時間').toBeTruthy();

    // 重新整理後仍然一致
    await page.reload();
    await expect(page.locator('#PROMO_ENABLED')).toHaveAttribute('aria-checked', 'false');
    await expect(featureRow(page, 'PROMO_ENABLED').getByText('待審核', { exact: true })).toBeVisible();
  });
});
