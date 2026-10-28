import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { authHeaders, seedStore } from './helpers/store';

/**
 * AT-M17-004: 店鋪 Profile 更新 E2E 測試（真實後端，FRD US-M17-003 / AC-M17-003-1）
 *
 * 測試步驟：
 * 1. StoreOwner 登入系統
 * 2. 前往店鋪列表頁面 (/dashboard/tenants)，進入自己店鋪的詳情
 * 3. 點「編輯店鋪」進入編輯表單
 * 4. 修改店鋪資訊
 * 5. 儲存變更（成功後導回店鋪詳情）
 * 6. 驗證詳情頁顯示新值，且後端真的存下了（另外讀一次 API，不只相信畫面）
 *
 * ── 本 spec 的歷史（DEF-425，Sprint 308 發現、Sprint 310 修復）────────────────────────
 * 原本兩個案例在守門裡**每次都跳過**，UI 層實際上什麼都沒測：
 * - 用 `registerAndLogin` 新註冊的帳號（沒有店鋪）進列表頁，列表顯示「尚無店鋪資料」，找不到「編輯」就 `test.skip()`；
 * - 就算有店鋪，選擇器也已過期：列表卡片只有「查看詳情」，「編輯店鋪」在詳情頁；成功後不是顯示「成功」字樣，而是
 *   `router.push` 回詳情頁；編輯表單根本沒有「店鋪描述」欄位（原本寫「如果存在」才填，所以永遠沒填）。
 *
 * 現在用 `seedStore` 保證前提（有店鋪的店主），**不再有任何 `test.skip()`**——前提不成立就該紅燈（比照 at-m17-002）。
 * API 層的覆蓋在 `TenantControllerE2ETest`；本規格鎖的是 UI 這一層：表單送出的內容正確、其他欄位不被洗掉。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

interface TenantProfile {
  storeName: string;
  slug: string;
  storeDescription: string | null;
  businessType: string;
  contactEmail: string;
  contactPhone: string | null;
}

/** 以店主身分讀回店鋪（GET /v2/tenants/{id}）：驗證「後端真的存了」，不只相信畫面。 */
async function readTenant(page: Page, tenantId: string): Promise<TenantProfile> {
  const response = await page.request.get(`${API_BASE}/v2/tenants/${tenantId}`, {
    headers: await authHeaders(page),
  });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).data as TenantProfile;
}

/** 從店鋪列表走到編輯表單：列表 →「查看詳情」→「編輯店鋪」，每一步都確認網址。 */
async function openEditForm(page: Page, tenantId: string): Promise<void> {
  await page.goto('/dashboard/tenants');
  await page.getByRole('link', { name: '查看詳情' }).click();
  await expect(page).toHaveURL(new RegExp(`/dashboard/tenants/${tenantId}$`));
  await page.getByRole('link', { name: '編輯店鋪' }).click();
  await expect(page).toHaveURL(new RegExp(`/dashboard/tenants/${tenantId}/edit$`));
  await expect(page.locator('#storeName')).toBeVisible();
}

/** 按「儲存更改」並等 PUT 回應；成功後應導回詳情頁。 */
async function saveAndExpectDetail(page: Page, tenantId: string): Promise<void> {
  const saved = page.waitForResponse(
    (r) => r.url().includes(`/v2/tenants/${tenantId}`) && r.request().method() === 'PUT',
    { timeout: 20_000 }
  );
  await page.getByRole('button', { name: '儲存更改' }).click();
  expect((await saved).status()).toBe(200);
  await expect(page).toHaveURL(new RegExp(`/dashboard/tenants/${tenantId}$`));
}

/** 詳情頁「基本資訊／聯絡資訊」的唯讀欄位：標籤與輸入框包在同一個 div.space-y-1。 */
function detailField(page: Page, label: string) {
  return page
    .locator('div.space-y-1')
    .filter({ has: page.getByText(label, { exact: true }) })
    .locator('input');
}

test.describe('AT-M17-004: 店鋪 Profile 更新（真實後端）', () => {
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
        storeLabel: 'E2E Profile 更新店',
      });
      tenantId = seeded.tenantId;
      ownerEmail = seeded.owner.email;
      ownerPassword = seeded.owner.password;
    } finally {
      await page.close();
    }
  });

  test('E2E-M17-004-01: 店主在編輯表單更新名稱／網址代稱／信箱／電話 → 導回詳情頁顯示新值，後端已儲存', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, ownerEmail, ownerPassword);
    const before = await readTenant(page, tenantId);
    expect(before.storeDescription, '前提：seedStore 申請開店時帶了店鋪描述').toBeTruthy();
    await openEditForm(page, tenantId);

    const stamp = Date.now();
    const updated = {
      storeName: `E2E 更新後店鋪 ${stamp}`,
      slug: `e2e-updated-${stamp}`,
      contactEmail: `updated-${stamp}@example.com`,
      contactPhone: '+886-988-888-888',
    };
    await page.locator('#storeName').fill(updated.storeName);
    await page.locator('#slug').fill(updated.slug);
    await page.locator('#contactEmail').fill(updated.contactEmail);
    await page.locator('#contactPhone').fill(updated.contactPhone);
    await saveAndExpectDetail(page, tenantId);

    // 詳情頁顯示新值（不是編輯前的舊值）
    await expect(page.getByRole('heading', { name: updated.storeName })).toBeVisible();
    await expect(detailField(page, '聯絡信箱')).toHaveValue(updated.contactEmail);
    await expect(detailField(page, '聯絡電話')).toHaveValue(updated.contactPhone);

    // 後端真的存下了；編輯表單沒有的欄位（店鋪描述）與沒改的營業類型不可被洗掉
    const stored = await readTenant(page, tenantId);
    expect(stored).toMatchObject({
      ...updated,
      storeDescription: before.storeDescription,
      businessType: before.businessType,
    });
  });

  test('E2E-M17-004-02: 只改店鋪名稱 → 其他欄位維持原值（表單不會把沒改的欄位洗掉）', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, ownerEmail, ownerPassword);
    const before = await readTenant(page, tenantId);
    // 前提：描述還在，下面「沒改的欄位逐一比對」才有東西可比（不然 null 對 null 永遠相等）
    expect(before.storeDescription, '前提：店鋪描述應還在').toBeTruthy();

    await openEditForm(page, tenantId);
    const newName = `E2E 只改名稱 ${Date.now()}`;
    await page.locator('#storeName').fill(newName);
    await saveAndExpectDetail(page, tenantId);
    await expect(page.getByRole('heading', { name: newName })).toBeVisible();

    const after = await readTenant(page, tenantId);
    expect(after.storeName).toBe(newName);
    // 沒改的欄位逐一比對；storeDescription 不在表單裡，也不該因為儲存而消失
    for (const field of ['slug', 'storeDescription', 'businessType', 'contactEmail', 'contactPhone'] as const) {
      expect(after[field], `只改名稱時 ${field} 不該變`).toEqual(before[field]);
    }
  });
});
