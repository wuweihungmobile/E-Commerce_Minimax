# Release Notes - v2027.04.10-01 (Sprint 38)

**發布日期**: 2027-04-10（規劃）／實作完成 2026-07-02
**發布類型**: Minor（買家功能補完）
**Sprint**: Sprint 38
**狀態**: ⏳ 待 push（累積 S32~S38，完整守門 make validate-release + 檢查點徵詢後 push）

> Sprint 38 主題：買家體驗補完 —— 商品詳情頁。補上買家閉環最大功能缺口:買家自首頁商品卡點入即可查看完整商品詳情並加入購物車（PRODUCT）或選日期計價加購（ROOM）。純前端 + 前端 service 補強,**無後端/DB 變動**（端點/DTO 皆已存在）。

---

## 新功能 ✨

- **買家商品詳情頁（AI-2103）** `/listings/[id]`：
  - 置於 `(storefront)` group,沿用共用 Header/Footer + StorefrontShell（公開路由,未登入/401 顯示登入引導）。
  - 展示封面圖 + 標題 + 描述 + 價格 + 標籤 + 類型徽章。
  - **PRODUCT**:數量選擇 + 加入購物車（`POST /v2/cart/items`）→ 成功回饋 + 共用 Header 購物車數即時更新。
  - **ROOM**:入住/退房日期選擇 + 動態計價（`GET /v2/listings/{id}/price`）顯示 + 帶日期加入購物車。
  - 三態鑑別:loading skeleton / 404 找不到商品 / 401 登入引導。
  - 首頁/商品卡連結由評價頁改導向詳情頁（詳情頁內另留「查看評價」入口）。

## 改進 🚀

- **共用 Header 購物車數即時更新**：新增 `cartEvents`（自訂事件）,加購後通知共用 Header 重新抓取購物車數量（Header 置於 route-group layout、不隨導覽 re-render,故以事件通知）。

## 測試 / 驗證 ✅

- **前端**：`npm run build`（Turbopack）通過、`type-check` / `lint` 0 error。
- **E2E**：`make validate-e2e`（乾淨 DB 全棧 Playwright）**41 passed / 6 skipped / 0 failed**：
  - 新增 US-002（AI-1905，**page.route mock,免後端 seed**）:E2E-DETAIL-01 首頁有資料網格 + 分頁翻頁、E2E-DETAIL-02 點卡進詳情 + PRODUCT 加購、E2E-DETAIL-03/04 詳情頁 401/404 鑑別。
  - 既有 E2E（at-homepage / at-buyer-pages / m10 / m11 / m15 / m17）全數不退步。

## 技術決策 / 已知限制 ⚠️

- **AI-1905 用 mock 而非後端 seed**：以 page.route mock 達成「有資料網格 + 分頁」E2E,避開 seed 的租戶/FK 陷阱風險（記憶 `erp-tenant-test-seeding-gotcha`）。真實資料 seed 另立項評估。
- **ROOM 收斂為「日期 + 計價 + 加購（帶日期）」**：復用購物車流程,未另開 booking 建立流程（另立項）。
- **US-003 買家 live 走查順延**：需 live 環境,登記至 S39。
- **前端 Listing interface** 已含詳情所需欄位（description 等）;owner 等進階欄位未納入首版。

## 資料庫遷移 🗄️

- 無（純前端 + service 變更,Flyway 維持 V57）。

## 內含 Commit（Sprint 38）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 38 Plan | 3991960 | 買家體驗補完——商品詳情頁 |
| US-001 AI-2103 | eef1070 | 買家商品詳情頁（PRODUCT 加購 + ROOM 計價加購 + 三態）|
| US-002 AI-1905 | 1bbe3d5 | 商品詳情 + 首頁有資料 E2E（mock-based）|
| Sprint 38 收尾 | （本次）| Review / Retro / Release Notes + tracker |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**基於**: AISDLC v0.09 Release Management Workflow
