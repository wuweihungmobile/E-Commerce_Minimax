# Sprint 43 計劃 / Sprint 43 Plan

> **Sprint 編號**: Sprint 43
> **期間**: 2027-06-06 ~ 2027-06-19 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-02
> **基於**: [PRODUCT_BACKLOG.md](PRODUCT_BACKLOG.md) #7 M12 進階定價（P2，RICE 2.1）、S42 Retro、S43 三 Agent 探勘（M12 後端、M12 前端、filler 候選）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者選定「**M12 進階定價（新功能）**」

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者選「M12 進階定價（早鳥/長住/末班車）」 | 回歸新功能開發 |
| S42 狀態 | ✅ 已完成（6 commit，未 push）；活躍 DEF 歸零 | push 債累積 S41+S42 |
| 技術現況已調查 | ✅ 3 個 Explore Agent（M12 後端、M12 前端、filler） | 各項可行範圍已釐清 |
| **核心洞察** | ⚠️ **三折扣（EARLY_BIRD/LONG_STAY/LAST_MINUTE）引擎與前後端骨架皆已存在**（`PricingRule` enum 六型、`PricingService` 計算邏輯、`/v2/dashboard/pricing` API、前端 `PricingRuleList`/`pricing.ts` 皆備）| S43 本質是**接線 + 修正 + 補完 UI**，非從零 |
| **最大缺口** | 🔴 **定價引擎完全沒接進實際下單計價鏈**：`BookingService`/`OrderService`/`Cart` 仍直接用 `basePrice`，`PricingService` 只被 controller 呼叫 | 折扣目前「可設定、可試算，但下單不生效」 |
| **語意問題** | 🔴 早鳥/末班車現以 `rule.validFrom vs 入住日` 算天數，非業界「**今天 vs 入住日**」的提前/臨近語意 | 需引入 `bookingDate` 修正 |
| schema 影響 | ✅ 沿用既有 jsonb `config`（Map），**大機率 0 新表/0 新欄位**；折扣參數存 config | Flyway 維持 V57；若確認無 schema 變動則 push 較輕 |
| 範圍界線 | ✅ 本 Sprint 聚焦 **ROOM（旅宿）計價鏈**；PRODUCT/Cart 折扣接線另評估 | 誠實界線，控制高風險改動半徑 |
| push 前置 | ⚠️ 本 Sprint 有後端 production code 變動（計價邏輯）→ push 需完整 `make validate-release` | 承 S41+S42 push 債 |

---

## 1. Sprint 43 目標

> **主題**: M12 進階定價落地（早鳥/長住/末班車折扣「真正生效」於 ROOM 訂房）

把「已建好但沒接線」的定價引擎接進 ROOM 實際計價鏈，讓賣家設定的早鳥/長住/末班車折扣**真的反映在可用性總價與訂房金額**，買家端看得到折扣價與原價，賣家有 config 編輯與預覽。核心四件事：**(1)** 修正早鳥/末班車天數語意（引入 bookingDate）；**(2)** `PricingService` 接入 `BookingService` ROOM 計價（含 discount 明細，Feature Toggle 保護、向後相容）；**(3)** 前端 config 型別化編輯 UI（現為黑箱 `{}`）；**(4)** 買家端折扣顯示 + 賣家預覽。**避免「顯示折扣卻照原價收費」的半吊子狀態**——顯示與計價一併落地。

---

## 2. User Stories

### US-001：早鳥/末班車折扣語意修正（P1）（AI-2401）

> **SP**: 3 | **優先級**: P1 | **狀態**: 📋 Ready

**AC-001-1**: `CalculatePriceRequest` 新增 `bookingDate`（選填，預設今日）；`PricingService.calculatePrice` 傳遞 bookingDate 至規則適用性判斷
**AC-001-2**: `isRuleApplicable`/`calculateAdjustment` 修正——EARLY_BIRD = 「bookingDate 距入住日 ≥ `minDaysAhead`」；LAST_MINUTE = 「bookingDate 距入住日 ≤ `maxDaysAhead`」（改以今日 vs 入住日，非 validFrom）
**AC-001-3**: LONG_STAY 語意確認（隨 nights 遞增）維持既有或明確調整並記錄；三折扣皆有明確 config 參數契約（討論見附錄）
**AC-001-4**: 更新既有受影響測試（`PricingServiceTest` 9 + `M12PricingIntegrationTest` 8 的預期值），新增 bookingDate 邊界單元測試（剛好符合/不符合提前天數）
**AC-001-5**: config 型別轉換加防護（避免 jsonb 反序列化 Integer/Double ClassCastException）；無 schema 變動

### US-002：定價引擎接入 ROOM 訂房計價鏈（P1）（AI-2402）

> **SP**: 3 | **優先級**: P1 | **狀態**: 📋 Ready

**AC-002-1**: `BookingService.calculateTotalAmount`（及 availability 總價路徑）改為呼叫 `PricingService.calculatePrice`，套用折扣後總價；無 active 規則時 fallback 原 basePrice/calendar price（**向後相容**）
**AC-002-2**: 受 `DYNAMIC_PRICING_ENABLED` Feature Toggle 保護：關閉時維持原行為（basePrice/calendar），開啟且有規則時套折扣
**AC-002-3**: `AvailabilityResponse`（+ 視需要 `CalendarDay`）新增 discount 明細欄位（原價 / 折扣後 / appliedRuleName / discountAmount）；`/v2/bookings/availability` 回傳
**AC-002-4**: 新增整合測試——「早鳥規則使 booking 總價低於 basePrice×晚數」「無規則時總價=basePrice×晚數（不退步）」「toggle 關閉時不套折扣」
**AC-002-5**: 不動 `OrderService`/`Cart`（PRODUCT 路徑）本 Sprint 不接線（誠實界線，另立 AI-2403）；booking 建立金額與 availability 一致（避免顯示/收費不一致）

### US-003：前端 config 型別化編輯 UI（P2）（AI-2404）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-003-1**: `pricing.ts` 將 config 由 `Record<string, unknown>` 收斂為 discriminated union（依 ruleType 對應 EarlyBirdConfig / LongStayConfig / LastMinuteConfig 等）
**AC-003-2**: `PricingRuleFormModal` 依 `ruleType` 動態渲染 config 子表單（早鳥：提前天數 + 折扣%；長住：最少住晚 + 折扣%；末班車：窗口天數 + 折扣%），取代目前固定 `{}`
**AC-003-3**: 沿用既有受控表單模式（`formData` + spread）與 UI 元件庫（Input/Select/Switch）；React 19 嚴格 hooks（async-in-effect + cleanup，禁 effect 同步 setState）
**AC-003-4**: 編輯既有規則時正確載入 config 回填；`npm run build`+`tsc`+`lint` 0 error
**AC-003-5**: dashboard landing 加入 `/dashboard/pricing/rules` 入口連結（現為孤立路由）

### US-004：買家端折扣顯示 + 賣家預覽（P2）（AI-2405）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-004-1**: `ListingDetail` ROOM 分支——availability 回折扣時，顯示折扣後總價 + 原價刪除線 + 折扣標籤（沿用 `ProductCard` 的 `was` 樣式）；無折扣時維持現狀
**AC-004-2**: `MonthCalendar` 每日格——有折扣之日顯示折扣後價（可選原價刪除線）；`CalendarDay`/`booking.ts` 型別補 discount 欄位
**AC-004-3**: 沿用孤兒元件 `PricingCalendarPreview.tsx`（已具 base vs adjusted + appliedRuleName 徽章），嵌入賣家定價頁作規則預覽（近乎零成本）
**AC-004-4**: `at-room-booking.spec.ts` 補 E2E——mock availability 回折扣 → 詳情頁顯示折扣後價 + 原價 + 標籤（E2E-ROOM-06）
**AC-004-5**: `make validate-e2e` 綠、既有不退步

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 早鳥/末班車語意修正（AI-2401）| 3 | P1 |
| US-002 | 定價引擎接入 ROOM 計價鏈（AI-2402）| 3 | P1 |
| US-003 | 前端 config 型別化編輯 UI（AI-2404）| 2 | P2 |
| US-004 | 買家折扣顯示 + 賣家預覽（AI-2405）| 2 | P2 |
| **承諾合計** | | **10 SP** | |

> **Velocity 參考**：S37=10, S38=8, S39=10, S40=9, S41=12, S42=7。**本 Sprint 10 SP**，功能型 sprint 回到中上區間（S42 收尾偏保守後回升）。US-002 為高風險項（改變計價行為），已以 Feature Toggle + 向後相容 + 整合測試三重保護。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 AI-2401（語意修正：bookingDate → isRuleApplicable/calculateAdjustment → 更新 17 既有測試 + 補邊界測試）
   ↓ 後端計算正確，mvn 驗證（純單元/整合）
US-002 AI-2402（接入 BookingService：calculateTotalAmount 呼叫 PricingService + toggle + 向後相容 → availability discount 欄位 → 整合測試）
   ↓ 後端計價鏈綠（含不退步 + toggle 測試）
US-003 AI-2404（config discriminated union → PricingRuleFormModal 動態子表單 → 回填 → build/tsc/lint → landing 入口）
   ↓ 前端賣家設定綠
US-004 AI-2405（ListingDetail/MonthCalendar 折扣顯示 + PricingCalendarPreview 嵌入 + E2E-ROOM-06）
   ↓
make validate-e2e（一次驗證 US-003/004 前端 + 全棧不退步）
   ↓
收尾（Review / Retro / Release Notes + trackers + 更正 PRODUCT_BACKLOG）
```

**強制**：US-001/002 每次改計算/接線後**立即** `mvn test`（先跑 M12 + Booking 相關）確認不退步；前端每檔完成 build/tsc/lint；US-004 後 validate-e2e。**US-002 為高風險，整合測試必須涵蓋「有規則折扣生效」「無規則不退步」「toggle 關閉」三路徑。**

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| **接入計價鏈改變既有訂單/可用性金額（高風險）** | 僅接 ROOM booking 路徑（不動 Order/Cart）；`DYNAMIC_PRICING_ENABLED` toggle 保護；無 active 規則時 fallback 原價（向後相容）；整合測試三路徑把關；booking 金額與 availability 一致 |
| 修語意破壞既有 17 個 M12 測試 | US-001 同步更新既有測試預期值，並新增 bookingDate 邊界測試；先跑 M12 測試群確認 |
| jsonb config 型別轉換脆弱（ClassCastException） | US-001 加 config 讀取防護（Number 轉型統一）；US-003 前端 discriminated union 收斂契約 |
| 前後端 config schema 不對齊 | US-001（後端讀 config key）與 US-003（前端寫 config）明確對齊參數名（minDaysAhead/maxDaysAhead/minNights/discountPercent）；計劃附錄固定契約 |
| 「顯示折扣卻照原價收費」半吊子 | US-002（計價）與 US-004（顯示）同 Sprint 落地；booking 金額來源=availability 折扣後總價 |
| 有後端 production 變動 → push 需完整 validate-release | 承 S41+S42 push 債累積後徵詢 |

---

## 6. config 參數契約（附錄，前後端對齊）

| ruleType | config 欄位 | 語意 |
|----------|------------|------|
| EARLY_BIRD | `minDaysAhead`(int)、`discountPercent`(number) | bookingDate 距入住 ≥ minDaysAhead → 打折 discountPercent% |
| LAST_MINUTE | `maxDaysAhead`(int)、`discountPercent`(number) | bookingDate 距入住 ≤ maxDaysAhead → 打折 discountPercent% |
| LONG_STAY | `minNights`(int)、`discountPercent`(number) | 住 ≥ minNights → 折扣（隨 nights 遞增，維持既有演算法或明確調整並記錄） |

> 註：WEEKDAY_WEEKEND / SEASONAL / MANUAL_OVERRIDE 非本 Sprint 範圍（既有邏輯不動）。

---

## 7. Definition of Done

- [ ] US-001（AI-2401）：早鳥/末班車以 bookingDate vs 入住日；既有 17 測試更新 + 邊界測試；config 型別防護；無 schema 變動
- [ ] US-002（AI-2402）：BookingService 接入 PricingService（toggle + 向後相容）；availability 回 discount 明細；整合測試三路徑（折扣/不退步/toggle）
- [ ] US-003（AI-2404）：config discriminated union + 動態子表單 + 回填 + landing 入口；build/tsc/lint 0 error
- [ ] US-004（AI-2405）：買家 ROOM 折扣顯示（折扣後 + 原價 + 標籤）+ PricingCalendarPreview 嵌入 + E2E-ROOM-06
- [ ] `make validate-e2e` 綠、既有不退步；schema 無漂移（預期 Flyway 維持 V57）
- [ ] Sprint 43 Review / Retro / Release Notes + trackers 更新 + 更正 PRODUCT_BACKLOG（M14/M18「0 測試」已過時）
- [ ]（檢查點）承 S41+S42 push 債，累積後於徵詢時完整 `make validate-release` 後 push（嚴禁 --no-verify）

---

## 8. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端語意 + 接線 | `backend/.../core/pricing/PricingService.java`、`.../dto/.../CalculatePriceRequest`、`.../core/booking/BookingService.java`、`.../dto BookingDto/AvailabilityResponse`、測試 `PricingServiceTest`/`M12PricingIntegrationTest`/`Booking*Test` |
| 前端 config UI | `frontend/src/services/pricing.ts`、`src/components/pricing/PricingRuleList.tsx`(FormModal)、`src/app/dashboard/page.tsx`(入口) |
| 買家顯示 + 預覽 | `frontend/src/components/storefront/ListingDetail.tsx`、`MonthCalendar.tsx`、`src/services/booking.ts`、`src/components/pricing/PricingCalendarPreview.tsx`(嵌入)、`e2e/at-room-booking.spec.ts` |
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）、trackers + PRODUCT_BACKLOG（`docs/04_planning/`）|

---

## 9. 🔴 待使用者確認點

1. **範圍**：M12 進階定價「真正生效於 ROOM」= US-001 語意修正 + US-002 接入 ROOM 計價鏈 + US-003 config UI + US-004 買家顯示 = **10 SP**。是否核准?
2. **高風險項**：US-002 改變 booking 計價行為（以 toggle + 向後相容 + 整合測試保護），且本 Sprint **只接 ROOM，不接 PRODUCT/Cart**（另立 AI-2403）。是否同意此界線?
3. **語意修正**：早鳥/末班車改為「今天 vs 入住日」（業界標準），將調整既有 M12 測試預期值。是否同意?

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
