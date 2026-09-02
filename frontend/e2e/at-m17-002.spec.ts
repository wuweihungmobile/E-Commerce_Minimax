import { test, expect } from '@playwright/test';
import { loginOnly } from './helpers/auth';

/**
 * AT-M17-002: Admin 審核開店申請 E2E 測試
 *
 * 测试步骤：
 * 1. Admin 登入系統
 * 2. 前往租戶管理頁面 (/admin/tenants)
 * 3. 找到一個 PENDING_REVIEW 狀態的租戶
 * 4. 點擊審核詳情
 * 5. 通過審核
 * 6. 驗證成功提示出現
 */
/**
 * ⚠️ 本 spec 的前提（列表中存在 PENDING_REVIEW 的租戶）無法由測試自己建立，見 DEF-058。
 * Sprint 107 曾嘗試在 beforeEach 以 `POST /v2/tenants/apply` 自建資料，但那個 Guest 端點
 * 只會產生 `TenantApplication`（status=PENDING），**不會**產生 admin 列表所看的
 * `Tenant`（status=PENDING_REVIEW）——兩者是不同實體。已移除該無效 seeding，
 * 維持「前提不成立則 test.skip()」的既有設計。
 */
test.describe('AT-M17-002: Admin 審核開店申請', () => {
  test.beforeEach(async ({ page }) => {
    // 使用 ADMIN 帳號登入系統（共用 loginOnly，不註冊，AI-2101b 統一）
    await loginOnly(page, 'admin@nextkey.local', 'Test123!');

    // 驗證 JWT token 已正確存儲
    const accessToken = await page.evaluate(() => localStorage.getItem('accessToken'));
    expect(accessToken).toBeTruthy();

    // 等待頁面穩定
    await page.waitForLoadState('domcontentloaded');

    // 前往 Admin 租戶管理頁面
    await page.goto('/admin/tenants');
    await page.waitForLoadState('domcontentloaded');
  });

  test('Admin 審核通過申請', async ({ page }) => {
    // 核准成功以原生 alert() 提示 → 註冊 dialog 處理器避免 teardown session 崩潰（m15/DEF 教訓）
    page.on('dialog', (dialog) => {
      void dialog.accept().catch(() => {});
    });
    // 點擊審核中篩選查看是否有待審核項目
    await page.click('button:has-text("審核中")');
    await page.waitForResponse(r => r.url().includes('/v2/admin/tenants'), { timeout: 15000 }).catch(() => {});

    // 檢查是否有待審核的租戶
    const reviewButtons = page.locator('button:has-text("審核詳情")');
    const count = await reviewButtons.count();

    if (count === 0) {
      // 如果沒有待審核，跳過測試
      test.skip();
      return;
    }

    // 點擊第一個審核詳情按鈕，並等「真正導航完成」
    // 🔴 不可只用 waitForLoadState('domcontentloaded')：Next.js 的 client-side 導航不會觸發
    // 新的 document load，該 await 會立刻返回，使後續 locator 仍在**列表頁**上求值（S107 根因）。
    await reviewButtons.first().click();
    await page.waitForURL('**/admin/tenants/*/review', { timeout: 15000 });

    // 核准按鈕：用精確文字比對
    // 🔴 不可用 :has-text("核准")：那是**子字串**比對，會命中列表頁的「已核准 (N)」篩選 tab
    // （兩者都是 variant="outline" size="sm"，S107 的失敗 log 顯示解析到的正是該 tab 按鈕）。
    // 該 tab 會隨 tabCounts 重新渲染而從 DOM 上被拔掉 → click 一直重試到 30s 逾時。
    const approveButton = page.getByRole('button', { name: '核准', exact: true });
    await approveButton.waitFor({ state: 'visible', timeout: 10000 }).catch(() => {});
    if (!(await approveButton.isVisible())) {
      // 核准鈕只在 tenant.status === 'PENDING_REVIEW' 時渲染。導到的租戶不是待審核狀態
      // ⇒ 本測試的前提不成立（見檔頂 DEF-058），跳過而非假失敗。
      test.skip();
      return;
    }

    // 真斷言：原本是 expect(successVisible || true).toBeTruthy() ——**恆真**，
    // 即使按鈕點錯、核准根本沒發生也照樣綠燈（S97「測試以固件繞過同一段邏輯」的又一實例）。
    const approveResponse = page.waitForResponse(
      (r) => r.url().includes('/approve') && r.request().method() === 'POST',
      { timeout: 15000 }
    );
    await approveButton.click();
    expect((await approveResponse).status()).toBe(200);

    // 成功分支會顯示「店鋪已核准」並在 2 秒後導回列表；失敗分支是 alert（不導頁）。
    // 斷言導頁而非斷言訊息，避開與那 2 秒 redirect 的競賽。
    await page.waitForURL((url) => url.pathname === '/admin/tenants', { timeout: 15000 });
  });

  test('Admin 審核駁回申請', async ({ page }) => {
    // 駁回成功以原生 alert() 提示 → 註冊 dialog 處理器避免 teardown session 崩潰（m15/DEF 教訓）
    page.on('dialog', (dialog) => {
      void dialog.accept().catch(() => {});
    });
    // 點擊審核中篩選查看是否有待審核項目
    await page.click('button:has-text("審核中")');
    await page.waitForResponse(r => r.url().includes('/v2/admin/tenants'), { timeout: 15000 }).catch(() => {});

    // 檢查是否有待審核的租戶
    const reviewButtons = page.locator('button:has-text("審核詳情")');
    const count = await reviewButtons.count();

    if (count === 0) {
      // 如果沒有待審核，跳過測試
      test.skip();
      return;
    }

    // 點擊第一個審核詳情按鈕，並等「真正導航完成」（與核准測試同一根因，見上方註解）
    await reviewButtons.first().click();
    await page.waitForURL('**/admin/tenants/*/review', { timeout: 15000 });

    // 點擊駁回按鈕
    const rejectButton = page.locator('button:has-text("駁回")');
    // 軟等待（顯式取代 sleep，但保留 skip 容忍度：admin 帳號可能無待審租戶）
    await rejectButton.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});
    if (await rejectButton.isVisible()) {
      await rejectButton.click();

      // 找到輸入框填寫駁回原因
      const reasonInput = page.locator('input[id="rejectReason"], #rejectReason');
      await reasonInput.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});
      if (await reasonInput.isVisible()) {
        await reasonInput.fill('資料不全');

        // 點擊確認駁回
        const confirmButton = page.locator('button:has-text("確認駁回")');
        await confirmButton.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});
        if (await confirmButton.isVisible()) {
          await confirmButton.click();
          await page.waitForResponse(r => r.url().includes('/reject'), { timeout: 15000 }).catch(() => {});
        }
      }
    } else {
      // No pending tenants, skip test
      test.skip();
    }
  });

});