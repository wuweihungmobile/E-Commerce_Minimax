# Sprint 328 Plan — 全站 UI 以「意象若水 Homepage 設計稿」為 BASE 統一

## 1. 起點與範圍

### 1.1 起點

[Sprint 327](SPRINT_327_PLAN.md) 收尾時，使用者把總結貼回來並說「請繼續完成任務！」，**沒有 `==>` 回覆**，並**另外**問了一題、又在執行中補了一句：

1. 「目前前端 UI 是否都是以 `docs/01_requirements/Design_UI/Homepage (standalone) _OK.html` 為 BASE 進行設計？」
2. 「若沒有，請以 Homepage (standalone) _OK.html 為 BASE，統一所有 UI」

第 2 句是**明確指示**（不是對「請繼續」的推論）：答案若是「沒有」，就要把全站 UI 統一到該設計稿。它也與 [`HOMEPAGE_TEMPLATE_INTEGRATION_FRD.md`](../01_requirements/HOMEPAGE_TEMPLATE_INTEGRATION_FRD.md) 2026-07-01 使用者確認的決策 **D1（全站統一改版：買家店面＋賣家後台 dashboard／admin／cms）** 一致。

- 上一輪總結的「下一步」是 `DEF-453` 補量測。本輪以使用者的 UI 指示為主；`DEF-453` **沒有做**，順延（見 §10）。
- 總結「還等你決定」的項目（`DEF-294`、`DEF-449`、`DEF-419`、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取、Sprint 325 的兩個觀察、fail-open 取捨）**不在授權範圍內**，本輪不碰。
- 長期授權（2026-10-29）：本輪 commit 後直接 push，連同 Sprint 327 留在本機的純文件回填 commit `89623513` 一起帶出。

### 1.2 成功標準（開工時定義）

1. **先回答問題，答案要有證據**：逐項核對設計稿本身（解包）與前端現況，不憑印象（§2）。
2. **設計稿的規格全部落到程式碼的單一來源**（`globals.css` 與 `components/ui/*`），而不是散在各頁：色票（5 套）、字級、圓角、**無陰影**、焦點樣式、按鈕／輸入框／卡片／徽章／警示／開關／骨架屏的規格。
3. **每一頁都在共用外殼之下**：買家頁、店面、賣家後台、平台管理台、CMS、blog、開店申請。判準：列出每個 `page.tsx` 的 layout 鏈，除了明確豁免者（登入流程頁沿用既有 `(auth)`、`oauth/callback`）都有 TOP＋Bottom。
4. **原始 Tailwind 色盤 class 歸零**（基線 1666 次、115 個檔案），陰影 class 歸零（基線 106 次）；**用 lint 守門**，且守門本身要用「放進一個違規 → lint 必須紅」證明有效（突變驗證）。
5. **主題切換真的全站生效**：新增永久 E2E，切換 `data-theme` 後各區域的頂欄顏色跟著變；並用突變（拿掉某區域的 layout）證明這支規格會紅。
6. **不退步**：`type-check`、`lint`（基線都是 0）、`next build`、既有 52 支 Playwright 規格（`make validate-e2e`）通過；任何失敗先判斷是我造成還是既有，不憑猜測。
7. 範圍不擴張：**不改後端、不改 API、不新增功能、不重做頁面資訊架構**（只處理重複的頁面頂欄與缺少導覽的問題）。

### 1.3 動手前用工具核對的事實

| 說法（出處） | 核對結果 |
|---|---|
| 設計稿是什麼 | `Homepage (standalone) _OK.html`（6,605,621 bytes）是 Claude Design 的「Bundled Page」：`__bundler/manifest`（117 個資源，base64＋gzip）＋`__bundler/template`（4176 行 HTML）。**只有一個畫面：賣場首頁**（頂欄、頁首含搜尋與購物車、左側分類、排序列、商品網格、分頁、頁尾、懸浮客服）。沒有購物車、結帳、訂單、後台任何畫面 → 「全站都以它為 BASE」只能解讀為「沿用它的 token、外殼與元件規格」 |
| FRD 說設計系統有『6 個 React 元件＋43 條 CSS』（FRD §3） | **不完整**。解包後 `@ds-bundle` 標頭列出 **12 個元件**：Button、IconButton、Pagination、Avatar、Badge、ProductCard、Skeleton、Input、SearchBar、SidebarNav、SortToolbar、Icon。專案只重建了其中 6 個；Button／Input／Skeleton 用的是 shadcn 預設樣式 |
| 色票是否忠於設計稿 | **是**。用腳本逐主題逐 token 比對（`--color-X` ↔ `--rs-X`）：blue／green／lotus／pastel／test05 **5 套 × 21 個 token 全部相同**，無缺漏無多出 |
| 設計稿的非色彩規格 | 字族 Inter＋Noto Sans TC；字級 h1 36／h2 30／h3 24／h4 20／h5 18／h6 16／body 16／sm 14／xs 12（**恰好等於 Tailwind 預設字級**）；行高標題 1.35／內文 1.65；字重 400／500／600／700；8px 基準間距；圓角 sm 4／md 8（按鈕、輸入框）／lg 12（卡片、影像、控制列）／full；容器 1440、左右 24、側欄 240、欄距 24；**「嚴禁陰影，僅用 1px 實線框與色差分層」**；轉場 0.2s ease-out；骨架屏 `#F3F4F6`；`:focus-visible` 2px 主色外框；只支援淺色 |
| 按鈕／輸入規格 | `rs-btn`：sm 32／md 40／lg 48 高，radius-md，字重 500，solid／outline／ghost／accent，disabled＝disabled 底與字；`rs-input`：最小高 44、padding 10×16、1px 邊框、radius-md、聚焦＝主色邊框＋2px 外框、error＝error 色邊框 |
| 目前有幾個 layout | **3 個**：`app/layout.tsx`（根）、`(storefront)/layout.tsx`、`(auth)/layout.tsx`。**沒有** dashboard／admin／cms／blog／tenant 的 layout |
| 採用現況（`ui_adoption.py` 掃 `frontend/src` 164 個 .tsx） | 含設計稿 token（`rs-*`）的檔案 **26 個（16%）**。`app/dashboard` 49 檔、`app/admin` 9、`app/cms` 4、`app/blog` 3、`app/tenant` 1：**0 個**用到 `rs-*`。`app/(auth)` 28 檔只有 3 個用到，其餘 27 個（含 335 次）用 Tailwind 預設色盤；它們有 Header／Footer（layout 提供），內容區沒有跟著設計稿 |
| Sprint 35 的路線圖後來怎麼了 | 階段一（首頁）S35 完成；階段二（買家頁）順延到 S37 完成（`(auth)/layout.tsx` 註解『S37 AI-1901』）；**階段三（行動項 `AI-1904`：dashboard／admin／cms 套 Shell，seller 變體）在 `SPRINT_35_RETRO.md:61` 排『Sprint 37』，全部 docs 精確 grep `AI-1904` 只有這 1 處**——之後沒有任何計畫書、追蹤表接手或結案。屬沉默漏掉 |
| 原始色盤用量（`palette_freq.py`） | 73 種寫法、**1666 次**：gray 1071、red 275、blue 211、green 59、yellow 29、amber 18、orange 2、purple 1。變體前綴只有 `hover:`（172）、`focus:`（59）、`placeholder:`（9）、`group-hover:`（2）、`disabled:`（1），**沒有 dark／響應式變體**。`bg-white` 88 次、`text-white` 78 次（設計稿允許 `#FFFFFF`）。`shadow*` 106 次（`shadow-sm` 62、`shadow` 27、`shadow-md` 13、`shadow-xl` 2、`shadow-lg` 2）。`rounded*` 428 次。寫死 hex 色值：**0 個**（腳本曾回報 `dashboard/posts/new` 有 `#123`／`#125`，讀原始碼後確認是 HTML 實體 `&#123;`／`&#125;`＝花括號，正則誤判） |
| 後台頁面現況（`nav_clusters.py`，dashboard／admin／cms／blog／tenant／oauth 共 67 個 page） | **29 頁**自己組一整套頂欄（`min-h-screen bg-gray-50` ＋ `<nav>`；其中 23 頁是『麵包屑＋email＋登出』同一套骨架，4 頁只有標題、2 頁是儀表板與管理台首頁）；**38 頁完全沒有頁面層級的頂欄**（admin 除總覽外的 8 頁、cms 4 頁、blog 3 頁、`oauth/callback`，以及 dashboard 內的 bookings／chat／erp／faq／knowledge／media／notifications／posts／returns／revenue／support 等），這些頁沒有共用的導覽列 |
| E2E 對後台頂欄的依賴（精確 grep） | 小：①儀表板首頁斷言 `${email} (STORE_OWNER)` 文字、標題『營運總覽』、連結『商品管理』『房型管理』（`at-m15-e2e`）；②`dashboard-tenants-link`（`at-dashboard-tenant-nav-real`）、`admin-tenants-link`／`admin-pending-*`（`at-admin-overview-real`）等 testid；③『登出』文字只在買家頁 `/cart` 點。→ 側欄標籤與 testid 要沿用現有快速入口。**（開工時的這個 grep 不完整：它只找標籤與 testid，漏了另外四類耦合，第一次完整守門 5 個失敗全是這些，見 §8）** |
| 共用 Header 加進頁面的已知陷阱（記憶：`e2e-login-page-shared-header-collision`） | Header 的『搜尋』是 `button[type=submit]`，會被泛用選擇器搶走。`/tenant/apply` 的規格選擇器已經是 `button[type="submit"]:not(:has-text("搜尋"))`；後台用**不含搜尋的賣家 Header** 就沒有這個問題 |
| 全域樣式決策依據（`pattern_probe.py`） | 標題 133 個、**只有 2 個沒有字級 class**（`blog/[slug]` 與 `ui/card` 的 `CardTitle`）→ 可以安全採用設計稿的標題規則；`<Input>` 113 個、**0 個覆寫高度** → 可統一成 44px；`<table>` 只在 10 個檔案，欄位標頭最常見的寫法是 `font-medium px-4 py-3 text-gray-500 text-left text-xs uppercase`（39 次） |
| 基線 | `git status` 乾淨；`npm run type-check` **0**、`npm run lint` **0**（開工前各跑一次） |

## 2. 回答使用者的問題

**不是。** 現況是「部分採用」：

| 區域 | 採用情況 |
|---|---|
| 色票與主題切換（根層 `globals.css`、`ThemeScript`） | ✅ 與設計稿逐 token 一致 |
| 店面（`(storefront)`：首頁、listings、products、rooms、stores、隱私權、服務條款）＋共用 Header／Footer／分類側欄／排序列／商品卡／分頁 | ✅ 使用設計稿元件與 token（`(storefront)` 與 `components/storefront` 掃描結果：Tailwind 預設色盤 0 次） |
| 買家頁（`(auth)`：購物車、結帳、訂單…） | 🟡 外殼（Header／Footer）是設計稿的；**內容區 27／28 個檔案仍是 Tailwind 預設灰藍紅**，換主題不會跟著變 |
| 賣家後台（`dashboard`，49 檔）、平台管理台（`admin`，9）、CMS（`cms`，4）、blog（3）、開店申請（`tenant`，1） | ❌ **完全沒有**：沒有共用外殼、0 個檔案用到 `rs-*` token |
| `components/ui/*` 基礎元件（Button／Input／Card／Select…） | ❌ shadcn 預設樣式（36px 高、帶陰影、中性灰藍），不是設計稿的 `rs-btn`／`rs-input` 規格 |

## 3. 做法

分五層，由槓桿最大的往下（改一處、影響全站，優先於逐頁改）：

**A. 地基（`globals.css`）**
- shadcn 語意 token（`--color-primary`、`--color-background`、`--color-muted-foreground`、`--color-border`…）改成指向 `--rs-*`（`@theme inline`），讓沿用 shadcn token 的元件與頁面自動跟隨主題。
- 圓角比例覆寫為設計稿：`--radius-md: 8px`、`--radius-lg: 12px`（`rounded-md`／`rounded-lg` 全站自動對齊）。
- 預設邊框色改成 `--rs-border`（Tailwind v4 的裸 `border` 預設是 `currentColor`，卡片會是近黑邊框）。
- 全域 body／標題／焦點／捲動條／`color-scheme: light` 依設計稿；補 `--rs-skeleton`。

**B. 基礎元件（`components/ui/*`）改成設計稿規格**：Button（40 高、solid／outline／ghost／accent）、Input（44 高）、Select、Label、Card、Alert、Badge、Switch、Skeleton；全部去陰影。

**C. 共用外殼**
- 新增賣家外殼 `SellerShell`＝`SellerHeader`（賣家變體：頂欄＋頁首，**無搜尋、無購物車**）＋`SellerSidebar`（設計稿 SidebarNav 的語言）＋`StorefrontFooter`；`dashboard`／`admin`／`cms` 各一個 `layout.tsx`，導覽設定集中一個檔案。
- `blog`、`tenant` 套用既有的店面 Header／Footer。

**D. 頁面遷移（腳本輔助＋逐檔審查）**
- 移除 29 頁自帶頂欄與 `min-h-screen bg-gray-50` 外框（email／角色／登出改由賣家 Header 提供；麵包屑由側欄取代）；首頁的快速入口移進側欄並**沿用 testid 與字面**。
- 全站原始色盤 class → 設計稿 token（映射表見下）；陰影 → 邊框（白底且無邊框的元素補 `border border-rs-hairline`）。

**E. 守門（防止漂移）**：自訂 ESLint 規則，禁止原始色盤 class、陰影 class、任意 hex 色值；錯誤訊息直接指出該改成什麼。

### 3.1 色盤映射表（腳本依此轉換；`<v>` 為 `hover:`／`focus:` 等前綴原樣保留）

| 原始 class | 設計稿 token | 理由 |
|---|---|---|
| `text-gray-{700,800,900,950}` | `text-rs-ink` | 主要文字 |
| `text-gray-{500,600}` | `text-rs-ink-muted` | 次要文字 |
| `text-gray-{50…400}` | `text-rs-disabled-text` | 停用／佔位 |
| `bg-gray-50`、`bg-gray-100` | `bg-rs-bg-base`、`bg-rs-disabled-bg` | 淺底（表頭、列 hover）／停用底 |
| `bg-gray-{200,300}`、`bg-gray-{400+}` | `bg-rs-hairline`、`bg-rs-disabled-text` | 軌道、分隔 |
| `border-gray-*`、`divide-gray-*`、`ring-gray-*` | `border-rs-hairline`、`divide-rs-hairline`、`ring-rs-hairline` | 1px 實線框 |
| `bg-blue-{500,600}` | `bg-rs-primary`（`hover:` → `hover:bg-rs-primary-hover`） | 主要動作 |
| `bg-blue-{700,800,900}` | `bg-rs-primary-hover` | 加深 |
| `bg-blue-{50,100,200}`、`hover:bg-blue-{100,200}` | `bg-rs-tertiary`、`hover:bg-rs-secondary` | 主色淺底 |
| `text-blue-*` | `text-rs-primary` | 連結／強調 |
| `border-blue-{500,600}`、`ring-blue-*` | `border-rs-primary`、`ring-rs-primary` | 聚焦 |
| `bg-red-{50,100}`／`bg-red-{500,600}` | `bg-rs-error/10`／`bg-rs-error`（`hover:` → `/90`） | 錯誤底／危險動作 |
| `text-red-*`、`border-red-{200,300}`、`border-red-{400+}` | `text-rs-error`、`border-rs-error/30`、`border-rs-error` | |
| `bg-green-{50,100}`／`bg-green-{500+}`、`text-green-*`、`border-green-*` | `bg-rs-success/10`／`bg-rs-success`、`text-rs-success`、`border-rs-success/30` | |
| `bg-yellow-*`、`bg-amber-*`、`border-yellow-*`、`border-amber-*` | `bg-rs-warning/15`（實心按鈕 `bg-rs-warning`）、`border-rs-warning/40` | |
| `text-yellow-*`、`text-amber-*` | `text-rs-ink` | 警告色（#fbbf24）當文字不可讀，文字用 ink、顏色交給底與框 |
| `text-orange-*`、`bg-orange-*`、`bg-purple-*` | `text-rs-accent`、`bg-rs-accent/10`、`bg-rs-info/10` | 各 1 次，逐一人工確認 |
| `shadow*`、`hover:shadow-md` | 移除；白底且無邊框者補 `border border-rs-hairline`；`hover:shadow-md` → `hover:border-rs-primary` | 設計稿「嚴禁陰影」 |
| `bg-white`、`text-white`、`bg-black/50` | **不動** | 設計稿允許 `#FFFFFF`；遮罩是純黑透明 |

## 4. 驗證計畫（預先登記）

| # | 項目 | 預期 |
|---|---|---|
| V1 | 基線：`type-check`＝0、`lint`＝0 | ✅ 已量（開工前） |
| V2 | 每個步驟後立即 `type-check`＋`lint`（開發-編譯-測試循環）；整體完成後 `next build` | 0／0／成功 |
| V3 | `ui_adoption.py` 前後對照 | 含預設色盤的檔案 115 → 0；次數 1666 → 0；dashboard／admin／cms／blog／tenant 皆有 `rs-*` 或在共用外殼之下 |
| V4 | 守門突變：在任一檔放 `text-gray-500`、`shadow-sm`、`bg-[#123456]` | `npm run lint` 紅，訊息指出該改成什麼；移除後回綠 |
| V5 | 主題 E2E（新）：各區域切到 `green` 後頂欄背景＝`#336a29`、`blue`＝`#173a72` | 通過；突變（拿掉 `dashboard/layout.tsx`）→ 紅 |
| V6 | 全套 `make validate-e2e`（先手動重建前端，見記憶 `e2e-gate-skip-build-reuses-stale-frontend`） | 52 支規格通過；失敗者先判斷是否既有 |
| V7 | 視覺檢查：自起全棧，兩個主題各抓代表頁截圖，用眼睛看（不只信斷言） | 沒有不可讀的對比、溢出、疊在一起的導覽 |

## 5. 風險

1. **E2E 選擇器撞名**：側欄與頁面內容出現同名連結會讓 Playwright strict mode 失敗 → 側欄標籤沿用現有快速入口、移除首頁重複的入口、只渲染一份導覽（不用兩份 DOM 靠 CSS 切換）。
2. **移除頁面頂欄後留下未使用的變數／import**（`eslint --max-warnings 0` 會擋）→ 依 lint 輸出逐一清理。
3. **輸入框變高（36 → 44px）、按鈕變高（36 → 40px）**會讓密集的篩選列變擠 → 視覺檢查（V7）把關；若確實過擠，再另提 compact 尺寸，不在本輪預先加。
4. **設計稿的 error／success／muted 色對比度偏低**（例如藍色主題 error `#ef4444` 對白底約 3.8:1，低於 WCAG AA 一般文字的 4.5:1）：本輪**沿用設計稿 token、不另行加深**，列入限制；要改是改 `globals.css` 一處。
5. **映射是機械式的**：例如 `text-gray-600` 一律變 muted，主題不同時對比會變。V7 抽查，不聲稱逐頁視覺等價。

## 6. 我替使用者做的決定（可一句話推翻）

1. **「統一所有 UI」＝落實 FRD D1**（階段三＋買家頁內容區＋blog＋開店申請），不是只改買家頁。
2. **後台用側欄取代各頁自帶的麵包屑頂欄與首頁的快速入口 chips**；email／角色／登出移到共用賣家 Header。testid 與標籤字面沿用，E2E 不需改選擇器。
3. **賣家 Header 不含搜尋與購物車**（FRD 寫「seller 變體 header」），避開 `button[type=submit]` 撞名。
4. **依設計稿尺寸**：按鈕 40／輸入框 44，而不是維持 shadcn 的 36。
5. **去陰影**，白底卡片補 1px 框（設計稿明文）。
6. **不覆寫 Tailwind 預設色盤變數**（那樣能零改動讓 `bg-blue-600` 變主題色，但程式碼語意說謊），而是逐 class 遷移＋ lint 守門。
7. **守門用自訂 ESLint 規則**（專案既有 lint 就在 pre-commit、CI、pre-push 都跑，不新增基礎設施）。
8. **`DEF-453` 順延**，不是跳過；追蹤表原樣保留。
9. **`blog` 搬進 `(storefront)`、`tenant/apply` 搬進 `(auth)`**（`git mv`，網址不變）：route group 不影響 URL，直接套用既有 layout，不複製 layout 程式碼。`tenant/apply` 原本頁面自己的登入檢查與 `AuthGuard` 行為相同（都導去 `/login?redirect=`），所以拿掉重複的檢查。
10. **懸浮客服鈕改成設計稿規格**（`rounded-md`、padding 10×20、18px，原本是全圓角膠囊）：設計稿的 FAB 是 8px 圓角；賣家外殼不放這顆鈕（會蓋住資料表右下角，訊息中心已在側欄）。
11. **標題字重**：84 處 `<h1>`～`<h6>` 上的 `font-bold` 改成 `font-semibold`（設計稿「600 標題、700 價格」）；非標題的 25 處 `font-bold` 沒動。
12. **補回被移除頂欄吃掉的兩個出口**：`features` 頁加「← 返回店鋪詳情」；側欄「定價日曆」標籤改成「定價日曆預覽」（沿用原連結文字，E2E 依賴）。
13. **修改了 4 支既有 E2E 的定位方式**（不弱化斷言，見 §7.4）：`at-embed-card-status`（限定在卡片內）、`at-review-reply-real`（限定在 main 內）、`at-m15-e2e`（active 樣式斷言改成新 token，2 個案例 4 處）。
14. **`DEF-454` 只登記、不修**：共用店面 Header 在 390px 橫向溢出（既有）。

## 7. 結果

### 7.1 改了什麼

| 層 | 內容 |
|---|---|
| A 地基 | `globals.css`：shadcn 語意 token 全部指向 `--rs-*`（`@theme inline`）；`--radius-md` 8px／`--radius-lg` 12px；預設邊框色 `--rs-border`；body／標題／焦點／捲動條／`color-scheme: light`；新增 `--rs-skeleton` |
| B 基礎元件 | `ui/` 的 Button、Input、Select、Label、Card、Alert、Badge、Switch、Skeleton 改成設計稿規格並去陰影。順帶修掉既有缺陷：`Alert` 的 `destructive` 變體寫 `bg-destructive`／`text-destructive`，但專案從沒定義過 `--color-destructive`，所以從來沒上過色（`admin/tenants/[id]/review` 也有 2 處同款） |
| C 外殼 | 新增 `SellerShell`＝`SellerHeader`＋`SellerSidebar`＋Footer 與 `sellerNav.ts`（27 項導覽，沿用原快速入口的標籤與 testid）；`dashboard`／`admin`／`cms` 各一個 `layout.tsx`；`StorefrontFooter` 加 `showChatFab` 並校正 FAB 規格 |
| D 頁面遷移 | `migrate_classes.py`：**115 個檔案、1657 處色盤 class、94 處陰影、補 66 處邊框**（冪等：第二次乾跑 0）；移除 28 頁自帶頂欄（31 個 `<nav>`）及其不再使用的 import／變數；統一 `<main>` 外框（24＋2＋4＋1 處）；首頁兩個「快速入口」移進側欄；84 處標題字重 |
| E 守門 | `eslint-rules/design-tokens.mjs`（自訂規則）＋`eslint.config.mjs`；新 E2E `at-ui-design-base.spec.ts` 7 個案例 |

採用統計（`ui_adoption.py`，前後對照）：

| 指標 | 之前 | 之後 |
|---|---|---|
| 含 Tailwind 預設色盤 class 的檔案／次數 | 115 個／1666 次 | **0／0** |
| `shadow*` | 106 次 | **0** |
| 使用設計稿 token（`rs-*`）的檔案 | 26／164（16%） | **144／170（85%）**；其餘 26 個是沒有任何顏色 class 的檔案（layout、純邏輯頁） |
| `(auth)` 買家頁使用 token 的檔案 | 3／28 | **29／29** |
| `app/dashboard`、`admin`、`cms` 使用 token 的檔案（分母含本輪新增的各一個 `layout.tsx`） | 0／49、0／9、0／4（`.tsx` 檔，不含測試） | 45／50、9／10、4／5（`blog` 3 檔併入 `(storefront)`、`tenant` 1 檔併入 `(auth)`，一併計入各自群組） |
| `page.tsx` 在共用 layout 之下 | 只有 `(storefront)`、`(auth)` 兩個 route group 有外殼 | **104 個 page 中 103 個**（`admin` 9、`dashboard` 49、`cms` 4、`(auth)` 28、`(storefront)` 13）；唯一例外 `oauth/callback`（轉場頁，刻意豁免） |

（1666 → 1657 的差 9 次：是我改寫 `ui/Alert`（6 個）與 `ui/Badge` warning（3 個）時就消掉的，統計是在那之前量的。）

### 7.2 驗證結果（對照 §4 預先登記）

| # | 結果 |
|---|---|
| V1 基線 | `type-check` 0、`lint` 0 |
| V2 | 每個步驟後立即 `type-check`＋`lint`；全部 0／0。`next build` 成功（多次）。搬動 `blog`／`tenant` 後 `type-check` 曾報 `.next/types` 的舊路徑錯誤——那是上一次 build 產生的過時型別檔，不是原始碼；`next typegen` 後消失 |
| V3 | 見上表 |
| V4 守門突變 | 放入一個暫存檔含 8 個刻意違規（`text-gray-500`、`hover:bg-blue-600`、`focus:ring-red-500/50`、`border-b-gray-200`、`shadow-sm`、`bg-[#123456]`、template 內的 `bg-green-100`、`md:shadow-lg`）→ lint **剛好 8 個錯誤**；同一個檔案裡的允許寫法（`shadow-none`、`bg-white`、`text-white`、`bg-black/50`、`rs-*`）0 誤報；刪除後全站 lint 回 0，沒有殘留 |
| V5 主題 E2E | 新規格 7 個案例通過；首版 UI-02 紅過一次——是規格自己的缺陷（`selectOption` 發生在 hydration 完成之前，React 之後把值重設回 `blue`），改用 `toPass` 重試；之後連跑 3 輪 9／9 穩定。**突變驗證（預先登記）**：M1 刪 `admin/layout.tsx`、M2 讓 CMS 頂欄寫死藍色、M3 把 blog 搬出 `(storefront)`、M4 移除側欄自動捲動 → **UI-04／UI-05／UI-01／UI-06 紅，UI-02／UI-03 綠，與登記完全一致**；UI-06 紅的訊息是『目前頁的下緣不能被側欄裁掉：期望 ≤ 743、實際 1084』（前提檢查通過）。還原後三個檔案與 blog 目錄 SHA-256 全部相符，`git status` 雜湊與突變前相同 |
| V6 完整守門 | 見 §7.4 |
| V7 視覺 | 一次性探針（已刪、`git status` 確認無 `zz-` 殘留）：真實播種一間店（商品＋房源＋一筆已付款訂房＋一筆商品訂單），匿名／買家／店主／管理員四種身分合計 47 條路由，各在藍、綠兩個主題下造訪（47×2＝**94 次造訪**）：**0 崩潰、0 pageerror、0 個 5xx、沒有任何頁面回 4xx／5xx**；console 錯誤只有 admin 開 `/cms` 時 posts／media 回 403（admin 沒有店鋪，既有行為）。我實際用眼睛看了 8 張截圖（店主儀表板、訂單頁、新增商品表單、綠色主題成員頁、買家訂單詳情、管理台租戶頁、ERP 庫存頁、手機展開側欄），找到 3 個問題並處理（§7.5） |
| 稽核（V8，執行中新增） | 用 TypeScript AST 對 HEAD 版逐一列出被移除的 31 個 `<nav>` 裡的 71 個互動元素：其餘都是『麵包屑上層連結／登出』，唯一的功能連結是 `pricing/rules` → `pricing/calendar`（已補回），另 `features` 頁失去回店鋪詳情的出口（已補回） |

### 7.3 為什麼是『核心 6 項』而不只是 grep

- 色盤與陰影歸零是**靜態**證據，換主題真的全站生效是**執行期**證據（V5）；兩者缺一不可：靜態乾淨不代表頁面套了外殼，套了外殼也不代表顏色來自 token。
- 突變驗證證明 E2E 和 lint 會紅；視覺檢查證明『綠燈』沒有騙人（它抓到了斷言看不到的 3 件事）。

### 7.4 完整守門（`make validate-e2e`，`E2E_GATE_SKIP_BUILD=1`，乾淨資料庫）

**第一次**（對含新外殼、尚未調整測試的建置）：301 個案例，**294 通過、5 失敗、2 未執行**（9.5 分鐘）。5 個失敗**全是本輪改動造成**，沒有既有 flaky：

| 案例 | 根因 | 處置 |
|---|---|---|
| `E2E-EMBED-02` | blog 套用店面 Footer 後，Footer 的『聯絡客服』（`/notifications`）與嵌入卡片 CTA（`/contact`）同名，`getByRole('link')` 命中 2 個（strict mode） | E2E 限定在卡片 `<article>` 內查詢（比原本更精確） |
| `E2E-PRICING-CAL-02` | **功能遺失**：定價規則頁頂欄裡唯一通往日曆的連結被我一起移除 | 側欄標籤改成『定價日曆預覽』（沿用原連結文字） |
| `E2E-M15-001`『CMS 列表頁篩選功能』、`E2E-M15-003`『媒體庫篩選功能』 | 測試斷言實作細節 `toHaveClass(/bg-blue-100/)`；active 樣式已是設計稿 token `bg-rs-tertiary` | 斷言改成 `/bg-rs-tertiary/`（4 處＋2 處註解） |
| `E2E-RVREPLY-04` | 賣家 Header 的主題切換 `<select>` 也是 `combobox`，頁面變成 2 個 | 限定在 `main` 內 |

修正後只跑受影響的規格（5 個失敗案例＋新規格＋這次動到的 `features` 頁相關規格）：**34／34 通過**。之後又補了 UI-07（賣家 Header 登出）與標題字重調整，所以**最終守門重跑一次**（中止了一次進行中的守門，因為它是針對字重調整之前的建置）：

**最終守門**（對最終程式碼：含 UI-07、標題字重、被補回的兩個出口、4 支調整過定位的既有規格）：**302 個案例全部通過（7.7 分鐘）**，0 失敗、0 跳過、0 未執行；schema 對齊（`ddl-auto=validate`＋Flyway）。302 ＝ 既有 295 ＋ 新規格 7（第一次守門的 301 是 295 ＋ 當時的 6 個）。後端記錄到認證端點被限流 **15 次**，E2E 輔助函式依 `Retry-After` 重試後全部自癒（第一次守門是 0 次；**沒有查為什麼這次比較多**，推測與執行時序有關，未驗證）。

### 7.5 視覺檢查找到的 3 個問題

1. 頂欄的帳號文字被截斷，角色 `(STORE_OWNER)` 看不到 → `max-w-[260px]` 放寬到 360px。
2. **目前頁在側欄下半段時，高亮看不到**（側欄 27 項、自己會捲動；ERP 庫存、店鋪成員管理都在畫面外）→ 目前頁變動時把它捲到側欄中間（只捲側欄本身，不用 `scrollIntoView`）；新增 E2E-UI-06（含『側欄內容確實比可視高度長』的前提檢查）。
3. 390px 寬度時頂欄的『返回賣場』被擠成兩行 → `flex-none whitespace-nowrap`。

另外，手機寬度（390×844）探針：賣家外殼（`/dashboard`、`/admin`，收合與展開）`scrollWidth` 等於 390，沒有溢出；**共用店面 Header 溢出**（`/` 577、買家 `/orders` 645）——`StorefrontHeader.tsx` 在本輪零修改，是既有，登記 `DEF-454`。

## 8. 過程中的失誤與修正

1. **把 make 的 exit code 2 當成「環境／前置錯誤」**：守門腳本的定義是 0／1／2＝綠／測試失敗／環境錯誤，但 `make` 在 recipe 失敗時自己會回 2，我沒讀日誌就下了結論。讀日誌才看到是 5 個測試失敗。**教訓：背景任務的 exit code 先放著，讀日誌再說**（記憶裡已經有同類提醒，這次是 make 的變體）。
2. **開工時『E2E 對頂欄依賴小』的 grep 不完整**（§1.3）：只找了標籤與 testid，漏了四類耦合——①同名連結（Footer『聯絡客服』）、②同 role 元素數量（主題切換 `<select>` 也是 `combobox`）、③斷言實作 class 名（`bg-blue-100`）、④頁面頂欄裡的功能連結（『定價日曆預覽』）。我在 §5 風險 1 預測過撞名，但只想到第一類。
3. **移除頂欄時只核對『主流骨架』，沒有逐元素稽核**：因此刪掉了 `pricing/rules` 通往日曆的連結、`features` 頁回店鋪詳情的出口。稽核腳本第一版還犯了同一種錯——把所有指向 `/dashboard/...` 的連結都當成『上層連結』放過，是人工讀 71 個元素的清單才抓到平行連結。**教訓：機械分類器的『預期內』桶要人工複核一遍，尤其是放過去的那一邊。**
4. **E2E-UI-02 首版在 hydration 前就 `selectOption`**（§7.2 V5）：是規格的缺陷，不是外殼的缺陷。
5. **統計數字差 9 次追了一輪**：基線 1666 與遷移腳本的 1657 對不上，我先懷疑兩支腳本的正則，逐一排除後才發現是改寫 `ui/Alert`、`ui/Badge` 前後的差異。
6. **誤判 `#123`／`#125` 是寫死色碼**（開工前就更正）：腳本把 HTML 實體 `&#123;` 當成色碼。
7. 小失誤：把暫存輸出寫到 `/tmp/dummy`（已刪，規則是用 scratchpad）；`Write` 時把 scratchpad 路徑打成 `/private/tmp/claude-501/-Users-wuweihong/…`，在 scratchpad 之外多建了一個目錄（只含一個 `x`，已用 `rm`＋`rmdir` 清除，沒有影響專案）；用 BSD `sed` 的 `\s` 讓輸出顯示成亂碼（只是顯示，數字沒錯）。

## 9. 限制（誠實揭露）

- **沒有逐頁人工驗證 104 頁**：視覺檢查是機械檢查 94 次造訪（崩潰／pageerror／5xx）加上我看的 8 張截圖。映射是機械式的（例如 `text-gray-600` 一律 `text-rs-ink-muted`），在綠／蓮田／柔彩主題下 muted 色更淡、對比更低；我只看了藍色與綠色兩個主題，**蓮田、柔彩、橄欖沒有看過畫面**（色票已逐項比對一致、E2E 只切了 blue↔green）。
- **對比度**：設計稿的 `error`、`success`、`ink-muted` 對白底低於 WCAG AA 一般文字門檻，沿用設計稿未加深（§5 風險 4）。
- **只有 Chromium**。
- **密集資料頁沒看到**：探針資料沒有 ERP 供應商／庫存／採購單，所以輸入框 44px、按鈕 40px 在『有大量資料的表格』裡擠不擠沒看過。
- **手機寬度只測 5 個畫面**；店面 Header 溢出是既有（`DEF-454`）。
- **『統一』的範圍**：顏色、圓角、陰影、字重（標題）、外殼、基礎元件規格。**沒有統一**：非標題的 25 處 `font-bold`、各頁內部的版面結構（有的用表格、有的用卡片）、文案、設計稿 12 個元件中沒有使用處的（Avatar、IconButton）。
- **懸浮客服鈕、`:focus-visible` 外框、`StorefrontHeader` 本身**：只校正了 FAB；Header 的版面沒動。
- 沒跑 `validate-schema-doc`（無 schema 變更）、沒跑後端 `mvn verify`（**本輪沒有任何後端變更**，`git status` 確認）。
- 本輪**沒有**做 `DEF-453` 的補量測；上輪總結還等你決定的項目也沒碰。

## 10. 狀態

- **狀態**：實作、新 E2E（7 案例）、突變驗證、完整本地 E2E 守門（302／302）、文件均完成；追蹤表登記 `DEF-454`；`AI-1904`（FRD 階段三）本輪結案（見 FRD §7.1）。**沒有後端變更**。
- **push**：依 2026-10-29 長期授權直接 push：`3e03d234..0d69f9db`（連同 Sprint 327 留在本機的純文件回填 commit `89623513`）；沒有詢問、沒有使用 `--no-verify`；pre-commit（`eslint --fix`＋`tsc`＋secret 掃描）與 pre-push 輕量守門（後端單元〔act〕、schema 漂移、前端）都一次通過。
- **雲端 CI 全綠**：run [42064093164](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/42064093164)，三個 job 皆 success（後端單元 2:42、前端 lint＆build 1:07、後端整合 8:04，整個 run 約 10 分 50 秒），以 `gh run view --json` 交叉確認 `headSha` 與本機 commit 一致；下載整合 job 的完整日誌（約 69 MB）核對 **整合 1027／0**（140 個類別沒有任何失敗；與 Sprint 327 相同，符合沒有後端變更）、Checkstyle 0 違規、`BUILD SUCCESS`。這個 workflow 的三個 job **不含 Playwright**，E2E 的證據是本機的 302／302。
  - 兩個工具上的小失誤：①`gh run watch` 第一次因網路連線被重置回 1（日誌是 `failed to get run … read: ca…`，**不是 CI 失敗**），改用 `gh run view --json` 確認後重新 watch；②`gh api …/logs` 沒加 `--allow-escape-sequences`，得到 0 bytes 的日誌檔，而我的核對腳本讀空檔也「成功結束」——記憶裡早就有這條，我又忘了一次。
- **本回填 commit 只改文件，刻意不 push**，隨下一輪的 push 帶出（沿用 Sprint 322～327）。
- **收尾 Docker 清理**（所有 Docker 用途結束之後、雲端 CI 還在跑時）：盤點容器 0、映像 3 個（核准基底，無懸空）、卷 13＝8 匿名＋5 具名（1.178GB）、建置快取 25 筆 1.889GB；逐一刪 8 個匿名卷（0 失敗），卷→928MB，`docker image prune -f` 回收 0B；保留 5 個具名卷（`act-toolcache`、`e-commerce_minimax_maven_cache`／`minio_data`／`postgres_data`／`redis_data`）與 3 個核准基底映像；建置快取只回報、不刪。Docker Desktop 開工前就在跑，未關閉。匿名卷比平常的 4 個多：這輪跑了 3 次本地 E2E 守門（含 1 次中止的）、我自己起的 2 次全棧、1 次 pre-push 守門；沒有逐一驗證來源。
- **仍等你決定**：沿用 Sprint 327 的清單（`DEF-294`、`DEF-449`、`DEF-419`、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取、Sprint 325 的兩個觀察、退款 fail-open 的取捨）。**本輪新增兩件請你看一眼的 UI 事項**：①蓮田／柔彩／橄欖三個主題我沒看過畫面（色票逐項一致、E2E 只切了藍↔綠）；②設計稿的 `error`／`success`／`ink-muted` 對白底對比度偏低，我沿用 token 沒加深——要不要在設計稿層面加深，是你的設計決定。
- **下次說『請繼續』我可以直接做**：`DEF-453` 的補量測（Sprint 327 總結的下一步，本輪順延）。`DEF-454`（店面 Header 窄螢幕怎麼收）是設計題，我可以先量測影響範圍並提出方案，但頂欄怎麼收要你定。
