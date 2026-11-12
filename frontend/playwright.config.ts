import { defineConfig, devices } from '@playwright/test';

// 「伺服器端渲染不可用」專案（DEF-500，Sprint 341）。
//
// 公開頁的首份資料改由 Next 伺服器直接向後端取（SEO），瀏覽器端的 page.route 攔截不到那一個請求。
// 下面這些規格靠 page.route 模擬商品／房源／貼文卡片的回應（假的編號，真實後端沒有），在一般伺服器上伺服器端會向真實後端
// 取不到（404）；它們要驗證的是「瀏覽器自己載入資料」的那條路徑——也就是伺服器端取不到資料時的備援。
// 所以這個專案對一台「後端位址故意不通」（API_INTERNAL_URL 指向沒人在聽的埠）的 Next 伺服器執行：伺服器端一律取不到、
// 元件退回由瀏覽器載入，行為與改成伺服器端渲染之前完全相同，原本的模擬照常生效。
// 這台伺服器由 scripts/validate-e2e.sh 啟動（預設 :3001，用同一份建置）；單獨執行這些規格時要自己先啟動它：
//   cd frontend && API_INTERNAL_URL=http://127.0.0.1:1/api PORT=3001 npm run start
const SSR_UNAVAILABLE_SPECS = [
  /at-listing-detail\.spec\.ts$/,
  /at-room-booking\.spec\.ts$/,
  /at-homepage\.spec\.ts$/,
  /at-load-failure-screens\.spec\.ts$/,
];
const SSR_UNAVAILABLE_URL = process.env.E2E_SSR_UNAVAILABLE_URL || 'http://localhost:3001';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
      testIgnore: SSR_UNAVAILABLE_SPECS,
    },
    {
      name: 'ssr-unavailable',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: SSR_UNAVAILABLE_URL,
      },
      testMatch: SSR_UNAVAILABLE_SPECS,
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    // CI 已由 workflow（Start Frontend: npm run start）啟動 production server 並等待就緒，
    // 故一律重用既有 server，避免 Playwright 另起 dev server 撞上已佔用的 3000；
    // 本機若無 server 在跑則自動以上方 command 啟動。
    reuseExistingServer: true,
    timeout: 120000,
  },
});