# Sprint 69 計劃 / Sprint 69 Plan

> **Sprint 編號**: Sprint 69
> **期間**: 2028-06-04 ~ 2028-06-17 (2 週，接續 Sprint 68 緊急插入後的排程)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-05
> **基於**: `SPRINT_67_PLAN.md` 第 6 節「後續 Sprint 待處理清單」建議之 Sprint 68 目標——`OrderService`（7 方法，訂單狀態機核心）測試強化，因 Sprint 68 插入 `DEF-023` 緊急安全修復而順延至本 Sprint
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 68 收尾狀態 | ✅ 已收尾並 push（commit `1ac2e37`，追加修復 commit `447fe4c`） | `DEF-023`（Booking 付款/預訂/取消擁有權缺口）五處 + 追加一處，共六處完全修復並結案 |
| `OrderService` 方法盤點 | ✅ 完成：共 **7 個 public 方法**——`createOrderFromCart`、`createBooking`、`getUserOrders`、`getOrder`、`updateOrderStatus`、`cancelOrder`、`getOrderStateLogs` | 與 `SPRINT_67_PLAN.md` 第 6 節所述「7 方法」數量一致 |
| ⚠️ **前置認知落差誠實揭露** | 探勘證實：`OrderService` **完全沒有** `OrderServiceTest.java` 這類以 Mockito mock repository 的純單元測試檔案（此點與任務指示「不要假設是零測試」的提醒相符——本 Sprint 確認落差方向相反：真的是零，但並非毫無測試覆蓋）。實際覆蓋現況：獨立的 `OrderStateMachineTest.java`（純狀態機邏輯，不觸及 `OrderService` 本身）；另有 `OrderControllerE2ETest`、`OrderPaymentControllerE2ETest`、`BuyerOrderJourneyE2ETest`、`M11LogisticsOrderIntegrationTest` 這類需要完整 Spring Context + 真實 PostgreSQL 的 Controller 層 E2E/整合測試，間接透過 HTTP 呼叫涵蓋部分 `OrderService` 流程（happy path 為主）。**本 Sprint 目標為建立 `OrderServiceTest.java`，以 Mockito 隔離 repository/外部服務，補齊 7 個方法本身的正常/邊界/錯誤路徑單元測試**，非取代既有 E2E/整合測試 | 依 Rule 12「大聲失敗」誠實揭露：純單元測試層級確實是 0，但不等於「完全無測試涵蓋」，避免誤導 |
| 🔴 額外發現：`updateOrderStatus` 擁有權檢查缺口（待人工決策，本 Sprint **不**修改） | 撰寫測試過程中逐方法比對發現：同檔案內 `getOrder`（DEF-018 修復過）、`cancelOrder`、`getOrderStateLogs` 皆有 owner-or-admin 擁有權檢查（`!isAdmin && !userId.equals(order.getUserId())` → E_1007），**唯獨 `updateOrderStatus` 完全沒有這段檢查**，直接呼叫 `findOrderById`（無租戶過濾）就修改任意訂單狀態。追查 `OrderController`：`updateOrderStatus` 端點僅以 `@PreAuthorize("hasAuthority('order:update')")` 把關；查證 `RolePermissionMapping.java` 確認 `order:update` 由 **SELLER、STORE_OWNER、ADMIN、SUPER_ADMIN** 四種角色持有（非僅單一平台 ADMIN）。由於系統設計為多租戶各自擁有 SELLER/STORE_OWNER 角色，此缺口形同**任一租戶的賣家可對任意 orderId（含其他租戶的訂單）執行狀態轉換**（如任意改為 SHIPPING/CANCELLED/REFUNDING），屬跨租戶 IDOR，性質與 `DEF-018/019/023` 系列一致但先前未被涵蓋。已登記為 `DEF-024`（見 `DEFERRED_ITEMS_TRACKER.md`），**待 PO/Security owner 決策是否及何時修復**，本 Sprint 依任務指示僅記錄不修改，且未撰寫「證明漏洞存在」的測試（避免將現況鎖進測試基準線） | 屬於超出「幫 OrderService 補測試」授權範圍的安全決策，且與 Sprint 68 `DEF-023` 修復模式高度相似，適合下一輪安全修復 Sprint 一併處理或單獨插隊，由使用者拍板 |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. Sprint 69 目標

> **主題**: 多 Sprint 測試強化計劃（第三階段）——`OrderService`（訂單狀態機核心）單元測試從 0 建立

為訂單狀態機核心 `OrderService` 建立完整的 Mockito 單元測試（`OrderServiceTest.java`），涵蓋 7 個 public 方法的正常流程、邊界案例（含 `createOrderFromCart` 內部 PRODUCT/ROOM 兩條分支、`OrderStateMachine` 轉換合法性、擁有權檢查、租戶回退邏輯）與錯誤路徑，並誠實記錄過程中發現的 `updateOrderStatus` 擁有權檢查缺口（`DEF-024`）供後續決策。

---

## 2. User Story

### US-001：OrderService 單元測試從 0 建立至 7 方法完整覆蓋

> **SP**: 8 | **優先級**: P1 | **狀態**: ✅ 完成

**AC-001-1**：新增 `OrderServiceTest.java`，為 `createOrderFromCart`（PRODUCT 分支：happy path、使用者/租戶不存在 E_1006/E_2000、購物車為空 E_5004、刊登項目不存在/未上架 E_3000/E_3002、SKU 找不到時優雅降級為 null；ROOM 分支：happy path、必填欄位缺漏 E_9005、日期區間不合法 E_4003、房源不存在/型別錯誤/未上架 E_3000/E_9005/E_3002、租戶不存在時回退系統租戶/`platform` slug/最終 E_2000 三層 fallback）建立完整單元測試。

**AC-001-2**：為 `createBooking`（目前為未完成 stub，驗證通過後拋 `UnsupportedOperationException`）、`getUserOrders`（分頁大小上限裁切為 100、排序方向透傳）、`getOrder`/`cancelOrder`/`getOrderStateLogs`（擁有者本人放行、非擁有者 E_1007、admin 放行、資源不存在 E_5000）、`updateOrderStatus`（合法/不合法狀態轉換 E_5001、資源不存在 E_5000）建立測試，涵蓋正常路徑與錯誤路徑。

**AC-001-3**：`cancelOrder` 額外驗證狀態機聯動：CREATED/CONFIRMED 直接取消（1 筆狀態日誌）、PAID 取消後自動觸發 REFUNDING（2 筆狀態日誌、2 次 `orderRepository.save`）、狀態不可取消（如 SHIPPING）回 E_5002。

**AC-001-4**：測試風格比照既有 `PaymentServiceOwnershipTest`/`BookingServiceOwnershipTest`/`PaymentStateServiceTest`，使用 `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` mock 全部 10 個 repository/service 依賴，`TenantContext`/`SecurityContextHolder` 於 `@AfterEach` 清理；因 `Order.tenantId` 為 `insertable=false/updatable=false` 影子欄位，純 mock 情境下無法透過 `OrderResponse.getTenantId()` 觀察租戶回退結果，改以 `ArgumentCaptor<Order>` 驗證實際傳入 `orderRepository.save` 的 `Order.getTenant()`。

**AC-001-5**：過程中若發現 `OrderService` 生產程式碼真實 bug 則修正並記錄；若發現行為疑似缺陷但屬於超出測試補齊範圍的安全/架構決策（如本 Sprint 發現的 `updateOrderStatus` 擁有權缺口），停止修改、記錄為待決策項目（`DEF-024`），不自行假設或修復。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | OrderService 單元測試從 0 建立至 7 方法完整覆蓋 | 8 | P1 |
| **合計** | | **8** | |

> **Velocity 參考**：貼近 Sprint 60-67（皆 8 SP）之單 Sprint 產能。訂單狀態機為核心模組，與付款/物流/退款皆有連動，單一 Sprint 聚焦一個 Service 的完整覆蓋，不與其他模組並行。

---

## 4. Definition of Done

- [x] US-001：新增 `OrderServiceTest.java`（42 個測試，涵蓋 7 個方法的正常/邊界/錯誤路徑）
- [x] 確認**未修改任何 `OrderService` 生產程式碼**（本 Sprint 測試撰寫過程未發現需修正的真實 bug；`updateOrderStatus` 擁有權檢查缺口記錄為 `DEF-024`，不在本 Sprint 修改）
- [x] 後端單元 + 真 DB 整合全量回歸（`mvn verify -Pintegration-test`）**603 + 342 = 945 tests，0 fail**
- [x] `make validate-schema` 無漂移（本 Sprint 無 migration）
- [x] `DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-024`（updateOrderStatus 擁有權檢查缺口，使用者已決定另立 Sprint 70 緊急修復，不在本 Sprint 範圍）
- [x] Sprint 69 Review / Retro / Release Notes + trackers
- [x]（檢查點）本 Sprint 收尾後立即 push

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端測試 | `OrderServiceTest.java`（新檔，42 個測試） |
| 追蹤文件 | `DEFERRED_ITEMS_TRACKER.md`（新增 DEF-024） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration、無前端變動、無生產程式碼變動**（純測試補強 + 一項待決策發現的文件化記錄）。

---

## 6. 後續 Sprint 待處理清單（多 Sprint 測試強化計劃）

依風險排序，供 Sprint 70+ 規劃參考：

1. **🔴 Sprint 70（使用者已決策，緊急插隊）**：`DEF-024`——`updateOrderStatus` 跨租戶 IDOR，性質與 `DEF-018/019/023` 系列相同。使用者於 Sprint 69 收尾時已看到本發現並決定**另開 Sprint 70 緊急修復**，比照 Sprint 68（`DEF-023`）處理模式插隊優先於例行測試強化排程，建議儘速比照既有 `checkOrderOwnership`/`isAdmin` inline pattern 補齊租戶或擁有者檢查
2. **Sprint 71（原排程遞延，建議）**：`BookingService` + `RoomCalendarService` 尚未涵蓋的其餘方法測試強化（`DEF-023` 已涵蓋擁有權相關三方法，其餘業務邏輯方法如 `checkAvailability`/`createBooking`/`getCalendar` 等仍待系統性單元測試盤點）
3. **Sprint 72+（建議）**：ERP 模組整體（Supplier/StockMovement/PurchaseOrder/Inventory，測試目錄完全不存在）
4. 其餘：`ReviewService`（14 方法）、`CmsService`（11 方法）、`ChatService`、`LogisticsService`、`NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService`

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
