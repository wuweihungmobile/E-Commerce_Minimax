import { test, expect } from '@playwright/test';
import { loginOnly, registerAndLogin } from './helpers/auth';
import { Account, adminAccessToken, authHeaders, seedStore } from './helpers/store';

/**
 * AT-PRICING-RULES-REAL: 定價規則的寫入與列表（真實後端、Flyway 建出的真實 schema）—— Sprint 303
 *
 * 為什麼需要：
 * - DEF-414：`POST /rules`、`POST /calendar/price`、`POST /rules/{id}/override` 原本只設影子欄位 `tenantId`，
 *   `pricing_rules.tenant_id`（NOT NULL）收到 NULL，一律 500。既有後端測試全把 Repository 換成 mock，INSERT 從未真的執行過。
 *   後端另有真實 PostgreSQL 的 `PricingRulePersistenceE2ETest`，但它跑在 Hibernate 產生的 schema；本規格跑在 Flyway 建出的資料庫，
 *   是最接近正式環境的一道。
 * - DEF-416：`GET /rules?roomListingId=…`（預設 `activeOnly=false`）原本 `findAll()` 全表掃描再於記憶體過濾；Flyway 建出的資料庫
 *   原本帶著 V5 測試資料的一列 `rule_type = 'WEEKEND_SURCHARGE'`（不在 Java 列舉內），整個請求就 500。Hibernate 產生的測試庫對
 *   `rule_type` 有 CHECK 約束，放不進這種值，所以只有這一層（Flyway 資料庫）能重現。
 *   ⚠️ Sprint 305（DEF-418）的 V94 已把 V5 的測試租戶連同它的定價規則刪除，新建的 Flyway 資料庫不再帶有那一列，所以這一層
 *   **已不再能抓到「退回 findAll()」的回歸**（Sprint 303 的 JAR 層突變 J1 當時只有它擊殺）。Sprint 306（DEF-421）把這道護欄
 *   改放在後端：`PricingRulesCorruptRowFlywayIntegrationTest` 在 Flyway 實跑的資料庫裡自己種一列不在列舉內的 `rule_type`
 *   （Flyway 資料庫沒有 CHECK 約束），走真實 HTTP 驗證三種列表查詢都不被拖垮；另有單元測試
 *   `PricingServiceTest.RuleListingQueryTests` 的 `verify(never()).findAll()`。本規格不再負責這個回歸。
 * - DEF-411：沒有店鋪的買家（租戶是系統租戶佔位值）不得列出系統租戶名下的規則。完整的矩陣在後端 `PricingSystemTenantIsolationE2ETest`，
 *   這裡做冒煙。Flyway 資料庫的系統租戶底下原本沒有任何規則，『買家得空陣列』會是空洞的斷言（Sprint 303 的 JAR 層突變證實：
 *   把守門拿掉，這個案例照樣綠），所以由平台管理員先建立一條系統租戶名下的『平台自營規則』（不綁房源，`createRule` 本來就允許）。
 *
 * 資料全由真實流程建立（見 helpers/store.ts），動態定價開關由管理員開啟（核准後預設關閉，沒開就建立不了規則，E-2004）。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

const SYSTEM_TENANT_ID = '00000000-0000-0000-0000-000000000001';

const day = (offset: number) => new Date(Date.now() + offset * 86_400_000).toISOString().slice(0, 10);

test.describe('AT-PRICING-RULES-REAL: 定價規則寫入與列表（真實後端）', () => {
  // 開店流程只需要做一次；serial 讓後面的案例沿用同一組播種資料
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let tenantId: string;
  let roomId: string;
  let ruleId: string;
  let adminToken: string;
  let platformRuleId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(150_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'BOOKING_ONLY',
        storeLabel: 'E2E 定價規則店',
        room: { name: 'E2E 定價規則房型', price: 1500 },
      });
      owner = seeded.owner;
      tenantId = seeded.tenantId;
      roomId = seeded.roomId!;

      adminToken = await adminAccessToken(page);
      const toggle = await page.request.put(`${API_BASE}/v2/admin/tenants/${tenantId}/features/DYNAMIC_PRICING_ENABLED`, {
        headers: { Authorization: `Bearer ${adminToken}` },
        data: { enabled: true },
      });
      expect(toggle.status(), await toggle.text()).toBe(200);

      // 平台自營規則：管理員（沒有店鋪歸屬）的租戶是系統租戶，他建的規則就蓋成系統租戶
      const platform = await page.request.post(`${API_BASE}/v2/dashboard/pricing/rules`, {
        headers: { Authorization: `Bearer ${adminToken}` },
        data: {
          ruleType: 'SEASONAL',
          ruleName: 'E2E 平台自營規則',
          priority: 1,
          config: { multiplier: 1.1 },
          validFrom: day(500),
          validTo: day(510),
        },
      });
      expect(platform.status(), await platform.text()).toBe(201);
      const platformRule = (await platform.json()).data as { ruleId: string; tenantId: string };
      expect(platformRule.tenantId).toBe(SYSTEM_TENANT_ID);
      platformRuleId = platformRule.ruleId;
    } finally {
      await page.close();
    }
  });

  test('E2E-PRICING-01: 店主建立定價規則 → 201，規則的租戶是自己的店鋪（DEF-414）', async ({ page }) => {
    await loginOnly(page, owner.email, owner.password);
    const create = await page.request.post(`${API_BASE}/v2/dashboard/pricing/rules`, {
      headers: await authHeaders(page),
      data: {
        roomListingId: roomId,
        ruleType: 'SEASONAL',
        ruleName: 'E2E 旺季加價',
        priority: 3,
        config: { multiplier: 1.4 },
        validFrom: day(400),
        validTo: day(410),
      },
    });
    expect(create.status(), await create.text()).toBe(201);
    const created = (await create.json()).data as { ruleId: string; tenantId: string };
    expect(created.tenantId).toBe(tenantId);
    ruleId = created.ruleId;
  });

  test('E2E-PRICING-02: 日曆改價與區間覆蓋 → 200；帶房源 id 的規則列表（預設 activeOnly=false）列得出三條且都屬於自己的店鋪（DEF-414／416）', async ({ page }) => {
    await loginOnly(page, owner.email, owner.password);
    const headers = await authHeaders(page);

    const calendarPrice = await page.request.post(`${API_BASE}/v2/dashboard/pricing/calendar/price`, {
      headers,
      data: { roomListingId: roomId, date: day(30), price: 1888, reason: 'E2E 日曆改價' },
    });
    expect(calendarPrice.status(), await calendarPrice.text()).toBe(200);

    const override = await page.request.post(`${API_BASE}/v2/dashboard/pricing/rules/${ruleId}/override`, {
      headers,
      data: { startDate: day(60), endDate: day(62), overridePrice: 1999, reason: 'E2E 區間覆蓋' },
    });
    expect(override.status(), await override.text()).toBe(200);

    // 不帶 activeOnly：走「全部規則」分支（驗證這條分支回傳正確、且只含這間店鋪的規則）。V94（Sprint 305）之後 Flyway 資料庫
    // 已沒有 V5 的 WEEKEND_SURCHARGE 壞資料列，「全表掃描」的回歸改由後端 PricingRulesCorruptRowFlywayIntegrationTest 守住（DEF-421）
    const list = await page.request.get(`${API_BASE}/v2/dashboard/pricing/rules?roomListingId=${roomId}`, { headers });
    expect(list.status(), await list.text()).toBe(200);
    const rules = (await list.json()).data as Array<{ ruleName: string; tenantId: string; roomListingId: string }>;
    expect(rules.map((r) => r.ruleName)).toEqual(expect.arrayContaining(['E2E 旺季加價', 'E2E 日曆改價', 'E2E 區間覆蓋']));
    expect(rules.every((r) => r.tenantId === tenantId && r.roomListingId === roomId), '只應列出這間房源、這間店鋪的規則').toBe(true);
  });

  test('E2E-PRICING-03: 沒有店鋪的買家列不出系統租戶的平台自營規則、帶別店房源 id 得 403；平台管理員列得出（DEF-411 冒煙）', async ({ page }) => {
    // 對照：平台管理員（租戶同為系統租戶）列得出自己建的平台自營規則——守門不得變成「對所有人都回空」
    const adminList = await page.request.get(`${API_BASE}/v2/dashboard/pricing/rules`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(adminList.status(), await adminList.text()).toBe(200);
    expect(((await adminList.json()).data as Array<{ ruleId: string }>).map((r) => r.ruleId)).toContain(platformRuleId);

    await registerAndLogin(page);
    const headers = await authHeaders(page);

    const list = await page.request.get(`${API_BASE}/v2/dashboard/pricing/rules`, { headers });
    expect(list.status(), await list.text()).toBe(200);
    expect((await list.json()).data as unknown[], '沒有店鋪的買家不得列出系統租戶名下的任何規則').toEqual([]);

    const byListing = await page.request.get(`${API_BASE}/v2/dashboard/pricing/rules?roomListingId=${roomId}`, { headers });
    expect(byListing.status(), await byListing.text()).toBe(403);
  });
});
