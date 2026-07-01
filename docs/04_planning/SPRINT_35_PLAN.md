# Sprint 35 計劃 / Sprint 35 Plan

> **Sprint 編號**: Sprint 35
> **期間**: 2027-02-14 ~ 2027-02-27 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-01
> **基於**: [HOMEPAGE_TEMPLATE_INTEGRATION_FRD.md](../01_requirements/HOMEPAGE_TEMPLATE_INTEGRATION_FRD.md)、[SPRINT_34_RETRO.md](../05_development/SPRINT_34_RETRO.md)
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 需求/設計已確認 | ✅ FRD 完成、4 項架構決策使用者確認 | D1 全站 / D2 非瀏覽頁隱藏 Tools / D3 shadcn 重建 / D4 首 Sprint 完整 |
| 設計資產已抽出 | ✅ 6 DS 元件 + 43 CSS + 5 色票 + 資料模型 | 存於分析檔，供實作對齊 |
| 前端架構已盤點 | ✅ 僅 root layout、無共用版型、shadcn/ui + Tailwind v4 就緒 | 缺口明確 |
| 累積批次狀態 | ⚠️ S32~34 共 9 commit 未 push | 前端變更續累積；本地 lint/build/E2E 逐項把關 |
| 活躍 DEF | 1（DEF-019 物流/賣家側，安全）| ⚠️ 仍活躍，不因本 Sprint 而遺漏；排入後續 |

---

## 1. Sprint 35 目標

> **主題**: 賣場店面版型基礎 + 首頁改版（階段一）

建立全站共用店面版型的**基礎建設**（主題色票系統 + Design System 元件 + 共用 Shell layout），並完成**首頁**：TOP/Tools/Bottom 共用 + Content（商品網格）接真實 `/v2/listings` 商品資料，含 Playwright E2E。此為「全站統一改版」的第一階段，Shell 設計為可全站重用（S36+ 逐步推廣）。

---

## 2. User Stories

### US-001：主題色票系統 + 字體（P1）

> **SP**: 3 | **優先級**: P1 | **狀態**: 📋 Ready

**AC-001-1**: `globals.css` 導入 5 套色票主題（blue 預設 / green / lotus / pastel / test05），各 21 個 `--color-*` token，以 `[data-theme="..."]` 選擇器定義
**AC-001-2**: 建立 `ThemeScript`（inline 防 FOUC，於 paint 前設主題）+ `themeStore.ts`（外部 store）+ `ThemeSwitcher`（`useSyncExternalStore`），於 `<html>` 設 `data-theme`，localStorage 記憶選擇，預設 blue（改用 store 模式以符合 React 19 禁 effect 內同步 setState 限制）
**AC-001-3**: root `layout.tsx` 以 `next/font` 導入 Inter + Noto Sans TC，設為 `--font-base`；更新 metadata（title/lang zh-TW）
**AC-001-4**: `npm run build` 0 error；`npm run lint` 0 error（含 Next 16/React 19 嚴格 hooks 規則）

### US-002：Design System 元件（shadcn 重建）（P1）

> **SP**: 5 | **優先級**: P1 | **狀態**: 📋 Ready

**AC-002-1**: 擴充 `ui/badge.tsx` variant（promo/logistics/logisticsAlt/feature/rating/count），對齊設計稿樣式
**AC-002-2**: 新建 `storefront/SearchBar.tsx`（input+button，onSubmit）、`storefront/SidebarNav.tsx`（items/active/onSelect）
**AC-002-3**: 新建 `storefront/SortToolbar.tsx`（tabs + 分頁資訊）、`ui/pagination.tsx`（current/total/onChange）
**AC-002-4**: 新建 `storefront/ProductCard.tsx`（image/title/price/was/promo/rating/sold/logistics/features/href）組合 card+badge
**AC-002-5**: 每個元件建立後即 build + lint 通過才進入下一個（開發-編譯循環）；props 型別完整（TypeScript）

### US-003：共用 Shell Layout（TOP / Tools / Bottom）（P1）

> **SP**: 5 | **優先級**: P1 | **狀態**: 📋 Ready

**AC-003-1**: `components/layout/StorefrontHeader.tsx`（TOP）：topbar（賣家中心/App/通知/幫助/語言 + ThemeSwitcher）+ 主頁首（logo + SearchBar + 熱搜 + 購物車 badge，接 cart service count）
**AC-003-2**: `components/layout/StorefrontFooter.tsx`（Bottom）：版權 + 隱私/條款/客服連結 + 懸浮客服鈕
**AC-003-3**: `components/layout/StorefrontTools.tsx`（Tools）：SidebarNav + SortToolbar；設計為條件式（D2：非瀏覽頁隱藏）
**AC-003-4**: `components/layout/StorefrontShell.tsx`：組合 TOP + (可選 Tools via `showTools` prop) + Content 插槽 + Bottom；響應式（≥1024px 顯示側欄，行動版收合）
**AC-003-5**: root `layout.tsx` 掛載 `<ThemeScript />` + 字體（`<html>` 補 `suppressHydrationWarning` 配合 ThemeScript 於 paint 前設主題）；Shell 套用至首頁（機制可全站重用）
**AC-003-6**: build + lint 0 error

### US-004：首頁 Content 接真實商品資料（P1）

> **SP**: 3 | **優先級**: P1 | **狀態**: 📋 Ready

**AC-004-1**: 重寫 `app/page.tsx` 為 Content-only（`'use client'` 或 Server Component + client wrapper），置於 StorefrontShell（`showTools` = true）
**AC-004-2**: 串接 `GET /v2/listings`（service 層）取商品 → ProductCard 4 欄網格 + Pagination；含 loading skeleton 與空狀態
**AC-004-3**: SidebarNav 分類、SortToolbar 排序、SearchBar 搜尋連動商品查詢（sort/page/keyword/category 參數）；熱搜 + cartCount 接資料（cartCount 未登入則 0/隱藏）
**AC-004-4**: 沿用 axios `X-Tenant-ID`（多租戶隔離不破壞）；錯誤處理友善
**AC-004-5**: build + lint 0 error

### US-005：首頁 E2E（P1）

> **SP**: 2 | **優先級**: P1 | **狀態**: 📋 Ready

**AC-005-1**: 新增 `e2e/at-homepage.spec.ts`：驗證首頁載入、四區塊呈現（header/tools/product grid/footer）
**AC-005-2**: 驗證商品網格渲染、搜尋列輸入、分頁互動、主題切換（data-theme 變更）
**AC-005-3**: `make validate-e2e`（乾淨 DB + full-stack + Playwright）綠燈；既有 E2E 不退步

### US-006（Buffer）：DEF-019 物流/賣家側 IDOR 盤點（P3）

> **SP**: 2 | **優先級**: Buffer/P3（安全，活躍 DEF）

**AC-006-1**: 盤點 `LogisticsService.createLogistics` 租戶語意 + M11 測試 seeding（比照 DEF-017 範式）；若時間允許則修，否則產出修法設計延後（不遺漏此活躍安全項）

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 主題色票系統 + 字體 | 3 | P1 |
| US-002 | Design System 元件（shadcn 重建）| 5 | P1 |
| US-003 | 共用 Shell Layout | 5 | P1 |
| US-004 | 首頁 Content 接真實資料 | 3 | P1 |
| US-005 | 首頁 E2E | 2 | P1 |
| **P1 承諾合計** | | **18 SP** | |
| US-006 | DEF-019 盤點（Buffer）| 2 | P3 |

> **Velocity 參考**：S30=8, S31=8, S32=5, S33≈2, S34=3。**本 Sprint 18 SP 偏高**——因前端純新增（低耦合、無既有測試衝突），且開發者為 AI（快速迭代）。若 build/lint/E2E 出現連鎖問題則誠實縮減至核心（US-001~004），US-005 E2E 可部分延後。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 主題+字體（基礎，其他都依賴色票）
   ↓ build+lint 綠
US-002 DS 元件（逐一：Badge→SearchBar→SidebarNav→SortToolbar→Pagination→ProductCard，每個 build+lint）
   ↓ build+lint 綠
US-003 Shell（Header→Footer→Tools→Shell→掛 root layout）
   ↓ build+lint 綠
US-004 首頁接資料（listings service→page.tsx→連動查詢）
   ↓ build+lint 綠
US-005 E2E（at-homepage.spec.ts→make validate-e2e）
   ↓ 綠
US-006（Buffer，時間允許）
```

**強制**：每個元件/檔案完成後**立即** `npm run build` + `npm run lint`，失敗立即修，**絕不累積**（CLAUDE.md 開發-編譯-測試循環）。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| Next 16 / React 19 嚴格 hooks lint（ref 不可 render 期更新、effect 不可同步 setState）| 寫 hook 前參考記憶 `nextjs16-strict-react-hooks-lint`；client 元件用 event handler 而非 render 期 setState |
| Tailwind v4 `@theme` 與 `[data-theme]` 動態切換相容性 | 色票用純 CSS 變數 + `[data-theme]` 選擇器（非 @theme 內），Tailwind 消費 `var(--color-*)` |
| `/v2/listings` 回傳結構與 ProductCard props 不符 | US-004 先確認 API 回傳 schema（讀 service 層 + 後端 DTO），再對映 |
| 18 SP 偏高 | 分層交付；US-001~004 為核心（可用首頁），US-005 E2E 為驗證，Buffer 可延 |
| E2E flaky（參考 `m15-media-filter-e2e-flaky`）| at-homepage 獨立 spec；flaky 時重跑；不阻斷核心交付 |
| 全站改版範圍大 | 本 Sprint 僅基礎 + 首頁；(auth)/dashboard 分 S36/S37（FRD §7 路線圖）|

---

## 6. Definition of Done

- [ ] US-001~004（P1 核心）AC 達成
- [ ] `npm run build` 0 error、`npm run lint` 0 error
- [ ] 首頁四區塊正確呈現、商品接真實 `/v2/listings`、5 色票可切換
- [ ] US-005 E2E：`make validate-e2e` 綠、既有 E2E 不退步
- [ ] 多租戶 `X-Tenant-ID` 不破壞
- [ ] Sprint 35 Review / Retrospective / Release Notes 建立
- [ ] DEF-019 活躍安全項狀態明確交代（修或設計延後，不遺漏）
- [ ] 本地把關綠燈後，累積批次（S32~35）於檢查點徵詢後 push

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| 主題系統 | `frontend/src/app/globals.css`、`components/theme/` |
| DS 元件 | `frontend/src/components/storefront/`、`components/ui/`（badge/pagination）|
| 共用版型 | `frontend/src/components/layout/`（Storefront Header/Footer/Tools/Shell）|
| 首頁 | `frontend/src/app/page.tsx`、`app/layout.tsx`、listings service |
| E2E | `frontend/e2e/at-homepage.spec.ts` |
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）|

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
