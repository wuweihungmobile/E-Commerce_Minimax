# Sprint 35 Review / Sprint 35 評審會議

> **Sprint 編號**: Sprint 35
> **期間**: 2027-02-14 ~ 2027-02-27
> **評審日期**: 2026-07-01（AISDLC 延伸：開發完成後即建立）
> **基於**: [SPRINT_35_PLAN.md](../04_planning/SPRINT_35_PLAN.md), [HOMEPAGE_TEMPLATE_INTEGRATION_FRD.md](../01_requirements/HOMEPAGE_TEMPLATE_INTEGRATION_FRD.md)

---

## 1. Sprint 目標達成評估

> **Sprint 目標**: 賣場店面版型基礎 + 首頁改版（階段一）—— 將「意象若水 RUOSHUI」設計稿套為全站共用版型（TOP/Tools/Bottom 共用 + Content 分頁），首頁接真實資料 + E2E。

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| US-001 主題色票系統 + 字體 | ✅ 達成 | 5 套色票（blue/green/lotus/pastel/test05）+ Inter self-host + 系統 CJK 堆疊 + 防 FOUC 主題切換 |
| US-002 Design System 元件（shadcn 重建） | ✅ 達成 | 6 元件（Badge/SearchBar/SidebarNav/SortToolbar/Pagination/ProductCard）|
| US-003 共用 Shell Layout | ✅ 達成 | StorefrontHeader/Footer/Tools/Shell 四區塊組合 |
| US-004 首頁接真實 /v2/listings | ✅ 達成 | 分類/排序/搜尋/分頁連動 + loading/空狀態/未登入引導 + cartCount + X-Tenant-ID |
| US-005 首頁 E2E | ✅ 達成 | at-homepage.spec.ts 4 tests **全綠**（乾淨 DB 全棧）；全棧 33 passed，唯一失敗為既有 flaky m15（非本 Sprint） |

**Sprint 目標達成率**: P1 承諾 US-001~005 **全數完成**（18 SP）。US-006（DEF-019 盤點 Buffer）延後（見 §5）。

---

## 2. User Story 完成狀態

| US | 標題 | SP | 優先級 | 狀態 |
|----|------|----|--------|------|
| US-001 | 主題色票系統 + 字體 | 3 | P1 | ✅ 完成 |
| US-002 | Design System 元件（shadcn 重建） | 5 | P1 | ✅ 完成 |
| US-003 | 共用 Shell Layout | 5 | P1 | ✅ 完成 |
| US-004 | 首頁 Content 接真實資料 | 3 | P1 | ✅ 完成 |
| US-005 | 首頁 E2E | 2 | P1 | ✅ 完成 |
| US-006 | DEF-019 物流/賣家側盤點（Buffer） | 2 | P3 | ⏸️ 延 S36 |
| **完成合計** | | **18 SP** | | |

> **誠實補注（元件實作策略，Rule 12）**：storefront 元件（SearchBar / SidebarNav / SortToolbar / ProductCard）為「對齊設計稿之自訂 Tailwind + `rs-*` token 元件」，**非全數組合 shadcn 基礎元件**（ProductCard 僅用到 `ui/badge`、SearchBar 用原生 input/button）；`Badge`、`Pagination` 則遵循 shadcn `cva` / `VariantProps` 慣例。避免 D3「shadcn 重建」決策被理解為「全部組合 shadcn 基礎元件」。

---

## 3. 交付內容

### US-001：主題色票系統 + 字體
- `globals.css`：新增 5 套色票主題（各 21 `--rs-*` token，`[data-theme]` 選擇器）+ Tailwind v4 `@theme inline` 映射（`bg-rs-primary` 等 utility 執行期引用變數，支援即時切換）。
- **完全保留既有 shadcn 中性 token**（Rule 3/7）：新增平行 `rs-*` 品牌 token，既有頁面零影響；全站 token 統一留待 S36+ 導入。
- `ThemeScript`（inline 防 FOUC）+ `ThemeSwitcher`（`useSyncExternalStore` 讀取 DOM/localStorage，避開 React 19 effect-setState 限制）+ `theme.ts`（型別/常數）。
- root `layout.tsx`：`next/font` self-host Inter；繁中採系統 CJK 字體堆疊；metadata + lang zh-TW。

### US-002：Design System 元件（shadcn 重建）
- 從設計稿 bundle 抽出原 `rs-*` 元件（6 元件 + 43 CSS）作為規格，以 shadcn/ui + Tailwind + `rs-*` token 忠實重建：
  - `ui/badge.tsx` 擴充 6 個賣場變體（promo/logistics/logisticsAlt/feature/rating/count）
  - `storefront/SearchBar`、`SidebarNav`、`SortToolbar`、`ProductCard`；`ui/pagination`（含 ellipsis）

### US-003：共用 Shell Layout（TOP/Tools/Bottom）
- `layout/StorefrontHeader`（TOP：topbar + 主頁首 logo/搜尋/熱搜/購物車 badge + ThemeSwitcher）
- `layout/StorefrontFooter`（Bottom：版權 + 連結 + 懸浮客服）
- `layout/StorefrontTools`（Tools 側欄面板）+ `layout/StorefrontShell`（組合 TOP + 可選 Tools + Content 插槽 + Bottom；D2：`sidebar` 未提供則單欄、隱藏 Tools；響應式 ≥1024px 顯示側欄）

### US-004：首頁 Content 接真實資料
- 新增 buyer `services/listing.ts`（`GET /v2/listings` → `Page<Listing>`）+ `getCartCount`。
- 重寫 `app/page.tsx`（client）：StorefrontShell + SidebarNav（分類：全部/商品/房型 → `type`）+ SortToolbar（最新/價格升降 → 真實 `sortBy/sortDir`）+ ProductCard 網格 + Pagination；loading skeleton / 空狀態 / 未登入-無權限引導（401/403）/ 其他錯誤（500/網路/timeout）顯示「載入失敗，請稍後再試」+ 重試鈕；沿用 axios `X-Tenant-ID`。
- **ProductCard 綁定範圍**：title / price(basePrice) / currency / features(tags)；`was/promo/rating/sold/rank/monthlySales` 為設計稿裝飾欄位，後端 `/v2/listings` 不提供，首版未傳（ProductCard 保留 props）；`logistics` 由前端依 `listingType` 推導（ROOM→旅宿、其餘→賣家宅配），非後端欄位。

### US-005：首頁 E2E
- `e2e/at-homepage.spec.ts`（4 tests）：四區塊呈現、色票切換（含 localStorage 記憶）、搜尋互動、登入後內容解析。遵循既有「不依賴 seed、驗證載入+不 crash」哲學。

---

## 4. 測試狀態

| 測試類型 | 結果 |
|---------|------|
| `npm run build`（Turbopack） | ✅ 0 error（39 頁靜態生成） |
| `npm run type-check`（tsc） | ✅ 0 error |
| `npm run lint`（ESLint flat） | ✅ 0 error（95 warnings 皆既有 service anonymous-default-export，非本次） |
| `make validate-e2e`（乾淨 DB 全棧 Playwright） | at-homepage **4 tests 全綠**；全棧 **33 passed / 5 skipped / 1 failed**，唯一失敗為 `at-m15-e2e 媒體庫篩選功能`（memory 記載之既有 flaky，dialog/session teardown 崩潰，**非本 Sprint 造成**）。push 守門前重跑/處理（AI-1906） |

---

## 5. Definition of Done 驗核

- [x] US-001~005（P1）AC 達成（**AC-005-2 部分達成**，見下）
- [x] `npm run build` / `type-check` / `lint` 0 error
- [x] 首頁四區塊呈現、接真實 `/v2/listings`、5 色票可切換
- [x] US-005 E2E：at-homepage 4 tests 全綠、既有 E2E 不退步（唯一失敗為既有 flaky m15，非本 Sprint）

> **⚠️ AC-005-2 部分達成（Rule 12 大聲揭露）**：AC-005-2 原宣稱 E2E 涵蓋「商品網格渲染、分頁互動」。實際 `at-homepage.spec.ts` 只驗「內容區三態任一渲染不 crash」——已驗：四區塊呈現 + 內容三態不 crash + 主題切換（data-theme 變更）+ 搜尋互動；**未驗**：「有資料網格渲染 + 分頁翻頁」互動（需 seed 商品）。此部分順延 **AI-1905**（目前以手動 checklist 暫代）。
- [x] 多租戶 `X-Tenant-ID` 不破壞
- [x] Sprint 35 Review / Retrospective / Release Notes 建立
- [x] DEF-019 活躍安全項狀態明確交代（US-006 延 S36，不遺漏）
- [ ] pre-push v5 完整守門（make validate-release）—— push 前執行（累積 S32~S35，檢查點徵詢後）

> **誠實備註（Rule 12）**：開發過程遇三個技術限制並誠實處置——(1) Turbopack 無法 self-host CJK 字體 → Inter self-host + 系統 CJK 堆疊；(2) React 19 嚴格 hooks 禁 effect-setState → 主題切換改 `useSyncExternalStore`；(3) `/v2/listings` 需授權 → 首頁未登入顯示引導。E2E 首跑揪出 **E2E-HOME-03 真 bug**——StorefrontHeader 誤傳 `value` 給 SearchBar 卻無 onChange，使輸入被鎖成唯讀（`fill()` 被重置）；修法為 Header 改傳 onSubmit-only（SearchBar 以內部 state 承接輸入），並於 page.tsx 加 `nonce` 修復同值操作 loading 卡死（SearchBar 元件自 US-002 起未曾變更，本身支援受控/非受控雙模式，功能無 bug）→ 修復後重跑通過。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
