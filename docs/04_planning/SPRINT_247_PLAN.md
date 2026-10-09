# Sprint 247 Plan — 訂房納入結算（DEF-353，依 PRD §6.2.1）

**Sprint**: Sprint 247
**日期**: 2026-10-09

## 1. 起點與範圍

### 1.1 起點

Sprint 245 收尾使用者拍板（見 [SPRINT_245_PLAN.md](SPRINT_245_PLAN.md) §2.2、[DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md) DEF-353）：訂房收益與退款納入結算（PRD §6.2.1「訂房納入結算」小節），且**開放訂房真實收款（Stripe）前必須完成**。Sprint 246 完成 DEF-350／352／354 後，本輪依序處理 DEF-353，是 M07 結算系統與訂房系統的整合收尾。

### 1.2 動工前的程式碼核對

| 核對項 | 程式碼現況 | 本輪處理 |
|--------|------------|----------|
| 結算來源 | `SettlementGenerator.generateStatementForTenant` 只讀 `Order`（`SETTLEABLE_STATUSES = COMPLETED, DELIVERED`） | 加入 `Booking` 來源，同一張結算單彙總（PRD：「訂房與訂單同週期、同抽成」） |
| 訂房的「恰好結算一次」標記 | `orders.settled_statement_id` 已有（DEF-273）；`bookings` 沒有對應欄位 | 新增 `bookings.settled_statement_id`（V91，比照 V83） |
| 訂房何時算「可結算」 | 無任何定義 | `status = COMPLETED`（退房完成）或（`status = CANCELLED` 且 `refundStatus = NONE` 且存在一筆 SUCCESS/PARTIALLY_REFUNDED 付款）——見 §2.1 的關鍵判斷 |
| 跨期退款調整 | `SettlementAdjustmentService.handleOrderRefund`／`adjustment_statements.order_id`（NOT NULL） | 新增 `handleBookingRefund`；`order_id` 改 nullable、新增 nullable `booking_id`（比照 `payments` 表 orderId/bookingId 互斥慣例） |
| 退款呼叫點 | `PaymentStateService.refundBookingPaymentAsSystem`（Q14／no-show PENDING 排程退款）、`refundBookingPaymentManually`（DEF-354 管理員人工退款）都明確註解「訂房不參與結算，沒有結算調整」 | 兩處都改為呼叫 `handleBookingRefund`（比照 `refundOrderPaymentCore` 的 best-effort try/catch） |
| 結算單駁回釋放 | `SettlementReviewer.rejectStatement` 只釋放 `orderRepository.releaseOrdersOfStatement` | 加入 `bookingRepository.releaseBookingsOfStatement` |
| 結算單筆數欄位 | `settlement_statements.total_orders` 只計訂單 | 新增 `total_bookings`，避免「總筆數」欄位說謊（金流稽核文件，不可讓訂房筆數靜默混進訂單數） |
| SRD／FRD 現況描述 | SRD §6.3.4「完成不觸發結算」；FRD BR-M06-12、§6A.7 第 5 點、§6B.7 第 7 點都明記「訂房不進結算」為已知差異 | 全部改為已納入，並記錄分辨「未收款」與「收款後不退款」的規則 |

### 1.3 關鍵判斷：CANCELLED 訂房何時算「已收款、不退款」

`Booking.refundStatus = NONE` 是建構時的預設值，以下兩種情況都會停在 `NONE`：

1. **從未收款**：`CREATED` 逾時取消（`BookingTimeoutService`／`cancelIfPaymentExpired`）；或買家在 `CREATED` 狀態主動取消。沒有任何成功付款，不該算收益。
2. **已收款、依政策不退款**：買家本人在入住前 24 小時內取消（PRD Q14）；或 no-show 自動取消（DEF-352，`cancelIfNoShow`，退款邏輯刻意不碰 `refundStatus`，維持 `NONE`）。這兩種都是從 `PAID`／`CONFIRMED` 轉態，已有一筆 SUCCESS 付款，依政策保留收益。

`Booking` 實體本身不記錄「取消前是什麼狀態」，無法單從 `refundStatus` 欄位分辨。判斷方式改為：**是否存在一筆 `SUCCESS` 或 `PARTIALLY_REFUNDED` 的 `Payment`**（與 `decideRefundAmount`／`refundBookingPaymentAsSystem` 已經使用的付款篩選條件相同）。查詢以 JPQL `EXISTS` 子查詢在資料庫層完成（比照 `BookingRepository.findExpiredUnpaidBookingIds` 既有寫法），不违反 `SettlementCalculator` 「純函數、不碰 DB」的既有設計原則（見該檔案類別註解）。

### 1.4 方法

- 一張結算單彙總訂單與訂房（不新增 `statement_type` 判別欄位，不拆表）：`total_gmv`／`total_refunds`／`commission_amount`／`net_settlement_amount` 為兩者合計，`total_orders`／`total_bookings` 分開計數。
- 認領（`settled_statement_id` 標記）比照 DEF-273：原生 `UPDATE ... WHERE settled_statement_id IS NULL`，訂單與訂房各自一次原子認領；任一邊認領數不足都拋例外，回滾整張結算單（同一交易，天然一致）。
- 退款調整（跨結算週期）比照 Sprint 86 既有流程：未結算→不動（下次結算時從付款的 `refundedAmount` 自然反映）；`PENDING`／`PENDING_REVIEW`→直接扣當期；`APPROVED`／`PAID`／`FAILED`→生調整單，下期折入。
- 歷史訂房不回填（`settled_statement_id` 留 NULL）：比照 V83 的既有先例，下一次週結算會把「目前已符合可結算條件、尚未認領」的歷史訂房一併納入（包含 Sprint 245～246 已經退房完成或 no-show 取消的既有資料）。**此為刻意的已知副作用，非本輪新增假設**。
- 測試順序：單元（`SettlementCalculator` 新增 overload、`SettlementAdjustmentService.handleBookingRefund`）→真實資料庫整合測試（結算生成、併發認領回滾、跨期退款調整、駁回釋放）→`PaymentStateServiceTest` 既有退款測試改為驗證有呼叫 `handleBookingRefund`。

## 2. 使用者決策與假設

### 2.1 已拍板（Sprint 245 收尾，PRD §6.2.1）

- DEF-353：訂房收益與退款納入結算；不退款取消的已收款於取消所在結算週期視為商家收益；開放訂房真實收款前必須完成；假設業務沒有訂房的線下對帳流程。

### 2.2 本輪假設（PRD／既有決議未明定）

| 題目 | 本輪採用 | 理由 | 若要改 |
|------|----------|------|--------|
| 「已收款」的判斷依據 | 存在 `SUCCESS`／`PARTIALLY_REFUNDED` 付款（§1.3） | `refundStatus=NONE` 無法單獨分辨「未收款」與「收款後不退款」；此判斷與既有退款邏輯（`decideRefundAmount`）同一套篩選條件，非新規則 | 若改為在 `Booking` 新增「取消前狀態」欄位記錄，需另一次遷移 |
| 結算單是否拆分訂單／訂房 | 不拆，彙總同一張（`total_bookings` 新欄位僅計數，GMV／抽成/退款/淨額合計） | PRD 原文「本節公式的『訂單』包含訂房」「訂房與訂單同週期、同抽成」 | 若商家需要分開對帳，需新增 breakdown 欄位或子表 |
| 認領的時間基準 | 比照訂單用 `created_at`（建立時間）而非完成／取消時間 | 與 DEF-273 同一邏輯：避免「本週建立、下週才完成」的訂房永遠跳過任何一期 | — |
| 歷史訂房回填 | 不回填，下次結算自然掃到（§1.4） | 比照 V83 既有先例；歷史結算單過去從未對訂房結算過，沒有「補算」的基準可回填 | — |

## 3. 實作內容（清單）

| 檔案 | 變更 |
|------|------|
| `backend/.../db/migration/V91__Bookings_Settlement_And_Adjustment_Booking_Id.sql` | 新增 `bookings.settled_statement_id`＋索引；`settlement_statements.total_bookings`；`adjustment_statements.booking_id`（nullable）＋`order_id` 改 nullable |
| `backend/.../domain/model/order/Booking.java` | 新增唯讀影子欄位 `settledStatementId` |
| `backend/.../domain/model/settlement/SettlementStatement.java` | 新增 `totalBookings` |
| `backend/.../domain/model/settlement/SettlementAdjustment.java` | `orderId` 改 nullable；新增 `bookingId` |
| `backend/.../domain/repository/BookingRepository.java` | `findUnsettledEligibleByTenantIdAndCreatedAtBefore`、`markSettled`、`releaseBookingsOfStatement`、`findSettledStatementId` |
| `backend/.../core/settlement/SettlementCalculator.java` | `filterSettleableBookings`、`calculateTotalGmv(List<Booking>)`、`calculateTotalRefunds(List<Booking>, Map)` overload |
| `backend/.../core/settlement/SettlementGenerator.java` | `generateStatementForTenant` 彙總訂房 GMV／退款／認領；`totalBookings` 寫入 |
| `backend/.../core/settlement/SettlementAdjustmentService.java` | 新增 `handleBookingRefund` |
| `backend/.../core/settlement/SettlementReviewer.java` | `rejectStatement` 加入 `releaseBookingsOfStatement` |
| `backend/.../core/settlement/SettlementMapper.java`／`SettlementService.java` | DTO 加 `totalBookings` |
| `backend/.../core/payment/PaymentStateService.java` | `refundBookingPaymentAsSystem`／`refundBookingPaymentManually` 呼叫 `handleBookingRefund`；更新過時註解 |
| `backend/.../core/booking/BookingService.java` | `checkOut`／no-show 相關註解更新（不再宣稱「不觸發結算」） |
| 單元／整合測試（多檔） | 見 §4 |
| [API_M06_Booking.md](../02_architecture/API_M06_Booking.md) | 退款端點行為說明更新（結算調整副作用） |
| [SRD_System_Architecture.md](../02_architecture/SRD_System_Architecture.md) | §6.3.4 改寫；§6.2.1（若有）或指向 PRD |
| [SRD_Database_Schema.md](../02_architecture/SRD_Database_Schema.md) | `make sync-schema-doc` 自動同步，不手改 DDL |
| [E-Commerce_FRD_v1.0.md](../01_requirements/E-Commerce_FRD_v1.0.md) | BR-M06-12、§6A.7 第 5 點、§6B.7 第 7 點更新為已納入；新增 BR-M07 條目 |
| [DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md) | DEF-353 移入已完成；版本鏈 |
| [RELEASE_TRACKER.md](RELEASE_TRACKER.md) | Sprint 247 列 |
| 本檔 | 計畫書 |

## 4. 守門與測試

| 層 | 內容 | 結果 |
|----|------|------|
| 單元 | `SettlementCalculatorTest`、`SettlementAdjustmentServiceTest` 新增 `handleBookingRefund` 8 案例（PENDING／PENDING_REVIEW／APPROVED／PAID／FAILED／REJECTED 系列／未結算／零金額）；`SettlementScheduledJobIntegrationTest`／`SettlementGeneratorClaimTest`／`SettlementGeneratorBusinessWeekTest`／`SettlementGeneratorManualTriggerTest`／`SettlementReviewerTest`／`SettlementTenantScopeTest` 因建構子新增 `bookingRepository` 參數同步修正 | ✅ 全數通過：Settlement 套件 98 個測試（0 失敗）；`PaymentStateServiceBookingTest`／`RefundProcessingServiceTest`／`PaymentStateServiceTest`／`BookingServiceCheckInOutTest` 139 個測試（0 失敗），既有測試零回歸 |
| 整合（真實 PostgreSQL） | 新增 `SettlementBookingIntegrationTest`（7 案例）：COMPLETED 訂房納入恰好一次／CANCELLED+NONE+已收款納入／CANCELLED+NONE+從未收款排除／CANCELLED+PENDING 排除／訂房與訂單同張彙總／駁回釋放訂房／事後人工退款產生 `booking_id` 調整單 | ✅ 全數通過（含 V91 遷移本身在真實 Postgres 乾淨套用） |
| 編譯 | 每個檔案異動後確認 `mvn -o compile`／`test-compile` 真的執行了 javac（非 `Nothing to compile` 假訊號；過程中靠 `mcp__claude-vscode__getDiagnostics` 抓到一次被 Maven／JDT 競態掩蓋的真錯誤，見 [[mvn-phantom-build-success-after-failure]]） | ✅ 全部確認為真實編譯結果 |
| 全量回歸 | 改動生產邏輯（結算、付款退款），依規範跑 `mvn -o verify` | ✅ BUILD SUCCESS：單元 2165、整合 808，皆 0 失敗／0 錯誤；checkstyle（main+test）0 違規 |
| Schema 文件 | `make validate-schema-doc` → 發現 PRD §8.2.9 漏列 `total_bookings`（SRD_Database_Schema.md 的 DDL 本身因 `bookings`／`adjustment_statements` 不在其追蹤清單內，無需改） → 補上後重新驗證通過 | ✅ 通過（91 個遷移套用一致） |
| 本地完整守門 | `make validate-release`（等價雲端 CI：act backend-unit + backend-integration + frontend + schema 漂移守門 + Playwright E2E） | ✅ 全數通過：E2E 138 passed／4 skipped／0 failed（5.7 分鐘）；FULL 記錄已寫，30 分內放行 push |
