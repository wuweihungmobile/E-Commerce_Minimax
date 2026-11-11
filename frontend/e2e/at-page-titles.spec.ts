import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';

/**
 * AT-PAGE-TITLES: 每個頁面有自己的 <title>（DEF-465，Sprint 332，真實瀏覽器）
 *
 * 在這之前整站 104 個頁面有 102 個共用同一個 document.title（「意象若水 RUOSHUI｜生活減法…」）：
 * 分頁列、瀏覽紀錄、書籤分不出是哪一頁，螢幕閱讀器換頁時念出的標題也一模一樣。
 *
 * 守的是三件靜態守門（src/architecture/page-titles.test.ts）看不到的事：
 *   ① 伺服器真的把標題寫進 HTML——各種形態的頁面（Server Component、薄殼改成 Server Component 的、有狀態的 client 頁用 layout 提供的、
 *      巢狀路由）都量一個；巢狀路由特別重要：中間層 layout 的標題沒有 template 會切斷上層的 template（開發時實際踩到，只有瀏覽器看得到）。
 *   ② 軟導覽（client-side navigation，不整頁重載）換頁之後 document.title 會跟著更新——使用者實際換頁的方式就是這個。
 *   ③ 靜態守門的「模擬 Next 解析標題」與真實的 Next 一致（不一致時以這裡為準）。
 *
 * 為什麼 HTML 層用 request 不開瀏覽器：登入守門是 client 端的，任何路由的 HTML 都會回 200 並帶標題；不需要登入、不需要真實資料，
 * 動態路由用任意的 id 即可，涵蓋的路由形態就能比開瀏覽器多很多。
 *
 * 登入密度：本檔 1 次表單登入（管理員，由 Flyway 種子＋E2E 專用密碼）。
 */

const ADMIN ={ email: 'admin@nextkey.local', password: 'Test123!' };
const BRAND = '意象若水 RUOSHUI';
const SITE_TITLE = `${BRAND}｜生活減法，無負擔的購物體驗`;
const site = (name: string) => `${name}｜${BRAND}`;
const dashboard = (name: string) => `${name}｜賣家後台｜${BRAND}`;
const admin = (name: string) => `${name}｜平台管理｜${BRAND}`;

/** [路由, 預期標題, 說明這條路由代表哪一種形態] */
const SSR_TITLES: Array<[string, string, string]> = [
  ['/', SITE_TITLE, '首頁：完整品牌標題（title.absolute）'],
  ['/products', site('商品'), 'Server Component'],
  ['/listings/x', site('商品／房源詳情'), 'Server Component（動態路由）'],
  ['/stores/demo/products', site('店鋪商品'), 'Server Component（巢狀、動態路由）'],
  ['/privacy', site('隱私權政策'), '本來就有標題的頁面：不可變成「…｜品牌｜品牌」'],
  ['/login', site('登入'), '有狀態的 client 頁：標題由同目錄 layout 提供'],
  ['/blog', site('部落格'), 'Server Component（Sprint 339，DEF-495：跨店鋪列表；標題在同目錄 layout，/blog/category/{id} 已轉址到這裡）'],
  ['/orders/x/payment/cancel', site('訂單付款已取消'), '薄殼改成 Server Component，在 client 頁 /orders/[id] 的 layout 底下（template 不能被切斷）'],
  ['/bookings/x/payment/success', site('預訂付款結果'), 'client 頁在 client 頁 /bookings/[id] 的 layout 底下'],
  ['/dashboard', dashboard('營運總覽'), '賣家後台首頁：區域 layout 的 default'],
  ['/dashboard/products', dashboard('商品管理'), '薄殼（無 hook）改成 Server Component'],
  ['/dashboard/erp/inventory/x', dashboard('庫存明細'), 'client 頁在 client 頁 /dashboard/erp/inventory 的 layout 底下'],
  ['/dashboard/erp/purchase-orders/x/edit', dashboard('編輯採購訂單'), '薄殼（路由 hook）改成 async Server Component，在 client 頁的 layout 底下'],
  ['/admin', admin('平台總覽'), '平台管理台首頁：區域 layout 的 default'],
  ['/admin/tenants/x/review', admin('店鋪詳情'), 'client 頁（巢狀、動態路由）'],
  ['/dashboard/tenants', dashboard('店鋪管理'), '同名頁面：與下一行只差區域後綴'],
  ['/admin/tenants', admin('店鋪管理'), '同名頁面：與上一行只差區域後綴'],
];

test.describe('AT-PAGE-TITLES: 伺服器寫進 HTML 的標題（各種頁面形態，含巢狀路由）', () => {
  for (const [path, expected, note] of SSR_TITLES) {
    test(`E2E-TITLE-01: ${path} → 「${expected}」（${note}）`, async ({ request }) => {
      const response = await request.get(path);
      expect(response.status()).toBe(200);
      const html = await response.text();
      const titles = [...html.matchAll(/<title>([^<]*)<\/title>/g)].map((m) => m[1]);
      expect(titles, '一個頁面只該有一個 <title>').toEqual([expected]);
    });
  }
});

/** 讓軟導覽可以被證明：在頁面 window 上放記號，整頁重新載入的話記號會消失 */
async function markPage(page: Page) {
  await page.evaluate(() => {
    (window as unknown as { __softNavMarker: boolean }).__softNavMarker = true;
  });
}
async function stillSamePage(page: Page): Promise<boolean> {
  return page.evaluate(() => (window as unknown as { __softNavMarker?: boolean }).__softNavMarker === true);
}

test.describe('AT-PAGE-TITLES: 換頁（軟導覽，不整頁重載）之後標題跟著更新', () => {
  test('E2E-TITLE-02: 賣家後台側欄連續點擊——每換一頁標題就換成那一頁的（涵蓋 Server Component、client 頁、巢狀層級）', async ({ page }: { page: Page }) => {
    await loginOnly(page, ADMIN.email, ADMIN.password);
    await page.goto('/dashboard');
    await expect(page).toHaveTitle(dashboard('營運總覽'));
    await markPage(page);

    const sidebar = page.getByTestId('seller-sidebar');
    const steps: Array<[string, string]> = [
      ['商品管理', dashboard('商品管理')], // 薄殼改成 Server Component
      ['訂單管理', dashboard('訂單管理')], // client 頁，標題在 layout
      ['庫存', dashboard('庫存')], // client 頁，底下還有子路由
      ['供應商', dashboard('供應商')],
      ['貼文管理', dashboard('貼文管理')],
      ['營運總覽', dashboard('營運總覽')], // 回到區域首頁（layout 的 default）
    ];
    for (const [label, title] of steps) {
      await sidebar.getByRole('link', { name: label, exact: true }).click();
      await expect(page, `點「${label}」之後`).toHaveTitle(title);
    }
    expect(await stillSamePage(page), '這幾次換頁都應該是軟導覽（不整頁重載）；否則上面量到的只是整頁載入的標題').toBe(true);
  });

  test('E2E-TITLE-03: 平台管理台側欄換頁（另一個區域的 template）；店面頁尾連結換頁（一般頁面的 template）', async ({ page }: { page: Page }) => {
    await loginOnly(page, ADMIN.email, ADMIN.password);
    await page.goto('/admin');
    await expect(page).toHaveTitle(admin('平台總覽'));
    await markPage(page);
    await page.getByTestId('seller-sidebar').getByRole('link', { name: '稽核紀錄', exact: true }).click();
    await expect(page).toHaveTitle(admin('稽核紀錄'));
    await page.getByTestId('seller-sidebar').getByRole('link', { name: '租戶管理', exact: true }).click();
    await expect(page).toHaveTitle(admin('店鋪管理'));
    expect(await stillSamePage(page)).toBe(true);

    // 一般頁面：從隱私權政策用頁尾連結換到服務條款
    await page.goto('/privacy');
    await expect(page).toHaveTitle(site('隱私權政策'));
    await markPage(page);
    await page.getByTestId('storefront-footer').getByRole('link', { name: '服務條款' }).click();
    await expect(page).toHaveTitle(site('服務條款'));
    expect(await stillSamePage(page)).toBe(true);
  });
});
