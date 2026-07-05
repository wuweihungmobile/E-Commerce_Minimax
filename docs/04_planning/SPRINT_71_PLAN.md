# Sprint 71 計劃 / Sprint 71 Plan

> **Sprint 編號**: Sprint 71
> **期間**: 2026-07-06（恢復例行測試強化排程）
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-06
> **基於**: `SPRINT_70_RETRO.md`「下一步」建議——安全缺口已清零，恢復例行測試強化排程：`BookingService`/`RoomCalendarService` 尚未涵蓋的其餘業務邏輯方法測試強化
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 70 收尾狀態 | ✅ 已收尾並 push | `DEF-024`（`OrderService.updateOrderStatus` 跨租戶 IDOR）修復完成並結案，活躍高優先級延後項目回到 0 |
| `BookingService`/`RoomCalendarService` 方法盤點與既有覆蓋現況 | ✅ 完成盤點 | 詳見下方「既有覆蓋現況」 |
| ⚠️ **前置認知落差誠實揭露** | 探勘證實：`BookingService` **並非零測試**——Sprint 68（`DEF-023`）已建立 `BookingServiceOwnershipTest.java`（10 個測試，涵蓋 `getBooking`/`updateBooking`/`cancelBooking` 三方法的擁有權檢查），另有 `BookingServiceDynamicPricingTest`（4）、`BookingServiceOpenWindowTest`（4）、`BookingServiceRoomTitleTest`（2）分別聚焦特定切面。但 **`createBooking`（訂房建立流程，含 Redis 鎖並發控制）與 `updateBooking` 的日期變更（reschedule）業務邏輯完全零單元測試覆蓋**（先前僅由 `BookingIntegrationTest`/`BookingControllerE2ETest` 這類需完整 Spring Context + 真實 PostgreSQL + Redis 的 E2E/整合測試間接涵蓋）。`RoomCalendarService`（328 行，含 `bookDateRange` 的 idempotency 核心邏輯）**確認先前完全沒有 `RoomCalendarServiceTest.java` 這類單元測試檔案，是真正的零覆蓋**。本 Sprint 目標為補齊這兩塊真正的覆蓋缺口，不重複造 Sprint 68 已完成的擁有權測試 | 依 Rule 12「大聲失敗」誠實揭露：`BookingService` 並非完全零測試，避免誤導 |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. 既有覆蓋現況（Dev David + QA Quincy 盤點）

### `BookingService`（813 行，9 個 public 方法）

| 方法 | 既有單元測試涵蓋 | 本 Sprint 動作 |
|------|-------------------|----------------|
| `checkAvailability` | `BookingServiceDynamicPricingTest`（動態定價）、`BookingServiceOpenWindowTest`（開放窗）局部涵蓋 | 不重複造輪子，維持現況 |
| `getCalendar` | `BookingServiceOpenWindowTest` 局部涵蓋 | 維持現況 |
| **`createBooking`** | **零單元測試**（僅 E2E/整合間接涵蓋） | ✅ **本 Sprint 新建 `BookingServiceCreateBookingTest.java`** |
| `getUserBookings` | `BookingServiceRoomTitleTest` 局部涵蓋 | 維持現況 |
| `getBooking` | `BookingServiceOwnershipTest`（Sprint 68 DEF-023，3 個擁有權測試） | 已涵蓋，不重複 |
| **`updateBooking`**（日期變更邏輯 `handleDateChange`/`processDateRangeChange`） | 僅擁有權/狀態檢查有涵蓋（`BookingServiceOwnershipTest`），**日期變更本身業務邏輯零覆蓋** | ✅ **本 Sprint 新建 `BookingServiceUpdateDateChangeTest.java`** |
| `cancelBooking` | `BookingServiceOwnershipTest`（Sprint 68 DEF-023，4 個擁有權/狀態測試） | 已涵蓋，不重複 |

### `RoomCalendarService`（328 行，9 個 public 方法）

**先前完全沒有對應的單元測試檔案**（確認為真正的零覆蓋，非僅零 mock 隔離測試）。本 Sprint 新建 `RoomCalendarServiceTest.java`，涵蓋：

- `bookDateRange`（**idempotency 核心**：同 bookingId 重複呼叫應跳過、不同 bookingId 衝突應擋）
- `isDateRangeAvailable`（悲觀鎖 FOR UPDATE NOWAIT）
- `releaseDateRange` / `blockDateRange` / `unblockDateRange`
- `lockDateRangeNoWait` / `unlockDateRange`（Redis 鎖生命週期，含部分取鎖失敗回滾）

---

## 2. Sprint 71 目標

> **主題**: 多 Sprint 測試強化計劃（恢復例行排程）——`BookingService.createBooking`/`updateBooking`（日期變更）+ `RoomCalendarService`（訂房核心，含 idempotency）單元測試強化

為訂房核心補齊真正的覆蓋缺口：`RoomCalendarService`（先前零單元測試、含 idempotency 核心邏輯）與 `BookingService.createBooking`/`updateBooking` 日期變更流程（先前僅由重量級 E2E/整合測試間接涵蓋），以 Mockito 隔離 repository/外部服務（Redis 鎖、Pricing、FeatureToggle）建立純單元測試，並誠實記錄過程中發現的技術債觀察。

---

## 3. User Story

### US-001：`BookingService`（createBooking/updateBooking 日期變更）+ `RoomCalendarService`（idempotency 核心）單元測試強化

> **SP**: 8 | **優先級**: P1 | **狀態**: ✅ 完成

**AC-001-1**：新增 `RoomCalendarServiceTest.java`，為 `bookDateRange` 建立完整的 idempotency 測試矩陣——日期無記錄建立新條目、unique constraint 並發衝突（E_4001）、**同一 bookingId 重複呼叫應 idempotent 跳過（不重複 save）**、不同 bookingId 佔用應擋（E_4001）、既有非 BOOKED 記錄更新為 BOOKED、悲觀鎖失敗（E_4001）；並為 `isDateRangeAvailable`、`releaseDateRange`、`blockDateRange`/`unblockDateRange`、`lockDateRangeNoWait`/`unlockDateRange`（含部分取鎖失敗時釋放已取得的鎖，避免鎖洩漏）建立測試。

**AC-001-2**：新增 `BookingServiceCreateBookingTest.java`，為 `createBooking` 建立正常路徑（鎖定→可用性二次確認→寫入→更新日曆→釋放鎖）測試，以及房源不存在/非 ROOM 類型/未上架/Room 資料不存在/人數超限/日期不合法/超出開放窗等前置驗證錯誤路徑；並驗證 Redis 鎖在 `finally` 區塊「無論成功或失敗皆釋放」的健壯性（取鎖失敗不誤呼叫 unlock、取鎖後續流程失敗仍正確 unlock）。

**AC-001-3**：新增 `BookingServiceUpdateDateChangeTest.java`，為 `updateBooking` 的日期變更（reschedule）邏輯建立測試——日期未變更時不觸碰日曆、日期變更成功時「釋放舊日期→鎖定新日期→開放窗守門→可用性二次確認→更新日期並重新計價」完整流程、新日期取鎖失敗/超出開放窗/不可用等錯誤路徑及對應的 `finally` 釋放鎖行為。

**AC-001-4**：測試風格比照既有 `BookingServiceOwnershipTest`/`BookingServiceOpenWindowTest`，使用 `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` mock 全部依賴，`TenantContext` 於 `@AfterEach` 清理；因 `Booking.roomListingId` 為 `insertable=false/updatable=false` 影子欄位，純 mock 情境下不透過 `BookingResponse.getRoomListingId()` 驗證，改以 `checkInDate`/`checkOutDate`/`totalAmount` 及 `ArgumentCaptor`/`argThat` 驗證實際存入的 `Booking.roomListing` 關聯正確。

**AC-001-5**：過程中若發現真實 bug 或擁有權/租戶檢查缺口則停止修改、記錄待決策；若發現非安全性質的程式碼技術債（如死碼參數），記錄至 `DEFERRED_ITEMS_TRACKER.md` 供使用者決策是否清理，不自行假設。本 Sprint 發現 `BookingService.createBooking` 的 `idempotencyKey` 參數為死碼（真正 idempotency 由 `BookingController` 經 `IdempotencyService` 處理），已記錄為 `DEF-025`（🟢 低優先級），使用者審閱後決定不清理。

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | `BookingService`（createBooking/updateBooking 日期變更）+ `RoomCalendarService`（idempotency 核心）單元測試強化 | 8 | P1 |
| **合計** | | **8** | |

> **Velocity 參考**：貼近 Sprint 66/67/69（皆 8 SP）之單 Sprint 產能，緊急安全修復 Sprint（68/70）不計入例行排程節奏。

---

## 5. Definition of Done

- [x] US-001：新增 `RoomCalendarServiceTest.java`（新檔，17 個測試）
- [x] US-001：新增 `BookingServiceCreateBookingTest.java`（新檔，12 個測試）
- [x] US-001：新增 `BookingServiceUpdateDateChangeTest.java`（新檔，5 個測試）
- [x] 確認**未修改任何 `BookingService`/`RoomCalendarService` 生產程式碼**（本 Sprint 純測試補強；發現的 `idempotencyKey` 死碼參數記錄為 `DEF-025`，使用者已決策不清理，不在本 Sprint 修改）
- [x] 開發-編譯-測試循環：每新增一個測試檔案後立即編譯 + 執行該檔驗證通過，才繼續下一檔，未累積
- [x] 後端單元回歸（`mvn test`）**640 tests，0 fail**（含本 Sprint 新增 34 個）。**驗證方式說明**：本 Sprint 僅新增測試檔案、未修改任何生產程式碼，依 2026-07-05 使用者確認之全量回歸頻率政策，純補測試 Sprint 只需 `mvn test`（純 Mockito 單元測試，不需真實 Spring Context/DB），不需執行全量 `mvn verify -Pintegration-test`。過程中首次執行 `mvn test` 因本機測試 DB（`make test-db-up`）未啟動，導致既有 `SellerDashboardServiceCacheTest`（`@ActiveProfiles("integration-test")` + `@SpringBootTest`，與本 Sprint 變更無關）3 個測試因 `ApplicationContext` 載入失敗而報錯；執行 `make test-db-up` 後重跑，640 tests 全數 0 fail，確認非本 Sprint 造成的迴歸，純環境前置條件問題
- [x] `make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）
- [x] `DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-025`（🟢 低優先級技術債，`idempotencyKey` 死碼參數，已記錄不排入排程）+ Sprint 71 歷史紀錄條目
- [x] Sprint 71 Review / Retro / Release Notes + trackers
- [x] 本 Sprint 收尾後立即 push

---

## 6. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端測試 | `RoomCalendarServiceTest.java`（新檔，17 個測試） |
| 後端測試 | `BookingServiceCreateBookingTest.java`（新檔，12 個測試） |
| 後端測試 | `BookingServiceUpdateDateChangeTest.java`（新檔，5 個測試） |
| 追蹤文件 | `DEFERRED_ITEMS_TRACKER.md`（新增 `DEF-025`） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration、無前端變動、無生產程式碼變動**（純測試補強 + 一項已由使用者審閱決策的技術債記錄）。

---

## 7. 後續 Sprint 待處理清單（多 Sprint 測試強化計劃）

依風險排序，供 Sprint 72+ 規劃參考：

1. **Sprint 72+（建議）**：ERP 模組整體（Supplier/StockMovement/PurchaseOrder/Inventory，測試目錄完全不存在）
2. 其餘：`ReviewService`（14 方法）、`CmsService`（11 方法）、`ChatService`、`LogisticsService`、`NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService`

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
