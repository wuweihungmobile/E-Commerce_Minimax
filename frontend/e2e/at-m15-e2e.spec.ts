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

// 歷史：CMS／媒體／儀表板頁在載入或操作失敗時（新帳號權限不足 → 4xx）曾觸發原生 alert()，該 dialog 於 teardown
// 間歇造成 session 崩潰（m15 flaky 根因，S37 AI-2001），當時用「檔案級 dialog.dismiss()」繞過症狀。
// Sprint 329 起全站不再有原生 alert()／confirm()（改為 Toast／ConfirmDialog，ESLint design-tokens/no-native-dialog 守門），
// 根因已移除，所以這個 workaround 一併拿掉；訊息改用 getByTestId('toast') 斷言。

/**
 * E2E-M15-001: 建立並發布貼文流程
 */
test.describe('E2E-M15-001: 建立並發布貼文流程', () => {
  // Sprint 332（DEF-466）：原本的 /cms 貼文介面併進 /dashboard/posts；/cms 現在是轉址（at-cms-redirect.spec.ts 守住）
  test('登入後訪問貼文管理列表頁', async ({ page }) => {
    await registerAndLogin(page);

    // 訪問貼文管理列表頁
    await page.goto('/dashboard/posts');
    await page.waitForLoadState('domcontentloaded');

    console.log('貼文列表 URL:', page.url());
    expect(page.url()).toContain('/dashboard/posts');
  });

  test('貼文管理列表頁篩選功能', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/dashboard/posts');
    // 🔴 不可只等 domcontentloaded：它在 React hydration 完成前就返回，
    // 此時篩選按鈕已由 SSR 渲染出來但 onClick 尚未接上，click 會是**空操作**，
    // 後續斷言 class 變成 bg-rs-tertiary 便會輪詢到逾時（S107 實測 14 次都是 bg-gray-100；
    // Sprint 328 起 active 樣式是設計稿 token bg-rs-tertiary，原本的 bg-blue-100 已隨全站改用設計稿 token 移除）。
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
      await expect(publishedBtn).toHaveAttribute('aria-pressed', 'true', { timeout: 10000 });
      console.log('已發布篩選完成');
    }

    // 點擊「草稿」篩選
    const draftBtn = page.locator('button:has-text("草稿")').first();
    if (await draftBtn.isVisible()) {
      await draftBtn.click();
      await expect(draftBtn).toHaveAttribute('aria-pressed', 'true', { timeout: 10000 });
      console.log('草稿篩選完成');
    }
  });
});

/**
 * E2E-M15-003: 媒體上傳流程
 */
test.describe('E2E-M15-003: 媒體上傳流程', () => {
  // Sprint 332（DEF-466）：媒體管理只剩 /dashboard/media（原本的 /cms/media 併入）。這兩個案例只守「頁面開得起來、
  // 篩選控制項可操作」；真實的上傳／列出／刪除在 at-media-real.spec.ts（真實後端、真實檔案）。
  test('訪問媒體中心頁面', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/dashboard/media');
    await page.waitForLoadState('domcontentloaded');

    console.log('媒體中心 URL:', page.url());
    expect(page.url()).toContain('/dashboard/media');

    const content = await page.content();
    console.log('頁面包含「媒體中心」:', content.includes('媒體中心'));
    console.log('頁面包含「上傳」:', content.includes('上傳'));
  });

  test('媒體中心篩選功能', async ({ page }) => {
    // 新帳號沒有店鋪、沒有媒體權限 → 載入 403 → 頁面顯示錯誤 Toast；篩選控制項由本地 state 控制、不依賴後端資料，
    // 仍然穩定可操作（Sprint 329 前錯誤是原生 alert()，曾於 teardown 間歇造成 session 崩潰，S37 AI-2001；現在是普通 DOM）。
    await registerAndLogin(page);

    await page.goto('/dashboard/media');
    await page.waitForLoadState('domcontentloaded');

    // 檔案類型下拉是頁面上唯一有「圖片」選項（value=image/）的 select
    const typeSelect = page.locator('select', { has: page.locator('option[value="image/"]') });
    await expect(typeSelect).toBeVisible({ timeout: 15000 });
    await expect(typeSelect).toHaveValue('');

    await typeSelect.selectOption('image/');
    await expect(typeSelect).toHaveValue('image/');

    await typeSelect.selectOption('');
    await expect(typeSelect).toHaveValue('');
    // 等網路 idle，確保篩選 refetch 完成後才結束測試，避免 in-flight 請求於 teardown 時崩潰
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
 * 同步訊號一律用「這一次操作的網路回應」與 Toast 訊息（waitForResponse／getByTestId('toast')；Sprint 329 前是原生 alert 的 waitForEvent('dialog')），
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
  let storeSlug: string;
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
      storeSlug = seeded.storeSlug;

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
    await page.goto('/dashboard/posts/new');

    const title = `E2E M15 新建貼文 ${Date.now()}`;
    await page.getByPlaceholder('輸入標題').fill(title);
    await page.getByPlaceholder(/^輸入內容/).fill('# 標題\n\n這是 E2E 新建並發布的貼文內容。');

    const [create] = await Promise.all([
      page.waitForResponse(
        (r) => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/v2/dashboard/posts')
      ),
      page.getByRole('button', { name: '發布', exact: true }).click(),
    ]);
    expect(create.status(), await create.text()).toBe(200);
    const created = (await create.json()).data as { id: string; status: string };
    expect(created.status, '點「發布」建立的貼文應直接是已發布').toBe('PUBLISHED');
    // Toast 掛在根 layout，client 端導向（router.push）後仍在，所以可以在導向後再斷言
    await expect(page.getByTestId('toast').filter({ hasText: '貼文已發布' })).toBeVisible();
    await page.waitForURL(`**/dashboard/posts/${created.id}/edit`);

    // 前台的店鋪貼文列表列得出剛發布的貼文（Sprint 339 起平台層 /blog 是跨店鋪的列表，全套並行時「剛建的一定在第一頁」不成立，
    // 所以用這間店鋪自己的列表）
    await page.goto(`/stores/${storeSlug}/blog`);
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
  });

  test('E2E-M15-002: 從列表進入編輯頁，改標題並儲存——列表顯示新標題', async ({ page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/posts');

    // 播種的草稿顯示在列表，狀態是草稿
    const row = page.locator('tr', { hasText: DRAFT_TITLE });
    await expect(row).toBeVisible();
    await expect(row.getByText('草稿', { exact: true })).toBeVisible();

    await row.getByRole('link', { name: '編輯', exact: true }).click();
    await page.waitForURL(`**/dashboard/posts/${draftId}/edit`);

    // 編輯頁帶入原標題
    const titleInput = page.locator('form input[type="text"]').first();
    await expect(titleInput).toHaveValue(DRAFT_TITLE);

    const newTitle = `${DRAFT_TITLE}（改）`;
    await titleInput.fill(newTitle);
    const [update] = await Promise.all([
      page.waitForResponse(
        (r) => r.request().method() === 'PUT' && new URL(r.url()).pathname.endsWith(`/v2/dashboard/posts/${draftId}`)
      ),
      page.getByRole('button', { name: '儲存變更' }).click(),
    ]);
    expect(update.status(), await update.text()).toBe(200);
    await expect(page.getByTestId('toast').filter({ hasText: '貼文已儲存' })).toBeVisible();

    await page.goto('/dashboard/posts');
    const editedRow = page.locator('tr', { hasText: newTitle });
    await expect(editedRow).toBeVisible();
    await expect(editedRow.getByText('草稿', { exact: true })).toBeVisible();
  });

  test('E2E-M15-004: 前台文章詳情——匿名訪客也看得到已發布的文章；不存在的 slug 顯示「找不到文章」', async ({ page }) => {
    // Sprint 336（DEF-483）：文章網址帶店鋪（/stores/{店鋪}/blog/{文章}），店鋪由網址決定，不再靠 localStorage 的 tenantId，
    // 所以這個案例不登入——這就是匿名訪客從店鋪貼文列表點進文章時看到的畫面（之前只看得到「請先登入並設定店鋪以瀏覽部落格」）
    await page.goto(`/stores/${storeSlug}/blog/${embedSlug}`);
    await expect(page.getByRole('heading', { name: EMBED_TITLE, level: 2 })).toBeVisible();
    await expect(page.getByText('找不到文章')).toHaveCount(0);
    await expect(page.getByText('請先登入並設定店鋪以瀏覽部落格')).toHaveCount(0);

    const notFound = page.waitForResponse(
      (r) => r.request().method() === 'GET' && new URL(r.url()).pathname.endsWith('/v2/posts/non-existent-slug-12345')
    );
    await page.goto(`/stores/${storeSlug}/blog/non-existent-slug-12345`);
    expect((await notFound).status(), '不存在的 slug 後端應回 404').toBe(404);
    await expect(page.getByRole('heading', { name: '找不到文章' })).toBeVisible();
    await expect(page.getByRole('heading', { name: EMBED_TITLE })).toHaveCount(0);
  });

  test('E2E-M15-005: 前台部落格列出含嵌入語法的文章，進入詳情後顯示嵌入的商品卡', async ({ page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto(`/stores/${storeSlug}/blog`);

    await page.getByRole('link', { name: new RegExp(EMBED_TITLE) }).click();
    await page.waitForURL(`**/stores/${storeSlug}/blog/${embedSlug}`);

    // 嵌入語法被解析成商品卡：商品名稱、價格與「查看詳情」CTA 都在；原始語法不得外露
    await expect(page.getByRole('heading', { name: PRODUCT_NAME })).toBeVisible();
    await expect(page.getByText(/^TWD\s*450$/)).toBeVisible();
    await expect(page.getByText('查看詳情')).toBeVisible();
    await expect(page.getByText('{{embed:listing:')).toHaveCount(0);
  });

  test('E2E-M15-006（Sprint 338，DEF-492）: 貼文分類管理——名稱只差中文的分類都建得起來；同名（大小寫不同也算）被擋並告知「分類名稱已存在」，不是籠統的「請稍後再試」', async ({ page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/posts/categories');
    await expect(page.getByRole('heading', { name: '貼文分類管理' })).toBeVisible();

    const createCategory = async (name: string) => {
      await page.getByRole('button', { name: '新增分類' }).click();
      await page.getByPlaceholder('分類名稱').fill(name);
      await page.getByRole('button', { name: '儲存', exact: true }).click();
    };
    const toast = (text: string) => page.getByTestId('toast').filter({ hasText: text });

    // 名稱不同、只差中文（網址代稱都是時間戳）：原本第二個起被 409 E-8014 擋下；現在兩個都建立成功、列表看得到
    const travel = `旅遊筆記${STAMP}`;
    const coffee = `咖啡手帖${STAMP}`;
    await createCategory(travel);
    await expect(toast('分類已建立').first()).toBeVisible();
    await expect(page.getByRole('cell', { name: travel, exact: true })).toBeVisible();
    await createCategory(coffee);
    await expect(page.getByRole('cell', { name: coffee, exact: true })).toBeVisible();
    await expect(toast('儲存分類失敗')).toHaveCount(0);

    // 同名（大小寫不同也算）：擋下，訊息明確指出是名稱的問題；表單留著，列表沒有多一列
    const food = `Food${STAMP}`;
    await createCategory(food);
    await expect(page.getByRole('cell', { name: food, exact: true })).toBeVisible();
    await createCategory(food.toUpperCase());
    await expect(toast('分類名稱已存在，請換一個名稱')).toBeVisible();
    await expect(toast('儲存分類失敗，請稍後再試')).toHaveCount(0);
    await expect(page.getByPlaceholder('分類名稱')).toHaveValue(food.toUpperCase());
    await page.getByRole('button', { name: '取消' }).click();
    await expect(page.getByRole('cell', { name: food.toUpperCase(), exact: true })).toHaveCount(0);
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
