import { test, expect } from '@playwright/test';

/**
 * AT-AUTH-REFRESH-REAL: Refresh Token 輪替（真實後端，打包 JAR）—— Sprint 230（DEF-315）
 *
 * 登入後立刻換發，兩次簽發幾乎必然落在同一秒內。修復前 `generateRefreshToken` 沒有 jti、iat 只到秒，同一秒內簽發的兩顆 token
 * 位元組完全相同：換發出的新 token 與舊 token 共用 Redis key，輪替剛標成「已使用」的 key 又被寫回「有效」，
 * 以舊 token 重放會回 200（Sprint 223 用打包 JAR 實測）。
 *
 * 這個案例在打包後的真實後端（真實 Redis、與生產相同的序列化器）上走一遍：新 token 必須與舊 token 不同、舊 token 只能用一次、
 * 偵測到重放後撤銷所有 session。修復前，只要兩次簽發落在同一秒（登入後立刻換發時幾乎必然），第一個斷言就會失敗。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const PASSWORD = 'Test123!';

test.describe('AT-AUTH-REFRESH-REAL: Refresh Token 輪替（真實後端）', () => {
  test('E2E-RTR-01: 登入後立刻換發 → 新 token 與舊 token 不同；以舊 token 重放 → 401 E-1003，且新 token 也被撤銷', async ({ request }) => {
    const email = `e2e-rtr-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
    const register = await request.post(`${API_BASE}/v2/auth/register`, {
      data: { email, password: PASSWORD, fullName: 'E2E 換發', userType: 'BUYER' },
    });
    expect(register.status(), await register.text()).toBeLessThan(300);

    const login = await request.post(`${API_BASE}/v2/auth/login`, { data: { email, password: PASSWORD } });
    expect(login.status(), await login.text()).toBe(200);
    const first = (await login.json()).data.refreshToken as string;

    // 登入後立刻換發：兩次簽發幾乎必然落在同一秒內（修復前兩顆 token 位元組相同）
    const refreshed = await request.post(`${API_BASE}/v2/auth/refresh`, { data: { refreshToken: first } });
    expect(refreshed.status(), await refreshed.text()).toBe(200);
    const second = (await refreshed.json()).data.refreshToken as string;
    expect(second, '換發出的新 token 必須與舊 token 不同（同一秒內相同就會共用 Redis key）').not.toBe(first);

    // 舊 token 只能用一次：重放必須被拒絕
    const replay = await request.post(`${API_BASE}/v2/auth/refresh`, { data: { refreshToken: first } });
    expect(replay.status(), '以已換發過的舊 token 重放必須回 401（修復前同一秒內會回 200）').toBe(401);
    expect(await replay.text()).toContain('E-1003');

    // 偵測到重放 → 撤銷該使用者所有 refresh token：換發出的新 token 也不能再用
    const afterRevoke = await request.post(`${API_BASE}/v2/auth/refresh`, { data: { refreshToken: second } });
    expect(afterRevoke.status(), '偵測到重放後，新 token 也已被撤銷').toBe(401);
  });
});
