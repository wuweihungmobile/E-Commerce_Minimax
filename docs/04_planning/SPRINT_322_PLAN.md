# Sprint 322 Plan — DEF-446（退款直接扣款沒有狀態條件）與 DEF-445（提交審核的整列寫回蓋掉退款）

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 321](SPRINT_321_PLAN.md) 的總結，加一句『請繼續完成任務！』，**沒有任何 `==>` 回覆，也沒有互動選項的答案**。我的解讀（推論，不是使用者說的）：只做總結自己列為『下次說「請繼續」我可以直接做』的那兩項——先 `DEF-446`，再 `DEF-445`，兩者的測試一起跑；不碰 `DEF-419`、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取，也不做兩個『可選量測』。

長期授權（2026-10-29，使用者明文：『做完一輪後，直接 Commit + Push to main，請記住』）：本輪 commit 後直接 push，不再詢問。Sprint 321 的純文件回填 commit `6eca29c0`（領先遠端 1 個）是本輪 commit 的祖先，會隨這次 push 一起帶出。

### 1.2 成功標準（開工時定義）

1. **紅燈先行、而且是真實競態**：在**未修改的生產碼**上，用真實 PostgreSQL 量測 `DEF-446`（批准／駁回先拿到鎖、退款後到）與 `DEF-445`（提交的整列寫回蓋掉退款、兩次提交都成功）；每個修復都有不含併發的對照案例，必須是綠的。
2. **預測先寫下來**：動手前把『我預期會看到什麼』登記進 §3，跑完逐一比對，不符的如實揭露。
3. **兩者互相影響的交錯要有案例**：`DEF-446` 的狀態條件不可把『提交把 `PENDING` 推進到 `PENDING_REVIEW`』這個合法的直接扣款範圍擋掉（追蹤表：『修的時候兩者的測試要一起跑』）。
4. **修法有牙齒**：突變驗證預先登記每組『恰好該紅的案例』，結果逐一比對。
5. 全量後端 `mvn -o clean verify`（全新資料庫）通過、Checkstyle 0；push 後雲端 CI 全綠並核對整合測試數。
6. 追蹤表如實更新；過程中發現的相鄰問題只登記、不順手修。

### 1.3 動手前用工具核對的事實

| 說法（出處） | 核對結果 |
|---|---|
| 追蹤表 `DEF-446` 建議：『`applyRefundDeduction` 加 `AND status IN (…)`，影響 0 列時退回走建立調整單』 | **前半可行，後半不完整**。0 列有兩種原因：結算單不存在，或狀態在『讀』與『`UPDATE`』之間被推進。被推進後的狀態若是 `APPROVED`／`PAID`／`FAILED`，確實該走調整單；但若是 `REJECTED`／`REVERSAL_PENDING`／`REVERSED`，`SettlementAdjustmentService` 類別 Javadoc 與主分流寫明『不做事』——被駁回結算單的訂單已釋放，重新結算時由 `Payment.refundedAmount` 帶入退款，這時再建調整單會在下一期**重複扣**。所以後備路徑必須**重新讀取目前狀態、依同一張狀態表分流**，不能一律建調整單。 |
| 後備路徑怎麼重讀狀態 | `findById` 在同一個交易的持久化上下文裡會回傳**已快取的實體**（舊狀態，退款剛讀過它）。改用純量 JPQL 查詢 `SELECT s.status …`（每次都回資料庫的值）。**推論**（Hibernate 一級快取語意）；真實棧案例與突變 R8（改用 `findById`）驗證。 |
| 退款呼叫端『失敗不應影響已完成的退款主流程』 | `PaymentStateService`（第 538～543 行）以 `try/catch (RuntimeException)` 包住；但 `handleOrderRefund`／`handleBookingRefund` 是 `@Transactional`、**加入呼叫端的交易**，例外穿出代理時共用交易被標成 rollback-only（與 `DEF-443` 同一個機制）。所以後備路徑**絕不可丟例外**，否則已經成功退給 Stripe 的款項，本地的付款狀態會連帶回滾。 |
| 交易隔離等級 | `src/main` 沒有任何自訂隔離設定 → PostgreSQL 預設 `READ COMMITTED`。**推論**：被鎖擋住的 `UPDATE` 在鎖釋放後，會對該列的最新已提交版本重新評估 `WHERE`（`EvalPlanQual`），狀態已變時回 0 列而不是套用——修法依賴這個語意；真實棧案例驗證，Sprint 320／321 的搶占（`updateStatusIfCurrent`）已經在依賴同一個語意。 |
| 追蹤表 `DEF-445`：『`submitForReview` 讀取後 `setStatus(PENDING_REVIEW)`＋`save`，沒有搶占』 | 屬實（`SettlementReviewer.submitForReview`，第 85～86 行）。 |
| `submitForReview` 現有的單元測試 | **真實類別上沒有**：只有 `SettlementTenantScopeTest` 的系統租戶守門案例，與 `M07SettlementIntegrationTest` 把 `SettlementReviewer` 整個 mock 掉的 controller 測試。happy path、狀態守門、搶占都沒有單元層守門。（另有 `SystemTenantIsolationE2ETest` 的一個 HTTP 層案例走過真實的提交成功路徑，但它只斷言 `200` 與最終狀態，不守搶占或金額。） |
| 提交改成『搶占＋`refresh`』後不再有任何 setter，`updated_at` 不會被推進 | 實體本身宣告 `updatedAt`、由 `@PreUpdate` 維護；退款的原子 UPDATE 另自己設 `updated_at = CURRENT_TIMESTAMP`。**讀取者**：全庫生產碼（`backend/src/main/java` 中引用 `SettlementStatement` 的檔案，精確 grep `getUpdatedAt`／`updatedAt`）只命中 `Transfer`、`Order` 自己的欄位，沒有任何地方讀結算單的 `updatedAt`；回應 DTO 不含它、`frontend/src` 沒有 settlement 型別含它，也沒有呼叫 submit（只有沖正三個端點）。列為可推翻的決定（§8）。 |
| `applyRefundDeduction` 的呼叫點 | 生產碼只有 `SettlementAdjustmentService.applyDirectDeduction` 一處（訂單與訂房退款共用）；改它同時涵蓋 `handleOrderRefund` 與 `handleBookingRefund`。 |

## 2. 做法（開工前的設計，結果見 §4～§5）

### 2.1 DEF-446：退款的直接扣款加狀態條件，0 列時重讀狀態再分流

- `SettlementStatementRepository.applyRefundDeduction`：`WHERE id = :id` → `WHERE id = :id AND status IN ('PENDING', 'PENDING_REVIEW')`。條件與主分流的『直接扣款範圍』一致，由資料庫在 `UPDATE` 生效的那一刻判斷，不再依賴『讀狀態』當下的快照。
- `SettlementStatementRepository.findStatusById`（新）：純量 JPQL，只回目前狀態。
- `SettlementAdjustmentService.applyDirectDeduction`：影響 1 列＝照舊；影響 0 列＝重讀狀態，依與主分流相同的表處理——`APPROVED`／`PAID`／`FAILED` 建調整單（訂單版或訂房版，由呼叫端傳入）、`REJECTED`／`REVERSAL_PENDING`／`REVERSED` 不做事、結算單不存在記 `warn`（原有行為）、仍是 `PENDING`／`PENDING_REVIEW` 屬於『不可能』（狀態機沒有退回的轉換）→ 記 `error`、不丟例外。

### 2.2 DEF-445：提交比照批准／駁回搶占

`SettlementReviewer.submitForReview`：狀態檢查之後以 `updateStatusIfCurrent(id, PENDING, PENDING_REVIEW)` 搶占（輸家得到 `409 E-5014`，與循序的第二次提交同一個結果），搶占成功後 `entityManager.refresh(statement)`。搶占成功＝這一列已被本交易鎖住，退款的原子 UPDATE 要等本交易結束；`refresh` 讓回應拿到鎖住當下最新的狀態與金額。**之後不再有任何 `setStatus`／`save`**——整列寫回正是 `DEF-445` 本身（見 §6、§8）。

### 2.3 測試

- 改：`SettlementReviewRaceRealStackIntegrationTest`（10 → 18 案例）。`DEF-446`：批准先退款後（訂單、訂房各一）、駁回先退款後、循序對照；`DEF-445`：提交 vs 同時入帳的退款、兩次提交同時送出、循序對照、**提交先退款後（兩者互相影響的交錯）**。
- 改：`SettlementAdjustmentServiceTest`（後備路徑五種狀態結果、訂單版與訂房版）、`SettlementReviewerTest`（`submitForReview` 的 happy path、狀態守門、搶占輸家、`refresh` 順序）。

## 3. 修改前的紅燈（未修改的程式碼、真實 PostgreSQL）

### 3.1 預測（跑之前登記，來源是讀碼＋Sprint 321 的一次性探針）

測試：[SettlementReviewRaceRealStackIntegrationTest](../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementReviewRaceRealStackIntegrationTest.java) 新增的 8 個案例（只用 HTTP、`SettlementAdjustmentService.handleOrderRefund`／`handleBookingRefund` 與 JDBC，所以在未修改的生產碼上編得過）。種子結算單 GMV 1000、抽成 100、淨額 900，退款 100。批准／駁回／提交請求先卡在它的第一個寫入，退款（真實的退款服務）以 `FOR UPDATE` 對手交易的鎖序決定誰先寫（`pg_stat_activity` 確認，不靠睡眠）。**撥款金額不斷言**：批准先拿到鎖時，transfer 金額是 900 還是 800 取決於提交後的撥款交易與退款的 `UPDATE` 誰先讀／寫（Sprint 321 實測一次是 900，不確定的交錯）。

| 案例 | 預測（未修改） | 預測的違反項目 |
|---|---|---|
| **a. 批准先、退款後（訂單）**：批准請求先卡在搶占、退款（讀到 `PENDING_REVIEW`）後卡在直接扣款；放行 | 批准 `200`、`APPROVED`；批准提交後，退款那條**沒有狀態條件**的 `UPDATE` 仍生效：`total_refunds = 100.00`、淨額 `800.00`、`adjustment_statements` 0 筆 | 3 項：`total_refunds`（預期 `0.00`）、淨額（預期 `900.00`）、調整單內容（預期 1 筆 `-100.00`／`PENDING`／`order_id`＝該訂單） |
| **b. 批准先、退款後（訂房）**：同上，用 `handleBookingRefund` | 同 a | 3 項（調整單預期 `booking_id`＝該訂房、`order_id` 空） |
| **c. 駁回先、退款後（訂單）** | 駁回 `200`、`REJECTED`、訂單已釋放；退款的 `UPDATE` 仍生效，把退款扣在**已作廢**的結算單上：`total_refunds = 100.00`、淨額 `800.00`；調整單 0 筆（這項本來就對，修復後也必須維持 0——否則會和重新結算時的 `Payment.refundedAmount` 重複扣） | 2 項：`total_refunds`（預期 `0.00`）、淨額（預期 `900.00`） |
| **d. 對照（不含併發）：批准已提交後才到的退款** | 綠——主分流讀到 `APPROVED`，直接走調整單：`total_refunds = 0.00`、淨額 `900.00`、調整單 1 筆 | — |
| **e. 提交 vs 同時入帳的退款**：退款（真實服務）先寫入並持鎖；提交請求先讀到 `total_refunds 0／淨額 900`、卡在整列寫回的 `UPDATE`；退款提交後放行 | 提交 `200`、`PENDING_REVIEW`；整列寫回用舊值蓋掉退款：資料庫 `total_refunds = 0.00`、淨額 `900.00`；回應（由那個過期的受管實體組的）`totalRefunds = 0.00`、`netSettlementAmount = 900.00` | 4 項：資料庫 `total_refunds`、淨額、回應 `totalRefunds`、回應 `netSettlementAmount` |
| **f. 兩次提交同時送出** | 提交沒有搶占，兩個請求各自整列寫回同一個值，**兩個都回 `200`**；結算單 `PENDING_REVIEW` | 2 項：後到者的回應碼（預期 `409`）與錯誤碼（預期 `E-5014`） |
| **g. 對照（不含併發）：退款先提交、提交後到** | 綠——提交讀到的就是退款後的金額：`100.00`／`800.00`，回應也是 | — |
| **h. 兩者互相影響：提交先、退款後**（提交先卡在寫入、退款（讀到 `PENDING`）後卡在直接扣款；放行後提交先提交，結算單變成 `PENDING_REVIEW`，退款才生效） | **綠**——未修改的退款扣款沒有狀態條件，照樣生效：`100.00`／`800.00`、調整單 0 筆。這個案例的用途不是抓現在的缺陷，而是**修復後仍必須綠**：它守住『狀態條件必須涵蓋 `PENDING_REVIEW`』（狀態條件若只寫 `PENDING`，這個交錯下退款會被丟掉） | — |

**預測的總計**：18 案例（既有 10＋新增 8）中 5 紅（a、b、c、e、f）、13 綠；違反項目共 14（3＋3＋2＋4＋2）。

### 3.2 實測結果（未修改的生產碼，真實 PostgreSQL）

18 案例跑、**5 紅 13 綠**（Failures 5、Errors 0）；既有 10 案例全綠，新增 8 案例中 5 紅 3 綠。**逐格符合預測**：

| 案例 | 實測 | 與預測 |
|---|---|---|
| a. 批准先、退款後（訂單） | **3 項違反**：`total_refunds` 預期 `0.00` 實際 `100.00`；淨額預期 `900.00` 實際 `800.00`；調整單預期 1 筆實際 `[]`。結算單 `APPROVED`；transfer 為 `SKIPPED_ONBOARDING_INCOMPLETE`、金額 `900.00`（不斷言，見下） | 相符 |
| b. 批准先、退款後（訂房） | **3 項違反**（同 a，調整單預期 `booking_id` 那筆，實際 `[]`；transfer 金額同樣 `900.00`） | 相符 |
| c. 駁回先、退款後 | **2 項違反**：`total_refunds` 實際 `100.00`、淨額實際 `800.00`（退款扣在已作廢的結算單上）；調整單 `[]`（第三項斷言通過，預測就是對的）；`REJECTED`、訂單已釋放、審核人與原因都對 | 相符 |
| d. 對照：批准已提交後才到的退款 | 綠 | 相符 |
| e. 提交 vs 同時入帳的退款 | **4 項違反**：資料庫 `total_refunds` `0.00`（預期 `100.00`）、淨額 `900.00`（預期 `800.00`）；回應 `totalRefunds` `0.00`、`netSettlementAmount` `900.00`；回應 `status` 是 `PENDING_REVIEW`（通過） | 相符 |
| f. 兩次提交同時送出 | **2 項違反**：後到者 HTTP `200`（預期 `409`）、錯誤碼 `""`（預期 `E-5014`）——兩個提交都成功；最終 `PENDING_REVIEW`（通過） | 相符 |
| g. 對照：退款先提交、提交後到 | 綠 | 相符 |
| h. 提交先、退款後（兩者互相影響） | 綠 | 相符（用途是修復後仍須綠） |

**撥款金額不斷言的原因（實測支持）**：a、b 兩案的 transfer 金額都量到 `900.00`——結算單顯示扣了 100（`800.00`）、撥款卻是 900、也沒有調整單，退款對撥款而言消失，與 Sprint 321 探針是同一個交錯；但這是『撥款交易讀到退款 `UPDATE` 生效之前的值』的一次交錯結果，換個時序會是 `800.00`，所以不寫成斷言。三次觀察（Sprint 321 探針一次、本輪兩案）都是 `900.00`，只是樣本，不是保證。

## 4. 修改後與突變驗證

### 4.1 修改後（單元／整合）

- 單元：`SettlementAdjustmentServiceTest` 18 → 26（+8）、`SettlementReviewerTest` 19 → 23（+4）、`SettlementTenantScopeTest` 4，三個類別共 53 案例 0 失敗。
- 真實棧：`SettlementReviewRaceRealStackIntegrationTest` 先只修 `DEF-446`：**18 案例 2 紅**——紅的正是 e、f（`DEF-445` 尚未修），a、b、c 轉綠、h 與既有 10 案例維持綠；兩個修復都做完後 **18 案例 0 失敗**。這也實證了 §1.3 兩個標為『推論』的語意：被鎖擋住的 `UPDATE` 在 READ COMMITTED 下對最新版本重新評估 `WHERE`（a、b、c 的退款回 0 列後，後備路徑重讀到 `APPROVED`／`REJECTED`）；純量查詢讀到的是資料庫現況（突變 A3 另證 `findById` 讀不到）。

### 4.2 突變驗證（預先登記：每組先寫下『恰好該紅的案例』，跑完逐一比對）

驅動沿用 Sprint 321 的 `bmut.py`（從前一個 session 的暫存目錄複製；被突變的三個主檔事先備份並記錄 SHA-256），規格用檔案式、`old` 全是程式碼行（CJK 註解行留在被突變區塊之外），開跑前乾跑『每個 `old` 恰好出現一次』全數通過。兩組各有自己的基準 Z0（全新資料庫）：

- **446 組**：單元 `SettlementAdjustmentServiceTest`（26）＋整合 `SettlementReviewRaceRealStackIntegrationTest`（18）＋`M07SettlementRefundConcurrencyIntegrationTest`（2）＋`SettlementBookingIntegrationTest`（7），共 53 案例。
- **445 組**：單元 `SettlementReviewerTest`（23）＋`SettlementTenantScopeTest`（4）＋整合 `SettlementReviewRaceRealStackIntegrationTest`（18）＋`SystemTenantIsolationE2ETest` 的提交案例（1，店主提交自己店鋪的結算單，是真實棧上唯一另一個走過提交成功路徑的案例），共 46 案例。

判決標準：失敗集合與登記的 `must` **恰好**吻合才算 `KILLED(exactly must)`。以下 `must` 是**跑之前**登記的（來源：逐個案例讀斷言；縮寫對照見表後）：

| 組 | 突變 | 登記的恰好該紅 |
|---|---|---|
| R1 | 拿掉 `applyRefundDeduction` 的狀態條件（`DEF-446` 本身） | 競態 a、b、c（3） |
| R2 | 狀態條件只留 `PENDING`（退款落在 `PENDING_REVIEW` 就被丟掉） | 競態：循序退款對照、批准 vs 退款、送給 Stripe 的金額、駁回 vs 退款（這三個靠退款持鎖，退款回 0 列就不再持鎖→等不到請求卡住而逾時）、a、b、c（退款不再被擋住→等不到第二個請求卡住而逾時）、h（提交先、退款後：退款丟掉）（8） |
| R3 | 狀態條件加上 `APPROVED` | 競態 a、b（2；c 不受影響因為 `REJECTED` 仍不在條件內，d 因為主分流讀到 `APPROVED` 根本不走 UPDATE） |
| A1 | 後備路徑不重讀（回到舊行為：記 `warn` 後丟掉退款） | 單元：訂單調整單、訂單作廢、訂單不存在、訂單不可能、訂房調整單、訂房作廢（6；後四者靠 Mockito 嚴格 stub 的『未使用』連帶紅）＋競態 a、b（2）＝8 |
| A2 | 後備路徑一律建調整單（含已作廢的結算單） | 單元：訂單作廢、訂房作廢（2）＋競態 c（1）＝3 |
| A3 | 後備路徑改用 `findById` 重讀（持久化上下文快取的舊狀態） | 同 A1：8 |
| A4 | 直接扣款成功後仍走後備（批准在扣款之後才提交就會重複計入） | 單元：訂單成功不重讀、訂房成功不重讀（2） |
| A5 | 訂房的後備路徑接成『訂單』調整單 | 單元：訂房調整單（1）＋競態 b（1）＝2 |
| A6 | 『不可能』分支（重讀仍是 `PENDING`／`PENDING_REVIEW`）改成猜著建調整單 | 單元：訂單不可能（1） |
| A7 | 『不可能』分支丟例外（會把共用的退款交易標成 rollback-only） | 單元：訂單不可能（1） |
| A8 | 『結算單不存在』分支丟例外 | 單元：訂單不存在（1） |
| V1 | 提交不搶占（沒有任何程式寫入 `PENDING_REVIEW`） | 單元 S1、S2（2）＋競態 e、f、g、h（4；e、f、h 靠請求不再卡在鎖上而逾時）＋E2E 提交案例（1）＝7 |
| V2 | 搶占結果被忽略（輸家不被擋下） | 單元 S2（1）＋競態 f（1）＝2 |
| V3 | 拿掉 `refresh`（回應顯示過期快照） | 單元 S1（1）＋競態 e（1）＝2 |
| V4 | `refresh` 移到搶占**之前**（鎖住之前就讀） | 單元 S1、S2（2；S2 的 `verifyNoInteractions(entityManager)` 會被前移的 `refresh` 弄紅）＋競態 e（1）＝3 |
| V6 | 拿掉提交的前置狀態檢查（外部行為等價：搶占也會擋下，只有單元的『不打資料庫搶占』守得住） | 單元 S3（1） |

縮寫：競態 a～h 見 §3.1；『循序退款對照』等是 Sprint 321 既有的案例名；單元的訂單／訂房案例是 `SettlementAdjustmentServiceTest` 的後備路徑案例；單元 S1＝提交先搶占再 `refresh` 且回應是重載後的狀態、S2＝搶占輸了不 `refresh`、S3＝已不是 `PENDING` 就不搶占（皆在 `SettlementReviewerTest`）。

（結果見下方 §4.3。）

### 4.3 結果

**446 組**（基準 Z0，全新資料庫：53 案例 0 失敗）：11 組突變的失敗集合**全部與登記的 `must` 恰好吻合**（驅動的 `restored=True` 12 次、`MSG-MISMATCH` 0、`INVALID` 0；失敗數與登記數逐組相同：R1 3、R2 8、R3 2、A1 8、A2 3、A3 8、A4 2、A5 2、A6 1、A7 1、A8 1）。**445 組**（基準 Z0，全新資料庫：46 案例 0 失敗）：5 組全部吻合（V1 7、V2 2、V3 2、V4 3、V6 1）。兩組結束後，被突變的三個主檔與三個測試檔的 SHA-256 與事先記錄逐一相同，沒有突變殘留字串。以下是讀各組失敗訊息確認的歸因：

1. **R2（狀態條件只留 `PENDING`）的 8 個失敗有兩種原因**：6 個是『靠鎖編排』的案例——退款對 `PENDING_REVIEW` 的結算單回 0 列、不再持有或等待那一列的鎖，案例等不到『N 個請求卡在鎖上』而逾時（訊息 `等不到 N 個請求卡在結算單的鎖上（目前 k 個）`；對手交易自己有 30 秒持鎖上限，到期放行，所以 k 可能是 0）；另 2 個（循序退款對照、h）是退款真的被丟掉（`total_refunds` 預期 `100.00` 實際 `0.00`）。h 守的正是這件事：狀態條件若漏掉 `PENDING_REVIEW`，『提交先、退款後』的交錯下退款會消失。
2. **A3（後備路徑改用 `findById` 重讀）把 §1.3 標為『推論』的事升級為事實**：同一個持久化上下文裡 `findById` 回傳快取的舊狀態（`PENDING_REVIEW`），落入『不可能』分支、退款被丟掉；a、b 兩案各只剩一項違反（調整單預期 1 筆、實際 `[]`），資料庫金額維持 `0.00`／`900.00`。
3. **A4（直接扣款成功後仍走後備）只被單元案例殺掉**：真實棧裡，成功扣款後多一次重讀只會讀到 `PENDING`／`PENDING_REVIEW`，落入『不可能』分支、只多一行 `error` 日誌——**日誌對照量到**：基準 Z0 的 `unexpected, refund NOT applied` 是 2 行（單元案例故意模擬『不可能』），A4 是 19 行，多出的 17 行逐一對得上成功扣款的真實棧案例（`M07SettlementRefundConcurrencyIntegrationTest` 的 10 筆併發退款 10 行；競態類別 7 行，其中 2 行在測試主執行緒＝兩個循序案例、5 行在執行緒池＝五個併發案例），沒有改變任何結果。『重複計入』要在『扣款成功之後、批准才提交』的交錯下才會發生，真實棧排不出這個時序，單元層用 `lenient()` 讓重讀回 `APPROVED` 模擬。
4. **A6／A7／A8 各被恰好一個單元案例殺掉**：釘住『不可能』分支不猜（不建調整單）、不丟例外，以及『結算單不存在』分支不丟例外——例外穿出會把共用的退款交易標成 rollback-only，已經成功退給 Stripe 的款項在本地的付款狀態就會連帶回滾。
5. **V3／V4 的真實棧案例（e）紅在『回應』，不是資料庫**：提交之後沒有任何 setter，搶占的 UPDATE 已經寫完狀態，資料庫本來就是對的；`refresh` 管的是回應（e 的 3 項違反是回應的 `status` `PENDING`、`totalRefunds` `0.00`、`netSettlementAmount` `900.00`，資料庫斷言都通過）以及『之後若加了 setter 時整列寫回帶的是不是最新值』。V4 連帶讓單元 S2 紅（登記時就預測了）：`refresh` 移到搶占前，搶占輸了時 `entityManager` 已被碰到。
6. **V6 是外部行為等價的突變**（搶占也會擋下，回應同樣是 `409 E-5014`），只有單元 S3 的『不打資料庫搶占』守得住（同 Sprint 245 的『防禦縱深前置檢查』）。
7. **V1 把整個『搶占』拿掉**：沒有任何程式再寫入 `PENDING_REVIEW`——e、f、h 因請求不再卡在鎖上而逾時，g 與 E2E 提交案例紅在狀態仍是 `PENDING`，證實搶占的 UPDATE 是提交唯一的寫入。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**18 分 31 秒**；正式 Maven 編譯 387 個主程式檔、322 個測試檔；單元 **2464／0 失敗／0 錯誤／0 跳過**（Sprint 321 的 2452＋`SettlementAdjustmentServiceTest` +8、`SettlementReviewerTest` +4）；整合 **1005／0／0／0**（997＋競態類別 +8）；Checkstyle main／test 各 0 違規；日誌 `<<< FAILURE`／`<<< ERROR`／`Unresolved compilation`／`ClassFormatError`／`NoClassDefFoundError` 各 0 個。新增／變動的類別在 verify 內確實執行：`SettlementReviewRaceRealStackIntegrationTest` 18、`SettlementAdjustmentServiceTest` 26、`SettlementReviewerTest` 23、`SettlementTenantScopeTest` 4、`SettlementApprovalPayoutTriggerRealStackIntegrationTest` 4、`SettlementBookingIntegrationTest` 7、`SettlementExactlyOnceIntegrationTest` 6、`M07SettlementRefundConcurrencyIntegrationTest` 2、`SystemTenantIsolationE2ETest` 9、`M07SettlementIntegrationTest` 14。 |
| 紅燈先行（§3） | 未修改的程式碼上 18 案例 5 紅 13 綠、14 項違反，預測逐格相符；沒有預測落空。 |
| 突變驗證（§4） | 兩組基準 Z0（446 組 53／0、445 組 46／0）＋16 組突變，**全部 `KILLED(exactly must)`**，預先登記逐一吻合；被突變的三個主檔與三個測試檔 SHA-256 在驅動結束後與事先記錄的一致，無殘留字串。 |
| 穩定性 | 競態類別（18 案例）在全量 verify 之後連跑 3 次（每次重新啟動 Spring context），每次 18 案例 0 失敗。 |
| IDE Problems | 我改動的 6 個檔案診斷 **0**；`getDiagnostics` 全域列出的所有條目也都是空陣列。 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 在**所有文件定稿後**執行，結果見 §10。 |
| 沒有執行的項目 | Playwright 完整 E2E 與 `make validate-schema-doc`：本輪沒有前端、Flyway 遷移或 schema 變更（改的是原生 SQL 的 `WHERE` 條件與一個純量 JPQL 查詢，不是 schema），見 §7 第 8 點。 |

## 6. 過程中的失誤與修正

1. **一次推論被查證推翻**：寫 `DEF-447` 草稿時，我讀 `SettlementReversalService.confirmReversal` 沒有搶占，推論『兩個確認同時送出會產生兩張信用票據』。登記前去查 `credit_notes` 有沒有唯一約束，讀到 Flyway `V36` 的 `UNIQUE (tenant_id, credit_note_number)`，而編號是 `CN-{租戶前綴}-{日期}`（每租戶每天一個）——第二個 INSERT 會撞約束而回滾，不會有兩張信用票據；同一條件反而露出另一個更確定的問題（同一租戶同一天第二張逆轉的確認會撞同一條約束），登記為 `DEF-448`。推論已在追蹤表改寫，沒有留下錯的版本。
2. **開發-編譯-測試循環的偏差**：`DEF-446` 的生產碼改完後先編譯＋跑了既有的 18 個單元案例，才寫新案例；`DEF-445` 則是改完 `SettlementReviewer.submitForReview` 之後**直接寫單元測試**，第一次編譯＋執行是三個單元類別一起跑（53／0，綠的）。流程上應該在寫測試前先單獨編譯一次。
3. **工作目錄漂移**：Bash 的 `cd` 讓工作目錄數次改變（`backend/`、`backend/src/main/java/…`），之後 Bash 一律用絕對路徑與 `mvn -f`。
4. **`Nothing to compile - all classes are up to date`**（IDE 語言服務搶先編譯）：用案例數（18／26／23）確認新案例確實執行；突變驅動的 `purge_classes` 強制 Maven 重編；正式編譯由全量 `clean verify`（387＋322 個檔案）與 pre-commit 擔任。
5. **`R2` 類逾時突變很慢**：狀態條件只留 `PENDING` 時，6 個案例等不到鎖而逾時（每個 30 秒），單組 256 秒；沒有影響結果，只是耗時。

## 7. 限制（誠實揭露）

1. **新案例沒有走到閘道**：測試店鋪沒有 Connect 帳戶，transfer 一律是 `SKIPPED_ONBOARDING_INCOMPLETE`；批准先拿到鎖的交錯下，送給 Stripe 的最終金額沒有斷言（Sprint 321 的『送給 Stripe 的金額』案例仍在，但那是退款先提交的交錯）。閘道本身仍是 `@MockBean`、沒有對真實 Stripe 驗證（承 Sprint 80 起的既有限制）。
2. **每個案例只確定一個交錯**：批准、駁回、提交三種請求與退款的兩個先後順序都有案例，但沒有窮舉更細的時序（鎖序由 `pg_stat_activity` 確認，不靠睡眠）。
3. **後備路徑在真實棧只走到 `APPROVED`（a、b）與 `REJECTED`（c）**；`PAID`／`FAILED`／`REVERSAL_PENDING`／`REVERSED`、『結算單不存在』與『不可能』分支只有單元層（mock）守門。
4. **『不可能』分支是依狀態機推論的**：全庫 grep——只有 `SettlementGenerator` 建立新結算單與實體的預設值是 `PENDING`，提交的搶占（`PENDING → PENDING_REVIEW`）在直接扣款範圍之內，沒有任何 SQL 或 Flyway 遷移把結算單狀態設回去；真的發生時的處置（記 `error`、不猜、不丟例外）由單元案例釘住。
5. **提交不再推進 `updated_at`**（§8 第 5 點）。
6. **提交之後『加 setter』的保護只靠 `refresh` 的位置**：單元測試不禁止之後再 `save`（`refresh` 之後的整列寫回帶的是最新值，本身是安全的）；『`refresh` 之後再 `setStatus(PENDING_REVIEW)`＋`save`』這個突變依構造等價（狀態已是 `PENDING_REVIEW`，髒檢查看不到變動），沒有登記也沒有跑。
7. **其他整列寫回結算單的路徑仍未量測**（`DEF-447`）、**信用票據編號每租戶每天一個**（`DEF-448`）：兩者都只有讀碼推論，沒有跑任何測試或探針。
8. **沒有執行 Playwright 完整 E2E 與 `make validate-schema-doc`**：沒有前端、Flyway 遷移或 schema 變更。提交端點沒有前端呼叫者（`frontend/src` 只有沖正的三個端點），所以兩次同時提交後到者從 `200` 變 `409` 沒有已知的前端依賴；外部（腳本、管理工具）呼叫者我無從得知。

## 8. 我替使用者做的決定（可一句話推翻）

1. 『請繼續完成任務！』（無 `==>`）＝依 Sprint 321 總結自己寫的『下次說「請繼續」，我可以直接做 `DEF-446`，再做 `DEF-445`；兩者的測試要一起跑』，只做這兩項；不碰 `DEF-419`、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取，也不做兩個『可選量測』。
2. **`DEF-446` 沒有照追蹤表原建議『0 列一律退回走調整單』**：已駁回的結算單，訂單已釋放、重新結算時由 `Payment.refundedAmount` 帶入退款，再建調整單會在下一期重複扣。改成 0 列時**重新讀取目前狀態、依主分流同一張表處理**（理由 §1.3；突變 A2 證實案例 c 守得住）。
3. **重讀狀態用純量 JPQL 查詢**，不用 `findById`／`refresh`：`findById` 在同一個持久化上下文回傳快取的舊狀態（突變 A3 證實）；`refresh` 對不存在的列會丟例外，而這條路徑絕不可丟例外。
4. **『不可能』分支（重讀仍是 `PENDING`／`PENDING_REVIEW`）記 `error`、不猜、不丟例外、退款不套用**：另一個選項是在那裡建調整單（保證退款不被丟掉），但那是對一個不應發生的狀態猜測處置；單元案例釘住現在的行為（突變 A6、A7）。
5. **`DEF-445` 用『搶占＋`refresh`、不再 `setStatus`／`save`』**（追蹤表建議、比照批准／駁回），沒選 `@DynamicUpdate`（實體層級變更；transfer 金額這類由記憶體值計算的地方仍需 `refresh`）。兩個**可見行為變更**：(a) 同時送出兩次提交時，後到者從 `200` 變成 `409 E-5014`（與循序的第二次提交相同）；(b) 提交不再推進 `updated_at`（生產碼 grep：沒有任何讀取者，回應 DTO 也不含它）。若要保留 (b)，在 `refresh` 之後加一行 `statement.setUpdatedAt(Instant.now())`，代價是多一次整列 UPDATE。
6. **新案例放進既有的競態類別**（共用 Spring context 與編排輔助，新增 `requestThenRefund`），類別的 `DisplayName` 與 Javadoc 一併更新，說明它現在涵蓋審核、提交與退款的併發。
7. **不改 PRD／FRD**：需求沒變，PRD §6.2.1 的『依結算單狀態分流』在競態下現在也成立；只有 `API_M07` 升為 v1.4。
8. **`DEF-447`／`DEF-448` 只登記、不量測、不修**，並明確標示只有讀碼推論（量測 `DEF-448` 需要先建同形唯一索引，因為整合測試庫是 Hibernate 建表、沒有 Flyway `V36` 的約束）。

## 9. 新登記的缺陷

- **`DEF-447`**（🟢，**讀碼推論、未量測**）：結算單其他狀態轉換路徑沒有搶占，或搶占之後仍用搶占前讀到的實體整列寫回——① `handleTransferReversedWebhook` 讀到 `PAID` 才改 `FAILED` 但沒有搶占，與 `initiateReversal` 的搶占交錯時可能把 `REVERSAL_PENDING` 蓋回 `FAILED`；② `retryFailedTransfer` 的 `FAILED → APPROVED` 整列寫回沒有搶占；③ `initiateReversal` 搶占後沒有 `refresh`；④ `createTransferForStatement` 的 `setStatus(PAID／FAILED)` 整列寫回（『蓋掉退款』依構造不成立，因為 `DEF-446` 之後退款只會直接扣在 `PENDING`／`PENDING_REVIEW`）。這些是**狀態對狀態**的競態，與 `DEF-444`～`DEF-446` 不同類。**建議**：比照 `DEF-442`～`DEF-446`，每條路徑先用本輪的競態編排在未修改的程式碼上量成事實，屬實才修。
- **`DEF-448`**（🟢，**讀碼＋schema 推論，確定性而非競態，未量測**）：信用票據編號是 `CN-{租戶前綴}-{日期}`、而 Flyway `V36` 有 `UNIQUE (tenant_id, credit_note_number)`，所以同一租戶同一天確認第二張逆轉必然撞唯一約束（`confirmReversal` 整個回滾，HTTP 狀態沒有查）。同一條約束也（偶然地）擋住了同一張結算單兩個確認同時送出會產生兩張信用票據的可能（輸家得到資料庫錯誤而不是乾淨的 `409`）。**建議**：先以同形唯一索引模擬 `V36` 量測；修法的編號格式屬產品／會計決策，要使用者拍板。

## 10. 狀態

**文件守門測試**（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`）在文件定稿後執行：4 個類別共 **14 個案例，0 失敗**（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1），`BUILD SUCCESS`。

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 322 列。本輪的 commit 範圍：生產程式碼 `SettlementStatementRepository`（`applyRefundDeduction` 加狀態條件、新增 `findStatusById`）、`SettlementAdjustmentService`（0 列時重讀狀態、依同一張表分流）、`SettlementReviewer`（`submitForReview` 搶占＋`refresh`）；測試 `SettlementReviewRaceRealStackIntegrationTest`（10→18）、`SettlementAdjustmentServiceTest`（18→26）、`SettlementReviewerTest`（19→23）；文件 `API_M07_Settlement.md`（v1.4）、`DEFERRED_ITEMS_TRACKER.md`（`DEF-445`／`DEF-446` 已完成、新增 `DEF-447`／`DEF-448`）、`RELEASE_TRACKER.md`、本文件。

**待使用者處理**：`DEF-419`、舊規則資料遷移（若要）、Sprint 306 的 `make down-clean` → `make up` → `make dev-admin-password`、Docker 建置快取。**一句話可推翻的決定**：§8 第 5 點（提交的兩個可見行為變更：後到者 `409`、不再推進 `updated_at`）與第 2 點（後備路徑不照追蹤表『一律走調整單』）。

**下次無 `==>` 的『請繼續』我可以直接做的**：`DEF-447`（量測題：每條路徑先用本輪的競態編排在未修改的程式碼上量成事實，屬實才修）與 `DEF-448` 的量測（先以同形唯一索引模擬 `V36`；修法的編號格式屬產品／會計決策，要你拍板）。另有兩個**可選的量測**（不是缺陷，需要你決定值不值得）：另一間店鋪的店主打九個端點、編輯頁進場守門（見 [SPRINT_318_PLAN.md](SPRINT_318_PLAN.md) §10）。

**push 與雲端 CI**：本輪 commit 後依 2026-10-29 長期授權直接 push（連同 Sprint 321 的純文件回填 commit `6eca29c0`）；push 結果與雲端 CI 於 push 後回填。
