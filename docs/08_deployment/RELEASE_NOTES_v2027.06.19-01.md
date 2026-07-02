# Release Notes - v2027.06.19-01 (Sprint 43)

**發布日期**: 2027-06-19（規劃）／實作完成 2026-07-02
**發布類型**: Minor（M12 進階定價落地：早鳥/長住/末班車折扣真正生效於 ROOM；無 schema 變動）
**Sprint**: Sprint 43
**狀態**: ⏳ 待 push（本 Sprint 5 commit；push 債累積 S41+S42+S43，於檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 43 主題：**M12 進階定價落地**。三種折扣（早鳥 EARLY_BIRD / 長住 LONG_STAY / 末班車 LAST_MINUTE）的引擎與前後端骨架早已存在但**從未接進實際下單計價鏈**、且早鳥/末班車天數語意有誤。本 Sprint 修正語意、把定價引擎接進 ROOM 訂房計價（Feature Toggle + 向後相容保護）、補完賣家 config 編輯 UI、買家端顯示折扣。後端變動限於**DTO 與服務接線**（無 entity/migration/schema，Flyway 維持 V57）。

---

## 新功能 ✨

- **ROOM 進階定價折扣真正生效（AI-2402，US-002）**：賣家設定的早鳥/長住/末班車折扣，現會**實際反映在 ROOM 可用性總價與訂房金額**。`BookingService` 接入 `PricingService`——`DYNAMIC_PRICING_ENABLED` 開啟且該區間有折扣時，`checkAvailability` 與訂房金額 `calculateTotalAmount` 同步套折扣後總價（兩者一致，杜絕「顯示折扣卻收原價」）；無規則/toggle 關閉時維持既有 calendar/basePrice 計價（向後相容）。`GET /v2/bookings/availability` 新增 `originalTotalPrice`/`discountAmount`/`appliedRuleName`。
- **買家端折扣顯示（AI-2405，US-004）**：ROOM 詳情頁查詢可用性後，折扣生效時顯示「折扣後總價 + 原價刪除線 + 折扣標籤（規則名｜省 N）」。
- **定價規則 config 編輯 UI（AI-2404，US-003）**：定價規則新增/編輯表單依規則型別動態渲染參數子表單（早鳥：提前天數 + 折扣%；長住：最少晚數 + 折扣%；末班車：窗口天數 + 折扣%；及平假日/季節/手動覆蓋對應參數），取代原本固定送出空 config `{}` 的黑箱。dashboard 新增「快速管理」入口（商品/房型/定價規則）。
- **賣家定價預覽（AI-2405，US-004）**：定價規則頁輸入房源 ID 篩選後，顯示該房源套用規則後的每日 base vs adjusted 預覽（沿用既有 `PricingCalendarPreview`）。

## 改進 🚀

- **早鳥/末班車折扣語意修正（AI-2401，US-001）**：改以「下單日 vs 入住日」計提前/臨近天數（原用規則 `validFrom` vs 入住日，非業界語意）。`CalculatePriceRequest` 新增 `bookingDate`（選填，預設今日）。EARLY_BIRD：下單距入住 ≥ `minDaysAhead`；LAST_MINUTE：入住前 `maxDaysAhead` 天內（含）下單。

## Bug 修復 🐛

- **jsonb config 型別轉換脆弱**（AI-2401）：`PricingService` config 讀取改用 Number 安全轉型（`getIntConfig`/`getDoubleConfig`），消除 jsonb 反序列化為 Integer/Double 時直接 cast 的 ClassCastException 風險。

## 測試 / 驗證 ✅

- **後端單元**：`PricingServiceTest` **12 tests 0 fail**（含 UT-M12-010~012 bookingDate 邊界）；`BookingServiceDynamicPricingTest` **3 tests 0 fail**（折扣生效 / toggle 關 / 無折扣 三路徑）。
- **後端整合（真實 DB）**：Booking + M12 共 **36 tests 0 fail**（不退步）。
- **前端**：`tsc` 0 error、`npm run build`（Turbopack）0 error。
- **本地 E2E 守門（`make validate-e2e`）**：**47 passed / 6 skipped / 0 failed**（含新增 E2E-ROOM-06 折扣顯示；既有全數不退步）。
- **schema 漂移**：無（ddl-auto=validate 對齊）。

## 技術決策 / 已知限制 ⚠️

- **只接 ROOM 計價鏈（誠實揭露 Rule 12）**：本 Sprint 僅接 `BookingService`（ROOM）；`OrderService`/`Cart`（PRODUCT）**未接線** → 另立 **AI-2403**。PRODUCT 折扣仍僅供 consumer 查詢（getEffectivePrice），未接結帳。
- **買家整月日曆每日折扣未做**：`MonthCalendar` 每日折扣需擴充後端 `GET /v2/bookings/calendar` 回傳 discount，本 Sprint 只接 availability → 另立 **AI-2405b**。買家折扣觸點為 availability 查詢（已完成）。
- **僅套用折扣型規則**：只讓折扣（adjustedTotal < baseTotal）流入 booking 計價；漲價型（weekend/seasonal 倍率）維持既有路徑不變，動態定價計算失敗降級為不套用（不阻斷訂房）。
- **兩套定價機制並存**：折扣生效時以 `PricingService`（basePrice + 規則）為基準，per-day `room_calendar` 手動價不參與（刻意設計，記於程式註解）→ 統一評估另立 AI-2406。
- **後端無 schema 變動**：US-001/002 皆為 DTO + 邏輯 + 服務接線，沿用既有 jsonb config，Flyway 維持 V57。

## 資料庫遷移 🗄️

- 無（Flyway 維持 V57）。

## 內含 Commit（Sprint 43）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 43 Plan | 7b090ab | M12 進階定價落地（4 US / 10 SP）|
| US-001 AI-2401 | c771f88 | 早鳥/末班車語意修正（bookingDate + config 防護）|
| US-002 AI-2402 | 19554a0 | 定價引擎接入 ROOM 訂房計價鏈（toggle + 向後相容 + 整合測試）|
| US-003 AI-2404 | 32b0cdc | 定價規則 config 型別化編輯 UI + dashboard 入口 |
| US-004 AI-2405 | 202a96b | 買家折扣顯示 + 賣家預覽 + E2E-ROOM-06 |
| Sprint 43 收尾 | （本次）| Review / Retro / Release Notes + trackers + PRODUCT_BACKLOG |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**基於**: AISDLC v0.09 Release Management Workflow
