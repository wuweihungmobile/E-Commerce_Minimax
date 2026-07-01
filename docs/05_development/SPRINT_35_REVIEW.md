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
- 重寫 `app/page.tsx`（client）：StorefrontShell + SidebarNav（分類：全部/商品/房型 → `type`）+ SortToolbar（最新/價格升降 → 真實 `sortBy/sortDir`）+ ProductCard 網格 + Pagination；loading skeleton / 空狀態 / 未登入引導；沿用 axios `X-Tenant-ID`。

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

- [x] US-001~005（P1）AC 達成
- [x] `npm run build` / `type-check` / `lint` 0 error
- [x] 首頁四區塊呈現、接真實 `/v2/listings`、5 色票可切換
- [x] US-005 E2E：at-homepage 4 tests 全綠、既有 E2E 不退步（唯一失敗為既有 flaky m15，非本 Sprint）
- [x] 多租戶 `X-Tenant-ID` 不破壞
- [x] Sprint 35 Review / Retrospective / Release Notes 建立
- [x] DEF-019 活躍安全項狀態明確交代（US-006 延 S36，不遺漏）
- [ ] pre-push v5 完整守門（make validate-release）—— push 前執行（累積 S32~S35，檢查點徵詢後）

> **誠實備註（Rule 12）**：開發過程遇三個技術限制並誠實處置——(1) Turbopack 無法 self-host CJK 字體 → Inter self-host + 系統 CJK 堆疊；(2) React 19 嚴格 hooks 禁 effect-setState → 主題切換改 `useSyncExternalStore`；(3) `/v2/listings` 需授權 → 首頁未登入顯示引導。E2E 首跑揪出 **E2E-HOME-03 真 bug**（SearchBar 受控無 onChange + loading 卡死）→ 修復後重跑通過。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
