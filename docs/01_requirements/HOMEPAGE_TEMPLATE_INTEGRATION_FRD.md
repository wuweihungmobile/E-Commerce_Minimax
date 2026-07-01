# 賣場店面版型整合 FRD / Homepage Template Integration

> **文件類型**: FRD（功能需求文件）
> **建立日期**: 2026-07-01
> **狀態**: ✅ 已確認（4 項架構決策經使用者確認）
> **來源設計**: `docs/01_requirements/Design_UI/Homepage (standalone) _OK.html`（意象若水 RUOSHUI 賣場首頁範本）
> **協作**: SA Amanda + SD Marcus + PM Victoria + Dev David + QA Quincy

---

## 1. 背景與目標

將設計工具產出的「意象若水 RUOSHUI 賣場首頁」設計稿，作為全站共用版型（Template）套入 Next.js 前端。頁面拆為四個區塊，其中 **TOP / Tools / Bottom 為共用版型**，**Content 為各分頁專屬內容**。

### 1.1 使用者確認的架構決策（2026-07-01）

| # | 決策項 | 選擇 |
|---|--------|------|
| D1 | 套用範圍 | **全站統一改版**（買家店面 + 賣家後台 dashboard/admin/cms 分階段導入） |
| D2 | Tools 區塊策略 | **非瀏覽頁隱藏 Tools**（TOP + Bottom 全站共用；Tools 僅商品瀏覽頁顯示） |
| D3 | 元件導入方式 | **用 shadcn/ui 重建**（對齊設計稿 props/樣式，與現有元件庫一致） |
| D4 | 首個 Sprint 完成度 | **完整**（共用 layout + DS 元件 + 首頁接真實 `/v2/listings` + E2E） |

---

## 2. 四區塊架構（Template Regions）

| 區塊 | 設計內容 | 共用性 | Next.js 對應 |
|------|---------|--------|-------------|
| **TOP** | ① 頂欄 topbar（賣家中心 / 下載 App / 通知 / 幫助中心 / 語言切換）② 主頁首（RUOSHUI logo + 搜尋列 SearchBar + 熱搜關鍵字 + 購物車 badge） | **全站共用** | 共用 `layout.tsx` 內 `<StorefrontHeader />` |
| **Tools** | ① 左側分類導覽 SidebarNav（所有分類）② 主內容區頂部排序工具列 SortToolbar（最新/價格低到高/價格高到低 + 分頁資訊；設計稿原為綜合/最新/月銷，因需後端排序支援，首版改為最新/價格升降）| **僅商品瀏覽頁**（D2） | 共用 `<StorefrontTools />`，非瀏覽頁隱藏 |
| **Content** | 商品網格 ProductCard × N（桌面 4 欄、響應式 2/3/4 欄，每頁 12 筆）+ Pagination | **各分頁不同** | 各路由 `page.tsx`（`{children}` 插槽） |
| **Bottom** | ① 頁尾（版權 © 2026 意象若水 + 隱私權/服務條款/聯絡客服）② 懸浮客服鈕（聊聊） | **全站共用** | 共用 `layout.tsx` 內 `<StorefrontFooter />` |

**設計原則**：`layout.tsx` 提供 TOP + (條件式 Tools) + Bottom，`{children}` 為 Content 插槽；各 `page.tsx` 只負責 Content。完全對應 Next.js App Router 巢狀 layout 機制。

---

## 3. Design System 元件（從設計稿抽出，shadcn 重建）

設計稿內含完整可移植 Design System（原 `rs-*` 命名，來自 bundle `js_fadb426d.js`：6 個 React 元件 + 43 條 CSS）作為規格參考。實作策略：`Badge`、`Pagination` 遵循 shadcn 慣例（`cva` / `VariantProps`）；`SearchBar` / `SidebarNav` / `SortToolbar` / `ProductCard` 為「以 Tailwind + `rs-*` token 對齊設計稿之自訂元件」，**非全數組合 shadcn 基礎元件**：

| 元件 | 原始 props | 實作重建策略 |
|------|-----------|-----------------|
| **Badge** | `variant`(promo/logistics/logisticsAlt/feature/rating/count), `icon`, `children` | 擴充現有 `ui/badge.tsx` 的 variant（遵循 shadcn `cva` 慣例）|
| **SearchBar** | `placeholder`, `buttonLabel`, `value`, `onChange`, `onSubmit` | 自訂 `storefront/SearchBar.tsx`（Tailwind + 原生 input/button，支援受控/非受控雙模式）|
| **SidebarNav** | `title`, `items[]`, `active`, `onSelect` | 自訂 `storefront/SidebarNav.tsx`（Tailwind + `rs-*` token）|
| **SortToolbar** | `tabs[]`(newest/priceAsc/priceDesc), `sort`, `page`, `totalPages` | 自訂 `storefront/SortToolbar.tsx`（Tailwind + `rs-*` token）|
| **ProductCard** | `image`, `title`, `price`, `was`, `currency`, `promo`, `rank`, `logistics[]`, `features[]`, `rating`, `sold`, `monthlySales`, `href` | 自訂 `storefront/ProductCard.tsx`（Tailwind + `rs-*` token；僅用到 `ui/badge`）|
| **Pagination** | `current`, `total`, `onChange` | 新建 `ui/pagination.tsx`（遵循 shadcn `cva` 慣例）|

---

## 4. 主題色票系統（5 套主題）

設計原生支援即時切換色票主題，共 5 套，各 21 個 `--color-*` token：

| 主題 | primary | 特色 |
|------|---------|------|
| **blue**（設計稿預設）| `#3770bf` | 藍主色 + 琥珀 accent |
| green | `#1D6517` | 綠主色 |
| lotus | `#1D6517` | 綠主色 + 橘 accent |
| pastel | `#3C7184` | 藍綠柔和 |
| test05 | `#437118` | 橄欖綠（原品牌色）|

**Token 類別**：primary/secondary/tertiary/accent（品牌）、bg-base/bg-surface/topbar/header（背景）、text-main/text-muted（文字）、border/disabled、success/warning/error/info（狀態）、skeleton。

**整合方式**：導入現有 Tailwind v4 `@theme`（`globals.css`），以 `<html data-theme="...">` 切換；預設 blue。字體：Inter + Noto Sans TC（`next/font`）。

---

## 5. 資料模型與 API（首頁 Content）

**後端 `/v2/listings` 實際回傳欄位**：`Listing` 僅提供 `id / tenantId / listingType / title / description / coverImageUrl / status / basePrice / currency / tags / createdAt / updatedAt`——**不提供** `was / promo / rating / sold / rank / monthlySales`。

| 綁定變數 | 來源 |
|---------|------|
| `products[]` | `GET /v2/listings`（既有 buyer 商品 API，service 層 `services/listing.ts`）。**首頁 ProductCard 實際綁定範圍為 title / price(basePrice) / currency / features(tags)**；`was / promo / rating / sold / rank / monthlySales` 為設計稿裝飾欄位，後端目前不提供，首版不綁定（ProductCard 保留 props 但首頁未傳）|
| `logistics` | **前端依 `listingType` 推導寫死**（ROOM → 「旅宿」；其餘 → 「賣家宅配」），**非後端欄位** |
| `sidebar[]`（分類）| 商品分類（後端分類 API 或靜態分類 + 篩選）|
| `hotKeywords[]`（熱搜）| 靜態設定或搜尋熱度 API（首版可靜態）|
| `cartCount` | 既有購物車 service `services/cart.ts` |
| `sort` / `page` / `totalPages` | 前端狀態 + `/v2/listings` 分頁參數 |

**多租戶**：既有 axios 攔截器自動注入 `X-Tenant-ID`；首頁商品依當前租戶篩選（沿用現況）。

---

## 6. 現有前端資產盤點（可重用 / 缺口）

**可重用**：Next.js App Router、Tailwind v4（`@theme` + CSS 變數）、shadcn/ui（button/card/input/label/select/alert/badge/switch/skeleton）、lucide-react、axios（token 刷新 + X-Tenant-ID）、完整 service 層（product/cart/order/listings…）。

**缺口（本次建立）**：共用版型元件（Header/Tools/Footer/Shell）、Design System 元件（6 個）、主題切換機制、5 套色票、`next/font` 字體、首頁 Content。

**現況**：僅 root `layout.tsx`（create-next-app 樣板）、無任何 route-group layout、無共用 Header/Footer（各頁自建 nav）、首頁 `page.tsx` 為樣板。

---

## 7. 全站導入路線圖（多 Sprint）

| 階段 | 範圍 | Sprint |
|------|------|--------|
| **階段一：基礎 + 首頁** | 主題系統 + DS 元件 + 共用 Shell（TOP/Tools/Bottom）+ 首頁接真實資料 + E2E | **Sprint 35（本次）** |
| 階段二：買家店面全頁 | `(auth)` 買家頁（cart/checkout/orders/bookings/notifications/reviews）套 Shell（Tools 隱藏）| Sprint 36 |
| 階段三：賣家後台 | dashboard/admin/cms 套 Shell（seller 變體 header + 側欄導覽）| Sprint 37+ |

---

**文件版本**: v1.0
**建立者**: SA Amanda + SD Marcus + PM Victoria + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Requirements Workflow
