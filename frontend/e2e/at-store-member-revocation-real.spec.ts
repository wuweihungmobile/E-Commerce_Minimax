import { test, expect, Page } from '@playwright/test';
import { registerAndLogin, loginOnly } from './helpers/auth';
import { Account, accessToken, authHeaders, seedStore } from './helpers/store';

/**
 * AT-STORE-MEMBER-REVOCATION-REAL: 店鋪成員被移除（或只是受邀）後，登入不得再帶店鋪租戶與員工角色（真實後端，完全不 mock）—— Sprint 235（DEF-329）
 *
 * 為什麼需要：`AuthService.resolveTenantForUser` 用不看狀態的 `findByUserId` 解析 JWT 的租戶，而 `removeMember`／`declineInvite`
 * 只改成員狀態、不改 `user.role`。讀碼推論：被移除的 STORE_STAFF 下次登入或換發 token（refresh 也走同一個解析）仍帶店鋪租戶與員工角色
 * ——移除成員收不回存取；只是受邀（尚未接受）的買家，下次登入就帶真實店鋪租戶。Sprint 234 稽核 agent 僅讀碼提出，未重現；本 spec 先重現再修。
 *
 * 資料全由真實流程建立：店主開店→受邀人註冊→店主邀請（STORE_STAFF）→受邀人接受→店主移除。
 * 判斷依據是 JWT 本身的 claims（`role`、`tenantId`）：那就是之後所有授權與租戶過濾的唯一來源。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const SYSTEM_TENANT_ID = '00000000-0000-0000-0000-000000000001';

type Claims = { sub: string; role: string; tenantId: string | null };

function claimsOf(token: string): Claims {
  return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8')) as Claims;
}

async function refreshTokenOf(page: Page): Promise<string> {
  const token = await page.evaluate(() => localStorage.getItem('refreshToken'));
  expect(token, '應已登入並持有 refreshToken').toBeTruthy();
  return token as string;
}

test.describe('AT-STORE-MEMBER-REVOCATION-REAL: 移除成員必須收回存取（真實後端）', () => {
  // 後面的案例依賴前面建立的店鋪、邀請與成員狀態
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let tenantId: string;
  let staff: Account;
  let staffId: string;
  let refreshBeforeRemoval: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      ({ owner, tenantId } = await seedStore(page, { businessType: 'RETAIL_ONLY', storeLabel: 'E2E 成員撤銷店' }));
    } finally {
      await page.close();
    }
  });

  test('E2E-MREV-01: 受邀人註冊（前提）；店主邀請為 STORE_STAFF；尚未接受前，受邀人重新登入不得帶店鋪租戶', async ({ page }: { page: Page }) => {
    staff = await registerAndLogin(page);
    staffId = claimsOf(await accessToken(page)).sub;

    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, owner.email, owner.password);
    const invite = await page.request.post(`${API_BASE}/v2/tenants/${tenantId}/members/invite`, {
      headers: await authHeaders(page),
      data: { userId: staffId, role: 'STORE_STAFF' },
    });
    expect(invite.status(), await invite.text()).toBe(201);

    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, staff.email, staff.password);
    const claims = claimsOf(await accessToken(page));
    expect(claims.tenantId, '只是受邀、尚未接受的人，登入後不得帶店鋪租戶').not.toBe(tenantId);
    expect(claims.role).toBe('BUYER');
  });

  test('E2E-MREV-02: 受邀人接受邀請後重新登入 → 帶店鋪租戶與 STORE_STAFF（合法的對照組）', async ({ page }: { page: Page }) => {
    await loginOnly(page, staff.email, staff.password);
    const accept = await page.request.post(`${API_BASE}/v2/tenants/${tenantId}/members/invite/accept`, {
      headers: await authHeaders(page),
    });
    expect(accept.status(), await accept.text()).toBe(200);

    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, staff.email, staff.password);
    const claims = claimsOf(await accessToken(page));
    expect(claims.tenantId).toBe(tenantId);
    expect(claims.role).toBe('STORE_STAFF');
    refreshBeforeRemoval = await refreshTokenOf(page);
  });

  test('E2E-MREV-03: 店主移除成員後，被移除者重新登入 → 不得再帶店鋪租戶與 STORE_STAFF', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    const removed = await page.request.delete(`${API_BASE}/v2/tenants/${tenantId}/members/${staffId}`, {
      headers: await authHeaders(page),
    });
    expect(removed.status(), await removed.text()).toBeLessThan(300);

    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, staff.email, staff.password);
    const claims = claimsOf(await accessToken(page));
    expect(claims.tenantId, '被移除的成員登入後不得再帶店鋪租戶').not.toBe(tenantId);
    expect(claims.tenantId).toBe(SYSTEM_TENANT_ID);
    expect(claims.role, '被移除的成員不得保留店員角色').toBe('BUYER');
  });

  test('E2E-MREV-04: 被移除者用移除前取得的 refresh token 換發 → 同樣不得帶店鋪租戶與 STORE_STAFF', async ({ page }: { page: Page }) => {
    const response = await page.request.post(`${API_BASE}/v2/auth/refresh`, {
      data: { refreshToken: refreshBeforeRemoval },
    });
    // 兩種修法都可接受：拒絕換發（舊 session 被撤銷），或換發出已降級的 token
    if (response.status() === 200) {
      const claims = claimsOf((await response.json()).data.accessToken as string);
      expect(claims.tenantId).not.toBe(tenantId);
      expect(claims.role).not.toBe('STORE_STAFF');
    } else {
      expect([400, 401, 403]).toContain(response.status());
    }
  });
});
