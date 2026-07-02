# Sprint 45 計劃 / Sprint 45 Plan

> **Sprint 編號**: Sprint 45
> **期間**: 2027-07-04 ~ 2027-07-17 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-02
> **基於**: Sprint 44 Retro Action Items（AI-2406 / AI-2202d）+ S45 二 Agent 探勘（定價機制、開放窗語意）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者選定「**定價區技術債收斂**」

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者選「定價區技術債收斂」 | M12 全覆蓋後的架構/語意收斂 |
| S44 狀態 | ✅ 已完成（5 commit，未 push）；活躍 DEF=0 | push 債累積 S41~S44（4 Sprint）|
| 技術現況已調查 | ✅ 2 個 Explore Agent（定價機制、開放窗語意） | **均建議縮為決策 + 低風險型** |
| **AI-2406 關鍵發現** | 🔴 `room_calendar.price` 寫入路徑（setDatePrice/setDatePriceBulk）為**死碼**（零呼叫者、恆 NULL、計價恆等 basePrice）；MANUAL_OVERRIDE 才是實際手動日價路徑 | 「雙機制」實為死碼假象；統一可 schema-free |
| **AI-2406 真正風險** | 🔴 「booking/availability 全面改走 PricingService」會使**漲價型規則計入訂房總價**（計價語意變更）→ 需 PO 決策，另立 **AI-2406b** | 本 Sprint 不做行為變更 |
| **AI-2202d 關鍵發現** | 🔴 「無記錄=可訂」是 availability/booking/calendar **三層硬語意**；權威區分開放窗**必然需後端語意 + 可能 schema**，與 S42~44 零-migration 慣例衝突 | 屬產品語意決策 |
| 範圍取向 | ✅ 本 Sprint 定位「**決策 + 低風險清理**」：清死碼、停讀死欄位、產出兩份決策文件；重實作/行為變更另立 | 誠實界線 |
| schema 影響 | ✅ 皆 schema-free（room_calendar.price 保留欄位、邏輯停讀；開放窗僅產決策不實作）| Flyway 維持 V57 |
| push 前置 | ⚠️ 本 Sprint 有後端變動（移除死碼方法）→ push 需完整 `make validate-release` | 承 S41~S44 push 債 |

---

## 1. Sprint 45 目標

> **主題**: 定價區技術債收斂（清死碼 + 語意決策）

M12 進階定價全覆蓋後，收斂定價區的兩項技術/架構債。探勘揭示兩者本質皆為**決策密集**而非實作密集，故本 Sprint 定位為「**低風險清理 + 決策文件**」：**(1)** 統一手動日價機制——移除 `room_calendar.price` 的死碼寫入路徑、邏輯層停讀該（恆 NULL）欄位，確立 MANUAL_OVERRIDE 為唯一手動日價路徑，並就「漲價型規則是否計入 booking 總價」產出決策（實際行為變更另立 AI-2406b）；**(2)** 開放窗語意——產出「未開放 vs 可訂」的評估與決策文件（選項比較 + schema/既有房源 backfill 策略），實作另立。全部 **schema-free**。

---

## 2. User Stories

### US-001：定價機制統一——清死碼 + 停讀 + 決策（P3）（AI-2406）

> **SP**: 3 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-001-1**: 移除死碼 `RoomCalendarService.setDatePrice` / `setDatePriceBulk`（全專案零呼叫者，經探勘確認：無 controller/service/test/前端/seed）
**AC-001-2**: booking/availability/getCalendar 的 base 價讀取移除 `calendar.getPrice() ?? basePrice` 的死欄位 fallback，改直接以 `listing.getBasePrice()`（因 room_calendar.price 恆 NULL，**行為等價**，僅簡化並消除雙機制混淆）；`calendarBaseTotal`、`checkAvailability`、`getCalendar` 三處一致
**AC-001-3**: `RoomCalendar.price` 欄位標記 `@Deprecated` + 註解（schema 保留，邏輯停讀；DROP COLUMN 另立後續低風險任務）
**AC-001-4**: 產出決策文件 `docs/06_quality/PRICING_MECHANISM_UNIFICATION.md`：確立「手動日價唯一路徑 = MANUAL_OVERRIDE 規則」，並就「**漲價型規則（MANUAL_OVERRIDE 調高、weekend/seasonal 加成）是否計入 booking 總價**」提出分析與建議，標記為需 PO 裁決 → 另立 **AI-2406b**（行為變更 + QA 回歸，5-8 SP，非本 Sprint）
**AC-001-5**: 既有測試（BookingService/M12/cart/order 計價相關）全數不退步；`make test-db-up` 跑相關整合測試綠；無 schema 變動

### US-002：開放窗「未開放 vs 可訂」語意評估與決策（P3，spike）（AI-2202d）

> **SP**: 2 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-002-1**: 產出決策文件 `docs/07_design/CALENDAR_OPEN_WINDOW_ASSESSMENT.md`，記錄現況：「無 room_calendar 記錄 = 可訂」為 availability/booking/calendar 三層硬語意；room_calendar 稀疏、lazy 建立（新房源日曆全空）
**AC-002-2**: 比較三選項——(a) Room 層級開放窗欄位（open_from/open_until 或 booking_window_days，需 migration + 改四處 booking 邏輯，推薦）；(b) room_calendar 新增 CLOSED 狀態（varchar 無約束可免 DB migration，但反轉預設會使既有房源全變不可訂 / densify 成本高 → 高風險）；(c) 純前端顯示（無權威、booking 仍接受 → 不建議單用）。含各自 schema 影響、對既有已上架房源的衝擊、與零-migration 慣例的張力
**AC-002-3**: 給出建議選項 + 對既有房源的 migration/backfill 策略草案 + 明確標記為「需 PO 拍板」，決策通過後另立實作 US **AI-2202e**
**AC-002-4**: 本 US 為 spike，**不改 production code**（純文件與設計）；不影響既有行為

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 定價機制統一（清死碼 + 停讀 + 決策）（AI-2406）| 3 | P3 |
| US-002 | 開放窗語意評估與決策（spike）（AI-2202d）| 2 | P3 |
| **承諾合計** | | **5 SP** | |

> **Velocity 參考**：S40=9, S41=12, S42=7, S43=10, S44=8。**本 Sprint 5 SP**，決策/收斂型 sprint 偏輕——因探勘揭示兩項皆為決策密集，重實作/行為變更均誠實另立（AI-2406b / AI-2202e），避免在未經 PO 決策下做高風險計價/語意變更。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 AI-2406（移除死碼 setDatePrice/setDatePriceBulk → 三處 base 價讀取簡化 → @Deprecated 註記 → 決策文件 → mvn 驗證不退步）
   ↓ 後端清理綠（需 make test-db-up 跑計價整合測試）
US-002 AI-2202d（開放窗評估決策文件，純文件 spike）
   ↓
make validate-e2e（驗證 US-001 清理不退步；US-002 無 code 變更）
   ↓
收尾（Review / Retro / Release Notes + trackers）
```

**強制**：US-001 移除死碼後**立即** mvn 編譯 + 跑 booking/M12/cart/order 計價整合測試（需 test DB）確認行為等價不退步；US-002 純文件無需測試。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| 移除死碼誤傷 | 探勘已確認 setDatePrice/setDatePriceBulk 零呼叫者（含前端/測試/seed）；移除後編譯 + 整合測試把關 |
| 停讀 room_calendar.price 改變行為 | 該欄位恆 NULL（無寫入者），fallback 恆回 basePrice → 改直接 basePrice **行為等價**；以整合測試證實不退步 |
| 誤把 AI-2406b 行為變更做進來 | 明確界線：本 Sprint **不讓漲價規則計入 booking**；僅清理 + 決策文件；行為變更另立需 PO 決策 |
| AI-2202d 破零-schema 慣例 | 本 Sprint 僅產決策文件不實作；schema 取捨交 PO 拍板後另立 AI-2202e |
| 有後端變動 → push 需完整 validate-release | 承 S41~S44 push 債累積後徵詢 |

---

## 6. Definition of Done

- [ ] US-001（AI-2406）：死碼 setDatePrice/setDatePriceBulk 移除；三處 base 價讀取簡化（行為等價）；RoomCalendar.price @Deprecated；決策文件產出（含 AI-2406b 界定）；計價整合測試不退步；無 schema
- [ ] US-002（AI-2202d）：開放窗評估決策文件產出（三選項 + schema/backfill + 建議 + AI-2202e 界定）；無 production code 變更
- [ ] `make validate-e2e` 綠、既有不退步；schema 無漂移（Flyway V57）
- [ ] Sprint 45 Review / Retro / Release Notes + trackers 更新
- [ ]（檢查點）承 S41~S44 push 債，累積後於徵詢時完整 `make validate-release` 後 push（嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| 定價機制清理 | `backend/.../core/booking/RoomCalendarService.java`、`BookingService.java`、`domain/model/room/RoomCalendar.java` |
| 定價機制決策 | `docs/06_quality/PRICING_MECHANISM_UNIFICATION.md` |
| 開放窗決策 | `docs/07_design/CALENDAR_OPEN_WINDOW_ASSESSMENT.md` |
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）、trackers（`docs/04_planning/`）|

---

## 8. 🔴 待使用者確認點

1. **範圍**：定價區技術債收斂 = US-001 清死碼 + 停讀 + 決策文件（AI-2406）+ US-002 開放窗評估 spike（AI-2202d）= **5 SP（決策/清理型）**。是否核准?
2. **誠實界線**：探勘揭示兩項皆決策密集——AI-2406 的「漲價規則計入 booking」行為變更另立 **AI-2406b**（需 PO 決策）；AI-2202d 的實作另立 **AI-2202e**（需 PO 拍板 schema）。本 Sprint 只做低風險清理 + 決策文件。是否同意此定位?
3. **輕量 sprint**：本 Sprint 5 SP 偏輕（決策型）。是否接受，或希望加入一項實作/新功能填充?

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
