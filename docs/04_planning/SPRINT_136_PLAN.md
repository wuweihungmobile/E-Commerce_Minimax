# Sprint 136 Plan — 併發競態（Race Condition）系統性掃描 + 修復

**Sprint**: Sprint 136
**日期**: 2026-09-07 ~ 2026-09-08

---

## 1. 本輪範圍與方法論

延續多輪系統性安全/正確性掃描慣例（Sprint 128 契約漂移、Sprint 134 檔案上傳、Sprint 135 儲存型 XSS + 稽核日誌覆蓋率），本輪使用者拍板方向為「併發競態全掃」，理由：本專案 `domain/model/` 下 59 個 `@Entity` 只有 2 個帶 `@Version` 樂觀鎖（`Inventory`、`ProductInventory`），過去 Sprint 102/103/105/106/113 已在個別模組發現並修復多起讀後寫競態，但從未做過一次涵蓋全部核心服務的系統性掃描。

**執行方式**：以 `Workflow` 工具進行三階段多 agent 系統性掃描（比照 Sprint 134/135 慣例，並套用同日稍早新增到 `CLAUDE.md` 的「Workflow 子 Agent 唯讀範圍強制規則」——所有 Discover/Analyze/Verify 階段 prompt 皆明文禁止寫入檔案與執行 git 指令，且整個 Workflow **不含任何 Fix 階段**，所有實際修復均由主控 session 自己動手，逐一編譯/測試）：

- **Discover**：6 個角度平行掃描（金流/結算、租戶/管理、訂房/訂單/商品、客服/知識庫/CMS、退貨/ERP/物流 五個檔案群組 + 1 個 TOCTOU 唯一性競態角度），找出候選方法。
- **Analyze**：對每個候選深入讀碼，回答「後果是什麼／今天有沒有人走這條路／真的有併發窗口嗎」三問法（沿用 read-modify-write-race-playbook 既有方法論），判定 `isRealRace` 與嚴重度。
- **Verify**：對 Analyze 判定為真的候選，各用 3 個獨立懷疑視角對抗式駁倒，多數決定生死。

**結果**：Discover 80 筆候選，去重後 75 筆進入 Analyze，Verify 階段最終 **71 筆確認為真實競態，1 筆被多數駁倒**（`RoomCalendarService.unblockDateRange`）。

**🔴 誠實揭露：本輪 Verify 駁倒率明顯偏低（71/72 ≈ 99% 存活），與過去 Sprint 的經驗（例如 Sprint 135 的 18 選 1）落差很大。**主控 session 沒有照單全收：抽查 3 個代表性候選（1 個 high 財務級、1 個 high 庫存級、1 個 medium CMS 內容級）親自重讀原始碼逐一核實，結論是這些判斷本身站得住腳——高駁倒率的落差原因是本專案「57/59 個實體沒有樂觀鎖」這個架構缺口本來就很普遍，systematic 掃描找到大量同型缺口是真實現況，不是 Workflow 品質差。但 71 筆是一個 Sprint 不可能安全消化的量（逐筆走 CLAUDE.md 的「開發→編譯→測試」循環），使用者拍板本輪只修復 **7 筆 high + 12 筆 medium/financial，共 19 筆**；其餘 52 筆登記為技術債（見第 8 節），且明確標註「僅通過 Workflow 自動化分析，未經主控 session 逐筆獨立複核」，未來排入 Sprint 前應重新驗證，不可照單全收嚴重度標籤。

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

每完成一支程式，立即編譯（`mvn compile`）+ checkstyle + 執行相關測試，絕不累積開發。過程中兩次撞上本專案已知的「編譯失敗後下一次 `mvn compile` 回報假 BUILD SUCCESS」陷阱（`mvn-phantom-build-success-after-failure`），皆以 `rm -rf target/maven-status target/classes` 清空後重新編譯確認為真後才繼續。

---

## 3. 額外發現（範圍外但直接相關）：`payments.idempotency_key` 欄寬不足，100% 必現

處理 `PaymentStateService.initiateStripeCheckout` 的併發修復時，發現 `idempotencyKey = "ORDER-CHECKOUT-" + orderId` 組出的字串固定 51 字元，但 `payments.idempotency_key` 欄位在 `V1__Initial_Schema.sql` 定義為 `VARCHAR(36)`，從未被加寬過；同一個 bug 也存在於 `PaymentService.processOrderPayment`（`"ORDER-"+UUID`=42 字元）與 `processBookingPayment`（`"BOOKING-"+UUID`=44 字元）。

**後果**：只要 `STRIPE_PAYMENT_ENABLED`／訂單付款／訂房付款這幾條路徑真的被觸發，寫入資料庫時會 **100% 撞到 `value too long for type character varying(36)` 而失敗**——這不是機率性的併發問題，是必現的既有缺陷。

**為什麼過去沒被抓到**：`STRIPE_PAYMENT_ENABLED` 預設關閉，且本地整合測試／本地 CI 皆用 `ddl-auto=update`（Hibernate 依 entity 自動建表，欄位未標長度時預設 255 字元），只有真正套用 Flyway migration 的環境欄位才會卡在 36 字元——這正好解釋了為什麼這個必現 bug 一直沒被抓到（見 `local-ci-cannot-catch-schema-validation` 既有教訓的同型案例）。

**修法**（經使用者確認一併修復）：新增 `V79__Widen_Payments_Idempotency_Key_And_Enforce_Uniqueness.sql`，將欄位加寬為 `VARCHAR(64)`，同時補上 `idx_transfers_idempotency_key_unique` 唯一索引（取代原本非唯一的 `idx_payments_idempotency`），供本輪的併發防護（claim-before-external-call 模式）使用；`Payment.idempotencyKey` 的 `@Column` 補上對應 `length` 屬性。`make validate-schema`／`make validate-schema-doc` 皆通過，無 entity↔migration 漂移、無文件漂移。

---

## 4. 修復摘要：金流核心（`TransferService`、`PaymentStateService`、`PaymentService`）

### 4.1 `TransferService.createTransferForStatement`（🔴 high，financial）

**問題**：結算單審核通過後轉帳給賣家 Connect 帳戶的核心方法，`existing == null` 檢查與「呼叫 Stripe」之間沒有任何資料庫層級原子保護；DB 的 `idx_transfers_settlement_statement_id` 唯一索引只在最後 `save()`（Stripe 呼叫**之後**）才生效，擋不住已經真的撥出去的第二筆錢；`StripePaymentGateway.createTransfer` 也從未帶 idempotency key。

**修法**：改為「先以唯一索引原子性佔位（`saveAndFlush` + 捕捉 `DataIntegrityViolationException`），成功佔位才呼叫 Stripe」——把「claim 再做外部呼叫」的順序對調，讓第二個併發請求在真正打到 Stripe 之前就被擋下；`StripePaymentGateway.createTransfer` 補上 `setIdempotencyKey(sourceReferenceId)` 作為第二層防禦。`retryFailedTransfer` 的最壞後果（重複真實撥款）因此被一併擋下；殘餘的 `SettlementStatement.status` 覆寫風險屬次要、判斷不需額外改動。

**測試**：新增 `createTransferForStatement_concurrentClaim_returnsExistingWithoutCallingStripe`；`StripePaymentGatewayTest` 既有 TC-S011 補上 `Idempotency-Key` header 斷言。

### 4.2 `TransferService.handleTransferReversedWebhook`（medium，security_or_access）

**問題**：Stripe `transfer.reversed` webhook 延遲送達時，會無條件把 `SettlementStatement.status` 改回 `FAILED`，可能覆寫掉人工雙重授權逆轉流程（`SettlementReversalService`）已推進到 `REVERSAL_PENDING`／`REVERSED` 的決策。

**修法**：加狀態守門，只有目前仍是 `PAID`（webhook 延遲送達前的預期前置狀態）才允許改為 `FAILED`；否則只記錄 log、跳過覆寫。新增測試驗證 `REVERSAL_PENDING` 狀態下不受影響。

### 4.3 `PaymentStateService.markStripePaymentSucceeded`（🔴 high，inventory_or_stock）

**問題**：`payment.getStatus() == SUCCESS` 檢查與後續 `deductStockSafely`（原生 UPDATE 相對扣減庫存）之間沒有原子保護；回跳確認流程與 webhook 幾乎同時處理同一筆付款時，兩邊都可能通過檢查，各自扣一次庫存（真實扣兩倍）。

**修法**：新增 `PaymentRepository.markSuccessIfNotAlready`（條件式原子 UPDATE，`WHERE status <> newStatus`），取代「讀狀態→setStatus→save」；只有真正搶到轉換的一邊才繼續扣庫存。

### 4.4 `PaymentStateService.refundOrderPayment`（🔴 high，financial）

**問題**：`resolveRefundAmount` 讀取 `refundedAmount` 驗證額度後，`setRefundedAmount/setStatus/save` 沒有原子保護；兩個併發退款請求都可能對同一張結算單呼叫兩次 `settlementAdjustmentService.handleOrderRefund`，短付賣家；`refundedAmount` 本身的 lost update 也可能讓之後的第三次請求退超過原始金額。

**修法**：新增 `PaymentRepository.applyRefundIfUnchanged`（compare-and-swap，`WHERE refundedAmount = 讀取當下的舊值`），且**佔用額度排在呼叫 Stripe 之前**（claim-before-external-call，與 4.1 同一原則）：佔用失敗直接拒絕，绝不會走到呼叫外部金流；in-memory 的 `setRefundedAmount/setStatus` 特意延後到 Stripe 呼叫**之後**才做，確保 Stripe 失敗時交易整體回滾、記憶體物件不會呈現「已退款」的假象。

### 4.5 `PaymentStateService.initiateStripeCheckout`（🔴 high，financial）

**問題**：本地 `payments` 表對 `order_id`／`idempotency_key` 都沒有唯一約束，兩個併發請求各自 `save()` 出重複的 PROCESSING Payment 列，打壞下游 `findByTransactionId`（單結果查詢）。

**修法**：搭配第 3 節的欄寬修復，`saveAndFlush` + 捕捉唯一索引衝突；由於 Stripe 自己的 idempotency key 已保證兩邊拿回同一個 session，衝突時直接沿用該 session 資訊回應，不重複寫入。

### 4.6 `PaymentService.processOrderPayment` / `processBookingPayment`（medium，financial）

**問題**：與 4.5 同一根因（`idempotency_key` 欄寬 + 缺唯一索引），Mock 付款流程併發下會產生兩筆 SUCCESS Payment，造成重複計入營收、且其中一筆「幽靈」付款可被拿去退款把真實已付款訂單/訂房打成取消。

**修法**：`saveAndFlush` + 捕捉唯一索引衝突，衝突時拒絕（`E_6003 Payment already processed`）。

### 4.7 `PaymentService.processRefund`（medium，financial）

**問題**：`status == SUCCESS` 檢查與 `setStatus(REFUNDED)` 之間無原子保護，兩個併發退款請求可能都回應「成功」（各自拿到一組不同的假 refundId）。

**修法**：新增 `PaymentRepository.updateStatusIfCurrent`（條件式狀態轉換），取代無條件 `setStatus+save`。

---

## 5. 修復摘要：狀態機/全欄位覆寫類（`AdminService`、`OrderService`、`ReturnRequestService`、`PromoService`）

### 5.1 `AdminService.approvePurchaseOrder` / `rejectPurchaseOrder`（medium，financial）

**問題**：`po.canReview()`（`status == PENDING_APPROVAL`）檢查與 `setStatus/save` 之間無原子保護，approve 與 reject（或兩個併發 approve）可能互相覆寫審批結果與稽核紀錄（`reviewedBy`/`reviewedAt`）。

**修法**：新增 `PurchaseOrderRepository.reviewIfStatus`（條件式原子 UPDATE），佔用失敗拋 `E_7002`。

### 5.2 `OrderService.updateOrderStatus`（medium，financial）

**問題**：`OrderStateMachine.canTransition(currentStatus, targetStatus)` 檢查與 `setStatus/save` 之間無原子保護，兩個併發請求可能以同一舊快照各自要求不同的下一步狀態（例如 `PAID→CONFIRMED` 與 `PAID→REFUNDING`），最後 commit 者覆寫另一邊。

**修法**：新增 `OrderRepository.updateStatusIfCurrent`，佔用失敗拋 `E_5001`。

### 5.3 `ReturnRequestService.receiveReturn`（🔴 high，inventory_or_stock）

**問題**：`requireStatus(APPROVED)` 檢查與後續 `productInventoryService.applyReturnReceipt`（原生 UPDATE 相對加回庫存）之間無原子保護，同一筆退貨單被併發呼叫兩次會造成庫存被重複加回（幽靈庫存）。

**修法**：新增 `ReturnRequestRepository.updateStatusIfCurrent`（`APPROVED→RECEIVED`），claim 排在庫存異動之前，佔用失敗拋 `E_5017`。**新增真實併發整合測試**（5 條執行緒同時呼叫同一筆退貨單的 `receiveReturn`，真實 PostgreSQL + `ExecutorService`/`CountDownLatch`）：斷言 5 個併發請求中恰好只有 1 個成功、庫存只淨加回一次（`+3`，不是 `+6/+9/.../+15`）、台帳只留一筆 `RETURN` 紀錄——這是本輪唯一以「真執行緒對真 DB 施壓」直接證明修復生效的案例（其餘案例以 Mockito 驗證 repository 互動正確，或以 Hibernate 產生的實際 SQL 佐證）。

### 5.4 `BookingService.cancelBooking` → `PromoService.revokeAndReleaseQuota`（medium，financial）

**問題**：取消訂房觸發的優惠券額度退還（`releaseBookingSide`/`releaseOrderSide` → `revokeAndReleaseQuota`）本身的「額度遞減」雖已在 Sprint 102（DEF-046）改為原子相對遞減，但**是否要遞減**這個判斷（`usage.setStatus(REVOKED)` 前的隱含檢查）從未加鎖：若 `revokeAndReleaseQuota` 因外層協調邏輯被呼叫兩次，`releaseUsageQuota` 也會跟著執行兩次，讓同一張促銷碼的已用額度被多退一次。

**修法**：新增 `PromoCodeUsageRepository.updateStatusIfCurrent`（`ACTIVE→REVOKED`），只有真正搶到轉換的一邊才繼續呼叫 `releaseUsageQuota`。

---

## 6. 修復摘要：全欄位覆寫類（`@DynamicUpdate`）

`RoomService.updateRoom`、`ProductService.updateProduct`、`BookingService.updateBooking` 三者共同的根因：DTO 層是「部分欄位選填」的 PATCH 語意，但 `Listing`/`Room`/`Product`/`Booking` 四個實體皆無 `@DynamicUpdate`，Hibernate 預設對 managed entity 用「整列所有欄位」組 UPDATE SQL（以本次交易載入時的快照覆寫，而非只覆寫真正被 setter 改動過的欄位）。兩個併發請求各自只改不同欄位時，後 commit 者會用自己交易一開始讀到的舊快照，把先寫入者已提交的欄位悄悄覆蓋回去（例如交易 A 改價格、交易 B 併發只改標題，B 提交後價格被悄悄改回舊值）。

**修法**：對 `Listing`、`Room`、`Product`、`Booking` 四個實體加上 `@DynamicUpdate`（Hibernate 讓 UPDATE 只包含本次交易內實際被 setter 改動過的欄位）。同時修復 `AdminService.updateUserStatus`（🔴 high，security_or_access）：`User` 實體加上 `@DynamicUpdate`，避免 `AuthService.login()`（只改 `lastLoginAt`）在 `updateUserStatus`（停權/封禁）併發轉換 `status` 之後才 commit 時，用登入當下讀到的舊快照把剛生效的停權結果悄悄復原——**已用真實 PostgreSQL + 兩條真執行緒 + `CountDownLatch` 精確重現該交錯順序**，並直接讀 Hibernate 產生的實際 SQL 確認：`update users set last_login_at=?,updated_at=? where id=?`（登入方）與 `update users set status=?,updated_at=? where id=?`（停權方）——兩者互不干擾，這是本輪除 5.3 外唯一另一個以「讀真實 SQL」而非僅靠斷言驗證修復效果的案例。

`make validate-schema` 確認四個實體加註解後 entity↔migration 仍完全對齊（`@DynamicUpdate` 純屬 Hibernate SQL 產生策略，不影響 schema）。

---

## 7. 誠實揭露：`UserPrivacyService.deleteMyAccount`（🔴 high，other）的修復僅縮小競態窗口，非完全消除

**問題**：帳戶自助刪除（GDPR 被遺忘權）前會檢查「有無未結案訂單/訂房」，但檢查與匿名化提交之間，若併發建立了新的未結案訂單/訂房，該筆交易的 `userId` 會永久指向一個已匿名化的帳號，形成無法追蹤的孤兒交易。

**修法**：`UserRepository` 新增 `findByIdForUpdate`（`SELECT ... FOR UPDATE` 悲觀鎖）取代原本的 `findById`。由於 `orders.user_id`/`bookings.user_id` 對 `users(id)` 有外鍵約束，PostgreSQL 會讓併發新增訂單/訂房的 INSERT 隱含需要此列的鎖，這能擋下「鎖先於新訂單建立」這個方向的競態（deleteMyAccount 先鎖住，新訂單的 INSERT 會被擋到 deleteMyAccount 提交/回滾之後，deleteMyAccount 若尚未提交時搶到鎖，仍可能正確偵測到新訂單並中止刪除）。

**但這不是完整的修復**：反方向（新訂單的 INSERT 先搶到鎖並提交，deleteMyAccount 才輪到取得鎖）仍然只是把兩件事的順序序列化，deleteMyAccount 這時看不到「未來」才會發生的訂單，仍會繼續匿名化——訂單建立完成後即指向一個剛被匿名化的帳號，結果與修復前相同。**完整杜絕需要訂單/訂房建立端自行驗證買家帳號狀態仍為 `ACTIVE`**，這是另一個範圍更大的架構性待辦（跨 `OrderService`/`BookingService` 的建立流程），不在本輪範圍內，記錄供未來排程參考。

---

## 8. 調查過程中發現、本輪未修復的候選（技術債，DEF-114 ~ DEF-165，共 52 筆）

Verify 階段確認為真但本輪未排入修復範圍的候選，依嚴重度與後果類別列表如下（完整推理過程留存於 Workflow 執行紀錄，未逐筆複製到本文件）。**🔴 重要限制**：以下 52 筆僅通過 Workflow 自動化 Discover→Analyze→Verify 三階段分析，**未經主控 session 逐筆獨立複核**（本輪僅抽查 3 筆代表性樣本驗證方法論可信，詳見第 1 節）；未來排入 Sprint 前，應比照本輪對已修復 19 筆的做法，重新獨立讀碼確認後才動手，不可直接信任下表的嚴重度標籤或逕自套用修復。

### 🟡 中優先級（medium，DEF-114 ~ DEF-144，31 筆）

| ID | 檔案／方法 | 類別 | 後果摘要 |
|----|-----------|------|---------|
| DEF-114 | AdminService.approveTenantApplication | security_or_access | 併發下可能產生兩個獨立 ACTIVE 狀態的 Tenant（孤兒資源） |
| DEF-115 | AdminService.rejectTenantApplication | security_or_access | 跨兩交易的 lost update，業務狀態與系統狀態矛盾 |
| DEF-116 | AdminService.updateTenantFeatureToggle | other | 功能開關 + 稽核時間戳 lost update，不會自我修復 |
| DEF-117 | AdminService.updateTenantStatus | security_or_access | 兩個 SUPER_ADMIN 併發下達相反狀態轉換時結果不確定 |
| DEF-118 | ChatService.createConversation | other | 併發「發起聊天」可能產生重複 Conversation |
| DEF-119 | CmsService.publishPage | other | ContentPage 全欄位 lost update |
| DEF-120 | KnowledgeBaseService.createVersionSnapshot | other | 併發下可能造成持續性功能性 500 錯誤 |
| DEF-121 | KnowledgeBaseService.restoreVersion | other | 還原版本時整包覆寫造成內容遺失 |
| DEF-122 | KnowledgeBaseService.updateArticle | other | 含發布狀態被悄悄復原/復活的內容一致性問題 |
| DEF-123 | LogisticsService.createLogistics | other | 同一訂單可被建立兩筆物流記錄，違反業務不變量 |
| DEF-124 | OrderService.cancelOrder | inventory_or_stock | 庫存與優惠券額度可能被重複釋放（幻影庫存/超發） |
| DEF-125 | PaymentStateService.mockPaymentSuccess | inventory_or_stock | 與已修復的 markStripePaymentSucceeded 同型，Mock 路徑重複扣庫存 |
| DEF-126 | PurchaseOrderService.cancelPurchaseOrder | inventory_or_stock | PO 狀態可被併發 receive/cancel 靜默覆蓋 |
| DEF-127 | PurchaseOrderService.receivePurchaseOrder | inventory_or_stock | 收貨數量與庫存台帳資料完整性/內控失效 |
| DEF-128 | PurchaseOrderService.submitPurchaseOrder | other | 併發提交可能繞過金額審批門檻判斷 |
| DEF-129 | PurchaseOrderService.updatePurchaseOrder | other | PO 全欄位 lost update |
| DEF-130 | ReturnRequestService.approveReturn | other | 審核/駁回/取消三者併發時狀態靜默遺失更新 |
| DEF-131 | ReturnRequestService.cancelReturnRequest | other | 與退款流程解耦，但 return_requests 自身狀態不一致 |
| DEF-132 | ReturnRequestService.createReturnRequest | inventory_or_stock | 併發申請可能讓退貨總量超過實際購買量 |
| DEF-133 | ReturnRequestService.rejectReturn | other | 同 DEF-130，狀態/稽核不一致 |
| DEF-134 | RoomCalendarService.markMaintenance | inventory_or_stock | 共用 Booking 列被靜默覆寫（狀態/日期/金額） |
| DEF-135 | RoomCalendarService.releaseDateRange | inventory_or_stock | room_calendar 與 Booking.statusFlags 資料不一致 |
| DEF-136 | RoomCalendarService.unmarkMaintenance | inventory_or_stock | 同一 room_calendar 列兩種可能的 lost update |
| DEF-137 | RoomService.clearOpenWindow | inventory_or_stock | Room 整列 lost update，影響開放窗與其他欄位 |
| DEF-138 | SettlementReversalService.initiateReversal | security_or_access | 逆轉流程雙重授權狀態可能被併發影響 |
| DEF-139 | SupportTicketService.createTicket | other | 客服單號產生器併發下可能重複（自癒但體驗劣化） |
| DEF-140 | TenantService.acceptInvite | security_or_access | tenant_members.status lost update，影響面已查證比初步描述窄 |
| DEF-141 | TenantService.createApplication | other | 併發下可能產生同一使用者的重複 PENDING 申請 |
| DEF-142 | TenantService.removeMember | security_or_access | 已移除成員可能因併發覆寫「復活」為 ACTIVE |
| DEF-143 | TenantService.updateFeatureToggle | other | 功能開關 lost update |
| DEF-144 | TenantService.updateMemberRole | security_or_access | 角色變更與移除併發時的存取控制完整性問題 |

### 🟢 低優先級（low，DEF-145 ~ DEF-165，21 筆）

| ID | 檔案／方法 | 類別 | 後果摘要 |
|----|-----------|------|---------|
| DEF-145 | AdminService.approveTenant | security_or_access | 併發 approve 可能重複初始化 feature toggle（自癒/低頻） |
| DEF-146 | AdminService.rejectTenant | security_or_access | Tenant 最終狀態可能由 commit 順序而非業務邏輯決定 |
| DEF-147 | AdminService.reviewTenant | security_or_access | 對 status 無條件覆寫，與 updateTenantStatus 併發時的邊界情況 |
| DEF-148 | AdminService.setFeatureToggle | other | 已有部分 DB 唯一約束保護，僅更新路徑仍有 lost update |
| DEF-149 | CmsService.createPage | other | slug 唯一性 check-then-act，自癒型/非資料損毀 |
| DEF-150 | CmsService.publishBanner | other | Banner 內容/統計 lost update |
| DEF-151 | CmsService.updateBanner | other | 同上 |
| DEF-152 | CmsService.updatePage | other | ContentPage lost update |
| DEF-153 | KnowledgeBaseService.createArticle | other | 已有 DB 唯一約束保護，非資料損毀 |
| DEF-154 | KnowledgeBaseService.createCategory | other | 已有 DB 唯一約束保護 |
| DEF-155 | KnowledgeBaseService.deleteCategory | other | 極窄視窗下與併發 createArticle 交錯的邊界情況 |
| DEF-156 | KnowledgeBaseService.schedulePublish | other | 非關鍵 CMS 內容 lost update |
| DEF-157 | KnowledgeBaseService.updateCategory | other | 分類中繼資料 lost update |
| DEF-158 | LogisticsService.cancelLogistics | other | 影響侷限於顯示欄位，不外溢金流/安全 |
| DEF-159 | LogisticsService.updateLogisticsStatus | other | 追蹤資訊顯示層不一致，自癒型 |
| DEF-160 | MediaService.createCategory | stats_or_cosmetic | 純統計/外觀層級瑕疵 |
| DEF-161 | OrderService.createRoomOrder | inventory_or_stock | 死碼路徑（前端零呼叫點），效果等同無庫存保護但目前不可達 |
| DEF-162 | PaymentStateService.markStripeRefunded | financial | 純記錄方法，無金流/安全後果 |
| DEF-163 | SupportTicketService.updateStatus | other | 客服單狀態 lost update |
| DEF-164 | TenantService.declineInvite | security_or_access | accept/decline 交錯的邊界情況 |
| DEF-165 | TenantService.inviteMember | security_or_access | 依路徑而異，部分已有 DB 唯一約束保護 |

---

## 9. 驗證

- **編譯**：每支程式改動後立即 `mvn compile`（兩次撞上已知的「假 BUILD SUCCESS」陷阱，皆以清空 `target/maven-status` 重新編譯確認為真）。
- **checkstyle**：每輪改動後 `mvn checkstyle:check`，最終 **0 violations**。
- **單元測試**：新增/更新測試涵蓋全部 19 個修復點（含 8 個新增的併發防護專屬測試案例：`TransferServiceTest` ×2、`PaymentStateServiceTest`/`PaymentStateServiceStripeTest` ×3、`AdminServiceTest` ×1、`OrderServiceTest` ×1、`PromoServiceTest` ×1、`PaymentServiceConcurrencyTest`/`PaymentServiceRefundConcurrencyTest` 新檔 ×2）。
- **真實 DB 併發整合測試**（真執行緒 + 真 PostgreSQL，非 Mockito）：
  - `UserStatusRaceIntegrationTest`（新檔）：直接讀 Hibernate 產生的 SQL 確認 `@DynamicUpdate` 生效。
  - `M05ReturnRequestIntegrationTest` 新增 `IT-M05-RETURN-RACE`：5 執行緒併發呼叫同一筆退貨單的 `receiveReturn`，確認庫存只淨加回一次。
  - `M07PaymentMockIntegrationTest`（既有 8 案例）、`M05ReturnRequestIntegrationTest`（既有 12 案例）於 Mock 付款/退貨流程改動後重跑皆通過。
- **Schema 守門**：`make validate-schema`（entity↔migration）、`make validate-schema-doc`（migration↔文件）皆通過，V79 遷移與四個實體的 `@DynamicUpdate` 註解均未造成漂移。
- **全量回歸**：`mvn -o verify`（含 failsafe 整合測試）**BUILD SUCCESS**：單元 **1156**（相對 Sprint 135 的 1144，+12，即本輪新增的併發防護測試）、整合 **477**（相對 Sprint 135 的 475，+2，即 `UserStatusRaceIntegrationTest` 新檔 +1、`M05ReturnRequestIntegrationTest` 新增併發案例 +1），0 failures/errors；checkstyle（main+test）**0 violations**。首次執行時因 checkstyle 外掛讀取自己剛產生的 `checkstyle-result.xml` 報表遇到暫時性 I/O 競態（`in epilog non whitespace content is not allowed but got \u0`，非程式碼問題——477 個整合測試與 checkstyle 本身的檢查動作在該次執行中均已顯示 0 failures/violations，僅報表檔案讀取時機沒接上），重跑一次確認乾淨為真。

---

## 10. 刻意不做的事（避免範圍蔓延）

- 不修復第 8 節列出的 52 筆技術債——規模超出本 Sprint 可安全消化的量，且尚未逐筆獨立複核。
- 不重構訂單/訂房建立流程以徹底杜絕 `UserPrivacyService.deleteMyAccount` 的殘餘競態（見第 7 節）——需要跨模組設計決策，非本輪範圍。
- 不處理 `TransferService.retryFailedTransfer` 殘餘的 `SettlementStatement.status` 覆寫風險——已被 4.1 的修復大幅緩解，剩餘風險判斷為低（純狀態欄位、無金流動作）。
