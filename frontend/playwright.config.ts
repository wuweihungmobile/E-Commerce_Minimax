import { defineConfig, devices } from '@playwright/test';

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