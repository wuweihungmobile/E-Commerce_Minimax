# Sprint 69 Review / Sprint 69 評審會議

> **Sprint 編號**: Sprint 69
> **期間**: 2028-06-04 ~ 2028-06-17（規劃）／實作完成 2026-07-05
> **評審日期**: 2026-07-05
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 多 Sprint 測試強化計劃（第三階段）——`OrderService`（訂單狀態機核心）單元測試從 0 建立

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | OrderService 單元測試從 0 建立至 7 方法完整覆蓋 | 8 | ✅ 完成 |

**8 SP 全數完成**。恢復因 Sprint 68 緊急安全修復（`DEF-023`）而順延的例行測試強化排程，完成 `SPRINT_67_PLAN.md` 第 6 節原建議之 Sprint 68 目標（`OrderService` 測試強化）。

---

## 2. 交付內容

### 探勘與前置確認

- 盤點確認 `OrderService` 共 7 個 public 方法：`createOrderFromCart`、`createBooking`、`getUserOrders`、`getOrder`、`updateOrderStatus`、`cancelOrder`、`getOrderStateLogs`。
- 誠實確認測試覆蓋現況：`OrderService` **完全沒有**專屬的 Mockito 單元測試檔案（真的是 0，這次探勘方向與過去 Sprint 67 的「並非零測試」提醒相反）；既有覆蓋僅來自不觸及 Service 本身的 `OrderStateMachineTest`，以及需要完整 Spring Context + 真實 PostgreSQL 的 `OrderControllerE2ETest`/`OrderPaymentControllerE2ETest`/`BuyerOrderJourneyE2ETest`/`M11LogisticsOrderIntegrationTest` 等 Controller 層 E2E/整合測試（間接涵蓋部分 happy path）。

### 測試（新檔）

- **`OrderServiceTest.java`**（新檔，42 個測試）：
  - `createOrderFromCart`（PRODUCT 分支）：happy path、使用者/租戶不存在（E_1006/E_2000）、購物車為空（E_5004）、刊登項目不存在/未上架（E_3000/E_3002）、SKU 找不到優雅降級為 null。
  - `createOrderFromCart`（ROOM 分支，`createRoomOrder` 私有輔助）：happy path、必填欄位缺漏（E_9005）、日期區間不合法/0 晚（E_4003）、房源不存在/型別錯誤/未上架（E_3000/E_9005/E_3002）、租戶不存在時三層 fallback（系統租戶 by id → `platform` slug → 最終 E_2000）。
  - `createBooking`：驗證必填欄位（E_9005）、日期不合法（E_4003）、合法輸入時拋 `UnsupportedOperationException`（現況為未完成 stub，如實記錄非本 Sprint 修復範圍）。
  - `getUserOrders`：分頁大小上限裁切為 100、排序方向透傳驗證。
  - `getOrder`/`cancelOrder`/`getOrderStateLogs`：擁有者本人放行、非擁有者 E_1007、admin 放行、資源不存在 E_5000。
  - `updateOrderStatus`：合法/不合法狀態轉換（E_5001）、資源不存在（E_5000）。
  - `cancelOrder` 額外驗證狀態機聯動：CREATED/CONFIRMED 直接取消（1 筆狀態日誌）、PAID 取消後自動觸發 REFUNDING（2 筆狀態日誌、2 次 save）、不可取消狀態回 E_5002。

### 文件

- **`SPRINT_69_PLAN.md`**（新檔）：本 Sprint 計劃，含前置條件確認（誠實記錄零測試現況 + `DEF-024` 發現）、User Story/AC、後續 Sprint 建議清單。
- **`DEFERRED_ITEMS_TRACKER.md`**：新增 `DEF-024`（`updateOrderStatus` 擁有權檢查缺口）於「🔴 高優先級」表格，並補上「Sprint 69」歷史紀錄條目。

---

## 3. 🔴 重大發現：DEF-024（未修改生產程式碼，待決策 → 已決策插隊 Sprint 70）

撰寫測試過程中逐方法比對 7 個方法的擁有權檢查邏輯，發現：

- 同檔案內 `getOrder`（`DEF-018` 已修復過）、`cancelOrder`、`getOrderStateLogs` 皆有 `!isAdmin && !userId.equals(order.getUserId())` → `E_1007` 的 owner-or-admin 擁有權檢查。
- **唯獨 `updateOrderStatus` 完全沒有這段檢查**，直接呼叫無租戶過濾的 `findOrderById` 就修改任意訂單狀態。
- 進一步查證 `OrderController`（僅 `@PreAuthorize("hasAuthority('order:update')")` 把關）與 `RolePermissionMapping.java`（確認 `order:update` 由 **SELLER、STORE_OWNER、ADMIN、SUPER_ADMIN** 四種角色持有，非僅單一平台 ADMIN），得出結論：由於系統為多租戶各自擁有 SELLER/STORE_OWNER 角色，此缺口形同**任一租戶的賣家可對任意 orderId（含其他租戶的訂單）執行狀態轉換**，屬跨租戶 IDOR，性質與 `DEF-018/019/023` 系列相同。

依任務指示，本 Sprint **未自行修改**生產程式碼，也**未撰寫「證明漏洞存在」的測試**（避免將現況鎖進測試基準線），僅記錄為 `DEF-024` 待人工決策。**收尾時使用者看到本發現後，決定比照 Sprint 68（`DEF-023`）處理模式，另立 Sprint 70 緊急修復，插隊優先於例行測試強化排程。**

---

## 4. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 後端單元（`mvn verify -Pintegration-test` 的 unit 階段）| ✅ **603 tests，0 fail**（含本 Sprint 新增 `OrderServiceTest` 42 個） |
| 後端完整整合（`mvn verify -Pintegration-test`，含 failsafe）| ✅ **603 + 342 = 945 tests，0 fail** |
| checkstyle-main / checkstyle-test | ✅ 0 violations |
| `make validate-schema` | ✅ 無漂移（本 Sprint 無 entity/migration 變更） |
| 開發-編譯-測試循環 | ✅ 依 `OrderService` 方法分組（PRODUCT/ROOM 分支、createBooking+getUserOrders+getOrder、updateOrderStatus+cancelOrder+getOrderStateLogs）逐段撰寫並立即編譯 + 執行驗證，發現的 3 個測試 fixture 缺陷（influence order fallback 斷言方式、租戶 mock 缺漏、orderType 未設定）皆當場修正，未累積到最後才驗證 |
| 前端 | 本 Sprint 無前端變動，未執行 |

---

## 5. 誠實揭露（Rule 12）

1. **測試覆蓋現況方向與過去提醒相反**：任務指示提醒「不要假設是零測試」，但本次探勘確認 `OrderService` 專屬單元測試層級確實是 0（非任務指示所暗示的「表面上是 0，實際上有部分覆蓋」情境）；已於 `SPRINT_69_PLAN.md` 前置條件確認表格中明確區分「單元測試層級 0」與「並非完全無測試涵蓋」（有 E2E/整合測試間接涵蓋 happy path）兩個不同陳述，避免誤導。
2. **`Order.tenantId` 影子欄位測試陷阱**：`Order.tenantId` 為 `insertable=false/updatable=false`，僅由 Hibernate 讀取 DB 時回填；純 Mockito mock（無真實持久化）情境下 `OrderResponse.getTenantId()` 恆為 null，最初撰寫的兩個租戶 fallback 測試因此斷言失敗。改以 `ArgumentCaptor<Order>` 驗證實際傳入 `orderRepository.save` 的 `Order.getTenant()` 物件解決，已在測試註解中記錄此陷阱供未來參考（單元測試層級的「erp-tenant-test-seeding-gotcha」等價教訓）。
3. **`resolveTenant` 與 `calculateNights` 呼叫順序**：`createRoomOrder` 內 `resolveTenant` 在 `calculateNights` 之前執行，最初撰寫「0 晚」邊界測試時未 mock 租戶，導致實際拋出 `E_2000`（租戶解析失敗）而非預期的 `E_4003`（日期不合法），並非生產程式碼 bug，而是測試 fixture 未依實際執行順序佈置 mock，已修正。
4. **`createBooking` 現況為未完成 stub**：驗證通過後固定拋出 `UnsupportedOperationException`（缺 `RoomBookingService` 實作），測試如實記錄此現況而非視為需修復的 bug，非本 Sprint 範圍。
5. **發現 `DEF-024` 跨租戶 IDOR**：詳見上方第 3 節，未自行修改，已記錄並經使用者決策排入 Sprint 70。

---

## 6. Demo 重點

- **`OrderService` 單元測試缺口清零**：7 個 public 方法皆有 Mockito 隔離測試涵蓋正常/邊界/錯誤路徑，與 `PaymentStateService`（Sprint 67）、Booking 擁有權隔離（Sprint 68）合計完成金流/訂單/訂房三大核心模組測試強化第一輪。
- **`createOrderFromCart` 內部 PRODUCT/ROOM 雙分支完整覆蓋**：含 ROOM 訂單三層租戶 fallback（系統租戶 by id → `platform` slug → E_2000）與 `cancelOrder` 的 PAID→CANCELLED→REFUNDING 自動退款狀態機聯動皆有測試驗證。
- **誠實揭露的安全發現直接轉化為下一步行動**：`DEF-024` 發現後立即記錄、不擅自修改，使用者決策後隨即排入 Sprint 70，展現「大聲失敗」原則的實際運作。

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
