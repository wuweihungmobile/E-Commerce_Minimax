import { test, expect, Page } from '@playwright/test';
import { asMockLoggedInBuyer } from './helpers/mockLogin';
import { STATUS_REGISTRY, featureStatusEntry } from '../src/lib/status';

/**
 * AT-UI-DESIGN-SYSTEM: 設計系統契約（Sprint 329）
 *
 * 為什麼有這支：Sprint 328 換皮改了 144 個檔案，因為「控制項長什麼樣」這個決定散在上百個檔案裡（61% 的 className 是原樣複製貼上）。
 * Sprint 329 把這些配方收進 components/ui，這支規格守的是「收進去之後真的只有一個來源」：
 *   - 量的是實際 computed style（高度、圓角、框色、字級…），不是 class 名——換皮改 class 名時，這裡不該紅；有人繞過元件時，這裡會紅。
 *   - at-ui-design-base（Sprint 328）守「外殼與主題」；這支守「外殼之內的控制項、表格、回饋、狀態」。
 *
 * 全程 mock API（page.route）、不需要後端 seed、不走真實登入（asMockLoggedInBuyer 只種 accessToken）：
 * 契約測的是前端元件，不是後端資料；也不消耗登入限流額度。
 *
 * - E2E-UIDS-01: 欄位幾何契約——Input／NativeSelect／Textarea 的最小高、圓角、框色（取自 token）、字級、內距，無陰影
 * - E2E-UIDS-02: 資料表契約——表頭字級／內距、儲存格內距、外框；狀態徽章的顏色取自語意 token
 * - E2E-UIDS-03: 頁面標題契約——所有後台頁的 <h1> 同字級同字重
 * - E2E-UIDS-04: Toast——錯誤／成功訊息是普通 DOM（role、data-kind），可手動關閉、會自動消失
 * - E2E-UIDS-05: ConfirmDialog——取消、ESC 都不送 DELETE，按「刪除」才送；全程沒有原生對話框
 * - E2E-UIDS-06: Dialog——遮罩是半透明（修掉 bg-opacity-50 在 Tailwind v4 變純黑）、焦點鎖定、ESC 關閉
 * - E2E-UIDS-07: 換皮演練（執行期）——只改 token，所有欄位、按鈕、表格外框都跟著變（證明單一來源）
 * - E2E-UIDS-08: 狀態登錄表語意一致——同一種狀態在所有領域同一個顏色（貼文與知識庫的草稿／封存曾顏色相反）
 */

// ---------------------------------------------------------------------------------------------
// mock 工具
// ---------------------------------------------------------------------------------------------
type MockResult = { status?: number; data?: unknown };
type MockHandler = (ctx: { method: string; path: string }) => MockResult | undefined;

/** 攔截全部 /api/**；沒被 handler 處理的請求回 `{success:true,data:[]}`（頁面載入時順帶發的請求不會打到真實後端）。 */
async function mockApi(page: Page, handler: MockHandler) {
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api/, '');
    const result = handler({ method: route.request().method(), path }) ?? { data: [] };
    const status = result.status ?? 200;
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify({ success: status < 400, data: result.data ?? null, message: status >= 400 ? 'mock error' : undefined }),
    });
  });
}

const FAQ_CATEGORY = { id: 'c1', name: '一般問題', slug: 'general', description: '', icon: '', sortOrder: 0, createdAt: '2030-01-01T00:00:00Z', updatedAt: '2030-01-01T00:00:00Z' };
const FAQ_ARTICLE = {
  id: 'a1', categoryId: 'c1', categoryName: '一般問題', question: '契約測試用的問題？', answer: '契約測試用的答案', slug: 'q1', sortOrder: 0,
  viewCount: 3, isPinned: false, isPublished: true, publishedAt: '2030-01-01T00:00:00Z', createdAt: '2030-01-01T00:00:00Z', updatedAt: '2030-01-01T00:00:00Z',
};
const POSTS = (['DRAFT', 'PUBLISHED', 'ARCHIVED'] as const).map((status, i) => ({
  id: `p${i}`, tenantId: 't', authorId: 'u', authorName: '作者', title: `契約貼文-${status}`, slug: `p${i}`, content: 'c', excerpt: '', featuredImageUrl: '',
  status, categoryId: '', categoryName: '分類', tags: [], viewCount: i, publishedAt: '2030-01-01T00:00:00Z', createdAt: '2030-01-01T00:00:00Z', updatedAt: '2030-01-01T00:00:00Z', embeds: [],
}));

/** 後台頁面共用的 mock：FAQ、貼文、貼文分類。 */
const baseHandler: MockHandler = ({ path }) => {
  if (path === '/v2/faqs') return { data: { content: [FAQ_ARTICLE], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true } };
  if (path === '/v2/faqs/categories') return { data: [FAQ_CATEGORY] };
  if (path === '/v2/faqs/pinned') return { data: [] };
  if (path === '/v2/dashboard/posts') return { data: { posts: POSTS, totalCount: 3, page: 0, size: 20, totalPages: 1 } };
  if (path.endsWith('/post-categories')) return { data: { categories: [], totalCount: 0 } };
  return undefined;
};

async function openAs(page: Page, path: string, handler: MockHandler = baseHandler) {
  await asMockLoggedInBuyer(page);
  await mockApi(page, handler);
  await page.goto(path);
}

/** 把 CSS 變數解析成瀏覽器實際算出來的 rgb()，用來和 computed style 比對（不寫死色碼，換主題也成立）。 */
async function tokenColor(page: Page, name: string): Promise<string> {
  return page.evaluate((n) => {
    const el = document.createElement('i');
    el.style.color = `var(${n})`;
    document.body.appendChild(el);
    const c = getComputedStyle(el).color;
    el.remove();
    return c;
  }, name);
}

type FieldMeasure = { tag: string; label: string; h: number; br: string; bs: string; fs: string; bc: string; pl: string };

/** 內容區（不含頂欄／側欄／頁尾）所有可見的文字輸入框、下拉、多行輸入的實際樣式。 */
async function measureFields(page: Page): Promise<FieldMeasure[]> {
  return page.evaluate(() => {
    const chrome = (el: Element) =>
      !!el.closest('[data-testid="storefront-header"],[data-testid="seller-header"],[data-testid="seller-sidebar"],[data-testid="storefront-footer"]');
    const visible = (el: Element) => {
      const r = el.getBoundingClientRect();
      const s = getComputedStyle(el);
      return r.width > 1 && r.height > 2 && s.visibility !== 'hidden' && s.display !== 'none';
    };
    const NON_TEXT = ['checkbox', 'radio', 'hidden', 'file', 'submit', 'button', 'range', 'color', 'image', 'reset'];
    return Array.from(document.querySelectorAll<HTMLElement>('input, select, textarea'))
      .filter((el) => visible(el) && !chrome(el) && !(el instanceof HTMLInputElement && NON_TEXT.includes(el.type)))
      .map((el) => {
        const s = getComputedStyle(el);
        return {
          tag: el.tagName.toLowerCase(),
          label: el.getAttribute('placeholder') || el.getAttribute('name') || el.id || el.getAttribute('aria-label') || '(無標示)',
          h: el.getBoundingClientRect().height,
          br: s.borderTopLeftRadius,
          bs: s.boxShadow,
          fs: s.fontSize,
          bc: s.borderTopColor,
          pl: s.paddingLeft,
        };
      });
  });
}

// ---------------------------------------------------------------------------------------------
test.describe('AT-UI-DESIGN-SYSTEM: 設計系統契約', () => {
  test('E2E-UIDS-01: 欄位幾何契約——Input／NativeSelect／Textarea 走同一個規格（最小高 44、圓角 8、框色取自 token、字級 16、內距 16、無陰影）', async ({ page }) => {
    const border = { value: '' };
    for (const path of ['/dashboard/faq', '/dashboard/posts/new']) {
      await test.step(`${path}`, async () => {
        await openAs(page, path);
        await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: 15000 });
        // 等欄位出現（FAQ 頁有下拉＋搜尋框；新增貼文頁有標題、分類、內容）
        await expect(page.locator(path.endsWith('/faq') ? 'select' : 'textarea').first()).toBeVisible({ timeout: 15000 });
        border.value = await tokenColor(page, '--rs-border');

        const fields = await measureFields(page);
        expect(fields.length, `${path} 應至少有 2 個欄位，否則這個契約什麼都沒量到`).toBeGreaterThanOrEqual(2);
        const violations: string[] = [];
        for (const f of fields) {
          const minH = f.tag === 'textarea' ? 80 : 44;
          if (f.h < minH - 0.5) violations.push(`${f.tag}「${f.label}」高度 ${f.h}px < ${minH}px`);
          if (f.br !== '8px') violations.push(`${f.tag}「${f.label}」圓角 ${f.br}（規格 8px）`);
          if (f.bs !== 'none') violations.push(`${f.tag}「${f.label}」有陰影 ${f.bs}（設計稿嚴禁陰影）`);
          if (f.fs !== '16px') violations.push(`${f.tag}「${f.label}」字級 ${f.fs}（規格 16px）`);
          if (f.pl !== '16px') violations.push(`${f.tag}「${f.label}」左內距 ${f.pl}（規格 16px）`);
          if (f.bc !== border.value) violations.push(`${f.tag}「${f.label}」框色 ${f.bc}（應為 token --rs-border＝${border.value}）`);
        }
        expect(violations, `${path} 有欄位繞過設計系統元件：\n${violations.join('\n')}`).toEqual([]);

        // 單行欄位（輸入框、下拉）同一頁要等高：原生 <select> 的高度由行高撐出來，沒有明確行高會比 Input 矮 2px，
        // 同一排的篩選列會對不齊（Sprint 329 實測 44 vs 46，fieldStyles 加 leading-6 修掉）。
        const singleLine = Array.from(new Set(fields.filter((f) => f.tag !== 'textarea').map((f) => Math.round(f.h))));
        expect(singleLine, `${path} 的單行欄位高度不一致：${singleLine.join('／')}px`).toHaveLength(1);
      });
    }
  });

  test('E2E-UIDS-02: 資料表契約——表頭／儲存格規格與外框來自 Table 元件；狀態徽章顏色取自語意 token', async ({ page }) => {
    await openAs(page, '/dashboard/posts');
    await expect(page.getByRole('row').filter({ hasText: '契約貼文-DRAFT' })).toBeVisible({ timeout: 15000 });

    const heads = await page.locator('th').evaluateAll((els) =>
      els.map((el) => {
        const s = getComputedStyle(el);
        return { text: (el.textContent || '').trim(), fs: s.fontSize, fw: s.fontWeight, pt: s.paddingTop, pl: s.paddingLeft };
      })
    );
    expect(heads.length, '貼文列表至少 5 欄').toBeGreaterThanOrEqual(5);
    for (const h of heads) {
      expect(h, `表頭「${h.text}」`).toMatchObject({ fs: '12px', fw: '500', pt: '12px', pl: '16px' });
    }
    const cells = await page.locator('tbody td').evaluateAll((els) => els.map((el) => ({ pt: getComputedStyle(el).paddingTop, pl: getComputedStyle(el).paddingLeft })));
    expect(cells.length).toBeGreaterThan(0);
    for (const c of cells) expect(c).toEqual({ pt: '12px', pl: '16px' });

    // 外框容器：1px 框（token）、圓角 12、橫向可捲動（窄螢幕不撐破版面）
    const wrap = await page.locator('table').first().evaluate((t) => {
      const w = t.parentElement as HTMLElement;
      const s = getComputedStyle(w);
      return { br: s.borderTopLeftRadius, bw: s.borderTopWidth, bc: s.borderTopColor, ox: s.overflowX, bs: s.boxShadow };
    });
    expect(wrap).toMatchObject({ br: '12px', bw: '1px', ox: 'auto', bs: 'none' });
    expect(wrap.bc).toBe(await tokenColor(page, '--rs-border'));

    // 狀態徽章：已發布＝success token、已歸檔＝tertiary token、草稿＝透明底＋框
    const bg = async (rowText: string, badge: string) =>
      page.getByRole('row').filter({ hasText: rowText }).getByText(badge, { exact: true }).evaluate((el) => {
        const s = getComputedStyle(el);
        return { bg: s.backgroundColor, bc: s.borderTopColor };
      });
    expect((await bg('契約貼文-PUBLISHED', '已發布')).bg).toBe(await tokenColor(page, '--rs-success'));
    expect((await bg('契約貼文-ARCHIVED', '已歸檔')).bg).toBe(await tokenColor(page, '--rs-tertiary'));
    const draft = await bg('契約貼文-DRAFT', '草稿');
    expect(draft.bg, '草稿徽章是透明底（outline）').toBe('rgba(0, 0, 0, 0)');
    expect(draft.bc).toBe(await tokenColor(page, '--rs-border'));
  });

  test('E2E-UIDS-03: 頁面標題契約——後台頁面的 <h1> 同字級（24px）同字重（600）', async ({ page }) => {
    await asMockLoggedInBuyer(page);
    await mockApi(page, baseHandler);
    for (const [path, title] of [
      ['/dashboard/faq', 'FAQ 管理'],
      ['/dashboard/posts', '貼文管理'],
      ['/dashboard/posts/categories', '貼文分類管理'],
      ['/dashboard/posts/new', '新建貼文'],
    ] as const) {
      await test.step(path, async () => {
        await page.goto(path);
        const h1 = page.getByRole('heading', { level: 1, name: title });
        await expect(h1).toBeVisible({ timeout: 15000 });
        await expect(h1).toHaveCSS('font-size', '24px');
        await expect(h1).toHaveCSS('font-weight', '600');
      });
    }
  });

  test('E2E-UIDS-04: Toast——錯誤訊息是普通 DOM（role=alert、data-kind=error），可手動關閉；成功訊息 role=status 並自動消失；沒有原生對話框', async ({ page }) => {
    const nativeDialogs: string[] = [];
    page.on('dialog', (d) => {
      nativeDialogs.push(d.message());
      void d.dismiss();
    });
    // FAQ 列表失敗 → 頁面送出錯誤通知（原本是原生 alert()）
    await openAs(page, '/dashboard/faq', (ctx) => (ctx.path === '/v2/faqs' ? { status: 500 } : baseHandler(ctx)));
    const error = page.getByTestId('toast');
    await expect(error).toContainText('載入 FAQ 文章失敗，請稍後再試');
    await expect(error).toHaveAttribute('data-kind', 'error');
    await expect(error).toHaveAttribute('role', 'alert');
    await error.getByRole('button', { name: '關閉通知' }).click();
    await expect(error).toBeHidden();

    // 成功通知：刪除一則 FAQ → 「刪除成功」，role=status，4 秒後自動消失
    await page.unroute('**/api/**');
    await mockApi(page, baseHandler);
    await page.goto('/dashboard/faq');
    await page.getByRole('button', { name: '刪除', exact: true }).click();
    await page.getByTestId('confirm-dialog-confirm').click();
    const ok = page.getByTestId('toast').filter({ hasText: '刪除成功' });
    await expect(ok).toBeVisible();
    await expect(ok).toHaveAttribute('data-kind', 'success');
    await expect(ok).toHaveAttribute('role', 'status');
    await expect(ok, '成功通知 4 秒後應自動消失').toBeHidden({ timeout: 9000 });
    expect(nativeDialogs, '全站不該再出現原生對話框').toEqual([]);
  });

  test('E2E-UIDS-05: ConfirmDialog——按取消、按 ESC 都不送 DELETE；按「刪除」才送一次；全程沒有原生 confirm()', async ({ page }) => {
    const nativeDialogs: string[] = [];
    page.on('dialog', (d) => {
      nativeDialogs.push(d.message());
      void d.dismiss();
    });
    let deletes = 0;
    await openAs(page, '/dashboard/faq', (ctx) => {
      if (ctx.method === 'DELETE' && ctx.path === '/v2/faqs/a1') {
        deletes++;
        return { data: null };
      }
      return baseHandler(ctx);
    });
    await expect(page.getByText(FAQ_ARTICLE.question)).toBeVisible({ timeout: 15000 });

    const trigger = page.getByRole('button', { name: '刪除', exact: true });
    const dialog = page.getByTestId('confirm-dialog');

    // 取消鈕
    await trigger.click();
    await expect(dialog).toBeVisible();
    await expect(dialog).toHaveAttribute('role', 'alertdialog');
    await expect(dialog).toContainText('確定要刪除這則 FAQ 嗎？');
    await page.getByTestId('confirm-dialog-cancel').click();
    await expect(dialog).toBeHidden();
    expect(deletes, '按取消不該送 DELETE').toBe(0);

    // ESC
    await trigger.click();
    await expect(dialog).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    expect(deletes, '按 ESC 不該送 DELETE').toBe(0);

    // 確定
    await trigger.click();
    await page.getByTestId('confirm-dialog-confirm').click();
    await expect.poll(() => deletes, { message: '按「刪除」才送出 DELETE，且只送一次' }).toBe(1);
    expect(nativeDialogs, '全站不該再出現原生 confirm()／alert()').toEqual([]);
  });

  test('E2E-UIDS-06: Dialog——遮罩是半透明（不是純黑）、焦點鎖在對話框內、ESC 關閉', async ({ page }) => {
    await openAs(page, '/dashboard/posts/categories');
    await page.getByRole('button', { name: '新增分類' }).click();

    const dialog = page.getByRole('dialog', { name: '新增分類' });
    await expect(dialog).toBeVisible();
    // Tailwind v4 移除了 bg-opacity-*：舊的 `bg-black bg-opacity-50` 遮罩實測是 rgb(0, 0, 0) 純黑，整頁看不到。
    // 瀏覽器把 `bg-black/50` 算成 `oklab(0 0 0 / 0.5)`（不是 rgba），所以解析 alpha 而不是比對字串格式。
    const overlayColor = await page.getByTestId('dialog-overlay').evaluate((el) => getComputedStyle(el).backgroundColor);
    const alpha = Number(overlayColor.match(/(?:,|\/)\s*([0-9.]+)\s*\)$/)?.[1] ?? '1');
    expect(alpha, `遮罩顏色「${overlayColor}」應該是半透明（0 < alpha < 1），不是不透明的純黑`).toBeGreaterThan(0);
    expect(alpha, `遮罩顏色「${overlayColor}」應該是半透明（0 < alpha < 1），不是不透明的純黑`).toBeLessThan(1);
    await expect
      .poll(() => page.evaluate(() => !!document.activeElement?.closest('[role="dialog"]')), { message: '焦點應鎖在對話框內' })
      .toBe(true);

    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
  });

  test('E2E-UIDS-07: 換皮演練（執行期）——只改 token，所有欄位、按鈕、表格外框都跟著變；沒有任何元件繞過單一來源', async ({ page }) => {
    // 演練：圓角改 20px、邊框色改純綠、主色改純紅——完全不動任何元件或頁面。
    // 同時關掉 transition：欄位有 transition-colors（150ms），換色後立刻量會量到動畫中間值（實測 rgb(219,233,232)）；
    // 這裡要驗的是「token 傳到了每個控制項」，不是動畫。
    const RESKIN =
      ':root{--radius-md:20px;--radius-lg:30px;--rs-border:rgb(0,255,0);--rs-primary:rgb(255,0,0);}*,*::before,*::after{transition:none!important}';

    await test.step('欄位：圓角與框色全部跟著變', async () => {
      await openAs(page, '/dashboard/faq');
      await expect(page.locator('select').first()).toBeVisible({ timeout: 15000 });
      await page.addStyleTag({ content: RESKIN });
      const fields = await measureFields(page);
      expect(fields.length).toBeGreaterThanOrEqual(2);
      const stray = fields.filter((f) => f.br !== '20px' || f.bc !== 'rgb(0, 255, 0)').map((f) => `${f.tag}「${f.label}」圓角 ${f.br} 框色 ${f.bc}`);
      expect(stray, `這些欄位沒有跟著 token 變（有人繞過了欄位元件）：\n${stray.join('\n')}`).toEqual([]);
    });

    await test.step('按鈕與表格外框：同樣跟著變', async () => {
      await page.unroute('**/api/**');
      await mockApi(page, baseHandler);
      await page.goto('/dashboard/posts');
      await expect(page.getByRole('row').filter({ hasText: '契約貼文-DRAFT' })).toBeVisible({ timeout: 15000 });
      await page.addStyleTag({ content: RESKIN });
      const cta = page.getByRole('link', { name: '新建貼文' });
      await expect(cta).toHaveCSS('background-color', 'rgb(255, 0, 0)');
      await expect(cta).toHaveCSS('border-top-left-radius', '20px');
      const wrap = page.locator('table').first().locator('xpath=..');
      await expect(wrap).toHaveCSS('border-top-color', 'rgb(0, 255, 0)');
      await expect(wrap).toHaveCSS('border-top-left-radius', '30px');
    });
  });

  test('E2E-UIDS-08: 狀態登錄表語意一致——同一種狀態在所有領域是同一個語意色（Sprint 329 前貼文與知識庫的草稿／封存顏色相反）', async () => {
    // 規則寫在 src/lib/status.ts 檔頭；新增狀態若不屬於任何一條，這裡會紅，逼開發者明確歸類。
    const RULES: Array<[RegExp, string]> = [
      [/^DRAFT$/, 'outline'],
      [/^(PENDING|PENDING_REVIEW|PENDING_APPROVAL|PARTIALLY_RECEIVED)$/, 'warning'],
      [/^(ACTIVE|PUBLISHED|APPROVED|RECEIVED)$/, 'success'],
      [/^(INACTIVE|ARCHIVED)$/, 'secondary'],
      [/^(REJECTED|CANCELLED|SUSPENDED|TERMINATED)$/, 'destructive'],
    ];
    const EXPLICIT: Record<string, string> = { SUBMITTED: 'default' }; // 流程中、等待下一步
    const problems: string[] = [];
    let checked = 0;
    for (const [domain, table] of Object.entries(STATUS_REGISTRY)) {
      for (const [status, entry] of Object.entries(table)) {
        checked++;
        const rule = RULES.find(([re]) => re.test(status));
        const want = rule ? rule[1] : EXPLICIT[status];
        if (!want) problems.push(`${domain}.${status} 沒有對應的語意規則（請在 status.ts 檔頭與本測試的 RULES 明確歸類）`);
        else if (entry.variant !== want) problems.push(`${domain}.${status}「${entry.label}」是 ${entry.variant}，依規則應為 ${want}`);
      }
    }
    expect(checked, '登錄表不該是空的').toBeGreaterThan(20);
    expect(problems, problems.join('\n')).toEqual([]);

    // 功能開關的狀態：待審核必須先判斷（PENDING 時 isEnabled 本身就是 false，DEF-247／DEF-359）
    expect(featureStatusEntry('PENDING', true, false)).toMatchObject({ label: '待審核', variant: 'warning' });
    expect(featureStatusEntry('ACTIVE', false, false)).toMatchObject({ label: '已停用', variant: 'secondary' });
    expect(featureStatusEntry('ACTIVE', false, true)).toMatchObject({ label: '已啟用', variant: 'success' });
  });
});
