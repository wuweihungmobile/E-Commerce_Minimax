# Sprint 226 Plan — 自動退款（DEF-303 (5)）與付款成功時訂單已取消（DEF-308 訂單側）

**Sprint**: Sprint 226
**日期**: 2026-10-01

## 1. 起點

使用者 2026-10-01 對 `DEF-308`、`DEF-303` (5) 回覆「請依照最佳化進行！」（排程見 [SPRINT_225_PLAN.md](SPRINT_225_PLAN.md) §1）。PRD §15.2.5「退款：若已支付，觸發 M04 退款流程」、M07「自動化退款流」。

本輪處理**訂單側**；訂房側（取消退款依 PRD Q14，`DEF-312`）與 `DEF-308` 訂房側在 Sprint 227，共用本輪建好的退款執行器。

## 2. 查證（讀程式碼，下列皆已在真實資料庫重現）

1. **取消已付款訂單後，錢只有管理員呼叫退款 API 才會動。** `OrderService.compensateCancellation` 把 `PAID`／`CONFIRMED` 的訂單轉 `REFUNDING`（Sprint 218），之後沒有任何程式會動錢；買家看到「退款中」，實際沒有人在退。
2. **`refundOrderPayment` 對 Stripe 付款仍看 `STRIPE_PAYMENT_ENABLED`**：`toggle 開 && 付款方式為 STRIPE` 才呼叫 Stripe。toggle 之後被關掉（上線檢核表 §E 的降級做法），這筆已經在 Stripe 的錢會被「只在本地標成已退款」——付款與訂單都變 `REFUNDED`、錢沒有退回買家，而且終態不會再有人處理。自動退款上線後這會變成**靜默的錯帳**，所以一併修。
3. **`markStripePaymentSucceeded` 對訂單是「讀狀態→`canPay`→`setStatus`→`save`」，不是條件式 UPDATE**，而且訂單不是 `CREATED` 時什麼都不做：買家還停在 Stripe 付款頁時，訂單被取消（管理員、賣家、買家自己、逾時），付款成功、錢已收，訂單 `CANCELLED`，沒有退款、沒有告警（`DEF-308`）。取消與付款併發時更糟：`save` 會用舊快照把剛取消的訂單寫回 `PAID`，已釋放的預留與優惠券額度沒有人收回。
4. **舊版 `POST /v2/payments/refund` 對 Stripe 付款只標 `REFUNDED`、從不呼叫 Stripe**（`DEF-303` (3)）：ADMIN 與自己下過單的店主都呼叫得到。

## 3. 設計（為什麼是「排程＋持久標記」）

**自動退款由排程做，不在買家的取消請求裡同步退。** `REFUNDING` 本來就是持久的「待退款」標記：

- Stripe 呼叫不該卡在買家的取消請求裡（Stripe 暫時不可用就取消不了訂單），也不該在取消的資料庫交易裡（外部呼叫與交易無法一起回滾）。取消與退款各自是獨立的交易，任何一步失敗都不會留下半套狀態，下一輪自然重試。
- 也是同一個機制涵蓋 `DEF-308`：付款成功時訂單已取消 → 轉 `REFUNDING`，同一個排程把它退掉，不必再寫第二套退款流程。
- 多個後端實例同時跑是安全的：退款額度用 `payments.refunded_amount` 的 compare-and-swap 佔用，同一筆付款只會被退一次（既有機制，本輪用 4 條併發處理者的整合測試鎖住）。

**`RefundProcessingService`**（每分鐘，`APP_REFUND_CHECK_INTERVAL_MS`）：以 id 為游標逐頁走完所有 `REFUNDING` 訂單，逐張各自一個交易呼叫 `PaymentStateService.refundOrderPaymentAsSystem`。

- 系統入口與使用者入口共用同一個退款核心（額度 CAS、Stripe 呼叫與冪等鍵、訂單轉 `REFUNDED`、狀態紀錄、結算調整），差別只有：不做擁有權檢查、操作者為 null、只處理 `REFUNDING`、金額一律是剩餘全額。
- **失敗整個交易回滾**：訂單維持 `REFUNDING`、付款不被標成已退款，並寫一筆 `AUTO_REFUND_FAILED` 稽核（含原因）。失敗的訂單以指數退避（5、10、20… 分鐘，上限 6 小時）重試，避免永久失敗的訂單每輪都打一次 Stripe；退避只存在記憶體，重啟後多試一次不影響正確性（冪等鍵與額度 CAS 擋住重複退款）。
- **永久失敗的訂單不餓死其他訂單**：游標逐頁走完，不因整頁失敗而停。
- **搶輸不算失敗**：兩個處理者（或處理者與管理員手動退款）同時處理同一張訂單，搶輸的一方會在行鎖釋放後以 `E-6009`／`E-5012` 收場。失敗當下若訂單已不再是 `REFUNDING`，就是別人做完了——不寫稽核、不退避，否則多實例會製造假的失敗紀錄讓人困惑。

**退款只看付款方式，不看 toggle**（查證 2）：付款方式為 `STRIPE` 的付款一律經 Stripe 退款。toggle 只決定「新的付款」走哪條路。

**`DEF-308`**：`markStripePaymentSucceeded` 改用條件式 UPDATE 搶占 `CREATED → PAID`（取消也是條件式 UPDATE，兩者搶同一個狀態，恰好一邊成功）。搶不到時：

- 訂單 `CANCELLED` → 條件式 UPDATE 轉 `REFUNDING`，狀態紀錄註明「Payment received after cancellation: refund pending」，交給自動退款；
- 其他狀態（例如已 `PAID` 又收到一筆＝重複付款，該退哪一筆沒有唯一答案）→ **不自動處理**，留稽核與錯誤日誌（不再靜默）。

兩種情況都寫 `STRIPE_PAYMENT_ORDER_NOT_PAYABLE` 稽核（含 `refundQueued=true/false`；當下狀態以純量查詢取得，不用 open-in-view 下可能過期的實體）。

**舊版退款端點**：拒絕 Stripe 付款（`E-6002`，請改用訂單退款端點）。

前端：訂單詳情頁在 `REFUNDING`／`REFUNDED` 顯示「退款資訊」（處理中說明、已退款金額）。

## 4. 實作

- `PaymentStateService`：`refundOrderPayment` 拆成「擁有權檢查＋核心」，新增 `refundOrderPaymentAsSystem`；Stripe 退款只看付款方式；`markOrderPaidByStripe`／`queueRefundForUnpayableOrder`。
- `RefundProcessingService`（`@Scheduled`）；`OrderRepository.findRefundingOrderIdsAfter`／`findStatusById`；`application.yml` 的 `app.refund.*`。
- `PaymentService.processRefund`：拒絕 Stripe 付款。
- 前端：`OrderPaymentState.refundedAmount`、訂單詳情退款資訊卡片。
- `scripts/validate-e2e.sh`：E2E 堆疊的後端把退款排程間隔調成 3 秒（真實後端 E2E 才不用等 1 分鐘）。
- 文件：`SETTLEMENT_JOB_RUNBOOK.md`（設定與「關閉排程＝關閉自動退款」）、`STRIPE_PRODUCTION_CHECKLIST.md`（§D 兩個人工驗證項、§E 關閉 toggle 的新語意）、`API_M05_Order.md` 狀態表。

## 5. 驗證

**測試**

- 單元：`RefundProcessingServiceTest` 9 案例（走頁游標、失敗隔離與稽核、整頁失敗不餓死後面、退避加倍與上限、成功重設、過期狀態清除、搶輸不算失敗、無待退款）；`PaymentStateServiceStripeTest` +4（toggle 關閉仍經 Stripe、系統入口不做擁有權檢查、只處理 `REFUNDING`、Stripe 拒絕時拋出且不留半套）；`PaymentStateServiceTest` +2（已取消訂單收到付款 → `REFUNDING`；已 `PAID` 又收到一筆 → 只稽核）；`PaymentServiceRefundOwnershipTest` +1（舊版端點拒絕 Stripe）；`PaymentRefundIdempotencyKeyTest` +1（WireMock：系統退款＋toggle 關閉，真實 Stripe gateway 仍收到恰好一次請求、帶 `payment_intent` 與冪等鍵）。兩個既有案例（UT-PAY-STRIPE-003、UT-PAY-STATE-041）補上 `CREATED → PAID` 條件式 UPDATE 的 stub。
- 整合（真實 PostgreSQL＋真實 Redis，Stripe 閘道 mock，`OrderAutoRefundIntegrationTest` 11 案例）：Mock 付款取消後自動退款（付款／訂單 `REFUNDED`、狀態紀錄操作者 null、不呼叫 Stripe）；重複執行只退一次；沒被取消的已付款訂單不會被退；Stripe 付款經 Stripe 退一次（冪等鍵＝付款意圖＋退款前累計額＋金額）、存 refund id；**toggle 關閉後仍經 Stripe**；Stripe 拒絕 → 整個交易回滾（付款仍 `SUCCESS`、`refunded_amount=0`、無多餘狀態紀錄）、留失敗稽核、退避期間不重試、退避結束且 Stripe 恢復後成功；永久失敗的訂單不擋住同一輪的其他訂單；**4 個處理者同時處理同一張訂單 → Stripe 只被呼叫一次、只退一次**；`REFUNDING` 但沒有付款 → 大聲失敗；**webhook 遲到付款（訂單已取消）→ `CANCELLED → REFUNDING`、留稽核、自動退回**；付款與取消併發（3 輪）兩種順序最後都退回買家、預留不重複釋放或漏釋放。
- 前端 mock E2E：`at-order-refund.spec.ts` 3 案例（處理中／已退款金額／其他狀態不顯示）。真實後端 E2E：`E2E-BPAYR-07`（取消已付款訂單 → 輪詢到 `REFUNDED`、全額、詳情頁顯示「款項已退回」）。

**突變驗證**（逐一套用後跑相關單元＋整合測試，每個都被抓到）：

| 突變 | 失敗的測試 |
|------|-----------|
| S1 退款又看 toggle | 整合「toggle 關閉後仍經 Stripe」、WireMock UT-REFUND-KEY-004、UT-PAY-STRIPE-012／015 |
| S2 遲到付款什麼都不做 | 整合遲到付款與併發、UT-PAY-STATE-045／046 |
| S3 遲到付款只留稽核、不轉 `REFUNDING` | 整合遲到付款與併發、UT-PAY-STATE-045 |
| S4 系統退款不檢查訂單是否 `REFUNDING` | UT-PAY-STRIPE-014 |
| S5 失敗後不退避 | 整合「Stripe 拒絕 → 回滾 → 退避後重試」、退避加倍／上限 |
| S6 整頁都失敗就停 | 「整頁失敗不餓死後面」等 5 個 |
| S7 舊版端點放行 Stripe 付款 | 舊版端點拒絕 Stripe |
| S9 搶輸被當成失敗 | 「搶輸不算失敗」 |
| S11 忽略額度 CAS 的結果 | 整合「4 個處理者只退一次」、既有的 `refund_concurrentClaim_rejectsWithoutCallingStripe` |

**全量**（`mvn -o clean verify`，11 分 42 秒）：單元 1900（+17）／整合 662（+11）／0 失敗；checkstyle 0、PMD 通過。`make validate-e2e`：**101 個測試，97 通過／4 略過／0 失敗**（3.7 分；含新的真實後端 `E2E-BPAYR-07` 與 3 個訂單退款 mock 案例）。

## 6. 決策與已知限制

- **Stripe 真實路徑仍沒對真實 Stripe 驗證**（沒有金鑰）：`WireMock` 與 mock 閘道驗證「我們送什麼、收到結果怎麼處理」，不驗證 Stripe 對同冪等鍵的反應與退款延遲。`STRIPE_PRODUCTION_CHECKLIST.md` §D 已加兩個測試模式的人工驗證項。
- **只涵蓋訂單**；訂房的取消退款與 `DEF-308` 訂房側是 Sprint 227。
- **重複付款不自動處理**：訂單已 `PAID` 又收到第二筆付款，留稽核與錯誤日誌，不退任何一筆（沒有唯一正確答案；Stripe 冪等鍵讓這個情況在正常流程下不會發生）。
- **退避狀態只在記憶體**：重啟後多試一次；看不到「第幾次」的持久紀錄，但每次失敗都有 `AUTO_REFUND_FAILED` 稽核（含 attempt 與下次重試時間）。沒有管理後台畫面，只能查稽核紀錄。
- **`APP_SCHEDULING_ENABLED=false` 會連自動退款一起關閉**（取消已付款訂單只會停在 `REFUNDING`，需管理員手動退款）；已寫進 runbook 與上線檢核表。
- **不通知買家退款完成**（通知類型 `REFUND_COMPLETED` 已存在於通知系統，但沒有接上；PRD「取消後即時收到退款狀態通知」同屬 PRD US-014 的通知項目，同 Sprint 219／225 的未做項目）。
- **`refundOrderPayment` 的語意變更**：Stripe 付款不再依賴 toggle。對「toggle 關閉＋STRIPE 付款」的管理員手動退款也是如此——這是修正（原本是靜默錯帳），不是新限制。

## 7. 後續

- Sprint 227：`DEF-312`（訂房取消依 PRD Q14 退款：≥24 小時全額、<24 小時不退、商家取消全額）＋`DEF-308` 訂房側；訂房的取消需改條件式 UPDATE（避免併發重複退款），並把舊版退款端點對訂房付款的旁路收掉（只標 `CANCELLED`、不釋放日曆）。
- Sprint 228：依賴審計改為每週排程（不擋 push）。
- 已登記未修：`DEF-315`。
