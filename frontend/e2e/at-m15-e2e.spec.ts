import { test, expect } from '@playwright/test';
import { loginOnly, registerAndLogin } from './helpers/auth';
import { Account, authHeaders, seedStore } from './helpers/store';

/**
 * AT-M15-E2E: M15 CMS 功能 E2E 測試
 *
 * 測試涵蓋 (根據 TR_M15_FE_INTEGRATION_TEST_PLAN.md):
 * - E2E-M15-001: 建立並發布貼文流程
 * - E2E-M15-002: 編輯並更新貼文流程
 * - E2E-M15-003: 媒體上傳流程
 * - E2E-M15-004: 前台部落格瀏覽流程
 * - E2E-M15-005: 嵌入商品卡解析流程
 *
 * Sprint 303（DEF-410）：原本 12 個案例有 7 個完全沒有 `expect(`（只 console.log，或「有元素才點」的條件式分支，
 * 而剛註冊、沒有店鋪的帳號永遠沒有貼文，分支永遠不執行），不可能失敗；其中「商品頁面」開的正是 DEF-401 崩潰的路由，
 * 卻因為用沒有店鋪的帳號而抓不到。這 7 個改寫為檔案最下方 `AT-M15-REAL`（真實店主、真實貼文／商品／房源，每個案例都有
 * 斷言）；「商品頁面」已由 `at-seller-product-list-real.spec.ts` 的 E2E-PRODLIST-01 完整涵蓋，不重複保留。
 * 上方其餘 5 個案例原本就有斷言，維持不動。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

// 全域 dialog 處理器：CMS/媒體/儀表板頁在載入或操作失敗時（新帳號權限不足 → 4xx）會觸發
// 原生 alert()，該 dialog 於 teardown 間歇造成 session 崩潰（m15 flaky 根因，S37 AI-2001）。
// 檔案級 beforeEach 註冊自動關閉，涵蓋本檔所有測試（取代 DEF-022 移除的固定 sleep 之保護作用）。
test.beforeEach(async ({ page }) => {
  page.on('dialog', (dialog) => {
    void dialog.dismiss().catch(() => {})
  })
})

/**
 * E2E-M15-001: 建立並發布貼文流程
 */
test.describe('E2E-M15-001: 建立並發布貼文流程', () => {
  test('登入後訪問 CMS 列表頁', async ({ page }) => {
    await registerAndLogin(page);

    // 訪問 CMS 列表頁
    await page.goto('/cms');
    await page.waitForLoadState('domcontentloaded');

    console.log('CMS 列表 URL:', page.url());
    expect(page.url()).toContain('/cms');
  });

  test('CMS 列表頁篩選功能', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/cms');
    // 🔴 不可只等 domcontentloaded：它在 React hydration 完成前就返回，
    // 此時篩選按鈕已由 SSR 渲染出來但 onClick 尚未接上，click 會是**空操作**，
    // 後續斷言 class 變成 bg-blue-100 便會輪詢到逾時（S107 實測 14 次都是 bg-gray-100）。
    // 列表資料由 useEffect 內的 loadPosts() 取得，故該回應到達即證明 hydration 已完成。
    await page.waitForResponse(
      (r) => r.url().includes('/v2/dashboard/posts') && r.request().method() === 'GET',
      { timeout: 15000 }
    );
    await expect(page.locator('text=載入中...')).toBeHidden({ timeout: 10000 });

    // 點擊「已發布」篩選
    const publishedBtn = page.locator('button:has-text("已發布")').first();
    if (await publishedBtn.isVisible()) {
      await publishedBtn.click();
      await expect(publishedBtn).toHaveClass(/bg-blue-100/, { timeout: 10000 });
      console.log('已發布篩選完成');
    }

    // 點擊「草稿」篩選
    const draftBtn = page.locator('button:has-text("草稿")').first();
    if (await draftBtn.isVisible()) {
      await draftBtn.click();
      await expect(draftBtn).toHaveClass(/bg-blue-100/, { timeout: 10000 });
      console.log('草稿篩選完成');
    }
  });
});

/**
 * E2E-M15-003: 媒體上傳流程
 */
test.describe('E2E-M15-003: 媒體上傳流程', () => {
  test('訪問媒體庫頁面', async ({ page }) => {
    // dialog 處理器已由檔案級 beforeEach 註冊（涵蓋 /cms/media 載入失敗的原生 alert）
    await registerAndLogin(page);

    await page.goto('/cms/media');
    await page.waitForLoadState('domcontentloaded');

    console.log('媒體庫 URL:', page.url());
    expect(page.url()).toContain('/cms/media');

    const content = await page.content();
    console.log('頁面包含「媒體庫」:', content.includes('媒體庫'));
    console.log('頁面包含「上傳」:', content.includes('上傳'));
  });

  test('媒體庫篩選功能', async ({ page }) => {
    // 媒體庫載入/篩選失敗時頁面會觸發原生 alert()（media/page.tsx loadMedia catch）；
    // 新帳號可能無 CMS 媒體權限 → getMediaList 403 → alert，該原生 dialog 於 teardown 時
    // 會間歇造成 session 崩潰（既有 flaky 根因，S37 AI-2001）。註冊 dialog 處理器自動關閉。
    page.on('dialog', (dialog) => {
      void dialog.dismiss().catch(() => {});
    });

    await registerAndLogin(page);

    await page.goto('/cms/media');
    await page.waitForLoadState('domcontentloaded');

    // 篩選鈕的 active class（bg-blue-100）由本地 filter state 控制、不依賴後端資料 → 穩定可斷言
    const allBtn = page.getByRole('button', { name: /^全部/ });
    const imageBtn = page.getByRole('button', { name: '圖片', exact: true });
    await expect(allBtn).toBeVisible({ timeout: 15000 });
    await expect(imageBtn).toBeVisible();

    // 點「圖片」→ 該鈕轉 active
    await imageBtn.click();
    await expect(imageBtn).toHaveClass(/bg-blue-100/, { timeout: 10000 });

    // 點「全部」→ 轉 active；再等網路 idle，確保篩選 refetch 完成後才結束測試，
    // 避免 in-flight 請求（及其失敗 alert）於 teardown 時崩潰（取代原無斷言 + 固定 sleep）。
    await allBtn.click();
    await expect(allBtn).toHaveClass(/bg-blue-100/, { timeout: 10000 });
    await page.waitForLoadState('networkidle');
  });
});

/**
 * E2E-M15-004: 前台部落格瀏覽流程（首頁）
 */
test.describe('E2E-M15-004: 前台部落格瀏覽流程', () => {
  test('訪問前台部落格首頁', async ({ page }) => {
    await page.goto('/blog');
    await page.waitForLoadState('domcontentloaded');

    console.log('前台首頁 URL:', page.url());
    expect(page.url()).toContain('/blog');
    console.log('前台首頁測試完成');
  });
});

/**
 * AT-M15-REAL（Sprint 303，DEF-410）：以真實店主改寫原本零斷言的 7 個案例
 *
 * 資料全由真實流程建立（見 helpers/store.ts）：店主註冊→驗證 Email→申請開店→管理員核准→店主重新登入→建立商品與房源，
 * 再由 API 建立一篇草稿與一篇已發布且嵌入商品卡的貼文。每個案例都先在畫面上看到「播種進去的那筆資料」，才算頁面真的載入。
 *
 * 同步訊號一律用「這一次操作的網路回應」與原生 alert 的訊息（waitForResponse／waitForEvent('dialog')），
 * 不用畫面文字：按鈕名稱含「發布」「已發布」，用文字比對會誤判（見 playwright-text-sync-and-role-login-pitfalls）。
 */
test.describe('AT-M15-REAL: CMS／部落格／儀表板（真實後端，店主視角）', () => {
  // 開店流程只需要做一次；serial 讓後面的案例沿用同一組播種資料
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  const STAMP = Date.now();
  const PRODUCT_NAME = `E2E M15 商品 ${STAMP}`;
  const ROOM_NAME = `E2E M15 房型 ${STAMP}`;
  const DRAFT_TITLE = `E2E M15 原始貼文 ${STAMP}`;
  const EMBED_TITLE = `E2E M15 嵌入商品卡文章 ${STAMP}`;

  let owner: Account;
  let draftId: string;
  let embedSlug: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      // seedStore 結束時瀏覽器已是店主登入狀態，authHeaders(page) 取得的就是店主的 token
      const seeded = await seedStore(page, {
        businessType: 'HYBRID',
        storeLabel: 'E2E M15 店',
        product: { name: PRODUCT_NAME, price: 450 },
        room: { name: ROOM_NAME, price: 1600 },
      });
      owner = seeded.owner;

      const draft = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
        headers: await authHeaders(page),
        data: { title: DRAFT_TITLE, content: '這是一篇還沒發布的草稿。' },
      });
      expect(draft.status(), await draft.text()).toBe(200);
      draftId = (await draft.json()).data.id as string;

      const embed = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
        headers: await authHeaders(page),
        data: { title: EMBED_TITLE, content: `推薦商品：{{embed:listing:${seeded.productId}}}` },
      });
      expect(embed.status(), await embed.text()).toBe(200);
      const embedPost = (await embed.json()).data as { id: string; slug: string };
      embedSlug = embedPost.slug;
      const publish = await page.request.post(`${API_BASE}/v2/dashboard/posts/${embedPost.id}/publish`, {
        headers: await authHeaders(page),
      });
      expect(publish.status(), await publish.text()).toBe(200);
    } finally {
      await page.close();
    }
  });

  test('E2E-M15-001: 新建貼文並發布——送出成功、導向編輯頁，前台部落格看得到', async ({ page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/cms/posts/new');

    const title = `E2E M15 新建貼文 ${Date.now()}`;
    await page.getByPlaceholder('輸入標題').fill(title);
    await page.getByPlaceholder(/^輸入內容/).fill('# 標題\n\n這是 E2E 新建並發布的貼文內容。');

    const dialog = page.waitForEvent('dialog');
    const [create] = await Promise.all([
      page.waitForResponse(
        (r) => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/v2/dashboard/posts')
      ),
      page.getByRole('button', { name: '發布', exact: true }).click(),
    ]);
    expect(create.status(), await create.text()).toBe(200);
    const created = (await create.json()).data as { id: string; status: string };
    expect(created.status, '點「發布」建立的貼文應直接是已發布').toBe('PUBLISHED');
    expect((await dialog).message()).toBe('貼文已發布');
    await page.waitForURL(`**/cms/posts/${created.id}/edit`);

    // 前台部落格（tenantId 取自登入後的 localStorage）列得出剛發布的貼文
    await page.goto('/blog');
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
  });

  test('E2E-M15-002: 從列表進入編輯頁，改標題並儲存——列表顯示新標題', async ({ page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/cms');

    // 播種的草稿顯示在列表，狀態是草稿
    const row = page.locator('tr', { hasText: DRAFT_TITLE });
    await expect(row).toBeVisible();
    await expect(row.getByText('草稿', { exact: true })).toBeVisible();

    await row.getByRole('link', { name: '編輯', exact: true }).click();
    await page.waitForURL(`**/cms/posts/${draftId}/edit`);

    // 編輯頁帶入原標題
    const titleInput = page.locator('form input[type="text"]').first();
    await expect(titleInput).toHaveValue(DRAFT_TITLE);

    const newTitle = `${DRAFT_TITLE}（改）`;
    await titleInput.fill(newTitle);
    const dialog = page.waitForEvent('dialog');
    const [update] = await Promise.all([
      page.waitForResponse(
        (r) => r.request().method() === 'PUT' && new URL(r.url()).pathname.endsWith(`/v2/dashboard/posts/${draftId}`)
      ),
      page.getByRole('button', { name: '儲存變更' }).click(),
    ]);
    expect(update.status(), await update.text()).toBe(200);
    expect((await dialog).message()).toBe('貼文已儲存');

    await page.goto('/cms');
    const editedRow = page.locator('tr', { hasText: newTitle });
    await expect(editedRow).toBeVisible();
    await expect(editedRow.getByText('草稿', { exact: true })).toBeVisible();
  });

  test('E2E-M15-004: 前台文章詳情——已發布的 slug 顯示文章，不存在的 slug 顯示「找不到文章」', async ({ page }) => {
    // 文章詳情頁的 tenantId 取自 localStorage；沒登入過的匿名頁面永遠停在載入中（所以原案例不可能看到「找不到文章」）
    await loginOnly(page, owner.email, owner.password);

    await page.goto(`/blog/${embedSlug}`);
    await expect(page.getByRole('heading', { name: EMBED_TITLE, level: 1 })).toBeVisible();
    await expect(page.getByText('找不到文章')).toHaveCount(0);

    const notFound = page.waitForResponse(
      (r) => r.request().method() === 'GET' && new URL(r.url()).pathname.endsWith('/v2/posts/non-existent-slug-12345')
    );
    await page.goto('/blog/non-existent-slug-12345');
    expect((await notFound).status(), '不存在的 slug 後端應回 404').toBe(404);
    await expect(page.getByRole('heading', { name: '找不到文章' })).toBeVisible();
    await expect(page.getByRole('heading', { name: EMBED_TITLE })).toHaveCount(0);
  });

  test('E2E-M15-005: 前台部落格列出含嵌入語法的文章，進入詳情後顯示嵌入的商品卡', async ({ page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/blog');

    await page.getByRole('link', { name: new RegExp(EMBED_TITLE) }).click();
    await page.waitForURL(`**/blog/${embedSlug}`);

    // 嵌入語法被解析成商品卡：商品名稱、價格與「查看詳情」CTA 都在；原始語法不得外露
    await expect(page.getByRole('heading', { name: PRODUCT_NAME })).toBeVisible();
    await expect(page.getByText(/^TWD\s*450$/)).toBeVisible();
    await expect(page.getByText('查看詳情')).toBeVisible();
    await expect(page.getByText('{{embed:listing:')).toHaveCount(0);
  });

  test('Dashboard 首頁：店主看到自己的帳號與角色、快速入口、播種的商品／房型數，沒有錯誤橫幅', async ({ page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard');

    await expect(page.getByText(`${owner.email} (STORE_OWNER)`)).toBeVisible();
    await expect(page.getByRole('heading', { name: '營運總覽' })).toBeVisible();
    await expect(page.getByRole('link', { name: '商品管理' })).toBeVisible();
    await expect(page.getByRole('link', { name: '房型管理' })).toBeVisible();
    await expect(page.getByText('無法載入儀表板資料')).toHaveCount(0);

    // 播種：1 件商品、1 間房型 → 「上架中商品 / 房型」是 1 / 1
    const card = page.locator('div', { has: page.getByText('上架中商品 / 房型', { exact: true }) }).last();
    await expect(card.getByText('1 / 1')).toBeVisible();
  });

  test('房型頁面：店主看到自己播種的房型，頁面沒有崩潰', async ({ page }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', (error) => pageErrors.push(String(error)));

    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/rooms');

    await expect(page.getByRole('heading', { name: '房源管理' })).toBeVisible();
    await expect(page.getByText(ROOM_NAME)).toBeVisible();
    await expect(page.getByText('This page couldn’t load')).toHaveCount(0);
    expect(pageErrors, `頁面不得有未捕捉的 render 錯誤：${pageErrors.join(' | ')}`).toEqual([]);
  });
});
