import { test, expect, APIRequestContext, APIResponse } from '@playwright/test';
import { Account, seedStore } from './helpers/store';

/**
 * AT-STORE-LESS-SELLER-REAL: 沒有店鋪的 SELLER／HOST 不得碰店家層資料（真實後端，完全不 mock）—— Sprint 240（DEF-326）
 *
 * 為什麼需要：自助註冊可以直接得到 SELLER 或 HOST，沒有審核、不建租戶；開店核准後的角色是 STORE_OWNER。
 * 所以正式環境的 SELLER／HOST 幾乎都是「還沒開店的人」，租戶落在系統租戶佔位值，卻持有商品、房源、定價、運費模板、
 * CMS、貼文的寫入權限，各擁有權檢查又是「資源的租戶 == 呼叫者的租戶」——於是他們與平台自營資料彼此互通。
 * 使用者拍板（Sprint 238 結尾）：未歸屬任何店鋪者不得寫入，與 PRD「需先申請開店」一致。
 *
 * 做法：沒有店鋪的 SELLER／HOST 在簽發 token 時以買家身分簽發（權限完全由 token 的 role 宣告決定）。判斷依據是租戶、不是角色標籤。
 *
 * 探針刻意用「不存在的隨機 ID」：權限檢查先於資源查詢，所以通過權限的人得到 404、被擋下的人得到 403 E-1007，
 * 而且不會在共用的資料庫留下任何資料（例如在系統租戶新增一個對所有買家結帳生效的運費模板）。
 * 對照組是走真實開店流程的店主：同樣的探針通過權限檢查（404），並能讀賣家儀表板（修復前他呼叫不了）。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const PASSWORD = 'Test123!';
const NO_SUCH_ID = '00000000-0000-4000-8000-0000000000aa';
const DENIED = 'E-1007';

type Session = { token: string; role: string };

async function registerAndLoginViaApi(request: APIRequestContext, userType: 'SELLER' | 'HOST'): Promise<Session> {
  const email = `e2e-s240-${userType.toLowerCase()}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
  const register = await request.post(`${API_BASE}/v2/auth/register`, { data: { email, password: PASSWORD, userType } });
  expect(register.status(), await register.text()).toBe(201);
  return loginViaApi(request, email, PASSWORD);
}

async function loginViaApi(request: APIRequestContext, email: string, password: string): Promise<Session> {
  const login = await request.post(`${API_BASE}/v2/auth/login`, { data: { email, password } });
  expect(login.status(), await login.text()).toBe(200);
  const data = (await login.json()).data;
  return { token: data.accessToken as string, role: data.user.role as string };
}

function bearer(session: Session): { Authorization: string } {
  return { Authorization: `Bearer ${session.token}` };
}

/** 被擋下：403 且錯誤碼是「權限不足」（不是別的 403）。 */
async function expectDenied(response: APIResponse, what: string): Promise<void> {
  expect(response.status(), `${what}：${await response.text()}`).toBe(403);
  expect((await response.json()).code, what).toBe(DENIED);
}

test.describe('AT-STORE-LESS-SELLER-REAL: 沒有店鋪的 SELLER／HOST 不得碰店家層資料（真實後端）', () => {
  // 開店流程只需要做一次
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      ({ owner } = await seedStore(page, { businessType: 'RETAIL_ONLY', storeLabel: 'E2E 店家層對照店' }));
    } finally {
      await page.close();
    }
  });

  test('E2E-SLS-01: 自助註冊的 SELLER（沒有店鋪）→ 以買家身分登入；商品、運費模板、CMS、ERP、賣家儀表板全部被擋', async ({ request }) => {
    const seller = await registerAndLoginViaApi(request, 'SELLER');
    // soft：角色不對時，下面的探針結果也要一起列出，不被這一行擋住
    expect.soft(seller.role, '沒有店鋪的 SELLER 以買家身分簽發').toBe('BUYER');

    await expectDenied(
      await request.put(`${API_BASE}/v2/products/${NO_SUCH_ID}`, { headers: bearer(seller), data: {} }),
      '更新商品'
    );
    await expectDenied(
      await request.put(`${API_BASE}/v2/shipping-templates/${NO_SUCH_ID}`, { headers: bearer(seller), data: {} }),
      '更新運費模板（結帳時對所有一般買家生效）'
    );
    await expectDenied(
      await request.put(`${API_BASE}/v2/cms/pages/${NO_SUCH_ID}`, { headers: bearer(seller), data: {} }),
      '更新 CMS 頁面（線上內容）'
    );
    await expectDenied(await request.get(`${API_BASE}/v2/dashboard/suppliers`, { headers: bearer(seller) }), 'ERP：供應商');
    await expectDenied(await request.get(`${API_BASE}/v2/seller/dashboard`, { headers: bearer(seller) }), '賣家儀表板');
  });

  test('E2E-SLS-02: 自助註冊的 HOST（沒有店鋪）→ 以買家身分登入；房源與 CMS 被擋', async ({ request }) => {
    const host = await registerAndLoginViaApi(request, 'HOST');
    expect.soft(host.role, '沒有店鋪的 HOST 以買家身分簽發').toBe('BUYER');

    await expectDenied(
      await request.put(`${API_BASE}/v2/rooms/${NO_SUCH_ID}`, { headers: bearer(host), data: {} }),
      '更新房源'
    );
    await expectDenied(
      await request.put(`${API_BASE}/v2/cms/pages/${NO_SUCH_ID}`, { headers: bearer(host), data: {} }),
      '更新 CMS 頁面'
    );
  });

  test('E2E-SLS-03: 對照——走真實開店流程的店主通過同樣的權限檢查（404，不是 403），並能讀賣家儀表板', async ({ request }) => {
    const ownerSession = await loginViaApi(request, owner.email, owner.password);
    expect(ownerSession.role).toBe('STORE_OWNER');

    for (const [what, url] of [
      ['更新商品', `${API_BASE}/v2/products/${NO_SUCH_ID}`],
      ['更新運費模板', `${API_BASE}/v2/shipping-templates/${NO_SUCH_ID}`],
      ['更新 CMS 頁面', `${API_BASE}/v2/cms/pages/${NO_SUCH_ID}`],
    ] as const) {
      const response = await request.put(url, { headers: bearer(ownerSession), data: {} });
      expect(response.status(), `${what}：通過權限檢查後找不到資源，是 404，不是 403：${await response.text()}`).toBe(404);
    }

    const dashboard = await request.get(`${API_BASE}/v2/seller/dashboard`, { headers: bearer(ownerSession) });
    expect(dashboard.status(), `真正的店主要能讀自己店鋪的儀表板：${await dashboard.text()}`).toBe(200);
  });
});
