# Sprint 58 計劃 / Sprint 58 Plan

> **Sprint 編號**: Sprint 58
> **期間**: 2028-01-02 ~ 2028-01-15 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-04
> **基於**: `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` 活躍延後項目（AI-2408）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 前置技術調查 | ✅ 完成：`BookingService.checkAvailability` 有 3 處組成 `unavailableReason` 英文字串字面值（日期範圍無效、開放窗超出、`RoomCalendarStatus` 非 AVAILABLE 之三態）；前端 `ListingDetail.tsx` 直接原樣顯示；`frontend/package.json` 確認**無任何 i18n 框架依賴**，整站為純繁體中文介面，這幾個英文字串是唯一語言不一致例外 |
| **範圍決策（工程範圍判斷，非業務決策）** | ✅ **不導入 i18n 框架**，採最小方案：後端回傳結構化 reason code（enum name 字串），前端用簡單 code→中文訊息對照表顯示 | 判斷依據：全站目前無多語系需求（純中文介面），若為此單一功能引入完整 i18n 框架（如 next-intl）屬過度工程（Rule 2）；此為工程範圍判斷，不涉及業務語意，未徵詢 PO |
| Push 狀態 | ⏸️ 維持批次 push 決策，累積延伸至 S41~S58 | 不影響本 Sprint 開發 |

---

## 1. Sprint 58 目標

> **主題**: Availability unavailableReason 錯誤碼化——後端回傳 code，前端中文對照表顯示

`BookingService.checkAvailability` 目前回傳的 `unavailableReason` 是英文字串（如 "Date X is booked"），前端原樣顯示造成中英混雜。本 Sprint 改為回傳結構化 code（`INVALID_DATE_RANGE`/`NOT_OPEN_FOR_BOOKING`/`BOOKED`/`BLOCKED`/`MAINTENANCE`），前端以對照表顯示對應中文訊息。

---

## 2. User Story

### US-001：Availability reason 碼化（AI-2408）

> **SP**: 2 | **優先級**: P4 | **狀態**: 📋 Ready

**AC-001-1**: `BookingDto` 新增巢狀 enum `AvailabilityReasonCode`：`INVALID_DATE_RANGE`、`NOT_OPEN_FOR_BOOKING`、`BOOKED`、`BLOCKED`、`MAINTENANCE`。

**AC-001-2**: `BookingService.checkAvailability` 三處 `unavailableReason` 賦值改為對應 enum `.name()`：
- 日期範圍無效（checkOut ≤ checkIn）→ `INVALID_DATE_RANGE`
- 開放窗超出（`notOpenNight != null`）→ `NOT_OPEN_FOR_BOOKING`
- `findFirstUnavailableReason` 依 `RoomCalendarStatus`（BOOKED/BLOCKED/MAINTENANCE）→ 對應同名 code

**AC-001-3**: 前端 `ListingDetail.tsx` 新增 `AVAILABILITY_REASON_MESSAGES` 對照表（code → 中文訊息），顯示時查表；查無對應 code 時 fallback 顯示原始值（不可為空白，向後相容未預期的未來 code）。

**AC-001-4**: 測試——後端 `BookingControllerE2ETest` 既有斷言（`containsString("not open")`）更新為檢查新 code 值；前端 E2E（`at-room-booking.spec.ts`）既有以英文字串 mock 的案例更新為新 code + 對應中文斷言。

**誠實揭露**：本次僅碼化 `checkAvailability` 回傳的 `unavailableReason` 欄位；`BookingService` 建單/改期時超窗擋訂拋出的 `BusinessException(E_3002, "Room is not open for booking on ...")` 例外訊息不在本次範圍（那是全站通用的 BusinessException 訊息一律英文的更大範圍問題，非本 ticket 範疇）。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | Availability reason 碼化（AI-2408）| 2 | P4 |

> **Velocity 參考**：符合 `DEFERRED_ITEMS_TRACKER.md` 原估 2 SP（無 schema 變動，前後端小幅調整）。

---

## 4. Definition of Done

- [ ] US-001：`BookingDto.AvailabilityReasonCode` enum + `checkAvailability` 三處改用 code + 前端對照表
- [ ] 後端/前端既有測試更新為新 code 格式；既有測試不退步
- [ ] `make validate-schema` 無漂移（schema-free）
- [ ] `DEFERRED_ITEMS_TRACKER.md` AI-2408 狀態更新
- [ ] Sprint 58 Review / Retro / Release Notes + trackers

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端 DTO | `api/dto/BookingDto.java`（新增 `AvailabilityReasonCode` enum）|
| 後端 Service | `core/booking/BookingService.java`（三處 `unavailableReason` 改用 code）|
| 前端 | `frontend/src/components/storefront/ListingDetail.tsx`（新增對照表）|
| 後端測試 | `BookingControllerE2ETest.java`（更新既有斷言）|
| 前端測試 | `frontend/e2e/at-room-booking.spec.ts`（更新既有 mock/斷言）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration、不導入 i18n 框架**（schema-free，純字串格式調整）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
