# 定價計算器統一評估 / Pricing Calculator Unification Assessment

> **文件類型**: 技術債評估（Spike，非架構決策記錄）
> **版本**: v1.0
> **建立日期**: 2026-07-04
> **來源**: Sprint 55 US-001（AI-2409）；探勘於 Sprint 48（AI-2406c）Retro 提出
> **狀態**: 🔍 評估完成；**建議選項見第 4 節，是否落地待 PO/SD 排入後續 Sprint**

---

## 1. 背景：ROOM/PRODUCT 兩套規則套用邏輯並存

`PricingService` 對 ROOM（訂房，`calculatePrice`）與 PRODUCT（商品，`getEffectivePrice`）分別維護獨立的規則套用邏輯：

| # | 情境 | 方法 | 輸入 context | 回傳型別 |
|---|------|------|-------------|----------|
| (1) | ROOM 訂房 | `calculateAdjustment`（`PricingService.java:432-509`）| `rule, basePrice, date, nights` | `AdjustmentResult{applied, adjustedPrice, type, value}` |
| (2) | PRODUCT 商品 | `applyProductRule` / `applyProductMarkup`（`:577-614`）| `rule, basePrice, checkDate` | `BigDecimal`（僅調整後價格）|

Sprint 48（AI-2406c）已將 `MANUAL_OVERRIDE`/`SEASONAL`/`WEEKDAY_WEEKEND` 三型別的 config key 對齊（`price`/`multiplier`/`weekendMultiplier` 兩端一致），但底層仍是兩份獨立實作，未合併，Retro 記錄為 AI-2409 待評估。

---

## 2. 結構性差異表

| 面向 | ROOM（`calculateAdjustment`）| PRODUCT（`applyProductRule`）|
|------|------------------------------|------------------------------|
| `MANUAL_OVERRIDE` | config `price`，FIXED_AMOUNT，`value=adjustedPrice-basePrice` | config `price`，直接回傳，**語意等價**（Sprint 48 已對齊）|
| `SEASONAL` | config `multiplier`，PERCENTAGE | config `multiplier`，**語意等價** |
| `WEEKDAY_WEEKEND` | config `weekendMultiplier`，依 `date.getDayOfWeek()` 判斷五/六/日 | config `weekendMultiplier`，依 `checkDate.getDayOfWeek()` 判斷五/六/日，**語意等價** |
| `EARLY_BIRD` | config `discountPercent` + `minDaysAhead` gating（`bookingDate` vs `checkIn` 天數差）| **無專屬處理**，落入通用 `discountPercent` fallback（見第 3 節）|
| `LONG_STAY` | config `discountPercent` + `minNights` gating（依 `nights` 遞增折扣）| **無專屬處理**，落入通用 `discountPercent` fallback |
| `LAST_MINUTE` | config `discountPercent` + `maxDaysAhead` gating | **無專屬處理**，落入通用 `discountPercent` fallback |
| 回傳資訊 | `type`（PERCENTAGE/FIXED_AMOUNT）+ `value`（差額/百分比）| 僅價格；`type`/`value` 由呼叫端另行推導（`EffectivePriceResponse` 不含此二欄位）|
| 逐日/單日 | 逐日呼叫（`calculatePrice` for-loop 迭代每晚）| 單日呼叫（`getEffectivePrice` 僅計算 `checkDate` 當日）|
| 候選規則查詢與有效性判斷 | `isRuleApplicable`（`:364-372`）集中判斷日期 + 型別專屬 gating | `getEffectivePrice` 內以 stream `filter` inline 判斷日期範圍，**無型別專屬 gating**（見第 3 節）|

**結論**：`MANUAL_OVERRIDE`/`SEASONAL`/`WEEKDAY_WEEKEND` 三型別自 Sprint 48 起已達語意等價，僅程式碼層面重複（兩處各寫一份幾乎相同的計算邏輯）。`EARLY_BIRD`/`LONG_STAY`/`LAST_MINUTE` 三型別在 PRODUCT 端**並非「未對齊」，而是完全沒有對應語意**——這三型別的核心概念（提前下單天數、入住晚數、臨近入住天數）本就是「未來某個目標日期」的相對關係，PRODUCT 作為即時購買品項沒有對應的「目標日期」（`getEffectivePrice` 的 `checkDate` 參數本身即代表「現在」，沒有『訂購日 vs 到貨/使用日』的區隔），故無法比照 ROOM 直接套用同一套 gating 邏輯。

---

## 3. 關鍵發現：PRODUCT 端 stay-based 型別的靜默 gating 略過

`applyProductRule`（`:577-594`）的邏輯是：先試 `applyProductMarkup`（僅處理 `MANUAL_OVERRIDE`/`SEASONAL`/`WEEKDAY_WEEKEND`，回 `null` 表示未命中），未命中則落入**通用 `discountPercent` fallback**（不分 `ruleType`，只要 config 含 `discountPercent` 就套用）。

若賣家（或測試/未來功能）建立一條 `ruleType=EARLY_BIRD`、`listingId`（PRODUCT）、`config={discountPercent: 15, minDaysAhead: 7}` 的規則：

- ROOM 路徑（若誤用 `roomListingId`）：`isRuleApplicable` 會檢查 `minDaysAhead`，未達門檻不套用。
- PRODUCT 路徑：`applyProductMarkup` 對 `EARLY_BIRD` 回 `null`（無對應 case）→ 落入通用 fallback → **只要有 `discountPercent`，無條件套用 15% 折扣，`minDaysAhead=7` 完全被忽略**。

**曝險評估（本次探勘確認）**：

1. **後端無驗證阻擋**：`PricingService.validateRuleRequest`（`:358-362`）僅檢查 `validTo` 不早於 `validFrom`，未檢查 `ruleType` 與 `roomListingId`/`listingId` 的搭配合理性；`createRule` API 可直接傳入任意組合。
2. **前端目前無法觸發**：`frontend/src/components/pricing/PricingRuleList.tsx`（唯一的定價規則管理 UI，掛載於 `/dashboard/pricing/rules`）表單只有「房源 ID」欄位（`roomListingId`），**沒有 `listingId` 欄位，也沒有 PRODUCT 對應的定價規則管理頁面**。故此落差**目前僅能透過直接 API 呼叫觸發**（例如測試 seed 資料），一般賣家透過現有 UI 操作不會誤觸。
3. **性質判斷**：這不是「兩套計算器對齊未完成」導致的 bug，而是「stay-based 規則型別對 PRODUCT 本就無語意」與「後端未做型別/listing 搭配驗證」兩者疊加的**資料完整性缺口**，與「統一計算器」是兩個不同問題，不應混為一談。

---

## 4. 合併選項比較

### 選項 A：維持現狀（不合併，僅文件記錄）

- **內容**：不動程式碼，僅以本文件記錄現況供未來參考。
- **優點**：零風險。
- **缺點**：`MANUAL_OVERRIDE`/`SEASONAL`/`WEEKDAY_WEEKEND` 三型別的重複邏輯持續存在，未來若三型別計算公式需調整（如加入新的 config 選項），需兩處同步修改，有遺漏風險。

### 選項 B：抽共用 helper，僅統一已對齊的三型別（建議）

- **內容**：抽出 `applyAlignedMarkupRule(ruleType, config, basePrice, date)` 共用方法，處理 `MANUAL_OVERRIDE`/`SEASONAL`/`WEEKDAY_WEEKEND`（回傳統一的調整結果結構）；ROOM 的 `calculateAdjustment` 與 PRODUCT 的 `applyProductMarkup` 皆改呼叫此共用方法取得這三型別的計算結果，各自保留自己的 gating/包裝邏輯（ROOM 的逐日迴圈 + `AdjustmentResult` 包裝；PRODUCT 的單日呼叫 + `BigDecimal` 解包）。`EARLY_BIRD`/`LONG_STAY`/`LAST_MINUTE` **維持 ROOM 專屬**，不嘗試對 PRODUCT 提供對應邏輯（因無自然語意）。
- **優點**：消除三型別的實質程式碼重複；不改變任何現有行為（純重構，行為等價）；風險低，可比照 Sprint 45 AI-2406 的「行為等價清理」模式執行，估 2-3 SP。
- **缺點**：`EARLY_BIRD`/`LONG_STAY`/`LAST_MINUTE` 的分歧本質仍在（但如第 2 節分析，這是合理分歧非缺陷，不需要解決）。

### 選項 C：全面合併（含 stay-based 型別，導入通用 context 物件）

- **內容**：設計一個可選攜帶 `nights`/`bookingDate` 的通用 context 物件，讓 PRODUCT 也能呼叫同一套含 stay-based 邏輯的計算器（PRODUCT 端 `nights`/`bookingDate` 傳 `null`，stay-based 型別在缺 context 時視為不適用或報錯）。
- **優點**：形式上單一計算器。
- **缺點**：**複雜度顯著增加**（需為天生不對稱的 context 設計妥協介面）、**與第 2 節結論矛盾**（stay-based 型別對 PRODUCT 本就無語意，強行統一介面不會產生實質效益，只會讓程式碼更難理解「這個參數什麼時候有意義」）、regression 風險高於選項 B。**不建議**。

### 資料完整性缺口（獨立於合併議題，第 3 節發現）

- **建議另立**：在 `validateRuleRequest` 增加檢查——`ruleType` 為 `EARLY_BIRD`/`LONG_STAY`/`LAST_MINUTE` 時要求必須帶 `roomListingId`（禁止搭配 `listingId`），在建立規則時就阻擋語意不通的組合，而非讓其在計算時靜默略過 gating。此為**低風險驗證強化**，與是否執行選項 B/C 無關，可獨立排入未來 Sprint（估 1 SP，含測試）。

---

## 5. 建議

- **合併本身**：建議選項 B（抽共用 helper，僅統一已對齊三型別），風險低、消除實質重複；**非急迫**（P4，不影響任何現有功能正確性），可視未來容量排入。
- **資料完整性缺口**：建議獨立評估是否要在 `validateRuleRequest` 補上 `ruleType`↔listing 類型搭配驗證，防止未來若 PRODUCT 定價規則管理 UI 上線後才發現此缺口。
- **不建議**：選項 C（全面合併含 stay-based 型別）——複雜度增加但無對應語意效益。
- 本次評估**不涉及任何需要 PO 商業語意決策的事項**（純工程可行性與風險評估），是否排入實作屬一般容量/優先級判斷，可由 SD/Dev 於未來 Sprint 規劃時視容量決定，不需另立 PO 決策項目。

---

## 6. 影響與驗證

- **schema**：無變動。
- **行為**：本 Sprint 純評估，**不改動 production code**，無行為變更。
- **驗證**：無需額外測試（無程式碼異動）。

---

**文件版本**: v1.0｜**建立者**: SD Marcus + Dev David + Claude Code｜**基於**: AISDLC v0.09
