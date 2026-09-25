import { test, expect } from '@playwright/test';

/**
 * AT-SEC-HDR: 前端頁面安全標頭（Sprint 197）
 *
 * 登入、結帳頁若沒有 X-Frame-Options，任何網站都能用 iframe 嵌入並誘導使用者點擊（點擊劫持）。
 * 標頭由 next.config.ts 的 headers() 設定；若被移除或 source 樣式寫錯，沒有任何功能測試會變紅，
 * 故以 HTTP 層級直接驗證真實回應（不開瀏覽器、不依賴後端 seed 資料 → 不 flaky）。
 *
 * - E2E-SEC-HDR-01: 登入頁（表單頁）帶防點擊劫持／MIME 嗅探／Referrer 標頭
 * - E2E-SEC-HDR-02: 404 頁面同樣帶標頭（source 樣式須涵蓋所有路徑）
 * - E2E-SEC-HDR-03: 不洩漏 X-Powered-By
 */
test.describe('AT-SEC-HDR: 前端頁面安全標頭', () => {
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
});
