# Sprint 68 計劃 / Sprint 68 Plan

> **Sprint 編號**: Sprint 68
> **期間**: 2026-07-05（緊急插入，優先於例行排程）
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-05
> **基於**: `DEF-023`（Sprint 67 撰寫 `PaymentStateService` 測試時發現的 IDOR 缺口）——使用者已明確授權插入緊急安全修復 Sprint，優先於 `SPRINT_67_PLAN.md` 第 6 節建議之 Sprint 68（`OrderService` 測試強化，順延至 Sprint 69+）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 67 收尾狀態 | ✅ 已收尾並 push（commit `c4f1b95`） | Review/Retro/Release Notes 齊備 |
| `DEF-023` 完整記錄確認 | ✅ 已讀取 `DEFERRED_ITEMS_TRACKER.md` | 缺口：`PaymentStateService.getBookingPaymentState` 無擁有權檢查；同模組 `BookingService.getBooking/updateBooking`、`PaymentService.processBookingPayment` 同缺；`booking:read` 連 GUEST 角色都持有 |
| Order 側既有修復模式確認 | ✅ 已讀取 `DEF-018`（commit `be89014`）、`DEF-019`（commit `32b5590`、`0cd5bbc`）之實際 diff | 統一模式：`checkOrderOwnership`/`checkOrderPaymentOwnership` helper——買家限本人（`userId.equals(order.getUserId())`）、`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 放行，越權拋 `BusinessException(ErrorCode.E_1007)`；檢查置於狀態檢查**之前**避免向未授權者洩漏資源狀態 |
| 修復範圍界定（本 Sprint） | 僅本次授權明確列出之四處：`PaymentStateService.getBookingPaymentState`、`BookingService.getBooking`、`BookingService.updateBooking`、`PaymentService.processBookingPayment` | 比照 Order 側模式：買家（`booking.getUserId()`）本人 or admin 放行 |
| `booking:read` 權限調整方式評估 | ✅ 確認為純記憶體 Java 常數（`RolePermissionMapping.java` 的 `EnumMap`），**無對應 DB table/migration**（`grep` 確認 migration 中的 `GUEST` 僅為 `user.role` 欄位列舉值，非權限映射表） | 採低風險方式：直接移除 `GUEST` 角色的 `Permission.BOOKING_READ`，**不新增 Flyway migration** |
| Push 節奏 | 本 Sprint 收尾後立即 push（安全修復不宜久留本機） | 不累積 |

---

## 1. Sprint 68 目標

> **主題**: 緊急安全修復——Booking 付款/預訂擁有權檢查缺口（IDOR，`DEF-023`）

修復 Booking 模組系統性 IDOR 缺口：任何登入使用者原本都能查詢/操作他人的訂房付款狀態與訂房資料。比照 Order 側（`DEF-018`/`DEF-019`）既有修復模式補齊四處擁有權檢查，並收斂 `booking:read` 權限不再開放給 `GUEST` 角色，同時新增回歸測試證明修復前後行為差異。

---

## 2. User Story

### US-001：Booking 付款/預訂擁有權檢查修復（DEF-023）

> **SP**: 5 | **優先級**: 🔴 P0（安全） | **狀態**: ✅ 完成

**AC-001-1**：`PaymentStateService.getBookingPaymentState` 加入擁有權檢查（比照 `checkOrderOwnership`，新增 `checkBookingOwnership` helper）——買家限本人（`booking.getUserId()`），`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 放行，越權回 `BusinessException(E_1007)`（映射 HTTP 403）。檢查置於資源存在性檢查之後、其餘邏輯之前。

**AC-001-2**：`BookingService.getBooking` 加入擁有權檢查（比照 `OrderService.getOrder` 的 inline pattern）——買家限本人，admin 放行，越權回 403/`E_1007`。

**AC-001-3**：`BookingService.updateBooking` 加入擁有權檢查（同上模式），置於狀態檢查之前，避免向未授權者洩漏預訂狀態。

**AC-001-4**：`PaymentService.processBookingPayment` 加入擁有權檢查（比照 `PaymentService.checkOrderPaymentOwnership`，新增 `checkBookingPaymentOwnership` helper），置於狀態檢查之前。

**AC-001-5**：新增回歸測試證明修復前後行為差異——每個方法皆有「非擁有者 → `E_1007`」「本人通過擁有權檢查（續走原邏輯）」「admin 放行」三種案例，測試風格比照既有 `PaymentServiceOwnershipTest`/`LogisticsServiceOwnershipTest`（Mockito + `TenantContext`/`SecurityContextHolder`）。

**AC-001-6**：開發-編譯-測試循環——每完成一個方法的修復，立即撰寫對應測試並執行 `mvn test -Dtest=...` 驗證通過，不累積到最後才一次驗證。

### US-002：`booking:read` 權限收斂——移除 GUEST 角色

> **SP**: 1 | **優先級**: 🔴 P0（安全） | **狀態**: ✅ 完成

**AC-002-1**：確認 `booking:read` 權限定義位置為 `RolePermissionMapping.java`（純 Java 常數，`EnumMap<UserRole, Set<Permission>>`），無對應 DB 權限表、無需 Flyway migration。

**AC-002-2**：自 `RolePermissionMapping.ROLE_PERMISSIONS.get(GUEST)` 移除 `Permission.BOOKING_READ`。GUEST 角色修改後僅保留 `PRODUCT_READ`、`ROOM_READ`（訪客仍可瀏覽商品/房源，但不可查詢任何預訂資料——包含理論上不存在的「自己的預訂」，因 GUEST 本質上不應有 Booking 記錄）。

**AC-002-3**：確認移除後不影響其他角色（`BUYER`/`SELLER`/`HOST`/`STORE_OWNER`/`STORE_STAFF`/`ADMIN`/`SUPER_ADMIN`）既有的 `BOOKING_READ` 權限——僅 `GUEST` 一行變動，最小爆炸半徑。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | Booking 付款/預訂擁有權檢查修復（DEF-023） | 5 | 🔴 P0 |
| US-002 | `booking:read` 權限收斂——移除 GUEST 角色 | 1 | 🔴 P0 |
| **合計** | | **6** | |

> **說明**：本 Sprint 為緊急安全修復插入，不計入例行測試強化排程 velocity 序列（S60-67 皆 8 SP）；`SPRINT_67_PLAN.md` 第 6 節原建議之 Sprint 68（`OrderService` 測試強化）順延至 Sprint 69+。

---

## 4. Definition of Done

- [x] US-001：`PaymentStateService.getBookingPaymentState`、`BookingService.getBooking`、`BookingService.updateBooking`、`PaymentService.processBookingPayment` 四處擁有權檢查全數補齊，比照 Order 側既有模式，最小改動
- [x] US-001：新增/擴充回歸測試證明修復前後行為差異（非擁有者 403、本人放行、admin 放行）
- [x] US-002：`RolePermissionMapping.java` 移除 `GUEST` 角色的 `BOOKING_READ` 權限（純程式碼常數變更，無 migration）
- [x] 開發-編譯-測試循環：每完成一個方法立即編譯+執行對應測試，不累積
- [x] 後端單元 + 真 DB 整合全量回歸（`mvn verify -Pintegration-test`）0 fail
- [x] `make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）
- [x] `DEFERRED_ITEMS_TRACKER.md`：`DEF-023` 自「🔴 待決策」移至「已完成延後項目」，記錄解決方式與 commit
- [x] Sprint 68 Review / Retro / Release Notes + trackers
- [x] 本 Sprint 收尾後立即 push

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 生產程式碼修改 | `PaymentStateService.java`、`BookingService.java`、`PaymentService.java`、`RolePermissionMapping.java` |
| 後端測試 | `PaymentStateServiceTest.java`（擴充 `getBookingPaymentState` 擁有權案例）、`BookingServiceOwnershipTest.java`（新檔）、`PaymentServiceOwnershipTest.java`（擴充 `processBookingPayment` 擁有權案例） |
| 追蹤文件 | `DEFERRED_ITEMS_TRACKER.md`（`DEF-023` 結案） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration、無前端變動**——`booking:read` 權限調整為純 Java 常數變更（`RolePermissionMapping.java`），不涉及資料庫 schema。

---

## 6. 已知殘留事項（待人工決策，本 Sprint 不修改）

1. **`BookingService.cancelBooking` 同樣缺少擁有權檢查**：盤點時發現 `cancelBooking` 亦僅 `findBookingById` 無擁有權過濾，理論上與 `DEF-023` 同源，但**不在本次授權明確列出的四處修復範圍內**（`DEF-023` 原始記錄與使用者任務指示皆僅列 `getBookingPaymentState`/`getBooking`/`updateBooking`/`processBookingPayment`）。依 Rule 3（精準改動）不擅自擴大範圍，另立追蹤項目待決策。
2. **`HOST` 角色持有 `booking:update` 權限，但 `updateBooking` 修復後僅放行「本人（買家）或 admin」**：目前 `PUT /v2/bookings/{id}` 為買家/賣家共用同一端點，`HOST`（房源方，管理自己房源的訂房）修復後若非該筆訂房的買家本人，將收到 403。若 `HOST` 對自己房源的訂房確有合法更新需求（例如比照 `DEF-019` 收尾時 `LogisticsService` 額外補的租戶側檢查），需要另一組「租戶/房源擁有權」判斷邏輯，屬於超出本次授權的架構決策，建議由 PO/SD 評估後續是否需要（若目前 HOST 從未實際呼叫此端點，則此為關閉一個原本未被利用的 IDOR 缺口，非破壞既有合法流程）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
