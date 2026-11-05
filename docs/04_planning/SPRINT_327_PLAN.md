# Sprint 327 Plan — DEF-452：退款流程對調整單處理的 try/catch（量測、結論與修正）

## 1. 起點與範圍

### 1.1 起點

[Sprint 326](SPRINT_326_PLAN.md) 收尾時，使用者把總結貼回來並說「請繼續完成任務！」，**沒有 `==>` 回覆**。這是無 `==>` 變體的第十七個實例（解讀為**推論**，不是使用者明說的）。總結自己寫了下一步：「下次說『請繼續』我可以直接做：先量測 `DEF-452`，屬實的話修法比照 `DEF-443`」，所以範圍就是這一項，不另外重新分類追蹤表。

- 總結「還等你決定」的項目（`DEF-294`、`DEF-449`、`DEF-419`、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取，以及 Sprint 325 留下的兩個觀察）**不在這句話的授權範圍內**，本輪不碰。
- 長期授權（2026-10-29）：本輪 commit 後直接 push，連同 Sprint 326 留在本機的純文件回填 commit `6e8d8fef` 一起帶出。

### 1.2 成功標準（開工時定義）

1. **先量測、再決定修不修、怎麼修**：`DEF-452` 目前只是讀碼推論（Sprint 326 追蹤表明寫『未量測』）。用真實 PostgreSQL＋真實的 `@Transactional` 代理量測三個呼叫點，不用 mock（mock 沒有交易代理，看不到 rollback-only）。
2. 修法要先對照程式庫既有慣例（Rule 11）：同一種形狀（交易代理內的例外被 try/catch 吞掉）在 `OrderService.compensateCancellation`（DEF-303）已經有結論；`DEF-443`（撥款移到提交之後）的前提是被呼叫端是**外部副作用**——這點要確認調整單是不是同一類。
3. 紅燈先行：把想要的行為寫成真實棧案例，先在未修改的程式碼上跑出紅燈，預測先登記（§2）。
4. 突變驗證預先登記每組『恰好該紅的案例』。
5. 全量後端 `mvn -o clean verify`（全新資料庫）通過、Checkstyle 0；push 後雲端 CI 全綠並核對整合測試數。
6. 追蹤表如實更新；過程中發現的相鄰問題**只登記、不順手修**。

### 1.3 動手前用工具核對的事實

| 說法（出處） | 核對結果 |
|---|---|
| 『`PaymentStateService` 有 3 處（約 383／452／539 行）以 `catch (RuntimeException e)` 吞掉 `handleBookingRefund`／`handleOrderRefund` 的例外』（`DEF-452` 登記） | 屬實。三處分別在 `refundBookingPaymentAsSystem`（`@Transactional`）、`refundBookingPaymentManually`（`@Transactional`）、`refundOrderPaymentCore`（私有，被 `refundOrderPayment` 與 `refundOrderPaymentAsSystem` 兩個 `@Transactional` 入口共用）。生產碼呼叫者只有這三處 |
| 『被呼叫的方法自己也是 `@Transactional`』 | 屬實：`SettlementAdjustmentService.handleOrderRefund`（第 47 行）、`handleBookingRefund`（第 148 行）都是預設 `REQUIRED` |
| 這個 try/catch 的出處 | `SPRINT_86_PLAN.md` §2.3：『退款主流程失敗不應被此邏輯拖垮——比照既有 `recordAudit` 的 try-catch 容錯模式』。也就是照抄稽核寫入的容錯寫法；稽核的 `save` 對 UUID 主鍵通常延到 flush 才寫資料庫（Sprint 326 追蹤表的說法，本輪沒有再驗證），而這裡被呼叫的是 `@Transactional` 方法，兩者不能類比 |
| 既有單元測試怎麼『證明』這個承諾 | `PaymentStateServiceStripeTest.refund_settlementHookThrows_doesNotFailRefund`（第 453～472 行）用 Mockito `doThrow` 讓調整單服務拋例外，斷言記憶體裡的 `payment.getStatus()` 是 `PARTIALLY_REFUNDED`。mock 沒有交易代理，所以它只能證明『例外被 catch 住了』，看不到交易是否被標成 rollback-only |
| 訂房側有沒有測試 | **沒有**。`PaymentStateServiceBookingTest` 完全沒提到 `handleBookingRefund`；`SettlementBookingIntegrationTest` 等既有整合測試都是**直接呼叫** `settlementAdjustmentService.handleBookingRefund(...)`。精確 `grep`：碰 `settled_statement_id`／`settledStatementId` 的測試檔共 10 個〔含本輪新增的這一個〕，其中**只有本輪新增的**同時呼叫 `refundBookingPaymentAsSystem`／`refundBookingPaymentManually`／`processPendingBookingRefunds`。所以把這兩處呼叫整個刪掉，原本應該沒有任何測試會紅——這是推論（偵測這個突變需要『已被結算單認領的訂房』加上完整退款流程，只有本輪新增的類別有），沒有用全套測試跑這個突變確認 |
| 同型寫法在程式庫別處的處理 | `OrderService.compensateCancellation` 的 Javadoc：『不再以 try/catch 吞掉庫存或優惠券的失敗：PRD §15.3.1.2 要求狀態變更與資源釋放在同一交易內原子提交。何況兩者都經過交易代理，例外一拋出，交易就已被標成 rollback-only，吞掉只會讓提交時改丟 `UnexpectedRollbackException`』——**同一種形狀，結論是不吞** |
| `DEF-443` 為什麼適合 `afterCommit` | 撥款（Stripe Transfer）是**外部副作用**，不能與資料庫交易一起回滾；而且核准後的結算單停在 `APPROVED`（持久標記）、有重試端點可以補。調整單是**純資料庫寫入**（UPDATE 結算單金額或 INSERT 調整單），可以與退款在同一個交易原子提交 |
| PRD 對這個步驟的要求 | PRD §6.2.1『跨結算週期退款處理機制』：退款涉及已結算單時，『系統自動生成 `adjustment_statement`』——是退款的必然後果，不是可選項 |
| 全域例外處理器怎麼映射 | `GlobalExceptionHandler.handleGenericException`：所有非 `BusinessException` 的例外（含 `UnexpectedRollbackException`、`DataIntegrityViolationException`、NPE）→ `500 E-9900`。所以對 HTTP 呼叫者，修復前後的回應相同；差別在例外型別、日誌、與自動退款的失敗稽核文字 |

## 2. 量測設計與預測（跑之前登記）

### 2.1 做法

新真實棧類別 `RefundSettlementAdjustmentFailureRealStackIntegrationTest`（`integration` 套件）：真實 PostgreSQL＋真實 Redis，走真實的 `PaymentStateService`／`SettlementAdjustmentService`（含各自的 `@Transactional` 代理）與 `RefundProcessingService`；只有 Stripe 閘道以 `@MockBean` 取代（與 `OrderAutoRefundIntegrationTest`、`BookingCancellationRefundIntegrationTest` 相同的 `@MockBean` 組合，共用同一個 Spring context）。**不用任何測試替身讓調整單服務拋例外**，改用真實程式碼會自然失敗的資料：

- **資料庫層失敗（T-DB）**：被訂單／訂房認領的結算單是 `PENDING_REVIEW`，`total_refunds` 設成 `999999999999.00`（欄位 `DECIMAL(14,2)` 的上限附近）——真實的 `applyRefundDeduction` 會因 `numeric field overflow` 失敗，PostgreSQL 交易中止。
- **Java 層失敗（T-NPE）**：結算單 `status` 為 NULL（欄位可為 NULL）——真實的 `switch (statement.getStatus())` 拋 `NullPointerException`，此時 **PostgreSQL 交易沒有中止**，只剩 Spring 的 rollback-only 標記能讓退款失敗。這是區分『資料庫中止』與『Spring 標記』兩種機制的關鍵。

結算單與被認領的訂單／訂房用 JDBC 種（與 `SettlementReviewRaceRealStackIntegrationTest.seedStatement` 同形）。這是**合成的資料異常**：NULL 狀態與接近上限的金額在正式環境不是正常資料，我沒有數據說明它們多久發生一次（見 §7）。

### 2.2 案例與預測

| 案例 | 呼叫點／入口 | 觸發 | 預測（未修改的程式碼） |
|---|---|---|---|
| C0 控制組 | 訂單，自動退款排程 | 無（健康結算單） | 綠：訂單與付款 `REFUNDED`、結算單 `total_refunds` 增加、`net_settlement_amount` 減少（證明固件確實走到調整單、直接扣款在同一交易提交） |
| O1 | 訂單核心（`refundOrderPaymentAsSystem`），Stripe 付款 | T-DB | **整個退款回滾**（訂單仍 `REFUNDING`、付款 `SUCCESS`／0、無 `REFUNDED` 狀態紀錄、無 `ORDER_PAYMENT_REFUNDED` 稽核、結算單與調整單不變）；Stripe 被呼叫 **1 次**（錢已退出去）；`AUTO_REFUND_FAILED` 稽核的原因文字是 `Transaction silently rolled back because it has been marked as rollback-only`，**不含** `numeric field overflow`。資料修好後重試：退款完成、結算單被扣、Stripe 第 2 次呼叫、**冪等鍵與第 1 次相同** |
| O2 | 訂單核心（`refundOrderPayment`，使用者入口），Mock 付款 | T-NPE | 呼叫者收到 `UnexpectedRollbackException`（不是 NPE）；資料庫狀態與 O1 相同（整個回滾）。**這是證明『rollback-only 標記本身就讓退款失敗』的案例（PostgreSQL 交易沒有中止）** |
| B1 | 訂房系統退款（`refundBookingPaymentAsSystem`） | T-DB | 同 O1（沒有 Stripe）：整個回滾、失敗稽核文字是 rollback-only、資料修好後重試完成且結算單被扣 |
| B2 | 訂房人工退款（`refundBookingPaymentManually`） | T-NPE | 同 O2：呼叫者收到 `UnexpectedRollbackException`、整個回滾；狀態修好後重試完成且結算單被扣 |

**總預測**：資料庫狀態類斷言（整個回滾）在未修改的程式碼上**全綠**——try/catch 救不了退款，現況本來就是『調整單失敗 ⇒ 整個退款回滾』（fail-closed），與註解承諾的『失敗不應影響已完成的退款主流程』相反；紅燈只會出現在**例外型別**與**失敗稽核的原因文字**這兩類斷言。如果資料庫狀態類斷言紅了（退款在例外後仍被提交），代表 rollback-only 的機制判斷是錯的，`DEF-452` 的前提要重寫。

我事先認為『退款在例外後仍被提交』的可能性很低：`applyDirectDeduction` 的 Javadoc 已經寫了同一個機制（『例外穿出會把共用交易標成 rollback-only，已經成功退給 Stripe 的款項在本地的付款狀態就會連帶回滾』），`OrderService.compensateCancellation` 的 Javadoc 也是；但那兩處都是讀碼，這裡是第一次實測。

## 3. 修改前的紅燈（新真實棧類別，未修改的程式碼）

新類別 `RefundSettlementAdjustmentFailureRealStackIntegrationTest`（`integration` 套件，5 案例）。未修改的程式碼上 **5 個案例 4 個紅**（`Tests run: 5, Failures: 4`），用 `SoftAssertions` 讓一次紅燈列出該案例所有違反的斷言：

| 案例 | 預測（§2.2，跑之前登記） | 實測（未修改的程式碼） |
|---|---|---|
| C0 控制組 | 綠 | **綠** ✅ |
| O1（訂單、系統退款、T-DB、Stripe） | 資料庫狀態類斷言全綠；紅在失敗稽核的原因文字 | `Multiple Failures (2 failures)`，兩個都是原因文字：實際 `attempt=1,nextAttemptAt=…,error=Transaction silently rolled back because it has been marked as rollback-only`，既不含 `numeric field overflow`、又含 `rollback-only`。其餘**全綠**：訂單仍 `REFUNDING`、付款 `SUCCESS`／0／無 `stripe_refund_id`、無 `REFUNDED` 狀態紀錄、無 `ORDER_PAYMENT_REFUNDED` 稽核、結算單 `total_refunds` 不變、無調整單、`AUTO_REFUND_FAILED` 恰 1 筆、買家沒收到通知；Stripe 被呼叫恰 1 次 ✅ |
| O2（訂單、使用者退款、T-NPE） | 呼叫者收到 `UnexpectedRollbackException`；資料庫狀態類全綠 | `Multiple Failures (1 failure)`：`Expecting actual throwable to be an instance of NullPointerException but was UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only`；資料庫狀態類**全綠** ✅ |
| B1（訂房、系統退款、T-DB） | 同 O1 | 同 O1（2 個失敗，都在原因文字） ✅ |
| B2（訂房、人工退款、T-NPE） | 同 O2 | 同 O2（1 個失敗，在例外型別） ✅ |

**預測全部吻合，而且回答了本項的核心問題：`try/catch` 救不了退款。** O2／B2 是最乾淨的證據：它們的失敗是 Java 層的 `NullPointerException`，**PostgreSQL 交易沒有中止**，退款仍整個回滾——只能是 Spring 的 rollback-only 標記造成的。O1／B1（資料庫層）則是兩個機制疊加（PostgreSQL 交易中止＋Spring 標記）。

固件確實撞到我設計的失敗點（被吞掉的 `log.error` 帶著真正原因）：

```text
Settlement adjustment failed after booking refund: bookingId=…, error=JDBC exception executing SQL [UPDATE settlement_statements … ] [ERROR: numeric field overflow
Settlement adjustment failed after refund: orderId=…, error=JDBC exception executing SQL [UPDATE settlement_statements … ] [ERROR: numeric field overflow
Settlement adjustment failed after manual booking refund: bookingId=…, error=Cannot invoke "…SettlementStatement$SettlementStatus.ordinal()" because the return value of "…SettlementStatement.getStatus()" is null
Settlement adjustment failed after refund: orderId=…, error=Cannot invoke "…SettlementStatement$SettlementStatus.ordinal()" because the return value of "…SettlementStatement.getStatus()" is null
```

三個呼叫點（訂房系統退款、訂單核心〔系統與使用者兩個入口〕、訂房人工退款）都走到了。

**揭露**：每個案例後半段的『資料修好後重試收斂』斷言，在未修改的程式碼上**沒有被執行**（`SoftAssertions` 在前半段結束時就丟出）；它們只在修復後的程式碼上跑過。我的推論是這段與修復無關（重試時沒有任何例外，try/catch 有沒有都一樣），但這是推論，不是在未修改的程式碼上量到的。

## 4. 修復與驗證

### 4.1 修改

1. `PaymentStateService` 三個呼叫點（訂房系統退款、訂房人工退款、訂單核心）：**移除 `try/catch`**，直接呼叫 `handleBookingRefund`／`handleOrderRefund`；訂單核心處的註解寫出理由（原子提交、rollback-only 機制、與 `OrderService.compensateCancellation` 一致、重試安全的依據），訂房兩處指向它。
2. `SettlementAdjustmentService` 類別 Javadoc 補一段：兩個入口加入呼叫端的交易、與退款同一個交易原子提交、呼叫端不吞例外。
3. 沒有遷移、沒有 API 形狀變更、HTTP 回應不變（所有非 `BusinessException` 的例外本來就回 `500 E-9900`）。日誌少一行（原本的 `Settlement adjustment failed …` 與提交時的 `UnexpectedRollbackException` 各一次，現在只有一次、帶真正原因）；自動退款的失敗稽核原因文字從泛稱的 rollback-only 變成真正原因。

### 4.2 單元測試

- `PaymentStateServiceStripeTest`：原案例 `refund_settlementHookThrows_doesNotFailRefund`（斷言『拋例外不影響已完成的退款』，mock 沒有交易代理所以看不到問題）改為 `refund_settlementHookThrows_propagatesTheOriginalException`：`isSameAs(failure)`，不被吞也不被包。
- `PaymentStateServiceBookingTest`：**新增 4 案例**（系統退款、人工退款各 2 個）：①成功後確實呼叫 `handleBookingRefund(租戶, 訂房, 實際退款金額)`；②調整單處理拋例外 → 原樣往外拋。**這補上了一個原本完全沒有的涵蓋**：修復前沒有任何測試（單元或整合）走完整的訂房退款流程並檢查調整單被呼叫——既有的訂房結算整合測試都是直接呼叫 `settlementAdjustmentService.handleBookingRefund(...)`。

### 4.3 突變驗證（預先登記：規格檔先寫好 `must`，跑完逐一比對）

受測集合＝2 個單元類別（`PaymentStateServiceStripeTest` 22、`PaymentStateServiceBookingTest` 46）＋新真實棧類別（5）＝ **73 案例**。驅動由我依記憶檔的設計重寫（沒有去找前幾輪放在 scratchpad 的版本）：備份 → 驗證每個 `old` 恰好出現一次 → 套用 → 刪掉被改檔案的 `.class` 逼 Maven 重編 → `mvn -o test-compile surefire:test … failsafe:integration-test … -Dmaven.test.failure.ignore=true` → 從 surefire／failsafe 的 XML 取失敗集合 → 還原 → SHA-256；判決＝失敗集合與 `must` **恰好**相同，另用 `must_msg` 核對失敗訊息的方向。規格檔（含每組的 `must`）在跑之前寫好。開跑前乾跑『每個 `old` 恰好出現一次』並備份被突變的主檔、記錄 SHA-256。

縮寫：`S`＝`PaymentStateServiceStripeTest`，`BS`＝`PaymentStateServiceBookingTest$RefundBookingPaymentAsSystem`，`BM`＝`PaymentStateServiceBookingTest$RefundBookingPaymentManually`；`hook`＝『成功後確實呼叫調整單處理』的單元案例，`propagates`＝『調整單處理的例外原樣往外拋』的單元案例；`R.C0／O1／O2／B1／B2`＝新真實棧類別的五個案例（§3）。

| 突變 | 內容 | 預先登記的 `must`（恰好該紅） | 結果 |
|---|---|---|---|
| Z0 | 無突變（基準，全新資料庫） | （空）；`ran` 必須是 73 | `ran=73 failed=0` ✅ |
| M1 | 訂單核心放回 `try/catch`（吞例外） | `S.propagates`、`R.O1`、`R.O2` | **KILLED(exactly must)**：`S`＝`Expecting code to raise a throwable`；`O1`＝失敗稽核原因文字是 `Transaction silently rolled back because it has been marked as rollback-only`；`O2`＝`Expecting actual throwable to be an instance of NullPointerException but was … UnexpectedRollbackException` |
| M2 | 訂房系統退款放回 `try/catch` | `BS.propagates`、`R.B1` | **KILLED(exactly must)**：同 M1 的兩種紅燈 |
| M3 | 訂房人工退款放回 `try/catch` | `BM.propagates`、`R.B2` | **KILLED(exactly must)**：同上 |
| M4 | 訂單核心整個刪掉調整單呼叫 | `S.hook`、`S.propagates`、`R.C0`、`R.O1`、`R.O2` | **KILLED(exactly must)**：`S.hook`＝`Wanted but not invoked: settlementAdjustmentService.handleOrderRefund(…)`；`C0`＝`expected: 300.00 but was: 0.00`（沒有扣款）；`O1`＝`Multiple Failures (9 failures)`，第一個是 `expected: "REFUNDING" but was: "REFUNDED"`（退款沒有失敗、整個完成）；`O2`＝`Expecting actual not to be null`（沒有例外） |
| M5 | 訂房系統退款整個刪掉調整單呼叫 | `BS.hook`、`BS.propagates`、`R.B1` | **KILLED(exactly must)**：`B1`＝`Multiple Failures (6 failures)`，第一個是 `expected: "PENDING" but was: "COMPLETED"`。修復前沒有任何測試走完整訂房退款流程並檢查調整單（§1.3），這個突變原本應該存活（推論） |
| M6 | 訂房人工退款整個刪掉調整單呼叫 | `BM.hook`、`BM.propagates`、`R.B2` | **KILLED(exactly must)**：`B2`＝`Multiple Failures (5 failures)`，第一個是 `Expecting actual not to be null`（沒有例外） |
| M7 | 訂單核心把 `refundAmount` 換成 `payment.getAmount()`（部分退款時扣錯金額） | `S.hook`、`R.O2` | **KILLED(exactly must)**：`S.hook`＝`Argument(s) are different!`；`O2`＝`expected: 100.00 but was: 300.00`。**只有『資料修好後重試完成』那一半斷言殺得到它**：`O2` 前半用 NPE 擋住、金額沒傳到；`C0`、`O1` 是全額退款，換成付款總額值相同，所以不會紅（預先登記時就預測到） |

### 4.4 結果

8 次執行共約 23.5 分鐘（Z0 含重建資料庫 214 秒；M1 244 秒，其餘 147～183 秒）。**七組突變全部 `KILLED(exactly must)`，預先登記逐一吻合**，`must_msg` 全部找得到；每組還原後 SHA-256 與備份相同，結束後 `shasum -a 256 -c` 兩個檔案皆 OK、`git status` 只有預期的檔案、原始碼裡沒有突變殘留字串。紅燈原因都讀過失敗訊息（上表『結果』欄的引文就是來源），不只看紅綠。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 受影響的單元類別（`PaymentStateServiceStripeTest`、`PaymentStateServiceBookingTest`、`PaymentStateServiceTest`、`PaymentStateServiceStoreGuardTest`、`RefundProcessingServiceTest`、`SettlementAdjustmentServiceTest`；修復後第一次跑） | 182／0 |
| 新真實棧類別 `RefundSettlementAdjustmentFailureRealStackIntegrationTest` | 5／0（修復前 4 個紅）；全量 verify 內同為 5／0 |
| 突變驗證 | 基準 Z0＋7 組，全部 `KILLED(exactly must)`（§4.3） |
| 一次性探針（`DEF-453`） | 2 案例，輸出原文在附錄 A；已從測試類別移除，還原後 SHA-256 與探針前一致、類別裡沒有探針殘留，`target` 裡含探針的舊 class 與報告已刪 |
| 全量後端 `mvn -o clean verify`（全新資料庫） | `BUILD SUCCESS`，**24 分 24 秒**（Sprint 326 為 19 分 07 秒；差異原因沒查）；單元 **2482／0**（2478＋4）、整合 **1027／0**（1022＋5）、0 失敗／0 錯誤／0 跳過；Checkstyle main／test 0 違規。關聯類別：新類別 5、`PaymentStateServiceStripeTest` 22、`PaymentStateServiceBookingTest` 的退款兩組 10＋11、`RefundProcessingServiceTest` 17、`OrderAutoRefundIntegrationTest` 11、`BookingCancellationRefundIntegrationTest` 19 |
| 文件守門（四個類別，改完所有文件後跑） | **14／0**：`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1 |
| push／雲端 CI | **已 push**：`59cd8e72..3e03d234`（連同 Sprint 326 的純文件回填 commit `6e8d8fef`），pre-commit 與 pre-push 輕量守門〔含 schema 漂移關卡〕都一次通過，沒有使用 `--no-verify`。**雲端 CI 全綠**：run [42030547563](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/42030547563)，三個 job 皆 success（Backend Unit Tests 2 分 45 秒／Frontend Lint & Build 1 分 11 秒／Backend Integration Tests & Package 12 分 56 秒），整個 run 15 分 47 秒，`gh run view --json` 確認 headSha 為 `3e03d234`；下載整合 job 完整日誌（約 84 MB，去除 ANSI 色碼後解析；這版 `gh` 把 ESC 字元顯示成字面的 `^[`，兩種寫法都要去）確認整合 **1027／0／0／0**、Checkstyle 0，`RefundSettlementAdjustmentFailureRealStackIntegrationTest` 5、`OrderAutoRefundIntegrationTest` 11、`BookingCancellationRefundIntegrationTest` 19 與本機逐類別一致（沿用 Sprint 326 的觀察：單元 job 的 `mvn test` 帶 `-q`，日誌不印案例數；這次沒有下載單元 job 的日誌，單元案例數只核對了本機） |

本輪有生產程式碼變更（後端 `PaymentStateService` 三處＋一段 Javadoc），未跑 Playwright 完整 E2E 與 `validate-schema-doc`（沒有前端或 schema 變更）。測試類別的 Javadoc 限定（`DEF-453`）是全量 verify 之後才補的註解，沒有重跑全量；它是純註解變更，由 commit 時的編譯與 Checkstyle 檢查把關。

## 6. 過程中的失誤與修正

1. **追蹤表的建議在動手前被推翻。** `DEF-452` 的建議欄（我在 Sprint 326 寫的）是『屬實的話修法比照 `DEF-443`，改成 `afterCommit`＋`REQUIRES_NEW`』。讀 `OrderService.compensateCancellation` 的 Javadoc 與 `SettlementAdjustmentService.applyDirectDeduction` 的 Javadoc 之後，這個建議不成立（理由見 §8 第 2 點）。那條建議寫在量測之前（Sprint 326 追蹤表標明『讀碼推論』）；當時的同型盤點清單（Sprint 326 計畫書 §9）沒有包含 `OrderService.compensateCancellation`——它沒有 `catch`，不在『`catch` 與 `@Transactional` 並存』的盤點範圍內。
2. **我原本認為 fail-closed 的好處之一是『回滾後重試能補上調整單』，探針推翻了它的一半。** 測試裡的重試成立（沒有 webhook），但 Stripe 付款的 `charge.refunded` webhook 通常先到，把訂單與付款標成 `REFUNDED`，之後排程不再重試——調整單永遠漏掉（附錄 A 的 A3）。這是 `DEF-453`，不是本輪造成的（webhook 路徑原本就不呼叫調整單處理），但它讓『重試安全』只剩『不會多退』這一層成立，『重試收斂』不能一概而論。代碼註解只寫了『不會多退』，測試類別的 Javadoc 一併補上限定。
3. **寫文件時自審抓到兩句說得比證據多（commit 前）：**①DEF-453 草稿寫『出貨後的退款只能由客服在 Stripe 後台操作』——我沒有逐一查過所有退款相關端點，改成『我看得到的途徑是…（讀碼，沒有逐一查過）』；②追蹤表與計畫書草稿寫『把兩處訂房呼叫刪掉也不會有測試紅』當成事實——其實是推論（我只跑了 73 案例的子集，沒有用全套測試跑這個突變）。補上精確 `grep`（碰 `settled_statement_id` 的 10 個測試檔裡，只有本輪新增的同時呼叫訂房退款流程）當依據，並標明推論。
4. **同一輪寫 API 文件時，版本說明裡的章節號我先寫成 §4.9，核對標題後更正為 §4.15**（人工退款端點）。
5. **未修改程式碼上的紅燈沒有跑到『資料修好後重試』那一半**（§3 的揭露）：`SoftAssertions` 在前半段結束時就丟出。這一半在修復後的程式碼上通過；我的推論是它與修復無關，但沒有在未修改的程式碼上量到。

## 7. 限制（誠實揭露）

- **觸發條件是我合成的資料異常**（NULL 狀態、接近上限的金額）。真實環境多久發生一次、會由什麼觸發，我沒有數據，也沒有找到自然的、非資料庫層的觸發。一個自然觸發的**讀碼推論**（沒有實測）：駁回結算單（`rejectStatement`：先搶結算單那一列的鎖，再 `releaseOrdersOfStatement`／`releaseBookingsOfStatement` 更新訂單／訂房）與同一張結算單內的退款（先刷出訂單／訂房的更新，再 `applyRefundDeduction` 扣結算單）鎖順序相反，理論上可死鎖，PostgreSQL 會中止其中一個。那時 `try/catch` 更是徒勞（PostgreSQL 交易已中止）。
- **訂單側的調整單分支在正式環境沒有已知的觸發路徑**（讀碼）：可結算的訂單是 `COMPLETED`／`DELIVERED`（`SettlementCalculator.SETTLEABLE_STATUSES`），而呼叫調整單處理的 `refundOrderPayment`／`refundOrderPaymentAsSystem` 只接受 `PAID`／`CANCELLED`／`REFUNDING`（`OrderStateMachine.canRefund`；系統退款還要求 `REFUNDING`），`COMPLETED` 是狀態機終態。測試固件把訂單直接掛到結算單上是合成的。有現實路徑的是**訂房人工退款**（Sprint 247 的 Javadoc：no-show 取消的訂房〔`CANCELLED`＋`refund_status=NONE`〕會被週結算認領）；訂房系統退款的 Javadoc 說對這類訂房實際上恆為 no-op（尚未被認領）。所以 `DEF-452` 的**實務暴露面很小**，本輪價值主要在把不實的承諾與假測試拿掉、補上原本沒有的涵蓋。
- **Stripe 的外部副作用不能回滾**：調整單處理失敗、整個本地交易回滾時，Stripe 已經把錢退出去；本地狀態在重試或 webhook 之前與 Stripe 不一致（既有設計，`DEF-288` 的冪等鍵讓重試不會多退）。webhook 先到的情況見 `DEF-453`（附錄 A，實測）。
- **只在服務層呼叫，沒有走 HTTP**：HTTP 回應（`500 E-9900`）由讀 `GlobalExceptionHandler.handleGenericException` 得知，沒有用 MockMvc 實測。
- **單一 JVM、單一節點**；沒有測多節點同時退款（額度 CAS 與冪等鍵的既有保護不在本輪範圍）。
- 單元層的訂房案例（4 個）是修復後才寫的，沒有『修復前紅燈』；牙齒由突變驗證證明（M2、M3、M5、M6）。修復前的紅燈由真實棧提供（§3）。
- `AdminService.recordAudit`（`DEF-452` 原文的 (iii)）本輪沒有再看。

## 8. 我替使用者做的決定（可一句話推翻）

1. **『請繼續』的解讀**：照總結自己寫的——先量測 `DEF-452`。**沒有碰**『還等你決定』的項目。
2. **沒有照追蹤表原建議（比照 `DEF-443` 改到提交之後）修，改成『不吞例外、與退款同一個交易原子提交』。** 理由：①`DEF-443` 的撥款是**外部副作用**，不能與資料庫交易一起回滾，而且核准後的結算單停在 `APPROVED`（持久標記）、有重試端點可補；調整單是**純資料庫寫入**，可以與退款原子提交；PRD §6.2.1 把它寫成退款涉及已結算單時『系統自動生成』的必然後果。②同型寫法在 `OrderService.compensateCancellation`（`DEF-303`）已有結論——不吞、原子提交（PRD §15.3.1.2），Rule 11 要求配合程式庫既有慣例。③量測證明現況本來就是 fail-closed：改到提交之後會把『退款整個失敗、有失敗稽核、可重試』變成『退款成功、調整單靜默丟失』（失敗只剩一行日誌，賣家少被扣而沒有任何自動補救），是更差的帳務結果——這一句是對替代方案的**推論**，沒有實作也沒有量測。④本輪的修法**不改變任何資料庫結果**，只是把不實的承諾與假測試拿掉、讓失敗原因看得見。**想要『退款永遠不被結算簿記拖垮』（fail-open）的話**，需要的是 `afterCommit`＋`REQUIRES_NEW` **加上**失敗的持久記錄與補救機制（定期對帳或重試端點），還要處理 webhook 先到（`DEF-453`）——不是把 `try/catch` 包一層；成本比本輪大很多，也是帳務風險取捨，請你決定。
3. **修改範圍只有 `PaymentStateService` 的三處 `try/catch`、相關註解與 `SettlementAdjustmentService` 的類別 Javadoc**，不改調整單處理本身的邏輯。
4. **可見變更**：自動退款的 `AUTO_REFUND_FAILED` 失敗稽核原因文字從泛稱的 rollback-only 變成真正原因（維運在稽核表看得到為什麼失敗）；呼叫者收到真正的例外；日誌少一個重複的 error。HTTP 回應與資料庫結果不變。
5. **`DEF-453` 只登記、不修**：webhook 路徑要帶出退款金額、決定部分退款語意、避免與 API 路徑重複扣款，是設計題。
6. **`API_M06` 補記一句**（§4.15 人工退款，有現實路徑的呼叫點）並升版 v2.8；訂單退款的 API 文件沒有描述調整單的失敗行為，不動。

## 9. 新登記與更新的缺陷

| 項目 | 狀態 |
|---|---|
| `DEF-452` 退款流程對 `handleOrderRefund`／`handleBookingRefund` 的 `try/catch` 擋不住外層交易被標成 rollback-only（`PaymentStateService` 3 處） | 🟡 → ✅ 已完成（Sprint 327）；先量測屬實，但行為本來就是 fail-closed，修的是不實的承諾，沒有照原建議改成 `afterCommit` |
| `DEF-453` Stripe `charge.refunded` webhook 完結的退款不扣結算單（不呼叫調整單處理、不記 `refunded_amount`）；也讓 `DEF-452` 的『回滾後重試收斂』在 Stripe 付款上只有重試先於 webhook 才成立 | 新登記，🟡，**一次性探針實測**（附錄 A），未修 |

## 10. 狀態

- **狀態**：實作、真實棧測試、突變驗證、全量 verify、文件均完成；追蹤表 `DEF-452` 轉 ✅，新登記 `DEF-453`。
- **push**：依 2026-10-29 長期授權（『做完一輪後，直接 Commit + Push to main』）直接 push，`59cd8e72..3e03d234`，連同 Sprint 326 留在本機的純文件回填 commit `6e8d8fef`；沒有詢問、沒有使用 `--no-verify`；pre-commit 與 pre-push 輕量守門都一次通過。**雲端 CI 全綠**（run 42030547563，見 §5）。
- **本回填 commit 只改文件，刻意不 push**，隨下一輪的 push 帶出（沿用 Sprint 322～326，免得『回填→push→再回填』無限循環）。
- **收尾 Docker 清理**：刪 4 個匿名卷（0 失敗），卷 1.112GB → 928MB，懸空映像回收 0B；保留 5 個具名卷（`act-toolcache`、`maven_cache`、`minio_data`、`postgres_data`、`redis_data`）與 3 個核准基底映像，建置快取 1.889GB 只回報不刪。Docker Desktop 開工時已在跑（不是我啟動的），沒有關閉。
- **仍等你決定**：`DEF-294`（購物車併發加入掉數量）、`DEF-449`（pre-push 守門驗主目錄）、`DEF-419`（運費模板的權限模型）、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取，以及 Sprint 325 留下的兩個觀察（PRD 資料字典與 `V1` 對 `po_number` 的唯一範圍不一致、`E-7006` 採購單號重複已定義卻從未被拋出）；**加上本輪新增的一個可推翻的取捨**：退款流程要不要改成『退款永遠不被結算簿記拖垮』（fail-open），見 §8 第 2 點。
- **下次說『請繼續』我可以直接做**：`DEF-453` 的補量測（用 `COMPLETED` 訂單與部分退款補探針，確認 Stripe 後台退款的實際後果）；修不修、怎麼修是設計題，先量測再決定。

## 附錄 A：`DEF-453` 探針輸出原文

探針是一次性的（暫時插進 `RefundSettlementAdjustmentFailureRealStackIntegrationTest` 的兩個方法，跑完用備份還原、SHA-256 核對，未入庫）。**探針跑在修復後的程式碼上**；webhook 路徑本輪沒有改，第一次嘗試的資料庫結果修復前後相同（§3）。做法：

- **A**：Stripe 付款的訂單被自動退款，結算單 `total_refunds` 接近數值上限讓調整單步驟失敗（交易回滾）→ 送 `charge.refunded` webhook → 資料修好、退避結束後讓排程重試。
- **B**：已付款、被結算單認領的訂單只收到 `charge.refunded` webhook（等同 Stripe 後台發起的退款）。

固件裡訂單被結算單認領是合成的（正式環境被認領的是 `COMPLETED`／`DELIVERED` 訂單）。下面每筆輸出的欄位之間為了好讀加了換行，內容沒改；`stripeRefundCalls` 是 Stripe 閘道 mock 的 `processRefund` 被呼叫的次數。

```text
PROBE> [A0 退款前] order=REFUNDING
    payment={status=SUCCESS, refunded_amount=0.00, stripe_refund_id=null}
    stateLog=[null->CREATED by=b6496f4b-8814-47a5-9c03-b19107e8400d reason=Order created from cart, CREATED->PAID by=null reason=Stripe payment webhook success, session=cs_test_73e1d833-f847-4670-838b-42214212de55, PAID->CANCELLED by=b6496f4b-8814-47a5-9c03-b19107e8400d reason=changed my mind, CANCELLED->REFUNDING by=b6496f4b-8814-47a5-9c03-b19107e8400d reason=Order was paid: refund pending]
    statement(total_refunds=999999999999.00,net=9000.00)
    adjustments=0
    paymentAudits=[STRIPE_PAYMENT_SUCCEEDED_WEBHOOK]
    stripeRefundCalls=0
PROBE> [A1 第一次嘗試失敗（交易回滾）] order=REFUNDING
    payment={status=SUCCESS, refunded_amount=0.00, stripe_refund_id=null}
    stateLog=[null->CREATED by=b6496f4b-8814-47a5-9c03-b19107e8400d reason=Order created from cart, CREATED->PAID by=null reason=Stripe payment webhook success, session=cs_test_73e1d833-f847-4670-838b-42214212de55, PAID->CANCELLED by=b6496f4b-8814-47a5-9c03-b19107e8400d reason=changed my mind, CANCELLED->REFUNDING by=b6496f4b-8814-47a5-9c03-b19107e8400d reason=Order was paid: refund pending]
    statement(total_refunds=999999999999.00,net=9000.00)
    adjustments=0
    paymentAudits=[STRIPE_PAYMENT_SUCCEEDED_WEBHOOK]
    stripeRefundCalls=1
PROBE> [A2 charge.refunded webhook 到達] order=REFUNDED
    payment={status=REFUNDED, refunded_amount=0.00, stripe_refund_id=re_probe_1}
    stateLog=[null->CREATED by=b6496f4b-8814-47a5-9c03-b19107e8400d reason=Order created from cart, CREATED->PAID by=null reason=Stripe payment webhook success, session=cs_test_73e1d833-f847-4670-838b-42214212de55, PAID->CANCELLED by=b6496f4b-8814-47a5-9c03-b19107e8400d reason=changed my mind, CANCELLED->REFUNDING by=b6496f4b-8814-47a5-9c03-b19107e8400d reason=Order was paid: refund pending, REFUNDING->REFUNDED by=null reason=Stripe refund webhook (charge.refunded), refundId=re_probe_1]
    statement(total_refunds=999999999999.00,net=9000.00)
    adjustments=0
    paymentAudits=[STRIPE_PAYMENT_SUCCEEDED_WEBHOOK, STRIPE_PAYMENT_REFUNDED_WEBHOOK]
    stripeRefundCalls=1
PROBE> [A3 資料修好、退避結束後排程重試] order=REFUNDED
    payment={status=REFUNDED, refunded_amount=0.00, stripe_refund_id=re_probe_1}
    stateLog=[null->CREATED by=b6496f4b-8814-47a5-9c03-b19107e8400d reason=Order created from cart, CREATED->PAID by=null reason=Stripe payment webhook success, session=cs_test_73e1d833-f847-4670-838b-42214212de55, PAID->CANCELLED by=b6496f4b-8814-47a5-9c03-b19107e8400d reason=changed my mind, CANCELLED->REFUNDING by=b6496f4b-8814-47a5-9c03-b19107e8400d reason=Order was paid: refund pending, REFUNDING->REFUNDED by=null reason=Stripe refund webhook (charge.refunded), refundId=re_probe_1]
    statement(total_refunds=0.00,net=9000.00)
    adjustments=0
    paymentAudits=[STRIPE_PAYMENT_SUCCEEDED_WEBHOOK, STRIPE_PAYMENT_REFUNDED_WEBHOOK]
    stripeRefundCalls=1
PROBE> [B0 退款前（已付款、已被結算單認領）] order=PAID
    payment={status=SUCCESS, refunded_amount=0.00, stripe_refund_id=null}
    stateLog=[null->CREATED by=3aabec20-40b4-47d2-9cea-20ab8daec34e reason=Order created from cart, CREATED->PAID by=null reason=Stripe payment webhook success, session=cs_test_e1546fcd-e846-4aa3-ba4c-62529f914229]
    statement(total_refunds=0.00,net=9000.00)
    adjustments=0
    paymentAudits=[STRIPE_PAYMENT_SUCCEEDED_WEBHOOK]
    stripeRefundCalls=0
PROBE> [B1 只收到 charge.refunded webhook（例如 Stripe 後台發起的退款）] order=REFUNDED
    payment={status=REFUNDED, refunded_amount=0.00, stripe_refund_id=re_probe_1}
    stateLog=[null->CREATED by=3aabec20-40b4-47d2-9cea-20ab8daec34e reason=Order created from cart, CREATED->PAID by=null reason=Stripe payment webhook success, session=cs_test_e1546fcd-e846-4aa3-ba4c-62529f914229, PAID->REFUNDED by=null reason=Stripe refund webhook (charge.refunded), refundId=re_probe_1]
    statement(total_refunds=0.00,net=9000.00)
    adjustments=0
    paymentAudits=[STRIPE_PAYMENT_SUCCEEDED_WEBHOOK, STRIPE_PAYMENT_REFUNDED_WEBHOOK]
    stripeRefundCalls=0
```
