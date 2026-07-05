# Sprint 68 Review / Sprint 68 評審會議

> **Sprint 編號**: Sprint 68
> **期間**: 2026-07-05（緊急插入）
> **評審日期**: 2026-07-05
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 🔴 緊急安全修復——Booking 付款/預訂擁有權檢查缺口（IDOR，`DEF-023`）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | Booking 付款/預訂擁有權檢查修復（DEF-023） | 5 | ✅ 完成 |
| US-002 | `booking:read` 權限收斂——移除 GUEST 角色 | 1 | ✅ 完成 |

**6 SP 全數完成**。使用者明確授權插入緊急安全修復 Sprint，優先於例行測試強化排程（`OrderService` 順延至 Sprint 69+），修復 `DEF-023`（Sprint 67 撰寫測試時發現的 Booking 模組 IDOR 缺口）。

---

## 2. 交付內容

### 生產程式碼修改（比照 Order 側 `DEF-018`/`DEF-019` 既有修復模式）

- **`PaymentStateService.java`**：`getBookingPaymentState` 新增擁有權檢查（新增 `checkBookingOwnership` helper）——買家限本人（`booking.getUserId()`），`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 放行，越權回 403/`E_1007`。
- **`BookingService.java`**：`getBooking`/`updateBooking` 新增擁有權檢查（新增 `checkBookingOwnership` helper，兩方法共用），`updateBooking` 之檢查置於狀態檢查之前，避免向未授權者洩漏預訂狀態。
- **`PaymentService.java`**：`processBookingPayment`（私有方法，`/v2/payments` 對外入口的預訂付款分支）新增擁有權檢查（新增 `checkBookingPaymentOwnership` helper），置於狀態檢查之前。
- **`RolePermissionMapping.java`**：`GUEST` 角色移除 `Permission.BOOKING_READ`，僅保留 `PRODUCT_READ`/`ROOM_READ`。純 Java 常數變更（`EnumMap`），確認無對應 DB 權限表，**無 migration**。

### 測試

- **`PaymentStateServiceTest.java`**：`getBookingPaymentState` 新增 2 個測試（`UT-PAY-STATE-006b`/`006c`：非本人 403、admin 放行）。
- **`BookingServiceOwnershipTest.java`**（新檔，6 個測試）：`getBooking`/`updateBooking` 各 3 個（他人 403、本人放行、admin 放行）。
- **`PaymentServiceOwnershipTest.java`**：擴充 3 個測試（`processBookingPayment` 的他人 403、本人放行、admin 放行）。
- **`RolePermissionMappingTest.java`**（新檔，4 個測試）：GUEST 無 `BOOKING_READ`、GUEST 保留 `PRODUCT_READ`/`ROOM_READ`、`BUYER`/`ADMIN` 的 `BOOKING_READ` 不受影響。

### 文件

- **`SPRINT_68_PLAN.md`**（新檔）：本 Sprint 計劃，含前置條件確認（讀取 `DEF-023` 記錄 + Order 側修復 commit 之實際 diff）、User Story/AC、已知殘留事項。
- **`DEFERRED_ITEMS_TRACKER.md`**：`DEF-023` 自「🔴 高優先級 - 待決策」移至「已完成延後項目」，記錄解決方式；新增「Sprint 68」歷史紀錄條目。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 後端單元（`mvn verify -Pintegration-test` 的 unit 階段）| ✅ **557 tests，0 fail**（含本 Sprint 新增/擴充 15 個擁有權測試） |
| 後端完整整合（`mvn verify -Pintegration-test`，含 failsafe）| ✅ **557 + 342 = 899 tests，0 fail** |
| checkstyle-main / checkstyle-test | ✅ 0 violations |
| `make validate-schema` | ✅ 無漂移（本 Sprint 無 entity/migration 變更） |
| 開發-編譯-測試循環 | ✅ 每完成一個方法的修復，立即編譯 + 執行對應測試通過，未累積到最後才驗證 |
| 前端 | 本 Sprint 無前端變動，未執行 |

---

## 4. 誠實揭露（Rule 12）

1. **修復範圍嚴格限定於使用者明確授權的四處**：`getBookingPaymentState`/`getBooking`/`updateBooking`/`processBookingPayment`。盤點過程中發現 `BookingService.cancelBooking` 同樣完全沒有擁有權檢查（僅 `findBookingById` 無過濾），理論上與 `DEF-023` 同源，但**不在原始 `DEF-023` 記錄與本次任務指示明確列出的範圍內**，依 Rule 3（精準改動）未擅自擴大修復範圍，另立追蹤事項待人工決策是否需要後續 Sprint 處理。
2. **`HOST` 角色的 `booking:update` 權限與修復後行為的潛在落差**：`PUT /v2/bookings/{id}` 為買家/賣家共用同一端點，`RolePermissionMapping` 中 `HOST` 角色持有 `BOOKING_UPDATE` 權限。修復後 `updateBooking` 僅放行「本人（買家）或 admin」，若 `HOST` 對自己房源的訂房有合法更新需求（現無證據顯示此端點被如此使用），將收到 403。此為超出本次授權的架構決策，已在 `SPRINT_68_PLAN.md` 第 6 節記錄，建議由 PO/SD 評估是否需要比照 `DEF-019` 收尾時 `LogisticsService` 額外補上的租戶側擁有權判斷。
3. **`booking:read` 權限調整方式的驗證過程**：修復前已透過 `grep` 確認 `RolePermissionMapping.java` 為純記憶體 Java 常數（`EnumMap<UserRole, Set<Permission>>`），Flyway migration 中出現的 `GUEST` 字樣僅為 `users.role` 欄位列舉值，與權限映射無關；因此採用風險最低的程式碼常數調整，未新增任何 migration，符合任務指示「若程式碼常數即可調整，優先採用」的要求。
4. **未發現需額外修正的其他生產程式碼真實 bug**：本 Sprint 撰寫的擁有權檢查與既有 Order 側修復模式完全對齊（helper 命名、檢查順序、`E_1007` 錯誤碼），未在過程中發現與本次修復無關的其他缺陷。

**Addendum（Sprint 68 收尾並 push 後）**：使用者看到第 1 點誠實揭露的 `cancelBooking` 殘留問題後，決定立即授權追加修復。已沿用同一個 `checkBookingOwnership` helper 補上（不重複造新方法），新增 4 個測試（他人取消 403、本人正常取消、本人取消不可取消狀態驗證擁有權先於狀態檢查、admin 取消他人預訂放行），全量回歸 0 fail。詳見 `DEFERRED_ITEMS_TRACKER.md` DEF-023 最終條目。第 2 點（`HOST` 角色權限落差）維持待決策，本次追加未變更該行為。

---

## 5. Demo 重點

- **Booking 模組 IDOR 缺口清零**：`PaymentStateService.getBookingPaymentState`、`BookingService.getBooking`/`updateBooking`、`PaymentService.processBookingPayment` 四處皆已補齊擁有權檢查，任何登入使用者不再能查詢/操作他人的訂房付款狀態與訂房資料。
- **`booking:read` 權限收斂**：`GUEST` 角色不再持有 `booking:read`，訪客僅能瀏覽商品/房源，無法查詢任何預訂資料。
- **與 Order 側修復一致的安全模式**：本次修復完全比照 `DEF-018`/`DEF-019` 既有的 `checkOrderOwnership`/`checkOrderPaymentOwnership` 模式（helper 命名、`E_1007` 錯誤碼、檢查順序置於狀態檢查之前），使 Order 與 Booking 兩大交易模組的擁有權隔離機制達到一致性。

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
