# Release Notes - v2027.02.27-01 (Sprint 35)

**發布日期**: 2027-02-27（規劃）／實作完成 2026-07-01
**發布類型**: Minor（前端賣場版型 + 首頁改版）
**Sprint**: Sprint 35
**狀態**: ⏳ 待 push（累積 S32+S33+S34+S35，完整守門 + 檢查點徵詢後 push）

> Sprint 35 主題：賣場店面版型基礎 + 首頁改版 —— 將「意象若水 RUOSHUI」設計稿套為全站共用版型（TOP/Tools/Bottom 共用 + Content 分頁），首頁接真實商品資料 + E2E。

---

## 新功能 ✨

- **賣場首頁改版（意象若水 RUOSHUI）**：首頁 `/` 由 create-next-app 樣板改為完整賣場版型——頁首（logo + 搜尋列 + 熱搜 + 購物車）、左側分類側欄、排序工具列、商品網格、分頁、頁尾與懸浮客服。接真實 `GET /v2/listings`，支援分類（全部/商品/房型）、排序（最新/價格升降）、關鍵字搜尋、分頁連動。
- **5 套可切換色票主題**：blue（預設）/ green / lotus / pastel / test05，頁首即時切換並記憶於 localStorage（防 FOUC）。
- **共用店面版型（Shell）**：`StorefrontShell` 組合 TOP（Header）+ 可選 Tools（側欄）+ Content + Bottom（Footer），設計為全站可重用（(auth)/dashboard 分階段導入，見路線圖）。
- **Design System 元件**：6 個賣場元件（SearchBar / SidebarNav / SortToolbar / ProductCard / Pagination + Badge 賣場變體），以 shadcn/ui + Tailwind 重建。

## 改進 🚀

- root `layout.tsx`：`next/font` self-host Inter；繁中系統 CJK 字體堆疊；metadata + lang zh-TW。
- 保留既有 shadcn 中性 token 不動，新增平行 `rs-*` 品牌 token（既有頁面零影響）。

## 技術決策 / 已知限制 ⚠️

- **CJK 字體**：Turbopack 無法 self-host next/font 的 CJK（Noto Sans TC 大量 unicode-range 子集）→ Inter self-host + 系統 CJK 字體堆疊（PingFang TC / Noto Sans TC / Microsoft JhengHei）。
- **主題切換**：React 19 嚴格 hooks 禁 effect 內同步 setState → 改用 `useSyncExternalStore` 讀取 DOM/localStorage。
- **首頁商品需授權（非公開）**：`GET /v2/listings` 需 `product:read` 授權 → 未登入/無權限（401/403）顯示登入引導；其他錯誤（500/網路/timeout）顯示「載入失敗，請稍後再試」錯誤狀態 + 重試鈕（不再把故障偽裝成「無商品」）；已登入且有權限則載入商品。若需公開匿名瀏覽，需後端放寬 `/v2/listings` read 授權（另評估）。

## 資料庫遷移 🗄️

- 無（純前端變更，Flyway 維持 V57）。

## 測試 / 驗證 ✅

- `npm run build`（Turbopack）0 error（39 頁靜態生成）；`type-check` 0 error；`lint` 0 error（95 warnings 皆既有）。
- `make validate-e2e`（乾淨 DB 全棧 Playwright）：新增 `at-homepage.spec.ts` **4 tests 全綠**；全棧 **33 passed / 5 skipped / 1 failed**，唯一失敗為 `at-m15-e2e 媒體庫篩選功能`（memory 記載之既有 flaky，非本 Sprint 造成）→ push 守門前重跑/處理。
- E2E 首跑揪出並修復 E2E-HOME-03 真 bug：StorefrontHeader 誤傳 `value` 給 SearchBar 卻無 onChange，使輸入被鎖成唯讀（`fill()` 被重置）；修法為 Header 改傳 onSubmit-only（SearchBar 以內部 state 承接輸入），並於 page.tsx 加 `nonce` 修復同值操作 loading 卡死（SearchBar 元件自 US-002 起未曾變更、支援受控/非受控雙模式，功能無 bug）。

## 全站導入路線圖 🧭

| 階段 | 範圍 | Sprint |
|------|------|--------|
| 一（本次）| 基礎 + 首頁 | Sprint 35 ✅ |
| 二 | (auth) 買家頁套 Shell（Tools 隱藏） | Sprint 36 |
| 三 | dashboard/admin/cms 套 Shell（seller 變體） | Sprint 37 |

## 已知問題 / 後續

| 項目 | 處置 |
|------|------|
| **DEF-019 物流/賣家側 IDOR**（活躍安全） | Sprint 36（AI-1902）：createLogistics tenant-based 檢查 |
| 買家閉環 live 走查 | Sprint 36（AI-1903，需 live 環境） |
| 首頁商品「有資料」+ 分頁翻頁 E2E | 需 seed，後續補（AI-1905） |

## 內含 Commit（Sprint 35）

| US / 項目 | 說明 |
|----------|------|
| FRD + Sprint 35 Plan | 需求/設計分析 + 計劃 |
| US-001~005 | 主題系統 / DS 元件 / 共用 Shell / 首頁接資料 / E2E |
| Sprint 35 收尾 | Review / Retro / Release Notes + tracker |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**基於**: AISDLC v0.09 Release Management Workflow
