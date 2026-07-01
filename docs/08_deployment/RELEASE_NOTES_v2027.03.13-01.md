# Release Notes - v2027.03.13-01 (Sprint 36)

**發布日期**: 2027-03-13（規劃）／實作完成 2026-07-02
**發布類型**: Minor（安全收尾 + 前端架構重構）
**Sprint**: Sprint 36
**狀態**: ⏳ 待 push（累積 S32~S36，完整守門 + 檢查點徵詢後 push；含 m15 flaky 前置處理 AI-2001）

> Sprint 36 主題：安全收尾 + 版型架構債償還 —— 清償積欠多 Sprint 的活躍安全項 DEF-019（物流/賣家側 IDOR），並在 S37 買家頁套版導入前償還 DEF-020 版型 Shell 架構債。

---

## 安全修復 🔒

- **DEF-019 物流/賣家側 IDOR 收尾（活躍安全 DEF 歸零）**：
  - `LogisticsService.createLogistics` 加入 **tenant-based 擁有權檢查**（`order.tenantId == 當前租戶`、admin 放行），杜絕任何具 `order:create` 權限者為他租戶訂單建立物流；越權回 403/E_1007。
  - `PaymentService.processOrderPayment`（`/v2/payments` 對外入口）加入 **user-based 擁有權檢查**（買家限本人訂單），杜絕為他人訂單付款。
  - 擁有權檢查置於狀態檢查之前（不向未授權者洩漏訂單狀態）。
  - 承 S33 訂單付款側修復，DEF-019 全數落地。

## 改進 🚀

- **版型 Shell 架構重構（DEF-020）**：賣場店面版型改為 App Router route-group 架構——
  - `app/(storefront)/layout.tsx` 承載共用 TOP（Header）/Bottom（Footer），換頁不重建版型。
  - `"use client"` 邊界下推至葉節點（Header client；Shell/Footer/Tools server-capable），為 S37 買家頁 server component 化鋪路。
  - 搜尋/分類/排序/分頁改走 **URL query**（可分享、可用瀏覽器返回），移除 page-scoped callback 與 nonce 補丁。
  - 此為 S37「(auth) 買家頁全頁套版」的前置基建。

## 測試 / 驗證 ✅

- 後端（乾淨 DB docker postgres）：單元 353 + M07 8 + M11 4 整合 **0 fail**；新增 `LogisticsServiceOwnershipTest` + `PaymentServiceOwnershipTest`（各 3 tests）。
- **實測揪出並修好 S33 遺留的 M07 5 個整合測試失敗**（`@Transactional` 一級快取致 Order 影子 `userId` 為 null）。
- 前端 `build`（Turbopack）0 error（39 頁）、`type-check` / `lint` 0 error。
- `make validate-e2e`（乾淨 DB 全棧 Playwright）：**at-homepage 6 tests 全綠**（新增 E2E-HOME-05 錯誤+重試、E2E-HOME-06 401 鑑別）；全棧 **35 passed / 5 skipped / 1 failed**，唯一失敗為 `at-m15-e2e 媒體庫篩選功能`（memory 記載之既有 flaky，非本 Sprint 造成）。

## 技術決策 / 已知限制 ⚠️

- **URL-driven 篩選**：依 Next 16 官方建議，server page 讀 `searchParams` → props 傳 client `HomeContent`（免 `useSearchParams`+Suspense）；篩選變更以 key-remount 顯示 skeleton（避免 React 19 禁止的 effect 內同步 setState）。
- **首頁搜尋為全站級**：Header 搜尋導向 `/?keyword=`（fresh query，不保留當前分類）——符合全站共用 Header 設計。
- **processBookingPayment（預訂付款側）** 屬 DEF-019 同類但非本次（訂單/物流）範圍，另立項評估。

## 資料庫遷移 🗄️

- 無（純服務邏輯 + 前端變更，Flyway 維持 V57）。

## 已知問題 / 後續

| 項目 | 處置 |
|------|------|
| **m15 既有 flaky**（`at-m15 媒體庫篩選功能`）| **push 前必須處理**（AI-2001）：重跑；若穩定失敗則實修。會擋 make validate-release 嚴格守門。與本 Sprint 無關 |
| (auth) 買家頁套 Shell | Sprint 37（AI-1901，DEF-020 前置已就緒）|
| 買家閉環 live 走查 | Sprint 37（AI-1903，需 live 環境）|
| 首頁「有資料」網格 + 分頁翻頁 E2E | 需 seed，後續補（AI-1905）|

## 內含 Commit（Sprint 36）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 36 Plan | 6783a15 | 安全+架構收尾優先 |
| US-001 DEF-019 | 0cd5bbc | 物流/賣家側 IDOR 收尾（P0 安全）|
| US-002 DEF-020 | 7f34643 | 版型 Shell route-group 架構重構 |
| US-003 AI-1907 | 3b2af94 | home-error/重試 + 401 鑑別 E2E |
| Sprint 36 收尾 | （本次）| Review / Retro / Release Notes + tracker |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**基於**: AISDLC v0.09 Release Management Workflow
