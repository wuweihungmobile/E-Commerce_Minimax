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
 *
 * ── Sprint 314（DEF-432）：編輯表單新增「店鋪描述」欄位 ────────────────────────────────
 * FRD US-M17-003 / AC-M17-003-1 允許店主修改店鋪描述，後端 `PUT /v2/tenants/{id}` 也收（≤ 1000 字），表單先前沒有這個欄位。
 * 現在表單會預填目前的描述；**沒改過就不送出**（後端 null = 不變更），改過才送（空字串 = 清空）。案例 01／02 因此改為檢查
 * 「描述有被預填、沒改就沒送、沒被洗掉」，03／04 鎖「改了會存下」與「清空會真的清掉」。
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

/**
 * 按「儲存更改」並等 PUT 回應；成功後應導回詳情頁。回傳這次 PUT **實際送出的內容**——
 * 「沒改的欄位不送」是表單的行為，只讀回後端的結果看不出來。
 */
async function saveAndExpectDetail(page: Page, tenantId: string): Promise<Record<string, unknown>> {
  const saved = page.waitForResponse(
    (r) => r.url().includes(`/v2/tenants/${tenantId}`) && r.request().method() === 'PUT',
    { timeout: 20_000 }
  );
  await page.getByRole('button', { name: '儲存更改' }).click();
  const response = await saved;
  expect(response.status()).toBe(200);
  await expect(page).toHaveURL(new RegExp(`/dashboard/tenants/${tenantId}$`));
  return response.request().postDataJSON() as Record<string, unknown>;
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
    // 表單預填目前的店鋪描述（DEF-432：先前沒有這個欄位）
    await expect(page.locator('#storeDescription')).toHaveValue(before.storeDescription as string);

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
    const sent = await saveAndExpectDetail(page, tenantId);
    expect(sent, '沒改店鋪描述就不送出（後端 null = 不變更）').not.toHaveProperty('storeDescription');

    // 詳情頁顯示新值（不是編輯前的舊值）
    await expect(page.getByRole('heading', { name: updated.storeName })).toBeVisible();
    await expect(detailField(page, '聯絡信箱')).toHaveValue(updated.contactEmail);
    await expect(detailField(page, '聯絡電話')).toHaveValue(updated.contactPhone);

    // 後端真的存下了；沒改的店鋪描述與營業類型不可被洗掉
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
    const sent = await saveAndExpectDetail(page, tenantId);
    expect(sent.storeName).toBe(newName);
    expect(sent, '沒改店鋪描述就不送出（後端 null = 不變更）').not.toHaveProperty('storeDescription');
    await expect(page.getByRole('heading', { name: newName })).toBeVisible();

    const after = await readTenant(page, tenantId);
    expect(after.storeName).toBe(newName);
    // 沒改的欄位逐一比對；表單裡的店鋪描述沒動，也不該因為儲存其他欄位而消失
    for (const field of ['slug', 'storeDescription', 'businessType', 'contactEmail', 'contactPhone'] as const) {
      expect(after[field], `只改名稱時 ${field} 不該變`).toEqual(before[field]);
    }
  });

  test('E2E-M17-004-03: 修改店鋪描述 → 只送出描述的新值、後端存下、其他欄位不變；重新打開表單帶出新描述', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, ownerEmail, ownerPassword);
    const before = await readTenant(page, tenantId);
    await openEditForm(page, tenantId);
    const description = page.locator('#storeDescription');
    // 後端上限 1000 字（TenantUpdateRequest）：表單一開始就限制輸入長度，不要等後端回 400
    await expect(description).toHaveAttribute('maxlength', '1000');

    const newDescription = `E2E 新的店鋪描述 ${Date.now()}\n第二行`;
    await description.fill(newDescription);
    const sent = await saveAndExpectDetail(page, tenantId);
    expect(sent.storeDescription).toBe(newDescription);

    const after = await readTenant(page, tenantId);
    expect(after.storeDescription).toBe(newDescription);
    for (const field of ['storeName', 'slug', 'businessType', 'contactEmail', 'contactPhone'] as const) {
      expect(after[field], `只改描述時 ${field} 不該變`).toEqual(before[field]);
    }

    // 重新打開表單：帶出的是剛存的描述（不是空白、也不是舊值）
    await openEditForm(page, tenantId);
    await expect(page.locator('#storeDescription')).toHaveValue(newDescription);
  });

  test('E2E-M17-004-04: 清空店鋪描述 → 後端真的清掉（不是保留舊值）；其他欄位不變', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, ownerEmail, ownerPassword);
    const before = await readTenant(page, tenantId);
    // 前提：上一個案例存了新描述，這裡才有東西可清
    expect(before.storeDescription, '前提：此時店鋪還有描述').toBeTruthy();

    await openEditForm(page, tenantId);
    await page.locator('#storeDescription').fill('');
    const sent = await saveAndExpectDetail(page, tenantId);
    expect(sent.storeDescription, '清空要明確送出空字串（後端 null 才是不變更）').toBe('');

    const after = await readTenant(page, tenantId);
    expect(after.storeDescription ?? '', '描述應已被清掉').toBe('');
    for (const field of ['storeName', 'slug', 'businessType', 'contactEmail', 'contactPhone'] as const) {
      expect(after[field], `只清空描述時 ${field} 不該變`).toEqual(before[field]);
    }

    await openEditForm(page, tenantId);
    await expect(page.locator('#storeDescription')).toHaveValue('');
  });
});
