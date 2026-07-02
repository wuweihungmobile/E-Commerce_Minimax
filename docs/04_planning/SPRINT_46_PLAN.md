# Sprint 46 計劃 / Sprint 46 Plan

> **Sprint 編號**: Sprint 46
> **期間**: 2027-07-18 ~ 2027-07-31 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-02
> **基於**: Sprint 45 決策（AI-2406b，US-001 決策文件 PRICING_MECHANISM_UNIFICATION.md §3.2）+ S46 三 Agent 探勘（PricingService 計價 / BookingService 折扣接線 / 受影響測試）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者選定「**AI-2406b 定價機制真正統一（選項 B：漲價型規則計入 booking 總價）**」

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者選「AI-2406b 選項 B」 | 拍板：booking/availability/calendar 全面走 PricingService adjustedTotal（含漲價）|
| S45 狀態 | ✅ 已完成（3 commit，未 push）；活躍 DEF=0 | push 債累積 S41~S45（5 Sprint）|
| 技術現況已調查 | ✅ 3 個 Explore Agent（PricingService / BookingService / 測試） | 落點、降級缺口、欄位語意、測試缺口皆已釐清 |
| **計算核心已支援漲價** | ✅ `calculatePrice` 的 `adjustedTotal` 已含 weekend/seasonal/manual 加成（PricingService.java:228,240,430,440）| 本 Sprint **不需改計算核心**，改在「取用端 + 欄位語意 + 顯示」|
| **3 處折扣閘門** | 🔴 `BookingService.java:598-601`（tryDynamicPricing `adjusted<base`）、`:201-202`（getCalendar 逐日 `adjustedPrice<basePrice`）、`:555-556`（calculateTotalAmount 依賴 dynamic!=null）| 選項 B = 放寬此三閘門，全面採 adjustedTotal |
| **降級缺口** | 🔴 `calculatePrice` 無 Room 列 throw E_4000（→404）；`getListing()`/`basePrice` 為 null → NPE→500 | 決策文件明列「需補優雅降級」|
| **欄位語意失真** | 🔴 漲價下 `discountAmount`（clamp 為 0 矛盾 / 或負值）、`originalPrice`（語意變「加價前」）| 需定義中性「調整」語意 + 前端雙向顯示 |
| 範圍界線 | ✅ **只做 ROOM booking**；PRODUCT/cart（RedisCartService.tryProductDiscount 只折扣）**明確 out-of-scope** | 決策文件 §3.2 限定 booking；PRODUCT 漲價另立 |
| schema 影響 | ✅ schema-free（沿用 jsonb config + DTO 欄位；可能新增 transient DTO 欄位表達方向）| Flyway 維持 V57（連續 S42~S46 零 migration）|
| push 前置 | ⚠️ 本 Sprint 有實質後端 + 前端變動（計價行為變更）→ push 需完整 `make validate-release` | 承 S41~S45 push 債 |

---

## 1. Sprint 46 目標

> **主題**: 定價機制真正統一——漲價型規則計入 ROOM booking（M12 進階定價收官）

M12 進階定價 S43~S44 已讓「折扣」全覆蓋，但**漲價型規則（WEEKDAY_WEEKEND 週末加成、SEASONAL 旺季加成、MANUAL_OVERRIDE 調高）目前只在顯示層呈現、下單仍原價**，造成「顯示加價、下單原價」的顯示/收費不一致。本 Sprint 依 PO 拍板選項 B，讓 booking/availability/calendar **一律採用 `calculatePrice` 的 adjustedTotal（含漲價）**，使顯示與收費完全一致，並補上 PricingService 的優雅降級與漲價的雙向顯示。**只做 ROOM，PRODUCT 另立；schema-free。**

---

## 2. User Stories

### US-001：後端——ROOM 計價全面走 adjustedTotal（含漲價）+ 優雅降級（P2）（AI-2406b）

> **SP**: 5 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-001-1**: 放寬三處折扣閘門，`calculateTotalAmount` / `checkAvailability` / `getCalendar` 於 DYNAMIC_PRICING_ENABLED 開啟時**一律採 adjustedTotal / adjustedPrice（含漲價）**：
- `tryDynamicPricing`（`BookingService.java:598-601`）：移除 `adjustedTotal < baseTotal` 的 hasDiscount 過濾，改為「calculatePrice 成功即回 resp」（含漲價）；
- `getCalendar` 逐日（`BookingService.java:201-202`）：移除 `adjustedPrice < basePrice`，改為「有 adjustedPrice 即採用」（含漲價）；
- `calculateTotalAmount`（`BookingService.java:555-556`）：toggle 開啟即用 adjustedTotal，僅 toggle 關 / 計算失敗才 fallback `calendarBaseTotal`。
- **保留** DYNAMIC_PRICING_ENABLED toggle 關閉短路（`BookingService.java:588,227`）與計算失敗 catch 降級（`:602-606,245-247,559`）——不破 UT-BK-DP-002。

**AC-001-2**: `PricingService.calculatePrice` 補優雅降級——「無對應 Room 列 / `listing` 或 `basePrice` 為 null」時降級（回 basePrice 計價或讓呼叫端 fallback），**不 throw E_4000、不 NPE→500**；無 pricing_rules 列維持既有優雅行為（base==adjusted）。

**AC-001-3**: DTO 欄位語意——漲價下「折扣」命名失真，採**中性調整語意**：
- `AvailabilityResponse`：`originalTotalPrice` = 調整前價、`totalPrice` = 調整後價（含漲價）；`discountAmount` 改為**有號差額** `baseTotal − adjustedTotal`（正=折扣、負=加價），或新增 `priceAdjustmentType`（DISCOUNT / MARKUP / NONE）明確方向；繞過 PricingService `discount` 的非負 clamp（`PricingService.java:241`）對 availability 的影響。
- `CalendarResponse`：`originalPrice` = 調整前、`price` = 調整後（含漲價）；同上透出方向（沿用 `PriceBreakdown.adjustmentType` 概念）。
- 更新 DTO 折扣語意註解（`BookingDto.java:141,180-183`）。**保留既有欄位名以向後相容**，僅擴語意 + 補方向欄位。

**AC-001-4**: 新增 / 複查單元測試（Mockito）：
- `BookingServiceDynamicPricingTest` 新增漲價案例（WEEKDAY_WEEKEND / SEASONAL / MANUAL_OVERRIDE 漲高 → `totalPrice` 含漲價、方向為 MARKUP）；複查 `UT-BK-DP-003`（no-op 語意）+ helper `pricingResponse` 的 discount 夾 0 邏輯；
- `PricingServiceTest` 補一個 MANUAL_OVERRIDE 漲價單元案例（目前缺）。

**AC-001-5**: 新增整合測試（真實 DB）：`BookingControllerE2ETest` 新增「seed 漲價 PricingRule + 開 DYNAMIC_PRICING_ENABLED → availability 金額 / 建單 totalAmount / calendar 每日價**含漲價**」案例。

**AC-001-6**: 現有計價測試全數不退步（`make test-db-up` 跑 booking/M12 整合綠）；catch(Exception)=0、@Deprecated=0；**無 schema 變動**（Flyway V57）。

### US-002：前端——ROOM 漲價雙向顯示 + E2E（P2）（AI-2406b 前端）

> **SP**: 3 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-002-1**: `ListingDetail` availability 顯示支援**雙向**——`totalPrice > originalTotalPrice`（漲價）時以「加價」語意呈現（**不用刪除線 + 「省」文案**，改適當標示如「旺季/週末加價」badge + 加價金額）；折扣情境維持既有（原價刪除線 + 折扣 badge）。依後端透出的方向欄位或 `totalPrice` vs `originalTotalPrice` 大小判斷。

**AC-002-2**: `MonthCalendar` 每日格支援雙向——`price > originalPrice`（漲價日）適當標示（非刪除線）；折扣日維持既有刪除線。保留 `calendar-price-{date}` / `calendar-original-price-{date}` testid。

**AC-002-3**: E2E——新增 `E2E-ROOM-06` 漲價變體（availability 漲價 mock：`totalPrice > originalTotalPrice`）+ `E2E-ROOM-07` 漲價變體（calendar 每日漲價 mock）；既有折扣案例保留不退步。

**AC-002-4**: 前端 `tsc` / `build`（Turbopack）/ `lint` 0 error；`make validate-e2e` 綠（含新增漲價 E2E）。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 後端 ROOM 計價全面走 adjustedTotal（含漲價）+ 降級（AI-2406b）| 5 | P2 |
| US-002 | 前端 ROOM 漲價雙向顯示 + E2E（AI-2406b 前端）| 3 | P2 |
| **承諾合計** | | **8 SP** | |

> **Velocity 參考**：S41=12, S42=7, S43=10, S44=8, S45=5。**本 Sprint 8 SP**，健康區間。實作型 sprint（計價行為變更），較 S45（5 SP 決策型）回到正常負載。M12 進階定價自此**真正收官**（折扣 + 漲價皆顯示與收費一致）。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 後端（放寬三閘門 → PricingService 降級 → DTO 方向欄位 → 單元測試漲價案例 → mvn 編譯 + test-db-up 跑整合測試不退步）
   ↓ 後端綠（含新增漲價單元 + 整合覆蓋）
US-002 前端（ListingDetail/MonthCalendar 雙向顯示 → E2E-ROOM-06/07 漲價變體 → tsc/build/lint）
   ↓ 前端綠
make validate-e2e（全棧驗證：折扣不退步 + 漲價新案例）
   ↓
收尾（Review / Retro / Release Notes + trackers）
```

**強制**：US-001 每完成一個變動單元**立即** mvn 編譯 + 跑對應測試；放寬閘門後**立即**跑 booking/M12 整合測試（需 `make test-db-up`）確認折扣不退步 + 漲價生效，才進 US-002。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| 漲價生效改變既有訂房金額（商業行為變更）| PO 已拍板選項 B；toggle 關閉時完全不變（向後相容）；整合測試 seed 漲價規則證實生效、既有折扣/無規則案例不退步 |
| `discountAmount` 命名在漲價下失真 | AC-001-3 改中性調整語意（有號差額 + 方向欄位）；保留欄位名向後相容；前端據方向雙向顯示 |
| PricingService 無 Room / null basePrice → 500 | AC-001-2 補優雅降級（不 throw / 不 NPE）；補測試覆蓋降級路徑 |
| 前端刪除線/「省」文案對漲價不成立 | AC-002-1/2 雙向顯示（漲價用加價標示非刪除線）；E2E 漲價變體把關 |
| bestRule「最高 priority 勝出非最低價」→ 漲價/折扣互蓋 | **記錄於 Review**（既有 priority 治理語意，非本 Sprint 修）；若測試暴露非預期再評估 |
| `findActiveRulesForDateRange` 要求規則涵蓋整段區間（部分晚數 SEASONAL 會漏）| **記錄於 Review**（既有查詢語意，折扣亦受同限）；逐日精準查詢另立評估 |
| 有實質前後端變動 → push 需完整 validate-release | 承 S41~S45 push 債累積後徵詢 |

---

## 6. Definition of Done

- [ ] US-001：三閘門放寬（calculateTotalAmount/checkAvailability/getCalendar 一律 adjustedTotal 含漲價，toggle 關/失敗 fallback）；PricingService 無 Room/null 優雅降級；DTO 中性調整語意 + 方向欄位；漲價單元（weekend/seasonal/manual）+ UT-BK-DP-003 複查 + PricingServiceTest MANUAL_OVERRIDE 漲價 + BookingControllerE2ETest 漲價整合案例；既有不退步；無 schema
- [ ] US-002：ListingDetail/MonthCalendar 雙向顯示（漲價非刪除線）；E2E-ROOM-06/07 漲價變體；tsc/build/lint 0 error
- [ ] `make validate-e2e` 綠、既有不退步；schema 無漂移（Flyway V57）；catch(Exception)=0、@Deprecated=0
- [ ] Sprint 46 Review / Retro / Release Notes + trackers 更新（含 bestRule priority / range 查詢語意落差之揭露）
- [ ]（檢查點）承 S41~S45 push 債，累積後於徵詢時完整 `make validate-release` 後 push（嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端計價接線 | `backend/.../core/booking/BookingService.java`、`core/pricing/PricingService.java`、`api/dto/BookingDto.java`、`api/dto/PricingDto.java` |
| 後端測試 | `backend/.../core/booking/BookingServiceDynamicPricingTest.java`、`core/pricing/PricingServiceTest.java`、`api/controller/BookingControllerE2ETest.java` |
| 前端顯示 | `frontend/src/components/storefront/ListingDetail.tsx`、`MonthCalendar.tsx` |
| 前端 E2E | `frontend/e2e/at-room-booking.spec.ts` |
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）、trackers（`docs/04_planning/`）|

---

## 8. 🔴 待使用者確認點

1. **範圍**：AI-2406b 選項 B = US-001 後端 ROOM 計價全面走 adjustedTotal 含漲價 + 優雅降級（5 SP）+ US-002 前端漲價雙向顯示 + E2E（3 SP）= **8 SP**。是否核准?
2. **行為變更明示**：本 Sprint **會改變既有訂房金額行為**——賣家設的漲價型規則（週末/旺季/手動調高）將開始計入訂房總價與下單金額（toggle 開啟時）。toggle 關閉則完全不變。是否確認此為期望行為?
3. **範圍界線**：**只做 ROOM**；PRODUCT/cart 漲價（RedisCartService 只折扣）明確 out-of-scope，如需另立。bestRule priority 治理、range 查詢部分晚數落差**記錄不修**（既有語意）。是否同意此界線?
4. **欄位語意**：`discountAmount` 等「折扣」欄位改中性調整語意（有號差額 + 方向欄位），保留欄位名向後相容。是否同意?

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
