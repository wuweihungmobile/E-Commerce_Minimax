import { createServer } from 'node:http';
import { AddressInfo } from 'node:net';
import { test, expect } from '@playwright/test';
import { postAuthApi } from './helpers/auth';

/**
 * AT-AUTH-API-RETRY: 認證 API 輔助函式 postAuthApi 對 HTTP 429 的退避重試（本機假伺服器，不需要後端）—— Sprint 306（DEF-420）
 *
 * 為什麼需要：登入／註冊端點有 IP 限流（每個來源 IP、每個路徑 30 次／分），E2E 套件所有請求來自同一個 IP。規格直接以
 * `request.post` 打這兩個端點時沒有任何重試，套件尾端密集登入時 429 會變成 `expect(status).toBe(200)` 的硬失敗
 * （Sprint 304、305 預設並行度的守門每次都因此失敗）。postAuthApi 改成依 `Retry-After` 等待後重試。
 *
 * 為什麼不只靠守門驗證：重試是否生效，守門本身看不出來（後端日誌在腳本結束時被刪、一次全綠不代表有走到重試路徑）；
 * UI 層的 submitLogin 重試就曾經從 Sprint 234 起對真實 429 悄悄不生效，到 Sprint 288 才被量到（DEF-396）。
 * 所以這裡用一個會依腳本回 429／200 的本機假伺服器，確定地走過每條路徑，並量兩次請求之間的間隔。
 *
 * 間隔只斷言下限：機器負載再高，等待只會更久、不會更短，所以下限不會因負載而誤判；
 * 下限同時是分辨「依標頭等待」與「用預設值等待」的依據（標頭寫 3 秒、預設是 2.5 秒）。
 */

interface Reply {
  status: number;
  retryAfter?: string;
}

/** 啟動一個依序回覆 `replies` 的本機伺服器（用完最後一個就一直回最後一個），把每次請求抵達的時間記進 arrivals。 */
async function withServer(
  replies: Reply[],
  run: (url: string, arrivals: number[]) => Promise<void>
): Promise<void> {
  const arrivals: number[] = [];
  const server = createServer((req, res) => {
    req.resume();
    arrivals.push(Date.now());
    const reply = replies[Math.min(arrivals.length, replies.length) - 1];
    res.statusCode = reply.status;
    if (reply.retryAfter !== undefined) res.setHeader('Retry-After', reply.retryAfter);
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ status: reply.status }));
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    await run(`http://127.0.0.1:${(server.address() as AddressInfo).port}/v2/auth/login`, arrivals);
  } finally {
    server.closeAllConnections();
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
}

test.describe('AT-AUTH-API-RETRY: postAuthApi 對 429 的退避重試（本機假伺服器）', () => {
  test('E2E-RETRY-01: 429 帶 Retry-After: 3 → 等標頭指定的 3 秒（不是預設的 2.5 秒）後重試，第二次拿到 200', async ({ request }) => {
    await withServer([{ status: 429, retryAfter: '3' }, { status: 200 }], async (url, arrivals) => {
      const response = await postAuthApi(request, url, { email: 'retry@example.com' });
      expect(response.status()).toBe(200);
      expect(arrivals, '第一次被擋下、第二次成功，共兩次請求').toHaveLength(2);
      expect(arrivals[1] - arrivals[0], '兩次請求的間隔要照標頭等滿 3 秒').toBeGreaterThanOrEqual(2_900);
    });
  });

  test('E2E-RETRY-02: 429 沒有 Retry-After → 用預設等待 2.5 秒後重試', async ({ request }) => {
    await withServer([{ status: 429 }, { status: 200 }], async (url, arrivals) => {
      const response = await postAuthApi(request, url, { email: 'retry@example.com' });
      expect(response.status()).toBe(200);
      expect(arrivals).toHaveLength(2);
      expect(arrivals[1] - arrivals[0], '沒有標頭時要等滿預設的 2.5 秒').toBeGreaterThanOrEqual(2_400);
    });
  });

  test('E2E-RETRY-03: 持續 429 → 重試 8 次（共 9 次請求）後丟出明確的錯誤，不悄悄回傳 429 讓呼叫端在沒有 token 的狀態下往下走', async ({ request }) => {
    await withServer([{ status: 429, retryAfter: '1' }], async (url, arrivals) => {
      await expect(postAuthApi(request, url, { email: 'retry@example.com' })).rejects.toThrow(/持續被速率限制擋下/);
      expect(arrivals).toHaveLength(9);
    });
  });

  test('E2E-RETRY-04: 非 429 的回應（成功 200、帳號或密碼錯誤 401）一律只送一次、原樣回傳——不重試呼叫端可能刻意要測的失敗', async ({ request }) => {
    for (const status of [200, 401]) {
      await withServer([{ status }], async (url, arrivals) => {
        const response = await postAuthApi(request, url, { email: 'retry@example.com' });
        expect(response.status()).toBe(status);
        expect(arrivals, `HTTP ${status} 不該重試`).toHaveLength(1);
      });
    }
  });
});
