import { test, expect, Page } from '@playwright/test';

/**
 * AT-SEC-HDR: 前端頁面安全標頭與 CSP（Sprint 197 標頭、Sprint 198 嚴格 CSP）
 *
 * 登入、結帳頁若沒有 X-Frame-Options，任何網站都能用 iframe 嵌入並誘導使用者點擊（點擊劫持）；
 * 沒有 CSP，XSS 一旦成功就能讀走 localStorage 裡的 token。標頭由 next.config.ts 的 headers()
 * 與 src/proxy.ts（每次請求的 nonce）設定；被移除或 matcher 寫錯時沒有任何功能測試會變紅，
 * 故直接驗證真實回應，並在真實瀏覽器驗證兩件事：CSP 沒有誤擋正常頁面、且真的擋得住注入。
 *
 * 不依賴後端 seed 資料 → 不 flaky。
 *
 * - E2E-SEC-HDR-01~03: 防點擊劫持／MIME 嗅探／Referrer 標頭、404 頁同樣帶、不洩漏 X-Powered-By
 * - E2E-SEC-HDR-04: CSP 為嚴格 nonce 型（無 script 的 unsafe-inline）並含 frame-ancestors／object-src／base-uri
 * - E2E-SEC-HDR-05: nonce 每次請求不同，且 HTML 中每個 <script> 都帶當次 nonce
 * - E2E-SEC-HDR-06: 真實瀏覽器載入主要頁面，零 CSP 違規、inline 主題 script 確實執行
 * - E2E-SEC-HDR-07: 被注入的 inline 事件處理器（XSS 典型手法）確實被瀏覽器擋下
 * - E2E-SEC-HDR-08: HSTS 只在代理告知 https 時才送（純 http 不送）
 */

function directive(csp: string, name: string): string[] {
  const found = csp
    .split(';')
    .map((d) => d.trim().split(/\s+/))
    .find(([n]) => n === name);
  return found ? found.slice(1) : [];
}

const NONCE_IN_CSP = /'nonce-([^']+)'/;

type CspProbeWindow = Window & { __cspViolations?: string[]; __xss?: boolean };

async function recordCspViolations(page: Page) {
  await page.addInitScript(() => {
    const w = window as CspProbeWindow;
    w.__cspViolations = [];
    document.addEventListener('securitypolicyviolation', (e) => {
      w.__cspViolations!.push(`${e.violatedDirective} ${e.blockedURI}`);
    });
  });
}

const violationsOf = (page: Page) =>
  page.evaluate(() => (window as CspProbeWindow).__cspViolations ?? []);

test.describe('AT-SEC-HDR: 前端頁面安全標頭與 CSP', () => {
  test('E2E-SEC-HDR-01: 登入頁帶齊安全標頭', async ({ request }) => {
    const res = await request.get('/login');
    expect(res.status()).toBe(200);
    expect(res.headers()['x-frame-options']).toBe('DENY');
    expect(res.headers()['x-content-type-options']).toBe('nosniff');
    expect(res.headers()['referrer-policy']).toBe('strict-origin-when-cross-origin');
  });

  test('E2E-SEC-HDR-02: 不存在的路徑（404）同樣帶標頭', async ({ request }) => {
    const res = await request.get('/no-such-page-sec-hdr');
    expect(res.status()).toBe(404);
    expect(res.headers()['x-frame-options']).toBe('DENY');
    expect(res.headers()['x-content-type-options']).toBe('nosniff');
  });

  test('E2E-SEC-HDR-03: 不洩漏 X-Powered-By', async ({ request }) => {
    const res = await request.get('/login');
    expect(res.headers()['x-powered-by']).toBeUndefined();
  });

  test('E2E-SEC-HDR-04: CSP 為嚴格 nonce 型', async ({ request }) => {
    const csp = (await request.get('/login')).headers()['content-security-policy'];
    expect(csp).toBeTruthy();

    const scriptSrc = directive(csp, 'script-src');
    expect(scriptSrc).toContain("'strict-dynamic'");
    expect(scriptSrc.filter((s) => NONCE_IN_CSP.test(s))).toHaveLength(1);
    // 有 nonce 時 unsafe-inline 會被瀏覽器忽略，但出現在這裡代表有人想放寬——應該紅燈
    expect(scriptSrc).not.toContain("'unsafe-inline'");

    expect(directive(csp, 'frame-ancestors')).toEqual(["'none'"]);
    expect(directive(csp, 'object-src')).toEqual(["'none'"]);
    expect(directive(csp, 'base-uri')).toEqual(["'self'"]);
    expect(directive(csp, 'default-src')).toEqual(["'self'"]);
  });

  test('E2E-SEC-HDR-05: nonce 每次請求不同，且每個 <script> 都帶當次 nonce', async ({ request }) => {
    const first = await request.get('/login');
    const second = await request.get('/login');
    const nonceOf = (csp: string) => NONCE_IN_CSP.exec(csp)?.[1];

    const n1 = nonceOf(first.headers()['content-security-policy']);
    const n2 = nonceOf(second.headers()['content-security-policy']);
    expect(n1).toBeTruthy();
    expect(n1).not.toBe(n2);

    const html = await first.text();
    const scriptTags = html.match(/<script\b[^>]*>/g) ?? [];
    expect(scriptTags.length).toBeGreaterThan(0);
    for (const tag of scriptTags) {
      expect(tag).toContain(`nonce="${n1}"`);
    }
  });

  test('E2E-SEC-HDR-06: 真實瀏覽器載入主要頁面零 CSP 違規，且 inline 主題 script 確實執行', async ({ page }) => {
    await recordCspViolations(page);
    // ThemeScript 是專案自己的 inline script；若沒帶對 nonce 會被擋下，data-theme 就不會被改寫
    await page.addInitScript(() => localStorage.setItem('rs-theme', 'green'));

    for (const path of ['/login', '/register', '/']) {
      await page.goto(path);
      await expect(page.locator('body')).toBeVisible();
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'green');
      expect(await violationsOf(page), `${path} 出現 CSP 違規`).toEqual([]);
    }
  });

  test('E2E-SEC-HDR-07: 被注入的 inline 事件處理器被瀏覽器擋下', async ({ page }) => {
    await recordCspViolations(page);
    await page.goto('/login');

    // XSS 最常見的手法：把帶 onerror 的元素塞進頁面。沒有 CSP 時 onerror 會執行。
    await page.evaluate(() => {
      document.body.insertAdjacentHTML(
        'beforeend',
        '<img src="/__csp-probe.png" onerror="window.__xss = true">',
      );
    });

    await expect.poll(() => violationsOf(page)).toEqual(
      expect.arrayContaining([expect.stringContaining('script-src-attr')]),
    );
    expect(await page.evaluate(() => (window as CspProbeWindow).__xss)).toBeUndefined();
  });

  test('E2E-SEC-HDR-08: HSTS 只在代理告知 https 時才送', async ({ request }) => {
    const plain = await request.get('/login');
    expect(plain.headers()['strict-transport-security']).toBeUndefined();

    const behindProxy = await request.get('/login', { headers: { 'X-Forwarded-Proto': 'https' } });
    expect(behindProxy.headers()['strict-transport-security']).toBe('max-age=31536000; includeSubDomains');

    const plainHttpForwarded = await request.get('/login', { headers: { 'X-Forwarded-Proto': 'http' } });
    expect(plainHttpForwarded.headers()['strict-transport-security']).toBeUndefined();
  });
});
