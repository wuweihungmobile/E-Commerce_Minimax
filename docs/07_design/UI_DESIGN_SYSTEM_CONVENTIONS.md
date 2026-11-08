# UI 設計系統開發規範（以「意象若水 Homepage 設計稿」為 BASE）

> **建立**：Sprint 328（2026-11-05）
> **適用**：`frontend/` 全部 UI（買家、店面、賣家後台、平台管理台；原本的 CMS `/cms/*` 已於 Sprint 332 併入賣家後台）
> **權威來源**：`docs/01_requirements/Design_UI/Homepage (standalone) _OK.html`
> **守門**：`frontend/eslint-rules/design-tokens.mjs`（`npm run lint`，pre-commit／CI／pre-push 都會跑）
> **背景**：[SPRINT_328_PLAN.md](../04_planning/SPRINT_328_PLAN.md)、[HOMEPAGE_TEMPLATE_INTEGRATION_FRD.md](../01_requirements/HOMEPAGE_TEMPLATE_INTEGRATION_FRD.md)

---

## 1. 什麼是 BASE

設計稿是 Claude Design 匯出的「Bundled Page」，**只有一個畫面：賣場首頁**（頂欄、含搜尋與購物車的頁首、左側分類、排序列、商品網格、分頁、頁尾、懸浮客服）。購物車、結帳、訂單、後台沒有對應畫面——所以「以它為 BASE」的意思是：**沿用它的 token、外殼與元件規格**，其餘畫面由這些規格推導，不另創視覺語言。

設計稿內含的設計系統有 **12 個元件**（Button、IconButton、Pagination、Avatar、Badge、ProductCard、Skeleton、Input、SearchBar、SidebarNav、SortToolbar、Icon）。查規格的方法：瀏覽器直接開該 HTML；或解包——`<script type="__bundler/manifest">` 是 base64＋gzip 的資源表、`__bundler/template` 是 JSON 字串化的 HTML（色票在 `[data-theme]` 區塊、元件樣式在 `fadb426d…` 這包 JS 內的 `.rs-*` 規則）。

## 2. 顏色：一律走 `--rs-*` token

5 套主題（`blue` 預設、`green`、`lotus`、`pastel`、`test05`）各 21 個 token，定義在 `frontend/src/app/globals.css`，**與設計稿逐項一致**。`<html data-theme>` 切換，所有用到 token 的畫面即時跟著變。

| 用途 | class | 備註 |
|---|---|---|
| 頁面底色 | `bg-rs-bg-base` | body 已套用 |
| 卡片／面板表面 | `bg-rs-surface`（`bg-white` 等值，允許） | |
| 主要文字 | `text-rs-ink` | |
| 次要文字 | `text-rs-ink-muted` | |
| 停用／佔位文字 | `text-rs-disabled-text`、`placeholder:text-rs-ink-muted` | |
| 框線、分隔線 | `border-rs-hairline`、`divide-rs-hairline` | 全域預設邊框色也是它 |
| 主要動作 | `bg-rs-primary hover:bg-rs-primary-hover text-white` | |
| 主色淺底（選中、標籤） | `bg-rs-tertiary` | 側欄目前頁底色 |
| 輔助色 | `bg-rs-secondary` | |
| 強調（促銷、購物車徽章） | `bg-rs-accent`、`hover:bg-rs-accent-hover` | |
| 錯誤／成功／警告／資訊 | `rs-error`／`rs-success`／`rs-warning`／`rs-info` | 底 `bg-rs-error/10`、框 `border-rs-error/30`、字 `text-rs-error` |
| 頂欄／頁首 | `bg-rs-topbar text-rs-topbar-text`／`bg-rs-header` | |
| 骨架屏 | `bg-rs-skeleton` | 不隨主題變 |

- **`rs-warning` 是亮黃，不可當文字**（白底上不可讀）：警告訊息文字用 `text-rs-ink`，顏色交給 `bg-rs-warning/15` 與 `border-rs-warning/40`。
- `#FFFFFF` 允許（`bg-white`、`text-white`）；遮罩用 `bg-black/50`。**其他顏色不得寫死 hex**（設計稿鐵律）。
- shadcn 語意 class（`bg-primary`、`text-muted-foreground`、`border-input`、`text-error`…）已在 `globals.css` 改成指向 `--rs-*`，可以用；新程式碼優先寫 `rs-*`。注意 shadcn 的 `accent` 是「hover 淺色強調」（對應 `--rs-tertiary`），**不是**設計稿的橘色 accent（那個是 `rs-accent`）。
- **不要覆寫 Tailwind 預設色盤變數**（例如把 `--color-blue-600` 指到主題色）：那樣 `bg-blue-600` 會在綠色主題下變綠，程式碼語意說謊。

## 3. 版面、字級、圓角、層次

| 項目 | 規格 | 在程式碼裡 |
|---|---|---|
| 字族 | Inter ＋ 系統繁中字型堆疊（Turbopack 無法 self-host CJK next/font） | `layout.tsx`、`--font-sans` |
| 字級 | h1 36／h2 30／h3 24／h4 20／h5 18／h6 16／body 16／sm 14／xs 12 | **Tailwind 預設字級恰好等於它**：`text-4xl`…`text-xs` |
| 行高／字重 | 標題 1.35、內文 1.65；400／500（按鈕、標籤）／600（標題）／700（價格） | `globals.css` 的 `@layer base`。**標題用 `font-semibold`**（Sprint 328 把 84 處 `<h1>`～`<h6>` 上的 `font-bold` 改掉）；`font-bold` 只留給價格／金額強調。非標題的 `font-bold` 目前還有 25 處（統計數字、品牌字等），沒有逐一核對、也無法用 lint 區分，屬於未強制的部分 |
| 間距 | 8px 基準 | Tailwind 預設間距（4 的倍數） |
| 圓角 | sm 4（徽章、分頁）／md 8（按鈕、輸入框）／lg 12（卡片、影像）／full | `rounded-sm`／`rounded-md`／`rounded-lg`／`rounded-full`（`globals.css` 已把 md、lg 覆寫成設計稿值） |
| 容器 | 最大 1440、左右 24、側欄 240、欄距 24（頁面級 32） | `max-w-[1440px] mx-auto px-6`、`grid-cols-[240px_1fr]` |
| **層次** | **嚴禁陰影**，只用 1px 實線框與色差分層 | 卡片＝`<Card>`（`bg-rs-surface border border-rs-hairline`）；**不得出現 `shadow-*`** |
| **覆蓋層（z-index）** | 頁首 70 < `Dialog` 80 < `ConfirmDialog` 90 < Radix `Select` 彈出面板 95 < `Toast` 100；聊天懸浮鈕 60。Sprint 329 前 Modal 是 `z-50`、頁首是 `z-[70]`，所以 Modal 開啟時頁首仍浮在遮罩之上、Select 在 Dialog 裡會被蓋住 | 集中在 `components/ui/{dialog,confirm-dialog,select,toast}.tsx`；頁面不自己寫 `z-*` |
| 焦點 | `:focus-visible` 2px 主色外框、offset 2px | 全域規則，不要再寫 `focus:ring-*` |
| 轉場 | `all 0.2s ease-out` | `transition-colors` |
| 模式 | 只支援淺色 | `color-scheme: light`，不做 dark mode |

## 4. 元件目錄（`frontend/src/components/ui/`）

> **這是視覺決定唯一允許存在的地方**（見 §9）。頁面與領域元件只組合下列元件，不自己寫視覺 class。Sprint 329 前這個目錄只有 11 個原子；缺的「配方」層（標題、表格、狀態、回饋…）是 Sprint 328 換皮要動 144 個檔案的原因（[ADR-001](../02_architecture/adr/ADR-001-ui-design-system-layering.md)）。

### 4.1 原子

| 元件 | 規格 |
|---|---|
| `Button` | 高 sm 32／預設 40／lg 48／`icon` 40 方形／`inline`（行內，高度跟著文字）；radius-md；字重 500；變體 `default`（solid 主色）、`accent`、`destructive`、`success`、`warning`（文字用 ink）、`outline`、`secondary`、`ghost`、`link`（主色）、`linkDestructive`、`linkSuccess`、`linkNeutral`（表格列內的文字連結式按鈕，搭配 `size="inline"`）；停用＝disabled 底與字（不是 opacity）。連結要長成按鈕：`<Link className={buttonVariants({ variant, size })}>` |
| `Input`、`Textarea`、`NativeSelect`、`SelectTrigger`（Radix `Select`） | **單一規格 `field-styles.ts`**：單行固定高 44（`h-11`）、padding 8×16、1px 邊框（`--rs-border`）、radius-md、16px；聚焦＝主色邊框＋外框；`aria-invalid`＝error 色；停用＝disabled 底與字。`Textarea` 取消固定高、最小高 80。`NativeSelect` 是 `<select>` 的相容替代（`value`／`onChange(e)` 不變，Playwright 可 `selectOption`）；要自訂下拉面板才用 Radix 版 |
| `Checkbox`、`Radio` | 原生控制項＋`accent-color: --rs-primary`（保留原生鍵盤與無障礙行為） |
| `Label` | 14px／500／`text-rs-ink`。要上下排列加 `block mb-1`（版面 class 寫在頁面） |
| `Card`（`asChild`） | `rounded-lg`、1px 邊框、`bg-rs-surface`、無陰影。整張卡片是連結：`<Card asChild><Link href=…>…</Link></Card>` |
| `Alert` | `default`／`destructive`／`success`／`warning`，狀態色 token＋透明度，14px；頁面上的錯誤／成功／警告**橫幅**一律用它 |
| `Badge` | 12px／500／padding 4×8／`rounded-sm`；語意變體＋設計稿變體（`promo`、`logistics`、`feature`、`rating`、`count`…） |
| `Chip` | 可切換的小按鈕（篩選列、分頁籤、設施標籤）。選取態用 `selected`（輸出 `aria-pressed`），**不要用條件式 class 自己拼，E2E 也不要斷言 class 名** |
| `Switch`、`Skeleton`、`Pagination` | 同上述 token |

### 4.2 配方（頁面反覆出現的組合）

| 元件 | 取代的手刻 | 備註 |
|---|---|---|
| `PageTitle`、`PageDescription` | `<h1 className="text-2xl font-semibold…">`、`<p className="mt-1 text-sm text-rs-ink-muted">` | 1:1 取代元素，版面 class（`mb-6`、`flex`）仍寫在頁面 |
| `Table`、`TableHeader`、`TableBody`、`TableFooter`、`TableRow`、`TableHead`、`TableCell` | 10 張手刻 `<table>`（167 個 `thead/tbody/tr/th/td`） | `Table` 自帶外框（1px、`rounded-lg`）與橫向捲動容器；**不要再自己包 `overflow-x-auto` 或卡片容器**。表頭 12px／500／次要色／uppercase、儲存格 padding 12×16、列 hover 底色。欄位語意（對齊 `text-right`、次要欄 `text-rs-ink-muted`）寫在頁面 |
| `LoadingState`、`EmptyState`、`ErrorState` | 「載入中...」置中區塊、各式「尚無…」空狀態、載入失敗畫面 | `LoadingState` 預設文字「載入中...」；`EmptyState` 有 `icon`（emoji／圖示）、`title`、`description`、`action`；`ErrorState`（Sprint 332，DEF-468）`role="alert"`、預設標題「載入失敗，請稍後再試」、`onRetry` 有給才顯示「重新載入」鈕。**「失敗」與「沒有資料」必須是兩個畫面**——把失敗畫成 `EmptyState`，使用者會以為真的沒有資料、也不會重試。**Sprint 333 補三條**：①「載入中」也不能畫成「尚無…」（訊息中心的對話清單原本載入中就顯示「尚無對話」，DEF-471）；②**只有確定的結果才能說成「不存在／已下架」**——依 `errorInfo?.status` 分流（內嵌商品卡只有 404／400 才是「此商品已下架」，其餘是載入失敗＋重試，DEF-473）；③`ErrorState` 可用 `className` 縮成區塊內嵌的版本（`cn` 會讓 `py-4` 取代預設的 `py-12`）；表單內的載入失敗沿用 `Alert`＋「重新載入」鈕，並明講是哪一個清單失敗（採購單的供應商／商品下拉，DEF-472）。**這三條不是特例，是同一個原則：使用者看到的畫面必須只說「我們確定的事」** |
| `StatusBadge`＋`lib/status.ts` | 16 份各自為政的 `getStatusBadge` | `<StatusBadge domain="post" status={…} />`。「中文標籤＋語意色」集中在登錄表，**頁面不決定顏色**。語意規則：草稿＝outline、待審／審批中／部分到貨＝warning、啟用／已發布／已核准／已到貨／上架中＝success、停用／已封存／已下架＝secondary、駁回／取消／停權／終止＝destructive、已提交＝default。新增狀態只在 `status.ts` 加一列（E2E-UIDS-08 會驗證規則）。訂單、訂房、退貨、成員、付款、物流的狀態維持在各自的 `services/*.ts` |

### 4.3 回饋與覆蓋層

| 元件 | 用法 | 取代 |
|---|---|---|
| `toast`／`<Toaster />` | `toast.success('已儲存')`、`toast.error('刪除失敗')`、`toast.info(…)`。命令式，事件處理器與 `catch` 內可直接呼叫；成功／資訊 4 秒、錯誤 6 秒後自動消失，可手動關閉；錯誤 `role="alert"`、成功／資訊 `role="status"`；`data-testid="toast"`、`data-kind` | 113 個原生 `alert()` |
| `confirmDialog()`／`<ConfirmDialogHost />` | `if (!(await confirmDialog({ message, confirmLabel: '刪除', destructive: true }))) return`。取消、ESC、點遮罩一律回 `false`；`data-testid="confirm-dialog"`／`confirm-dialog-confirm`／`confirm-dialog-cancel` | 19 個原生 `confirm()`（刪除、取消確認） |
| `Dialog`（Radix） | `<Dialog open onOpenChange={…}><DialogContent><DialogTitle>…</DialogTitle>…</DialogContent></Dialog>`。焦點鎖定、ESC、`aria-modal`、遮罩 `bg-black/50`。**一定要有 `DialogTitle`**；沒有說明文字時在 `DialogContent` 傳 `aria-describedby={undefined}` | 5 個手刻 `fixed inset-0` Modal |

`<Toaster />` 與 `<ConfirmDialogHost />` 已在根 layout（`app/layout.tsx`）各掛一次，**不要在別處再掛**。

## 5. 外殼：每個頁面都在其中一個之下

| 區域（URL） | 外殼 | 實作 |
|---|---|---|
| 店面（`/`、`/products`、`/listings`、`/rooms`、`/stores/*`、`/blog/*`、`/privacy`、`/terms`） | `StorefrontHeader`＋`StorefrontFooter` | `app/(storefront)/layout.tsx` |
| 買家頁、開店申請（`/cart`、`/checkout`、`/orders`、`/account`、`/tenant/apply`…，含登入／註冊） | 同上＋`AuthGuard` | `app/(auth)/layout.tsx` |
| 賣家後台 `/dashboard/*`、平台管理台 `/admin/*` | `SellerShell`＝`SellerHeader`（無搜尋、無購物車）＋`SellerSidebar`＋Footer（無懸浮客服） | 各自的 `app/<區域>/layout.tsx`；導覽設定集中在 `components/layout/sellerNav.ts` |
| `/oauth/callback/*`、`/health` | 無（轉場頁／健康檢查） | |

**新增頁面**：把檔案放進上表對應的目錄就會自動套用外殼——**不要再在頁面裡自己組頂欄／頁尾／`min-h-screen` 外框**。賣家端新增功能頁時，要在 `sellerNav.ts` 加一項（標籤與 `data-testid` 一次寫在那裡；同一頁不可出現兩個同名連結，Playwright strict mode 會失敗）。

**為什麼賣家 Header 不含搜尋**：搜尋鈕是 `button[type=submit]`，會被表單頁 E2E 的泛用選擇器搶走（記憶 `e2e-login-page-shared-header-collision`）。

## 6. 守門

`frontend/eslint-rules/design-tokens.mjs`（同一個 plugin，`npm run lint`，pre-commit／CI／pre-push 都會跑）。錯誤訊息會直接指出該改成什麼。

| 規則 | 範圍 | 禁止 |
|---|---|---|
| `design-tokens/no-raw-palette`（Sprint 328） | 全部 `src/**` | ① Tailwind 預設色盤 class（`bg\|text\|border\|ring\|divide\|fill\|stroke\|from\|to\|via\|outline\|decoration\|placeholder-<色>-<色階>`，含 `hover:`／`focus:` 前綴、`/透明度`）；② 陰影 class（`shadow-*`，`shadow-none` 允許）；③ class 裡的任意 hex（`bg-[#123456]`） |
| `design-tokens/no-native-dialog`（Sprint 329） | 全部 `src/**` | 全域的 `alert()`／`confirm()`／`prompt()`（含 `window.`）；同名區域變數（`alerts.map((alert) => …)`）不算 |
| `design-tokens/no-removed-utility`（Sprint 329） | 全部 `src/**` | Tailwind v3 已移除的 `bg\|text\|border\|ring\|divide\|placeholder-opacity-*`（`bg-black bg-opacity-50` 在 v4 會變成**純黑不透明**）；專案沒定義的語意色 `destructive`／`warning`／`info`（`text-warning` 沒有任何樣式）。這兩類「不報錯、只是靜靜地沒有效果」 |
| `design-tokens/no-raw-control`（Sprint 329） | `src/**`，**豁免** `components/ui`、`components/layout`、`components/theme` | 原生 `<button>`、`<input>`、`<select>`、`<textarea>`、`<label>`、`<table>`／`<thead>`／`<tbody>`／`<tfoot>`／`<tr>`／`<th>`／`<td>` |
| `design-tokens/no-ui-recipe`（Sprint 329） | 同上豁免 | 手刻「已有元件」的配方：`<h1 className="text-2xl font-semibold…">`（→ `PageTitle`）、`bg-rs-error/10`＋`text-rs-error`＋`border`（→ `Alert`）、`flex items-center justify-center h-64`（→ `LoadingState`）、`text-center py-12 text-rs-ink-muted`（→ `EmptyState`）、`fixed inset-0`（→ `Dialog`）、`bg-white border rounded-lg`（→ `Card`） |

**例外必須逐行明列**：領域專用控制項（月曆日期格、星等評分鈕、對話列、設計稿 DS 元件 `SortToolbar`／`SidebarNav` 的選項）用 `// eslint-disable-next-line design-tokens/no-raw-control -- <理由>`；目前 5 處，新增例外要在 code review 說明為什麼不能用 `Button`／`Chip`。

**要新增設計稿沒有的顏色，不是在頁面裡寫死，而是先改 `globals.css`（五套主題同步）並在設計稿層面確認。**

## 7. 原始色盤 → token 對照（Sprint 328 遷移時使用，新程式碼也可參照）

| 原始 class | 設計稿 token |
|---|---|
| `text-gray-{700…950}`／`{500,600}`／`{50…400}` | `text-rs-ink`／`text-rs-ink-muted`／`text-rs-disabled-text` |
| `bg-gray-50`／`100`／`200,300`／`400+` | `bg-rs-bg-base`／`bg-rs-disabled-bg`／`bg-rs-hairline`／`bg-rs-disabled-text` |
| `border-gray-*`、`divide-gray-*`、`ring-gray-*` | `border-rs-hairline`、`divide-rs-hairline`、`ring-rs-hairline` |
| `bg-blue-{500,600}`（`hover:` 時）／`700+`／`50…200` | `bg-rs-primary`（`hover:bg-rs-primary-hover`）／`bg-rs-primary-hover`／`bg-rs-tertiary` |
| `text-blue-*`、`border-blue-{500,600}`、`ring-blue-*` | `text-rs-primary`、`border-rs-primary`、`ring-rs-primary` |
| `bg-red-{50,100}`／`{500,600}`、`text-red-*`、`border-red-*` | `bg-rs-error/10`／`bg-rs-error`、`text-rs-error`、`border-rs-error/30` |
| `green-*` | 同上，換成 `rs-success` |
| `bg-yellow-*`／`bg-amber-*`、`border-…`、`text-…` | `bg-rs-warning/15`、`border-rs-warning/40`、**`text-rs-ink`** |
| `orange-*`／`pink-*` | `rs-accent` |
| `purple-*`／`indigo-*`／`teal-*`／`sky-*` | `rs-info` |
| `shadow*` | 移除；白底且無邊框者補 `border border-rs-hairline`；`hover:shadow-md` → `hover:border-rs-primary` |

## 8. 已知限制

- 設計稿的 `error`（藍色主題 `#ef4444`）、`success`、`ink-muted` 對白底的對比度低於 WCAG AA 一般文字門檻（4.5:1）。沿用設計稿 token、未另行加深；要改只需改 `globals.css`。
- 共用的店面 Header（頂欄一排連結＋搜尋列）在 390px 寬度會橫向溢出（既有，非 Sprint 328 造成；追蹤表 `DEF-454`）。賣家外殼在 390px 沒有溢出，側欄可收合。
- 賣家側欄有 27 項，自己會捲動；目前頁會自動捲到側欄中間。
- 設計稿沒有 dark mode，專案也不做。
- **Sprint 328 的「統一」只對走 `ui/*` 的控制項成立**：手刻與原生的輸入框、下拉、多行輸入、按鈕、表格當時沒有吃到規格（Sprint 329 實測：原生 `<select>` 17 個只有 1 個達 44px；textarea 10 個內距全是 12px；按鈕字重非 500 占 49%）。Sprint 329 起這些都走元件並由 lint 守門（§9）。
- 設計稿的 `warning`（亮黃）不可當文字色；`Button variant="warning"` 因此用 ink 文字。
- 這輪沒有的元件：`Avatar`、`IconButton`（設計稿有，專案沒有使用處）、`Tabs`（篩選用 `Chip`）、`Tooltip`、`DropdownMenu`。需要時照 §9.4 新增，不要手刻。

## 9. 分層、頁面怎麼寫、新增元件、完成定義（Sprint 329）

> 背景與證據：[SPRINT_329_PLAN.md](../04_planning/SPRINT_329_PLAN.md)（四角色檢視）；決策：[ADR-001](../02_architecture/adr/ADR-001-ui-design-system-layering.md)；需求：[FRD §9 NFR-UI-001～006](../01_requirements/HOMEPAGE_TEMPLATE_INTEGRATION_FRD.md)。

### 9.1 誰可以寫視覺 class

| 層 | 位置 | 可以寫視覺 class（顏色、圓角、框、陰影、字級、字重、padding…）嗎 |
|---|---|---|
| 值（token） | `app/globals.css` | 是（唯一定義顏色／圓角／字族的地方） |
| 原子＋配方＋回饋 | `components/ui/*` | **是（唯一允許）** |
| 外殼 | `components/layout/*`、`components/theme/*` | 是（外殼自己的版面） |
| 領域元件、頁面 | `components/<領域>/*`、`app/**` | **否**：只做語意組合與**版面 utility**（`flex`、`grid`、`gap-*`、`w-*`、`max-w-*`、`mt-*`／`mb-*`、`col-span-*`、`text-right`、響應式前綴） |

換皮成本取決於「有多少檔案知道這個設計決定」——所以頁面不該知道。

### 9.2 頁面怎麼寫

```tsx
// ✅ 頁面只說「是什麼」
<PageTitle>貼文管理</PageTitle>
<Table>
  <TableHeader><TableRow><TableHead>標題</TableHead><TableHead className="text-right">操作</TableHead></TableRow></TableHeader>
  <TableBody>{posts.map((p) => (
    <TableRow key={p.id}>
      <TableCell>{p.title}</TableCell>
      <TableCell className="text-right"><StatusBadge domain="post" status={p.status} /></TableCell>
    </TableRow>
  ))}</TableBody>
</Table>
if (loading) return <LoadingState />
if (error) return <ErrorState title="載入貼文失敗，請稍後再試" onRetry={reload} />   // 失敗不能畫成下一行的「尚無貼文」
if (!rows.length) return <EmptyState title="尚無貼文" action={<Button>新增</Button>} />
if (!(await confirmDialog({ message: '確定要刪除嗎？', confirmLabel: '刪除', destructive: true }))) return
toast.success('刪除成功')

// ❌ 頁面不要自己決定「長什麼樣」
<h1 className="text-2xl font-semibold text-rs-ink">…</h1>          // → PageTitle
<input className="border rounded px-3 py-1.5 text-sm" />            // → Input／NativeSelect／Textarea
<div className="bg-rs-error/10 border border-rs-error/30 …">…</div> // → Alert variant="destructive"
alert('已儲存'); confirm('確定？')                                   // → toast／confirmDialog
```

### 9.3 E2E 怎麼寫（QA）

- **斷言語意與實際樣式，不斷言 class 名**：選取態斷言 `aria-pressed`（`Chip`）、刪除線斷言 `toHaveCSS('text-decoration-line', 'line-through')`、通知斷言 `getByTestId('toast')`（`data-kind`）、確認框用 `confirm-dialog-confirm`／`confirm-dialog-cancel`。換皮改 class 名時，E2E 不該紅。
- **不再有原生對話框**：不需要 `page.on('dialog')`；若某個流程又出現原生對話框，是 `no-native-dialog` 應該擋下的缺陷。
- 設計系統契約在 `frontend/e2e/at-ui-design-system.spec.ts`（全程 mock、量實際 computed style）；外殼與主題在 `at-ui-design-base.spec.ts`。
- 同 role 的新元素會讓泛用查詢變多（Playwright strict mode）：`Toast` 區域掛在 `body` 末端、不在任何 `main` 內；E2E 目前沒有 `getByRole('alert'|'status'|'dialog')` 查詢，新增這類查詢前先確認不會撞到 `Alert`／`Toast`／`Dialog`。

### 9.4 新增或修改元件的流程（SD）

1. **先確認真的需要新元件**：同一個配方在 ≥3 個地方出現、或有「長相由設計決定」的語意。只出現一次的版面不要抽。
2. 放在 `components/ui/`，檔名 `kebab-case`，沿用 `forwardRef`＋`cn`＋`cva`；API 克制（優先 `variant`／`size`，避免 prop 森林）；顏色只用 `rs-*` token。
3. **把它取代的手刻配方登記進 `eslint-rules/design-tokens.mjs` 的 `RECIPES`**（若是原生元素就加進 `RAW_CONTROLS`）——這一步讓「已有元件的配方不能再手刻」成為制度。
4. 加進本文件 §4 的目錄；若它改變可觀察的規格（高度、圓角、框色…），在 `at-ui-design-system.spec.ts` 加對應的契約。
5. 用掃描腳本或 AST 遷移既有用法（先乾跑、冪等），再 `type-check`＋`lint`；**不要只改一部分頁面就停**——一半手刻、一半元件是最糟的狀態。

### 9.5 完成定義（DoD）：任何新增或修改 UI 的工作

- [ ] `npm run type-check`、`npm run lint` 為 0（含本文件 §6 的全部規則）。
- [ ] 沒有手刻配方、沒有原生表單／表格元素（領域專用控制項逐行 `eslint-disable` 並寫理由）。
- [ ] 顏色／圓角／陰影沒有寫死；狀態顏色走 `lib/status.ts`；確認與通知走 `confirmDialog`／`toast`。
- [ ] 新增頁面放在 §5 對應目錄（自動套用外殼），沒有自己組頂欄／頁尾／`min-h-screen`。
- [ ] 有 E2E 或契約覆蓋新的行為（尤其破壞性操作的確認流程）；斷言語意而非 class 名。
- [ ] 看過截圖（桌面＋390px 手機各一張），沒有溢出、疊在一起、不可讀的對比。

### 9.7 頁面標題（Sprint 332，DEF-465）

每個頁面都要有自己的 `<title>`（分頁列、瀏覽紀錄、書籤與螢幕閱讀器換頁時靠它辨認；在這之前 104 個頁面有 102 個共用同一個）。標題用 Next 的 metadata API 寫在**路由自己的資料夾**：

| 頁面是什麼 | 標題寫在哪 |
|---|---|
| Server Component（沒有 `'use client'`） | `page.tsx`：`export const metadata: Metadata = { title: '頁名' }` |
| 有 `'use client'` 的頁面（`metadata` 只能從 Server Component 匯出） | 同目錄新增 `layout.tsx`，只放 `metadata` 與 `return children`；寫法照 `app/dashboard/orders/layout.tsx` |
| 薄殼頁（只是把路由參數傳給 client 元件） | 直接做成 Server Component：`async` 頁面、`const { id } = await params` |

- **只寫「頁名」**，品牌與區域後綴由 `title.template` 加：一般頁 `頁名｜意象若水 RUOSHUI`（根 layout）、賣家後台 `頁名｜賣家後台｜意象若水 RUOSHUI`、平台管理台 `頁名｜平台管理｜意象若水 RUOSHUI`（兩個區域 layout）。常數與完整規則在 `src/lib/site.ts`。首頁是完整品牌標題（`title.absolute`）。
- **中間層的 layout 必須重新宣告 template**：Next 的規則是中間層的標題沒有 `template` 就會切斷上層傳下來的 template。所以 client 頁的 `layout.tsx` 寫 `title: { default: '頁名', template: <區域的 TITLE_TEMPLATE> }`，不要寫成 `title: '頁名'`——否則它底下的子路由（例如 `/bookings/[id]/payment/cancel`）標題會少了品牌。這個錯誤 `tsc`、`eslint`、`next build` 都看不出來。
- **同名頁面靠區域後綴區分**：「客服工單」「店鋪管理」「訂單詳情」在買家、賣家、平台各有一頁；同一區域內的頁名不可重複。
- 動態路由（`/orders/[id]`、`/blog/[slug]`）用靜態的頁型名稱（「訂單詳情」「部落格文章」）：登入後頁面的資料在伺服器端取不到（憑證在 `localStorage`），公開頁的實體標題（商品名、店名）需要伺服器端的後端網址設定，見追蹤表 DEF-475。
- **守門**：`src/architecture/page-titles.test.ts` 模擬 Next 的解析，檢查每頁自己有標題來源、解析後帶品牌、區域後綴正確、彼此不重複；`e2e/at-page-titles.spec.ts` 在真實瀏覽器驗證各種形態（含巢狀路由與換頁後標題更新）。新增頁面忘了標題、或標題與別頁重複，`npm test` 會紅燈。

### 9.6 換皮成本模型（Sprint 329 換皮演練實測）

> 演練方法與數字見 [SPRINT_329_PLAN.md](../04_planning/SPRINT_329_PLAN.md) §7.3：對 `ui/*` 與 `globals.css` 做一次合成的重新設計（圓角 8→14、主色、欄位高 44→48、頁面標題 24→30、表頭 12→14、徽章全圓角、警示框線 2px、卡片 16px），**動 7 個檔案（`globals.css`＋6 個 ui 元件）、0 個頁面**；全站 81 條路由實測：欄位 155／155、單行欄位高度 145／145、主色按鈕 55／55、`<h1>` 61／61、表頭 46／46 跟著變（未跟上的 5 個按鈕本來就不用 `radius-md`）。同一次重新設計在 Sprint 328 的程式碼上，含手刻配方、不會自動跟著變的檔案約 **119 個**（靜態估計）。
>
> **例外（目前不會自動跟著 `ui/*` 變）**：外殼（`components/layout`、`components/theme`）與 5 處逐行 `eslint-disable` 的領域控件／DS 元件（`SidebarNav`、`SortToolbar`、`MonthCalendar`、`ReviewStars`、`ConversationList`），以及頁面裡少數對 ui 元件的視覺 className 覆寫（`account` 的外框危險鈕、`inventory` 的低庫存警示卡）。它們用到 `rounded-md`、`rs-*` 時仍會跟著 token 變。

| 想改的東西 | 改哪裡 |
|---|---|
| 顏色、字族、圓角比例 | `app/globals.css`（五套主題同步） |
| 所有欄位的高度、框、字級、padding | `components/ui/field-styles.ts`（一個字串） |
| 按鈕、徽章、卡片、警示、表格、頁面標題、載入／空狀態、確認框、通知的外觀 | 各自對應的 `components/ui/*.tsx`（一個檔案） |
| 頂欄、側欄、頁尾 | `components/layout/*` |
| 某個狀態的顏色或文字 | `lib/status.ts`（一列） |
| **頁面** | **不用改**——頁面不知道這些決定 |
