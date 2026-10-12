# Sprint 254 計畫：修復 DEF-359 + DEF-362（Sprint 253 登記的無決策待修缺陷）

## 1. 起點與範圍

### 1.1 起點

使用者貼回 Sprint 253 總結，只說「請繼續完成任務」，沒有針對任何一項逐條用 `==>` 回覆。依既有慣例（見記憶 `inline-decision-replies-mean-proceed` 的變體說明），這**不是**對「待您決策」項目的授權，而是：做總結裡自己列為「還沒做」、且 AI 能獨立處理（追蹤表沒標「需使用者決定」）的事。

Sprint 253 新登記 5 個缺陷，追蹤表逐項標註：
- **DEF-358**（4 個 PRD 路由真缺口）：🟡 需使用者確認每項是否要做、範圍多大 → **本輪不做**，留待收尾提出。
- **DEF-359**（自助功能開關頁徽章判斷順序錯誤）：🟢 不需產品決策，可下個 Sprint 直接修 → **本輪做**。
- **DEF-360**（`markMaintenance` 對全新房源靜默空操作）：🟠 需使用者確認修法方向（UPSERT vs 預先生成日曆列） → **本輪不做**，留待收尾提出。
- **DEF-361**（React Hydration Error #418，共用導覽列模式跨頁面既有缺陷）：🟢 無需決策，但根因修復範圍超出本輪（需排查所有共用該導覽列模式的頁面）→ **本輪不展開**，維持現狀（Rule 3：不擴大範圍去「順便」動其他頁面）。
- **DEF-362**（定價日曆時區誤判，UTC vs 本地時區）：🟢 不需產品決策，純前端修正 → **本輪做**。

### 1.2 本輪範圍：僅 DEF-359 + DEF-362，皆為前端純修正，不動後端

## 2. 實作範圍（逐項編譯＋測試，不累積開發）

### 2.1 DEF-359：`frontend/src/app/dashboard/tenants/[id]/features/page.tsx`

`getStatusBadge` 判斷順序錯誤：`if (!enabled) return 已停用` 放在最前面，但「待審核」（`requiresAdminReview && status==='PENDING'`）本身 `isEnabled` 就是 `false`（DEF-247 既有設計），導致此分支永遠先被 `!enabled` 擋住。修法：比照本輪已存在的 Admin 版頁面（`admin/tenants/[id]/features/page.tsx`，Sprint 253 建立時已用正確順序）調整判斷順序——「待審核」優先判斷。

### 2.2 DEF-362：`frontend/src/components/pricing/PricingCalendarPreview.tsx`

`toISOString()` 回傳 UTC 日期，用於「今天」這類本地日曆日概念時，在 UTC+8 時區每天 00:00~07:59 會算成前一天，使後端 `LocalDate.now()`（系統/JVM 時區）比對誤判為過去日期，回 400。修法：新增 `toLocalDateString()` 輔助函式（用 `getFullYear`/`getMonth`/`getDate`，不受時區位移影響），取代檔案內全部 5 處 `toISOString().split('T')[0]` 用法：
- 日曆總覽的 `start`/`end`（實際觸發 DEF-362 400 錯誤的呼叫）
- `getDefaultDates()` 的 `checkIn`/`checkOut` 預設值（同一根因，同一元件，雖未被回報但會影響「試算特定入住區間」功能）
- 兩個日期 `<Input>` 的 `min`/`max` 邊界（同一根因，影響選擇器在該時間窗口的可選範圍）

**範圍判斷**：這 5 處都是同一元件、同一根因（`toISOString()` 誤用於本地日曆日），不是「順便改進相鄰程式碼」，是修同一個缺陷在同一檔案的全部發生點。

## 3. 測試

- 兩個檔案改動皆為純前端，無後端變更，不需 `mvn verify`。
- 前端無獨立單元測試框架（`package.json` `test` script 為 placeholder），既有慣例以 `tsc --noEmit` + `eslint` 為編譯/靜態檢查層，Playwright E2E 為行為驗證層。
- **DEF-359**：新增 `frontend/e2e/at-dashboard-tenant-features-real.spec.ts`（既有自助頁從未有 E2E 覆蓋，這正是此 bug 長期未被發現的原因）。重用 `at-admin-tenant-features-real.spec.ts` 已驗證過的 setup（`seedStore` + `PUT /v2/dashboard/tenants/features/BOOKING_ENABLED` 送出需審核申請），斷言自助頁徽章顯示「待審核」而非「已停用」。
- **DEF-362**：既有 `at-dashboard-pricing-calendar-real.spec.ts` 的 E2E-PRICING-CAL-01 原本因 DEF-362 刻意不斷言日曆資料載入成功（見該檔案 DEF-362 註解區塊）。修復後移除該註解與迴避寫法，改為正面斷言載入成功、無錯誤訊息——消除一個已修復缺陷卻被測試靜默繞過的情況（CLAUDE.md Rule 12：不留默默跳過的驗證缺口）。
- 全量 `make validate-release`（act 三個 job＋schema 漂移＋完整 E2E）全綠後才 push。

**實測結果**：先獨立跑 `make validate-e2e`（commit 前，風險較高的首次新增/改寫 E2E 情境，刻意用一次重跑換取早期回饋）：schema 對齊無漂移，Playwright **154 passed／4 skipped／0 failed**（4.9 分鐘；較 Sprint 253 的 153 passed 多 1，恰為本輪新增的 `E2E-DASH-FEAT-01`），逐行核對輸出確認無任何 `failed`/`Error` 標記，不只信任背景任務通知的 exit code。

## 4. 不在本輪範圍（留待使用者決策）

收尾時以 `AskUserQuestion` 提出 DEF-358（4 個路由真缺口，逐項確認是否要做）與 DEF-360（`markMaintenance` 修法方向：UPSERT 語意 vs 預先生成日曆列）。
