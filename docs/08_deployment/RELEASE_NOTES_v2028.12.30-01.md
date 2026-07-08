# Release Notes - v2028.12.30-01 (Sprint 83)

**發布日期**: 2028-12-30（規劃）／實作完成 2026-07-08
**發布類型**: ✨ 新功能（M12 動態定價房東後台定價日曆預覽，PRD P0）；含前端變動
**Sprint**: Sprint 83（PRD v1.0 缺口盤點後使用者選定的 P0 項目）

> Sprint 83 主題：新增房東後台預覽未來 90 天定價日曆功能（PRD 明確標記 P0 Must Have）。完全複用既有 `BookingService.getCalendar` 逐日定價引擎，僅新增租戶擁有權檢查與 API 包裝，前端沿用既有 `PricingCalendarPreview.tsx` 元件新增自動載入區塊。

---

## ✨ 新功能：房東後台 90 天定價日曆預覽

- **問題**：PRD 明確要求「房東在後台預覽未來 90 天定價日曆｜P0」，但原本只有 `POST /calculate`（需輸入 checkIn/checkOut 模擬單次入住）與 `POST /calendar/price`（手動覆蓋寫入），沒有任何唯讀瀏覽端點。
- **架構決策**：完全複用 `BookingService.getCalendar`（Sprint 41/45/47 累積建置，已是逐日定價 + 開放窗判斷的完整封裝，已支援 92 天區間），零重複邏輯，僅新增 `getCalendarForOwner` 做租戶擁有權檢查後委派既有方法。
- **後端**：`BookingService.getCalendarForOwner(roomListingId, startDate, endDate, isSuperAdmin)` + `GET /v2/dashboard/pricing/calendar`（`room:read`/`product:read` 權限，比照 `TransferController`/`SettlementController` 既有 `UserPrincipal` 判斷模式，非 SUPER_ADMIN 限自己租戶）。
- **前端**：`PricingCalendarPreview.tsx` 新增掛載時自動載入的 90 天日曆總覽區塊，保留既有「試算特定入住區間」表單（用途不同：日曆為唯讀總覽，試算為模擬特定訂單晚數/總額）。

## 🔴 本 Sprint 發現的新待處理項目

- **`DEF-041`**（🔴 建議優先修復）：實作過程中比對既有房源/商品寫入方法時發現，`RoomService.updateRoom`/`clearOpenWindow`/`deleteRoom` 與 `ProductService.updateProduct`/`deleteProduct` 完全沒有租戶擁有權檢查，任一租戶可竄改/刪除他租戶的房源或商品。風險高於已修復的 `DEF-034`/`DEF-040`（跨租戶寫入/刪除 vs. 跨租戶讀取），無業務決策疑慮，記錄於 `DEFERRED_ITEMS_TRACKER.md`，本 Sprint 依範圍不修改，建議下一輪優先處理。

## 測試 / 驗證 ✅

- **`BookingServiceOwnerCalendarTest`**（新檔）：4 tests（跨租戶拒絕、本租戶放行、SUPER_ADMIN 放行、房源不存在），0 fail。
- **後端全量整合回歸**（`mvn verify -Pintegration-test`，`make test-db-up` 後）：詳見 commit 訊息最終數字。
- **schema 漂移守門**：`make validate-schema` 無漂移（無 migration，純新增方法/端點）。
- **前端**：`npx eslint` 0 error（既有 2 個 warning 為修改前既有，非本次引入）、`npm run build` 0 error（TypeScript 型別檢查通過）。

## 內含 Commit（Sprint 83）

| US / 項目 | 說明 |
|----------|------|
| Sprint 83 Plan | M12 定價日曆預覽規劃（含 DEF-041 發現記錄）|
| US-001 | `BookingService.getCalendarForOwner` + `PricingController` 新端點 |
| US-002 | 前端 `PricingCalendarPreview.tsx` 自動載入 90 天日曆區塊 |
| Sprint 83 收尾 | Retro / Release Notes + trackers（DEF-041 新增記錄）|

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-08
**基於**: AISDLC v0.09 Release Management Workflow
