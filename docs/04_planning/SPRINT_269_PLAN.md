# Sprint 269 Plan — 22 個 Dashboard 頁面 Hydration Error #418 根因修復（DEF-361）

**Sprint**: Sprint 269
**日期**: 2026-10-15

## 1. 範圍與起點

[SPRINT_268_PLAN.md](SPRINT_268_PLAN.md) 完成後，Sprint 261 文件一致性稽核登記的 9 項缺口全數結案。使用者對追蹤表盤點後，選擇依序處理 3 個「不需產品決策、AI 可獨立處理」的候選缺口：DEF-361（本輪）→ DEF-332 殘留範圍 → DEF-317。

DEF-361 原登記：`/dashboard/pricing/calendar`（Sprint 253 新頁）與既有 `/dashboard/pricing/rules` 在瀏覽器 console 噴出 React Hydration Error #418，推測是兩頁共用的既有導覽列寫法（`{AuthService.getCurrentUser()?.email}` 直接在渲染中讀 localStorage）所致，當時僅以放寬 E2E 斷言 timeout 處理，根因未修。

## 2. 問題複核

### 2.1 確認根因

`AuthService.getCurrentUser()`（`frontend/src/services/auth.ts:198-201`）在 `typeof window === 'undefined'` 時回傳 `null`（SSR 階段），否則讀取 `localStorage` 回傳真實使用者。Next.js 對 `'use client'` 元件仍會先做 SSR 再於客戶端 hydrate；hydration 當下的客戶端初次渲染會立即執行 `getCurrentUser()`（此時 `window` 已存在），與 SSR 輸出不一致，觸發 React Hydration Error #418。

### 2.2 範圍遠大於原登記

`grep -rl "AuthService.getCurrentUser()?.email" frontend/src/app` 確認全站 **22 個 `/dashboard/*` 頁面**都直接在渲染中呼叫此寫法（同一既有導覽列慣例，`DEF-324` 已記錄「無共用 dashboard layout」，故每頁各自內嵌），並非僅原登記的 2 頁。22 個頁面的三個錨點行（import、`const router = useRouter()`、`{AuthService.getCurrentUser()?.email}`）格式完全一致，逐一 `grep -c` 核對皆恰好各一次。

## 3. 做法

### 3.1 新增共用 hook（初版，後修正）

初版採 `useState('')` + `useEffect(() => setEmail(...), [])`——SSR 與 hydration 當下皆回傳初始值 `''`（一致），掛載後才切換為真實值。

### 3.2 `npx eslint` 擋下初版：`react-hooks/set-state-in-effect`

本專案（Next.js 16 + React 19 + `eslint-config-next`）啟用了訓練資料中沒有的嚴格 react-hooks 規則，禁止在 effect body 內同步呼叫 setState。初版寫法被判定為 `Avoid calling setState() directly within an effect`。

查找既有慣例：`frontend/src/components/theme/themeStore.ts`／`ThemeSwitcher.tsx` 已用 `useSyncExternalStore` 解決同型問題（主題讀取 `localStorage`/`document.documentElement`，SSR/CSR 不一致），註解明寫「避免 effect 內同步 setState」。改用同一模式：

```ts
function subscribe(): () => void { return () => {} }
function getSnapshot(): string { return AuthService.getCurrentUser()?.email ?? '' }
function getServerSnapshot(): string { return '' }
export function useCurrentUserEmail(): string {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)
}
```

`subscribe` 採真正 no-op（不比照 `themeStore` 監聽 `storage` 事件做跨頁同步）：本頁導覽列的 email 顯示沒有「同頁需即時反映登出/切換帳號」的既有需求，加上事件監聽屬超出本次缺口範圍的新增功能。

### 3.3 22 個頁面套用

三個錨點行格式一致，以 Python 腳本機械套用（而非 22 次個別 Edit）：
1. `import AuthService from '@/services/auth'` 後插入 `import { useCurrentUserEmail } from '@/hooks/useCurrentUserEmail'`
2. `const router = useRouter()` 後插入 `const userEmail = useCurrentUserEmail()`
3. `{AuthService.getCurrentUser()?.email}` → `{userEmail}`

`AuthService` import 保留（其餘頁面仍用 `AuthService.isAuthenticated()`／`AuthService.logout()`）。

## 4. 實作內容

| 檔案 | 變更 |
|---|---|
| `frontend/src/hooks/useCurrentUserEmail.ts`（新增） | `useSyncExternalStore` 包裝 `AuthService.getCurrentUser()?.email` |
| `frontend/src/app/dashboard/**/*.tsx`（22 個頁面） | 套用新 hook，取代直接渲染中讀取 |
| `frontend/e2e/at-dashboard-pricing-calendar-real.spec.ts` | 移除放寬的 15000ms timeout workaround；改用 `page.on('pageerror')` 正面斷言零錯誤；註解更新為「已修復」 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | DEF-361 狀態欄更新為已完成；版本歷史降級 v3.51 為歷史版本，新增 v3.52 |
| `docs/04_planning/RELEASE_TRACKER.md` | 新增 Sprint 269 row；一併補齊 Sprint 268 當時漏同步的統計區與版本歷史鏈（見下方第 5 節） |

## 5. 撰寫過程中新發現的落差（已於本輪一併修正，非新登記缺陷）

`RELEASE_TRACKER.md` 開工核對時發現：Sprint 268 的總覽表 row 本身已正確建立與回填（commit `4fb6c57c`／`404310cd`），但檔案底部的「📊 Release 統計」區塊與版本歷史鏈忘了同步，仍停在 Sprint 267——與既有記憶 [[deferred-items-tracker-drift]] 第 2/5/7 點記錄的「row 本身漏記」不同型，這次是 row 正確、僅統計區漏更新。已於本輪一併補上（詳見 `RELEASE_TRACKER.md` v3.91 版本歷史說明）。

## 6. 測試

依 CLAUDE.md「開發-編譯-測試循環」規則：

1. `npx tsc --noEmit`：通過，無錯誤。
2. `npx eslint <22個頁面> <新hook>`：通過，無錯誤無警告（初版因 `react-hooks/set-state-in-effect` 被擋，修正後重跑通過）。
3. `npm run build`：`BUILD SUCCESS`，無錯誤無警告。
4. `make validate-e2e`（乾淨 DB → Flyway 重建 → 全棧 host JAR + npm start，`ddl-auto=validate` → Playwright，複製雲端 e2e job）：**170 passed／4 skipped／0 failed**（6.8 分）。其中 `E2E-PRICING-CAL-01` 使用新增的 `page.on('pageerror')` 正面斷言，確認不再出現 Hydration Error；移除 15000ms 放寬後仍以預設 5000ms timeout 通過。

**驗證的侷限（誠實揭露）**：22 個頁面中僅 `/dashboard/pricing/calendar`／`rules` 兩頁有專屬的 `pageerror` 正面斷言；其餘 20 頁未逐一新增同等斷言（現有其他 E2E spec 會造訪多數頁面，但多數只斷言最終可見狀態，不會偵測已自行恢復的 hydration 錯誤）。修復本身是同一段程式碼的機械套用（22 個頁面的三個錨點行格式完全一致，已逐一 `grep` 核對），且 `tsc`／`eslint`／`npm run build` 全數通過，邏輯上足以確認修復套用正確；但「22 頁實際瀏覽器行為」僅對其中 2 頁做了正面即時驗證。

## 7. 範圍外與待使用者決定

- 本輪僅修正 DEF-361 本身（渲染中直接讀取 `localStorage` 造成的 hydration 不一致）。`DEF-324`（dashboard 無共用 layout，22 個頁面各自內嵌重複的導覽列程式碼）是另一個已登記、範圍更大的既有技術債，本輪未處理、未被本次變更擴大或縮小。
- 下一步依使用者已拍板的順序，進入 DEF-332 殘留範圍（測試固件 shadow 欄位一致性）。

## 8. Velocity 紀錄

本輪為 1 項缺口修復（根因修復＋範圍擴大至 22 頁），非新功能開發，不納入傳統 Story Point 計算。

## 9. 下一步／Action Items

1. commit＋push，等待雲端 CI 確認全綠。
2. 回填 push 狀態與雲端 CI 結果。
3. 開始 Sprint 270（DEF-332 殘留範圍）。
