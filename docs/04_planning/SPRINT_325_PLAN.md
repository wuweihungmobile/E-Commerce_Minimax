# Sprint 325 Plan — DEF-297：採購單號撞號沒有重試（含同型路徑盤點）

## 1. 起點與範圍

### 1.1 起點

[Sprint 324](SPRINT_324_PLAN.md) 收尾時，使用者把總結貼回來並說「請繼續完成任務！」，**沒有 `==>` 回覆**。這是無 `==>` 變體的第十五個實例（解讀為**推論**，不是使用者明說的）：

- 總結「還等你決定」的項目（`DEF-449` 守門工具鏈、`DEF-419` 權限模型、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取）**不在這句話的授權範圍內**，本輪不碰。
- 總結自己留下的唯一開放問題是：「追蹤表其餘的未完成項多數標為『不排入排程』，本輪沒有重新分類，所以我不確定現在有沒有不需要你決定、我能直接做的項目。」——所以本輪先做這件事，再挑其中 AI 能獨立處理的。

長期授權（2026-10-29，使用者明文：『做完一輪後，直接 Commit + Push to main，請記住』）：本輪 commit 後直接 push，連同 Sprint 324 留在本機的回填 commit `7817cf95` 一起帶出。

### 1.2 重新分類追蹤表（用腳本統計活躍區，不憑印象）

活躍區共 **137** 列：**94** 列已完成／已結案，**43** 列未完成。43 列依「誰能動它」分成四群：

| 群 | 列數 | 項目 | 為什麼 |
|---|---|---|---|
| 要使用者決定（產品／權限／基礎設施） | 11 | `DEF-306`（權限表與 PRD 出入）、`324`（後台共用版面）、`330`(a)、`337`（停權→恢復營業要不要自動重新上架）、`342`（排序參數：讓它生效還是從 API 移除）、`346`（Stripe 預設關閉，上線前必辦）、`348`（已由使用者決定維持）、`349`（接 LINE Pay 前必修）、`355`（Docker 檔案）、`419`（權限模型）、`449`（動守門行為＝基礎設施） | 追蹤表或 `CLAUDE.md` 明寫要人決定 |
| 已有結論，不需再動 | 4 | `DEF-025`（使用者決策不清理）、`193`（使用者拍板死欄位）、`387`（已決策維持現狀）、`422`（(a) 已修，(b) 兩處 `findAll()` 分析後明寫不修） | 本輪逐列讀過原文 |
| 死碼／零呼叫點／無可利用路徑 | 20 | `DEF-145/146/147`（生產不可達的死流程）、`169`–`177`（9 列）、`199`–`201`、`203`、`212`、`241`、`258`、`259` | 追蹤表逐項寫明零呼叫點或無可利用路徑 |
| **AI 能獨立處理、不需產品決策** | 8 | **`DEF-297`（本輪）**、`294`（購物車併發加入掉數量，**已實測**，修法牽動測試鷹架，追蹤表寫『是否值得修由你決定』）、`307`（通知消費速度，目前無人受影響）、`292`、`295`（Redis `KEYS`／重試佇列，**未量測**）、`334`（影響面極小）、`235`（低頻欄位讀後寫）、`236`（ERP 導覽入口缺失，非阻斷） | 價值都低於 `DEF-297`，或像 `294` 需要使用者先說值不值得 |

（11 + 4 + 20 + 8 ＝ 43，與腳本統計的未完成列數一致。）

**為什麼挑 `DEF-297`**：①有**真實失敗紀錄**（Sprint 215 全量 `verify` 撞過一次）；②不需要產品決策——追蹤表當年寫的『等使用者決定』是『值不值得修』，修法之一『撞號時重試』完全不改單號格式、不改 API、不需遷移；③程式庫有現成前例（`SupportTicketService`、`SettlementReversalService`，`DEF-441`／`DEF-448`）。**沒挑 `DEF-294`**：它是購物車的使用者體驗問題，修法要動測試鷹架（`IntegrationTestConfiguration` 把 `RedisTemplate` 換成 mock），追蹤表明寫『是否值得修由你決定』，我把它放進收尾建議而不是替使用者決定。

### 1.3 成功標準（開工時定義）

1. **紅燈先行**：把想要的行為寫成真實棧案例，先在**只加了接縫、尚未加重試**的生產碼上跑出紅燈（預測先登記於 §3.1）。
2. **配合程式庫既有慣例**（Rule 11）：公開方法**不加** `@Transactional`、`return uniqueConflictRetry.execute(() -> xxxOnce(...))`，唯一約束當最終仲裁者。
3. **單號格式不變**：仍是 `PO-{yyyyMMdd}-{6 位數}`；不加遷移、不改 API 回應形狀。
4. **修法有牙齒**：突變驗證預先登記每組『恰好該紅的案例』。
5. 全量後端 `mvn -o clean verify`（全新資料庫）通過、Checkstyle 0；push 後雲端 CI 全綠並核對整合測試數。
6. 追蹤表如實更新；過程中發現的相鄰問題**只登記、不順手修**（除非與本項同型且量測屬實，見 §2.3；**本輪最後沒有用到這個例外**，見 §6 第 4 點）。

### 1.4 動手前用工具核對的事實

| 說法（出處） | 核對結果 |
|---|---|
| 單號怎麼產生（追蹤表 `DEF-297`） | `PurchaseOrderService.generatePoNumber`（第 385 行）：`PO-{BASIC_ISO_DATE}-{ThreadLocalRandom.nextInt(100000, 999999)}`，每天 899,999 種後綴；全庫 `ThreadLocalRandom` 只有這一處（`grep`）。 |
| 唯一約束 | `purchase_orders.po_number VARCHAR(50) UNIQUE NOT NULL`（`V1`:383，**全平台**唯一、不分租戶）；之後沒有任何遷移碰它（`grep po_number db/migration/` 只有 `V1`）；實體 `@Column(unique = true)`，所以 Hibernate 建的整合測試庫**也有**這條約束（不像 Sprint 320 那樣需要自建索引）。 |
| 建單入口 | `createPurchaseOrder` 目前是 `@Transactional`（第 72 行）；**唯一**生產呼叫者是 `ErpController.createPurchaseOrder`（第 189 行，`@PreAuthorize("hasAuthority('STORE_OWNER')")`，回 `201`），呼叫時沒有外層交易。類別本身沒有類別層級 `@Transactional`。 |
| 主鍵 | `@GeneratedValue(strategy = GenerationType.UUID)`：應用端產生，**INSERT 在 flush（提交）時才執行**——撞號的例外在交易提交時才浮現，`UniqueConflictRetry` 整個重做的設計涵蓋這個時點。 |
| 撞號現在會怎樣 | `DataIntegrityViolationException` 沒有專屬處理，落到 `GlobalExceptionHandler` 的 `@ExceptionHandler(Exception.class)`（第 194 行）→ `500 E-9900`（與 Sprint 324 的 N1 同款）。 |
| `existsByPoNumber` | `PurchaseOrderRepository` 第 80 行宣告了，**全庫零呼叫者**（`grep`）。不用它：先查後寫在並行下一樣有視窗，最終仍要靠唯一約束仲裁，加了只是多一次查詢。 |
| `UniqueConflictRetry` 的限制 | 呼叫時**不能**已在交易內（已在交易內只執行一次、例外原樣往外拋）；`MAX_ATTEMPTS = 3`。 |
| 為什麼要接縫 | 單號是**隨機**的，測試無從預知會產生哪個號碼，就無法用前例的做法（『對手』交易先插入同號列但不提交，請求的 INSERT 卡在它的唯一索引項目上）確定性地製造真實撞號。程式庫對這類接縫沒有既定慣例：主程式碼沒有任何可注入的亂數／時間來源（`grep Clock|Supplier|RandomGenerator` 只有 `AccountTokenService` 一個 `SecureRandom` 欄位）；測試端 `ReflectionTestUtils.setField` 有 37 處（`grep` 統計欄位名）：多數是改 `@Value` 設定（`frontendBaseUrl` 7、`batchSize` 5、`allowedRedirectOriginsRaw` 4、`googleClientId` 3…），少數是替換注入的依賴（`entityManager` 3、`minioClient` 1）；沒有先例是用它換掉單號／亂數這類行為欄位。 |

**同型路徑盤點**（所有『業務編號』唯一欄位，用 `grep` 遷移檔的 `UNIQUE` 與 `generate…Number` 方法）：

| 欄位 | 產生方式 | 撞號處理 | 結論 |
|---|---|---|---|
| `purchase_orders.po_number` | `PO-日期-6 位隨機` | 無 | **`DEF-297`，本輪修** |
| `support_tickets.ticket_number` | 當日流水號 | `UniqueConflictRetry` | 已處理（`DEF-441`） |
| `credit_notes (tenant_id, credit_note_number)` | 開立日＋當日流水號 | `UniqueConflictRetry` | 已處理（`DEF-448`，Sprint 324） |
| `return_requests.return_number` | `RMA-日期-` ＋ `UUID` 前 8 碼十六進位（32 位元） | 無 | 撞號機率約 n²／8.6e9（每日 1000 筆約 1e-4），**不處理**，只記錄 |
| `settlement_statements (tenant_id, statement_number)` | `STL-{租戶前 8 碼}-{期間起日}`（**決定性**） | 手動端點與排程都沒有 | 見 §2.3，**待量測** |
| `inventory_checks.check_number` | 主程式碼零命中（`grep inventory_checks\|InventoryCheck`）：只有表、沒有程式 | — | 不適用 |
| `product_skus.sku_code`、`promo_codes.code`、各種 `slug` | 使用者輸入（`grep` 在含 `skuCode` 的行沒有 generate／random／UUID 字樣；**沒有逐一讀 SKU 建立流程**） | — | 不在範圍 |

## 2. 做法（開工前的設計，結果見後面各節）

### 2.1 生產碼

1. **接縫**（先加、單獨測試，行為不變）：`PurchaseOrderService` 新增一個非 `final` 欄位 `poNumberSuffix`（`IntSupplier`，預設就是現在的 `ThreadLocalRandom.nextInt(...)`）。這是**只為測試存在的接縫**，Javadoc 明寫；整合測試用 `ReflectionTestUtils.setField` 換成固定序列（Spring 的 `ReflectionTestUtils` 會自動解開代理）。為什麼不用別的：新增一個產生器類別＝為單次使用建抽象（Rule 2）；`mockStatic(ThreadLocalRandom)` 只對當前執行緒生效、而且會攔掉 JDK 內部（`ConcurrentHashMap`、`LongAdder`）對同一個類別的靜態呼叫，太脆。
2. **修復**：`createPurchaseOrder` 拿掉 `@Transactional`，改成 `return uniqueConflictRetry.execute(() -> createPurchaseOrderOnce(request, userId))`；原本的方法本體搬進私有的 `createPurchaseOrderOnce`，**每次重做都重新產生單號**。建構子多一個 `UniqueConflictRetry`。

### 2.2 測試

- 新類別 `BusinessNumberRaceRealStackIntegrationTest`（真實 PostgreSQL＋MockMvc 走完整 HTTP／JWT／權限鏈）。輔助方法（`HeldTransaction`、`awaitInsertBlockedOn`、`send`…）沿用程式庫現行做法：三個既有競態測試類別各帶一份私有副本，新類別照做第四份（不去動既有三份——Rule 3；重複是已知的技術債，不在本輪處理）。
- 單元：`PurchaseOrderServiceTest` 加撞號重做案例＋結構守門（反射檢查沒有 `@Transactional`，Sprint 324 同款）。

### 2.3 同型的『結算單手動產生』（本輪只量測，量測屬實再決定）

讀碼看到 `SettlementGenerator.generateStatementManually`（`@Transactional`）：『同租戶＋期間已有結算單就回傳既有的』的檢查，和後面的 INSERT 之間沒有鎖；結算單號又是決定性的（`STL-{租戶前 8 碼}-{期間起日}`），所以同一期間的兩個並行請求會撞 `(tenant_id, statement_number)`。既有的 `SettlementConcurrentClaimIntegrationTest` 類別 Javadoc 明寫它涵蓋的是『**期間不同**、不會被同期間冪等檢查擋下』的並行——**同一期間**的並行沒有測試量過。

- **只在量測屬實時才修**，修法是同一個模式（`UniqueConflictRetry`，重做時冪等檢查會看到贏家的結算單並直接回傳）。**結果**：量測屬實（`500`），但修法要改 5 個測試類別 10 處既有的建構子呼叫，改為登記 `DEF-450`、本輪不修（§6 第 4 點、§8 第 4 點）。
- 整合測試庫（Hibernate 建的）**沒有** `(tenant_id, statement_number)` 唯一約束（實體 `@Table` 沒宣告，只有 Flyway `V35` 有）——案例要自建同形索引（只涵蓋本案例的租戶），收尾移除，做法同 Sprint 319 的聊天案例。
- **另外讀碼看到、本輪不量測不修**：`generateWeeklyStatements` 是 `@Scheduled @Transactional`，一個交易包住**所有租戶**的迴圈，逐租戶的 `try/catch` 在同一個交易內。依 `DEF-310`／`DEF-441` 的機制（PostgreSQL 語句失敗後整個交易中止），推論：某個租戶發生資料庫層級失敗時，後面所有租戶都會失敗、整批回滾；而 `IllegalStateException`（認領筆數不足）被迴圈吞掉後，交易照常提交，『回滾整張結算單』的註解承諾不成立。**這是讀碼推論，沒有量測**，登記時照實標示。

## 3. 修改前的紅燈（只加接縫、尚未加重試的生產碼，真實 PostgreSQL）

### 3.1 預測（跑之前登記；尚未執行任何新案例）

紅燈基準＝**只加接縫、行為不變**的生產碼（沒有接縫，測試無法確定性地製造撞號，也無法對『尚未修復』的程式碼跑出紅燈）。

| 案例 | 預測 |
|---|---|
| `PO1` 撞號輸家：『對手』交易先插入 `PO-今天-X` 但不提交；接縫第一次回 `X`、第二次回全新的 `Y`；送出建單請求 | **紅**：請求 INSERT 卡在對手的唯一索引項目上（`pg_stat_activity` 看得到，這一步與修復無關、應通過）；對手提交後請求失敗 → **`500 E-9900`**；接縫被呼叫 **1 次**；資料庫只剩對手那一列、沒有 `Y`、沒有任何採購單品項 |
| `PO2` 對照：沒有撞號的正常建單，接縫回全新的 `Z` | **綠**（基準就綠，用來讓『多重試一次』的突變有東西可殺）：`201`、單號 `PO-今天-Z`、接縫被呼叫 1 次 |
| `ST1` 結算單手動產生輸家：『對手』交易先插入同租戶同期間的結算單但不提交；`POST /v2/admin/settlements/generate` | **紅**（**預測，未驗證**）：請求通過冪等檢查（看不到對手未提交的列）→ INSERT 卡住 → 對手提交後 **`500 E-9900`**；資料庫只剩對手那一列 |
| 單元：撞號（`save` 第一次拋 `DataIntegrityViolationException`）→ 整個重做、第二次用新單號 | **紅**：沒有重試元件，例外直接穿出 |
| 單元：`createPurchaseOrder` 與類別都沒有 `@Transactional` | **紅**：方法上有 |

修復後預測：`PO1` 轉綠（`201`、單號 `PO-今天-Y`、接縫被呼叫 2 次、資料庫兩列、品項恰好 1 組）；`PO2` 仍綠；單元兩個案例轉綠；`ST1` 依量測結果。

### 3.2 實測結果（只加接縫、尚未加重試的生產碼，真實 PostgreSQL）

第一次跑 `Tests run: 3, Failures: 3`，其中**對照組 `PO2` 的紅是我的固件錯誤**（§6 第 1 點），不是競態；更正固件後第二次跑 `Tests run: 3, Failures: 2`，與預測逐格相符：

| 案例 | 預測 | 實測 |
|---|---|---|
| `PO1` 撞號輸家 | 紅：`500 E-9900`；接縫被呼叫 1 次；資料庫只剩對手那一列 | **紅，吻合（前半）**：請求的 INSERT 卡在對手的唯一索引項目上（通過 `awaitInsertBlockedOn`），對手提交後 `expected: 201 but was: 500`，body 的 `code` 是 `E-9900`；日誌是真實的唯一索引撞號：`duplicate key value violates unique constraint … Key (po_number)=(PO-20261104-752365) already exists`。**後半（接縫被呼叫 1 次、資料庫只剩對手那一列）被前面失敗的斷言短路，沒有執行，屬推論**（Sprint 291 同款：紅燈只證明被斷言到的那一段） |
| `PO2` 對照 | 綠 | 第一次**紅（預測落空）**：`500`，根因是固件重複插入 `ERP_ENABLED`（§6）；更正後**綠** |
| `ST1` 結算單手動產生輸家 | 紅（**預測，未驗證**） | **紅，吻合**：`expected: 200 but was: 500`，`code` 是 `E-9900`；日誌：`DataIntegrityViolationException: … duplicate key value violates unique constraint "idx_it_num_race_stmt_…" Key (tenant_id, statement_number)=(…, STL-a17abb54-20300107) already exists`，從 `SettlementGenerator.generateStatementManually`（經 CGLIB 代理）的交易提交處拋出 → 登記為 `DEF-450`（量測屬實、本輪不修，§2.3、§8 第 4 點） |
| 單元兩個案例 | 紅 | **沒有在只加接縫的版本上跑過**：單元測試是修復之後才寫進來的。它們的牙齒由突變驗證證明（§4.3：PM1／PM7 → 撞號案例紅、PM2 → 結構守門紅），**單元層沒有做『修復前紅燈』，由突變取代** |

## 4. 修改後與突變驗證

### 4.1 修改

| 檔案 | 改動 |
|---|---|
| `core/erp/PurchaseOrderService.java` | ①新增只為測試存在的接縫 `poNumberSuffix`（`IntSupplier`，預設＝原本的 `ThreadLocalRandom.nextInt(...)`）；②建構子多一個 `UniqueConflictRetry`；③`createPurchaseOrder` 拿掉 `@Transactional`，變成 `return uniqueConflictRetry.execute(() -> createPurchaseOrderOnce(request, userId))`，原本的本體搬進私有的 `createPurchaseOrderOnce`（單號在裡面產生，所以**每次重做都換新單號**）；④`generatePoNumber` 讀接縫。單號格式、錯誤碼、回應形狀、資料表都沒有動 |
| `test/…/core/erp/PurchaseOrderServiceTest.java` | `@Spy UniqueConflictRetry`（真的元件＋mock 的交易管理員，前例同 `SettlementReversalServiceTest`）；新增 2 案例：撞號整個重做且換新單號（含『供應商前置檢查也跟著再做一次』）、`createPurchaseOrder` 與類別都沒有 `@Transactional`（結構守門） |
| `test/…/integration/BusinessNumberRaceRealStackIntegrationTest.java`（新） | 真實棧 2 案例：`PO1` 撞號輸家、`PO2` 對照組 |
| `docs/…/api/API_M16_ERP.md` | v1.1：`poNumber` 欄位說明、§4.7 補撞號段落、§7 已知限制列改寫（原本寫『無資料庫層級的重試保證』，且夾了一個不存在的 `[[…]]` 連結） |
| 追蹤表、`RELEASE_TRACKER.md`、本計畫書 | `DEF-297` 轉完成、新登記 `DEF-450`／`DEF-451`、Sprint 325 row |

### 4.2 單元與真實棧

- 單元 `PurchaseOrderServiceTest` 39 → **41／0**。
- 新真實棧類別 **2／0**。日誌顯示完整序列：INSERT 撞真實唯一約束（`uk_…` 開頭的約束名）→ `UniqueConflictRetry: Unique constraint race lost, re-running the whole transaction: attempt=1/3` → 同一個請求（同一個 requestId）再 INSERT 一次。
- 碰採購單建立的既有類別回歸：`M16ErpIntegrationTest` 41、`M16ErpE2ETest` 7、`M16ErpInventoryConcurrencyIntegrationTest` 5（當年撞號的那個類別）、`SkuManagementIntegrationTest` 2、`AdminControllerE2ETest` 24 ＝ **79／0**（連同新類別共 81／0）。

### 4.3 突變驗證（預先登記：規格檔先寫好 `must`，跑完逐一比對）

受測集合＝`PurchaseOrderServiceTest`（41）＋`BusinessNumberRaceRealStackIntegrationTest`（2）＝ 43 案例。驅動由我依記憶檔的設計重寫（前幾輪放在 scratchpad 的版本已被清空）：備份 → 套用（`old` 必須恰好出現一次）→ 刪掉被改檔案的 `.class` 逼 Maven 重編 → 跑單元＋整合 → 還原 → `cmp`／SHA-256；判決＝失敗集合與 `must` **恰好**相同。

| 突變 | 內容 | 預先登記的 `must`（恰好該紅） | 結果 |
|---|---|---|---|
| Z0 | 無突變（基準，全新資料庫） | （空）；`ran` 必須是 43 | `ran=43 failed=0` ✅ |
| PM1 | 拿掉重試包裝，直接呼叫 `createPurchaseOrderOnce`（等於沒有交易也沒有重試） | 單元撞號案例；真實棧 `PO1` | **KILLED(exactly must)**：單元＝`DataIntegrityViolationException` 穿出；真實棧＝`expected: 201 but was: 500`（`E-9900`）。對照組 `PO2` 仍綠——沒有交易的建單照常成功，所以只有撞號案例殺得到它 |
| PM2 | 把 `@Transactional` 加回公開方法（重試元件不再是最外層，只執行一次） | 單元結構守門；真實棧 `PO1` | **KILLED(exactly must)**：結構守門＝`expected: null but was: @Transactional(…)`；真實棧＝`500`。**單元撞號案例仍綠**（單元直接 new 服務，沒有 Spring 代理，`@Transactional` 無效）——所以結構守門不可省（Sprint 324 的 EM2 同款） |
| PM3 | 單號在重做區塊外只產生一次，重做沿用剛撞號的那個 | 單元撞號案例；真實棧 `PO1` | **KILLED(exactly must)**：單元＝兩次嘗試的單號都是 `PO-…-111111`；真實棧＝重做再撞同一個對手，用完 3 次後 `500` |
| PM4 | 每次嘗試單號被產生兩次（第一個值丟掉） | 單元撞號案例；真實棧 `PO1`、`PO2` | **KILLED(exactly must)**：`PO2`＝`[單號被產生的次數] expected: 1 but was: 2`；單元＝兩次嘗試的單號變成 `[…222222, …222222]`；`PO1`＝逾時（`等不到對 purchase_orders 的 INSERT 卡在…`，因為第一次嘗試用的是第二個值，根本沒撞號、請求不會卡住） |
| PM7 | 重試上限 `MAX_ATTEMPTS` 3 → 1 | 單元撞號案例；真實棧 `PO1` | **KILLED(exactly must)**：單元＝`DataIntegrityViolationException`；真實棧＝`500` |
| PM8 | 把供應商前置檢查搬出重做區塊（重做不再是『整個重做』） | 單元撞號案例；單元 `createPurchaseOrder_erpFeatureDisabled_throwsAndDoesNotSave` | **KILLED(exactly must)**：撞號案例＝`supplierRepository.existsByIdAndTenantId(…) Wanted 2 times … was 1`；`erpFeatureDisabled`＝`expected: E_2004`（前置檢查跑到 `ERP_ENABLED` 檢查前面，改拋 `E_7008`）。**真實棧看不到這個突變**（重做時不再檢查供應商，外部行為不變），只有單元的 `times(2)` 守得住 |

編號 PM5、PM6 跳號：想過『單號格式破壞』（既有單元斷言 `matches("PO-\\d{8}-\\d{6}")` 已守）與『接縫失效』（只會讓測試自己失效，不是對修復的突變），捨棄，編號不重排以免與規格檔對不上。

### 4.4 結果

7 次執行共約 17 分鐘（Z0 含重建資料庫 262 秒；PM7 285 秒——它的 Maven 日誌有 `Compiling 387 source files`／`324 source files`〔全部重編〕，其他組沒有〔IDE 先預編了〕，但多出的時間用在哪我沒有進一步查；其餘 76～108 秒）。**六組突變全部 `KILLED(exactly must)`，預先登記逐一吻合**；每組 `restoredIdentical=True`；結束後四個檔案的 SHA-256 與突變前逐一相同、突變殘留字串 `grep` 皆為 0、`git status` 只有預期的檔案。紅燈原因都讀過失敗訊息（上表『結果』欄的引文就是來源），不只看紅綠。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 單元 `PurchaseOrderServiceTest` | 41／0（39＋2） |
| 新真實棧類別 | 2／0；日誌確認撞真實唯一約束、`UniqueConflictRetry` 重做 |
| 既有相關類別回歸 | 79／0（連同新類別共 81／0） |
| 突變驗證 | 基準 Z0＋6 組，全部 `KILLED(exactly must)` |
| 全量後端 `mvn -o clean verify`（全新資料庫） | `BUILD SUCCESS`，34 分 40 秒（比 Sprint 324 的 19 分長不少，沒有查原因）；單元 **2474／0**（2472＋2）、整合 **1019／0**（1017＋2）、0 失敗／0 錯誤／0 跳過；Checkstyle main／test 0 違規、PMD 通過。`PurchaseOrderServiceTest` 41、`BusinessNumberRaceRealStackIntegrationTest` 2、`M16ErpIntegrationTest` 41、`M16ErpE2ETest` 7、`M16ErpInventoryConcurrencyIntegrationTest` 5、`AdminControllerE2ETest` 24、`SkuManagementIntegrationTest` 2，與先前單跑一致 |
| 文件守門（四個類別，改完所有文件後跑） | **14／0**：`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1 |
| push／雲端 CI | **已 push**：`ce23f3e1..6932928c`（連同 Sprint 324 的純文件回填 commit `7817cf95`），pre-commit 與 pre-push 輕量守門〔含 schema 漂移關卡〕都一次通過，沒有使用 `--no-verify`。**雲端 CI 全綠**：run [41848238162](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/41848238162)，三個 job 皆 success（Unit 3 分 9 秒／Frontend 1 分 53 秒／Integration 7 分 28 秒，整個 run 10 分 44 秒），`gh run view --json` 確認 headSha 為 `6932928c`；下載整合 job 完整日誌（約 62 MB）確認整合 **1019／0／0／0**、Checkstyle 0，新類別 2、`M16ErpIntegrationTest` 41、`M16ErpInventoryConcurrencyIntegrationTest` 5、`M16ErpE2ETest` 7、`AdminControllerE2ETest` 24、`SkuManagementIntegrationTest` 2 與本機逐類別一致，日誌可見真實的 `Key (po_number)=(PO-…) already exists` 與 `UniqueConflictRetry … attempt=1/3`（單元 job 的 `mvn test` 帶 `-q`，日誌不印案例數，單元案例數只核對了本機） |

本輪有生產程式碼變更（後端），未跑 Playwright 完整 E2E 與 `validate-schema-doc`（沒有前端或 schema 變更）。

## 6. 過程中的失誤與修正

1. **預測落空：對照組 `PO2` 基準不是綠而是 `500`。** 根因是我的固件錯誤：我在 `setUp` 手動插了一筆 `ERP_ENABLED` 功能開關，但建立店鋪時系統已自動種好 6 個預設開關（`ERP_ENABLED` 預設啟用），同一家店出現兩筆同鍵開關，`FeatureToggleService` 的單筆查詢拋 `IncorrectResultSizeDataAccessException`。先讀日誌的真實例外與資料庫現況（`SELECT … FROM tenant_feature_toggles`，7 列、時間戳證明 6 列是店鋪建立時一起種的）才定位，沒有憑猜測改。拿掉多餘插入、留註解說明後重跑。**同一個坑害到 `PO1`**：請求在到達 INSERT 之前就失敗，所以 `PO1` 第一次是『等不到請求卡在 INSERT』的逾時，不是競態。
2. **`PM8` 的 `must` 第一版漏了一格**：把供應商檢查搬出去會讓它跑在 `ERP_ENABLED` 檢查前面，所以既有的 `erpFeatureDisabled` 案例也會紅（mock 的供應商預設回 `false`，先拋 `E_7008`）。在**跑之前**逐案例推演時發現並更正，所以仍算預先登記；實測吻合。
3. **計畫書與追蹤表各算錯一次數字，寫完核對時抓到**：§1.2 的分群我先寫死碼群 22／AI 可處理群 6（實際 20／8，漏列 `DEF-236`、把 `292`／`295` 合成一項），用 `11＋4＋20＋8＝43` 對腳本統計的 43 才發現；追蹤表 `DEF-297` 列先寫『新類別 2、既有回歸 81』，81 其實已含新類別的 2（既有是 79）。
4. **範圍決定在量測後改了**：§2.3 原本寫『量測屬實就修 `ST1`』。量測屬實（500）之後才去數修法的代價：`SettlementGenerator` 被手動 `new` 的地方有 **5 個測試類別、10 個呼叫點**（先前讀碼時我沒數），加建構子參數要同步改，為了一個『管理員連點兩下』的罕見情境不划算，也偏離本輪主題。改為登記 `DEF-450`（附證據與建議修法）、`ST1` 案例從類別移除（免得提交一個必紅的測試），原文存附錄 A。
5. **雙重背景化再犯一次**：回歸測試我用了 `nohup … &` 又設 `run_in_background:true`，通知立刻宣告『完成』（Maven 其實還在跑）。這是記憶檔記過的坑；這次沒有信通知，改看日誌的 `BUILD SUCCESS` 行。
6. 前幾輪 scratchpad 裡的突變驅動已被清空（建立時間是今天），依記憶檔的設計重寫了一份。

## 7. 限制（誠實揭露）

- **只處理了『撞號之後的後果』，沒有降低撞號的機率。** 全平台每天 899,999 種後綴、約束是全平台唯一（不是 PRD 資料字典寫的『Tenant 內唯一』），撞號機率仍是 約 n／900,000（n＝當天已建採購單數）。有重試之後這只是多一次交易；若平台每天建立的採購單數到十萬等級，隨機 6 位數這個格式本身就不夠用，那是另一個決定。
- **連續 3 次都撞號仍是 `500 E-9900`**（機率約 (n／900,000)³，n＝1000 時約 1e-9）。`API_Error_Codes.md` 另列了 `E-7006` 採購單號重複〔409〕，`ErrorCode` 與 `GlobalExceptionHandler` 的狀態碼對照也有，但**程式碼從未拋出它**（`grep` 核對，只剩定義與對照表兩處）；本輪不接線。
- **接縫是只為測試存在的可變欄位**（非 `final`、非 `volatile`）。整合測試在送出請求前設定、結束後還原，不會與請求執行緒競爭；若將來有人在生產碼改它，Javadoc 已警告。
- **`PO1` 的後半預測（接縫被呼叫 1 次、資料庫只剩對手那一列）在紅燈階段被前面的斷言短路，沒有被執行**，那兩項是推論；修復後的綠燈階段這兩項有被斷言（`generated == 2`、單號集合 `containsExactlyInAnyOrder`）。
- **單元層沒有『修復前紅燈』**，由突變取代（§3.2）。
- **單號的日期用 `LocalDate.now()`（JVM 預設時區）**，沿用原本語意；測試用同一個函式算對手的單號，只有在午夜前後的毫秒級視窗會對不上（Sprint 324 同款的假設）。
- **`DEF-450` 的探針是一次性的**（真實棧、真實日誌，但沒有入庫），程式碼附在附錄 A；**`DEF-451` 完全是讀碼推論**，沒有任何量測。
- `API_M16_ERP.md` §7 原本那一列夾了一個 `[[po-number-random-collision-def297]]` 形式的連結（記憶檔的連結語法，文件裡不存在這個目標），已隨改寫移除。

## 8. 我替使用者做的決定（可一句話推翻）

1. **『請繼續』的解讀**：先重新分類追蹤表（因為總結自己說不確定有沒有我能做的項目），再挑 AI 能獨立處理者。**沒有碰** `DEF-306`／`324`／`330`／`337`／`342`／`346`／`348`／`349`／`355`／`419`／`449` 這 11 個要使用者決定的項目。
2. **挑 `DEF-297`、不挑 `DEF-294`（購物車併發加入掉數量）**：`DEF-294` 已實測屬實，但修法要動測試鷹架，追蹤表明寫『是否值得修由你決定』——我把它留給你。想要的話，下次說一聲。
3. **`DEF-297` 只做『撞號重試』、不改單號格式、不改資料表**（追蹤表當年列的另一個選項『改用資料庫序列』會讓單號從隨機變成連續——全平台共用的序列會讓每家店看得出平台的採購單量——而且是格式變更，屬於要你決定的範圍）。
4. **`DEF-450`（結算單手動產生同期競態）只登記、不修**，理由見 §6 第 4 點。
5. **接縫的選擇**：在 `PurchaseOrderService` 加一個測試用的非 `final` 欄位，而不是新增產生器類別（Rule 2）、`mockStatic(ThreadLocalRandom)`（只對當前執行緒生效，而且會攔掉 JDK 內部對同一個類別的靜態呼叫）、或用資料庫觸發器模擬唯一約束違反（不是真實撞號）。
6. **不接線 `E-7006`、不對齊 PRD 資料字典的『Tenant 內唯一』與 `VARCHAR(30)`、不改資料庫約束範圍**——這些是產品／資料模型的決定，已寫進 §7 與追蹤表供你裁決。
7. **`return_number`（`RMA-日期-` ＋ UUID 前 8 碼）不處理**：32 位元隨機，每天 1000 筆約 1e-4 的撞號機率，是 `po_number` 的五千分之一。

## 9. 新登記與更新的缺陷

| 項目 | 狀態 |
|---|---|
| `DEF-297` 採購單號撞號沒有重試 | 🟢 → ✅ 已完成（Sprint 325） |
| `DEF-450` 結算單手動產生同一期間的並行請求，後到的回 `500 E-9900`（端點文件承諾冪等） | 新登記，**已量測屬實**，未修；修法與成本寫在追蹤表（5 個測試類別、10 個建構子呼叫點） |
| `DEF-451` 週結算排程以單一交易包住所有租戶，逐租戶 `try/catch` 擋不住交易中止，也擋不住『認領筆數不足』的半套寫入 | 新登記，**讀碼推論、未量測**，未修；建議先量測 |
| （觀察，寫在 `DEF-297` 列與 §7）PRD 資料字典與 `V1` 對 `po_number` 的唯一範圍不一致；`E-7006` 從未被拋出 | 未另立編號，等使用者裁決 |

## 10. 狀態

- **狀態**：實作、真實棧測試、突變驗證、全量 verify、文件均完成；追蹤表 `DEF-297` 轉 ✅，新登記 `DEF-450`、`DEF-451`。
- **push**：依 2026-10-29 長期授權（『做完一輪後，直接 Commit + Push to main』）直接 push，`ce23f3e1..6932928c`，連同 Sprint 324 留在本機的純文件回填 commit `7817cf95`；沒有詢問、沒有使用 `--no-verify`。**雲端 CI 全綠**（run 41848238162，見 §5）。
- **本回填 commit 只改文件，刻意不 push**，隨下一輪的 push 帶出（沿用 Sprint 322～324，免得『回填→push→再回填』無限循環）。
- **收尾 Docker 清理**：刪 4 個匿名卷（0 失敗），卷 1.112GB → 928MB，懸空映像回收 0B；保留 5 個具名卷與 3 個核准基底映像，建置快取 1.889GB 只回報不刪。Docker Desktop 開工時沒在跑、是我啟動的，沒有關閉。
- **下次說『請繼續』我可以直接做**：`DEF-450`（結算單手動產生同期競態，**已量測屬實**，附錄 A 有紅燈起點）；`DEF-451` 先量測（量測題，不是修復題）。
- **仍等你決定**：`DEF-294`（購物車併發加入掉數量，已實測，值不值得修）、`DEF-449`（pre-push 守門驗主目錄，動守門屬基礎設施變更）、`DEF-419`（運費模板的權限模型）、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取；以及本輪新看到的兩個觀察——PRD 資料字典與 `V1` 對 `po_number` 的唯一範圍不一致（Tenant 內 vs 全平台）、`E-7006` 採購單號重複已定義卻從未被拋出。

## 附錄 A：`DEF-450` 探針原文（紅燈起點）

這是 Sprint 325 在**未修改的 `generateStatementManually`** 上量到 `500 E-9900` 的測試方法原文（曾放在 `BusinessNumberRaceRealStackIntegrationTest`，因本輪決定不修 `DEF-450` 而移除，免得提交一個必紅的測試）。下次修 `DEF-450` 時貼回該類別當紅燈起點。還需要的其餘部分：

- 常數 `GENERATE_STATEMENT_PATH = "/v2/admin/settlements/generate"`；
- `private final List<String> createdIndexes = new ArrayList<>();`（及 `import java.util.ArrayList;`），`@AfterEach` 裡 `createdIndexes.forEach(name -> jdbcTemplate.execute("DROP INDEX IF EXISTS " + name));`；
- 整合測試庫是 Hibernate 建的，`(tenant_id, statement_number)` 唯一約束只有 Flyway `V35` 有——方法內自建了只涵蓋該案例租戶的同形索引。

```java
    // ── 結算單手動產生（SettlementGenerator.generateStatementManually）────────────────────────

    /**
     * 兩個「手動產生同一期結算單」的請求同時送出（例如管理員連點兩下）：兩個都通過『這一期是否已有結算單』的檢查，各自 INSERT，
     * 輸的那個撞上 {@code (tenant_id, statement_number)} 唯一約束（結算單號是決定性的：{@code STL-租戶前 8 碼-期間起日}）。
     * 這個端點的文件承諾是冪等（同租戶＋期間已有結算單就直接回傳既有的），所以輸家要回 {@code 200} 和贏家的那一張，不是 500。
     *
     * <p>整合測試資料庫是 Hibernate 建的，實體沒有宣告這條唯一約束（只有 Flyway V35 有）；這裡建一個只涵蓋本案例租戶的同形索引，
     * 收尾移除，不影響其他測試。
     */
    @Test
    @DisplayName("結算單手動產生：同租戶同期間被對手搶先建立（唯一約束衝突）→ 200 且回傳贏家的那張結算單，不是 500；只剩一張")
    void generateStatementManually_losingTheSamePeriodRace_returnsTheWinnersStatement() throws Exception {
        String tenant = fx.store.getId().toString();
        // 遙遠未來的一個週一：不會跟其他測試的結算期間重疊
        String statementNumber = "STL-" + tenant.substring(0, 8) + "-20300107";
        String indexName = "idx_it_num_race_stmt_" + tenant.replace("-", "");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + indexName
                + " ON settlement_statements (tenant_id, statement_number) WHERE tenant_id = '" + tenant + "'");
        createdIndexes.add(indexName);
        UUID winners = UUID.randomUUID();

        // 對手：同一期的結算單已經寫入但還沒提交
        HeldTransaction rival = holdTransaction(jdbc -> jdbc.update("""
                INSERT INTO settlement_statements
                    (id, tenant_id, statement_number, period_start, period_end, total_orders, total_gmv,
                     total_refunds, commission_amount, net_settlement_amount, currency, status,
                     generated_at, created_at, updated_at)
                VALUES (?, ?, ?, DATE '2030-01-07', DATE '2030-01-13', 0, 0.00, 0.00, 0.00, 0.00,
                        'TWD', 'PENDING', NOW(), NOW(), NOW())
                """, winners, fx.store.getId(), statementNumber));
        MockHttpServletResponse loser;
        try {
            Future<MockHttpServletResponse> request = pool.submit(() -> send(
                    post(GENERATE_STATEMENT_PATH).param("tenantId", tenant).param("periodStart", "2030-01-07"),
                    fx.adminToken));
            awaitInsertBlockedOn("settlement_statements");
            Assertions.assertThat(request.isDone()).as("輸家的請求應該卡在對手的唯一索引項目上").isFalse();
            rival.commit();
            loser = request.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            rival.release();
        }

        Assertions.assertThat(loser.getStatus()).as("輸家的回應：%s", body(loser)).isEqualTo(200);
        Assertions.assertThat(json(loser).path("data").path("id").asText())
                .as("輸家應該拿到贏家的那張結算單：%s", body(loser)).isEqualTo(winners.toString());
        Assertions.assertThat(json(loser).path("data").path("statementNumber").asText()).isEqualTo(statementNumber);
        Integer statements = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM settlement_statements WHERE tenant_id = ?", Integer.class, fx.store.getId());
        Assertions.assertThat(statements).as("這家店的結算單張數").isEqualTo(1);
    }

```
