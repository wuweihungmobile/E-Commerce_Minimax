# Sprint 326 Plan — DEF-451／DEF-450：結算單生成的交易邊界（週結算逐租戶一個交易、手動產生撞號重做）

## 1. 起點與範圍

### 1.1 起點

[Sprint 325](SPRINT_325_PLAN.md) 收尾時，使用者把總結貼回來並說「請繼續完成任務！」，**沒有 `==>` 回覆**。這是無 `==>` 變體的第十六個實例（解讀為**推論**，不是使用者明說的）。這次總結自己寫了下一步：「下次說『請繼續』我可以直接做：先量測 `DEF-451`……接著做 `DEF-450`」，所以照這個順序做，不另外重新分類追蹤表。

- 總結「還等你決定」的項目（`DEF-294` 購物車併發加入、`DEF-449` pre-push 守門、`DEF-419` 運費模板權限、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取）**不在這句話的授權範圍內**，本輪不碰。
- 長期授權（2026-10-29，使用者明文：『做完一輪後，直接 Commit + Push to main，請記住』）：本輪 commit 後直接 push，連同 Sprint 325 留在本機的回填 commit `3392d816` 一起帶出。

### 1.2 成功標準（開工時定義）

1. **先量測、再決定修不修**：`DEF-451` 目前只是讀碼推論（Sprint 325 追蹤表明寫『未量測』），用真實 PostgreSQL 量測 (i)(a)(b) 三件事（見 §2）。
2. 量測屬實 → **紅燈先行**：把想要的行為寫成真實棧案例，先在未修改的程式碼上跑出紅燈，預測先登記。
3. 配合程式庫既有慣例（Rule 11）：公開入口不加 `@Transactional`，交給 `UniqueConflictRetry`（`DEF-441`／`DEF-297`／`DEF-448` 的前例）；逐項處理的排程由外層不開交易、逐項各自一個交易（`BookingNoShowService` 的前例）。
4. 修法有牙齒：突變驗證預先登記每組『恰好該紅的案例』。
5. 全量後端 `mvn -o clean verify`（全新資料庫）通過、Checkstyle 0；push 後雲端 CI 全綠並核對整合測試數。
6. 追蹤表如實更新；過程中發現的相鄰問題**只登記、不順手修**。

### 1.3 動手前用工具核對的事實

| 說法（出處） | 核對結果 |
|---|---|
| 『`new SettlementGenerator(` 在 5 個測試類別、10 處，`SettlementScheduledJobIntegrationTest` 6 處』（`DEF-450` 登記，Sprint 325） | **不準**。逐行 `grep -n "new SettlementGenerator("`：`SettlementGeneratorManualTriggerTest` 1、`…BusinessWeekTest` 1、`…ClaimTest` 1、`SettlementTenantScopeTest` 1、`SettlementScheduledJobIntegrationTest` **8** ＝ **12 處**。Sprint 325 為什麼寫成 10（ScheduledJob 6 處），我沒有查，差異原因不明 |
| 『`SettlementScheduledJobIntegrationTest` 直接 `new` 呼叫，沒有代理也沒有交易，測不出來』（`DEF-451` 登記） | 屬實，而且它根本是 **Mockito 單元測試**（所有 repository 都是 `@Mock`），名字裡的 `Integration` 是誤導。唯一涵蓋 `generateStatementForTenant` 的真實棧類別是 `SettlementConcurrentClaimIntegrationTest`，它只測『期間不同』的並行 |
| 『排程是不是透過代理呼叫、外層交易到底存不存在』（`DEF-451` 的 (i)，標為不確定） | **已量測**（§2）：啟用排程後 `ScheduledAnnotationBeanPostProcessor` 實際註冊的目標是 `SettlementGenerator$$SpringCGLIB$$0`（`isAopProxy=true`）。程式裡 6 個 `@Scheduled` 方法，只有這一個是代理 |
| 『`API_M07` 與維運手冊說單一租戶失敗不影響其他租戶（異常隔離設計）』 | 修復前**對資料庫層級的失敗不成立**（§2 的 A1／A2）；該說法只被 Mockito 測試『證明』過 |
| 先前的週結算作業在測試庫每次都對系統租戶記一筆 `Failed to generate … NullPointerException`（`Tenant.getCommissionRate()` 為 null） | **測試庫的假象，不是缺陷**：正式環境的系統租戶由 Flyway `V9` 建立，沒指定 `commission_rate`，套用 `V1` 的 `DEFAULT 0.05`；整合測試庫是 Hibernate 建的（沒有欄位預設），`TestDatabaseInitializer` 又沒填這欄，所以是 null。這個 NPE 不經過 repository 的交易代理，不會毒化交易（控制組 A0 證明其他租戶照常產生）。**不登記**，但它會出現在每次真實棧作業的日誌裡 |

## 2. 量測 `DEF-451`（修改前，未修改的程式碼，真實 PostgreSQL）

一次性探針（兩個類別，不入庫，輸出原文在附錄 A）。做法：用 JDBC 種租戶與訂單；週結算作業走 Spring 代理（`@Autowired SettlementGenerator`）；『認領筆數不足』用持鎖的對手交易製造，並以 `pg_stat_activity` 確認排程的 `UPDATE orders` 真的卡在對手的列鎖上（不靠睡眠）。

### 2.1 預測（跑之前登記）

| 探針 | 預測 |
|---|---|
| P0／S0 | 代理＝true，排程註冊的目標也是代理 |
| A0 控制組（兩個健康租戶） | 各 1 張結算單、訂單全認領 |
| A1／A2（H1、F、H2；F 的結算單號被一列期間不同的結算單佔用，自建只涵蓋 F 的唯一索引） | F 撞唯一約束讓交易中止；H2（以及 H1，因為整批回滾）都沒有結算單；**日誌只有每個租戶一行『Failed to generate』，作業本身不出聲** |
| B1（認領 1／2） | 結算單留在資料庫、`total_orders=2`，只有訂單 1 掛在它底下；調整單仍 `PENDING`，結算單的 `adjustment_amount` 卻含 −30 |

### 2.2 實測

| 項目 | 結果 | 與預測 |
|---|---|---|
| (i) 排程目標是不是代理 | 是（`generateWeeklyStatements` 的目標＝CGLIB 代理；其他 5 個 `@Scheduled` 目標都不是代理） | 相符 |
| A0 控制組 | H1、H2 各 1 張（`total_orders=2`、`total_gmv=200.00`），`settledOrders=2` | 相符 |
| **(a) A1**（F 有訂單） | **H1（迭代順序排在 F 前面）與 H2（排在 F 後面）都沒有結算單，`settledOrders=0`**；F 只剩佔位那一列；作業以 **`UnexpectedRollbackException: … marked as rollback-only`** 結束 | **部分落空**：全部租戶一起丟掉，相符；『作業不出聲』**不符**——例外往外拋（日誌 `Generated 1 statements` 之後作業丟例外） |
| (a) A2（F 沒有訂單） | 同 A1（H1、H2 都沒有、`UnexpectedRollbackException`） | 同上 |
| **(b) B1** | 作業正常結束（日誌 `Failed to generate … IllegalStateException: Settlement claimed 1 of 2 orders … rolling back statement STL-…-20261026`，接著 `Generated 0 statements`）；**結算單 `STL-…-20261026` 留在資料庫**：`total_orders=2`、`total_gmv=200.00`、`adjustment_amount=-30.00`；訂單 1 掛在它底下、**訂單 2 掛在別張結算單**；調整單 `PENDING`、`applied_statement_id=null` | 相符 |

**為什麼 (a) 是例外往外拋而不是靜默**（讀日誌後的解釋）：F 的 INSERT 失敗時，例外穿過 repository 的交易代理，代理把**共用的**外層交易標成 rollback-only；迴圈的 `try/catch` 把例外吞掉，作業照常往下走，直到外層交易提交時 Spring 發現 rollback-only，才拋 `UnexpectedRollbackException`。所以不是『靜默回滾』，而是『排程執行緒收到例外、一整週沒有任何結算單』。

**後果**（(b)）：被別張結算單搶先認領的那筆訂單，GMV 在兩張結算單各算一次（若兩張都核准撥款就是重複撥款）；留在調整單狀態 `PENDING` 的 −30，下一次作業會再套用一次；而且下一次作業的冪等檢查（『同期已有結算單就跳過』）會直接回傳這張半套的結算單，**不會修它**。

## 3. 修改前的紅燈（新真實棧類別，未修改的程式碼）

新類別 `SettlementGenerationRaceRealStackIntegrationTest`（`integration` 套件，3 案例）。因為 (a)(b) 屬實，且 `DEF-450` 與它們共用同一個修法與同一次建構子變更（見 §8），三個一起寫、一起紅：

| 案例 | 預測（跑之前登記） | 實測（未修改的程式碼） |
|---|---|---|
| `generateStatementManually_losingTheSamePeriodRace_returnsTheWinnersStatement`（`DEF-450`） | 輸家 `500 E-9900`，失敗在狀態碼斷言 | `expected: 200 but was: 500`，`E-9900`；日誌＝真實的 `duplicate key … idx_it_settle_tx_…` ✅ |
| `weeklyJob_oneTenantFailingAtTheDatabase_doesNotTakeTheOtherTenantsWithIt`（`DEF-451` (a)） | 作業拋 `UnexpectedRollbackException`，失敗在『不應以例外結束』 | `expected: null but was: UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only` ✅ |
| `weeklyJob_claimShortfall_rollsBackTheWholeStatement_andTheNextRunSettlesCorrectly`（`DEF-451` (b)） | 作業正常結束，失敗在『沒有本期結算單』 | `Expecting empty but was: [SettlementStatement@…]`；日誌 `Settlement claimed 1 of 2 orders … rolling back statement` ✅ |

三個都因為對的原因而紅（讀過失敗訊息與日誌，不只看紅綠）。

## 4. 修復與驗證

### 4.1 修改（只動 `SettlementGenerator` 一個主程式碼檔）

1. 注入 `UniqueConflictRetry`（建構子多一個參數）。
2. `generateWeeklyStatements`：**拿掉 `@Transactional`**；迴圈裡 `uniqueConflictRetry.execute(() -> generateStatementForTenant(...))`——每個租戶各自一個交易，單一租戶失敗只回滾它自己；同期撞號輸家整個重做。`try/catch` 與 `generatedCount` 沿用（只算成功的）。
3. `generateStatementManually`：**拿掉 `@Transactional`**；驗證（`periodEnd` 不早於 `periodStart`）留在重做區塊之外、不開交易；`return uniqueConflictRetry.execute(() -> mapper.toStatementResponse(generateStatementForTenant(...)))`。
4. `generateStatementForTenant` 不動（仍是 `@Transactional`；`SettlementConcurrentClaimIntegrationTest` 經代理直接呼叫它）。
5. 5 個測試類別共 **12 處** `new SettlementGenerator(...)` 補上 `new UniqueConflictRetry(mock(PlatformTransactionManager.class))`（Mockito 的交易管理員，`getTransaction` 回 null，`TransactionTemplate` 照常執行；Sprint 325 的 `PurchaseOrderServiceTest` 用過同一招）。

沒有遷移、沒有 API 形狀變更、單號格式不變。

### 4.2 單元與真實棧

- 新單元類別 `SettlementGenerationTransactionBoundaryTest`（4 案例）：①結構守門——類別與兩個入口方法都沒有 `@Transactional`（單元層沒有 Spring 代理，入口一旦又開外層交易，`UniqueConflictRetry` 偵測到『已在交易內』就只執行一次，單元測試看不出來）；②週結算：3 個租戶、中間一個拋 `DeadlockLoserDataAccessException`，用 mock 交易管理員計數 `getTransaction` ×3、`commit` ×2、`rollback` ×1，且產生結算單的是失敗者前後兩個租戶；③手動產生：第一次 `save` 撞唯一約束、第二次冪等檢查看到贏家，`getTransaction` ×2、`rollback` ×1、`commit` ×1、`save` ×1，回傳贏家那張的回應；④結束日早於起始日 → 直接拒絕，`getTransaction` 從未呼叫。
- 真實棧（§3 的三個案例）修復後全綠。(b) 案例額外斷言**下一次作業產出正確的結算單**：只含實際認領到的 1 張（`total_orders=1`、`total_gmv=100.00`）、套用 −30 調整單（`adjustment_amount=-30.00`、調整單 `APPLIED`、訂單掛在新結算單上）。

### 4.3 突變驗證（預先登記：規格檔先寫好 `must`，跑完逐一比對）

受測集合＝6 個單元類別（25 案例）＋2 個真實棧類別（5 案例）＝ **30 案例**。驅動由我依記憶檔的設計重寫（前幾輪放在 scratchpad 的版本已被清空）：備份 → 套用（`old` 必須恰好出現一次）→ 刪掉被改檔案的 `.class` 逼 Maven 重編 → 跑單元＋整合 → 還原 → SHA-256；判決＝失敗集合與 `must` **恰好**相同。開跑前備份被突變的兩個檔案並記錄 SHA-256。

縮寫：`B`＝`SettlementGenerationTransactionBoundaryTest`，`R`＝`SettlementGenerationRaceRealStackIntegrationTest`；`T1`＝手動產生撞號、`T2`＝單一租戶失敗、`T3`＝認領不足。

| 突變 | 內容 | 預先登記的 `must`（恰好該紅） | 結果 |
|---|---|---|---|
| Z0 | 無突變（基準，全新資料庫） | （空）；`ran` 必須是 30 | `ran=30 failed=0` ✅ |
| SM1 | `generateWeeklyStatements` 加回 `@Transactional`（舊結構） | `B` 結構守門、`R.T2`、`R.T3` | **KILLED(exactly must)**：結構＝`expected: null but was: @Transactional(…)`；`T2`／`T3`＝`UnexpectedRollbackException`。單元的週結算案例**仍綠**（沒有 Spring 代理），所以結構守門不可省 |
| SM2 | `generateStatementManually` 加回 `@Transactional` | `B` 結構守門、`R.T1` | **KILLED(exactly must)**：`T1`＝`expected: 200 but was: 500`（`E-9900`）。單元的撞號案例仍綠（同上） |
| SM3 | 週結算迴圈直接呼叫 `generateStatementForTenant`（沒有重試包裝＝自我呼叫、沒有交易） | `B` 週結算、`R.T2`、`R.T3` | **KILLED(exactly must)**：`B`＝`Wanted but not invoked: transactionManager.getTransaction`；`T2`＝`[健康租戶 …被認領的訂單] expected: 2 but was: 0`（日誌：沒有交易時 `markSettled`〔`flushAutomatically`〕拋 `TransactionRequiredException: No EntityManager with actual transaction available … 'flush'`，認領沒有發生）；**`T3` 是逾時紅**（`等不到含「update orders」的語句卡在另一個交易的鎖上`——沒有交易時 UPDATE 立刻失敗、不會卡在鎖上），不是斷言紅 |
| SM4 | 手動產生直接呼叫、不經 `UniqueConflictRetry` | `B` 手動撞號、`R.T1` | **KILLED(exactly must)**：`B`＝`DataIntegrityViolationException: duplicate key`；`T1`＝`500` |
| SM5 | `UniqueConflictRetry.MAX_ATTEMPTS` 3 → 1 | `B` 手動撞號、`R.T1` | **KILLED(exactly must)**：同 SM4 的兩種紅燈 |
| SM6 | 週結算迴圈的 `catch` 裡重新拋出例外（失敗不再隔離） | `B` 週結算、`SettlementScheduledJobIntegrationTest.oneTenantFails_OthersStillProcessed`、`R.T2`、`R.T3` | **KILLED(exactly must)**：`B`＝`DeadlockLoserDataAccessException` 穿出；`oneTenantFails…`＝`Unexpected exception thrown: RuntimeException: DB connection lost`；**`T2` 是紅在測試庫系統租戶的 NPE**（見 §1.3：它排在第一個，拿掉隔離後作業直接中止），不是 F 的唯一約束；`T3` 同樣逾時紅（作業在第一個租戶就中止，根本沒走到會卡住的租戶） |
| SM7 | 週結算在重試包裝**裡面**吞掉 `IllegalStateException`（交易照常提交） | `R.T3` | **KILLED(exactly must)**：`[認領不足 → 整張結算單回滾，不留下半套的結算單] Expecting empty but was: [SettlementStatement@…]`。**只有真實棧殺得掉它**（單元的失敗者是 `DeadlockLoser`，不是 `IllegalStateException`）；這是證明『逐租戶交易的回滾』在守 (b) 的唯一突變 |
| SM9 | 結束日驗證搬進重做區塊裡（外部行為等價，只多開一個交易） | `B` 無效期間案例 | **KILLED(exactly must)**：`Never wanted here: transactionManager.getTransaction`。**外部等價突變，只有單元的次數斷言守得住**（S245 同款） |

編號 SM8 跳號：想過『把 `generatedCount++` 搬到 `execute` 之前』（只影響一行日誌的數字，沒有行為斷言可守）而捨棄，編號不重排以免與規格檔對不上。

### 4.4 結果

9 次執行共約 9 分鐘（Z0 含重建資料庫 54 秒；SM3、SM6 各 86 秒，因為各有一個案例等 30 秒逾時；其餘 54～59 秒）。**八組突變全部 `KILLED(exactly must)`，預先登記逐一吻合**；每組還原後 SHA-256 與備份相同；結束後 `shasum -a 256 -c` 兩個被突變的檔案皆 OK、`git status` 只有預期的檔案。紅燈原因都讀過失敗訊息（上表『結果』欄的引文就是來源），不只看紅綠。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 既有 5 個單元類別（改完建構子後） | 21／0（ManualTrigger 3、BusinessWeek 3、Claim 3、TenantScope 4、ScheduledJob 8） |
| 新單元類別 `SettlementGenerationTransactionBoundaryTest` | 4／0 |
| 新真實棧類別 | 3／0（修復前 3 個全紅） |
| 既有真實棧 `SettlementConcurrentClaimIntegrationTest` 回歸 | 2／0 |
| 突變驗證 | 基準 Z0＋8 組，全部 `KILLED(exactly must)` |
| 全量後端 `mvn -o clean verify`（全新資料庫） | `BUILD SUCCESS`，**19 分 07 秒**（Sprint 325 的 34 分 40 秒當時沒查原因，這次回到 Sprint 324 的 19 分左右，同樣沒查原因）；單元 **2478／0**（2474＋4）、整合 **1022／0**（1019＋3）、0 失敗／0 錯誤／0 跳過；Checkstyle main／test 0 違規，PMD 無違規輸出。`SettlementGenerationTransactionBoundaryTest` 4、`SettlementGenerationRaceRealStackIntegrationTest` 3、`SettlementConcurrentClaimIntegrationTest` 2、`SettlementScheduledJobIntegrationTest` 8、`SettlementGeneratorBusinessWeekTest` 3、`…ClaimTest` 3、`…ManualTriggerTest` 3、`SettlementTenantScopeTest` 4，與先前單跑一致。新真實棧類別在共用測試庫裡跑全部 ACTIVE 租戶的週結算，其他整合類別沒有受影響（整合 1022／0） |
| 文件守門（四個類別，改完所有文件後跑） | **14／0**：`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1 |
| push／雲端 CI | **已 push**：`6932928c..59cd8e72`（連同 Sprint 325 的純文件回填 commit `3392d816`），pre-commit 與 pre-push 輕量守門〔含 schema 漂移關卡〕都一次通過，沒有使用 `--no-verify`。**雲端 CI 全綠**：run [41961893990](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/41961893990)，三個 job 皆 success（Backend Unit Tests 3 分 2 秒／Frontend Lint & Build 1 分 10 秒／Backend Integration Tests & Package 11 分 13 秒），整個 run 14 分 21 秒，`gh run view --json` 確認 headSha 為 `59cd8e72`；下載整合 job 完整日誌（約 68 MB，去除 ANSI 色碼後解析）確認整合 **1022／0／0／0**、Checkstyle 0，`SettlementGenerationRaceRealStackIntegrationTest` 3、`SettlementConcurrentClaimIntegrationTest` 2 與本機逐類別一致（單元 job 的 `mvn test` 帶 `-q`，日誌不印案例數，單元案例數只核對了本機） |

本輪有生產程式碼變更（後端），未跑 Playwright 完整 E2E 與 `validate-schema-doc`（沒有前端或 schema 變更）。

## 6. 過程中的失誤與修正

1. **預測落空：(a) 不是『作業不出聲』而是『例外往外拋』。** 我登記的是『日誌只有每個租戶一行 Failed to generate、作業本身不出聲』，實測作業以 `UnexpectedRollbackException` 結束。原因是讀日誌後才弄清的：例外穿過 repository 的交易代理時，**共用的**外層交易被標成 rollback-only，迴圈吞掉例外也沒用。追蹤表與 §2 已據此更正。『全部租戶一起丟掉』這一半預測相符。
2. **`DEF-450` 登記的呼叫點數量不準**：Sprint 325 寫『10 處』（`SettlementScheduledJobIntegrationTest` 6 處），這次 `grep -n` 逐行列出是 12 處（該類別 8 處）。**我沒有查 Sprint 325 當時是怎麼數出 6 的**，差異原因不明；只能確定的是：開工時第一個 `grep -c` 就顯示該類別 8 行，與登記的 6 不符，所以改用 `grep -n` 逐行核對後才採用 12。數字已在追蹤表更正。
3. **差點把測試庫的假象登記成缺陷**：探針第一次輸出裡，系統租戶每次作業都 `Failed to generate … NullPointerException`（`commissionRate` 為 null），連控制組 A0 也有。登記前先查：正式 Flyway 庫的系統租戶（`V9`）沒指定 `commission_rate`，套用 `V1` 的 `DEFAULT 0.05`；測試庫是 Hibernate 建的沒有欄位預設。這是測試庫的假象，不是缺陷，沒有登記（§1.3）。它的副作用是 SM6 的 `T2` 紅在這個 NPE，而不是紅在我設計的失敗租戶 F 上——表中已如實標註。
4. **用 `awk` 數行長度印出一大片雜訊**：想確認我補丁後的建構呼叫有沒有超過 Checkstyle 的行長，用 macOS `awk` 的 `length`——它以位元組計，中文字被算成 3 倍，連既有檔案都「超長」。真正的規則（`checkstyle.xml` 的 `LineLength` 150 字元，只管主程式碼、測試設定檔沒有這條）用 Python 以**字元**核對：`SettlementGenerator.java` 0 行超過。
5. **`SM6` 的 `T2` 不是乾淨的歸因**（見 §4.3）：拿掉 `catch` 之後，測試庫系統租戶的 NPE 先讓作業中止。結論（失敗隔離確實消失）仍成立，但歸因到『F 的唯一約束』是不準的。`SM3`／`SM6` 的 `T3` 同為逾時紅。

## 7. 限制（誠實揭露）

- **(a)(b) 的觸發條件是我合成的，真實環境的發生頻率沒有數據。** (a) 用『結算單號被另一個期間的結算單佔用』製造資料庫層級失敗；真實世界可能的來源（死鎖、逾時、數值溢位、連線中斷）多久發生一次我不知道。(b) 的真實觸發是排程與手動補產另一期間**同時**認領同一批訂單（Monday 00:00 的窗口），同樣沒有頻率數據。證據只證明『一旦發生，後果如 §2』。
- **多節點同時跑排程是推論，沒有另外量測。** 在有 Flyway `V35` 唯一約束的環境，兩個節點處理同一租戶同一期間，輸家的 INSERT 被贏家擋住、撞號；修復前這會把輸家節點的整批回滾（贏家完成了所有租戶，結果碰巧正確），修復後輸家的該租戶交易重做、看到贏家的結算單並回傳。這與 `T1`（手動產生撞號）是同一個機制，但『兩個完整的週結算作業同時跑』沒有被測試過。
- **被回滾的租戶要等下週一才會被排程補產**（或由 `SUPER_ADMIN` 用手動補產端點立刻補）。本輪沒有加『立刻重試』：認領筆數不足代表另一個結算剛認領了這批訂單，重試也可能再碰到；而且重試次數與間隔是產品／維運決定。
- **新真實棧類別跑的是資料庫裡所有 ACTIVE 租戶的週結算**（共用測試庫），副作用是替其他類別留下的 ACTIVE 租戶產生上週的結算單並認領其上週前的已完成訂單。已 `grep` 確認沒有任何測試依賴 `settlement_statements` 的全表計數或全域未結算訂單；本類別自己的租戶在案例結束時一律改成 `SUSPENDED`，不會被之後的作業再碰到，也不會干擾下一個案例。
- **`T3`／`SM3`／`SM6` 的 `T3` 是逾時紅**（§4.3）；`SM9` 是外部等價突變、`SM7` 只有真實棧殺得掉——這些都是設計上的覆蓋層次，不是漏洞，但讀表時要知道紅燈的類型。
- 單元層沒有『修復前紅燈』（單元測試是修復後才寫的）；牙齒由突變驗證證明（`SM1`～`SM9`），修復前的紅燈由真實棧提供（§3）。
- `SettlementScheduledJobIntegrationTest` 的名字有誤導（它是 Mockito 單元測試），本輪沒有改名（Rule 3）。

## 8. 我替使用者做的決定（可一句話推翻）

1. **『請繼續』的解讀**：照總結自己寫的順序——先量測 `DEF-451`，接著做 `DEF-450`。**沒有碰**『還等你決定』的項目。
2. **量測屬實後把 `DEF-451` 也修了，並與 `DEF-450` 一起修**（總結只寫了『先量測 `DEF-451`』，修是我的延伸）。理由：①追蹤表本來就寫了修法（逐租戶一個交易）；②(a)(b) 都涉及金流（結算單的 GMV 重複計算、調整單重複套用），後果比 `DEF-450` 嚴重；③`DEF-450` 當初不修的唯一理由是『要改 `SettlementGenerator` 的建構子、牽動 5 個測試類別』，而 `DEF-451` 的修法需要同一個 `UniqueConflictRetry`，建構子只改一次，這個成本理由不再成立。想只量測、不修的話，可以一句話推翻，還原 `SettlementGenerator.java` 與 5 個測試類別即可。
3. **用 `UniqueConflictRetry`，不是新增 `PlatformTransactionManager`／`TransactionTemplate` 參數或另一個 bean**：已有前例（`DEF-441`／`DEF-297`／`DEF-448`），而且它同時給了撞號重做；另一個 bean 的做法（`BookingNoShowService`）要新增類別，Rule 2。
4. **不加『認領不足就立刻重試』**，也不改變失敗租戶『下週再補、或手動補產』的行為（§7）。
5. **不登記系統租戶的 NPE**（§1.3、§6 第 3 點）。
6. **`SchedulingConfig` 與維運手冊的『多個後端實例同時執行是安全的』**：保留。修復前這句話靠的是『冪等檢查＋原子認領』，在單一外層交易的結構下並不完全成立（§7 第 2 點）；修復後更接近成立，但仍是推論。

## 9. 新登記與更新的缺陷

| 項目 | 狀態 |
|---|---|
| `DEF-450` 結算單手動產生同一期間的並行請求，後到的回 `500 E-9900`（端點文件承諾冪等） | 🟢 → ✅ 已完成（Sprint 326）；追蹤表更正了建構子呼叫點數（10 → 12） |
| `DEF-451` 週結算作業以單一交易包住所有租戶（(a) 單一租戶資料庫失敗拖垮整批、(b) 認領不足的半套結算單） | 🟡 → ✅ 已完成（Sprint 326）；先量測屬實，修法與 `DEF-450` 同一個 |
| `DEF-452` 退款流程對 `settlementAdjustmentService.handleOrderRefund`／`handleBookingRefund` 的 `try/catch` 擋不住外層交易被標成 rollback-only（`PaymentStateService` 3 處） | 新登記，**讀碼推論、未量測**，未修；建議先量測 |

`DEF-452` 的來源：為 `DEF-451` 做同型盤點——`backend/src/main/java` 裡同時有 `catch (RuntimeException`／`catch (Exception` 與 `@Transactional` 的類別共 8 個，我逐一看過 catch 周邊程式碼：`BookingNoShowService`（外層不開交易、逐筆交易在 `BookingService`，**正確形狀，是本輪的前例**）、`SettlementReviewer`（`afterCommit`，`DEF-443` 已修）、`OAuthService`（解析 URI）、`NotificationService`／`NotificationConsumerService`（捕捉的是 Redis 佇列失敗，不是資料庫）、`AdminService.recordAudit`（吞掉稽核 `save` 的例外；`save` 對 UUID 主鍵通常延到 flush 才寫資料庫，所以多半捕捉不到資料庫錯誤，風險較低，沒登記）、`SettlementGenerator`（本輪修）、`PaymentStateService`（上表 `DEF-452`）。

## 10. 狀態

- **狀態**：實作、真實棧測試、突變驗證、全量 verify、文件均完成；追蹤表 `DEF-450`、`DEF-451` 轉 ✅，新登記 `DEF-452`。
- **push**：依 2026-10-29 長期授權（『做完一輪後，直接 Commit + Push to main』）直接 push，`6932928c..59cd8e72`，連同 Sprint 325 留在本機的純文件回填 commit `3392d816`；沒有詢問、沒有使用 `--no-verify`。**雲端 CI 全綠**（run 41961893990，見 §5）。
- **本回填 commit 只改文件，刻意不 push**，隨下一輪的 push 帶出（沿用 Sprint 322～325，免得『回填→push→再回填』無限循環）。
- **收尾 Docker 清理**：刪 4 個匿名卷（0 失敗），卷 1.112GB → 928MB，懸空映像回收 0B；保留 5 個具名卷（`act-toolcache`、`maven_cache`、`minio_data`、`postgres_data`、`redis_data`）與 3 個核准基底映像，建置快取 1.889GB 只回報不刪。Docker Desktop 開工時已在跑（不是我啟動的），沒有關閉。
- **仍等你決定**：`DEF-294`（購物車併發加入掉數量，已實測，值不值得修）、`DEF-449`（pre-push 守門驗主目錄）、`DEF-419`（運費模板的權限模型）、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取；以及 Sprint 325 留下的兩個觀察——PRD 資料字典與 `V1` 對 `po_number` 的唯一範圍不一致、`E-7006` 採購單號重複已定義卻從未被拋出。
- **下次說『請繼續』我可以直接做**：先量測 `DEF-452`（涉及金流退款；量測題，不是修復題；屬實的話修法比照 `DEF-443`）。

## 附錄 A：`DEF-451` 量測輸出原文（修改前，未修改的程式碼）

探針是一次性的（兩個類別 `Def451ProbeTmpIntegrationTest`、`Def451SchedulerProbeTmpIntegrationTest`，已刪除、未入庫）；它的固件已併入 §3 的永久測試類別，這裡留輸出原文當證據。`H1`／`H2` 是健康租戶，`F` 是結算單號被佔用的租戶，`B` 是認領不足的租戶；`lastMonday=2026-10-26`。

```text
PROBE> [P0] generator class=com.nextkey.ecommerce.core.settlement.SettlementGenerator$$SpringCGLIB$$0 isAopProxy=true lastMonday=2026-10-26
PROBE> [A0] findByStatus(ACTIVE) 的迭代順序＝ 0:other 1:H1 2:H2
PROBE> [A0] generateWeeklyStatements() 回傳；例外=無
PROBE> [A0] H1 orders=2 settledOrders=2 statements=[{statement_number=STL-680b71de-20261026, period_start=2026-10-26, total_orders=2, total_gmv=200.00, adjustment_amount=0.00, status=PENDING}]
PROBE> [A0] H2 orders=2 settledOrders=2 statements=[{statement_number=STL-0d1f804b-20261026, period_start=2026-10-26, total_orders=2, total_gmv=200.00, adjustment_amount=0.00, status=PENDING}]
PROBE> [A1] findByStatus(ACTIVE) 的迭代順序＝ 0:other 1:H1 2:F 3:H2
PROBE> [A1] generateWeeklyStatements() 回傳；例外=org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
PROBE> [A1] H1 orders=2 settledOrders=0 statements=[]
PROBE> [A1] F orders=2 settledOrders=0 statements=[{statement_number=STL-f52d3f2e-20261026, period_start=2023-10-26, total_orders=0, total_gmv=0.00, adjustment_amount=0.00, status=PENDING}]
PROBE> [A1] H2 orders=2 settledOrders=0 statements=[]
PROBE> [A2] findByStatus(ACTIVE) 的迭代順序＝ 0:other 1:H1 2:F 3:H2
PROBE> [A2] generateWeeklyStatements() 回傳；例外=org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
PROBE> [A2] H1 orders=2 settledOrders=0 statements=[]
PROBE> [A2] F orders=0 settledOrders=0 statements=[{statement_number=STL-ba24b06b-20261026, period_start=2023-10-26, total_orders=0, total_gmv=0.00, adjustment_amount=0.00, status=PENDING}]
PROBE> [A2] H2 orders=2 settledOrders=0 statements=[]
PROBE> [B1] 排程的 UPDATE orders 已卡在對手的列鎖上＝true
PROBE> [B1] generateWeeklyStatements() 回傳；例外=無
PROBE> [B1] B orders=2 settledOrders=2 statements=[{statement_number=STL-c9a90ac4-20200106, period_start=2020-01-06, total_orders=0, total_gmv=0.00, adjustment_amount=0.00, status=PENDING}, {statement_number=STL-c9a90ac4-20261026, period_start=2026-10-26, total_orders=2, total_gmv=200.00, adjustment_amount=-30.00, status=PENDING}]
PROBE> [B1] 訂單1 settled_statement_id→[STL-c9a90ac4-20261026]
PROBE> [B1] 訂單2 settled_statement_id→[STL-c9a90ac4-20200106]
PROBE> [B1] 調整單 status=[{status=PENDING, applied_statement_id=null}]
PROBE> [S0] method=SettlementGenerator.generateWeeklyStatements targetClass=SettlementGenerator$$SpringCGLIB$$0 isAopProxy=true
（S0 另外 5 個 @Scheduled 方法：BookingTimeoutService.cancelExpiredUnpaidBookings、RefundProcessingService.processPendingRefunds、NotificationConsumerService.consumeNotifications、NotificationConsumerService.processRetryQueue、OrderTimeoutService.cancelExpiredUnpaidOrders，targetClass 都不是代理，isAopProxy=false）
```
