# Sprint 224 Plan — 前端相依套件的 critical／high 漏洞（DEF-314）

**Sprint**: Sprint 224
**日期**: 2026-09-30

## 1. 起點

Sprint 223 跑 `make validate-e2e` 時，`npm ci` 印出「12 vulnerabilities（1 critical）」。追蹤表沒有登記過，Sprint 223 登記為 `DEF-314` 並排入本輪。

這輪沒有使用者的新指示；處理的是已登記、AI 可獨立處理、且能被守門驗證的項目。`DEF-311`／`312`／`308`／`303` (5) 仍等使用者決定，未動。

## 2. 查證

**升級前**（`npm audit`，2026-09-30）：含開發相依共 12 個（1 critical、8 high、2 moderate、1 low）；`--omit=dev` 為 7 個（1 critical、5 high、1 moderate）：

- `next` 16.2.2（`package.json` 精確釘定，`create-next-app` 預設寫法）：27 則公告。critical 兩則——「Unauthenticated RCE on windows-hosted servers」與「Image Optimization API 處理 AVIF 的 RCE」（修於 16.3.3）；high——Middleware／Proxy bypass（segment-prefetch、動態路由參數、Turbopack 單語系）、Server Components DoS、Server Actions SSRF／DoS；moderate——「使用 CSP nonce 的 App Router XSS」（修於 16.2.5）。**本專案的嚴格 nonce CSP 就掛在 `src/proxy.ts`**。
- `axios` 1.15.0：28 則（多為 prototype pollution gadget、代理繞過，修於 1.18.0）。
- 傳遞相依：`form-data`、`nanoid`、`postcss`、`sharp`（後兩者隨 `next`）、`baseline-browser-mapping`。

**暴露面**：`frontend/src` 沒有使用 `next/image`，`next.config.ts` 沒設遠端圖片，圖片最佳化那幾則實際可打到的面小；Windows 那則對 Linux／macOS 主機不適用；Server Components DoS 與 proxy 相關的對所有動態頁成立（建置輸出 89 個路由全部是動態渲染，因為 nonce 需要每次請求一個新值）。

**為什麼沒人擋**：`ci.yml` 的 `npm audit --audit-level=high` 是 `continue-on-error: true`，而且 `ci.yml` 不是 push 觸發的 workflow；`npm ci` 印出的漏洞數不會讓任何關卡失敗。

**升級風險評估**：`npm audit` 標示 `next` → 16.3.7 為 `isSemVerMajor: false`。查閱該版本內附文件（`dist/docs/01-app/02-guides/upgrading/`）：16.3 只有一個**選用**的 Cache Components codemod（本專案沒啟用 `cacheComponents`），沒有 16.2→16.3 的破壞性變更。Node 20.19.5 ≥ 需求的 20.9.0；`react`／`react-dom` 19.2.4 符合 peer；`@playwright/test` 符合 peer。

## 3. 修法

- `next` 16.2.2 → **16.3.7**、`eslint-config-next` 16.2.2 → **16.3.7**（維持既有的精確釘定寫法，兩者同步）。
- `axios` `^1.7.9` → `^1.20.0`（實際安裝 1.20.0；下限拉高，之後全新安裝不會回到有漏洞的版本）。
- `npm audit fix` 處理傳遞相依：`form-data` 4.0.5 → 4.0.6、`nanoid` 3.3.11 → 3.3.19、`baseline-browser-mapping` 2.10.16 → 2.11.26；`postcss` 8.5.9 → 8.5.23 與 `sharp` 0.34.5 → 0.35.5 隨 `next` 升級（`sharp` 為 0.x 次版本，本專案未使用 `next/image`）；`next` 內含的舊版 `postcss@8.4.31` 已移除。
- `package-lock.json`：54 個套件變版本、24 個新增、1 個移除，**全是 minor／patch，沒有大版本跳動**（逐一比對新舊 lockfile）。

## 4. 驗證結果

- `npm audit`：含開發相依與 `--omit=dev` 皆 **0 vulnerabilities**（升級前 12／7）；守門的 `npm ci`（從新 lockfile 乾淨安裝）同樣印出 `found 0 vulnerabilities`，順便驗證 lockfile 一致。
- `npx tsc --noEmit` 乾淨；`npx eslint .` 0 error。警告由 93 變 95：新版 `eslint-config-next` 多了規則 `@next/next/no-location-assign-relative-destination`，命中兩處**既有**程式碼（`lib/axios.ts` 的 401 導向登入頁、`services/oauth.ts` 的 OAuth 導向）；屬建議性警告，未處理。
- `next build`：`Next.js 16.3.7 (Turbopack)` 編譯成功。
- `make validate-e2e`（全套、乾淨 DB、打包 JAR、新 lockfile）：**96 個測試：92 通過／4 略過（與基準相同的條件式略過）／0 失敗**，3.9 分鐘，含安全標頭與嚴格 nonce CSP 的 `at-security-headers`（S198）與 Sprint 223 的 `at-booking-payment-real`。

## 5. 決策與已知限制

- **沒有對新版 Next 修補的每一個漏洞逐一重現**，只依公告的影響版本範圍判斷「已不在範圍內」；「不再受影響」的依據是版本，不是重現失敗。
- **`ci.yml` 的 `npm audit` 仍是 `continue-on-error: true`，本輪沒動**。是否改成阻擋性是 CI 政策的取捨：改成阻擋後，任何一天出現新公告都會讓不相干的 push 突然變紅。折衷做法是排程（例如每週）跑 `npm audit --omit=dev --audit-level=high` 並在失敗時通知，不擋 push；**需使用者決定**。
- 新版 Next 的兩個新 lint 警告未處理（既有程式碼，行為刻意）。

## 6. 後續

- 等使用者決定：`DEF-311`／`DEF-312`（啟用 Stripe 前必決）／`DEF-308`／`DEF-303` (5)；依賴審計要不要進 CI 關卡（§5）。
- 已登記未修：`DEF-315`（同一秒簽發的 Refresh Token 位元組相同）。
