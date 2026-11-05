# UI 設計系統開發規範（以「意象若水 Homepage 設計稿」為 BASE）

> **建立**：Sprint 328（2026-11-05）
> **適用**：`frontend/` 全部 UI（買家、店面、賣家後台、平台管理台、CMS）
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
| **層次** | **嚴禁陰影**，只用 1px 實線框與色差分層 | 卡片＝`bg-rs-surface border border-rs-hairline`；**不得出現 `shadow-*`** |
| 焦點 | `:focus-visible` 2px 主色外框、offset 2px | 全域規則，不要再寫 `focus:ring-*` |
| 轉場 | `all 0.2s ease-out` | `transition-colors` |
| 模式 | 只支援淺色 | `color-scheme: light`，不做 dark mode |

## 4. 基礎元件（`frontend/src/components/ui/`）

| 元件 | 規格 |
|---|---|
| `Button` | 高 sm 32／預設 40／lg 48；radius-md；字重 500；變體 `default`（solid 主色）、`accent`、`destructive`、`outline`、`secondary`、`ghost`、`link`；停用＝disabled 底與字（不是 opacity） |
| `Input`、`SelectTrigger` | 最小高 44、padding 10×16、1px 邊框、radius-md、16px；聚焦＝主色邊框＋外框；`aria-invalid`＝error 色；停用＝disabled 底與字 |
| `Card` | `rounded-lg`、1px 邊框、`bg-rs-surface`、無陰影 |
| `Alert` | `default`／`destructive`／`success`／`warning`，狀態色 token＋透明度 |
| `Badge` | 12px／500／padding 4×8／`rounded-sm`；語意變體＋設計稿變體（`promo`、`logistics`、`feature`、`rating`、`count`…） |
| `Switch`、`Skeleton`、`Label`、`Pagination` | 同上述 token |

## 5. 外殼：每個頁面都在其中一個之下

| 區域（URL） | 外殼 | 實作 |
|---|---|---|
| 店面（`/`、`/products`、`/listings`、`/rooms`、`/stores/*`、`/blog/*`、`/privacy`、`/terms`） | `StorefrontHeader`＋`StorefrontFooter` | `app/(storefront)/layout.tsx` |
| 買家頁、開店申請（`/cart`、`/checkout`、`/orders`、`/account`、`/tenant/apply`…，含登入／註冊） | 同上＋`AuthGuard` | `app/(auth)/layout.tsx` |
| 賣家後台 `/dashboard/*`、平台管理台 `/admin/*`、CMS `/cms/*` | `SellerShell`＝`SellerHeader`（無搜尋、無購物車）＋`SellerSidebar`＋Footer（無懸浮客服） | 各自的 `app/<區域>/layout.tsx`；導覽設定集中在 `components/layout/sellerNav.ts` |
| `/oauth/callback/*`、`/health` | 無（轉場頁／健康檢查） | |

**新增頁面**：把檔案放進上表對應的目錄就會自動套用外殼——**不要再在頁面裡自己組頂欄／頁尾／`min-h-screen` 外框**。賣家端新增功能頁時，要在 `sellerNav.ts` 加一項（標籤與 `data-testid` 一次寫在那裡；同一頁不可出現兩個同名連結，Playwright strict mode 會失敗）。

**為什麼賣家 Header 不含搜尋**：搜尋鈕是 `button[type=submit]`，會被表單頁 E2E 的泛用選擇器搶走（記憶 `e2e-login-page-shared-header-collision`）。

## 6. 守門

ESLint 規則 `design-tokens/no-raw-palette`（`frontend/eslint-rules/design-tokens.mjs`）檢查所有字串字面值與 template literal 的靜態片段，禁止：

1. Tailwind 預設色盤 class：`bg|text|border|ring|divide|fill|stroke|from|to|via|outline|decoration|placeholder-<色>-<色階>`（含 `hover:`／`focus:` 前綴、`/透明度`、`border-t-…`、`ring-offset-…`）；
2. 陰影 class：`shadow`、`shadow-sm|md|lg|xl|2xl|xs|2xs|inner`（`shadow-none` 允許）；
3. class 裡的任意 hex：`bg-[#123456]`。

錯誤訊息會直接建議該改成什麼。**要新增設計稿沒有的顏色，不是在頁面裡寫死，而是先改 `globals.css`（五套主題同步）並在設計稿層面確認。**

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
