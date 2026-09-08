# Sprint 144 Plan — 訂單/付款/客服工單併發競態技術債查證與修復（最後一批）

**Sprint**: Sprint 144
**日期**: 2026-09-08

---

## 1. 本輪範圍與方法論

延續 Sprint 137~143 做法（不另開 Workflow，主控 session 直接逐筆重讀原始碼、獨立判斷、動手修復）。Sprint 143 完成治理雜項主題後，剩餘 9 筆技術債，這是 Sprint 136 併發競態全掃 52 筆技術債的**最後一批**。範圍涵蓋 `OrderService`（`DEF-124`/`161`）、`PaymentStateService`（`DEF-125`/`162`）、`SupportTicketService`（`DEF-139`/`163`），以及維持不動的 `DEF-145`/`146`/`147`（Sprint 137 已查證為死流程，本輪不重新處理）。

**查證結果**：逐一重讀原始碼、entity 定義、既有 DB 約束、既有 CAS 修法後，**6 筆全數確認需要動作**，其中 4 筆是真實併發缺陷並修復、1 筆查證確認原始「死碼」標籤屬實（不修復）、1 筆查證發現後果比原標籤更嚴重並修復：

| ID | 方法 | 查證結論 |
|----|------|---------|
| DEF-124 | OrderService.cancelOrder | 真實：訂單本身的取消動作可被併發觸發兩次，各自呼叫一次庫存釋放/優惠券退還 |
| DEF-125 | PaymentStateService.mockPaymentSuccess | 真實，與已修復的 `markStripePaymentSucceeded` 同型（同一 Sprint 136 §4.3 已示範修法，但本方法未被選入當輪 19 筆） |
| DEF-139 | SupportTicketService.createTicket | 真實：`ticket_number` 已有 DB 唯一約束兜底，但敗方收到原始 500 而非乾淨重試 |
| DEF-161 | OrderService.createRoomOrder | ✅ **查證確認原始「死碼路徑」標籤屬實**：前端零呼叫點，維持不修復 |
| DEF-162 | PaymentStateService.markStripeRefunded | ⚠️ **查證發現比原標籤（「無金流/安全後果」）更嚴重**：webhook 重複送達會寫入重複稽核紀錄 |
| DEF-163 | SupportTicketService.updateStatus | 真實：`SupportTicket` 無 `@DynamicUpdate`，與 `assignTicket` 交錯的全欄位覆寫 |

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

比照 Sprint 136~143 慣例：每完成一個邏輯修復單元，立即 `mvn compile` + 執行相關測試類別，確認通過才進入下一項；全部完成後才跑 `mvn -o verify` 全量回歸 + `checkstyle:check@checkstyle-main`/`@checkstyle-test` + `make validate-schema`/`validate-schema-doc`。

**🔴 過程中的教訓**：修復 `PaymentStateService.mockPaymentSuccess`（DEF-125）時，前一個 `mvn -o verify` 全量回歸仍在背景執行，而主控 session 在其執行期間又對同一個 `target/` 目錄跑了額外的 `mvn compile`/`mvn test`/`checkstyle:check` 呼叫——雖然表面上都各自回報成功，但兩個 Maven 行程同時寫入同一份 `target/classes`/`target/test-classes` 有污染風險，且該次全量回歸啟動時根本還沒包含 DEF-125 的程式碼變更，其「通過」不能代表本輪最終狀態。發現後立即 `kill` 該背景行程、清空 `target/maven-status`/`target/classes`/`target/test-classes` 重新乾淨編譯，才重跑最終的全量回歸。教訓：**同一個工作目錄不可有兩個 Maven 生命週期並行**，即使個別呼叫看似都成功，也不能保證彼此不互相污染；下次進行跨多個修復單元的 Sprint 時，應等前一個全量驗證完全結束再開始下一個会寫 `target/` 的動作，或改用獨立的 build 目錄。

---

## 3. 修復摘要：訂單取消（`OrderService.cancelOrder`，DEF-124）

**問題**：`OrderStateMachine.canCancel()` 狀態檢查與 `order.setStatus(CANCELLED)+save()` 之間沒有原子保護。兩個併發的取消請求都可能通過同一份舊快照的狀態檢查，各自繼續往下呼叫 `productInventoryService.releaseForOrder(order)`（相對遞減的原生 UPDATE，本身雖原子，但被呼叫兩次仍會扣兩次）與 `refundPromoUsage(order)`（優惠券額度相對遞增，同樣的重複呼叫風險），造成庫存被重複釋放（幻影庫存）、優惠券額度被重複退還（超發）。

**修法**：比照 claim-before-side-effects 原則，改用既有的 `OrderRepository.updateStatusIfCurrent`（Sprint 136 §5.2 為 `OrderService.updateOrderStatus` 新增，本輪重用而非新建）先原子搶占「目前狀態→CANCELLED」，只有搶到的一方才繼續釋放庫存/優惠券額度；搶輸沿用既有的 `E_5002`。移除原本第一段「setStatus+save」的全欄位覆寫式寫入（後續若訂單原為 PAID，仍需要的 CANCELLED→REFUNDING 轉換保留原本的 `save()`，因為此時已serialize 過，不再有併發風險）。

---

## 4. 誠實揭露：`OrderService.createRoomOrder`（DEF-161）確認為死碼，維持不修復

**查證過程**：追蹤 `createRoomOrder` 的唯一呼叫點——`OrderService.createOrderFromCart` 在 `orderType == ROOM` 時分派給它；`createOrderFromCart` 的唯一呼叫點是 `OrderController.createOrder`（`POST /v2/orders`）。前端搜尋確認：`OrderService.createOrder`（前端 service 層）僅被 `checkout/product/page.tsx` 呼叫，而該頁面是 PRODUCT-only 結帳流程；房源結帳的兩個頁面（`checkout/page.tsx`、`checkout/mixed/page.tsx`）皆呼叫 `bookingService.createBooking`，完全不經過 `/v2/orders` 端點。

**結論**：`createOrderFromCart` 內 `orderType == ROOM` 這個分支在目前前端零呼叫點，`createRoomOrder` 的併發風險（無 `RoomCalendarService` 呼叫、無日期鎖定、無可用性檢查）不會在生產環境被觸發，與既有教訓 [[room-booking-dual-path-gotcha]] 完全一致。原始標籤判定正確，**不修復，僅更正查證狀態備查**。

---

## 5. 修復摘要：Mock 付款成功（`PaymentStateService.mockPaymentSuccess`，DEF-125）

**問題**：與 Sprint 136 §4.3 已修復的 `markStripePaymentSucceeded` 完全同型，但當輪只選了 19 筆中的一部分修復，本方法被漏掉、登記為技術債。`existsByOrderIdAndStatus`（已付款檢查）與 `OrderStateMachine.canPay()` 檢查皆是 check-then-act，兩個併發的 mock 付款請求都可能通過同一份舊快照，各自建立一筆 SUCCESS Payment、各自呼叫一次 `deductStockSafely(order)`，造成庫存被重複扣帳（帳實不符）。

**修法**：完全比照 `markStripePaymentSucceeded` 的既有修法——claim-before-side-effects，改用既有的 `OrderRepository.updateStatusIfCurrent` 在建立 Payment 記錄、扣庫存**之前**先原子搶占「目前狀態→PAID」；搶輸沿用既有的 `E_5011`。

**驗證**：以既有真實 DB 整合測試 `M07PaymentMockIntegrationTest`（8 案例，`orderRepository`/`paymentRepository` 皆為真實 Spring Data JPA bean）驗證修改未破壞既有 Mock 付款流程。

---

## 6. 誠實揭露：`PaymentStateService.markStripeRefunded`（DEF-162）後果比原標籤更嚴重

**問題**：原始 Sprint 136 標籤判定「無金流/安全後果」，主控 session 獨立複核後同意這個核心結論（不涉及重複退款金額、不呼叫 Stripe API），但發現一個原標籤沒提到的真實後果：Stripe webhook 可能對同一筆退款事件重複送達（Stripe 官方文件本就要求消費端做冪等處理），`payment.getStatus() == REFUNDED` 的冪等檢查與後續寫入之間不是原子的，兩個併發的 webhook 處理都可能通過同一份舊快照，各自對訂單寫入一筆重複的 `order_state_log`——這不是「無後果」，而是稽核軌跡出現重複紀錄，影響資料完整性（雖然仍非金流損失）。

**修法**：新增 `PaymentRepository.markRefundedIfNotAlready`（`WHERE status <> newStatus` 條件式原子 UPDATE，完全比照既有的 `markSuccessIfNotAlready` 命名與語意），取代「讀 status==REFUNDED 檢查→setStatus→save」；搶輸（已是 REFUNDED 或已被另一併發 webhook 搶先處理）視為冪等 no-op。

---

## 7. 修復摘要：客服工單建立（`SupportTicketService.createTicket`，DEF-139）

**問題**：`generateTicketNumber()` 的 `countByTicketNumberStartingWith(prefix)` 是 check-then-act，同一天同一 prefix 內兩個併發請求可能算出相同編號。`support_tickets.ticket_number` 已有 DB `UNIQUE` 約束（`V68__Create_Support_Tickets_Tables.sql`）兜底資料完整性，但敗方原本會收到未攔截的原始 500——這正是原始標籤「自癒但使用者體驗劣化」的意思：資料不會壞，但客戶送出工單卻收到系統錯誤。

**修法**：由於編號是系統產生、非使用者輸入，改為捕捉唯一約束違反後**重新產生編號並有界重試**（最多 3 次）——這是本輪範圍內唯一採用「內部自動重試」而非「拒絕並要求呼叫端重試」的修法，理由是重試對使用者完全無感、且失敗原因與使用者輸入無關，若仍要求使用者手動重新送出表單反而是不必要的體驗劣化。超過重試上限時原樣拋出例外，不無限重試。

---

## 8. 修復摘要：客服工單狀態更新（`SupportTicketService.updateStatus`，DEF-163）

**問題**：`SupportTicket` 無 `@Version` 也無 `@DynamicUpdate`；`updateStatus`（碰 `status`/`priority`/`resolvedAt`）與 `assignTicket`（碰 `assignedTo`）若併發交錯，後 commit 者會用自己交易一開始讀到的舊快照整列覆寫，可能讓指派結果被悄悄復原。

**修法**：`SupportTicket` 加 `@DynamicUpdate`（比照 Sprint 136 §6 既有模式）。

---

## 9. 驗證

- **編譯**：每個修復單元改動後立即 `mvn compile` 確認真實編譯。
- **checkstyle**：`mvn checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test` **0 violations**。
- **單元測試**：新增 5 個測試——`OrderServiceTest` +1（`cancelOrder_concurrentClaimLost_throwsE5002WithoutReleasingStock`）、`SupportTicketServiceTest` +2（ticketNumber 碰撞重試成功 + 超過重試上限）、`PaymentStateServiceTest` +2（`markStripeRefunded`/`mockPaymentSuccess` 各一個併發搶占失敗守衛測試）；既有測試（`OrderServiceTest`/`OrderPromoCodeTest`/`PaymentStateServiceTest`/`PaymentStateServiceStripeTest`/`SupportTicketServiceTest`）的 mock 同步更新以符合新方法簽章（`updateStatusIfCurrent`/`markRefundedIfNotAlready`/`saveAndFlush`），部分測試的 `save()` 呼叫次數斷言隨程式邏輯變動調整。
- **整合測試（真實 DB）**：`M07PaymentMockIntegrationTest`（8 案例，真實 `orderRepository`/`paymentRepository`）驗證 DEF-125 修復未破壞既有 Mock 付款流程。
- **Schema 守門**：`make validate-schema`（entity↔migration）、`make validate-schema-doc`（migration↔文件）皆通過，本輪未新增任何 Flyway 遷移。
- **全量回歸**：因第 2 節所述的並行 Maven 行程污染風險，清空 `target/` 重新編譯後跑最終的 `mvn -o verify`（含 failsafe 整合測試）**BUILD SUCCESS**：單元 **1191**（相對 Sprint 143 的 1186，+5）、整合 **477**（與 Sprint 143 持平，本輪未新增整合測試方法），0 failures/errors；checkstyle（main+test）**0 violations**；總耗時 7:36 min。

---

## 10. 收尾：Sprint 136 併發競態全掃 52 筆技術債處理完畢

Sprint 136 併發競態全掃找出 52 筆技術債（`DEF-114`~`DEF-165`），Sprint 137~144（共 8 輪）依主題逐批查證並修復，本輪（Sprint 144）為最後一批。累計結果：

| Sprint | 主題 | 筆數 | 修復 | 死流程/無需修復 |
|--------|------|------|------|------------------|
| 137 | 租戶治理 | 10+3 | 10 | 3（DEF-145/146/147，本輪起維持不動） |
| 138 | 知識庫 | 8 | 8 | 0 |
| 139 | CMS | 5 | 5 | 0 |
| 140 | ERP | 7 | 7 | 0 |
| 141 | 退貨 | 4 | 4 | 0 |
| 142 | 房源日曆 | 4 | 3 | 1（DEF-137，已被 Sprint 136 自己的 §6 意外修復） |
| 143 | 治理雜項 | 5 | 5 | 0 |
| 144 | 訂單/付款/客服工單 | 6（另 3 筆維持不動） | 5 | 1（DEF-161，確認死碼） |
| **總計** | | **52** | **47** | **5**（3 筆死流程 + 1 筆意外已修復 + 1 筆確認死碼） |

**方法論總結**：全程未再另開 Workflow（僅 Sprint 136 本身使用過一次），改由主控 session 逐筆重讀原始碼、獨立判斷、動手修復，並在多輪中發現原始 Workflow 自動化分析的標籤有數處需要更正——3 筆死流程（Sprint 137）、1 筆已被意外修復（Sprint 142）、1 筆確認死碼（本輪）、2 筆後果比原標籤更嚴重（DEF-128 提交審批門檻假設不成立但仍有其他真實風險、DEF-158/159 可繞過業務規則、DEF-162 有重複稽核風險）、1 筆修復過程中發現與併發無關的獨立真實 bug（DEF-143 的 `.tenant()` 遺漏）。這印證了 Sprint 136 §1 自己的預告：「不可直接信任下表的嚴重度標籤或逕自套用修復」——每一筆都經過主控 session 重新獨立查證後才動手，而非照單全收。

---

## 11. 刻意不做的事（避免範圍蔓延）

- 不重新處理 `DEF-145`/`146`/`147`——Sprint 137 已查證為生產不可達的死流程，本輪僅在文件層級確認維持不動，不重新翻案。
- 不處理 `assignTicket`（`SupportTicketService`）的類似讀後寫模式——非本輪 52 筆候選之一，`@DynamicUpdate` 已從另一側（`updateStatus`）緩解與其交錯的欄位覆寫風險，`assignTicket` 自身若要有 CAS 防護屬於新的技術債候選，留待未來排程。
- 不為 `TenantService.initializeFeatureToggles`/`FEATURE_DEFINITIONS` 兩份清單同步的架構性風險立即重構——Sprint 143 已發現並記錄此風險，重構為單一事實來源是較大範圍的變更，非本輪範圍。
