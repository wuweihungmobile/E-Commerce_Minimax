# ADR-001：UI 設計系統分層——視覺決定只寫在 `components/ui`

> **狀態**：Accepted（2026-11-06，Sprint 329）
> **決策者**：使用者指示「UI 改版需要改這麼多程式，請 Architect／SA／SD／QA 全面檢視是否架構有問題、全面改善修正」；Claude（SD 角色）依實測提出，使用者的指示涵蓋採用
> **相關**：[SPRINT_329_PLAN.md](../../04_planning/SPRINT_329_PLAN.md)（量測與四角色檢視）、[UI_DESIGN_SYSTEM_CONVENTIONS.md](../../07_design/UI_DESIGN_SYSTEM_CONVENTIONS.md)（規範）、[HOMEPAGE_TEMPLATE_INTEGRATION_FRD.md](../../01_requirements/HOMEPAGE_TEMPLATE_INTEGRATION_FRD.md) §9（NFR-UI-001～006）
> **取代**：無（這是第一份 ADR；Sprint 328 的「token 重指向＋外殼＋ESLint 色盤守門」是本決策的前半，仍然有效）

## 背景

Sprint 328 把全站 UI 統一到「意象若水 Homepage 設計稿」，功能 commit 改了 **144 個檔案**（`git show --shortstat 0d69f9db`）。使用者問：只是換皮，為什麼要動這麼多程式？架構有問題嗎？

Sprint 329 的量測（HEAD＝`2d5ed426`，腳本與數字見計畫書 §2）：

| 觀察 | 數字 |
|---|---|
| 設計系統層（`components/ui`）占前端程式碼 | 11 個檔案、647 行 ≈ **2%**（其餘約 98% 的程式碼都能直接決定外觀） |
| 頁面與領域元件的 className 字串中，「同一整串原樣重複 ≥3 次」的比例 | **61%**（1,493 個中 916 個） |
| 最大宗的重複 | 頁面標題 42 檔、表單標籤 40 處、表頭儲存格 39 處、載入中 35 處、錯誤橫幅 31＋9 處 |
| 繞過 `ui/*` 直接用原生元素 | `<button>` 114、`<input>` 56、`<select>` 23、`<textarea>` 20、`<label>` 109、`<table>` 10（後兩者沒有對應元件） |
| 狀態→徽章對照表 | 16 個檔案各自定義，且互相矛盾（貼文「草稿」灰／知識庫「草稿」黃） |
| 原生 `alert()`／`confirm()` | 113／19；19 個刪除確認 E2E 零覆蓋 |
| 靜默失效的 class | `bg-black bg-opacity-50`（Tailwind v4 已移除，3 個 Modal 遮罩實測是純黑 `rgb(0,0,0)`）、`text-warning`／`border-warning`（從沒定義，6 處） |

**根因**：設計系統只有「原子」，缺一層「配方」；頁面只好用原子加一串 class 自己拼，同一個設計決定被複製到 30～80 個檔案（Parnas 的資訊洩漏）。而且**沒有任何東西阻止頁面繞過元件**（Sprint 328 的 ESLint 規則只擋「值」：色盤、陰影、hex；擋不到「配方」）。換皮要動多少檔案，取決於「有多少檔案知道這個設計決定」。

## 決策

1. **分層與責任**：值（`globals.css` 的 token）→ 原子與配方（`components/ui/*`，**唯一允許寫視覺 class 的地方**）→ 外殼（`components/layout/*`，自己的版面）→ 領域元件與頁面（**只做語意組合與版面 utility**：`flex`、`gap-*`、`w-*`、`mt-*`、響應式）。
2. **配方元件放在 `components/ui/`**（不另開 `patterns/` 目錄）：讓「允許寫視覺 class 的地方」只有一個目錄，守門的豁免範圍就是它。檔名沿用 `kebab-case`、`forwardRef`＋`cn`＋`cva`。
3. **用 lint 把它變成制度**（同一個 `design-tokens` plugin）：`no-raw-control`（原生表單／表格元素）、`no-native-dialog`、`no-removed-utility`（靜默失效的 class）、`no-ui-recipe`（「已有元件的配方」不得手刻）。**新增元件時，把它取代的配方登記進 `no-ui-recipe` 的 `RECIPES`**——「已有元件的配方不能再手刻」因此是制度，不是慣例。豁免：`components/ui`、`components/layout`、`components/theme`；領域專用控制項（月曆日期格、星等、設計稿 DS 元件 SortToolbar／SidebarNav、對話列）以逐行 `eslint-disable … -- <理由>` 明列。
4. **`NativeSelect` 與 Radix `Select` 並存**：23 個原生 `<select>` 改成與 `<select>` API 完全相容的 `NativeSelect`（不是全部改 Radix）。理由：E2E 與既有程式的 `value`／`onChange(e)` 不變、保留瀏覽器原生下拉（密集表單、行動裝置）、零邏輯風險。欄位規格抽成 `field-styles.ts`，Input／Textarea／NativeSelect／SelectTrigger 共用單一字串。
5. **回饋與覆蓋層用「命令式單例＋一個 Host」**：`toast.success/error/info()`、`await confirmDialog()`，Host（`<Toaster />`、`<ConfirmDialogHost />`）掛在根 layout 一次。呼叫習慣與 `alert()`／`confirm()` 一致，遷移是機械式的；不需要 Context 或 hook，任何函式（事件處理器、`catch`）都能呼叫。Modal 改用 Radix Dialog（焦點鎖定、ESC、`aria-modal`、半透明遮罩）。
6. **狀態語意色集中登錄**：`lib/status.ts` 的登錄表（中文標籤＋Badge variant）＋ `<StatusBadge domain status>`；語意規則（草稿＝outline、待審＝warning、啟用／已發布＝success、停用／封存＝secondary、駁回／取消／停權＝destructive）寫在檔頭並由 E2E 遍歷驗證。既有的 `services/*` 狀態（訂單、訂房、退貨…）維持原樣。
7. **契約用「實際 computed style」測，不測 class 名**：`at-ui-design-system.spec.ts`（欄位幾何、表格、標題、Toast、ConfirmDialog、Dialog 遮罩、換皮演練、狀態登錄表）。E2E 對選取態改斷言 `aria-pressed`、對刪除線改斷言 `text-decoration-line`。

## 考慮的選項

| 選項 | 優點 | 缺點 | 結論 |
|---|---|---|---|
| A. 只靠文件與 code review | 零成本 | Sprint 328 之前就有文件（設計稿規範、FRD），61% 重複仍然長出來——人工紀律擋不住複製貼上 | 否決 |
| B. 換整套成熟元件庫（MUI、Ant Design…） | 元件齊全 | 與設計稿「嚴禁陰影、rs-* token、5 套主題」衝突，要重做主題；遷移面積比本方案大一個量級；仍擋不住頁面繞過 | 否決 |
| C. 另開 `components/patterns/` 放配方 | 概念上「原子／配方」分開 | 允許寫視覺 class 的地方變成兩個目錄，lint 豁免與規範都更複雜；目前配方只有十來個，不需要再分層 | 否決（日後配方超過約 30 個再議） |
| D. CSS 層做元件類（`@apply .page-title`） | 不改 JSX | 把決定藏進 CSS，E2E 與無障礙語意（`<h1>`、`<table>`、`role`）仍要頁面自己寫對；Tailwind v4 對 `@apply` 的建議也是少用 | 否決 |
| **E. 配方進 `components/ui`＋lint 守門＋契約測試（本決策）** | 換皮只動 token 與 ui；守門讓它長不回來；可量化驗證（換皮演練） | 一次性遷移面積大（本輪約 250 個檔案）；新增 4 條自訂規則要維護 | **採用** |

## 後果

- **正面**：①下一次換皮（改圓角、輸入框高度、表頭、頁面標題字級、橫幅樣式）只動 `globals.css` 與 `components/ui/*`；②一致性由元件保證，不靠人記得；③回饋層可主題化、可被 E2E 觀察，破壞性確認流程第一次有測試；④死 class（靜默失效）由 lint 在寫的當下擋下。
- **負面**：①本輪是「還債」，遷移面積比 Sprint 328 更大（約 250 個檔案）；②`ui/` 變大後需要自己的紀律（元件 API 要克制，避免變成 prop 森林）；③4 條自訂規則的誤報要靠逐行 `eslint-disable` 處理，豁免清單要定期檢視。
- **風險與緩解**：①規則過嚴逼出「繞路寫法」（例如用 `<div role="button">` 避開 `<button>`）→ 規範 §9 明說原因，並以 code review 與契約測試（computed style）兜底；②`ui/` 元件規格改壞會一次影響全站 → 這正是契約測試與換皮演練要抓的；③`Toast`／`ConfirmDialog` 的命令式單例在 SSR 沒有 Host 時不會顯示 → 只在事件處理器裡呼叫（已由 lint 之外的規範約定），Host 掛在根 layout。

## 驗證

見 [SPRINT_329_PLAN.md](../../04_planning/SPRINT_329_PLAN.md) §7 結果：掃描前後對照、守門突變、契約規格突變、換皮演練（檔案數與全站控制項「跟著變」的比例）、完整本地 E2E 守門。
