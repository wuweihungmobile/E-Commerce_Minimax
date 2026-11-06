# Sprint 329 Plan — UI 架構分層：把「樣式配方」收進設計系統，讓下一次換皮只動少數檔案

## 1. 起點與範圍

### 1.1 起點

[Sprint 328](SPRINT_328_PLAN.md) 收尾時，使用者把總結貼回來並說「請繼續任務！」，**沒有 `==>` 回覆**，並附上一題明確指示：

> 「UI 改版需要改這麼多程式，請 Architect / SA / SD / QA 進行全面檢視是否架構有問題？請全面改善修正！」

- Sprint 328 的功能 commit `0d69f9db` 改了 **144 個檔案**（`git show --shortstat`：2459 增、2366 刪；`dashboard` 50、`(auth)` 28、`admin` 10、`ui` 9、`layout` 5、`cms` 5、其餘 `components/*` 約 25、E2E 4、文件 5）。使用者問的就是：**只是換皮，為什麼要動這麼多？這代表架構有問題嗎？**
- 上一輪總結的「下一步」是 `DEF-453` 補量測，**本輪不做**（順延）；總結「仍等你決定」的項目（`DEF-294`、`DEF-449`、`DEF-419`…）不在授權範圍內，不碰。
- 長期授權（2026-10-29）：本輪 commit 後直接 push，連同 Sprint 328 留在本機的純文件回填 commit `2d5ed426` 一起帶出。

### 1.2 我對指示的解讀（可一句話推翻）

1. **「全面檢視是否架構有問題」＝ 給「有／沒有」的答案，而且要有證據**：用工具量測，不憑印象；四個角色（Architect／SA／SD／QA）各自從自己的角度檢視（§3）。
2. **「全面改善修正」＝ 修掉造成「換皮要動很多檔」的結構問題，加上檢視過程中發現、與 UI 層直接相關的缺陷**（原生 `alert()`／`confirm()`、死 class、E2E 對 class 名的耦合）。**不含**與 UI 樣式無關的架構議題（頁面資料載入樣板、繞過 `services/` 層直接用 axios、Server Component 採用率、前端零單元測試）——這些量測了、登記了，不在本輪修（§9）。
3. 檢視與修正之間不停下來等確認：使用者的指示已經包含兩者（沿用 `inline-decision-replies-mean-proceed` 的做法）。

### 1.3 成功標準（開工時定義）

| # | 標準 | 怎麼量 |
|---|---|---|
| S1 | **回答問題，答案要有證據**：根因是什麼、為什麼 Sprint 328 要動 144 檔 | §2、§3 的量測表 |
| S2 | **視覺規格只有單一來源**：`components/ui/*` 之外，不再有原生 `<button>`／`<input>`／`<select>`／`<textarea>`／`<label>`／`<table>` 系列元素 | ESLint 規則守門（出現即紅）；掃描腳本前後對照 |
| S3 | **重複的樣式配方歸零**（頁面標題、表單欄位、表格、載入／空狀態、錯誤橫幅、卡片、狀態徽章、分頁、選取態晶片、Modal） | `scan_recipes.py` 前後對照；ESLint「配方→元件」規則 |
| S4 | **原生 `alert()`／`confirm()` 歸零**，換成可主題化、可被 E2E 觀察的 Toast／ConfirmDialog；手刻 Modal 改用 Radix Dialog | grep＋ESLint；新 E2E |
| S5 | **死 class 歸零**（原始碼寫了、建置出來的 CSS 卻沒有規則的 class） | `scan_dead_classes.py`（原始碼 token ↔ `.next` 實際 CSS） |
| S6 | **E2E 不再斷言 class 名**，並新增「設計系統契約」規格 | `toHaveClass(<視覺 class>)`＝0；新規格含突變驗證 |
| S7 | **換皮演練（rehearsal）**：對 `components/ui/*` 做一次合成的「重新設計」，量測需要動的檔案數，以及全站有多少比例的控制項真的跟著變 | 演練結果寫進 §7；基線（現況）一併量 |
| S8 | **不退步**：`type-check`＝0、`lint`＝0、`next build` 成功、`make validate-e2e` 全數通過；視覺探針 0 崩潰／0 個 5xx，並用眼睛看截圖 | 依序執行 |
| S9 | 範圍不擴張：**不改後端、不改 API、不新增功能、不重做頁面資訊架構** | `git status` 無後端變更 |

## 2. 動手前用工具核對的事實

（腳本都在本輪 scratchpad，**只讀**；數字以工具輸出為準，不是估算。HEAD＝`2d5ed426`，`type-check`＝0、`lint`＝0 為基線。）

### 2.1 規模與分層

| 項目 | 結果 |
|---|---|
| 前端規模 | `src/` 218 個檔案（170 `.tsx`＋48 `.ts`）、32,236 行；`app/` 104 個 `page.tsx` |
| 設計系統層（`components/ui/`） | **11 個檔案、647 行（約 2%）**：alert、authenticated-image、badge、button、card、input、label、pagination、select、skeleton、switch |
| 其餘 | 頁面（`app/`）與領域元件（`components/<領域>/`）合計約 2.6 萬行，**所有重複出現的外觀都寫在這裡** |
| `ui/*` 的採用 | 有 `import` 任何 `ui/*` 的檔案 **77／159**；`page.tsx` **51／104**（一半的頁面完全沒用設計系統元件） |

### 2.2 原生元素繞過設計系統（`components/ui` 之外）

| 元素 | 原生（處／檔） | 對應 `ui` 元件（處／檔） | 後果 |
|---|---|---|---|
| `<button>` | 114／37 | `<Button>` 253／62 | 31% 的按鈕不吃 40px／radius-md／disabled 色 |
| `<input>` | 56／25（含 checkbox 5、radio 2、file 2） | `<Input>` 113／35 | 33% 不吃 44px 規格 |
| `<select>` | 23／15 | `<Select>`（Radix）10／9 | 70% 不吃規格 |
| `<textarea>` | 20／20 | **無元件** | 100% 不吃規格 |
| `<label>` | 109／30 | `<Label>` 90／26 | 55% 不吃規格 |
| `<table>`（`thead/tbody/tr/th/td` 共 167 個） | 10／10 | **無元件** | 100% 手刻 |

**Sprint 328 的「統一」只對「走 `ui/*`」的控制項成立**。當時的判斷依據是「`<Input>` 113 個、0 個覆寫高度 → 可統一成 44px」（S328 §1.3），只量了走元件的那一邊，沒量繞過元件的 56＋90 處；S328 §9「限制」也沒有揭露這件事，這裡更正。

**真實瀏覽器實測**（Sprint 329 的探針：HEAD 的建置、81 條路由、匿名／買家／店主／管理員四種身分、藍色主題）：

- **文字輸入框 120 個**：110 個（91%）高度 ≥44（多數手刻輸入框 `py-2` 加上繼承的行高，碰巧也約 44px），10 個（8%）是 34／38px；差異主要在圓角（114 個 8px、6 個 12px）與字級（110 個 16px、10 個 14px）。**開工時我推論的「手刻輸入框約 34px」只有那 10 個成立，不是多數**——這個推論在量測之後被修正。
- **原生 `<select>` 17 個**（排除 Radix 內部 1px 高的隱藏 select）：**只有 1 個達 44px**（33px 9 個、40px 7 個、46px 1 個），9 個用 14px 字。
- **`<textarea>` 10 個**：左內距**全是 12px**（規格 16px）、2 個圓角 12px、3 個 14px 字。
- **按鈕 209 個**：65 個（31%）高度不在 32／40／48，102 個（49%）字重不是 500，13 個圓角 0px（表格列內的文字連結式按鈕）。
- **表頭 `th` 46 個**：內距 12×16 只有 29 個（63%），其餘 (8,12)、(8,16) 等。
- **Modal 遮罩 2 個**實測 `rgb(0, 0, 0)`＝**不透明純黑**（§2.4 的推論被真實瀏覽器證實；截圖上整頁背景是黑的）。

### 2.3 樣式複製貼上（`scan_dup.py`、`scan_recipes.py`）

- 非 `ui` 目錄共有 **1,493 個** 靜態 className 字串（≥3 個 token），其中 **916 個（61%）是「同一整串在 ≥3 處原樣重複」**；不同的整串 573 種，其中 121 種重複 ≥3 次。
- 最大宗（整串完全相同）：

| 整串 className | 次數／檔數 | 它其實是… |
|---|---|---|
| `text-2xl font-semibold text-rs-ink` | 42／42 | 頁面標題 |
| `block text-sm font-medium text-rs-ink mb-1` | 40／10 | 表單欄位標籤 |
| `px-4 py-3 text-left text-xs font-medium text-rs-ink-muted uppercase`（另有 `text-right` 版 9／8） | 39／8 | 表格表頭儲存格 |
| `flex items-center justify-center h-64` | 35／35 | 載入中 |
| `bg-rs-error/10 border border-rs-error/30 text-rs-error px-4 py-3 rounded-md`（另一寫法 `bg-error/10 border border-error/20…` 9／8） | 31／27 | 錯誤橫幅（**`Alert variant="destructive"` 明明存在**） |
| `mt-1 text-sm text-rs-ink-muted` | 27／27 | 頁面副標 |
| `px-3 py-1 text-sm border rounded disabled:opacity-50` | 16／4 | 手刻分頁鈕（`Pagination` 與 `Button` 都存在） |
| `border rounded px-3 py-1.5 text-sm w-full`／`…` | 11／2、11／5 | 手刻輸入框／下拉（約 34px 高） |

- 「配方外洩」（單一字串同時含全部特徵，非 `ui` 目錄）：頁面標題 88 處／81 檔、手刻輸入框 90／36、錯誤橫幅 56／47、置中載入／空狀態 103／66、表格表頭 53／9、手刻卡片 44／30、手刻按鈕 36／27、分頁手刻 26／12、選取態以 class 表達 17／8、徽章／晶片手刻 10／8。
- **狀態→徽章對照表散落**：`getStatusBadge` 在 **16 個檔案各自定義**；同一個概念在不同頁面**語意相反**——
  - `dashboard/posts/page.tsx`：草稿＝灰、已發布＝綠、已歸檔＝黃
  - `dashboard/knowledge/page.tsx`：已發布＝綠、**草稿＝黃、已封存＝灰**
  （另有 `cms/page.tsx`、`cms/posts/[id]/edit/page.tsx`；有的用 `<Badge>`，有的自己寫 `<span className="px-2 py-1 rounded-full text-xs …">`。）
- `CardContent` 上 `flex flex-col items-center justify-center h-48／py-16／py-12` 共 35 處＝空狀態（其中 18 個帶一個動作按鈕／連結）。

### 2.4 缺陷：用「建置出來的 CSS」對照原始碼找到的死 class（`scan_dead_classes.py`）

原始碼裡 484 種 utility 形 token，**15 種在實際建置的 CSS 裡沒有規則**；排除散文字串與 testid 的誤報後，真正的缺陷有三類：

| token | 位置 | 後果 |
|---|---|---|
| `bg-black bg-opacity-50` | `dashboard/posts/categories`、`dashboard/notifications`（2 處） | Tailwind v4 **移除了** `bg-opacity-*`，所以這三個 modal 的遮罩是**純黑不透明**（待用真實瀏覽器實測，§6 V6） |
| `text-warning`、`border-warning` | `admin/purchase-orders`、`admin/settlements/reversal`、`admin/tenants`（共 6 處） | shadcn 的 `warning` token **從沒定義過**；「待審批」的醒目標示從來沒生效。即使定義了，`rs-warning` 是亮黃，也不能當文字色 |

（和 S328 發現的 `Alert destructive` 從沒上色是同一類缺陷：**沒有任何機制檢查「寫出來的 class 有沒有對應的規則」**。）

### 2.5 回饋與覆蓋層（`alert`／`confirm`／Modal）

| 項目 | 結果 |
|---|---|
| 原生 `alert()` | 正則掃描 **114 處／29 檔**（含至少 1 處是註解裡的字樣）；其中約 73 處在 `catch` 內＝錯誤通知、約 41 處在 `catch` 外＝成功訊息或驗證提示（**這兩個是粗略括號計數，不是 AST；精確數字在轉換時用 AST 重數，結果補在 §10**） |
| 原生 `confirm()` | **19 處／19 檔**，全是「刪除／取消」確認 |
| 手刻 Modal（`fixed inset-0`） | 5 處／4 檔（無焦點鎖定、無 ESC、無 `aria-modal`） |
| `@radix-ui/react-dialog`、`react-alert-dialog` | **已安裝，但沒有任何檔案 import**（死依賴） |
| E2E 對原生對話框的處理 | 只有 `at-m15-e2e` 用：全域 `dialog.dismiss()`（註解記載：原生 `alert()` 曾在 teardown 造成 Playwright session 崩潰，S37 AI-2001）＋把 `alert` 訊息當**同步訊號**（斷言 `貼文已發布`、`貼文已儲存`） |
| 19 個 `confirm()` 的 E2E 覆蓋 | **0**：沒有任何 E2E 走到這些頁面的刪除鈕。Playwright 預設會自動關閉對話框（`confirm` 回 `false`），所以就算走到也只會「取消」 |

### 2.6 與 UI 樣式無關、但檢視時量到的架構現況（只登記，§9）

- 16 個 page／component 直接 `import axios` 或 `@/lib/api*`，繞過 `services/` 層。
- 104 頁中 64 頁有 loading state、49 頁有 `setError`、85 頁有 `useEffect`，**三者齊備 41 頁**（沒有共用的資料載入 hook）。
- `page.tsx` 有 **93／104 標 `'use client'`**。
- `package.json` 的 `test` 是 `echo "No unit tests configured"`；前端沒有單元／元件測試。
- 29 個 `page.tsx` 沒有 `<h1>`（部分是把標題放在領域元件裡，沒逐一確認）。

### 2.7 E2E 對實作細節的耦合（`frontend/e2e`，56 支規格、9,047 行）

- `toHaveClass`：9 處（`bg-rs-tertiary` ×4 ＝「選取態」，`line-through` ×5 ＝「原價刪除線」）；`.first()` 64 處；`getByTestId` 293 處；`getByRole` 130 次（`button` 56、`link` 38、`heading` 22…；依 role 加總）；**沒有任何 `getByRole('alert'|'status'|'dialog')`**（所以新元件加這些 role 不會造成 S328 吃過的「同 role 數量」耦合）。
- `selectOption` 只用在主題切換器（2 處），**內容頁的原生 `<select>` 沒有被 E2E 操作**。

## 3. 四角色檢視

### 3.1 Architect — 結構與責任

**結論：有問題，而且是結構性的。**

| # | 發現 | 證據 |
|---|---|---|
| A1 | **缺一層。** 設計系統只有「原子」（11 個 shadcn 元件），沒有「配方」層（頁面標題、欄位、表格、載入／空／錯誤狀態、狀態徽章、確認框、通知）。頁面只好用原子加一串 class 自己拼，同一個配方被複製到 30～80 個檔案 | §2.3：61% 的 className 是原樣重複 |
| A2 | **資訊洩漏（Parnas）**：「頁面標題長什麼樣」這個設計決定，被 81 個檔案各自知道一次。決定一變，81 處同時要改——**這就是「換皮要動這麼多檔」的機制** | 88 處標題配方／81 檔 |
| A3 | **已有的原子被繞過，而且沒有任何東西阻止**：沒有 lint 檢查原生元素，所以 31%～100% 的控制項走自己的樣式 | §2.2 |
| A4 | **守門只管「值」不管「配方」**：S328 的 ESLint 規則禁止色盤／陰影／hex（值），但頁面照樣可以手刻一個 `border rounded px-3 py-1.5` 輸入框——顏色全是 token、卻不是設計稿規格 | S328 的規則涵蓋不到 §2.2 |
| A5 | **外殼層（S328 已修）**：28 頁各自組頂欄是同一個問題的另一個實例（配方沒有收進元件），S328 用 `SellerShell` 解決了那一個，**沒有把同樣的原則套到原子以上、外殼以下的那一層** | S328 §1.3：29 頁自組頂欄 |
| A6 | **回饋／覆蓋層沒有架構**：沒有 Toast、沒有確認框、沒有 Dialog；各頁用 `alert()`、`confirm()`、手刻 `fixed inset-0`。Radix dialog 套件裝了卻沒人用 | §2.5 |
| A7 | **狀態語意沒有單一來源**：16 份 `getStatusBadge`，且彼此矛盾（草稿／封存的顏色互換） | §2.3 |
| A8 | **`ui/` 內部也有重複**：Input、SelectTrigger 各自複製同一串「欄位」class；若再加 Textarea／NativeSelect 就是第三、四份 | `input.tsx`、`select.tsx` |

**目標架構（§5.1）**：值（token）→ 原子與配方（`components/ui/*`，**唯一**允許寫視覺 class 的地方）→ 外殼（`components/layout/*`）→ 頁面（只做語意組合與版面 utility）。

### 3.2 SA — 需求與流程

| # | 發現 | 證據／處置 |
|---|---|---|
| SA1 | **FRD 只定義了「外殼套用範圍」，從沒定義「可換膚性」與「一致性」這類非功能需求。** 因此沒有驗收準則，也就沒有人會發現「配方散落」是缺陷 | `HOMEPAGE_TEMPLATE_INTEGRATION_FRD.md` 只有 §1 決策、§2 四區塊、§3 DS 元件、§4 主題、§7 路線圖；沒有 NFR 章節。→ 本輪補 NFR-UI-001～006（FRD §9） |
| SA2 | **需求在 Sprint 間無聲掉落**：行動項 `AI-1904`（階段三，dashboard／admin／cms 套 Shell）在 `SPRINT_35_RETRO.md` 排「Sprint 37」後沒有任何計畫書接手（S328 已查證，精確 grep 只有 1 處）。**這次的 144 個檔案，有一部分就是遲到的還債** | S328 §1.3 |
| SA3 | **「完成」的定義沒有包含「設計系統符合度」**：功能頁面通過 E2E 就算完成，不會問「有沒有手刻一個輸入框」。→ 補 DoD（`UI_DESIGN_SYSTEM_CONVENTIONS.md` §9） |
| SA4 | **對使用者的承諾說得比事實多**：S328 對使用者說「`ui/*` 基礎元件改成設計稿規格（44px 輸入）」＝「統一」，事實是只對走元件的那部分成立（§2.2）。**需求溝通上，「範圍邊界」要用量測值說，不用形容詞** | §2.2 更正 |
| SA5 | **業務可見的不一致**：同一種「草稿」狀態，在貼文管理是灰色、在知識庫是黃色。使用者（店主）會看到。→ 在狀態登錄表裡定義語意色（§5.2），而不是各頁自選 | §2.3 |
| SA6 | **使用者角色的操作安全**：19 個刪除確認是「破壞性操作的唯一安全網」，卻是不可主題化、不可測試、E2E 零覆蓋的原生 `confirm()`。屬於「資料安全」類 NFR | §2.5 |

### 3.3 SD — 設計

| # | 發現 | 處置 |
|---|---|---|
| SD1 | 沒有元件契約文件：哪些元件存在、各自的 API、何時用哪個——只有 token 與外殼的規範（`UI_DESIGN_SYSTEM_CONVENTIONS.md` §2～§5） | 規範補 §4 元件目錄、§9 DoD；新增 ADR-001 記錄「配方進 `ui/`」的決策 |
| SD2 | 欄位規格（`rs-input`）寫在 Input 與 SelectTrigger 兩處，無單一來源 | 抽 `field-styles.ts`，Input／Textarea／NativeSelect／SelectTrigger 共用 |
| SD3 | 沒有狀態登錄表：標籤（中文）與語意色分散在 16 個檔案 | `lib/status.ts`＋`<StatusBadge domain status>`；既有的 `services/*` 領域（訂單、訂房、退貨、成員）維持原樣不動 |
| SD4 | 沒有「選取態」元件：分頁籤、篩選鈕、設施晶片用條件式 class 表達狀態，E2E 只好斷言 class 名 | `Chip`（`aria-pressed`）；E2E 改斷言 `aria-pressed` |
| SD5 | 沒有 overlay 規範：z-index 有 `z-50`／`z-[60]`／`z-[70]`，遮罩有的 `bg-black/50` 有的壞掉 | Dialog／ConfirmDialog／Toast 以 Radix 實作，層次集中在 `ui/` |
| SD6 | 嚴格 CSP（`style-src 'self' 'unsafe-inline'`）已允許 Radix 的 inline style；Provider 依 Next 16 文件的 Context providers 做法（`'use client'` 元件包 `{children}`、在 Server 的 layout 引入） | 已查證（`node_modules/next/dist/docs/01-app/01-getting-started/05-server-and-client-components.md`） |

### 3.4 QA — 品質守門

| # | 發現 | 證據／處置 |
|---|---|---|
| Q1 | **lint 守門有缺口**：只擋色盤／陰影／hex。擋不到原生控制項、手刻配方、原生對話框、Tailwind v3 已移除的 utility | §2.2、§2.4；新增規則（§5.4） |
| Q2 | **沒有任何機制檢查「寫出來的 class 有沒有生效」**：`bg-opacity-50`（v4 移除）、`text-warning`（未定義）都靜靜地壞著；S328 的 `Alert destructive` 是同一類 | §2.4；規則＋`scan_dead_classes.py` 的做法納入驗證 |
| Q3 | **E2E 斷言實作 class 名**：`toHaveClass(/bg-rs-tertiary/)`（選取態）與 `/line-through/`（原價）。換皮時這些會紅（S328 因此改了 4 支既有 E2E） | 選取態改 `aria-pressed`；刪除線改 `toHaveCSS('text-decoration-line', …)` |
| Q4 | **原生 `alert()` 當同步訊號＋曾造成 session 崩潰**：`at-m15-e2e` 靠 `waitForEvent('dialog')`，且需要全域 `dismiss` 才不會 flaky（S37 AI-2001）。**症狀被繞過，原因（原生對話框）沒移除** | 換成 Toast：E2E 等 `data-testid="toast"` |
| Q5 | **破壞性確認流程 E2E 零覆蓋**：19 個 `confirm()` 沒有任何測試證明「取消不會送 DELETE、確定才送」 | 新增 E2E：取消不送、確定才送（§5.5） |
| Q6 | **沒有設計系統契約測試**：目前只有 `at-ui-design-base`（外殼與主題色，7 案例）。控制項幾何（高度、圓角、框色、無陰影）沒有任何測試，所以輸入框 34px／44px 的落差沒人發現 | 新增 `at-ui-design-system.spec.ts`（量實際 computed style） |
| Q7 | **「統一」沒有量化的採用度指標**：S328 總結只說 class 歸零，沒有「控制項有多少比例走元件」。**這輪把指標做成可重跑的腳本與 lint** | `scan_adoption.py`／ESLint |
| Q8 | 前端沒有單元／元件測試（`"test": "echo No unit tests configured"`） | 不在本輪（§9）；契約改以 E2E 的 computed style 與 lint 守 |

### 3.5 根因總結：為什麼 Sprint 328 要動 144 個檔案

```
         （Sprint 328 之前）                                  （目標）
  頁面 ── 直接寫顏色 class（1657 處）                  頁面 ── 只寫「是什麼」
   │      直接寫標題／欄位／表格／狀態／橫幅配方              （<PageTitle>、<Field>、<Table>、
   │      直接用原生 <input>／<select>／<textarea>            <StatusBadge>、<EmptyState>…）
   │      自己組頂欄（28 頁）                                  │
   ▼                                                           ▼
  換皮 = 每個頁面都要改                                components/ui（唯一寫視覺 class 的地方）
                                                              │
                                                              ▼
                                                        globals.css（token）
```

S328 把「值」（顏色、陰影）與「外殼」（頂欄）收回單一來源，**但配方層沒有動**——所以頁面仍然是「知道太多」的狀態，下一次換皮（改圓角、改輸入框高度、改表格表頭、改頁面標題字級…）還會再來一次。

## 4. 回答使用者的問題

**有問題，但不是「程式寫得亂」，而是「少了一層、又沒有守門」：**

1. 144 個檔案裡，絕大多數是**同一個設計決定被複製了很多次**（61% 的 className 是原樣重複；頁面標題配方 81 個檔案各寫一次）。
2. 設計系統只有 11 個原子元件（約 2% 的程式碼），其餘 98% 的程式碼都能直接決定外觀，而且**沒有任何東西阻止它們這樣做**。
3. S328 解決了「值」與「外殼」，沒有解決「配方」；本輪補上，並用 lint 讓它**無法再長回來**。
4. 我必須更正 S328 的說法：「輸入框 44px」只對走 `<Input>` 的那些成立；手刻與原生的 56＋90＋23＋20 處沒有（§2.2）。

## 5. 方案

### 5.1 目標架構與責任

| 層 | 位置 | 可以寫視覺 class 嗎 | 守門 |
|---|---|---|---|
| 值（token） | `app/globals.css` | 是（唯一定義顏色／圓角／字族的地方） | `no-raw-palette` |
| 原子＋配方 | `components/ui/*` | **是（唯一允許）** | — |
| 外殼 | `components/layout/*` | 是（外殼自己的版面） | `no-raw-palette` |
| 領域元件 | `components/<領域>/*` | **否**：只做語意組合與版面 utility（`flex`、`gap-*`、`w-*`、`mt-*`、響應式） | 新增三條規則 |
| 頁面 | `app/**` | **否**：同上 | 同上 |

### 5.2 新增元件（全部放 `components/ui/`，kebab-case 檔名，沿用既有 `forwardRef`＋`cn`＋`cva` 慣例）

| 元件 | 取代 | 備註 |
|---|---|---|
| `field-styles.ts`（共用常數） | Input／SelectTrigger 各自一串 | 欄位規格單一來源 |
| `Textarea` | 20 個原生 `<textarea>` | 同 `rs-input` 規格，最小高 80 |
| `NativeSelect` | 23 個原生 `<select>`（**不是**改成 Radix `Select`：E2E 與 API 相容；原生下拉對密集表單與行動裝置較合適） | 與 Input 同高同框 |
| `Checkbox`、`Radio` | 7 個原生 checkbox／radio | `accent-rs-primary` |
| `Table` 系列（`Table`、`TableHeader`、`TableBody`、`TableRow`、`TableHead`、`TableCell`） | 10 張手刻表格 | `Table` 含外框與橫向捲動容器 |
| `PageTitle`、`PageDescription` | 42＋27 處標題／副標配方 | 1:1 取代元素，不重組頁面版面 |
| `LoadingState`、`EmptyState` | 41 處「載入中…」、35＋7 處空狀態 | `role="status"`；沿用原文字「載入中...」 |
| `Chip` | 13＋17 處「選取態以 class 表達」的按鈕 | `aria-pressed` |
| `StatusBadge`＋`lib/status.ts` | 16 份 `getStatusBadge` | 登錄表集中「中文標籤＋語意色」 |
| `Dialog`（Radix） | 5 處手刻 Modal | 遮罩 `bg-black/50`（修掉純黑遮罩） |
| `ConfirmDialog`（Radix AlertDialog）＋`confirmDialog()` | 19 個 `confirm()` | 命令式 API，回傳 `Promise<boolean>` |
| `Toast`（`toast.success/error/info()`＋`<Toaster/>`） | 114 個 `alert()` | 命令式 API；成功／資訊 `role="status"`，錯誤 `role="alert"`；`data-testid="toast"` |

既有元件的調整：`Button` 不改 API（`variant`／`size` 已涵蓋所有手刻按鈕）；`Link` 當按鈕用 `buttonVariants()`；錯誤橫幅直接用既有的 `Alert variant="destructive"`；卡片用既有的 `Card`；分頁鈕用 `Button size="sm" variant="outline"`。

**狀態語意色（登錄表採用的規則，跨領域一致）**：草稿＝`outline`（中性、未完成）；待審／審批中／部分到貨＝`warning`；啟用／已發布／已核准／已到貨＝`success`；停用／已封存／已下架＝`secondary`；駁回／取消／停權／終止＝`destructive`；其餘＝`outline`。標籤文字**逐項沿用現有頁面的中文**（E2E 與使用者都看得到），只統一顏色。

### 5.3 遷移策略（腳本輔助＋逐檔審查）

沿用 S328 的做法：**TypeScript AST（`createRequire` 載 `frontend/node_modules/typescript`）**，都先乾跑、冪等，動手前後各跑一次掃描對照。每種轉換一支腳本、一個類別一個「編譯→測試」循環（`type-check`＋`lint`；階段性 `next build`）。

- 屬性搬移原則：**保留所有非 className 屬性**（`data-testid`、`id`、`name`、`value`、`onChange`、`key`、`aria-*`…）；className 只移除「已由元件提供的視覺 class」，**保留版面 class**（`w-*`、`flex-1`、`mt-*`、`col-span-*`、`text-right` 等）；無法判定的 token 保留並在乾跑報告列出頻率，由人工決定（不靜默丟棄）。
- 動態 className（template literal、`cn()`、條件式）**不自動轉**，列清單人工處理。
- 順序（依槓桿與風險，由低到高）：①死 class 修正 → ②`Label` → ③`Input`／`Textarea`／`NativeSelect`／`Checkbox`／`Radio` → ④`Table` → ⑤`PageTitle`／`PageDescription` → ⑥`LoadingState`／`EmptyState`／`Alert` 橫幅 → ⑦`Card` 配方 → ⑧`Button`／`Chip`／`buttonVariants` → ⑨`StatusBadge` → ⑩Dialog／ConfirmDialog／Toast。

### 5.4 守門規則（`frontend/eslint-rules/design-tokens.mjs`，與既有 `no-raw-palette` 同一個 plugin）

| 規則 | 禁止 | 訊息 |
|---|---|---|
| `design-tokens/no-raw-control`（`components/ui/**` 豁免） | 原生 `<button>`、`<input>`、`<select>`、`<textarea>`、`<label>`、`<table>`、`<thead>`、`<tbody>`、`<tfoot>`、`<tr>`、`<th>`、`<td>` | 指出該用哪個元件 |
| `design-tokens/no-native-dialog` | `alert()`、`confirm()`、`prompt()`、`window.*` 同名呼叫 | 指向 `toast`／`confirmDialog` |
| `design-tokens/no-removed-utility` | Tailwind v3 已移除的 `*-opacity-*`；專案未定義的語意色 `destructive`／`warning`／`info`（`bg-`／`text-`／`border-`…） | 指出替代寫法（例如 `bg-black/50`、`text-rs-ink`＋`border-rs-warning`） |
| `design-tokens/no-ui-recipe`（`components/ui/**` 豁免；**施工後補強：另加「徽章／胶囊」配方 `text-xs`＋小內距＋圓角，並放寬橫幅配方**） | 「已經有元件的配方」被手刻：`<h1>` 的 `text-2xl font-semibold`；`bg-rs-error/10`＋`text-rs-error`＋`border`（錯誤橫幅）；`flex items-center justify-center h-64`（載入）；`text-center py-12 text-rs-ink-muted`（空狀態）；`fixed inset-0`（Modal） | 指出該用哪個元件。**新增元件時，把它取代的配方加進這張表**（讓「已有元件的配方不能再手刻」成為制度） |

唯一預期的例外：`ThemeSwitcher` 的原生 `<select>`（頂欄上無框、透明、繼承顏色的特殊樣式，E2E 也用 `header select` 定位）→ 該行 `eslint-disable-next-line` 並寫明理由。

### 5.5 E2E 與測試

1. **改既有規格**（不弱化斷言）：`at-m15-e2e`（`waitForEvent('dialog')`＋訊息 → 等 `toast`；選取態 `toHaveClass(/bg-rs-tertiary/)` → `aria-pressed="true"`）、`at-room-booking`／`at-m11-cart-checkout`（`/line-through/` → `toHaveCSS('text-decoration-line', 'line-through')`）。
2. **新規格 `at-ui-design-system.spec.ts`**（不依賴後端 seed；同 `at-ui-design-base` 用 admin 登入＋`page.route` 模擬資料）：
   - 控制項幾何契約：代表頁上所有 `input`／`select`／`textarea` 的實際 `min-height ≥ 44`、`border-radius = 8px`、`box-shadow = none`、框色＝token；按鈕高度 ∈ {32,40,48}；表頭字級／內距；頁面標題字級／字重。
   - Toast：成功／錯誤訊息出現、自動消失、`role` 正確。
   - ConfirmDialog：**取消不送 DELETE、確定才送**；ESC 關閉＝取消。
   - Dialog：遮罩實際為半透明（`background-color` 的 alpha＜1），不是純黑。
   - **突變驗證（預先登記，§6）**：證明這支規格抓得到「元件被繞過」與「元件規格被改壞」。
3. **既有 `at-ui-design-base`（7 案例）不改**。

## 6. 驗證計畫（預先登記）

| # | 項目 | 預期 |
|---|---|---|
| V0 | 基線：`type-check`＝0、`lint`＝0；掃描腳本的「之前」數字（§2） | ✅ 已量 |
| V1 | 每個轉換步驟後立即 `type-check`＋`lint`（開發-編譯-測試循環）；階段性 `next build` | 0／0／成功 |
| V2 | 乾跑報告的「未處理清單」（動態 className、無法判定的 token）逐一人工讀完 | 每一筆有處置 |
| V3 | 掃描前後對照：原生元素（§2.2）、配方外洩（§2.3）、死 class（§2.4）、`alert`／`confirm` | 原生元素 0（`ThemeSwitcher` 例外 1）；配方外洩 0；死 class 0；`alert`／`confirm` 0 |
| V4 | **守門突變**：在暫存檔放進每條規則各數個違規（原生 `<button>`／`<table>`、`alert()`、`bg-opacity-50`、`text-warning`、`<h1 className="text-2xl font-semibold">`、手刻錯誤橫幅、`fixed inset-0`），以及同檔內的允許寫法 | 錯誤數**剛好等於**放進的違規數，允許寫法 0 誤報；刪除後全站 lint 回 0 |
| V5 | **契約規格突變**（預先登記）：M1 把某頁的 `<Input>` 換回手刻 `<input className="border rounded px-3 py-1.5">` → 幾何契約紅；M2 把 `field-styles.ts` 的 `min-h-11` 改 `min-h-9` → 紅；M3 把 `Dialog` 遮罩改回 `bg-black` → 遮罩契約紅；M4 讓 `ConfirmDialog` 的取消也呼叫 resolve(true) → 「取消不送 DELETE」紅 | 預期 4 紅；其餘綠 |
| V6 | **死 class 實測（不只靠推論）**：舊建置上用真實瀏覽器量 `bg-black bg-opacity-50` 遮罩的 `background-color`；修後再量 | 舊：`rgb(0, 0, 0)`（不透明）；新：alpha＜1 |
| V7 | **換皮演練（rehearsal）**：對 `components/ui/*` 做一次合成的重新設計（Button 全圓角、Input 48px／圓角 12、Card 圓角 16、Badge 全圓角、表頭換色、頁面標題 30px、Alert 邊框加粗），數「需要動的檔案」，並在全站路由量實際 computed style。**基線（現況 HEAD）也量一次** | 檔案數 ≤ 10；全站控制項「跟著變」的比例，基線 vs 修後 |
| V8 | 完整本地 E2E 守門（`make validate-e2e`；先手動 `npm run build`，見記憶 `e2e-gate-skip-build-reuses-stale-frontend`） | 302＋新案例，全數通過；失敗者先判斷是我造成還是既有 |
| V9 | 視覺探針（自起全棧、真實播種、四種身分×藍／綠兩主題逐路由）：0 崩潰／0 pageerror／0 個 5xx；**用眼睛看截圖**，含密集表格與 Modal／Toast／確認框 | 無崩潰、無溢出、無疊在一起的元素 |

## 7. 結果

### 7.1 改了什麼

| 層 | 內容 |
|---|---|
| 設計系統（`components/ui/`） | **新增 12 個檔案**：`field-styles.ts`（欄位規格的單一來源）、`textarea`、`native-select`、`checkbox`（`Checkbox`＋`Radio`）、`table`（`Table`／`TableHeader`／`TableBody`／`TableFooter`／`TableRow`／`TableHead`／`TableCell`，`density="compact"`）、`page-title`（`PageTitle`＋`PageDescription`）、`states`（`LoadingState`＋`EmptyState`，轉傳 `data-testid`）、`chip`、`dialog`、`confirm-dialog`、`toast`、`status-badge`；另有 `lib/status.ts`（狀態登錄表：8 個領域＋功能開關的判斷函式）。**調整 5 個**：`input`／`select` 改用 `fieldStyles`（單行欄位統一固定 `h-11`）、`card`（`asChild`）、`button`（新增 `success`／`warning`／`linkDestructive`／`linkSuccess`／`linkNeutral` 與 `inline` 尺寸）、`alert`（`text-sm`）。根 layout 掛一份 `<Toaster />` 與 `<ConfirmDialogHost />`；覆蓋層階層集中為頁首 70 < `Dialog` 80 < `ConfirmDialog` 90 < `Select` 面板 95 < `Toast` 100 |
| 遷移（TypeScript AST 轉換：先乾跑、冪等、每類別一個「編譯→lint」循環） | `<label>` 108、`<textarea>` 20、`<select>` 22、`<input>` 56（`Input` 49／`Checkbox` 5／`Radio` 2）；表格 177 個元素／10 張（連同 10 個外框容器）；頁面標題 73＋副標 39；載入中 41＋空狀態 41＋錯誤／成功橫幅 61＋卡片 24（＋`asChild` 6）；按鈕 110（含 `Chip` 12、按鈕樣式的 `Link` 10）；Modal 5；`alert()` 113（error 89／success 24，**人工複核過全部分類**）；`confirm()` 19；`getStatusBadge` 16。**另有逐處手動處理**：4 個「載入中」、8 個空狀態、3 個卡片、4 個標籤／設施晶片與按鈕、9 個手刻徽章／胶囊、1 個橫幅、1 個類別連結，以及死 class 6 處。轉換器只處理靜態 className，**template literal／條件式的動態寫法是補強 lint 之後才現形的**（§10 第 9 點） |
| 守門（`eslint-rules/design-tokens.mjs`） | 新增 4 條規則：`no-raw-control`、`no-native-dialog`、`no-removed-utility`、`no-ui-recipe`；`RECIPES` 登記 7 種配方（錯誤橫幅、徽章／胶囊、載入、空狀態、Modal 遮罩、卡片、`<h1>` 標題）；豁免 `components/ui`／`layout`／`theme`；逐行例外 5 處（`ReviewStars`、`MonthCalendar`、`ConversationList`、設計稿 DS 元件 `SidebarNav`／`SortToolbar` 各一），每處都寫明理由 |
| E2E | 新規格 `at-ui-design-system.spec.ts` **8 個案例**（欄位幾何、資料表、標題、Toast、ConfirmDialog、Dialog 遮罩、執行期換皮演練、狀態登錄表語意）；改 3 支既有規格（`at-m15-e2e`、`at-room-booking`、`at-m11-cart-checkout`）：`toHaveClass` 9→0、原生對話框事件→Toast 斷言、選取態改 `aria-pressed`、刪除線改 `toHaveCSS('text-decoration-line')` |
| 文件 | 本計畫書、**ADR-001**（專案第一份 ADR）、規範 `UI_DESIGN_SYSTEM_CONVENTIONS.md`（§3 層次與覆蓋層、§4 元件目錄、§6 守門、§8、§9 分層／DoD／換皮成本）、FRD §9（NFR-UI-001～006）、測試策略 §8、追蹤表 `DEF-455`～`DEF-461` |

**規模（誠實說）**：134 個已追蹤檔案修改（`frontend` 129、文件 5；`git diff --shortstat`：+2,559／−2,220）＋ 16 個新檔案（12 個 ui 元件、`lib/status.ts`、新 E2E 規格、ADR、計畫書）。**面積與 Sprint 328（144 個檔案）相當**——這是「一次還債」，本輪的價值在下一次換皮的成本（§7.3），不在這一次比較小。沒有後端變更。

### 7.2 前後對照（量測，不是估算）

**(a) 執行期：真實瀏覽器，同一套探針、同一組播種、同樣 81 條路由、藍色主題**（改前＝HEAD `2d5ed426` 的建置；改後＝本輪最終建置；兩次都 **0 崩潰、0 pageerror、0 個 5xx**）

| 控制項 | 改前 | 改後 |
|---|---|---|
| 文字輸入框（120 個） | 高度 ≥44：110（91%）；圓角 8px 114／12px 6；字級 16px 110／14px 10 | **120／120 為 44px、圓角 8px、字級 16px** |
| 原生 `<select>`（17 個，不含 Radix 隱藏的） | 高度 ≥44：**1／17**（33px 9、40px 7、46px 1） | **17／17 為 44px**（同一排與輸入框等高） |
| Radix `Select` 觸發器（8 個） | 46px 7、44px 1 | 44px 8 |
| `<textarea>`（10 個） | 左內距 12px ×10（規格 16px）；圓角 12px 2；14px 字 3 | **10／10：內距 16px、圓角 8px、16px 字** |
| 按鈕（209→213 個） | 字重 500：107（51%）；圓角 0px：13 | 字重 500：170（80%）；圓角 0px：3 |
| 表頭 `th`（46 個） | 內距 12×16：29（63%） | **46／46：12px／500／12×16** |
| `<label>`（160 個） | 字重 500：151（94%） | 160／160 |
| Modal 遮罩（2 個，實測） | `rgb(0, 0, 0)`（**純黑不透明**） | `oklab(0 0 0 / 0.5)`（半透明） |

按鈕「高度不在 32／40／48」改後仍有 53 個，**全是刻意的變體**：`Chip` 32 個（28px 的緊湊切換鈕）、行內文字連結鈕 10 個（`size="inline"`，高度跟著文字）、設計稿 DS 元件 `SortToolbar` 的分頁籤 9 個（36px）、Toast／Dialog 的 ✕ 關閉鈕 2 個；字重非 500 的 43 個同理（月曆日期格 30、`SortToolbar` 9、`SidebarNav` 2、關閉鈕 2）。所以「按鈕走設計稿高度規格的比例」（68%→75%）不是個好指標，不拿來當成果。

**(b) 靜態掃描（同一批腳本，前後對照）**

| 項目 | 改前 | 改後 |
|---|---|---|
| 原生 `<button>`（`ui/` 之外） | 114 處／37 檔 | **9 處／8 檔**（全是有理由的例外：外殼 3 檔、領域控件／DS 元件 5 檔） |
| 原生 `<input>`／`<textarea>`／`<table>` 系列 | 56／20／10 張 | **0／0／0** |
| 原生 `<select>`／`<label>` | 23／109 | 1／1（`ThemeSwitcher`，豁免） |
| 有 `import` `ui/*` 的檔案 | 77／159 | **126／159** |
| `page.tsx` 有 `import` `ui/*` | 51／104 | **92／104** |
| 手刻 Modal（`fixed inset-0`） | 5 處／4 檔 | **0** |
| 原生 `alert()`／`confirm()` 呼叫 | 113／19 | **0／0**（掃描器數到的 2＋1 是註解裡的字樣；lint 確認 0 個實際呼叫） |
| `getStatusBadge` | 16 個檔案 | **0** |
| 「含手刻配方、不會跟著 ui 元件變」的檔案聯集（`union_pre.py`，**靜態估計**，用正則找對應的手刻配方，不是實際編輯） | **119 個** | **9 個**（全是上面那些有理由的例外） |
| 配方外洩（`scan_recipes.py`）：手刻輸入框／錯誤橫幅／徽章／表頭／分頁手刻 | 90／56／10／53／26 | 3（引用區塊）／1／0／0／0 |
| 整串 className 原樣重複 ≥3 次的比例 | 61%（916／1,493） | 49%（428／867）——**剩下的主要是 `flex items-center justify-between` 這類版面 utility（合理的重複），視覺配方已不在其中**；這個比例本身不是好指標，只當參考 |
| 死 class（原始碼有、建置 CSS 沒規則） | 15 種，其中 3 類是真缺陷（`bg-opacity-50`、`text-warning`、`border-warning`） | 13 種，**全是誤報**（散文字串 `object`／`to`／`text`、testid 前綴），**真缺陷 0** |

殘留配方我逐一看過位置：標題配方 15 處都不是 `<h1>`（統計數字、品牌字）、卡片 3 處是外殼與 DS 元件、「輸入框」3 處是引用區塊／列表列、「按鈕」1 處是聊天氣泡、「橫幅」「警示卡」各 1 處是 `Button`／`Card` 的 className 覆寫（`account` 的外框危險鈕、`inventory` 的低庫存警示卡）——**這兩處是還留在頁面裡的視覺覆寫**（列入 §11）。

### 7.3 換皮演練（V7，這輪最重要的驗收）

**方法**：對 `components/ui/*` 與 `globals.css` 做一次合成的「重新設計」，**完全不動任何頁面**——`--radius-md` 8→14px、`--rs-primary` 藍→紫、欄位高 44→48（`field-styles.ts`）、頁面標題 24→30（`page-title.tsx`）、表頭 12→14（`table.tsx`）、徽章全圓角（`badge.tsx`）、警示框線 2px（`alert.tsx`）、卡片圓角 16（`card.tsx`）。重建後用同一套探針走 81 條路由，比對改前（最終程式碼）與改後。

| | 結果 |
|---|---|
| **需要動的檔案** | **7 個**（`globals.css`＋6 個 ui 元件），**頁面 0 個**（目標 ≤10） |
| 欄位（輸入框＋下拉＋多行輸入，155 個） | **155／155** 圓角跟著變成 14px；單行欄位（145 個）**145／145** 高度變 48px |
| 主色按鈕 | 改前用舊主色 55 個 → 改後舊主色 **0**、新主色 **55** |
| 頁面標題 `<h1>`（61 個） | **61／61** 變 30px（其中 1 個本來就是 30px 的 blog 標題） |
| 表頭 `th`（46 個） | **46／46** 變 14px |
| `Badge`／`Card`／`Alert`（探針不量，用一次性規格補量） | 徽章圓角 `3.35544e+07px`（全圓角）、卡片圓角 16px、警示框線 2px，**全部跟著變** |
| 按鈕圓角（214 個） | 209 個跟著變 14px；**5 個沒跟上**——3 個 `SidebarNav` 項目（本來就是 0px）、2 個 ✕ 關閉鈕（`rounded-sm`），都不是用 `radius-md` 的 |
| 健康 | 81 條路由 0 崩潰、0 pageerror、0 個 5xx |

**反事實對照**：同一次重新設計若在 Sprint 328 的程式碼（HEAD）上做，含「手刻配方、不會自動跟著 ui 元件變」的檔案是 **119 個**（靜態估計）；現在是 **7 個檔案＋0 個頁面**。換皮成本模型寫在規範 §9.6。演練後 7 個檔案用 SHA-256 核對還原（`restore_rc=0`），並重建確認 `.next` 新於原始碼。

### 7.4 守門與契約自己會不會紅（突變驗證）

**(a) lint 規則突變（V4）**：暫存檔放進每條規則的違規與同檔內的允許寫法 → **25 個錯誤**（`no-raw-control` 12、`no-native-dialog` 3、`no-removed-utility` 4、`no-ui-recipe` 6），**允許寫法 0 誤報**（含區域變數 `alert`、`rounded-md` 的控制項、`h-48`）；刪除暫存檔後全站 lint 回 0。**我預先登記的是 24，少算了 `thead`／`tbody` 各有一個 `<tr>`（實際 `no-raw-control` 是 12 不是 11）**，逐項核對後 25 與違規清單完全一致。之後補強的「徽章／胶囊」配方與放寬的橫幅配方，是用真實程式碼驗證的（lint 立刻在 13 處我漏遷的手刻徽章／橫幅現形，全部修掉後回 0）。

**(b) 契約規格突變（V5）**——**A 組 8 個突變同時套用（每個只對應一個案例），預期 8 個案例各自紅；B 組 1 個「頁面繞過元件」突變＋2 個不相干的控制組，預期只有 1 個案例紅。實際完全一致**：

| 突變 | 預期紅的案例 | 實際（失敗訊息摘錄） |
|---|---|---|
| M2 `fieldStyles` 的 `h-11`→`h-9` | UIDS-01 | 紅：「`select`／`input`高度 36px < 44px」 |
| M5 `TableHead` 內距 `px-4 py-3`→`px-2 py-1` | UIDS-02 | 紅：表頭 `pl` 不是 16px |
| M9 `PageTitle` `text-2xl`→`text-xl` | UIDS-03 | 紅：h1 期望 24px、實際 20px |
| M7 `Toast` 錯誤的 `role` 一律改 `status` | UIDS-04 | 紅：期望 `alert`、實際 `status` |
| M4 `ConfirmDialog` 取消也 resolve(true) | UIDS-05 | 紅：「按取消不該送 DELETE」期望 0、實際 1 |
| M3 `Dialog` 遮罩 `bg-black/50`→`bg-black` | UIDS-06 | 紅：「遮罩顏色 `rgb(0, 0, 0)` 應該是半透明」（**量到的正是原本的 bug**） |
| M8 `NativeSelect` 寫死 `rounded-[8px]`（繞過 token） | UIDS-07 | 紅：`select` 圓角 8px、期望 20px |
| M6′ 登錄表 `tenant.PENDING_REVIEW` 由 warning 改 secondary | UIDS-08 | 紅：「`tenant.PENDING_REVIEW`『審核中』是 secondary，依規則應為 warning」 |
| **A 組結果** | | **8 紅／0 綠** |
| M1 `posts/new` 的標題輸入框換回手刻 `<input className="border rounded px-3 py-1.5 text-sm">` | 只有 UIDS-01 | 紅：「高度 34px < 44px、字級 14px、左內距 12px」；**其餘 7 個綠** |
| C1 改 `Chip` 選取色、C2 改 `CardContent` 內距（不相干的控制組） | 全部維持綠 | **維持綠** |

綠色基線（沒有突變）**8／8**（在加入「同頁單行欄位等高」斷言、固定高 `h-11` 之後重跑過）。突變前後的還原都用 SHA-256 核對（`restore_rc=0`）——但**第一次 A 組嘗試有一個驅動缺陷**，見 §10 第 5 點。

**預先登記的 V5 與實際的差異（誠實記錄）**：原本登記的 M2 是「`min-h-11`→`min-h-9`」。我推演後發現它**不會紅**（欄位自然高度是 46px，大於 44，`min-h` 根本沒有發揮作用）；而新加的「同頁單行欄位等高」斷言同時抓到另一個真實缺陷（原生 `<select>` 44px、`Input` 46px）——所以欄位規格改成固定 `h-11`，M2 隨之改成 `h-11`→`h-9`。

### 7.5 驗證結果對照 §6

| # | 結果 |
|---|---|
| V0 基線 | `type-check` 0、`lint` 0（開工前）；掃描腳本的「之前」數字見 §2 與 §7.2 |
| V1 | 每個轉換步驟後立即 `type-check`＋`lint`，全部 0／0；`next build` 成功（多次）；舊碼的真實瀏覽器基線探針與最終探針、換皮演練探針共 3 輪 |
| V2 | 乾跑報告的「需人工處理」逐一讀完：`alert` 分類人工複核發現 **6 個驗證錯誤被誤判成成功**（「不支援的檔案類型」「檔案大小超過限制」「名稱與 slug 為必填」「模板代碼、名稱與內容為必填」「請至少新增一個品項」「請為每個品項選擇規格…」），補關鍵字後重跑；按鈕 114 個映射逐行看過；19 個 `confirm()` 的外層函式逐一確認都已是 `async`（沒有 `preventDefault` 順序風險） |
| V3 | 見 §7.2 |
| V4／V5 | 見 §7.4 |
| V6 死 class 實測 | 舊建置：`/dashboard/posts/categories`、`/dashboard/notifications` 的 Modal 遮罩 `rgb(0, 0, 0)`（截圖上整頁背景純黑）；新建置：`oklab(0 0 0 / 0.5)`；E2E-UIDS-06 永久守住 |
| V7 | 見 §7.3 |
| V8 完整守門 | 第一次 308／310（1 個自己造成的回歸＋1 個同因未執行）→ 修復後整個重跑 **310／310**（§7.6） |
| V9 視覺探針 | 81 條路由（匿名／買家／店主／管理員，**只掃藍色主題**）× 改前／改後／換皮後各一輪：0 崩潰、0 pageerror、0 個 5xx。**我用眼睛看了截圖**：改前 3 張（Modal、FAQ、採購單新增）、改後 9 張（FAQ、分類 Modal、貼文列表、採購單新增、通知模板、新增商品、知識庫、平台管理台、購物車），找到 4 個問題（§7.7） |

### 7.6 完整守門（`make validate-e2e`，`E2E_GATE_SKIP_BUILD=1`，乾淨資料庫）

守門前：手動 `npm run build`、確認 `.next` 新於所有原始碼（`find src -newer .next/BUILD_ID` 為 0），JAR 與原始碼一致（`mvn package` 判定無需重打）；自己的全棧先收掉，4 個埠與容器確認空閒。

**第一次**（對含全部遷移與新規格、但尚未補回兩個 `data-testid` 的建置）：**310 個案例：308 通過、1 失敗、1 未執行**（8.2 分鐘）；schema 對齊 ✅（`ddl-auto=validate`＋Flyway 乾淨庫）。
- **失敗 `E2E-BLOGCAT-03`**（不存在的分類 ID → `getByTestId('blog-category-not-found')` 找不到）：**本輪造成**——`empty` 轉換器整元素替換時丟了 `data-testid`。`E2E-BLOGCAT-04`（`blog-category-empty`）因同一個 describe 的 serial 相依而未執行，同一個原因。見 §10 第 11 點（含「機械審計確認丟失的只有這 2 個」）。

**修復後重跑整個守門**（不是只跑受影響的規格）：**310 個案例全部通過（7.6 分鐘）**，0 失敗、0 跳過、0 未執行；schema 對齊 ✅。後端記錄到認證端點被限流 **11 次**，E2E 輔助函式依 `Retry-After` 重試後全部自癒（第一次守門是 0 次；**沒有查為什麼這次比較多**，推測與執行時序有關，未驗證——Sprint 328 也有同樣的觀察）。

310 ＝ 既有 302 ＋ 新規格 8（`at-ui-design-system`）。既有規格裡 3 支改了斷言的實作（§7.1：`at-m15-e2e`、`at-room-booking`、`at-m11-cart-checkout`），全部通過。**這個 workflow 的雲端 CI 不含 Playwright，E2E 的證據是本機這次守門。**

### 7.7 視覺檢查找到的問題

1. **Modal 開啟時頁首仍浮在遮罩之上**（頂欄深藍與頁首淺色都沒被遮罩蓋到）：`Dialog` 是 `z-50`、頁首是 `z-[70]`。**這是既有問題**（改前截圖同樣）——Sprint 329 前的 Modal 就是這樣；順帶發現 Radix `Select` 彈出面板也是 `z-50`，放進 Dialog 會被蓋住。→ 階層統一為頁首 70 < Dialog 80 < ConfirmDialog 90 < Select 面板 95 < Toast 100。
2. **採購單明細表被我「標準化」成寬鬆內距，頁腳「0 元」換成兩行**（基線是一行）：那張表原本就是刻意的**緊湊密度**（`px-3 py-2`，內含輸入框的資料錄入表），其他 9 張是 `px-4 py-3`；我的表格轉換把它一併改成標準內距。→ `Table` 新增 `density="compact"`（單一來源），採購單明細表用它。**這是我造成的回歸，靠「改前／改後截圖並排」才看得出來，斷言看不到。**
3. **同一排的原生 `<select>`（44px）比 `<Input>`（46px）矮 2px**：加 `leading-6` 沒用（Chrome 的原生 `<select>` 不按行高撐高度）→ 單行欄位固定 `h-11`，並加「同頁單行欄位等高」斷言（E2E-UIDS-01）。
4. **`PricingCalendarPreview.tsx` 自己定義了一個同名的 `Badge` 元件**（蓋掉 ui 的 `Badge`，還用了專案從沒定義的 `bg-primary`）→ 刪掉，改用 ui 的。
5. （確認過不是問題）貼文列表第一列的灰色「刪除」是原本的邏輯（`disabled={post.status !== 'DRAFT'}`，只有草稿能刪）。

## 8. 風險與我替使用者做的決定

### 8.1 風險（開工時登記）

1. **輸入框從 ~34px 變 44px，密集的篩選列／表格內欄位會變擠**（S328 風險 3 的延伸；這次是真的會發生）。處置：V9 看截圖；若確實過擠，**再**加 `size="sm"` 變體（不預先加，不發明設計稿沒有的規格）。
2. **`alert()` 是阻塞式、Toast 是非阻塞**：`alert(...)` 之後緊接 `router.push()` 的流程，使用者看到訊息的時間變短。處置：成功訊息 4 秒、錯誤 6 秒；V9 抽看。
3. **E2E 的選擇器撞名**（S328 §8 的四類耦合）：新元件加 `role`、加元素（Toast 區域、Dialog）。已查證沒有 `getByRole('alert'|'status'|'dialog')` 查詢；Toast 區域掛在 `body` 末端、不在任何 `main` 內。
4. **`Label`／`Input` 取代後的版面位移**：標籤 `leading-none`、輸入框變高，可能讓表單頁排版略變。V9 抽看；E2E 以 testid／placeholder 定位，不受影響。
5. **機械轉換的語意風險**：`<label>` 沒有 `htmlFor`、被 `<input>` 包住與並列的情況不同；轉換只換元素與 class，不改結構，所以關聯行為不變。
6. **`StatusBadge` 統一顏色是行為變更**：貼文「已歸檔」從黃變灰之類。標籤文字不變；顏色統一是刻意的（§5.2 規則），列在「我替使用者做的決定」。
7. **修 `bg-opacity-50`／`text-warning` 是可見行為變更**（遮罩變半透明、「待審批」標示從無到有）：方向都是「做到原本打算的樣子」。

### 8.2 我替使用者做的決定（可一句話推翻）

1. **配方層放在 `components/ui/`**（不另開 `patterns/` 目錄）：讓「允許寫視覺 class 的地方」只有一個目錄，守門規則的豁免範圍就是 `components/ui/**`。記錄在 ADR-001。
2. **`NativeSelect` 而不是把 23 個 `<select>` 改成 Radix `Select`**。
3. **全部輸入框統一 44px**，不加 `sm` 尺寸（設計稿只有一種輸入框）；視覺檢查若過擠再加。
4. **`PageTitle`／`PageDescription` 1:1 取代元素，不做 `PageHeader` 包裝**：包裝要重組 81 個頁面的版面，風險大於收益；版面（`flex justify-between`）本來就是版面 class，不是「換皮」會動的東西。
5. **狀態語意色統一**（§5.2），貼文／知識庫等草稿與封存的顏色會變。
6. **把原生 `alert()`／`confirm()` 全部換掉**，包含 `at-m15-e2e` 把 `alert` 當同步訊號的用法（改等 Toast）。
7. **新增 4 條 ESLint 規則**，而不是只靠文件。
8. **修掉死 class**（遮罩、`warning`）。`rs-warning` 是亮黃，不當文字色：「待審批」的強調改用 `font-medium text-rs-ink`＋`border-rs-warning`。
9. **不碰** §9 的範圍外項目。
10. **DEF 編號沿用追蹤表順序**（下一個是 `DEF-455`）；不修的項目**登記，不當作已修**。

**施工中追加的決定**（開工時沒登記，事後補記，同樣可一句話推翻）：

11. **單行欄位統一固定高 44px（`h-11`）**，不是 `min-h-11`＋padding 撐高：原生 `<select>` 不按行高撐，會與 `Input` 差 2px（§7.7 第 3 點）。多行的 `Textarea` 最小高 80。
12. **`Table` 加 `density="compact"`**（採購單明細這種內含輸入框的資料錄入表用）：我的表格轉換曾把它標準化成寬鬆內距，造成版面回歸（§7.7 第 2 點）。這是設計稿沒有的變體，只在一處使用。
13. **覆蓋層層級重排**（頁首 70 < Dialog 80 < ConfirmDialog 90 < Select 面板 95 < Toast 100）：原本 Modal 開啟時頁首仍浮在遮罩之上。
14. **`tenant.PENDING_REVIEW`（審核中）由中性色改 `warning`**：與「待審／審批中」其他領域一致（登錄表規則）。
15. **`EmptyState` 標題用 ink 字（`font-medium`）**，原本多數空狀態是次要色；有圖示版（買家頁）保留圖示＋標題＋說明＋動作的結構。
16. **價格調整徽章（購物車、房源詳情）由淡底改實心 `Badge`**（省＝success、加價＝warning）；`warning` 實心鈕與徽章的文字用 ink（白字在亮黃上不可讀）。
17. **`Toast` 放在畫面底部置中**（成功 4 秒、錯誤 6 秒自動消失、可手動關閉）。
18. **blog 文章的類別連結改成 `Button secondary sm`**（原本是一個手刻的小胶囊連結，只此一處）。
19. **`SortToolbar`／`SidebarNav`（設計稿 DS 元件）與 `ReviewStars`、`MonthCalendar`、`ConversationList` 用逐行 `eslint-disable` 明列為例外**，而不是硬套 `Button`／`Chip`（它們的視覺規格就是自己的）。`ThemeSwitcher` 與外殼目錄整個豁免。

## 9. 範圍外（量測了、登記，本輪不修）

| 項目 | 量測 | 為什麼不在本輪 |
|---|---|---|
| 頁面繞過 `services/` 層直接用 axios | 16 個檔案 | 與外觀無關；改動牽涉資料層 |
| 頁面資料載入樣板（loading＋error＋effect 三件套） | 41 頁 | 需要設計共用的資料載入 hook，行為風險高，且與換皮成本無關 |
| `'use client'` 比例 | 93／104 | 渲染策略議題，需要逐頁評估 |
| 前端零單元／元件測試 | `test` 為 `echo` | 本輪契約改由 E2E 的 computed style 與 lint 守；是否引入元件測試框架是單獨的決定 |
| 29 個 `page.tsx` 沒有 `<h1>` | 29 | 需要逐頁確認標題在哪（領域元件？）；屬無障礙／SEO |
| 蓮田／柔彩／橄欖三個主題沒看過畫面；設計稿 error／success／ink-muted 對比度偏低；`DEF-454` 店面 Header 390px 溢出；非標題 25 處 `font-bold` | S328 已揭露 | 設計決定，需要使用者 |

已登記到 [DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md)：`DEF-455`（繞過 `services/` 層）、`DEF-456`（資料載入樣板）、`DEF-457`（`'use client'` 比例）、`DEF-458`（前端零單元測試）、`DEF-459`（29 頁沒有 `<h1>`）；以及施工時**新發現**的兩項：`DEF-460`（`POST /v2/faqs/categories` 重複 slug 回 `500 E-9900`，只有 1 次觀察、未查根因）、`DEF-461`（設計稿 DS 元件放在 `components/storefront/` 而不在 `ui/`）。

## 10. 過程中的失誤與修正

1. **我在 Sprint 328 的總結說「統一」，卻沒量繞過元件的那一邊**（§3.2 SA4）：只有走 `ui/*` 的控制項吃到規格。本輪更正並量化（§2.2）。
2. **預先登記的數字有誤**：lint 守門突變預期 24、實際 25（漏算 `thead`／`tbody` 各一個 `<tr>`）；V5 的 M2（`min-h-11`→`min-h-9`）其實會是綠的（自然高度 46 > 44），被新加的「同頁單行欄位等高」斷言連同 44 vs 46 的真實缺陷一起暴露，之後改成固定高（§7.4）。
3. **轉換器的寬度語意缺陷**：我把頁面上的 `w-full` 當成「元件預設」去掉，但 `NativeSelect`／`Textarea` 是新元件、預設 `w-full`，原本「自動寬度」的 12 個 select、6 個 input、1 個 textarea 會在 flex 篩選列裡被撐滿。**看 diff 時發現**，用盤點資料確認影響面後修轉換器（原本既沒有 `w-full` 也沒有明確寬度的補 `w-auto`），還原 45 個檔案重跑。
4. **表格轉換把「緊湊密度」的資料錄入表標準化**（採購單明細表）→ 版面回歸（頁腳「0 元」換行）。**靠改前／改後截圖並排才看出，斷言看不到**；補 `Table density="compact"`（§7.7）。
5. **我的突變驗證驅動第一次 A 組嘗試的 `apply` 不是交易式——工作樹一度被污染**：第 6 個突變的匹配字串，因為我稍早把 `z-50` 改成 `z-[80]` 而過時，`apply` 在寫了前 5 個突變之後中止；而還原用的雜湊檔只在 `apply` 全部成功後才寫，所以 `restore` 找不到它，**前 5 個突變（`field-styles`、`table`、`page-title`、`toast`、`confirm-dialog`）一度留在工作樹裡**。我看到驅動輸出第一行「apply 失敗」就立刻從備份逐檔還原並核對標記；之後把 `apply` 改成交易式（先全部驗證、先備份並寫雜湊、最後才寫檔），並對三組突變做「套用→立刻還原」空轉。**沒有任何突變被帶進建置、測試或 commit**（`apply` 失敗時驅動在建置之前就結束；之後三組突變的每一次套用與還原都用 SHA-256 核對；最後一次建置前工作樹再核對過「突變標記」全數不在）。
6. **新契約規格首版自己的 4 個問題**：猜錯標題文字（「新增貼文」vs「新建貼文」）、猜錯連結名稱、欄位有 `transition-colors`，換皮後立刻量會量到動畫中間值 `rgb(219,233,232)`、Chrome 把 `bg-black/50` 算成 `oklab(0 0 0 / 0.5)` 而不是 `rgba(…)`——**都是規格的錯，不是元件的錯**，逐一釐清後修規格，沒有放寬斷言。
7. **探針第二次播種撞到唯一約束**（SKU 代碼、FAQ slug 全域唯一）→ 路由 79 條（前一次 81）、表格變少；改成播種值帶唯一後綴後重跑，前後才能一對一比對（順帶發現 `DEF-460`）。
8. **工具小失誤**（記憶裡都已經有同類紀錄，這次各又犯一次）：①zsh 未加引號的 `--include=*.tsx` 讓開頭的統計全是假 0（改用 Python 掃描）；②背景指令又加了 `&`（雙重背景化）→ 完成通知不可信，之後只看日誌檔裡的結尾行；③Python heredoc 內含中文又寫了 `# -*- coding: ascii -*-` 標頭，腳本根本沒執行（有兩次是我以為改成了、事後 `grep` 才發現沒變）；④一個路徑多打了一個 `/` 讀不到檔。
9. **轉換器漏掉動態 className**：它只處理靜態字串，template literal／條件式寫的徽章與橫幅（13 處）、`min-h-[40vh]` 的「Loading...」區塊（2）、`py-24` 的空狀態（5）、類別胶囊連結（1），是**補強 lint 配方、再列出殘留配方的位置逐一看**才現形的——所以「先把守門做強，再迁移」這個順序是對的；也提醒轉換器的「完成」不等於「沒有殘留」。
10. **這輪改動面積（150 個檔案＝已追蹤修改 134＋新檔案 16）與 Sprint 328（144 個）相當，沒有比較小**。「重構」不該讓人聽起來像「更小的改動」；價值在下一次（§7.3），要明說。
11. **整元素替換的轉換器丟掉了屬性——第一次完整守門因此失敗**：`empty`（以及 `loading`）轉換器遇到特定 className 的 `<div>`，就把**整個元素**換成 `<EmptyState title=… />`。我自己的原則是「保留所有非 className 屬性」，這幾支卻沒做到：`blog/category/[id]` 的 `data-testid="blog-category-not-found"` 與 `blog-category-empty` 因此被丟掉。**第一次完整守門 310 個案例：308 通過、1 失敗（`E2E-BLOGCAT-03`）、1 因 serial 相依未執行（`E2E-BLOGCAT-04`，同一個原因）**。我自己的檢查（`type-check`、`lint`、契約規格、探針、突變驗證）**全都沒抓到，是完整守門抓到的**。修復後我用 `git diff` 做了**機械審計**：列出 diff 中被移除、卻沒有在新增行重新出現的屬性（`data-testid`、`id`、`role`、`aria-*`、`htmlFor`、`name`、`placeholder`、`data-*`、`href`、`onClick`、`onChange`…），確認丟失的**只有這 2 個** `data-testid`；修復只是用已有的 `EmptyState` 轉傳屬性把它們補回去（轉傳是我在完整守門之前、遷移 `ListingDetail` 那幾處時就加的）。**教訓：整元素替換的轉換器，轉換後要立刻做「屬性丟失」的機械審計，不要等到完整守門；這個審計寫進測試策略 §8.2 第 6 點。**修復後**重跑完整守門**（不是只跑受影響的規格）。

## 11. 限制（誠實揭露）

- **只有 Chromium；探針與視覺檢查只掃藍色主題**（Sprint 328 掃過藍與綠）。蓮田、柔彩、橄欖三個主題沒看過畫面；換皮演練的 token 層級也只動了藍色那一組。
- **視覺檢查不是逐頁**：104 頁裡我用眼睛看了改後 9 張、改前 3 張；有大量資料的密集表格（ERP）探針只播了各 1 筆，擠不擠沒看過。
- **19 個刪除確認流程只有 1 個頁面在 E2E 實際點過**（FAQ 管理頁，mock）；其餘 18 個靠同一個 `confirmDialog()`、型別檢查與 lint（不能再呼叫原生 `confirm()`），**沒有逐頁點過**。
- **Toast 是非阻塞、會自動消失**（成功 4 秒、錯誤 6 秒）；原本 `alert()` 後緊接 `router.push()` 的流程，使用者看到訊息的方式變了（Toast 掛在根 layout，client 端導向後仍在）。**我沒有逐一走過 113 個呼叫點**，只驗證了 E2E 涵蓋到的與代表頁（FAQ、貼文、分類）。
- **使用者可見的設計變更**（我替使用者做的決定，§8.2）：狀態顏色統一（貼文「已歸檔」黃→灰、知識庫「草稿」黃→outline…）、`EmptyState` 標題改成 ink 字（多數原本是次要色）、價格調整徽章（購物車、房源詳情）由淡底改實心色、blog 類別連結改成 `Button secondary sm`、單行欄位統一固定 44px。
- **換皮演練是合成的**重新設計；「119 個檔案」的反事實是**靜態估計**（正則找對應的手刻配方），不是在 HEAD 上實際做一次的編輯數。
- **頁面裡仍有視覺覆寫**：`account` 的外框危險鈕（`border-rs-error/30 text-rs-error…`）、`inventory` 的低庫存警示卡，以及一些 `Card`／`CardContent` 的 padding 覆寫（`p-6`、`pt-6`）——lint 沒有守「對 ui 元件的 className 覆寫」這一層。
- **ESLint 規則靠字串／token 比對**：擋得住「照抄舊配方」，擋不住「用別的 class 拼出同樣的外觀」；契約測試（computed style）補一部分，不是全部。
- 沒跑後端測試與 `validate-schema-doc`（沒有後端或 schema 變更）；`DEF-455`～`DEF-461` 只登記、沒修；`DEF-453`、`DEF-454` 仍順延。

## 12. 狀態

- **狀態**：實作、新契約規格（8 案例）、守門與契約的突變驗證、換皮演練、完整本地 E2E 守門（**310／310**，第一次守門抓到我自己造成的 1 個回歸，修復後整個重跑）、文件（計畫書、ADR-001、規範、FRD §9、測試策略 §8）均完成；追蹤表登記 `DEF-455`～`DEF-461`；**沒有後端變更**。
- **push／雲端 CI／Docker 收尾**：依 2026-10-29 長期授權 commit 後直接 push；結果由回填 commit 補記（沿用 Sprint 322～328：回填 commit 只改文件，隨下一輪帶出）。
- **仍等你決定**：沿用 Sprint 328 的清單（`DEF-294`、`DEF-449`、`DEF-419`、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取、退款 fail-open 的取捨…）。**本輪新增請你看一眼的**：①§11 列的「使用者可見的設計變更」（狀態顏色統一、`EmptyState` 標題改 ink 字、價格調整徽章淡底→實心、類別連結改 `Button secondary sm`）——這些是我替你做的決定，可一句話推翻；②蓮田／柔彩／橄欖三個主題我仍沒看過畫面；③設計稿的 `error`／`success`／`ink-muted` 對白底對比度偏低，沿用 token 沒加深。
- **下次說『請繼續』我可以直接做**：`DEF-453` 補量測（已順延兩輪）；或 `DEF-460`（量測題：同店鋪／不同店鋪重複 slug 的 HTTP 狀態與錯誤碼）。`DEF-455`～`DEF-459` 是較大的結構題，建議你先決定要不要做。
