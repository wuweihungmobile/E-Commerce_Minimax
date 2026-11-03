# Sprint 323 Plan — DEF-447（結算單核准之後的狀態對狀態競態）與 DEF-448（信用票據編號每租戶每天一個）的量測

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 322](SPRINT_322_PLAN.md) 的總結，加一句『請繼續完成任務！』，**沒有任何 `==>` 回覆，也沒有互動選項的答案**。我的解讀（推論，不是使用者說的）：只做總結自己列為『下次說「請繼續」我可以直接做』的那兩項——`DEF-447` 與 `DEF-448` 的**量測**；`DEF-448` 的修法（信用票據編號格式）總結明寫『要先由你拍板，那屬於產品／會計決策』，所以本輪只量測它，不替使用者決定編號格式；不碰 `DEF-419`、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取，也不做兩個『可選量測』。

長期授權（2026-10-29，使用者明文：『做完一輪後，直接 Commit + Push to main，請記住』）：本輪 commit 後直接 push，不再詢問。開工時 Sprint 322 的純文件回填 commit `e8383a4e` 領先遠端 1 個；它後來隨使用者中途回報的 CI 失敗的修復 commit `959dbeca` 一起 push 出去了（見 §10），所以本輪的 push 不再帶它。

### 1.2 成功標準（開工時定義）

1. **每一條路徑先量成事實**：`DEF-447` 的四個子項原本只有讀碼推論。先做『誰會寫結算單』的全庫盤點，找出**真的有並行寫入者**的路徑，再對那些路徑用真實 PostgreSQL 在**未修改的生產碼**上量測；沒有並行寫入者的子項不硬湊測試，明寫『依盤點無可達的並行寫入者』。
2. **預測先寫下來**：動手前把『我預期會看到什麼』登記進 §3.1，跑完逐一比對，不符的如實揭露。
3. **屬實才修，而且只修不需產品決策的**：`DEF-447` 量到的真實缺陷（有紅燈的）修好；`DEF-448` 只量測、不修，把量到的事實（含 HTTP 狀態）寫進追蹤表，編號格式用互動選項問使用者。
4. **修法有牙齒**：突變驗證預先登記每組『恰好該紅的案例』。
5. 全量後端 `mvn -o clean verify`（全新資料庫）通過、Checkstyle 0；push 後雲端 CI 全綠並核對整合測試數。
6. 追蹤表如實更新；過程中發現的相鄰問題只登記、不順手修。

### 1.3 動手前用工具核對的事實

| 說法（出處） | 核對結果 |
|---|---|
| 『其他整列寫回結算單的路徑仍未量測』（`DEF-444`／`DEF-445` 追蹤表）——到底有哪些路徑 | **盤點**：`backend/src/main/java` 對 `settlement_statements` 的寫入點只有 10 個方法（14 個呼叫點）——`SettlementGenerator`（只有 `INSERT` 新結算單）、`SettlementReviewer`（提交、批准、駁回：條件式更新搶占，批准／駁回搶占後 `refresh`＋整列寫回）、`SettlementAdjustmentService`（`applyRefundDeduction`，Sprint 322 起帶狀態條件）、`TransferService`（`createTransferForStatement` 寫 `PAID`／`FAILED`、`retryFailedTransfer` 寫 `APPROVED`、`handleTransferReversedWebhook` 寫 `FAILED`，三處都是整列寫回、沒有搶占）、`SettlementReversalService`（`initiateReversal` 搶占＋整列寫回、`confirmReversal` 整列寫回、沒有搶占）。另外沒有任何原生或 JPQL 的 `UPDATE` 指向這張表（`grep` 只命中 `SettlementStatementRepository` 內的兩個）。 |
| 哪些寫入者會在**同一個狀態**上並行（真正的競態面） | 依上一列的狀態轉換整理：`PAID`＝{webhook 整列寫回、`initiateReversal` 搶占}；`REVERSAL_PENDING`＝{`confirmReversal` ×2，整列寫回、沒有搶占}；`FAILED`／`APPROVED`（有舊 transfer）＝{`retryFailedTransfer` ×2，整列寫回、沒有搶占}；`APPROVED`（沒有 transfer）＝{撥款觸發、重試}，靠 transfer 佔位的唯一索引（`DEF-441`，Sprint 319 已有真實競態案例）。**這是讀碼整理，不是執行結果。** |
| 追蹤表 `DEF-447` ③『`initiateReversal` 搶占後沒有 `refresh`』與 ④『`createTransferForStatement` 的整列寫回』 | 依上面的整理，`PAID` 與 `APPROVED` 這兩個狀態上，除了 ①（webhook）之外**沒有其他可達的並行寫入者**：`PAID` 的金額在 `DEF-446` 之後凍結（退款只直接落在 `PENDING`／`PENDING_REVIEW`，`PAID` 走調整單、不動這一列），`APPROVED` 同理。所以 ③④ 單獨存在時沒有可量測的失敗，**不另外寫測試**；③ 的 `refresh` 若加了也只是衛生。結論是讀碼推論，在計畫書與追蹤表標明。 |
| `DEF-447` ①：webhook 讀到 `PAID` 才改 `FAILED`，與 `initiateReversal` 交錯 | 讀碼屬實（`TransferService.handleTransferReversedWebhook`，`findById` → `setStatus(FAILED)` → `save`，沒有條件；`SettlementStatement` 沒有 `@DynamicUpdate`，所以寫的是**整列**：連 `initiateReversal` 剛寫的四個逆轉欄位也會被讀取當時的舊值〔null〕蓋掉，不只是狀態）。**未量測。** |
| `DEF-448`：同租戶同日第二張逆轉確認撞唯一約束 | `V36__Create_Credit_Notes_Table.sql` 有 `UNIQUE (tenant_id, credit_note_number)`、`credit_note_number VARCHAR(30)`；`grep -l credit_notes db/migration/*.sql` 只命中 `V36`，之後沒有 migration 動過這張表；`CreditNote` 實體沒有宣告這條約束，所以 Hibernate 建的整合測試庫沒有它。編號 `"CN-" + 租戶 id 前 8 碼 + "-" + 今天的 yyyyMMdd`＝20 字元，放得進 30。 |
| 撞約束之後 HTTP 是什麼 | `GlobalExceptionHandler` 的 `@ExceptionHandler` 只有 `BusinessException`、各種請求格式例外與通用的 `Exception.class`，**沒有** `DataIntegrityViolationException`、`ObjectOptimisticLockingFailureException`，所以會落入通用處理器：`500`／`E-9900`（Sprint 319、320 在別處實測過同一條路徑）。這是讀碼，本輪量測。 |
| 現有測試對這幾條路徑的覆蓋 | **逆轉發起／確認與 `transfer.reversed` webhook 沒有任何真實棧案例**：提到它們的測試檔只有 `TransferServiceTest`（Mockito 單元）、`PaymentWebhookServiceTest`（Mockito 單元）、`M07SettlementIntegrationTest`（`@MockBean` 11 個，含 `SettlementStatementRepository`）。`retryFailedTransfer` 的真實棧只有 `SettlementApprovalPayoutTriggerRealStackIntegrationTest` 那條『核准後無 transfer』；`TransferControllerE2ETest` 把 `TransferService` 整個 mock 掉。 |
| 真實入口 | webhook：`PaymentWebhookService.handleEvent(String payload)`（`@Transactional`、公開，payload 於 controller 驗簽之後才進來；測試直接餵 JSON，含事件去重）；逆轉：`POST /v2/admin/settlements/{id}/reverse/initiate`、`.../reverse/confirm`（`settlement:reverse`，`SUPER_ADMIN` 持有全部權限、`CFO` 持有它）；重試：`POST /v2/admin/transfers/{statementId}/retry`（`admin:write`）。 |

## 2. 做法（開工前的設計，結果見後面各節）

1. 新增真實棧競態類別 `SettlementPostApprovalRaceRealStackIntegrationTest`（核准之後：撥款重試、webhook、逆轉的併發），編排沿用 `SettlementReviewRaceRealStackIntegrationTest`（對手交易 `FOR UPDATE` 持鎖、`pg_stat_activity` 確認請求卡住、先到先寫）。**不改既有類別**：既有 18 個案例與它們的突變驗證結果保持不動（Rule 3）；代價是複製約 150 行編排輔助（先例：每個競態類別各帶自己的輔助）。
2. 紅燈先在**未修改的生產碼**上跑；預測見 §3.1。
3. 紅燈屬實的才修，修法比照 `DEF-442`～`DEF-446`（條件式更新搶占＋之後不再有 setter／`refresh`）；`DEF-448` 另用一次性探針量測（不入庫），因為它的修法尚未決定，沒有『正確行為』可寫成會綠的測試。

## 3. 修改前的紅燈（未修改的程式碼、真實 PostgreSQL）

### 3.1 預測（跑之前登記，來源是讀碼；尚未執行任何新測試）

新類別 `SettlementPostApprovalRaceRealStackIntegrationTest` 的 9 個案例。種子：店鋪結算單淨額 `900.00`；逆轉用 `SUPER_ADMIN` 發起、兩位 `CFO` 確認（雙重授權要求角色不同）；撥款重試的店鋪設成可撥款、閘道 `@MockBean` 回成功。**失敗訊息的方向也先登記**（Sprint 318 的做法）。

| 案例 | 預測（未修改） | 預測的違反項目 |
|---|---|---|
| **W0 對照：單獨的 `transfer.reversed` webhook**（`PAID` 結算單、`COMPLETED` transfer，沒有逆轉） | 綠——transfer `REVERSED`、結算單 `FAILED` | — |
| **W1 對照（不含併發）：先發起逆轉、webhook 後到** | 綠——webhook 讀到 `REVERSAL_PENDING` 就略過：結算單維持 `REVERSAL_PENDING`、發起人角色與原因都在；transfer `REVERSED`（Stripe 的事實照記） | — |
| **W2 發起先寫、webhook 後寫**（對手交易持鎖；發起請求先卡在搶占，webhook 〔已讀到 `PAID`〕後卡在整列寫回） | **紅**——發起 `200`（回應顯示 `REVERSAL_PENDING`）；放行後發起先提交，webhook 的整列寫回用舊值蓋掉：結算單變 `FAILED`，**連逆轉的發起人、角色、時間、原因四個欄位都被清成 null**；transfer `REVERSED`（這項通過） | 3 項：最終狀態（預期 `REVERSAL_PENDING`、實際 `FAILED`）、發起人角色（預期 `SUPER_ADMIN`、實際 null）、逆轉原因（預期寫入的文字、實際 null） |
| **W3 webhook 先寫、發起後到**（方向相反） | 綠——webhook 先提交 `FAILED`，發起的搶占（`WHERE status='PAID'`）重新評估後回 0 列：`409 E-5014`；結算單 `FAILED`；transfer `REVERSED` | — |
| **C0 對照（不含併發）：循序確認兩次** | 綠——第一次 `200`（`REVERSED`、一張信用票據），第二次被狀態檢查擋下 `409 E-5014` | — |
| **C1 確認 vs 確認，有同形唯一索引**（`UNIQUE (tenant_id, credit_note_number)`，模擬正式環境的 `V36`；兩位 CFO 同時確認同一張結算單） | **紅**——兩個請求都通過狀態檢查、產生相同編號；先寫的提交，後到者的 `INSERT credit_notes` 撞唯一約束：後到者 **`500`／`E-9900`**（不是 `409 E-5014`）；結算單 `REVERSED`、**只有一張**信用票據（約束偶然擋住了重複，這項通過） | 2 項：後到者的 HTTP 狀態（預期 `409`、實際 `500`）、錯誤碼（預期 `E-5014`、實際 `E-9900`） |
| **C2 確認 vs 確認，沒有索引**（模擬『編號格式改成每張結算單唯一之後』，也就是 Hibernate 建的整合測試庫的預設） | **紅**——沒有約束擋著，**兩個確認都成功**：後到者 `200`；結算單 `REVERSED`；**兩張**信用票據（沖銷了兩次）、編號相同 | 3 項：後到者的 HTTP 狀態（預期 `409`、實際 `200`）、錯誤碼（預期 `E-5014`、實際空字串）、信用票據張數（預期 1、實際 2） |
| **T0 對照（不含併發）：循序重試兩次**（`FAILED` 結算單＋`FAILED` transfer，店鋪可撥款、閘道成功） | 綠——第一次 `200`（結算單 `PAID`、transfer `COMPLETED`、閘道呼叫 1 次），第二次 `409 E-5014`（`COMPLETED` 的 transfer 不可重試） | — |
| **T1 重試 vs 重試**（同上的種子；兩位管理員同時重試） | **紅**——兩個請求都讀到 `FAILED` 的 transfer、都打算刪除它；先寫的完成撥款並提交，後到者的 `DELETE` 刪不到列（已被刪除）→ Hibernate 的列數檢查丟例外 → 後到者 **`500`／`E-9900`**，整個交易回滾；閘道只被呼叫 **1 次**、結算單 `PAID`、只有一筆 `COMPLETED` 的 transfer（狀態是安全的，這三項通過） | 2 項：後到者的 HTTP 狀態（預期 `409`、實際 `500`）、錯誤碼（預期 `E-5014`、實際 `E-9900`） |

**預測的總計**：9 案例中 4 紅（W2、C1、C2、T1）、5 綠；違反項目共 10（3＋2＋3＋2）。**不確定的地方**（跑完要特別核對）：W2 的『整列寫回清掉四個逆轉欄位』與 T1 的『500 來自 DELETE 的列數檢查』兩個機制是推論；T1 後到者卡住的語句（`UPDATE settlement_statements` 或 `DELETE FROM transfers`）取決於 Hibernate 的 flush 順序（我預期更新先於刪除），觀察用的 `pg_stat_activity` 過濾條件因此涵蓋 `settlement_statement`、`credit_notes`、`transfers` 三種語句。

**`DEF-448` 一次性探針的預測**（不入庫）：同一租戶、同一天，兩張 `PAID` 結算單各自『發起→確認』。有同形唯一索引時：第一張 `200`、信用票據編號恰為 `CN-{租戶 id 前 8 碼}-{今天 yyyyMMdd}`；**第二張的確認 `500`／`E-9900`**，第二張結算單回滾後維持 `REVERSAL_PENDING`、沒有第二張信用票據。沒有索引時（Hibernate 預設）：兩張都 `200`、**兩張信用票據編號完全相同**。

### 3.2 實測結果（未修改的生產碼，真實 PostgreSQL）

新類別 9 案例跑、**4 紅 5 綠**（Failures 4、Errors 0）。**逐格符合預測**（紅的正是 W2、C1、C2、T1；違反項目 3＋2＋3＋2＝10，沒有預測落空）：

| 案例 | 實測 | 與預測 |
|---|---|---|
| W0 對照：單獨的 webhook | 綠 | 相符 |
| W1 對照：先發起逆轉、webhook 後到 | 綠——結算單維持 `REVERSAL_PENDING`、發起人角色與原因都在、transfer `REVERSED` | 相符 |
| **W2 發起先寫、webhook 後寫** | **3 項違反**：最終狀態預期 `REVERSAL_PENDING` 實際 `FAILED`；發起人角色預期 `SUPER_ADMIN` 實際 `null`；逆轉原因預期『對帳發現重複撥款』實際 `null`。失敗訊息裡的狀態列同時顯示 `initiatedBy=null`、`requestedAt=null`——**逆轉的四個欄位（發起人、角色、時間、原因）全被 webhook 的整列寫回清成 null**。發起請求本身回 `200`、transfer 記為 `REVERSED`（這兩項通過） | 相符（含『整列寫回清掉四個欄位』這個標為推論的機制，現在是量到的事實） |
| W3 webhook 先寫、發起後到 | 綠——發起得到 `409 E-5014`、結算單維持 `FAILED`、沒有殘留的逆轉欄位 | 相符 |
| C0 對照：循序確認兩次 | 綠 | 相符 |
| **C1 確認 vs 確認，有同形唯一索引** | **2 項違反**：後到者 HTTP 預期 `409` 實際 `500`；錯誤碼預期 `E-5014` 實際 `E-9900`。結算單 `REVERSED`、**只有一張**信用票據（通過——約束偶然擋住了重複）。日誌：`DataIntegrityViolationException: … duplicate key value violates unique constraint "idx_it_post_race_credit_notes_…"` | 相符 |
| **C2 確認 vs 確認，沒有索引** | **3 項違反**：後到者回應是**成功**（`success: true`、`status: REVERSED`，HTTP `200`）；錯誤碼空字串；信用票據**兩張**，編號完全相同（`["CN-eda85080-20261102", "CN-eda85080-20261102"]`）——同一張逆轉被沖銷兩次 | 相符 |
| T0 對照：循序重試兩次 | 綠 | 相符 |
| **T1 重試 vs 重試** | **2 項違反**：後到者 HTTP 預期 `409` 實際 `500`；錯誤碼預期 `E-5014` 實際 `E-9900`。閘道只被呼叫 **1 次**、結算單 `PAID`、只有一筆 `COMPLETED` 的 transfer（這三項通過——狀態是安全的）。日誌：`ObjectOptimisticLockingFailureException: Row was updated or deleted by another transaction … [Transfer#…]`，丟在 `TransferService.createTransferForStatement:75`（`findBySettlementStatementId` 觸發的自動 flush）←`retryFailedTransfer:185` | 相符（『500 來自刪除舊 transfer 時列數檢查失敗』現在是量到的事實） |

**兩件要誠實說的事**：(1) 預測裡 T1 後到者卡住的語句（`UPDATE settlement_statements` 或 `DELETE FROM transfers`）我沒有逐一記錄是哪一個——只確認兩個請求在放行前都被 `pg_stat_activity` 觀察到卡在鎖上；500 的堆疊指向 flush（第 75 行），與『更新先於刪除』的預期相容，但**卡住的是哪一句**這件事沒有量。(2) C1／C2 的對照說明了一件重要的事：**今天『同一張逆轉不會被沖銷兩次』完全靠 `V36` 的唯一約束（而編號又恰好重複）偶然擋住**；C2（沒有約束）顯示一旦約束消失——例如 `DEF-448` 把編號改成每張結算單唯一——同一張逆轉會被沖銷兩次。所以 `confirmReversal` 的搶占是 `DEF-448` 修法的**前置條件**，不是順手。

### 3.3 `DEF-448` 一次性探針的實測結果（不入庫）

探針暫放進測試目錄跑完即刪（原始碼、`target` 下的 class 與報告都已移除，`git status` 只剩本輪預定的檔案；新測試類別的 SHA-256 與探針前一致）。同一租戶、同一天，兩張 `PAID` 結算單各自『`SUPER_ADMIN` 發起→`CFO` 確認』：

| 條件 | 第一張 | 第二張 | 信用票據 |
|---|---|---|---|
| **有同形唯一索引**（模擬 `V36`） | 發起 `200`、確認 `200`、`REVERSED` | 發起 `200`、**確認 `500`／`E-9900`（「發生未預期的錯誤」）**，結算單回滾後維持 `REVERSAL_PENDING` | 只有一張，編號 `CN-33fc26b4-20261102`＝預期的 `CN-{租戶 id 前 8 碼}-{今天 yyyyMMdd}` |
| 沒有索引（Hibernate 預設） | 同上 | 發起 `200`、確認 `200`、`REVERSED` | 兩張，**編號完全相同**（`CN-6e34d126-20261102` ×2） |

**與預測逐項相符**（沒有預測落空）。`DEF-448` 從『讀碼＋schema 推論』升級為**量到的事實**：同一租戶同一天的第二張逆轉**確認不了**，回的是 `500`（不是任何業務錯誤碼），而且要等隔天才有可能成功。這是**確定性缺陷，不是競態**。限制：這裡的唯一索引是『與 `V36` 同形』的模擬，不是 Flyway 建出的真實約束（語意相同：`UNIQUE (tenant_id, credit_note_number)`）；沒有在 Flyway 建出的資料庫上實測。

**結論：本輪只量測、不修 `DEF-448`**——編號格式屬產品／會計決策，收尾用互動選項問使用者（§9）。

## 4. 修改後與突變驗證

### 4.1 修改（三處，比照 `DEF-442`～`DEF-446`：條件式更新搶占，搶輸的人乾淨失敗）

1. **webhook**（`TransferService.handleTransferReversedWebhook`）：原本 `findById` → 讀到 `PAID` 才 `setStatus(FAILED)` → `save`（整列寫回）；改成 `updateStatusIfCurrent(statementId, PAID, FAILED)`，不載入結算單實體，因此沒有整列寫回。搶輸（0 筆）只記 info 日誌（為保留『已在什麼狀態』這個資訊，用 `findStatusById` 重讀一次，只在這條路徑才多一個查詢）；**transfer 的 `REVERSED` 照記**（Stripe 的事實不因結算單的流程而改變）。
2. **確認逆轉**（`SettlementReversalService.confirmReversal`）：在前置檢查與載入確認人之後、產生信用票據**之前**，`updateStatusIfCurrent(REVERSAL_PENDING → REVERSED)`；搶輸得 `409 E-5014`（與循序的第二次確認同一個結果），不產生信用票據。搶占的 `UPDATE` 已把狀態寫完，之後**不再 `setStatus`／`save`**，回應的狀態補在 DTO 上。**沒有 `refresh`**——這與 Sprint 321／322 的批准、駁回、提交不同，理由見 §8 第 3 點。
3. **重試**（`TransferService.retryFailedTransfer` ＋ `TransferRepository.deleteByIdAndStatusIn`）：舊 transfer 記錄原本用實體層級的 `delete`；改成條件式刪除 `DELETE FROM Transfer t WHERE t.id = :id AND t.status IN :statuses` 當作搶占，回傳 0 → `409 E-5014`。『可重試的狀態』抽成一個常數，前置檢查與條件式刪除共用同一份。搶占發生在寫結算單**之前**，所以輸家不碰結算單、也走不到 Stripe。

### 4.2 單元與真實棧

- 單元：`TransferServiceTest` 19 → **23**（webhook：委派給條件式更新且不載入／不 `save` 結算單實體、搶輸不覆寫、結算單不存在不丟例外；重試：搶輸乾淨失敗、`SKIPPED` 的 transfer 也可重試且條件式刪除涵蓋兩種狀態、搶占先於寫結算單）、`SettlementReversalServiceTest` 11 → **12**（先搶占再產生信用票據、搶輸不產生信用票據、實體保持乾淨、回應狀態是 `REVERSED`）。`M07SettlementIntegrationTest` 的 `IT-M07-S-012`（真實 service ＋ `@MockBean` repository）補上對新搶占呼叫的 stub——與 Sprint 137 為 `DEF-138` 在 `IT-M07-S-011` 補的是同一個道理：Mockito 對未 stub 的 `int` 回 0 會被當成搶占失敗。
- 真實棧（`SettlementPostApprovalRaceRealStackIntegrationTest`）：**先只修 webhook**，9 案例 **3 紅**（C1 2 項、C2 3 項、T1 2 項違反，項數與修復前相同）——W2 轉綠、其餘三個沒有動，三個修復彼此獨立；**三個都修完**：**9 案例 0 失敗**（46 秒）。另外新增了對『確認逆轉的回應狀態必須是 `REVERSED`』的斷言（C0／C1／C2 的贏家）——這個回應狀態是補在 DTO 上的，沒有它，單元以外沒有任何東西會紅。

### 4.3 突變驗證（預先登記：規格檔先寫好 `must`，跑完逐一比對）

驅動沿用 Sprint 322 的 `bmut.py`（從前一個 session 的暫存目錄複製）；規格檔 `mkspec_s323.py` 產兩份，開跑前乾跑『每個 `old` 恰好出現一次』全數通過，被突變的兩個主檔事先記錄 SHA-256。兩組各自的基準 Z0 在全新資料庫上跑。判決標準：失敗集合與登記的 `must` **恰好**吻合才算 `KILLED(exactly must)`。

縮寫：競態 W0～W3、C0～C2、T0～T1 見 §3.1；**TW1**＝`handleTransferReversedWebhook_existingTransfer_marksReversedAndFailsStatement`、**TW2**＝`…_statementInReversalPending_doesNotOverrideStatus`、**TW3**＝`…_statementNotFound_doesNotThrow`；**TR1**＝`retryFailedTransfer_superAdmin_bypassesTenantCheck`、**TR2**＝`…_notFailedStatus_throws`、**TR3**＝`…_concurrentRetryClaimedTheTransfer_throwsE5014WithoutSideEffects`、**TR4**＝`…_skippedTransfer_isRetryable_claimCoversBothRetryableStatuses`、**TR5**＝`…_claimsTheOldTransferBeforeWritingTheStatement`；**RC1**＝`confirmReversal_differentRole_succeeds`、**RC2**＝`confirmReversal_concurrentClaimLost_throwsE5014WithoutCreditNote`；**M07-012**＝`IT-M07-S-012`。

**A 組**（`TransferService`）：單元 `TransferServiceTest`（23）＋真實棧（9），共 32 案例。

| 組 | 突變 | 登記的恰好該紅 |
|---|---|---|
| Z0 | 無突變（基準，全新資料庫） | 無（32 案例全綠） |
| WM1 | webhook 回到舊寫法（讀 → `setStatus(FAILED)` → `save`） | TW1、TW2、TW3、W2（4） |
| WM2 | webhook 搶占的預期狀態寫成 `REVERSAL_PENDING` | TW1、TW2、TW3、W0、W1、W2、W3（7）。W2、W3 預期紅在『等不到 N 個請求卡在鎖上』逾時：條件對快照上的 `PAID` 列不成立，PostgreSQL 不去等鎖，webhook 立刻結束 |
| WM3 | webhook 搶輸（0 筆）時丟例外 | TW2、TW3、W1、W2（4） |
| WM4 | webhook 不再把 transfer 記為 `REVERSED` | TW1、TW2、TW3、W0、W1、W2、W3（7） |
| RM1 | 重試回到實體層級的 `delete(existing)`（本輪修的缺陷本身） | TR1、TR3、TR4、TR5、T1（5） |
| RM2 | 重試的搶占結果被忽略 | TR3、T1（2）。T1 預期紅在輸家回 `200`、且結算單 `PAID` 被輸家的整列寫回蓋成 `APPROVED`（訊息 `but was: "APPROVED"`） |
| RM3 | 『可重試的狀態集合』只留 `FAILED` | TR4（1）。真實棧沒有 `SKIPPED` 重試案例，只有單元守得住 |
| RM4 | 搶占挪到寫結算單**之後** | TR3、TR5（2）。**真實棧 T1 預期仍綠**：輸家的交易整個回滾，連同它先寫的結算單一起撤銷 |
| RM5 | 拿掉重試的前置狀態檢查 | TR2（1）。外部行為等價：條件式刪除對 `COMPLETED` 也回 0，只有單元的『不嘗試搶占』守得住（Sprint 245 同款） |

**B 組**（`confirmReversal`）：單元 `SettlementReversalServiceTest`（12）＋真實棧（9）＋`M07SettlementIntegrationTest`（14），共 35 案例。

| 組 | 突變 | 登記的恰好該紅 |
|---|---|---|
| Z0 | 無突變（基準，全新資料庫） | 無（35 案例全綠） |
| CM1 | 確認回到舊寫法（無搶占、`setStatus`＋`save`） | RC1、RC2、C1、C2（4） |
| CM2 | 搶占結果被忽略 | RC2、C1、C2（3） |
| CM3 | 搶占的預期狀態寫成 `PAID` | RC1、RC2、C0、C1、C2、M07-012（6）。C1、C2 預期紅在請求不卡鎖的逾時 |
| CM4 | 搶占挪到信用票據**之後** | RC1、RC2、C1（3）。C1 被擊殺是因為自動 flush 會先把 `INSERT credit_notes` 送出去撞唯一索引；**C2 預期仍綠**（輸家交易整個回滾，連同它的信用票據一起撤銷） |
| CM5 | 搶占之後加回 `setStatus`＋`save` | RC1（1）。外部行為等價（同一個值），只有單元的『實體保持乾淨』守得住 |
| CM6 | 不再把回應狀態補成 `REVERSED` | RC1、C0、C1、C2（4）（訊息 `but was: "REVERSAL_PENDING"`） |

（結果見下方 §4.4。）

### 4.4 結果

兩組都先跑基準 Z0（全新資料庫）：A 組 **32 案例 0 失敗**（單元 23＋真實棧 9）、B 組 **35 案例 0 失敗**（單元 12＋真實棧 9＋M07 整合 14；**登記時我寫 36**，是 `grep -c '@Test'` 把 `@TestMethodOrder` 也算進去，實跑 `ran=35`，見 §6 第 1 點）。15 組突變的失敗集合**全部與登記的 `must` 恰好吻合**（驅動的 `restored=True` 17 次〔含兩個基準〕；沒有 `INVALID`、沒有 `MSG-MISMATCH`）；失敗數與登記數逐組相同：WM1 4、WM2 7、WM3 4、WM4 7、RM1 5、RM2 2、RM3 1、RM4 2、RM5 1、CM1 4、CM2 3、CM3 6、CM4 3、CM5 1、CM6 4。兩組結束後，被突變的 `TransferService.java`、`SettlementReversalService.java` 與其餘工作檔的 SHA-256 與事先記錄逐一相同，沒有突變殘留字串。以下是讀失敗訊息確認的歸因：

1. **預期狀態寫成不可能的值（WM2、CM3）時請求從不卡住**：`UPDATE … WHERE status = 'REVERSAL_PENDING'` 對快照上是 `PAID` 的列不符合條件，PostgreSQL 不去等鎖就直接回 0 筆，webhook／確認立刻結束。真實棧的競態案例因此被『等不到 N 個請求卡在鎖上（目前 0 個）』逾時擊殺（WM2 的 W2、W3 訊息逐字如此，`must_msg` 吻合；『目前 0 個』是對手交易的 30 秒持鎖上限到期、自動放行了——`HeldTransaction` 的程式碼如此）。擊殺有效，但歸因是逾時、不是狀態斷言——與 Sprint 320 的 M5／M6 同一個現象。
2. **RM2（重試的搶占結果被忽略）實證了『忽略結果』的真實後果**：T1 的輸家回 **`200`**、錯誤碼空，而且結算單最終是 `APPROVED`（預期 `PAID`）、`transfer` 卻是贏家的 `COMPLETED`（失敗訊息裡的狀態列：`status=APPROVED … transferStatuses=[COMPLETED]`）——輸家的整列寫回把贏家剛撥款完成的結算單蓋回 `APPROVED`。這與 `TransferService.claimTransferSlot` 的註解（Sprint 319／320 寫的）是同一件事：輸家若『成功』並提交，會把贏家已推進到 `PAID` 的結算單覆寫回 `APPROVED`，讓輸家乾淨失敗才安全。所以搶占的**回傳值檢查**才是這個修法的核心，不是 UPDATE／DELETE 本身。
3. **WM3（webhook 搶輸時丟例外）**：W1、W2 的訊息是 `webhook 處理結果 expected: "ok" but was: "EXCEPTION java.lang.IllegalStateException: mutated: webhook lost the claim"`；W2 另外因為 webhook 的交易整個回滾，`transfer` 停在 `COMPLETED`（預期 `REVERSED`）。`PaymentWebhookService.handleEvent` 的註解寫明『任何情況皆不拋例外（controller 回 2xx 避免 Stripe 無限重送）』，所以『搶輸只記錄』是修法的一部分。
4. **三個外部等價的突變（RM4、RM5、CM5）與一個部分等價的突變（CM4），只有單元的順序／乾淨斷言守得住**：RM4（重試的搶占挪到寫結算單之後）真實棧 T1 仍綠——輸家的交易整個回滾，連同它先寫的結算單一起撤銷；CM4（確認逆轉的搶占挪到信用票據之後）C1（有唯一索引）被擊殺——該輪日誌恰好 1 次 `Unexpected error`，是輸家撞唯一索引的 `DataIntegrityViolationException`（機制推論：搶占的 `updateStatusIfCurrent` 帶 `flushAutomatically`，會先把待寫的 `INSERT credit_notes` 送出去）——但 C2（沒有索引）仍綠，理由同上；RM5（拿掉重試的前置狀態檢查）外部等價——條件式刪除對 `COMPLETED` 也回 0；CM5（搶占之後加回 `setStatus`＋`save`）外部等價——同一個值。RM4、RM5、CM5 的擊殺者只有單元案例（`claimsTheOldTransferBeforeWritingTheStatement` 與 `concurrentRetryClaimedTheTransfer…`、`notFailedStatus_throws` 的 `never().deleteByIdAndStatusIn`、`confirmReversal_differentRole_succeeds` 的實體保持乾淨／`never().save`）；CM4 在 C1 由真實棧擊殺、在 C2 只有單元案例（`InOrder`、搶輸不產生信用票據）守住。
5. **RM3（可重試的狀態集合只留 `FAILED`）只被單元案例殺掉**：真實棧沒有 `SKIPPED` 重試案例（§7 第 2 點）。
6. **CM6（不再把回應狀態補成 `REVERSED`）被真實棧擊殺**，訊息 `but was: "REVERSAL_PENDING"`：C0／C1／C2 贏家的回應狀態斷言就是為了守住這個『補在 DTO 上』的行為而新增的（實體在搶占後沒有 setter，狀態是舊的）。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**27 分 04 秒**；單元 **2469／0 失敗／0 錯誤／0 跳過**（Sprint 322 的 2464＋`TransferServiceTest` +4、`SettlementReversalServiceTest` +1）；整合 **1014／0／0／0**（1005＋新類別 9）；Checkstyle main／test 各 0 違規；日誌 `<<< FAILURE`／`<<< ERROR`／`Unresolved compilation`／`ClassFormatError`／`NoClassDefFoundError` 各 0 個。新增／變動的類別在 verify 內確實執行：`SettlementPostApprovalRaceRealStackIntegrationTest` 9、`TransferServiceTest` 23、`SettlementReversalServiceTest` 12、`M07SettlementIntegrationTest` 14、`SettlementReviewRaceRealStackIntegrationTest` 18（既有，未動）。（verify 之後只多了一處註解：`TransferServiceTest.arrangeRetryOf` 補 `@SuppressWarnings("unchecked")`，消掉 IDE 的泛型陣列警告；補完後 `TransferServiceTest` 23／0 重跑通過。） |
| 紅燈先行（§3） | 未修改的程式碼上新類別 9 案例 4 紅 5 綠、10 項違反，預測逐格相符；沒有預測落空。`DEF-448` 探針兩個條件的結果也與預測逐項相符。 |
| 突變驗證（§4.3～§4.4） | 兩組基準 Z0（32／0、35／0）＋15 組突變，**全部 `KILLED(exactly must)`**，預先登記逐一吻合。 |
| 穩定性 | 新真實棧類別（9 案例）在全量 verify 之後連跑 3 次（每次重新啟動 Spring context），每次 9 案例 0 失敗。 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 在**所有文件定稿後**執行：4 個類別共 **14 個案例，0 失敗**（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1），`BUILD SUCCESS`。 |
| IDE Problems | 我改動的 7 個檔案（`TransferService`、`SettlementReversalService`、`TransferRepository`、`TransferServiceTest`、`SettlementReversalServiceTest`、`M07SettlementIntegrationTest`、`SettlementPostApprovalRaceRealStackIntegrationTest`）診斷 **0**。`getDiagnostics` 全域清單中唯一有內容的一筆，是 `TransferServiceTest` 新增輔助方法缺 `@SuppressWarnings("unchecked")` 的泛型陣列警告（已補上，再次確認為空）；其餘條目是 VS Code 在突變期間留下的 `.tmp.NNNN` 暫存項目與已刪除的探針檔——磁碟上 `*.tmp.*` 與 `Def448Probe*` 都是 0 個（用 `find` 核對）。 |
| 使用者回報的 CI 失敗（依賴安全審計 #7） | 已修並在雲端驗證，見 §10。 |
| 沒有執行的項目 | Playwright 完整 E2E 與 `make validate-schema-doc`：本輪 Sprint 323 的 commit 沒有前端、Flyway 遷移或 schema 變更（依賴修復是另一個 commit，只改 lockfile，`npm ci`／lint／build 由 pre-push 守門與雲端 CI 的前端 job 驗證）。 |

## 6. 過程中的失誤與修正

1. **三個計數寫錯**：(a) 草稿寫『`settlement_statements` 的寫入點只有 11 個』——沒有依據，commit 前自審時重跑 grep：**14 個呼叫點、10 個方法**，計畫書與 API 文件都改了；(b) 我一度以為 `M07SettlementIntegrationTest` 有 15 個案例，並**把『S322 寫的 14 不對』講給了使用者聽**——`grep -c '@Test'` 把 `@TestMethodOrder` 也算進去，真案例 14 個，S322 的 14 才是對的；(c) B 組基準的預先登記因此多寫了一個（36，實跑 35）。教訓：計數用執行結果（驅動輸出的 `ran=`、surefire XML）或精確 regex，**『更正』別人的數字前先用第二種方法再數一次**。
2. **隔離假設錯誤**：使用者回報依賴審計失敗後，我為了不讓 pre-push 守門驗到做到一半的 Sprint 323 工作，開了獨立 git worktree 從那裡 push。守門仍失敗在後端單元——`Makefile` 把 `act` 的目錄寫死成主倉庫（第 153、174 行），驗的是主工作目錄，被我當時的 WIP（新版 `confirmReversal` 配舊版單元測試）擋下，白費約 13 分鐘。改成備份＋雜湊→`git stash -u`→cherry-pick→push→`git stash pop`→逐檔核對。登記 `DEF-449`。
3. **`npm audit fix` 過度**：整批修復夾帶開發工具鏈升級（`@typescript-eslint` 8.58→8.72 等）與約 19 個套件的 `resolved`／`integrity` 改寫；用 python 逐套件比對發現後還原，改 `npm update follow-redirects --package-lock-only`（3 行）。
4. **zsh 的 `rm -f 目錄/*Pattern*` 在沒有符合檔案時整條 `&&` 鏈中止**（記憶檔 Sprint 320 記過），第一次「紅燈」其實 Maven 根本沒跑（背景任務回 exit 1、`red1.log` 不存在才發現），去掉 `rm` 重跑。
5. **macOS 的 `strings` 對 `.class` 檔報『fat file truncated』**（`CAFEBABE` 與 Mach-O fat 魔數相同），所以我第一次『Unresolved 標記數 0』的檢查沒有任何證明力；改用 `grep -a` ＋ `javap -p -c` 查位元組碼。
6. **`Nothing to compile - all classes are up to date`**（IDE 搶先編譯）出現多次：刪 class 逼 Maven 重編，並用 `javap`／案例數確認；全量驗證前做了一次 `clean test-compile`。
7. **工作目錄漂移**（Bash 的 `cd` 讓 cwd 數次改變），一律絕對路徑與 `mvn -f`。
8. **`M07SettlementIntegrationTest` 的 `IT-M07-S-012` 差點被 F2 弄壞**：真實 service ＋ `@MockBean` repository，新搶占呼叫回 0（→409）且 `refresh` 對未被管理的實體會丟例外；先唯讀盤點才發現，導致『確認逆轉不做 `refresh`』這個設計決定（§8 第 3 點）與 stub 補丁。
9. **開發-編譯-測試循環**：三個修復都先單獨編譯（編譯結果以 `javap`／案例數確認）、再跑單元測試；真實棧在只修完 webhook 時跑了一次（3 紅 6 綠），F2＋F3 都做完後再跑一次（9 案例 0 失敗）。沒有累積多支程式才編譯的偏差（與 Sprint 322 的 `DEF-445` 不同）。

## 7. 限制（誠實揭露）

1. **`DEF-448` 的量測用的是『與 `V36` 同形』的唯一索引**，不是 Flyway 建出的真實約束（語意相同：`UNIQUE (tenant_id, credit_note_number)`）；沒有在 Flyway 建出的資料庫上實測。
2. **真實棧沒有 `SKIPPED` 的重試案例**：重試的案例都用 `FAILED` 的 transfer；『`SKIPPED` 也可重試』只有單元守門（RM3 只被單元殺掉）。
3. **閘道是 `@MockBean`**：重試案例的撥款走 mock 的成功回應，沒有對真實 Stripe 驗證（承 Sprint 80 起的既有限制）；`transfer.reversed` webhook 的測試直接呼叫 `PaymentWebhookService.handleEvent`（payload 在 controller 驗簽之後才進來），沒有走簽章驗證。
4. **③ `initiateReversal` 搶占後沒有 `refresh`、④ `createTransferForStatement` 的整列寫回**：依寫入者盤點，這兩個狀態（`PAID`、`APPROVED`）上除了 webhook 之外沒有可達的並行寫入者，單獨存在時沒有可量測的失敗——**這是讀碼整理，沒有另外執行**；若日後有人在這兩個狀態上加了新的原子更新，要重新評估。
5. **每個案例只確定一個交錯**：鎖序由 `pg_stat_activity` 確認，沒有窮舉更細的時序；T1 後到者卡住的是 `UPDATE settlement_statements` 還是 `DELETE FROM transfers` 在修復前沒有逐一記錄。
6. **webhook 與確認逆轉不再推進結算單的 `updated_at`**（JPQL `UPDATE` 繞過 `@PreUpdate`）：精確 grep——14 個引用 `SettlementStatement` 的生產檔案裡，`updatedAt` 只出現在 `Transfer`／`Order` 自己的欄位與實體本身的宣告及 `@PrePersist`／`@PreUpdate`，沒有任何地方讀結算單的 `updatedAt`；回應 DTO 與前端 settlement 相關檔案不含它；沒有以 `updated_at` 排序或篩選這張表的 SQL。
7. **三個外部等價的突變（RM4、RM5、CM5）與部分等價的 CM4（C2 仍綠）只有單元守門**（§4.4 第 4 點）。
8. **這一輪的 Sprint 323 commit 沒有前端、schema 變更**，沒有跑 Playwright 完整 E2E 與 `validate-schema-doc`；依賴修復的 lockfile commit 另行驗證（§10）。

## 8. 我替使用者做的決定（可一句話推翻）

1. 『請繼續完成任務！』（無 `==>`）＝依 Sprint 322 總結自己寫的『下次說「請繼續」，我可以直接做 `DEF-447` 和 `DEF-448` 的量測』，只做這兩項的量測；**`DEF-447` 屬實才修**（三條都修了），**`DEF-448` 只量測不修**（總結明寫編號格式要使用者拍板）。不碰 `DEF-419`、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取，也不做兩個『可選量測』。
2. **使用者中途貼來的 CI 失敗（依賴安全審計 #7）優先處理**，並與 Sprint 323 分成兩個 commit：先修、先 push（`959dbeca`），Sprint 323 的 commit 另行。
3. **`confirmReversal` 搶占後不做 `refresh`，也不再 `setStatus`／`save`，回應狀態補在 DTO 上**——與 Sprint 321／322 的批准、駁回、提交不同（那三處都 `refresh`）。理由：`REVERSAL_PENDING` 的內容是凍結的（金額在 `DEF-446` 之後只在 `PENDING`／`PENDING_REVIEW` 變動，逆轉欄位只在 `PAID → REVERSAL_PENDING` 時寫一次），沒有可達的並行寫入者，`refresh` 只是衛生；讓實體保持乾淨（沒有 setter）本身就避免整列寫回；`IT-M07-S-012` 用真實 service 搭未被管理的實體，`refresh` 會丟例外。若要與姊妹方法一致：加 `refresh` 並調整 `IT-M07-S-012`。
4. **重試用『條件式刪除舊 transfer』當搶占**，而不是對結算單做狀態 CAS：條件式刪除涵蓋 `FAILED` 與 `SKIPPED_ONBOARDING_INCOMPLETE` 兩種可重試狀態（推論：結算單 CAS 在『`APPROVED` 結算單＋`SKIPPED` transfer』兩個重試都以 `SKIPPED` 收場時分不出勝負，沒有實測），並且讓鎖序變成『先 transfer、後結算單』，與撥款、webhook 一致。
5. **新案例放在新類別**（`SettlementPostApprovalRaceRealStackIntegrationTest`），沒有擴充既有 18 案例的 `SettlementReviewRaceRealStackIntegrationTest`：保持既有案例與它們的突變驗證結果不動（Rule 3）；代價是複製約 150 行編排輔助（先例：每個競態類別各帶自己的輔助）。兩個類別的註解組合與 `@MockBean` 相同，Spring 應會共用快取的 context（推論，沒有逐一核對啟動次數）。
6. **兩個可見行為變更**：同時確認／同時重試的輸家從 `500` 變成 `409 E-5014`；webhook 與確認逆轉不再推進結算單的 `updated_at`（§7 第 6 點）。
7. **依賴修復用針對單一套件的 `npm update follow-redirects --package-lock-only`**，不用整批 `npm audit fix`（§6 第 3 點）。
8. **不改 `Makefile`／守門**：`DEF-449` 只登記（動守門是基礎設施變更）。
9. **`DEF-448` 的編號格式用互動選項問使用者**：收尾提出四個選項（A 沿用結算單編號〔我的建議〕／B 開立日＋結算單碼／C 開立日＋當日流水號／先不改）。**使用者選了 C**，實作見 [SPRINT_324_PLAN.md](SPRINT_324_PLAN.md)。

## 9. 新登記與更新的缺陷

- **`DEF-447`**（🟢→✅）：已量測、已修，見追蹤表。新發現一個原登記沒有的子項（確認逆轉沒有搶占），並證明它是 `DEF-448` 修法的前置條件。
- **`DEF-448`**（🟢，**已量測、尚未修**）：同一租戶同一天的第二張逆轉確認 `500 E-9900`（確定性缺陷）；修法待使用者決定編號格式。收尾以互動選項詢問使用者，**使用者選了 C**（`CN-{租戶 id 前 8 碼}-{開立日}-{當日流水號}`），由 [Sprint 324](SPRINT_324_PLAN.md) 實作。
- **`DEF-449`**（🟢，**新登記，只登記不修**）：`make validate-fast`／`validate-all` 一律驗主倉庫目錄（`Makefile` 第 153、174 行寫死絕對路徑），不是正在 push 的那棵樹；有未提交的在途工作時無關的 push 會被擋下，從別的 worktree push 也無法隔離。建議把路徑改成 `$(CURDIR)`，或讓守門驗 HEAD 的匯出副本。目前的繞法：push 前備份＋`git stash -u`。

## 10. 狀態

**使用者回報的 CI 失敗（依賴安全審計 #7，已修並在雲端驗證）**：排程 run [41406412925](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/41406412925)（2026-11-02 07:27Z）因 `follow-redirects 1.16.1`（`axios` 的傳遞相依，high，`GHSA-mrwp-f3q8-3g9p`）exit 1。修復 commit `959dbeca`（`frontend/package-lock.json` 三行，`follow-redirects → 1.16.2`；整批 `npm audit fix` 因夾帶開發工具鏈升級被還原）已 push（`df7c84f5..959dbeca`，連同 Sprint 322 的純文件回填 commit `e8383a4e`）。**雲端驗證**：手動觸發 [41467316938](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/41467316938)（`workflow_dispatch`）success，headSha `959dbeca`，`npm audit` 步驟通過；push 觸發的主 CI [41467149421](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/41467149421) 三個 job 全綠（單元 3 分 06 秒／前端 2 分 02 秒／整合 10 分 40 秒）。第一次 push 被 pre-push 守門擋下（`DEF-449`，§6 第 2 點）。

**Sprint 323 本身**：功能 commit `297e101a` 已 push（`959dbeca..297e101a`，依 2026-10-29 長期授權直接 push，沒有再詢問；pre-commit〔Checkstyle、編譯、核心測試、secret 掃描〕與 pre-push 輕量守門〔act 單元、前端、schema 漂移無漂移〕都一次通過，沒有使用 `--no-verify`）。**雲端 CI 全綠**：run [41486537336](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/41486537336)，三個 job 皆 success（Backend Unit Tests 2 分 45 秒／Frontend Lint & Build 2 分 02 秒／Backend Integration Tests & Package 7 分 58 秒），整個 run 10 分 48 秒（2026-11-02 16:57:53Z～17:08:41Z），以 `gh run view --json status,conclusion,headSha,jobs` 確認 headSha 為 `297e101a`，並下載整合 job 的完整雲端日誌（61.8 MB，剝掉 ANSI 色碼後）確認整合測試 **1014／0 失敗／0 錯誤／0 跳過**、`BUILD SUCCESS`、Checkstyle main／test 各 0 違規、`<<< FAILURE`／`<<< ERROR` 標記 0 個，`SettlementPostApprovalRaceRealStackIntegrationTest` 9、`SettlementReviewRaceRealStackIntegrationTest` 18、`M07SettlementIntegrationTest` 14、`SettlementApprovalPayoutTriggerRealStackIntegrationTest` 4 與本機 verify 逐類別一致（單元 job 只確認結論為 success，沒有另外抓雲端的單元案例數）。本輪結尾的 Docker 清理：匿名卷 8 個全刪（0 失敗）、卷 1.234GB → 928MB，3 個核准基底映像與 5 個具名卷保留，建置快取 1.889GB 只回報、沒刪。
