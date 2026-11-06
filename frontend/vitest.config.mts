import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

// 前端單元／元件測試（DEF-458，Sprint 331）。E2E 在 e2e/（Playwright），這裡只放 src/ 內與原始碼並排的 *.test.ts(x)。
//
// 固定時區為台灣：本專案反覆踩到「用 toISOString() 當今天」在 UTC+8 的 00:00～07:59 算成前一天（DEF-362、DEF-405）。
// 在 UTC 的 CI 機器上這類錯誤測不出來，所以測試一律在 Asia/Taipei 跑，結果不隨機器時區而變。
process.env.TZ = 'Asia/Taipei'

export default defineConfig({
  resolve: {
    // 與 tsconfig.json 的 paths（"@/*" → "./src/*"）一致
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['./vitest.setup.ts'],
    // 樣式契約由 E2E 的 computed style 守（at-ui-design-system.spec.ts），單元測試不載入 CSS
    css: false,
    // 預設 5 秒對「同時在跑建置／Docker 的開發機、act 容器」太緊：突變驗證時曾因 next build 同時在跑，
    // 4 個 Radix 對話框案例逾時，再經由模組層級的單例連鎖紅燈（失敗與被測行為無關）。逾時只放寬上限，不影響測試速度。
    testTimeout: 20_000,
  },
})
