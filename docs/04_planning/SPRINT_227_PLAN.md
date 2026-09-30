# Sprint 227 Plan — 訂房取消依 PRD Q14 退款（DEF-312）與付款成功時訂房已取消（DEF-308 訂房側）

**Sprint**: Sprint 227
**日期**: 2026-10-01

## 1. 起點

使用者 2026-10-01 對 `DEF-312` 回覆「請對齊PRD」，對 `DEF-308` 回覆「請依照最佳化進行！」（排程見 [SPRINT_225_PLAN.md](SPRINT_225_PLAN.md) §1）。本輪建立在 Sprint 226 的自動退款排程上（[SPRINT_226_PLAN.md](SPRINT_226_PLAN.md)）。

PRD §15.2.5 與 §17.4.5（Q14，決策日期 2026-03-25）：

- 買家取消：距離預訂時段 **>= 24 小時**全額退款；**< 24 小時**不退款（`refundStatus = NONE`，TC-M05-016／017）。
- 商家主動取消：一律全額退款。
- 取消回應含 `canceledAt`／`canceledBy`／`refundStatus`（`NONE | PENDING | COMPLETED`）／`refundAmount`。
- 驗收標準：取消後即時收到退款狀態；確認預訂前顯示 Q14 取消政策摘要（US-012）。

## 2. 查證（現況）

1. `cancelBooking` 只改訂房狀態、釋放日曆、退還優惠券，**不動付款、不看 Q14、沒有併發防護**（讀狀態→判斷→`setStatus`→`save`；兩個併發取消都通過舊快照檢查，各自釋放一次日曆與優惠券。自動退款上線後就是重複退款）。
2. 訂房沒有 `REFUNDING` 狀態，取消時沒有地方記「這次該退多少、退到哪了」。
3. 舊版 `POST /v2/payments/refund` 對訂房付款「退款」＝付款標 `REFUNDED`、訂房標 `CANCELLED`，**不釋放日曆**（日期永久被佔）、不看 Q14、不退優惠券（`DEF-303` (3) 的訂房部分）。
4. Stripe 付款成功時訂房已取消（買家在 Stripe 頁停留時取消）：Sprint 221 只留稽核，沒有退款（`DEF-308` 訂房側）。
5. **商家端沒有取消訂房的入口**：`POST /v2/bookings/{id}/cancel` 的擁有權檢查只放行買家本人與管理員——`STORE_OWNER` 雖持有 `booking:cancel`，取消別人的訂房仍回 403；前端沒有 PRD 列的 `/dashboard/bookings`，`BookingRepository.findByTenantIdOrderByCreatedAtDesc` 沒有任何呼叫者。「商家取消一律全額退款」目前只有管理員代為取消這條路走得到（見 §6）。

## 3. 設計

**取消決定、排程執行**——與 Sprint 226 同一個理由（Stripe 呼叫不該在買家的取消請求與資料庫交易裡）。`V85` 在 `bookings` 記錄 PRD 要求的欄位：

| 欄位 | 說明 |
|------|------|
| `cancelled_at`／`cancelled_by` | 取消時間；取消方 `CUSTOMER` 買家本人／`MERCHANT` 商家或管理員代為取消／`SYSTEM` 逾時取消（Sprint 225 的逾時取消一併記錄） |
| `refund_status` | `NONE` 不需退款（未付款，或依 Q14 不退）／`PENDING` 等待自動退款／`COMPLETED` 已退回；`NOT NULL DEFAULT 'NONE'`，歷史訂房一律 `NONE` |
| `refund_amount` | 取消時決定的應退金額；`NONE` 時為 NULL |

**`BookingRefundPolicy`**（純邏輯）：買家取消看 24 小時門檻（`Duration.between(取消, 入住) >= 24h` 才全額，**剛好 24 小時算足夠**）；商家、管理員代為取消、系統取消一律全額。入住時刻＝入住日當天、房型設定的入住時間（預設 15:00），以**營運時區**（Asia/Taipei，`BusinessTime`）換成絕對時刻——雲端容器是 UTC，退款結果不能取決於部署環境（DEF-269 同一類）。

**`cancelBooking` 改寫**：

- 先以條件式 UPDATE 搶占「目前狀態 → `CANCELLED`」，同一筆被同時取消只有一個成功（其餘 `E-4007`）；退款只決定一次，日曆與優惠券只處理一次。日曆釋放失敗整個交易回滾。
- 只有已付款（`PAID`／`CONFIRMED`）且有可退款的付款（`SUCCESS`／`PARTIALLY_REFUNDED`）才有款項；應退金額以「付款還可退的金額」（付款金額減已退金額）為基準。
- 取消方：本人＝`CUSTOMER`；非本人（擁有權檢查只放行管理員）＝`MERCHANT`。
- 回應改為 PRD 的 `CancelResponse`；稽核 `BOOKING_CANCELLED` 不變，已付款者另寫 `BOOKING_REFUND_DECIDED`（含「退多少／不退」、取消方、入住日——事後爭議的依據）。

**`RefundProcessingService`** 同一個排程也退訂房（`refund_status = PENDING`）：共用游標、指數退避、失敗隔離、「搶輸不算失敗」；訂房版的搶輸判斷是「失敗當下退款進度已不是 `PENDING`」。`PaymentStateService.refundBookingPaymentAsSystem` 與訂單版同一個做法（額度 CAS → Stripe（付款方式為 `STRIPE` 一律經 Stripe，冪等鍵同為付款意圖＋退款前累計額＋金額）→ `PENDING → COMPLETED`），訂房不參與結算所以沒有結算調整。

**`DEF-308` 訂房側**：`markBookingPaidByStripe` 搶不到 `CREATED → PAID`（訂房已取消）時，條件式 UPDATE 把 `NONE → PENDING` 並記下付款金額，全額退回——**不適用 24 小時門檻**：訂房沒有成立、買家什麼都沒拿到。訂房不會被拉回 `PAID`（日曆可能已被別人訂走）。其他狀態（已 `PAID` 又收到一筆＝重複付款）不自動處理，留稽核與錯誤日誌。稽核 `STRIPE_PAYMENT_BOOKING_NOT_PAYABLE` 記 `refundQueued`（當下狀態以純量查詢取得）。

**舊版退款端點**拒絕訂房付款（`E-6002`），並移除因此成為死碼的訂房分支；退款只能經取消訂房。

**前端**：取消面板顯示取消政策摘要、取消成功後顯示「將退款 NT$X」或「依取消政策不退款」（未付款只說預訂已取消——回應的 `NONE` 分不出「沒付過款」與「依 Q14 不退」，由前端以取消前的狀態判斷）；詳情頁在 `PENDING`／`COMPLETED` 顯示退款資訊；結帳頁在「確認預訂」前顯示政策摘要（PRD US-012）。

## 4. 實作

- `V85__Bookings_Cancellation_Refund.sql`（欄位、CHECK、`idx_bookings_refund_pending` 部分索引）；`Booking` 新欄位與 `CancelledBy`／`RefundStatus`。
- `BookingRefundPolicy`；`BookingService.cancelBooking`／`decideRefundAmount`；`BookingDto.CancelResponse` 與 `BookingResponse` 新欄位；`BookingController` 回傳退款資訊。
- `BookingRepository`：`findPendingRefundBookingIds`／`completeRefundIfPending`／`requestRefundIfCancelled`／`findStatusById`／`findRefundStatusById`；`cancelIfPaymentExpired` 一併記錄取消方 `SYSTEM`。
- `PaymentStateService.refundBookingPaymentAsSystem`、`markBookingPaidByStripe`；`RefundProcessingService` 共用 `sweep`（訂單／訂房兩種 `Target`）；`PaymentService.processRefund` 拒絕訂房付款。
- 前端：`Booking` 型別與 `CancelBookingResult`、`CANCELLATION_POLICY_SUMMARY`、`cancelResultMessage`；詳情頁與結帳頁。
- 文件：`API_M06_Booking.md`（取消章節、欄位）、`SETTLEMENT_JOB_RUNBOOK.md`、`STRIPE_PRODUCTION_CHECKLIST.md`。

## 5. 驗證

**測試**

- 單元：`BookingRefundPolicyTest` 8 案例（剛好 24 小時＝全額、差 1 秒＝不退、24 小時又 1 秒＝全額、入住時間已過＝不退、商家／系統一律全額、以「還可退的金額」為基準、營運時區換算入住時刻）；`BookingServiceCancelRefundTest` 7 案例（已付款好幾天前取消＝全額 `PENDING`、入住日已過＝不退且稽核記「不退」、管理員代為取消＝`MERCHANT` 全額、未付款不查付款、找不到可退款的付款、`PARTIALLY_REFUNDED` 只退剩餘、併發搶輸 `E-4007` 且無任何副作用）；`PaymentStateServiceBookingTest` +10（訂房遲到付款排入全額退款／已 `PAID` 又收到一筆只稽核；`refundBookingPaymentAsSystem` 8 案例：Mock、Stripe 經 Stripe 即使 toggle 關閉、Stripe 拒絕、不在等待退款、找不到付款、額度 CAS 搶輸、部分金額、已部分退過只退剩餘）；`RefundProcessingServiceTest` +4（訂房游標走頁、失敗稽核記 `BOOKING`、搶輸不算失敗、排程進入點同時處理訂單與訂房）；`PaymentServiceRefundOwnershipTest` +1（舊版端點拒絕訂房付款）。四個既有取消案例（`BookingServiceOwnershipTest`、`BookingPromoCodeTest`）補上條件式 UPDATE 的 stub。
- 整合（真實 PostgreSQL＋真實 Redis，Stripe 閘道 mock，`BookingCancellationRefundIntegrationTest` 12 案例）：已付款、好幾天前、買家取消 → 全額退款（回應 `PENDING` 與金額、資料庫欄位、日曆釋放、優惠券退還、取消當下付款仍 `SUCCESS`；排程之後付款 `REFUNDED`、訂房 `COMPLETED`、稽核各一筆）；入住當天取消 → `NONE`、付款維持 `SUCCESS`、日曆仍釋放、稽核留下「不退」；未付款取消；**管理員代為取消入住當天的已付款訂房 → `MERCHANT` 全額**；Stripe 付款經 Stripe 退一次（冪等鍵、refund id）；**toggle 關閉後仍經 Stripe**；Stripe 拒絕 → 整個交易回滾、失敗稽核、退避期間不重試、恢復後成功；**4 個執行緒同時取消同一筆已付款訂房 → 恰好一個成功、其餘 `E-4007`、退款只決定一次、優惠券只退一次**；4 個處理者同時處理同一筆待退款訂房 → Stripe 只被呼叫一次；**webhook 遲到付款（訂房已取消）→ 全額 `PENDING`、留稽核、經 Stripe 退回（入住當天的訂房也全額）**；付款與取消併發（3 輪）兩種順序最後都退回；重複取消 `E-4007`。`BookingTimeoutIntegrationTest` 補斷言：逾時取消記錄 `cancelled_by = SYSTEM`。
- 前端 mock E2E：`at-booking-cancel-refund.spec.ts` 5 案例（取消政策與「將退款」＋退款處理中卡片、入住前不足 24 小時「不退款」、未付款只說預訂已取消、退款完成、結帳頁取消政策摘要）。真實後端 E2E：`E2E-BPAYR-08`（已付款訂房好幾天前取消 → 全額退款、日曆釋放、詳情顯示已退款）、`E2E-BPAYR-09`（入住當天取消 → 不退款、付款維持成功）。

**突變驗證**（逐一套用後跑上述單元＋整合測試，十一個都被抓到）：

| 突變 | 失敗的測試 |
|------|-----------|
| A 買家 24 小時內取消也退款 | 整合「不足 24 小時不退款」、規則 2 個邊界案例、`paidCustomerCancelsTooLate` |
| B 商家／系統取消也套用 24 小時門檻 | 整合「管理員代為取消仍全額」、規則 2 個案例、`paidCancelledByAdminAfterCheckIn` |
| C 取消不用條件式 UPDATE 搶占 | 整合「同時取消只決定一次」、`lostTheRace_isRejectedBeforeAnySideEffect` |
| E 取消不把退款進度記成 `PENDING` | 整合 7 個以上（所有需要退款的案例） |
| F 訂房遲到付款不排退款 | 整合「遲到付款全額退款」與併發、`paymentAfterCancellation_queuesFullRefund` |
| G 退款後不把訂房標為 `COMPLETED` | 整合所有退款完成的案例 |
| H 退款金額不取還可退的金額 | `alreadyPartiallyRefunded_refundsOnlyTheRemainder` |
| I 舊版退款端點放行訂房付款 | 舊版端點拒絕訂房付款 |
| J 訂房搶輸被當成失敗 | 「訂房搶輸不算失敗」 |
| K 取消不釋放日曆 | 整合日曆釋放斷言、`BookingServiceCancelRefundTest` 2 個 |
| L 管理員取消也算買家本人取消 | 整合「管理員代為取消」、`paidCancelledByAdminAfterCheckIn` |

**全量**（`mvn -o clean verify`，13 分 19 秒）：單元 1930（+30）／整合 674（+12）／0 失敗；checkstyle 0、PMD 通過。`make validate-e2e`（乾淨 DB、Flyway 含 V85、`ddl-auto=validate`、打包 JAR）：**108 個測試，104 通過／4 略過／0 失敗**（4.0 分；含新的真實後端 `E2E-BPAYR-08／09` 與 5 個取消退款 mock 案例）。`make validate-schema-doc` 由 pre-commit 在 Flyway 變動時自動執行。`tsc --noEmit` 乾淨。

## 6. 決策與已知限制

- **商家端取消入口仍不存在**（查證 5）。PRD「商家主動取消 → 全額退款」的規則已實作（`MERCHANT` 一律全額），但目前只有管理員代為取消走得到；`STORE_OWNER`／`HOST` 要能取消自己租戶的訂房，需要租戶範圍的擁有權檢查（IDOR 敏感）、商家端列表／取消 API 與 PRD 列的 `/dashboard/bookings` 頁面，並牽涉 `DEF-306` 的權限模型（HOST 沒有 `booking:cancel`）——這是新功能與權限決策，不是退款缺陷，**登記為 `DEF-316`，未做**。
- **管理員代為取消一律視為商家／平台取消（全額退款）**：PRD 的請求本文有 `canceledBy` 欄位讓呼叫端指定，本系統沒有提供；若管理員是受買家之託代為取消並希望套用 24 小時門檻，目前做不到。尚無這種營運流程，不另設參數。
- **不通知買家退款結果**（PRD「取消後即時收到退款狀態通知」同屬 PRD US-014 的通知項目，同 Sprint 219／225／226 的未做項目）；買家可在訂房詳情看到退款進度。
- **Stripe 真實路徑仍未對真實 Stripe 驗證**；`STRIPE_PRODUCTION_CHECKLIST.md` §D 已加兩個訂房的測試模式人工驗證項。
- **24 小時邊界的時區假設**：入住日以營運時區（台北）解讀，入住時間取房型設定；房型的時區欄位不存在，所有房源都視為台北時間。
- 重複付款（訂房已 `PAID` 又收到一筆）不自動處理，同訂單側。
- **`PARTIALLY_REFUNDED` 的訂房付款**在現行流程不會出現（訂房沒有部分退款入口）；`cancelBooking` 與退款執行仍以「還可退的金額」為基準，不假設付款是完整的。

## 7. 後續

- Sprint 228：依賴審計改為每週排程（不擋 push）。
- 等使用者決定：`DEF-316`（商家端訂房管理，含權限模型）、`DEF-306`。
- 已登記未修：`DEF-315`。
