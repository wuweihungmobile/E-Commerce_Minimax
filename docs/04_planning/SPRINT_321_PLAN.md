# Sprint 321 Plan — DEF-444（批准／駁回的整列寫回蓋掉退款）與 DEF-443（核准後撥款觸發的回滾）

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 320](SPRINT_320_PLAN.md) 的總結，加一句『請繼續完成任務！』，**沒有任何 `==>` 回覆，也沒有互動選項的答案**。我的解讀（推論，不是使用者說的）：只做總結自己列為『下次說「請繼續」我可以直接做的』那兩項——先 `DEF-444`（建議方向：搶占成功後 `refresh`），再 `DEF-443`；不碰 `DEF-419`、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取，也不做兩個『可選量測』。

長期授權（2026-10-29，使用者明文：『做完一輪後，直接 Commit + Push to main，請記住』）：本輪 commit 後直接 push，不再詢問。Sprint 320 的純文件回填 commit `21a6f577`（領先遠端 1 個）是本輪 commit 的祖先，會隨這次 push 一起帶出。

### 1.2 成功標準（開工時定義）

1. **紅燈先行、而且是真實競態**：在**未修改的生產碼**上，用真實 PostgreSQL 量測『批准／駁回的整列寫回蓋掉同時入帳的退款』；有一個不含併發的循序對照案例必須是綠的。
2. **預測先寫下來**：動手前把『我預期會看到什麼』登記進 §3，跑完逐一比對，不符的如實揭露。
3. **修法有牙齒**：突變驗證預先登記每組『恰好該紅的案例』，結果逐一比對。
4. `DEF-443` 先實測『其他觸發點』（追蹤表要求），確認機制成立再選修法。
5. 全量後端 `mvn -o clean verify`（全新資料庫）通過、Checkstyle 0；push 後雲端 CI 全綠並核對整合測試數。
6. 追蹤表如實更新；過程中發現的相鄰問題只登記、不順手修。

### 1.3 動手前後用工具核對的事實

| 說法（出處） | 核對結果 |
|---|---|
| Sprint 320 總結／追蹤表 `DEF-444`：『建議方向：搶占成功後 `entityManager.refresh(statement)`』 | **可行，而且有前例**。`PaymentStateService.confirmStripeBookingCheckout`（`PaymentStateService.java:823`）就是在條件式 `UPDATE` 之後 `entityManager.refresh(payment)`，`EntityManager` 以 `@PersistenceContext` 欄位注入（不在建構子），單元測試用 `ReflectionTestUtils.setField(service, "entityManager", entityManager)`（`PaymentStateServiceBookingTest.java:92`）。本輪照這個慣例做（Rule 11）。 |
| 追蹤表 `DEF-444` 列的另一個選項『`@DynamicUpdate`』 | 程式碼庫也有前例：`TenantFeatureToggle`（DEF-116／143／148，類別 Javadoc 就是在說同一個機制：整列寫回用交易一開始讀到的舊快照蓋掉併發的修改）。**本輪沒選**：①它只讓整列寫回不再蓋金額，transfer 金額仍用過期的記憶體值（追蹤表自己寫的缺點）；②是實體層級的變更，會影響 `SettlementStatement` 的所有寫入者（產生器、逆轉、撥款…），需要各路徑自己的量測與突變。列為 `DEF-445` 的候選修法（見 §9）。 |
| 『撥款觸發移到提交之後』的作法 | 程式碼庫已有『提交之後』的慣例：`OrderService`、`AuthService`、`BookingService`、`PaymentStateService`、`PaymentService` 都以 `TransactionSynchronizationManager.isSynchronizationActive()` 判斷後註冊 `afterCommit`，沒有交易時立即執行；`BuyerNotificationService` 的 Javadoc 明說『在 `afterCommit` 回呼裡呼叫時，原交易雖已提交、資源卻仍綁在執行緒上，直接加入它的話，寫入永遠不會被提交』，所以自己以 `REQUIRES_NEW` 開新交易。本輪照做：`TransferService.createTransferAfterApproval` 標 `REQUIRES_NEW`。 |
| 追蹤表 `DEF-443` 列：『②改成 `REQUIRES_NEW` 不可行（推論：新交易讀不到尚未提交的 `APPROVED`）』 | 在**核准的交易裡**用 `REQUIRES_NEW` 確實不可行（推論一致：新交易看不到尚未提交的 `APPROVED`，而且核准的交易持有那一列的鎖，新交易若要寫就會等它）；**提交之後**才用 `REQUIRES_NEW` 則沒有這個問題。突變 T1（把觸發放回核准交易裡）專門驗證。 |
| 『`open-in-view` 讓同一個請求的多個交易共用 persistence context』 | `src/main/resources`、`src/test/resources` grep `open-in-view` 無任何設定 → Spring Boot 預設開啟；`PaymentStateService` 的 Javadoc（第 804～806 行）也明說這件事。**推論**（沒有另外量測）：`REQUIRES_NEW` 會暫停並解除綁定目前的 `EntityManager`，撥款的新交易拿到全新的 persistence context，讀到的是核准已提交的資料。 |
| 追蹤表『重試端點只接受已有 `FAILED`／`SKIPPED` 的 transfer 記錄』 | 屬實（`TransferService.retryFailedTransfer`：`existing == null` 直接 `E-5014`）；**紅燈實測**：核准失敗之後的重試回 `409 E-5014`（§3.2）。沒有任何排程會處理停在 `APPROVED` 的結算單：全庫 `@Scheduled` 在結算套件只有 `SettlementGenerator` 的每週產生。 |
| 『`StripePaymentGateway.createTransfer` 把 `StripeException` 包成 `BusinessException`』 | 屬實（`StripePaymentGateway.java:367～371`，`E_6010`）。所以真實的 Stripe 拒絕走 `TransferService` 內建的 `FAILED` 路徑；穿出的是非 `BusinessException` 的例外（程式錯誤、連線層／基礎設施例外——**這一句是推論**，沒有對真實 Stripe 驗證）。 |
| API 文件 §4.5：『撥款失敗只記錄 log，**回應仍是 `200` 且結算單狀態仍是 `APPROVED`**』 | **與現況不符**：探針實測撥款成功時核准回應的 `data.status` 是 **`PAID`**（回應是在撥款推進同一個受管實體之後才組的）。撥款移到提交之後，回應變成 `APPROVED`——文件寫的行為反而成真，但這是**可見的行為變更**（§8 第 3 點）。 |

## 2. 做法

### 2.1 DEF-444：搶占成功後重新載入

`SettlementReviewer.approveStatement`／`rejectStatement` 在 `updateStatusIfCurrent` 搶占成功之後、任何 setter 之前，呼叫 `entityManager.refresh(statement)`。搶占成功＝這一列已被本交易的 `UPDATE` 鎖住（其他交易對它的 `UPDATE` 要等本交易結束），此時重新載入拿到的是鎖住當下最新的已提交值；之後的 `setStatus`＋`save`（整列寫回）帶的就是最新金額，`TransferService` 讀到的結算單（同一個受管實體）也是最新淨額，回應也是。必須在 setter 之前：`refresh` 會丟棄記憶體中尚未寫出的修改（突變 D4／D5 驗證）。

### 2.2 DEF-443：撥款觸發移到核准交易提交之後

- `SettlementReviewer.approveStatement`：稽核寫入後呼叫 `triggerTransferAfterCommit`——有作用中的交易同步時註冊 `afterCommit` 回呼，沒有（未經 Spring 交易的單元測試）時立即執行；回呼裡呼叫 `transferService.createTransferAfterApproval(statementId)`，任何 `RuntimeException` 只記錄 `ERROR`、不外洩（`afterCommit` 的例外會傳給提交的呼叫者，讓已提交的核准變成 HTTP 500——突變 T3 驗證）。交易回滾（搶占輸了、稽核寫入失敗…）時沒有 `afterCommit`，不會撥款（突變 T8 驗證）。
- `TransferService.createTransferAfterApproval`（新）：`@Transactional(propagation = REQUIRES_NEW)` 包著 `createTransferForStatement`。撥款失敗只回滾撥款自己的寫入，含 transfer 佔位——不會留下卡在 `PENDING` 的記錄擋住之後所有的重試。
- `TransferService.retryFailedTransfer`：`existing == null`（核准成立但撥款觸發本身失敗）時直接呼叫 `createTransferForStatement`，不再回 `409`。非 `APPROVED` 的結算單由 `createTransferForStatement` 開頭既有的守門擋下（同為 `E-5014`）。**這一段是必要的配套，不是順手加的功能**：沒有它，撥款移到提交之後會讓非預期失敗的結果變成『核准成立、沒有 transfer、沒有任何 API 可補救』，比修復前（整個回滾，管理員重新核准）更糟。

**沒選的方案**：
- **`noRollbackFor`**（讓例外穿出時不標 rollback-only）：核准會保留，但撥款失敗前已寫入的 transfer 佔位（`PENDING`）也一起提交，之後 `createTransferForStatement` 看到 `existing != null` 就直接回傳、重試端點又不接受 `PENDING`——結算單卡死。
- **`PROPAGATION_NESTED`（savepoint）**：能讓外層交易繼續，但 Hibernate 的 persistence context 在 savepoint 回滾後仍保留已回滾的實體狀態，是『吞掉例外再繼續用同一個 session』的脆弱做法。
- **非交易式的協調者／controller 在 `approveStatement` 返回後呼叫撥款**：能保留回應的 `PAID`，但要新增一層類別或讓 controller 管交易邊界；程式碼庫既有的『提交之後』慣例是 `afterCommit`（見 §1.3）。代價是回應變成 `APPROVED`，由 §8 第 3 點揭露。
- **把撥款失敗記成 `FAILED` transfer**（讓既有重試端點不必改）：要新增失敗狀態轉換與失敗原因的語意，範圍比放寬重試端點大。

### 2.3 測試

- 改：`SettlementReviewRaceRealStackIntegrationTest`（6→10 案例）——循序退款對照、批准 vs 退款、駁回 vs 退款、**批准 vs 退款時送給 Stripe 的金額**（把 Sprint 320 的『賣家多拿』推論變成在閘道邊界量到的事實）；類別加 `@MockBean PaymentGatewayFactory`（只有最後那個案例會走到閘道）。對手是真實的退款服務（`SettlementAdjustmentService.handleOrderRefund`），不是手寫 SQL。
- 新：`SettlementApprovalPayoutTriggerRealStackIntegrationTest`（4 案例）——非預期閘道例外不回滾核准、非預期失敗後重試補撥款、已知失敗記為 `FAILED`（對照）、閘道成功（對照，含送給 Stripe 的金額與冪等鍵）。
- 改：`SettlementReviewerTest`（14→19）——`refresh` 的位置（用『模擬真實 `refresh` 會用資料庫列取代記憶體狀態』的假 `refresh`，才分得出放在 setter 之前或之後）、提交前不可撥款、回滾不可撥款、撥款失敗不外洩；`TransferServiceTest`（15→19）——`REQUIRES_NEW` 結構測試、委派、補撥款、非 `APPROVED` 不可補撥款。

## 3. 修改前的紅燈（未修改的程式碼、真實 PostgreSQL）

### 3.1 DEF-444 的預測（跑之前登記，來源是讀碼＋Sprint 320 的一次性探針）

測試：[SettlementReviewRaceRealStackIntegrationTest](../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementReviewRaceRealStackIntegrationTest.java) 新增的 3 個案例。對手是一筆**真實的退款**（`SettlementAdjustmentService.handleOrderRefund`，在 `TransactionTemplate` 裡執行並持鎖不提交）；批准／駁回請求先讀到 `total_refunds = 0／淨額 = 900`、卡在搶占的 `UPDATE`，退款提交後放行。種子結算單 GMV 1000、抽成 100、淨額 900，退款 100。

| 案例 | 預測（未修改） | 預測的違反項目 |
|---|---|---|
| 循序對照：退款先提交、批准後到 | 綠 | — |
| 批准 vs 同時入帳的退款 | 回 `200`、`APPROVED`、審核人正確；但整列寫回用舊值蓋掉退款：資料庫 `total_refunds = 0.00`、淨額 `900.00`、transfer 金額 `900.00`；回應的 `totalRefunds`／`netSettlementAmount` 也是舊值（`0.00`／`900.00`，因為回應是由那個過期的受管實體組的） | 5 項：`total_refunds`、淨額、transfer 金額、回應 `totalRefunds`、回應 `netSettlementAmount` |
| 駁回 vs 同時入帳的退款 | 回 `200`、`REJECTED`、審核人／駁回原因正確、訂單已釋放；但 `total_refunds = 0.00`、淨額 `900.00` | 2 項：`total_refunds`、淨額 |

**實測結果（DEF-444，未修改的生產碼）**：9 案例跑、2 紅 7 綠；循序對照與原有 6 案例綠；批准案例 **5 項違反**（`total_refunds` 預期 `100.00` 實際 `0.00`；淨額預期 `800.00` 實際 `900.00`；transfer 金額預期 `["800.00"]` 實際 `["900.00"]`；回應 `totalRefunds` `0.00`；回應 `netSettlementAmount` `900.00`）、駁回案例 **2 項違反**（`total_refunds`、淨額，其餘不變量——`REJECTED`、審核人、駁回原因、訂單已釋放——都通過）。**逐格符合預測。**

### 3.2 DEF-443 的預測（跑之前登記，來源是讀碼＋Sprint 320 的日誌）

測試：[SettlementApprovalPayoutTriggerRealStackIntegrationTest](../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementApprovalPayoutTriggerRealStackIntegrationTest.java)（新）。`@MockBean PaymentGatewayFactory` 注入閘道行為；店鋪用 JDBC 設成 Connect 帳戶就緒（`connect_onboarding_status = COMPLETE`、charges／payouts 開啟）且撥款開關 `STRIPE_TRANSFER_ENABLED` 開啟，核准後的撥款觸發才會真的走到閘道。HTTP／JWT／權限鏈與資料庫都是真的。

| 案例 | 預測（未修改） | 預測的違反項目 |
|---|---|---|
| 閘道丟非預期例外（`IllegalStateException`）：核准不回滾 | 例外穿出 `createTransferForStatement` → 核准交易 rollback-only → 提交時 `UnexpectedRollbackException` → `500 E-9900`；結算單回到 `PENDING_REVIEW`、審核人 `null`、核准稽核 0；佔位的 transfer 隨交易回滾而消失（`transfers` 空，這一項通過） | 4 項：回應碼、結算單狀態、審核人、核准稽核筆數 |
| 非預期失敗之後可重試 | 核准 `500`；重試端點因結算單不是 `APPROVED`（且沒有 transfer 記錄）回 `409 E-5014`；最終 `PENDING_REVIEW`、沒有 transfer | 5 項：核准碼、重試碼、結算單狀態、付款時間、transfer 內容 |
| 對照：閘道丟 `BusinessException`（已知失敗） | 綠——`TransferService` 內建的 `catch (BusinessException)` 記為 `FAILED`，沒有例外穿出：核准 `200`、結算單／transfer 都是 `FAILED` | — |
| 對照：閘道成功 | 綠——結算單 `PAID`、transfer `COMPLETED`；送給 Stripe 的是 `90000` 分、冪等鍵是結算單 id。**探針**（非斷言）：核准回應的 `data.status` 預測為 `PAID`（回應是在撥款推進同一個受管實體之後才組的） | — |

**實測結果（DEF-443，`DEF-444` 已修、`DEF-443` 未修的生產碼）**：4 案例跑、3 紅 1 綠。

| 案例 | 實測 | 與預測 |
|---|---|---|
| 閘道丟非預期例外：核准不回滾 | **4 項違反**：核准回 `500 E-9900`；結算單 `PENDING_REVIEW`；審核人 `null`；核准稽核 0。`transfers = []`（佔位隨交易回滾，這項通過） | 逐項相符 |
| 非預期失敗之後可重試 | **5 項違反**：核准 `500`；**重試回 `409 E-5014`**（結算單不是 `APPROVED` 且沒有 transfer）；最終 `PENDING_REVIEW`、付款時間 `false`、`transfers = []` | 逐項相符 |
| 對照：閘道丟 `BusinessException` | **紅，但原因是我測試裡的字串斷言寫錯**：行為完全符合設計（核准 `200`、結算單／transfer 都是 `FAILED`、稽核 1、金額 900.00），`failureReason` 是 `Stripe Transfer 分潤轉帳錯誤: insufficient platform balance`——`BusinessException` 把錯誤碼的預設說明加在閘道說明前面，我預期的是閘道的原文。 | **預測落空**（我預測綠）。生產行為沒有偏差；斷言改為『包含閘道的說明』後重跑 |
| 對照：閘道成功 | 綠；送給 Stripe 的是 `createTransfer("STRIPE", acct, 90000, "TWD", <結算單 id>)`。**探針：核准回應的 `data.status` = `PAID`** | 相符 |

兩個紅燈案例（5＋4＝9 項違反）都紅在預測的位置。**`DEF-443` 的機制在第二個獨立的觸發點成立**：Sprint 320 的觸發點是 `TransferService.claimTransferSlot` 丟的 `BusinessException(E-5014)`（`createTransferForStatement` 內、Stripe 呼叫之前）；這次是 `PaymentGatewayFactory.createTransfer` 丟的 `IllegalStateException`（非 `BusinessException`，不會被 `createTransferForStatement` 內建的 `catch (BusinessException)` 接住）。兩者都一路穿出 `TransferService` 的交易代理，核准連同稽核被整個回滾。

另外證實一件修法**必須一併處理**的事：重試端點只接受『已有 FAILED／SKIPPED 的 transfer 記錄』（`existing == null` 直接 `409`）。若把撥款觸發移到核准提交之後，非預期失敗會讓結算單停在 `APPROVED`、沒有任何 transfer 記錄，現有端點補救不了——這會讓失敗模式比現在（整個回滾，管理員重新核准）更糟。

### 3.3 相鄰問題的一次性探針（預測，跑之前登記；探針不入庫，只為把『理論上同型』量成事實再登記）

`DEF-444` 的追蹤表寫『`submitForReview` 等其他整列寫回路徑理論上同型，未量測』；做修法時讀 `SettlementAdjustmentService` 另外看到退款的直接扣款沒有狀態條件（`applyRefundDeduction` 的 `WHERE id = :id`）。兩者都只是讀碼推論，各量一次：

| 探針 | 設定 | 預測（未修改的 `submitForReview`／`applyRefundDeduction`） |
|---|---|---|
| PROBE-1 `submitForReview` vs 同時入帳的退款 | 店主提交 `PENDING` 的結算單，先讀到 `total_refunds 0／淨額 900`；退款先提交；提交的整列寫回 | 提交回 `200`；**`total_refunds` 被蓋回 `0.00`、淨額 `900.00`**（與批准／駁回同型，且提交沒有搶占、沒有任何保護） |
| PROBE-2 批准先拿到鎖、退款後到 | 批准請求先卡在搶占，退款請求（讀到 `PENDING_REVIEW`）再卡在它的直接扣款 `UPDATE`；放行後批准先提交、退款的 `UPDATE`（無狀態條件）才生效 | 批准 `200`、結算單 `APPROVED`；**退款直接扣在已批准的結算單上**（`total_refunds = 100.00`），而且**沒有產生調整單**（`adjustment_statements` 0 筆）——設計上 `APPROVED` 以後的退款應走調整單、下一期折入 |

**實測結果**（探針暫時加進競態測試類別、跑完用 `python3 -I` 精確移除，6 個相關檔案的 SHA-256 與探針前逐一相符）：

| 探針 | 實測 | 與預測 |
|---|---|---|
| PROBE-1 `submitForReview` vs 退款 | 提交 `200`；結算單 `PENDING_REVIEW`、**`total_refunds = 0.00`、淨額 `900.00`**（退款被蓋掉，2 項違反） | 相符 |
| PROBE-2 批准先、退款後 | 批准 `200`、`APPROVED`、審核人正確；**`total_refunds = 100.00`、淨額 `800.00`（退款直接扣在已批准的結算單上）、`adjustment_statements` 0 筆**（2 項違反）；這次 transfer 金額記的是 **`900.00`**（`SKIPPED_ONBOARDING_INCOMPLETE`，測試租戶沒有走 Stripe） | 相符。**註**：『退款直接扣在已批准的結算單上、且沒有調整單』是確定的；transfer 金額是 `900.00` 還是 `800.00` 取決於提交後的撥款交易與退款的 `UPDATE` 誰先讀／寫（不確定的交錯，這一次是 900），所以『賣家多拿 100』只是其中一種交錯的後果 |

兩個相鄰缺陷都**只登記、不修**（見 §9）。

## 4. 修改後與突變驗證

### 4.1 修改後（單元／整合）

見 §5 驗證總表。

### 4.2 突變驗證（預先登記：每組先寫下『恰好該紅的案例』，跑完逐一比對）

驅動沿用 Sprint 320 的 `bmut.py`（從前一個 session 的暫存目錄複製）；受測集合＝單元 `SettlementReviewerTest`（19）＋`TransferServiceTest`（19）＋整合 `SettlementReviewRaceRealStackIntegrationTest`（10）＋`SettlementApprovalPayoutTriggerRealStackIntegrationTest`（4）＋`SettlementBookingIntegrationTest`（7）＋`SettlementExactlyOnceIntegrationTest`（6），共 65 案例。基準 Z0（全新資料庫）預期 65 案例 0 失敗。判決標準：失敗集合與登記的 `must` **恰好**吻合才算 `KILLED(exactly must)`。以下 `must` 都是**跑之前**登記的（來源：逐個案例讀斷言）：

| 組 | 突變 | 登記的恰好該紅 | 結果 |
|---|---|---|---|
| D1 | 批准：拿掉搶占後的 `refresh` | 單元 1（批准的 refresh 案例）＋整合 2（批准 vs 退款：資料庫金額、送給 Stripe 的金額） | `KILLED(exactly must)`，3 案例 |
| D2 | 駁回：拿掉 `refresh` | 單元 1（駁回的 refresh 案例）＋整合 1（駁回 vs 退款） | `KILLED(exactly must)`，2 案例 |
| D3 | 批准：`refresh` 移到搶占**之前** | 同 D1：單元 1（`InOrder` 順序）＋整合 2（讀到的仍是退款提交前的值） | `KILLED(exactly must)`，3 案例 |
| D4 | 批准：`refresh` 移到 setter **之後**（會丟掉記憶體中的審核人／批准時間） | 單元 1＋整合 2（批准 vs 批准、批准 vs 退款：審核人）＋payout 類別 3（審核人：非預期失敗、已知失敗、成功） | `KILLED(exactly must)`，6 案例 |
| D5 | 駁回：`refresh` 移到 setter 之後 | 單元 1＋整合 3（駁回先批准後、駁回 vs 駁回、駁回 vs 退款：審核人／駁回原因） | `KILLED(exactly must)`，4 案例 |
| T1 | 撥款觸發放回核准交易裡（立刻呼叫，try-catch 保留） | 單元 2（提交前不可撥款、回滾不可撥款）＋整合 8（所有需要 transfer 存在的案例） | `KILLED(exactly must)`，10 案例 |
| T2 | `createTransferAfterApproval` 的 `REQUIRES_NEW` 改成預設的 `REQUIRED` | 單元 1（註解結構測試）＋整合 8（同 T1 的集合；**整合部分登記時標為推論**） | `KILLED(exactly must)`，9 案例 |
| T3 | 拿掉回呼裡的 try-catch（例外從 `afterCommit` 洩漏） | 單元 1（不外洩）＋整合 2（非預期失敗：核准回 500；補撥款案例的核准步驟） | `KILLED(exactly must)`，3 案例 |
| T4 | 重試端點：沒有 transfer 記錄時改回拒絕 | 單元 1（補撥款案例）＋整合 1（補撥款案例的重試步驟） | `KILLED(exactly must)`，2 案例 |
| T7 | `createTransferAfterApproval` 不委派（回 `null`） | 單元 1（委派案例）＋整合 8（同 T1 的集合） | `KILLED(exactly must)`，9 案例 |
| T8 | `afterCommit` 改成 `afterCompletion`（回滾也會觸發） | 單元 3（提交後才觸發、回滾不觸發、失敗不外洩） | `KILLED(exactly must)`，3 案例 |

『整合 8』＝競態類別 6（循序對照、批准 vs 批准、批准先駁回後、循序退款對照、批准 vs 退款、送給 Stripe 的金額）＋payout 類別 2（已知失敗、成功）。

**結果**：基準 Z0（全新資料庫）65 案例 0 失敗；11 組突變的失敗集合**全部與登記的 `must` 恰好吻合**（驅動的 `restored=True` 共 12 次、`MSG-MISMATCH` 0、`INVALID` 0；失敗數與登記數逐組相同：3／2／3／6／4／10／9／3／2／9／3）。被突變的 `SettlementReviewer.java`、`TransferService.java` 與 4 個測試檔的 SHA-256 在驅動結束後與事先記錄的逐一相同，沒有突變殘留字串。細節：

1. **T1 與 T2 登記時標為『推論』的機制，日誌證實了**：T1（撥款放回核准交易、仍用 `REQUIRES_NEW`）的日誌是 `immediate trigger failed: 無效的對帳單狀態轉換: Only APPROVED statements can be transferred`——新交易看不到尚未提交的 `APPROVED`，追蹤表當初『②`REQUIRES_NEW` 不可行』的推論成立；T2（`afterCommit` 裡用 `REQUIRED`）的日誌是 `InvalidDataAccessApiUsageException: no transaction is in progress`（`jakarta.persistence.TransactionRequiredException`）——加入已完成的交易時 Hibernate 已無進行中的交易，所以是『直接失敗』，不是『寫入遺失』（我登記的是『寫入失敗或遺失』，對了一半）。
2. **D3（`refresh` 放在搶占之前）連整合測試都殺得掉**：搶占之前的 `refresh` 讀到的仍是退款提交前的值，所以不只 `InOrder` 的單元案例，批准 vs 退款的兩個整合案例也紅。D4／D5（`refresh` 放在 setter 之後）則靠審核人／駁回原因的斷言殺掉——`refresh` 會丟棄記憶體中尚未寫出的修改，是真實會發生的錯位。
3. **T8（`afterCommit` 換成 `afterCompletion`）只被單元案例殺掉**：真實棧的案例都是提交成功的情境，看不到『回滾時不可撥款』；這一道守門只有單元層（用手動啟用的交易同步與 `triggerAfterCompletion(STATUS_ROLLED_BACK)`）。見 §7 第 5 點。
4. **登記之前就移除了一個等價突變**：我原本在 `retryFailedTransfer` 補救分支多寫了一個『結算單不是 `APPROVED` 就擋』的前置檢查，寫突變規格時發現 `createTransferForStatement` 開頭本來就有同一個守門（同為 `E-5014`），拿掉我的檢查外部行為完全不變（等價突變，沒有任何測試能、也不該殺它）。依 Rule 2／3 把這個冗餘檢查和多出來的錯誤訊息常數刪掉（並確認 Z0 仍是 65／0）；『非 `APPROVED` 不可補撥款』這個意圖仍由 `retryFailedTransfer_noTransferAndNotApproved_throwsWithoutPayout` 守住。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**18 分 55 秒**；正式 Maven 編譯 387 個主程式檔、322 個測試檔；單元 **2452／0 失敗／0 錯誤／0 跳過**（Sprint 320 的 2443＋`SettlementReviewerTest` +5、`TransferServiceTest` +4）；整合 **997／0／0／0**（989＋競態類別 +4、新 payout 類別 +4）；Checkstyle main／test 各 0 違規；日誌 `<<< FAILURE`／`<<< ERROR`／`Unresolved compilation`／`ClassFormatError`／`NoClassDefFoundError` 各 0 個。新增／變動的類別在 verify 內確實執行：`SettlementReviewerTest` 19、`TransferServiceTest` 19、`SettlementReviewRaceRealStackIntegrationTest` 10、`SettlementApprovalPayoutTriggerRealStackIntegrationTest` 4、`SettlementBookingIntegrationTest` 7、`SettlementExactlyOnceIntegrationTest` 6、`UniqueConstraintRaceRealStackIntegrationTest` 4。 |
| 紅燈先行（§3） | `DEF-444`：未修改的程式碼上批准 5 項、駁回 2 項違反，預測逐格相符，循序對照綠。`DEF-443`：兩個紅燈案例（4＋5 項違反）逐項相符；一個對照案例的預測落空（我測試的字串斷言寫錯，§3.2）。兩個相鄰缺陷的探針預測逐格相符（§3.3）。 |
| 突變驗證（§4.2） | 基準 Z0＋11 組，**全部 `KILLED(exactly must)`**，預先登記逐一吻合；被突變的檔案 SHA-256 在驅動結束後與事先記錄的一致，無殘留字串。 |
| 穩定性 | 兩個真實棧類別（競態 10＋payout 4）在全量 verify 之後連跑 3 次（每次重新啟動 Spring context），每次 14 案例 0 失敗。 |
| IDE Problems | 我改動的 6 個檔案診斷 **0**；`getDiagnostics` 列出的所有條目也都是空陣列。 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 在**所有文件定稿後**執行，結果見 §10。 |
| 沒有執行的項目 | Playwright 完整 E2E 與 `make validate-schema-doc`：本輪沒有前端、Flyway 遷移或 schema 文件變更（只改後端程式碼、測試與文件），見 §7 第 9 點。 |

## 6. 過程中的失誤與修正

1. **預測落空一項**（§3.2）：我預測『已知閘道失敗』對照案例會綠，它紅了——原因是我測試裡寫死了閘道的原文當 `failureReason`，而 `BusinessException` 會在前面加錯誤碼的預設說明。生產行為沒有偏差，改為『包含閘道的說明』。
2. **多寫了一個冗餘檢查，而且改完沒有先單獨編譯＋跑單元**：補救分支裡我加了『結算單不是 `APPROVED` 就擋』，其實 `createTransferForStatement` 開頭本來就有同一個守門；我是在寫突變規格時（預見它會是等價突變）才發現並刪除。刪除之後我直接進入下一步（寫規格、啟動驅動），沒有照 CLAUDE.md『開發-編譯-測試循環』先單獨編譯＋跑單元——第一次驗證它的是驅動的基準 Z0（65／0，結果是綠的），但流程上應該先跑。
3. **zsh 老坑又犯兩次**：開場第一個 `grep --include=*.java` 沒加引號（`no matches found`，整條沒跑，不是『0 筆』）；`echo ======` 被當成 `=cmd` 展開而報 `not found`（兩者都已在記憶裡）。之後一律加引號、分隔線不用 `=`。
4. **Python 3.9 的 `heredoc` 在 stdin 放長的全形行 → `Non-UTF-8 code`**（附加計畫書章節時）：改用 Edit 工具與檔案式腳本。
5. **工作目錄漂移**：數次 `cd` 之後工作目錄留在子目錄，之後改用絕對路徑。
6. **一次性探針暫時插進測試類別再移除**：移除後用事先記錄的 SHA-256 逐檔核對與探針前完全一致，`grep` 確認沒有殘留。
7. **突變驅動被殺就不會還原**：開跑前把被突變的兩個主檔備份到暫存目錄（含未 commit 的修改，不能靠 `git checkout`）。這次驅動正常跑完、全部還原，沒有用到備份。
8. **校正一次過度結論**：PROBE-2 的 transfer 金額 `900.00` 只是不確定交錯下的一次觀察；我一開始差點把『賣家多拿 100』寫成確定的後果，寫追蹤表與計畫書時改為『確定的是狀態守門被違反（直接扣在已批准的結算單、沒有調整單）』。

## 7. 限制（誠實揭露）

1. **閘道是 `@MockBean`**：撥款的金額、冪等鍵、呼叫次數是在閘道介面邊界驗證，沒有對真實 Stripe 驗證（承 Sprint 80 起的既有限制）。測試租戶預設走不到閘道（開關關閉／Connect 未就緒），只有刻意設定的案例會。
2. **非預期例外是模擬的**（`IllegalStateException`）。真實環境裡哪些例外會穿出 `TransferService` 是推論：`StripePaymentGateway` 已把 `StripeException` 包成 `BusinessException`，所以穿出的多半是程式錯誤或連線層／基礎設施例外。
3. **『回滾時不可撥款』只有單元層守門**：突變 T8 只被單元案例殺掉；真實棧的案例都是提交成功的情境。
4. **撥款在提交之後仍在同一個請求執行緒、回應之前完成**（同步的 `afterCommit`）：慢的 Stripe 呼叫仍會拉長那次 HTTP 回應，只是不再持有結算單那一列的鎖到提交（修復前 Stripe 呼叫在核准的交易裡，鎖持有到提交）——這個好處是依構造的推論，沒有量測鎖持有時間。
5. **`afterCommit` 內的 `REQUIRES_NEW` 會在原連線仍被佔用時再取一條連線**，每次核准短暫佔兩條連線（與 `BuyerNotificationService` 既有的模式相同）。連線池耗盡時的行為沒有量測（核准是低頻的管理操作）。
6. **兩個重試同時送出的情境沒有新案例**：佔位競態由 Sprint 319 的 `UniqueConstraintRaceRealStackIntegrationTest` 覆蓋 `createTransferForStatement` 本身；補撥款分支直接呼叫同一個方法，是推論地受益。
7. **每個競態只測一個交錯**（退款先提交、批准／駁回後寫；沒有互換先後）。
8. **`DEF-445`／`DEF-446` 的探針是一次性的、未入庫**；`DEF-446` 的 transfer 金額是不確定交錯下的一次觀察。其他整列寫回路徑（`TransferService` 推進 `PAID`／`FAILED`、`retryFailedTransfer`、逆轉、`handleTransferReversedWebhook`）仍未量測。
9. **沒有執行 Playwright 完整 E2E 與 `make validate-schema-doc`**：沒有前端、Flyway 遷移或 schema 變更。核准回應 `data.status` 的變更沒有已知的前端呼叫者受影響：本輪重新 grep `frontend/src`，批准／駁回結算單端點的呼叫點是 0，`lib/api.ts` 的 `settlements` 只定義沖正的三個端點，也沒有人呼叫重試端點；外部（腳本、管理工具）呼叫者我無從得知。

## 8. 我替使用者做的決定（可一句話推翻）

1. 『請繼續完成任務！』（無 `==>`）＝依 Sprint 320 總結自己寫的『下次說「請繼續」我可以直接做 `DEF-444`，然後 `DEF-443`』，只做這兩項；不碰 `DEF-419`、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取，也不做兩個『可選量測』。
2. **`DEF-444` 用 `entityManager.refresh`**（追蹤表建議、`PaymentStateService` 有前例），不用 `@DynamicUpdate`、悲觀鎖（理由 §1.3、§2.1）。
3. **核准回應的 `data.status` 從 `PAID`（撥款成功時）變成一律 `APPROVED`**——這是撥款移到核准交易提交之後的**可見行為變更**。API 文件 §4.5 原本就寫『回應仍是 `APPROVED`』（與現況不符），現在文件寫的反而成真；前端沒有批准畫面（Sprint 320 grep 確認），沒有已知的呼叫者依賴 `PAID`。**若你要保留回應裡的撥款結果**，改法是新增一個非交易式的協調者（或讓 controller 在 `approveStatement` 返回後呼叫撥款並重讀），代價是多一層類別，說一聲我再改。
4. **重試端點放寬為也接受『已核准但沒有 transfer 記錄』的結算單**——不是順手加功能，而是 `DEF-443` 修法的必要配套（§2.2）；沒有它，非預期的撥款失敗會讓結算單卡在 `APPROVED` 且沒有任何 API 可補救。
5. **相鄰缺陷只量測、只登記、不修**：`submitForReview` 的整列寫回（`DEF-445`）與退款直接扣款沒有狀態條件（`DEF-446`）。依 Sprint 316～320 一貫的做法：量測順帶發現的相鄰問題先用一次性探針實測再登記，下輪『請繼續』直接可做。
6. **競態測試類別加 `@MockBean PaymentGatewayFactory`** 並新增『送給 Stripe 的金額』案例，把 Sprint 320 的推論（賣家多拿被退款的錢）變成在閘道邊界量到的事實。這讓該類別與 payout 類別共用同一個 Spring context（兩者的 `@MockBean` 組合相同），整個測試套件的 context 數不變。
7. 更新 `SRD_Module_Technical_Design.md` 一段（『核准→撥款的同步觸發與失敗隔離』）：它描述的正是被證實有問題的舊機制，不更新就是錯的。
8. `API_M07` 升為 v1.3；其他文件引用的舊版號（既有的版號漂移）照 Sprint 309／310 慣例不碰。

## 9. 新登記的缺陷

- **`DEF-445`**（🟢，**實測**）：`submitForReview`（`PENDING → PENDING_REVIEW`）同樣是整列寫回，而且沒有搶占、沒有任何保護——店主提交時讀到的金額若在寫回前被退款改過，退款就被舊金額蓋掉。**實測**（一次性探針，不入庫）：退款先提交、提交的整列寫回在後，提交回 `200`，結算單 `PENDING_REVIEW`，`total_refunds` 仍 `0.00`、淨額仍 `900.00`。**建議**：比照批准／駁回——用 `updateStatusIfCurrent(id, PENDING, PENDING_REVIEW)` 搶占（順便讓兩次提交的輸家得到 `409`）＋搶占成功後 `refresh`；或評估 `@DynamicUpdate`（一個註解涵蓋所有整列寫回路徑，但 transfer 金額這類由記憶體值計算的地方仍需 `refresh`，且是實體層級變更，要各路徑量測）。
- **`DEF-446`**（🟡，**實測**）：退款的直接扣款沒有狀態條件。`SettlementAdjustmentService.handleOrderRefund` 先讀結算單狀態、再對 `PENDING`／`PENDING_REVIEW` 的結算單呼叫 `applyRefundDeduction`（`UPDATE … WHERE id = :id`，**沒有 `AND status IN (…)`**）；批准的搶占若在退款『讀狀態』與退款『`UPDATE` 生效』之間拿到鎖並提交，退款就直接扣在已批准的結算單上，而不是依設計走調整單、下一期折入。**實測**（一次性探針，不入庫，確定性的是狀態守門被違反）：批准 `200`、結算單 `APPROVED`，`total_refunds = 100.00`、淨額 `800.00`、`adjustment_statements` **0 筆**；這次 transfer 金額是 `900.00`（撥款交易讀到扣款前的值）——結算單顯示扣了 100、撥款卻是 900、也沒有調整單，退款對撥款而言消失；換一種交錯（撥款交易在退款 `UPDATE` 生效後才讀）金額會是 `800.00`，所以『賣家多拿 100』只是其中一種交錯的後果。**建議**：`applyRefundDeduction` 加 `AND status IN ('PENDING','PENDING_REVIEW')`，影響 0 列時退回走 `createAdjustmentStatement`（`handleOrderRefund`／`handleBookingRefund` 兩處）；測試做法同本輪（批准請求先卡在搶占、退款請求後卡在扣款，放行後斷言調整單 1 筆、`total_refunds` 不變）。嚴重度標🟡而不是🟢：它牽涉賣家實際收到的金額，而且批准的交易越長（修復前撥款的 Stripe 呼叫就在批准的交易裡）窗口越大——本輪把撥款移出核准交易之後，這個窗口已經縮小（**推論**，沒有量測）。

## 10. 狀態

**文件守門測試**（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`）在文件定稿後執行：4 個類別共 **14 個案例，0 失敗**（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1），`BUILD SUCCESS`。

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 321 列。本輪的 commit `26cbb4cd` 已 push（`eb54106c..26cbb4cd`，連同 Sprint 320 的純文件回填 commit `21a6f577` 一起帶出），依使用者 2026-10-29 的長期授權（『做完一輪後，直接 Commit + Push to main』）直接 push，沒有再詢問；pre-commit（Checkstyle、編譯、核心測試、secret 掃描）與 pre-push 輕量守門（act 單元、前端、schema 漂移無漂移）通過，沒有使用 `--no-verify`。**雲端 CI 全綠**：run [41272406171](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/41272406171)，三個 job 皆 success（單元 2 分 37 秒／前端 1 分 56 秒／整合 10 分 13 秒，整個 run 12 分 55 秒），以 `gh run view --json` 交叉確認 headSha 為 `26cbb4cd`，並下載整合 job 的完整雲端日誌（61.1 MB）確認整合測試 **997／0 失敗／0 錯誤／0 跳過**、`BUILD SUCCESS`、Checkstyle 0 違規，`SettlementReviewRaceRealStackIntegrationTest` 10、`SettlementApprovalPayoutTriggerRealStackIntegrationTest` 4 在雲端環境也通過（第一次用 `grep` 比對 `Tests run:` 沒命中，原因是雲端日誌帶 ANSI 色碼，行尾對不上；剝掉色碼後數到 997）。本輪結尾的 Docker 清理：匿名卷 4 個全刪（0 失敗）、卷 1.112GB → 928MB，3 個核准基底映像與 5 個具名卷保留，建置快取 1.889GB 只回報、沒刪。

本輪的 commit 範圍：生產程式碼 `SettlementReviewer`（搶占後 `refresh`、撥款改在提交之後）、`TransferService`（`createTransferAfterApproval`、重試端點接受『`APPROVED` 且沒有 transfer』）；測試 `SettlementReviewRaceRealStackIntegrationTest`（6→10）、`SettlementApprovalPayoutTriggerRealStackIntegrationTest`（新，4）、`SettlementReviewerTest`（14→19）、`TransferServiceTest`（15→19）；文件 `API_M07_Settlement.md`（v1.3）、`SRD_Module_Technical_Design.md`（一段）、`DEFERRED_ITEMS_TRACKER.md`（`DEF-443`／`DEF-444` 已完成、新增 `DEF-445`／`DEF-446`）、`RELEASE_TRACKER.md`、本文件。

**待使用者處理**：`DEF-419`、舊規則資料遷移（若要）、Sprint 306 的 `make down-clean` → `make up` → `make dev-admin-password`、Docker 建置快取。**一句話可推翻的決定**：§8 第 3 點（核准回應的 `data.status` 變成一律 `APPROVED`）。

**下次無 `==>` 的『請繼續』我可以直接做的**：`DEF-446`（🟡，已實測；`applyRefundDeduction` 加狀態條件、影響 0 列時退回走調整單），然後 `DEF-445`（`submitForReview` 比照批准／駁回搶占＋`refresh`；兩者的測試要一起跑，因為提交會把結算單推進到退款直接扣款的範圍）。另有兩個**可選的量測**（不是缺陷，需要你決定值不值得）：另一間店鋪的店主打九個端點、編輯頁進場守門（見 [SPRINT_318_PLAN.md](SPRINT_318_PLAN.md) §10）。
