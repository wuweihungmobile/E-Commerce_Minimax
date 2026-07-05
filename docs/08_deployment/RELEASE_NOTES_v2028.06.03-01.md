# Release Notes - v2028.06.03-01 (Sprint 68)

**發布日期**: 2028-06-03（規劃）／實作完成 2026-07-05
**發布類型**: 🔴 Hotfix（緊急安全修復，IDOR；schema-free；後端聚焦，無前端變動）
**Sprint**: Sprint 68（緊急插入，優先於例行測試強化排程）
**狀態**: ⏳ 待 push（本 Sprint commit；收尾後即 push，嚴禁 `--no-verify`）

> Sprint 68 主題：**🔴 緊急安全修復——Booking 付款/預訂擁有權檢查缺口（IDOR，`DEF-023`）**。使用者明確授權插入緊急 Sprint，優先於 `SPRINT_67_PLAN.md` 第 6 節原建議之 Sprint 68（`OrderService` 測試強化，順延至 Sprint 69+）。

---

## 安全修復 🔴

- **Booking 付款/預訂 IDOR 修復（`DEF-023`）**：比照 Order 側 `DEF-018`/`DEF-019` 既有修復模式，補齊四處擁有權檢查——
  - `PaymentStateService.getBookingPaymentState`（新增 `checkBookingOwnership`）
  - `BookingService.getBooking`/`updateBooking`（新增 `checkBookingOwnership`）
  - `PaymentService.processBookingPayment`（新增 `checkBookingPaymentOwnership`）

  修復前：任何登入使用者皆可查詢/操作他人的訂房付款狀態與訂房資料（無擁有權過濾）。
  修復後：買家限本人（`booking.getUserId()`），`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 放行，越權回 403/`E_1007`；檢查置於狀態檢查之前，避免向未授權者洩漏資源狀態。

- **`booking:read` 權限收斂**：`RolePermissionMapping.java` 移除 `GUEST` 角色的 `Permission.BOOKING_READ`，訪客僅保留 `PRODUCT_READ`/`ROOM_READ`。純 Java 常數變更，**無 migration**（已確認無對應 DB 權限表）。

## 測試 / 驗證 ✅

- **新增/擴充測試**（共 15 個）：`PaymentStateServiceTest` +2、`BookingServiceOwnershipTest`（新檔）+6、`PaymentServiceOwnershipTest` +3、`RolePermissionMappingTest`（新檔）+4。
- **後端單元**：`mvn verify -Pintegration-test` unit 階段 **557 tests，0 fail**。
- **後端完整整合**：`mvn verify -Pintegration-test`（含 failsafe）**557 + 342 = 899 tests，0 fail**。
- **checkstyle-main / checkstyle-test**：0 violations。
- **schema 漂移守門**：`make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）。
- **開發-編譯-測試循環**：每完成一個方法的修復即編譯 + 測試通過，未累積驗證。
- **前端**：本 Sprint 無前端變動，未執行。

## 技術決策 / 已知限制 ⚠️

- **修復範圍精準限定於使用者明確授權的四處**：`BookingService.cancelBooking` 盤點時發現同樣缺擁有權檢查，但不在原始 `DEF-023` 記錄與本次授權範圍內，未修改，另立待決策事項。
- **`HOST` 角色的 `booking:update` 權限與修復後行為潛在落差**：`PUT /v2/bookings/{id}` 為買家/賣家共用端點，修復後僅放行買家本人或 admin；若 `HOST` 對自己房源訂房有合法更新需求，將收到 403，需另評估是否比照 `DEF-019` 收尾時 `LogisticsService` 補上的租戶側擁有權判斷。
- **與 Order 側完全對齊的修復模式**：helper 命名（`checkBookingOwnership`/`checkBookingPaymentOwnership`）、錯誤碼（`E_1007`）、檢查順序（置於狀態檢查之前）皆比照 `PaymentStateService.checkOrderOwnership`/`PaymentService.checkOrderPaymentOwnership`，維持程式碼庫一致性。

## 資料庫遷移 🗄️

- 無（schema-free；`booking:read` 權限調整為純 Java 常數變更）。

## 內含 Commit（Sprint 68）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 68 Plan | 6119523 | Booking IDOR 緊急修復計劃（2 US / 6 SP）|
| US-001+US-002 | 3c7fce6 | 四處擁有權檢查修復 + GUEST 移除 booking:read + 測試 |
| Sprint 68 收尾 | （本次） | Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**基於**: AISDLC v0.09 Release Management Workflow
