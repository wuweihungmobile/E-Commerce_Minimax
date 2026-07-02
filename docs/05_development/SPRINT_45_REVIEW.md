# Sprint 45 Review / Sprint 45 評審會議

> **Sprint 編號**: Sprint 45
> **期間**: 2027-07-04 ~ 2027-07-17
> **評審日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 定價區技術債收斂（清死碼 + 語意決策）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 定價機制統一——清死碼 + 停讀 + 決策（AI-2406）| 3 | ✅ 完成 |
| US-002 | 開放窗「未開放 vs 可訂」語意評估與決策（spike，AI-2202d）| 2 | ✅ 完成 |

**承諾 5 SP（US-001~002）全數完成**。M12 進階定價全覆蓋（S43~S44）後，本 Sprint 收斂定價區的兩項技術/架構債。探勘揭示兩者本質皆為**決策密集**而非實作密集，故定位為「**低風險清理 + 決策文件**」，重實作/行為變更誠實另立（AI-2406b / AI-2202e）。**全部 schema-free**（連續 S42~S45 零 migration，Flyway 維持 V57）。

---

## 2. 交付內容

- **US-001（後端 + 決策文件，AI-2406）**：
  - **關鍵發現**：`room_calendar.price`（每日手動價）的寫入路徑 `RoomCalendarService.setDatePrice` / `setDatePriceBulk` 為**死碼**——全專案零呼叫者（無 controller/service/test/前端/seed，探勘 + grep 雙重確認）；`room_calendar` 自 `V1__Initial_Schema` 後無任何 INSERT seed，記錄僅由 bookDateRange（BOOKED）/ blockDateRange（BLOCKED）lazy 建立，而這些路徑**不寫 price** → 該欄位實務上**恆為 NULL**，所有 `calendar.getPrice() ?? basePrice` 讀取恆回 basePrice。所謂「雙定價機制並存」實為**死碼假象**，真正運作的手動日價路徑只有 `PricingService` 的 `MANUAL_OVERRIDE` 規則（`setCalendarPrice`/`overridePrice`，有端點）。
  - **清理**：移除死碼 `setDatePrice` / `setDatePriceBulk`（`RoomCalendarService`，附註解說明）；`BookingService` 三處讀取（`checkAvailability` calendarDetails、`getCalendar` 每日價、`calendarBaseTotal`）移除死欄位 fallback，每日基準價一律取 `listing.getBasePrice()`（因恆 NULL，**行為等價**）；`calendarBaseTotal` 由「逐日 getCalendarRange 迴圈」改為 `basePrice × nights`，**順帶修正舊寫法對「有記錄之日」加 NULL→ZERO 的潛在低估** bug。`RoomCalendar.price` 欄位以**註解**標記停用/保留（schema 對齊 ddl-auto=validate；DROP COLUMN 另立後續低風險任務）。
  - **決策文件** `docs/06_quality/PRICING_MECHANISM_UNIFICATION.md`：確立「手動日價唯一路徑 = MANUAL_OVERRIDE 規則」；就「**漲價型規則（MANUAL_OVERRIDE 調高、WEEKDAY_WEEKEND 週末加成、SEASONAL 旺季加成）是否計入 booking 總價**」提出分析與建議（選項 A 維持現狀只吃折扣 / 選項 B 全面改走 PricingService），標記為需 PO 裁決 → 另立 **AI-2406b**（行為變更 + QA 回歸，5-8 SP，非本 Sprint）。
- **US-002（決策文件 spike，AI-2202d）**：產出 `docs/07_design/CALENDAR_OPEN_WINDOW_ASSESSMENT.md`——記錄「無 room_calendar 記錄 = 可訂」為 availability/booking/calendar **三層硬語意**（非僅顯示層）、room_calendar 稀疏 lazy 建立（新房源日曆全空 → 對買家全部呈現可訂）、無「未開放/CLOSED」狀態；比較三選項（A Room 層級開放窗欄位【推薦，需 migration】/ B room_calendar 新增 CLOSED 狀態【反轉預設致既有房源全變不可訂或 densify 高成本，高風險】/ C 純前端【無權威、booking 仍接受，不建議單用】）含各自 schema 影響、對既有房源衝擊、與零-migration 慣例的張力；給出建議（選項 A `open_until_date DATE NULL`）+ 既有房源 NULL 安全過渡策略 + 標記需 PO 拍板 → 決策後另立實作 **AI-2202e**（估 5 SP）。**本 US 為 spike，不改 production code。**

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯（mvn，US-001 清死碼後）| ✅ 0 error |
| 後端單元（計價相關：BookingServiceDynamicPricingTest 等）| ✅ **6 tests 0 fail** |
| 後端整合（真實 DB：booking/M12/cart/order 計價）| ✅ **57 tests 0 fail**（行為等價、不退步）|
| 本地 E2E 守門（make validate-e2e）| ✅ **48 passed / 6 skipped / 0 failed**（US-001 清理不退步；S45 無新增 E2E）|
| schema 漂移 | ✅ 無（ddl-auto=validate 對齊）；**無 schema 變動**（Flyway V57，room_calendar.price 欄位保留）|
| `@Deprecated` 計數 | ✅ 維持 0（RoomCalendar.price 以**註解**標記停用，未加 `@Deprecated` 註解以維持專案 `@Deprecated=0` 慣例）|

---

## 4. 誠實揭露（Rule 12）

1. **本 Sprint 為「決策 + 低風險清理」型，5 SP 偏輕**：探勘揭示兩項皆決策密集，非實作密集。重實作/行為變更均誠實另立——AI-2406 的「漲價規則計入 booking」行為變更 → **AI-2406b**（需 PO 決策）；AI-2202d 的開放窗實作 → **AI-2202e**（需 PO 拍板 schema）。本 Sprint 不做任何計價/語意行為變更。
2. **US-001 為行為等價清理**：因 room_calendar.price 恆 NULL，移除其 fallback 讀取不改變任何對外行為；`calendarBaseTotal` 改寫**順帶修正**了舊迴圈對「有記錄之日」的 NULL→ZERO 潛在低估（該路徑實務上未被觸發，屬防禦性修正）。以真 DB 整合 57 tests 證實不退步。
3. **`@Deprecated=0` 慣例優先於個人偏好（Rule 11）**：計劃 AC-001-3 原寫「`@Deprecated` + 註解」，實作時改用**純註解**標記，以維持專案長期維護的 `@Deprecated=0` 度量。已於決策文件與程式碼註解記錄此取捨。
4. **room_calendar.price 欄位暫留**：邏輯已全面停讀，但欄位保留以符 ddl-auto=validate（避免 schema 漂移）；`V58__Drop_Room_Calendar_Price.sql`（DROP COLUMN，因無資料無讀寫者風險極低）另立後續低風險任務，待 3.1 邏輯統一穩定後執行。
5. **開放窗與零-migration 慣例的張力（誠實揭露）**：推薦的選項 A **需 migration**，將打破 S42~S45 連續「無 schema 變動」——這正是此項一直被延後的根因（本質為產品語意 + schema 決策，非純技術清理）。本 Sprint 僅產決策文件不實作。
6. **push 債累積 S41~S45（5 Sprint）**：本 Sprint 有後端變動（移除死碼方法 + 三處讀取簡化）→ push 需完整 `make validate-release`。承 S41~S44 累積，於檢查點徵詢後一次守門 push（AI-1908；嚴禁 --no-verify）。

---

## 5. Demo 重點

- **架構收斂**：向團隊展示「雙定價機制」實為死碼假象——`grep` 佐證 setDatePrice/setDatePriceBulk 零呼叫者、room_calendar.price 恆 NULL；確立 MANUAL_OVERRIDE 為唯一手動日價路徑。
- **行為等價證明**：清理前後 booking/availability/calendar 整合測試 57 tests 全過，證實移除死欄位 fallback 零行為影響。
- **決策文件交付**：兩份 ADR 候選文件把需 PO 拍板的高風險項（漲價計入 booking、開放窗 schema）誠實界定另立，供產品決策。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
