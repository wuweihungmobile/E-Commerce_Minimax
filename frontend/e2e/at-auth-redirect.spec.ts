import { test, expect } from '@playwright/test';
import { landingPathForRole, loginHref, safeRedirectTarget } from '../src/lib/auth-redirect';

/**
 * AT-AUTH-REDIRECT: 登入後的落地頁與 ?redirect= 驗證（純函式，不開瀏覽器、不需後端）—— Sprint 301（DEF-409）
 *
 * 為什麼用表格式測試：`safeRedirectTarget` 是開放轉址與 XSS 的防線（Next 文件明說不可把未驗證的網址傳給 router.push），
 * 惡意輸入的種類很多（`//host`、`/\host`、`https://…`、`javascript:`、正規化後變成 `//host` 的 `/a/..//host`…），
 * 逐一走真實登入太貴也太慢；整合（登入頁真的呼叫它、導向真的生效）由 at-login-landing-real.spec.ts 的真實登入案例守住。
 * 這支規格直接 import src/lib 的純函式（沒有任何瀏覽器 API），由 Playwright 的 TypeScript 轉譯執行。
 */

const ORIGIN = 'http://localhost:3000';

test.describe('AT-AUTH-REDIRECT: 依角色決定登入後的落地頁', () => {
  const cases: Array<[string | null | undefined, string]> = [
    ['BUYER', '/'],
    ['STORE_OWNER', '/dashboard'],
    ['STORE_STAFF', '/dashboard'],
    ['SELLER', '/dashboard'],
    ['HOST', '/dashboard'],
    ['ADMIN', '/admin'],
    ['SUPER_ADMIN', '/admin'],
    ['GUEST', '/'],
    ['', '/'],
    [undefined, '/'],
    [null, '/'],
  ];
  for (const [role, expected] of cases) {
    test(`E2E-AUTHREDIR-01: 角色 ${JSON.stringify(role)} → ${expected}`, () => {
      expect(landingPathForRole(role)).toBe(expected);
    });
  }
});

test.describe('AT-AUTH-REDIRECT: ?redirect= 只接受同源的站內路徑', () => {
  const accepted: Array<[string, string]> = [
    ['/orders', '/orders'],
    ['/cart', '/cart'],
    ['/orders?tab=returns#top', '/orders?tab=returns#top'],
    ['/dashboard/orders/123', '/dashboard/orders/123'],
    ['/dashboard/returns?page=2', '/dashboard/returns?page=2'],
    ['/loginfo', '/loginfo'], // 只是前綴相同，不是登入流程頁面，不得誤擋
  ];
  for (const [raw, expected] of accepted) {
    test(`E2E-AUTHREDIR-02: 接受 ${JSON.stringify(raw)}`, () => {
      expect(safeRedirectTarget(raw, ORIGIN)).toBe(expected);
    });
  }

  const rejected: Array<[string | null | undefined, string]> = [
    [null, '沒有參數'],
    [undefined, '沒有參數'],
    ['', '空字串'],
    ['orders', '沒有開頭的斜線'],
    [' /orders', '開頭有空白'],
    ['https://evil.example.com', '外部絕對網址'],
    ['http://localhost:3000/orders', '同源的絕對網址也不收（只收以 / 開頭的站內路徑）'],
    ['//evil.example.com', '協定相對網址（開放轉址）'],
    ['/\\evil.example.com', '反斜線會被瀏覽器當成斜線 → //evil.example.com'],
    ['/\t/evil.example.com', '夾帶定位字元，URL 解析會移除它 → //evil.example.com'],
    ['/a/..//evil.example.com', '正規化後變成 //evil.example.com（開頭沒有 // 也能繞過）'],
    ['//', '只有兩個斜線：URL 解析會丟例外，必須吞掉並視為無效，不能讓登入頁崩潰'],
    ['/\\', '反斜線結尾：同上'],
    ['//evil.example.com:99999', '埠號超出範圍：URL 解析丟例外，同上'],
    ['javascript:alert(1)', 'javascript: 協定（XSS）'],
    ['/login', '登入頁本身（會繞圈）'],
    ['/login?redirect=%2Forders', '登入頁本身（會繞圈）'],
    ['/register', '註冊頁'],
    ['/forgot-password', '忘記密碼頁'],
    ['/reset-password', '重設密碼頁'],
    ['/verify-email', 'Email 驗證頁'],
    ['/oauth/callback/google', 'OAuth 回呼'],
  ];
  for (const [raw, why] of rejected) {
    test(`E2E-AUTHREDIR-03: 拒絕 ${JSON.stringify(raw)}（${why}）`, () => {
      expect(safeRedirectTarget(raw, ORIGIN)).toBeNull();
    });
  }
});

test.describe('AT-AUTH-REDIRECT: 要求先登入時的導向網址（loginHref）', () => {
  // loginHref 只讀 window.location.pathname／search，用最小的假 window 在 Node 裡驅動，不需要瀏覽器
  function loginHrefAt(pathname: string, search: string): string {
    const g = globalThis as { window?: unknown };
    const previous = g.window;
    g.window = { location: { pathname, search } };
    try {
      return loginHref();
    } finally {
      if (previous === undefined) delete g.window;
      else g.window = previous;
    }
  }

  const remembered: Array<[string, string]> = [
    ['/orders', ''],
    ['/dashboard/orders/123', '?tab=returns'],
    ['/dashboard/orders', '?status=PAID&page=2'], // 搜尋參數含 &：沒編碼的話 redirect 只會剩 ?status=PAID
    ['/loginfo', ''], // 只是前綴相同，不是登入流程頁面，要照常記住
  ];
  for (const [pathname, search] of remembered) {
    test(`E2E-AUTHREDIR-04: 在 ${pathname}${search} 被要求登入 → 記住這一頁，且登入頁能原樣接受回來`, () => {
      const href = loginHrefAt(pathname, search);
      expect(href.startsWith('/login?redirect=')).toBe(true);
      // 往返：登入頁從網址列取出 redirect，驗證後必須等於原本所在的頁面（否則登入後回不到原頁）
      const back = new URL(href, ORIGIN).searchParams.get('redirect');
      expect(safeRedirectTarget(back, ORIGIN)).toBe(pathname + search);
    });
  }

  for (const pathname of ['/login', '/register', '/forgot-password', '/reset-password', '/verify-email', '/oauth/callback/google']) {
    test(`E2E-AUTHREDIR-05: 已經在 ${pathname} 時不帶 redirect（否則登入後繞回登入頁）`, () => {
      expect(loginHrefAt(pathname, '?redirect=%2Forders')).toBe('/login');
    });
  }

  test('E2E-AUTHREDIR-06: 伺服器端渲染沒有 window → 退回 /login，不丟例外', () => {
    expect(loginHref()).toBe('/login');
  });
});
