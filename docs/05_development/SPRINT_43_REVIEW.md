# Sprint 43 Review / Sprint 43 評審會議

> **Sprint 編號**: Sprint 43
> **期間**: 2027-06-06 ~ 2027-06-19
> **評審日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: M12 進階定價落地（早鳥/長住/末班車折扣「真正生效」於 ROOM 訂房）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 早鳥/末班車折扣語意修正（AI-2401）| 3 | ✅ 完成 |
| US-002 | 定價引擎接入 ROOM 訂房計價鏈（AI-2402）| 3 | ✅ 完成 |
| US-003 | 前端 config 型別化編輯 UI（AI-2404）| 2 | ✅ 完成 |
| US-004 | 買家折扣顯示 + 賣家預覽（AI-2405）| 2 | ✅ 完成 |

**承諾 10 SP（US-001~004）全數完成**。功能型 Sprint，把「已建好但沒接線」的定價引擎接上實際計價鏈。

---

## 2. 交付內容

- **US-001（後端，AI-2401）**：`CalculatePriceRequest` 新增 `bookingDate`（選填，預設今日）；`PricingService.isRuleApplicable` 早鳥/末班車改以「下單日 vs 入住日」計提前/臨近天數（原用 `rule.validFrom` vs 入住日，非業界語意）——EARLY_BIRD: `daysAhead >= minDaysAhead`；LAST_MINUTE: `0 <= daysUntilCheckIn <= maxDaysAhead`。config 讀取加 Number 安全轉型（`getIntConfig`/`getDoubleConfig`），消除 jsonb 反序列化的 ClassCastException 風險。
- **US-002（後端，AI-2402）**：`BookingService` 注入 `PricingService` + `FeatureToggleService`，新增 `tryDynamicPricing`——`DYNAMIC_PRICING_ENABLED` 開啟且該區間有折扣（adjustedTotal < baseTotal）時，`calculateTotalAmount`（訂房金額）與 `checkAvailability`（可用性顯示）同步套折扣後總價，兩者一致（避免「顯示折扣卻照原價收費」）；無規則/toggle 關閉時 fallback 既有 calendar/basePrice（向後相容）。`AvailabilityResponse` 新增 `originalTotalPrice`/`discountAmount`/`appliedRuleName`。
- **US-003（前端，AI-2404）**：`pricing.ts` 新增 config discriminated union 型別；`PricingRuleFormModal` 依 ruleType 動態渲染 config 子表單（CONFIG_FIELDS 資料驅動：早鳥提前天數+折扣%、長住最少晚數+折扣%、末班車窗口天數+折扣%，及 weekend/seasonal/manual 對應參數），取代原固定送出空 `{}`；含參數提示、必填驗證、編輯回填、切換型別重置。dashboard landing 新增「快速管理」入口，解決定價路由孤立。
- **US-004（前端，AI-2405）**：`ListingDetail` ROOM 分支——availability 回折扣時顯示折扣後總價 + 原價刪除線 + 折扣標籤（規則名｜省 N）；`booking.ts` `AvailabilityResponse` 補 discount 欄位；嵌入既有孤兒元件 `PricingCalendarPreview`（賣家輸入房源 ID 篩選即顯示每日 base vs adjusted 預覽）；新增 E2E-ROOM-06。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端單元（PricingServiceTest）| ✅ **12 tests 0 fail**（含 UT-M12-010~012 bookingDate 提前/臨近邊界）|
| 後端單元（BookingServiceDynamicPricingTest）| ✅ **3 tests 0 fail**（折扣生效 / toggle 關不套 / 無折扣不套 三路徑）|
| 後端整合（真實 DB：Booking + M12）| ✅ **36 tests 0 fail**（BookingIntegration 4 + BookingControllerE2E 18 + M12Pricing 8 + M12EffectivePrice 3 + M12Product 3；不退步）|
| 前端 tsc / build | ✅ 0 error（Turbopack build 通過）|
| 本地 E2E 守門（make validate-e2e）| ✅ **47 passed / 6 skipped / 0 failed**（含新增 E2E-ROOM-06 折扣顯示）|
| schema 漂移 | ✅ 無（ddl-auto=validate 對齊，backend 啟動成功）|
| DB/migration | 無變動（Flyway 維持 V57，沿用既有 jsonb config）|

---

## 4. 誠實揭露（Rule 12）

1. **買家整月日曆每日折扣顯示未做（AC-004-2 縮減）**：`MonthCalendar` 每日折扣顯示需後端 `GET /v2/bookings/calendar` 回傳 rule-computed discount，但 US-002 只接了 `availability`（未動 getCalendar），故本 Sprint **未做**買家日曆每日折扣，改由 availability 查詢作為買家折扣觸點。誠實界線：日曆每日折扣 → 另立 **AI-2405b** 續辦（需擴充 getCalendar）。
2. **只接 ROOM 計價鏈**：US-002 僅接 `BookingService`（ROOM）；`OrderService`/`Cart`（PRODUCT 路徑）**未接線**（誠實界線，另立 **AI-2403**）。PRODUCT 折扣仍走既有 `getEffectivePrice`（僅 consumer 查詢用，未接結帳）。
3. **動態定價與 room_calendar 手動日價的優先關係**：折扣生效時以 `PricingService`（basePrice + 規則）為基準，per-day `room_calendar` 手動價（setDatePrice 路徑）不參與；此為刻意設計並記於程式註解。無 active 折扣規則時維持既有 calendar/basePrice（向後相容）。
4. **僅套用折扣型規則**：US-002 只讓「折扣」（adjustedTotal < baseTotal）流入 booking 計價；漲價型（weekend/seasonal 倍率）維持既有計價路徑不變，避免非預期漲價；動態定價計算失敗降級為不套用，避免阻斷訂房。
5. **無 schema 變動**：US-001 為 DTO + 邏輯、US-002 為 DTO + 服務接線，皆無 entity/migration（沿用 jsonb config），Flyway 維持 V57。

---

## 5. Demo 重點

- 賣家：dashboard →「定價規則」→ 新增「早鳥」規則，config 子表單填「提前 7 天、折 15%」→ 輸入房源 ID 篩選 → 定價日曆預覽顯示每日 base vs adjusted。
- 買家：ROOM 詳情頁選日期（提前 7 天以上）→ 查詢可用性 → 顯示「NT$5,440（原價 ~~NT$6,400~~）｜早鳥 15% off｜省 NT$960」。
- 一致性：加入購物車 / 建立 booking 的金額 = availability 顯示的折扣後總價（不會顯示折扣卻收原價）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
