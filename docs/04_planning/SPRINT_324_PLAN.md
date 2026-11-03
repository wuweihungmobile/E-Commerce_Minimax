# Sprint 324 Plan — DEF-448：信用票據編號改成『開立日＋當日流水號』（使用者以互動選項拍板）

## 1. 起點與範圍

### 1.1 起點

[Sprint 323](SPRINT_323_PLAN.md) 收尾時，我用互動選項請使用者決定 `DEF-448`（同一租戶同一天的第二張逆轉確認 `500`）的信用票據編號格式，列了四個選項：**A** 沿用結算單編號（`CN-{租戶前 8 碼}-{結算期間起始日}`，我的建議）、**B** 開立日＋結算單 id 前 6 碼、**C** 開立日＋當日流水號、**不改**。**使用者選了 C**：`CN-{租戶 id 前 8 碼}-{開立日 yyyyMMdd}-{當日流水號}`（`01`、`02`…）。這是對一個具體選項的選擇，授權範圍就是該選項：本輪完整實作、驗證、push，不再詢問（見記憶 `inline-decision-replies-mean-proceed` 的 S309 條）。

長期授權（2026-10-29，使用者明文：『做完一輪後，直接 Commit + Push to main，請記住』）：本輪 commit 後直接 push。

### 1.2 成功標準（開工時定義）

1. **紅燈先行**：把想要的行為寫成真實棧案例，先在**未修改的生產碼**上跑出紅燈（預測先登記於 §3.1）。
2. **配合程式庫既有慣例**（Rule 11）：流水號用 `SupportTicketService.generateTicketNumber()` 的做法（`countBy…StartingWith(前綴)` ＋ `%0Nd`），撞號靠 `UniqueConflictRetry`（`DEF-441`，整個交易丟掉、在新交易重做）、唯一約束 `V36` 當最終仲裁者。
3. **不破壞 Sprint 323 的搶占**：同一張結算單的輸家仍在搶占就得 `409 E-5014`，不會被當成撞號重試。
4. **修法有牙齒**：突變驗證預先登記每組『恰好該紅的案例』。
5. 全量後端 `mvn -o clean verify`（全新資料庫）通過、Checkstyle 0；push 後雲端 CI 全綠並核對整合測試數。
6. 追蹤表如實更新（`DEF-448` → 完成）；過程中發現的相鄰問題只登記、不順手修。

### 1.3 動手前用工具核對的事實

| 說法（出處） | 核對結果 |
|---|---|
| 編號被誰使用（選項說明裡我寫『只由 `SettlementReversalService` 產生一處、沒有任何 API 或前端使用』） | `grep`：`generateCreditNoteNumber` 是 `SettlementReversalService` 的私有方法，**唯一**生產呼叫點是 `confirmReversal`；沒有任何 DTO 回傳 `creditNoteNumber`；`frontend/src` 沒有任何 `creditNote`／`credit_note`／『信用票據』引用。`CreditNoteRepository.existsByTenantIdAndCreditNoteNumber` 存在，但沒有呼叫者。文件裡提到 `CN-` 格式的只有追蹤表、Sprint 322／323 計畫書與 `API_M07`。 |
| 既有慣例 | `SupportTicketService.createTicket`：公開方法**不加** `@Transactional`，`return uniqueConflictRetry.execute(() -> createTicketOnce(...))`；`generateTicketNumber()` ＝ `prefix + String.format("%03d", countByTicketNumberStartingWith(prefix) + 1)`；唯一約束兜底，撞號時 `UniqueConflictRetry` 把整個交易丟掉、在新交易重做（`MAX_ATTEMPTS = 3`），重做時 `count` 已包含贏家。單元測試用 `new UniqueConflictRetry(Mockito.mock(PlatformTransactionManager.class))`。 |
| `UniqueConflictRetry` 的限制 | 呼叫時**不能**已在交易內（已在交易內只執行一次、例外原樣往外拋）。所以 `confirmReversal` 的 `@Transactional` 要拿掉，改由 `UniqueConflictRetry` 內的 `TransactionTemplate` 開交易；`SettlementController` 呼叫它時沒有外層交易（與 `SupportTicketService` 同款）。 |
| 欄位上限 | `credit_notes.credit_note_number VARCHAR(30)`（`V36`）。新格式 `CN-` ＋ 8 ＋ `-` ＋ 8 ＋ `-` ＋ 流水號（至少 2 位）＝ 22 字元起；當日流水號到 5 位數仍是 25 字元。 |
| 與搶占的關係 | 流水號的 `count` 在**搶占成功之後**才算（只有贏家算）。同一張結算單的輸家在 `updateStatusIfCurrent` 得 `409`（`BusinessException`，不是 `DataIntegrityViolationException`），`UniqueConflictRetry` 不會重試它。**不同**結算單、同一租戶、同一天、同時確認才會算出同一個號碼——撞唯一索引的是 `INSERT credit_notes`，整個交易（含搶占）回滾後重做。 |
| 開立日用哪個時區 | 原本是 `java.time.LocalDate.now()`（JVM 預設時區）。本輪**不改**時區語意（Rule 3），只在後面加流水號。 |

## 2. 做法（開工前的設計，結果見後面各節）

1. `CreditNoteRepository.countByTenantIdAndCreditNoteNumberStartingWith(UUID, String)`（衍生查詢；`tenantId` 是實體的影子欄位，`findByTenantId` 已經在用）。
2. `SettlementReversalService`：`generateCreditNoteNumber` ＝ `"CN-{租戶前 8 碼}-{今天 yyyyMMdd}-"` ＋ `%02d`（`count + 1`）；`confirmReversal` 拿掉 `@Transactional`，改成 `uniqueConflictRetry.execute(() -> confirmReversalOnce(...))`，原本的方法本體搬進 `confirmReversalOnce`。
3. 測試：真實棧新增三個案例（見 §3.1）；單元補流水號與重試包裝；`M07SettlementIntegrationTest`（真實 service ＋ `@MockBean` repository）預期不需要改。
4. 舊的信用票據編號不遷移（`CN-{租戶前 8 碼}-{yyyyMMdd}`，沒有流水號後綴）：新前綴帶結尾的 `-`，所以不會算進當日流水號，也不會與新編號相同。

## 3. 修改前的紅燈（未修改的程式碼、真實 PostgreSQL）

### 3.1 預測（跑之前登記；尚未執行任何新案例）

三個新案例加進 `SettlementPostApprovalRaceRealStackIntegrationTest`（既有 9 案例在未修改的程式碼上維持綠）。都用 `V36` 同形唯一索引（`createCreditNoteNumberIndex()`）模擬正式環境。

| 案例 | 內容 | 預測（未修改） | 預測的違反項目 |
|---|---|---|---|
| **N1 同租戶同日兩張結算單依序確認** | 兩張 `REVERSAL_PENDING` 的結算單（同一租戶），依序由兩位 CFO 確認 | **紅**——第一張 `200`，編號是舊格式 `CN-{前 8 碼}-{今天}`（沒有 `-01`）；第二張算出同一個舊編號，撞唯一索引：`500`／`E-9900`，第二張回滾維持 `REVERSAL_PENDING` | 3 項：第二張的 HTTP 狀態（預期 `200`、實際 `500`）、當日信用票據編號清單（預期 `[…-01, …-02]`、實際只有一個舊格式編號）、第二張結算單最終狀態（預期 `REVERSED`、實際 `REVERSAL_PENDING`） |
| **N2 確認輸給『別張結算單』剛拿走的流水號** | 另一個交易已插入（尚未提交）同租戶當日的 `…-01` 信用票據；此時確認另一張結算單，放行後對手提交 | **紅，而且紅在逾時**——未修改的編號是不含流水號的舊格式，與對手的 `…-01` 不同，所以確認請求**不會**卡在對手尚未提交的唯一索引項目上，`awaitRequestsBlocked(1)` 等不到：『等不到 1 個請求卡在鎖上』（訊息） | 逾時（案例在斷言之前就被擊殺；對手交易在 `finally` 放行） |
| **N3 隔天流水號重新從 01 開始** | 同租戶已有一張**舊日期**的信用票據（`CN-{前 8 碼}-20200101-05`）；今天確認一張結算單 | **紅**——編號是舊格式 `CN-{前 8 碼}-{今天}`（沒有流水號） | 1 項：編號（預期 `CN-{前 8 碼}-{今天}-01`、實際 `CN-{前 8 碼}-{今天}`） |

**預測的總計**：新增 3 案例全紅（N1 三項違反、N2 逾時、N3 一項違反）；既有 9 案例維持綠。**不確定的地方**：N1 第二張回應的錯誤碼我預測 `E-9900`（與 Sprint 323 探針相同），N1 的第一張舊編號格式是 `CN-{前 8 碼}-{yyyyMMdd}`（Sprint 323 探針量到的格式）。

### 3.2 實測結果（未修改的生產碼，真實 PostgreSQL）

新類別 12 案例跑、**3 紅 9 綠**（Failures 3、Errors 0）；既有 9 案例全綠，新增 3 案例全紅。**逐格符合預測**：

| 案例 | 實測 | 與預測 |
|---|---|---|
| **N1 同租戶同日兩張結算單依序確認** | **3 項違反**：第二張確認 HTTP 預期 `200` 實際 `500`（回應 `E-9900`／『發生未預期的錯誤』）；當日信用票據編號預期 `[…-01, …-02]`、實際只有一個舊格式編號 `["CN-5946a51a-20261103"]`；第二張結算單最終狀態預期 `REVERSED`、實際 `REVERSAL_PENDING`（回滾） | 相符（含錯誤碼 `E-9900` 與舊編號格式） |
| **N2 確認輸給別張結算單剛拿走的流水號** | 逾時：`等不到 1 個請求卡在鎖上（目前 0 個）`——未修改的編號不含流水號，與對手的 `…-01` 不同，確認請求直接完成、沒有卡在對手尚未提交的唯一索引項目上 | 相符（紅在逾時，案例在斷言之前被擊殺） |
| **N3 隔天流水號重新從 01 開始** | **1 項違反**：編號預期 `CN-23440e94-…-01`、實際 `CN-23440e94-20261103`（舊格式，沒有流水號）；確認回 `200` | 相符 |

## 4. 修改後與突變驗證

### 4.1 修改

1. `CreditNoteRepository.countByTenantIdAndCreditNoteNumberStartingWith(UUID, String)`（衍生查詢）。
2. `SettlementReversalService.generateCreditNoteNumber`：`CN-{租戶 id 前 8 碼}-{開立日 yyyyMMdd}-` ＋ `%02d`（該租戶當日已開立張數＋1）。前綴帶結尾的 `-`，所以舊格式編號（沒有流水號後綴）不會被算進去。開立日沿用原本的 `LocalDate.now()`，沒有改時區語意。
3. `SettlementReversalService.confirmReversal`：**拿掉 `@Transactional`**，改成 `uniqueConflictRetry.execute(() -> confirmReversalOnce(...))`（原本的方法本體搬進 `confirmReversalOnce`）；撞號時整個交易（含 Sprint 323 的狀態搶占）回滾並在新交易重做，重做時贏家的票據已提交，流水號自然錯開。做法同 `SupportTicketService.createTicket`。

### 4.2 單元與真實棧

- 單元：`SettlementReversalServiceTest` 12 → **15**（流水號＝當日已開立張數＋1 且至少兩位：0→`-01`、8→`-09`、9→`-10`、98→`-99`、99→`-100`；INSERT 撞唯一約束→整個交易重做、改用下一個流水號、狀態搶占也重做一次；`confirmReversal` 不可標 `@Transactional`）。另外在既有案例補斷言：第一張是 `…-01`、計數在搶占**之後**（`InOrder`）、搶輸（`409`）**不**被當成撞號重做（`updateStatusIfCurrent` 只呼叫一次、完全沒有計數）。
- 真實棧：修復後 `SettlementPostApprovalRaceRealStackIntegrationTest` **12 案例 0 失敗**（含 N1、N2、N3 轉綠；N1～N3 是本計畫書的簡稱，程式裡沒有這些標籤，對應的測試方法依序是 `confirmTwoStatementsOfTheSameTenantOnTheSameDay_getConsecutiveNumbers`、`confirmLosesTheDailyNumberToAnotherStatement_retriesWithTheNextNumber`、`dailyNumberRestartsAtOneEachDay`），`M07SettlementIntegrationTest` 14 案例 0 失敗（真實 service ＋ `@MockBean` repository，走新的交易包裝）；兩者合跑 26／0。

### 4.3 突變驗證（預先登記：規格檔先寫好 `must`，跑完逐一比對）

驅動沿用 Sprint 323 的 `bmut.py`；規格檔 `mkspec_s324.py` 開跑前乾跑『每個 `old` 恰好出現一次』通過，被突變的檔案事先記錄 SHA-256。基準 Z0 在全新資料庫上跑，測試集合＝單元 `SettlementReversalServiceTest`（15）＋真實棧新類別（12），共 27 案例（`M07SettlementIntegrationTest` 不放進突變集合：它的啟動成本高、對這些突變沒有鑑別力，改由全量 verify 覆蓋）。判決標準：失敗集合與登記的 `must` **恰好**吻合才算 `KILLED(exactly must)`。

縮寫：**U_OK**＝`confirmReversal_differentRole_succeeds`、**U_LOST**＝`confirmReversal_concurrentClaimLost_throwsE5014WithoutCreditNote`、**U_NUM**＝`…_runningNumberIsCountPlusOnePaddedToTwoDigits`、**U_RETRY**＝`…_uniqueConflictOnTheFirstAttempt_rerunsTheWholeTransactionWithTheNextNumber`、**U_TX**＝`…_isNotTransactional_soTheRetryComponentOwnsTheTransaction`；N1～N3 見 §3.1。

| 組 | 突變 | 登記的恰好該紅 |
|---|---|---|
| Z0 | 無突變（基準，全新資料庫） | 無（27 案例全綠） |
| EM1 | `UniqueConflictRetry.MAX_ATTEMPTS` 改成 1（撞號輸家不再重做） | N2、U_RETRY（2）。N2 預期紅在輸家 `500`（訊息 `but was: 500`） |
| EM2 | `confirmReversal` 加回 `@Transactional`（重試元件不再是最外層，已在交易內只執行一次） | N2、U_TX（2）。N2 預期紅在輸家 `500`（撞約束發生在外層交易提交時，沒有人可以重做）；單元的 U_RETRY 直接呼叫服務、沒有 Spring 代理，所以預期**仍綠** |
| EM3 | 流水號忽略計數（永遠 `01`） | N1、N2、U_NUM、U_RETRY（4）。N3 預期仍綠（今天第一張本來就是 `01`） |
| EM4 | 流水號少加 1（第一張是 `00`） | N1、N2、N3、U_OK、U_NUM、U_RETRY（6）。N2 預期紅在逾時：`…-00` 與對手的 `…-01` 不同，不會卡住（訊息 `等不到 1 個請求卡在鎖上`） |
| EM5 | 流水號不補零（`01` 變 `1`） | 同 EM4：N1、N2、N3、U_OK、U_NUM、U_RETRY（6），N2 同樣紅在逾時 |
| EM6 | 計數的前綴不含日期（舊日期的票據也被算進今天） | N3、U_OK、U_NUM、U_RETRY（4）。N1、N2 預期仍綠（同一天的集合相同） |
| EM7 | 號碼在搶占**之前**就算（輸家也會計數） | U_OK、U_LOST（2）。真實棧預期全綠（輸家算了數字也沒有寫入，只有單元的順序與『輸家不計數』守得住） |

（結果見下方 §4.4。）

### 4.4 結果

基準 Z0（全新資料庫）**27 案例 0 失敗**（單元 15＋真實棧 12，與登記的案例數一致）。7 組突變的失敗集合**全部與登記的 `must` 恰好吻合**（驅動的 `restored=True` 8 次〔含基準〕；沒有 `INVALID`、沒有 `MSG-MISMATCH`）；失敗數與登記數逐組相同：EM1 2、EM2 2、EM3 4、EM4 6、EM5 6、EM6 4、EM7 2。結束後，被突變的 `SettlementReversalService.java`、`UniqueConflictRetry.java` 與其餘工作檔的 SHA-256 與事先記錄逐一相同（計畫書因我追加章節而不同是預期），沒有突變殘留字串。讀失敗訊息確認的歸因：

1. **EM1（`MAX_ATTEMPTS = 1`）、EM2（加回 `@Transactional`）、EM3（流水號永遠 `01`）三個，N2 的輸家都是 `500`／`E-9900`**（`expected: 200 but was: 500`）：EM1 是撞號後不再重做；EM2 是重試元件不在最外層，撞約束發生在外層交易提交時，沒有人能重做；EM3 是重做了也還是 `01`、再撞。EM3 的 N1 同樣在第二張 `500`，當日只留下 `…-01` 一個編號。EM2 的單元 U_RETRY 預期**仍綠**（登記時就寫了：單元直接 `new` 服務、沒有 Spring 代理，`@Transactional` 無效），所以另寫了反射式的結構守門 U_TX 才殺得到單元層。
2. **EM4（少加 1）、EM5（不補零）的 N2 紅在逾時**：編號是 `…-00`／`…-1`，與對手的 `…-01` 是不同字串，不會卡在對手尚未提交的唯一索引項目上，`awaitRequestsBlocked(1)` 等不到（`must_msg` 登記的『等不到 1 個請求卡在鎖上』吻合）。
3. **EM3 的 N3 仍綠、EM6 的 N1／N2 仍綠、EM7 的真實棧全綠，三個預測都實現**：EM3 時今天的第一張本來就是 `01`；EM6 把舊日期的票據算進來只影響有舊日期票據的 N3；EM7（搶占前就算號）輸家算了數字也沒寫入任何東西，外部行為等價，只有單元的 `InOrder`（計數在搶占之後）與 U_LOST 的『輸家完全沒計數』守得住。
4. **EM6 的單元三個案例紅在 Mockito 嚴格 stub 的參數對不上**（計數被換成較短的前綴，stub 的參數不符），不是斷言——登記時已把這種連帶算進 `must`。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**19 分 12 秒**；單元 **2472／0 失敗／0 錯誤／0 跳過**（Sprint 323 的 2469＋`SettlementReversalServiceTest` +3）；整合 **1017／0／0／0**（1014＋新案例 3）；Checkstyle main／test 各 0 違規；日誌 `<<< FAILURE`／`<<< ERROR`／`Unresolved compilation`／`ClassFormatError`／`NoClassDefFoundError` 各 0 個。變動與連動的類別在 verify 內確實執行：`SettlementPostApprovalRaceRealStackIntegrationTest` 12、`SettlementReversalServiceTest` 15、`M07SettlementIntegrationTest` 14、`TransferServiceTest` 23、`SupportTicketServiceTest` 20、`UniqueConflictRetryTest` 5、`UniqueConstraintRaceRealStackIntegrationTest` 4。 |
| 紅燈先行（§3） | 未修改的程式碼上新增 3 案例全紅（N1 三項違反、N2 逾時、N3 一項違反），既有 9 案例全綠，預測逐格相符；沒有預測落空。 |
| 突變驗證（§4.3～§4.4） | 基準 Z0（27／0）＋7 組突變，**全部 `KILLED(exactly must)`**，預先登記逐一吻合。 |
| 穩定性 | 新真實棧類別 `SettlementPostApprovalRaceRealStackIntegrationTest`（12 案例）在全量 verify 之外**另連跑 3 次**，每次 `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`（耗時 40.28／42.77／37.83 秒）。每次日誌各有恰好 1 行 `ERROR … duplicate key value violates unique constraint "idx_it_post_race_credit_notes_…"`，緊接著一行 `UniqueConflictRetry … re-running the whole transaction: attempt=1/3`——這是 N2 刻意製造的撞號與整個交易重做，不是失敗。 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | **14／0 失敗／0 錯誤／0 跳過**（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1），`BUILD SUCCESS`；在本計畫書、`API_M07` v1.6、追蹤表與發布追蹤表都寫完之後、commit 前執行。 |
| IDE Problems | 本輪變動的四個 Java 檔（`SettlementReversalService`、`CreditNoteRepository`、`SettlementReversalServiceTest`、`SettlementPostApprovalRaceRealStackIntegrationTest`）逐檔查詢 `getDiagnostics`，**皆為空陣列**；不帶 `uri` 的全域查詢回傳的所有條目（含連動的 `TransferService`、`UniqueConflictRetry`、`M07SettlementIntegrationTest`）diagnostics 也全為空。⚠️ 這個工具只涵蓋 VS Code 已分析的檔案，空結果本身不是證明——證明是編譯、Checkstyle 0 與全量 verify。 |
| 沒有執行的項目 | Playwright 完整 E2E 與 `make validate-schema-doc`：本輪沒有前端、Flyway 遷移或 schema 變更（編號格式是應用層的字串；`credit_note_number` 仍是 `VARCHAR(30)`，新格式 22 字元起）。 |

## 6. 過程中的失誤與修正

1. **設計被前例推翻**：我原本打算用 PostgreSQL 的 advisory lock 把『同租戶同日』的計數串行化。動手前讀 `SupportTicketService` 才發現程式庫早有同款問題的慣例（`count + 流水號`、唯一約束仲裁、`UniqueConflictRetry` 整個交易重做）。advisory lock 比前例複雜、而且是 PostgreSQL 專屬，改採前例（Rule 11）。教訓：**設計之前先找程式庫前例**。
2. **使用者的回答隔了約 7 小時**（互動選項會擋住整個 turn）：回來時 `date -u` 與先前對不上，先重新核對狀態——Sprint 323 的雲端 CI 早已完成，下載整合 job 日誌（61.8 MB）核對 1014／0 後才回填，沒有憑印象寫。
3. **IDE 的過時診斷**：新增測試輔助方法的編輯之後，IDE 回報『方法未定義』——是編輯尚未全部落地時算出的過時結果；用刪 class 逼 javac 重編確認。
4. **`awk` 的 `length` 在這台機器上以位元組計算**（CJK 每字 3 位元組），一度誤判多行超過 Checkstyle 的 150 字元上限；改用 Python 以字元數檢查，沒有超過。
5. 其餘循環如常：生產碼改完先單獨編譯（`javap` 確認新成員）、再更新並執行單元測試、再跑真實棧；沒有累積多支程式才編譯的偏差。

## 7. 限制（誠實揭露）

1. **開立日沿用原本的 `LocalDate.now()`（JVM 預設時區）**，沒有改成營運時區；跨日邊界上，編號裡的日期可能與 `issued_at`（`Instant`）換算成營運時區後的日期差一天。測試的 `todayNumberPrefix()` 也用 `LocalDate.now()`，若一次測試執行恰好跨過本機午夜，案例會因日期不同而失敗（機率可忽略，但不是零）。
2. **同租戶同日連續輸給 3 個不同對手**才會把撞號往外拋（`UniqueConflictRetry.MAX_ATTEMPTS = 3`，`500`）；雙重授權的人工操作下實務上不會發生。
3. **真實棧用的是『與 `V36` 同形』的唯一索引**（整合測試庫是 Hibernate 建的，沒有 Flyway 的約束），不是 Flyway 建出的真實約束。
4. **N2 模擬的是『另一個交易已插入同號、尚未提交』**，不是兩個真實的 `confirm` 請求同時進來；兩張不同結算單的真實並行確認只靠這個模擬加 `UniqueConflictRetry` 既有的測試（`UniqueConflictRetryTest`、`UniqueConstraintRaceRealStackIntegrationTest`）覆蓋。
5. **舊格式編號不遷移**，也不會被算進當日流水號；同一租戶同一天若已有一張舊格式編號，新編號仍從 `01` 開始（兩者是不同字串，不會相撞）。
6. **`M07SettlementIntegrationTest` 沒有放進突變集合**（啟動成本高、對這些突變沒有鑑別力），改由全量 verify 覆蓋。
7. 沒有執行 Playwright 與 `validate-schema-doc`（§5 最後一列）。

## 8. 我替使用者做的決定（可一句話推翻）

1. **只實作使用者選的 C**，不再推銷我建議的 A；C 較複雜之處（要計數、撞號要重試）如實寫進風險（§7）。
2. **照 `SupportTicketService` 的慣例做**（計數＋`%02d`＋`UniqueConflictRetry`），不用 advisory lock 或另建流水號表（後者要 Flyway 遷移）。
3. **開立日語意不變**（`LocalDate.now()`），不借機改時區（Rule 3）。
4. **舊編號不遷移**。
5. **流水號寬度＝`%02d`，超過 99 自然變三位**（`-100`），沒有上限。
6. **`confirmReversal` 拿掉 `@Transactional`**（`UniqueConflictRetry` 必須是最外層），並用單元的結構守門固定下來。
7. **新案例放在既有的 `SettlementPostApprovalRaceRealStackIntegrationTest`**（同一件事：核准之後的逆轉確認），不另開類別。
8. 本輪另有 Sprint 323 的純文件回填（push 狀態與雲端 CI 結果）：先另成一個純文件 commit，再提交本輪的功能 commit，兩者一起 push（所以發布追蹤表的 v4.64 與 v4.65 分屬兩個 commit）。

## 9. 新登記與更新的缺陷

- **`DEF-448`**（🟢→✅）：已修，見追蹤表。
- **`DEF-449`**（🟢，仍未處理）：`make validate-fast` 一律驗主倉庫目錄；本輪 push 前主目錄已 commit 乾淨，所以沒有再被擋下。
- 本輪沒有新登記的缺陷。

## 10. 狀態

**狀態：已 push，雲端 CI 全綠。**

**push**：功能 commit `ce23f3e1` 已 push（`297e101a..ce23f3e1`，連同 Sprint 323 的純文件回填 commit `6c27e63e`；依 2026-10-29 長期授權直接 push，沒有再詢問）。pre-commit〔Checkstyle、編譯、核心測試、secret 掃描〕兩個 commit 各一次通過，pre-push 輕量守門〔act 單元、前端、schema 漂移無漂移〕一次通過，沒有使用 `--no-verify`；push 前主工作目錄已 commit 乾淨，所以沒有再碰到 `DEF-449`。

**雲端 CI 全綠**：run [41559586116](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/41559586116)，三個 job 皆 success（Backend Unit Tests 2 分 37 秒／Frontend Lint & Build 1 分 59 秒／Backend Integration Tests & Package 8 分 28 秒），整個 run 11 分 11 秒（2026-11-03 01:24:54Z～01:36:05Z）。`gh run watch` 回 0 之後，另以 `gh run view --json status,conclusion,headSha,jobs` 交叉確認 headSha 為 `ce23f3e1`，並下載整合 job 的完整雲端日誌（61.8 MB，剝掉 ANSI 色碼後）確認：整合測試 **1017／0 失敗／0 錯誤／0 跳過**（與本機 verify 同數，§5 的預期成真）、`BUILD SUCCESS`、Checkstyle main／test 各 0 違規、`<<< FAILURE`／`<<< ERROR`／`Unresolved compilation`／`ClassFormatError`／`NoClassDefFoundError` 各 0 個；`SettlementPostApprovalRaceRealStackIntegrationTest` 12、`SettlementReviewRaceRealStackIntegrationTest` 18、`M07SettlementIntegrationTest` 14、`SettlementApprovalPayoutTriggerRealStackIntegrationTest` 4、`UniqueConstraintRaceRealStackIntegrationTest` 4，與本機 verify 逐類別一致。**單元 job 只確認結論為 success 與 Checkstyle 0**：它的 `mvn test` 帶 `-q`（安靜模式），日誌不印案例數，所以沒有雲端的單元案例數可核對。

**Docker 清理**（本輪結尾）：匿名卷 4 個全刪（0 失敗）、卷 1.112GB → 928MB，3 個核准基底映像與 5 個具名卷保留，懸空映像清理回收 0B，建置快取 1.889GB 只回報、沒刪。另一個觀察：push 前 `docker ps` 看得到 `nk-test-pg`／`nk-test-redis`，push（含 pre-push 守門）之後 `docker ps -a` 一個容器都沒有，下次後端 commit 前要先重新 `make test-db-up`。

**這個回填 commit 只改文件，尚未 push**，隨下一輪的 push 帶出（沿用 Sprint 322／323 的做法，避免『回填→再 push→再回填』無限循環）。
