# Sprint 69 Retrospective / Sprint 69 回顧會議

> **Sprint 編號**: Sprint 69
> **期間**: 2028-06-04 ~ 2028-06-17（規劃）／實作完成 2026-07-05
> **回顧日期**: 2026-07-05
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 8 SP（US-001）|
| 完成 SP | 8 SP（全數）|
| 主題 | 多 Sprint 測試強化計劃（第三階段）：`OrderService`（訂單狀態機核心）單元測試從 0 建立 |

---

## 2. 做得好的（What went well）

- **依方法分組、逐段編譯+測試，未累積開發**：`OrderServiceTest.java` 依 `OrderService` 的 7 個方法分 3 組撰寫（PRODUCT/ROOM 分支 → `createBooking`+`getUserOrders`+`getOrder` → `updateOrderStatus`+`cancelOrder`+`getOrderStateLogs`），每組寫完立即 `mvn -o test -Dtest=OrderServiceTest` 驗證，過程中發現的 3 類測試 fixture 缺陷（`Order.tenantId` 影子欄位無法在純 mock 情境觀察、`resolveTenant`/`calculateNights` 呼叫順序誤判、`orderType` 未在 fixture 設定導致 NPE）皆當場定位並修正，未累積到最後才發現一堆問題。
- **逐方法比對擁有權檢查邏輯，主動發現 `DEF-024`**：撰寫 `getOrder`/`cancelOrder`/`getOrderStateLogs` 測試時注意到三者皆有相同的 owner-or-admin inline pattern，反向比對 `updateOrderStatus` 時發現該方法完全沒有這段邏輯；沒有停在「這個方法就是這樣設計的」表面假設，而是進一步查證 Controller 層 `@PreAuthorize` 與 `RolePermissionMapping.java` 的角色權限對應，才確認這是跨租戶 IDOR 而非刻意設計（比照 Sprint 67/68 建立的查證模式）。
- **守住「不自行修改」邊界，同時提供充分決策資訊**：發現 `DEF-024` 後沒有比照 Sprint 68 的修復模式直接動手修，而是記錄清楚的技術細節（哪些角色持有權限、影響範圍、可比照的既有修復 pattern）供使用者快速決策；使用者也確實在看到揭露後立即決定排入 Sprint 70，證明「大聲失敗＋提供決策所需資訊」比「自行假設處理」更有效率。
- **未撰寫「證明漏洞存在」的測試**：面對 `DEF-024`，選擇只在「合法呼叫路徑」下驗證行為，不寫一個刻意示範「非擁有者也能改狀態成功」的測試鎖進測試基準線——避免讓修復後需要砍掉重寫的測試，也避免測試套件看起來像是「認可」了這個安全缺口。
- **背景長時間指令的等待協議完全遵守**：`mvn verify -Pintegration-test` 與 `make validate-schema`（皆預期需要較長時間）均以背景執行 + 明確 log 路徑回報後結束回合等待協調者確認，未發生 Sprint 66/67/68 曾出現的「結束回合空等」問題。

---

## 3. 待改善的（What to improve）

- **前置規劃時對 `createOrderFromCart` 複雜度的低估**：規劃階段將 `createOrderFromCart` 視為「一個方法」，但實際上內部 PRODUCT/ROOM 兩條分支的邏輯複雜度接近兩個獨立方法（ROOM 分支還有三層租戶 fallback），導致這一個方法就佔了 42 個測試中的 20 個。未來規劃類似「單方法內多分支」的核心邏輯測試強化時，應在前置盤點階段就估算分支數量而非只數方法數量，避免 SP 估點與實際工作量落差。
- **`Order.tenantId` 影子欄位陷阱在單元測試層級首次踩雷**：專案既有的「erp-tenant-test-seeding-gotcha」教訓記錄的是整合測試層級（真實 DB seeding）的陷阱，本 Sprint 才發現同一根因（`insertable=false/updatable=false` 欄位）在純 Mockito 單元測試層級也會造成斷言失敗，且更難察覺（沒有 DB 可以除錯，只能從程式碼邏輯推導）。建議將此教訓也整理進團隊記憶，避免未來寫其他 Service（如 `OrderItem`/`Listing` 等同樣有影子欄位的 entity）的單元測試時重蹈覆轍。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| DEF-024 | `OrderService.updateOrderStatus` 跨租戶 IDOR 修復 | 比照 `DEF-018/019/023` 既有 owner-or-admin/租戶檢查 pattern 補齊 | Dev David（實作）+ PO Victoria（範圍拍板） | 🔴 高（已決策插隊） | **Sprint 70** |
| （技術債，未立案）| 影子欄位（shadow field）單元測試陷阱記憶沉澱 | 將 `Order.tenantId` 類型陷阱（insertable=false/updatable=false 純 mock 無法回填）整理為可複用的測試撰寫指引，避免未來其他 entity 重蹈覆轍 | QA Quincy | 🟢 低 | 待排入（文件/記憶維護） |
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |
| （待決策，延續自 Sprint 68）| `HOST` 角色 `booking:update` 權限與 `updateBooking` 修復後行為落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403，需評估是否需比照 `LogisticsService` 補租戶側檢查 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待決策（未變更，非本 Sprint 範圍）|

---

## 5. Sprint 68 → Sprint 69 Action Items 追蹤結果

| Action Item | 內容 | Sprint 69 達成狀態 |
|------------|------|---------------------|
| `OrderService` 測試強化 | Sprint 67 第 6 節建議之 Sprint 68 目標，因 `DEF-023` 緊急插隊而順延 | ✅ **本 Sprint 完成**（42 個測試，7 方法完整覆蓋） |
| `HOST` 角色 `booking:update` 權限落差（待決策2）| 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | 未啟動（續留，非本 Sprint 範圍，本 Sprint 聚焦 `OrderService`）|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S65 | 8 |
| S66 | 8 |
| S67 | 8 |
| S68 | 6（緊急安全修復插隊，不計入例行測試強化排程節奏）|
| **S69** | **8**（恢復例行測試強化排程節奏）|

> **觀察**：S69 恢復連續 8 SP 的例行測試強化節奏。品質：後端單元 **603 tests**（新增 `OrderServiceTest` 42 個）+ 完整整合 **342 tests**，合計 **945 tests 0 fail**、`make validate-schema` 無漂移。**Order 模組單元測試缺口清零**，但同時發現 `DEF-024` 新安全缺口，活躍高優先級延後項目回升為 1（已決策排入 Sprint 70 緊急修復）。

---

## 7. 下一步

> **檢查點**：Sprint 69 已完成並 push。**下一 Sprint（Sprint 70）**：使用者已決策比照 Sprint 68 模式，緊急插隊修復 `DEF-024`（`OrderService.updateOrderStatus` 跨租戶 IDOR），優先於例行測試強化排程。Sprint 70 完成並結案後，恢復例行排程：Sprint 71 建議處理 `BookingService`/`RoomCalendarService` 尚未涵蓋的其餘業務邏輯方法測試強化，Sprint 72+ 建議 ERP 模組整體測試。

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
