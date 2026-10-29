import { test, expect, Page } from '@playwright/test';
import { registerAndLogin, loginOnly, postAuthApi } from './helpers/auth';
import { Account, adminAccessToken, seedStore, setStoreStatusWithToken } from './helpers/store';

/**
 * AT-DASHBOARD-TENANT-NAV-REAL: 店主端通往「店鋪管理」與「功能開關」的導覽入口（DEF-430，Sprint 314，真實後端）
 *
 * Sprint 310 的探針（收集 `a[href]`）量到：儀表板首頁的快速入口沒有「店鋪管理」，店鋪詳情頁只有「返回列表」與「編輯店鋪」，
 * 沒有通往 `/dashboard/tenants/{id}/features` 的連結——店主端的功能開關頁（FRD US-M17-006）只能手打網址。
 * `at-m17-003`、`at-dashboard-tenant-features-real` 因此都用網址直接前往；本規格鎖的是入口本身：
 *
 * - 儀表板首頁 →「店鋪管理」→ 店鋪列表 →「查看詳情」→「功能開關」→ 功能開關頁，全程只點連結，不輸入網址；
 * - 入口的可見性與功能開關頁自己的放行條件一致（`features/page.tsx` 只放行 STORE_OWNER／ADMIN，其他成員進去會被導回店鋪列表）：
 *   店鋪成員（STORE_STAFF）沒有入口；只有核准中（ACTIVE）的店鋪有入口，被停權的沒有（『編輯店鋪』不受此限，見 NAV-03）。
 *
 * 資料全由真實流程建立：店主開店（seedStore）→ 受邀人註冊 → 店主邀請為 STORE_STAFF → 受邀人接受（比照
 * at-store-member-revocation-real）；停權用管理員 API。登入端點有 IP 限流，所以 API 層的登入一律走 `postAuthApi`（429 自動重試）。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

/** 以 API 登入並回傳授權標頭——不動瀏覽器的登入狀態。 */
async function apiAuthHeaders(page: Page, account: Account): Promise<{ Authorization: string }> {
  const login = await postAuthApi(page.request, `${API_BASE}/v2/auth/login`, {
    email: account.email,
    password: account.password,
  });
  expect(login.status(), await login.text()).toBe(200);
  return { Authorization: `Bearer ${(await login.json()).data.accessToken as string}` };
}

test.describe('AT-DASHBOARD-TENANT-NAV-REAL: 店鋪管理與功能開關的導覽入口（真實後端）', () => {
  // 後面的案例依賴前面建立的店鋪、成員；最後一個案例會把店鋪停權
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let tenantId: string;
  let owner: Account;
  let staff: Account;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(180_000);
    const ownerPage = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(ownerPage, { businessType: 'RETAIL_ONLY', storeLabel: 'E2E 導覽入口店' });
      tenantId = seeded.tenantId;
      owner = seeded.owner;
    } finally {
      await ownerPage.close();
    }

    // 店鋪成員：註冊 → 店主邀請為 STORE_STAFF → 受邀人接受。用另一個頁面（乾淨的 localStorage），登入狀態互不干擾
    const staffPage = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const registered = await registerAndLogin(staffPage);
      staff = { email: registered.email, password: registered.password };
      const invite = await staffPage.request.post(`${API_BASE}/v2/tenants/${tenantId}/members/invite`, {
        headers: await apiAuthHeaders(staffPage, owner),
        data: { userId: registered.userId, role: 'STORE_STAFF' },
      });
      expect(invite.status(), await invite.text()).toBe(201);
      const accept = await staffPage.request.post(`${API_BASE}/v2/tenants/${tenantId}/members/invite/accept`, {
        headers: await apiAuthHeaders(staffPage, staff),
      });
      expect(accept.status(), await accept.text()).toBe(200);
    } finally {
      await staffPage.close();
    }
  });

  test('E2E-TENANT-NAV-01: 店主從儀表板首頁一路點連結走到功能開關頁（店鋪管理 → 查看詳情 → 功能開關）', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard');

    // 1. 儀表板首頁的快速入口
    const entry = page.getByTestId('dashboard-tenants-link');
    await expect(entry).toBeVisible({ timeout: 15_000 });
    await expect(entry).toHaveText('店鋪管理');
    await entry.click();
    await expect(page).toHaveURL(/\/dashboard\/tenants$/);

    // 2. 店鋪列表 → 詳情
    await page.getByRole('link', { name: '查看詳情' }).click();
    await expect(page).toHaveURL(new RegExp(`/dashboard/tenants/${tenantId}$`));
    // 對照組：同一排按鈕裡的「編輯店鋪」在，代表這一排確實渲染了，「功能開關」不是被別的原因擋掉
    await expect(page.getByRole('link', { name: '編輯店鋪' })).toBeVisible();

    // 3. 詳情 → 功能開關頁，頁面真的顯示功能開關（不是被導回列表）。先斷言入口可見：沒有入口時幾秒內就紅，
    //    不要讓 click 等到整個案例逾時（90 秒）才失敗
    const featuresEntry = page.getByRole('link', { name: '功能開關' });
    await expect(featuresEntry).toBeVisible();
    await featuresEntry.click();
    await expect(page).toHaveURL(new RegExp(`/dashboard/tenants/${tenantId}/features$`));
    await expect(page.locator('#CMS_ENABLED')).toBeVisible({ timeout: 15_000 });
  });

  test('E2E-TENANT-NAV-02: 店鋪成員（STORE_STAFF）看得到店鋪詳情，但沒有「功能開關」入口（那個頁面會把他導回列表）', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, staff.email, staff.password);
    // 前提：登入後帶的真的是店員角色與這間店鋪（不是退回買家，否則下面的「沒有入口」什麼都證明不了）
    const user = await page.evaluate(() => JSON.parse(localStorage.getItem('user') ?? '{}') as { role?: string; tenantId?: string });
    expect(user.role, '前提：受邀人接受後登入，角色是 STORE_STAFF').toBe('STORE_STAFF');
    expect(user.tenantId, '前提：登入帶這間店鋪的租戶').toBe(tenantId);

    await page.goto(`/dashboard/tenants/${tenantId}`);
    await expect(page.getByText(tenantId, { exact: true }), '詳情頁有載入（顯示店鋪 ID）').toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('link', { name: '功能開關' })).toHaveCount(0);
  });

  test('E2E-TENANT-NAV-03: 店鋪被停權後，店主在詳情頁看不到「功能開關」入口（只有核准中的店鋪能管理功能），但「編輯店鋪」仍在（Sprint 315：非 ACTIVE 允許更新）', async ({
    page,
  }: {
    page: Page;
  }) => {
    await setStoreStatusWithToken(page, await adminAccessToken(page), tenantId, 'SUSPENDED');

    await loginOnly(page, owner.email, owner.password);
    await page.goto(`/dashboard/tenants/${tenantId}`);
    // 前提：詳情頁載入了、而且確實是停權狀態
    await expect(page.getByText('已停權').first()).toBeVisible({ timeout: 15_000 });
    // 對照組：同一排按鈕裡的『編輯店鋪』在（FRD US-M17-003 邊界條件允許非 ACTIVE 的店鋪更新），
    // 所以『功能開關』不見是它自己的 ACTIVE 條件，不是整排按鈕都沒渲染
    await expect(page.getByRole('link', { name: '編輯店鋪' })).toBeVisible();
    await expect(page.getByRole('link', { name: '功能開關' })).toHaveCount(0);
  });
});
