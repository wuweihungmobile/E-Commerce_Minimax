# Sprint 319 Plan — DEF-441（唯一約束競態輸了的恢復路徑在 PostgreSQL 上無效）：先用真實競態重現四處、再修

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 318](SPRINT_318_PLAN.md) 的總結，加一句『請繼續完成任務！』，**沒有任何 `==>` 回覆，也沒有互動選項的答案**。我的解讀（推論，不是使用者說的）：只做總結自己列為『下次說「請繼續」我可以直接做的』那一項——`DEF-441`（追蹤表標『低風險，不需產品決策』）：先把探針正式化為入庫的真實競態案例（紅燈先行），再修恢復路徑，另外三處同型寫法先逐一探針確認。不碰：`DEF-419`、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取（都是使用者的），也不做兩個『可選量測』（需使用者決定）。

長期授權（2026-10-29，使用者明文：『做完一輪後，直接 Commit + Push to main，請記住』）：本輪 commit 後直接 push，不再詢問。Sprint 318 的純文件回填 commit `ef4bd309`（領先遠端 1 個）是本輪 commit 的祖先，會隨這次 push 一起帶出。

### 1.2 成功標準（開工時定義）

1. **紅燈先行、而且是真實競態**：每一處都先在未修改的程式碼上用真實 PostgreSQL 重現，紅燈的原因必須是『唯一約束違反 → 交易中止』，不是測試本身的問題（對照伺服器日誌）。
2. **修法不在中止的交易裡恢復**：全庫 `catch (DataIntegrityViolationException)` 逐一核對，沒有任何一個還在 catch 內碰資料庫。
3. **每個修法都有牙齒**：突變預先登記『恰好該紅的案例』與失敗訊息方向，結果逐一比對；特別要證明『在同一個交易裡重試』這個壞寫法被擊殺。
4. 全量後端 `mvn verify` 通過、Checkstyle 0；push 後雲端 CI 全綠並核對整合測試數。
5. 追蹤表如實更新；過程中發現的相鄰問題只登記、不順手修。

### 1.3 動手前後用工具核對的事實

| 說法（出處） | 核對結果 |
|---|---|
| 追蹤表 `DEF-441`：`RoomCalendarService.fillMissingCalendarEntries` 的恢復路徑在 PostgreSQL 上必然失敗（Sprint 318 的一次性探針） | **入庫的真實競態案例重現，紅燈**：`500 E-9900`，日誌依序是唯一約束違反 → `fillMissingCalendarEntries: Unique constraint violation` → `current transaction is aborted`（`RoomCalendarService.java:481`）。 |
| 『同型寫法另有三處，只讀碼、未重現』：`ChatService`、`SupportTicketService`、`TransferService` | **三處都用真實競態重現，全部紅燈**（§3）。工單那一處的日誌甚至印出 `createTicket: ticketNumber collision, retrying: attempt=1`，之後重試用的 `count` 查詢就撞上 `current transaction is aborted`——**`DEF-139`（Sprint 144）的『有界重試』在 PostgreSQL 上從沒生效過**。 |
| 全庫 `catch (DataIntegrityViolationException)` 共 18 處（Sprint 318 唯讀逐一讀過） | 本輪重做同一個 grep：修完後 17 處＝原本安全的 14 處＋`RoomCalendarService.bookDateRange`（直接丟 `E-4001`）＋`fillMissingCalendarEntries`（現在也是）＋`TransferService`（現在直接丟 `E-5014`）＋新元件 `UniqueConflictRetry` 本身；`ChatService`、`SupportTicketService` 的 catch 已移除。沒有任何 catch 內還碰資料庫（`PaymentService:185` 是 grep 視窗多抓了區塊之外的下一行）。 |
| 整合測試資料庫是 Hibernate 建的，沒有 Flyway 的索引 | 屬實：`conversations` 的 V81 部分唯一索引與 `transfers` 的 V64 唯一索引在整合測試資料庫**根本不存在**（`Conversation`、`Transfer` 實體都沒有宣告）；`room_calendar` 與 `support_tickets.ticket_number` 的唯一約束在實體上，所以存在。→ Chat、Transfer 兩個案例自己建一個**只涵蓋本案例鍵**的同形索引，收尾移除（先例：[OrderStripeCheckoutRetryIntegrationTest](../../backend/src/test/java/com/nextkey/ecommerce/integration/OrderStripeCheckoutRetryIntegrationTest.java) 等四個付款整合測試，測試自己 `CREATE UNIQUE INDEX IF NOT EXISTS`），不影響其他測試。 |

## 2. 做法

### 2.1 為什麼『在同一個交易裡恢復』永遠不成立

PostgreSQL 在交易內任何一個語句失敗後，整個交易進入中止狀態（`current transaction is aborted, commands ignored until end of transaction block`），之後的任何語句都會失敗；而且例外穿出 repository 的交易代理時，外層交易也被標成 rollback-only（`DEF-310`，[SPRINT_221_PLAN.md](SPRINT_221_PLAN.md) §4.1 早記過）。所以 catch 之後『改讀贏家的列』『重算單號再寫』『回傳贏家那一筆』都做不到——mock Repository 沒有交易中止語意，原本的單元測試一直看不到。可行的只有三種：①**不在中止的交易裡做任何事**（轉成錯誤）；②**把整個交易丟掉，在全新交易重做**；③避免衝突（鎖、`ON CONFLICT DO NOTHING`）。

### 2.2 四處各自的處理與理由

| 處 | 處理 | 為什麼 |
|---|---|---|
| `RoomCalendarService.fillMissingCalendarEntries`（`POST /v2/dashboard/rooms/{id}/maintenance`） | catch 內改丟 `E-4001`（該日期正被另一個操作修改，可重試），不再查詢 | 比照同檔案 `bookDateRange` 的既有做法；整個房間日曆本來就是『NOWAIT 鎖失敗即 `E-4001`』的 fail-fast 設計，不需要為了這個罕見競態做出『成功』的恢復 |
| `SupportTicketService.createTicket`（`POST /v2/support/tickets`） | 整個交易丟掉、在新交易重做（新元件 `UniqueConflictRetry`） | 單號是系統產生、使用者無感，`DEF-139` 的本意就是『重新產生再試』；重做時 `count` 已包含贏家那張，單號自然錯開 |
| `ChatService.createConversation`（`POST /v2/chat/conversations`） | 同上 | `DEF-118` 的本意是『搶輸時回傳贏家那一筆』；重做時『是否已有對話』的檢查就看得到它 |
| `TransferService.createTransferForStatement`（內部，從 `approveStatement` 與重試端點呼叫） | catch 內改丟 `E-5014`，**不**重做、**不**讓輸家『成功』 | 呼叫端 `SettlementReviewer.approveStatement` 在**自己的交易裡**先把結算單改成 `APPROVED` 才呼叫（無法在內層重做）；輸家若『成功』並提交，會把贏家已推進到 `PAID` 的結算單覆寫回 `APPROVED`（遺失更新，**讀碼推論、未重現**）。讓整個操作乾淨失敗、輸家的交易回滾才安全。只有佔位成功的交易才會呼叫 Stripe，這個財務安全性不變 |

為什麼不選別的方案（Rule 2／Rule 7）：

- **`REQUIRES_NEW` 只包 INSERT**：改動最小（catch 內的重查就變得有效），但外層交易持有一條連線的同時要再借一條，連線池（`maximum-pool-size: 20`）在併發時有互相等待的風險；專案內唯一的先例是 `BuyerNotificationService` 的盡力而為通知，風險等級不同。
- **`INSERT … ON CONFLICT DO NOTHING`（原生 SQL）**：能讓搶輸者在同一個交易裡繼續，但要為四個實體各寫一份原生 INSERT（`Conversation` 有 jsonb 欄位），實體欄位一變就要同步，專案內沒有先例。
- **鎖使用者列（`UserRepository.findByIdForUpdate`）**：有先例，但 PostgreSQL 的外鍵會讓對同一使用者列的 `INSERT … REFERENCES` 隱含 `FOR KEY SHARE`；雙向同時發起聊天（A→B 與 B→A）可能互相等待而死結（是否真的死結取決於 Hibernate 產生 `FOR UPDATE` 還是 `FOR NO KEY UPDATE`，我沒有驗證）。
- **整個交易重做**（採用）：不需要原生 SQL、不多借連線、與資料庫方言無關；代價是呼叫端必須是最外層（元件在已有交易時不重做，見 Javadoc）。

### 2.3 測試設計（真實競態，不靠 mock、不靠睡眠）

[UniqueConstraintRaceRealStackIntegrationTest](../../backend/src/test/java/com/nextkey/ecommerce/integration/UniqueConstraintRaceRealStackIntegrationTest.java)：一個『對手』交易（`TransactionTemplate`＋`JdbcTemplate`）先寫入會撞唯一約束的那一列、**不提交**並停住；另一個執行緒用 MockMvc 走完整的 HTTP／JWT／權限鏈送出請求，它的 INSERT 卡在對手尚未提交的唯一索引項目上——用 `pg_stat_activity`（`wait_event_type = 'Lock'`、`wait_event = 'transactionid'`、且正在執行對該表的 INSERT）**確認真的卡住了**，而不是睡一段時間猜；放行對手提交後，請求的 INSERT 才以唯一約束違反失敗。四個案例：標記維護、發起聊天、建立工單（HTTP 端點）與 transfer 佔位（直接呼叫服務，因為它沒有獨立端點）。

### 2.4 其他設計

- 新元件 [UniqueConflictRetry](../../backend/src/main/java/com/nextkey/ecommerce/infrastructure/persistence/UniqueConflictRetry.java)（`infrastructure/persistence`）：`TransactionTemplate` 包一圈，唯一約束違反時整個交易回滾、在新交易重做，含第一次最多 3 次（與 `DEF-139` 原本的上限一致）；呼叫時**已在交易內**就只執行一次、例外原樣往外拋（外層交易已被中止，無法重做，不假裝能恢復）；其他例外不重試。OSIV 開著（`spring.jpa.open-in-view` 未設定＝預設開）時，Spring 的 `JpaTransactionManager` 在回滾時會清空預先綁定的 `EntityManager`，所以重做不會看到上一次失敗的髒狀態（真實競態案例在預設設定下通過）。
- `ChatService`／`SupportTicketService` 的 `@Transactional` 移到元件的 `TransactionTemplate`（原方法改為薄薄一層、本體搬到 `*Once` 私有方法）；對外簽章不變。
- `TransferService`：`claimTransferSlotOrExisting` 更名為 `claimTransferSlot`（原名 `OrExisting` 已不是事實），`createTransferForStatement` 裡『佔位輸了就回傳既有記錄』的分支一併移除。

### 2.5 文件

`API_M02_Listing_Center.md` v2.3、`API_M10_Chat.md` v1.1、`API_M18_Support.md` v1.1、`API_M07_Settlement.md` v1.1 補記契約；追蹤表 `DEF-441` 完成並保留原文作證據。

## 3. 修改前的紅燈（未修改的程式碼、真實 PostgreSQL）

四個案例都先在未修改的生產程式碼上跑，**全部紅燈，原因與預測一致**（伺服器日誌逐一核對）：

| 案例 | 紅燈訊息 | 伺服器日誌（以 requestId／堆疊對上） |
|---|---|---|
| 標記維護補列 | `expected: 400 but was: 500`，回應 `E-9900` | 唯一約束違反（`ukljlm867d3ml4ewhlmjtd9gp34`）→ `fillMissingCalendarEntries: Unique constraint violation …` → `current transaction is aborted`（`RoomCalendarService.java:481`） |
| 發起聊天 | `expected: 200 but was: 500`，回應 `E-9900` | `SQLState 23505`（`idx_it_race_conv_…`）→ 緊接 `SQLState 25P02`（`current transaction is aborted`） |
| 建立工單 | `expected: 200 but was: 500`，回應 `E-9900` | `SQLState 23505`（`uk_63wdxui0979kwkbqiyclme9fw`）→ `createTicket: ticketNumber collision, retrying: attempt=1, ticketNumber=TK-20261031-001` → `SQLState 25P02`：重試用的 `count` 查詢撞上中止的交易 |
| transfer 佔位 | 輸家的失敗原因是 `JpaSystemException: could not execute statement [ERROR: current transaction is aborted …] [insert into transfers …]`，不是預期的 `BusinessException` | 同上機制 |

## 4. 測試與突變驗證

### 4.1 修改後的基準與穩定性

- 四個真實競態案例 + 元件 5 個案例 + 受影響的單元測試，在修好的程式碼上全綠。
- **穩定性**：整個 `UniqueConstraintRaceRealStackIntegrationTest` 在全量 verify 之後再連跑 3 次（疊在殘留資料上），每次 4 案例 0 失敗。

### 4.2 突變驗證（預先登記：每組先寫下『恰好該紅的案例』與失敗訊息方向，跑完逐一比對）

受測集合：6 個單元類別（`RoomCalendarServiceTest`、`UniqueConflictRetryTest`、`SupportTicketServiceTest`、`ChatServiceTenantResolutionTest`、`ChatServiceUnreadCountTest`、`TransferServiceTest`）加 1 個整合類別，共 88 案例。基準 Z0 全綠；10 組突變**全部 `KILLED(exactly must)`**，失敗集合與預先登記完全一致，沒有任何訊息方向不符（驅動沒有回報 `MSG-MISMATCH`），12 個被改過的檔案逐一 SHA-256 核對還原。

| 組 | 突變 | 恰好該紅的案例（預先登記，結果吻合） |
|---|---|---|
| M1 | 補列的 catch 改回『改讀對方的列』（舊的壞恢復寫法） | 標記維護真實競態（`but was: 500`）、`RoomCalendarServiceTest` 的 `never()` 重查案例 |
| M2 | 補列的錯誤碼 `E-4001` 換成 `E-4000` | 同上兩個（訊息含 `E-4000`） |
| M3 | `UniqueConflictRetry.MAX_ATTEMPTS = 1`（不重做） | 聊天、工單真實競態；工單單元 2 個（撞號重試、超過上限）；聊天搶輸單元 1 個；元件 `lostOnce…` |
| **M4** | **重試放回同一個交易**（舊壞寫法的泛化） | 聊天、工單真實競態（`but was: 500`）、元件 `lostOnce…`、`lostEveryTime…`。**工單／聊天的 mock 單元測試全綠**（預測的就是這樣） |
| M5 | 拿掉『已在交易內不重做』的守門 | 元件 `alreadyInsideATransaction_doesNotRetry` |
| M6 | 重試所有例外（不只唯一約束違反） | 元件 `otherException_isNotRetried` |
| M7 | Transfer 的 catch 改回『改查贏家的記錄』 | Transfer 真實競態（訊息含 `current transaction is aborted`）、`TransferServiceTest` 的『違反後不可再查』案例 |
| M8 | Transfer 的 catch 改成重拋原例外 | 同上兩個（訊息含 `DataIntegrityViolationException`） |
| M9 | 拿掉聊天的重做包裝 | 聊天真實競態、聊天搶輸單元 |
| M10 | 拿掉工單的重做包裝 | 工單真實競態、工單單元 2 個 |

### 4.3 這輪最重要的一條發現：mock 單元測試分辨不出『同交易重試』與『新交易重做』

M4 把重試放回同一個交易——也就是 `DEF-118`／`DEF-139` 當年的寫法——時，`SupportTicketServiceTest`、`ChatServiceTenantResolutionTest` 的撞號／搶輸案例**一個都沒紅**，因為 mock 的 repository 沒有 PostgreSQL 的交易中止語意。擊殺它的只有真實競態案例（回到舊症狀 `500 E-9900`）與元件自己的結構測試（驗 `getTransaction` 與 `rollback` 的次數）。這就是這三個缺陷過去一直沒被抓到的原因，也是為什麼這類修復的測試一定要有真實競態案例。

### 4.4 預測與結果的細節差異（誠實揭露）

- M3 下元件的 `lostEveryTime_givesUpAfterMaxAttemptsAndRethrows` **沒有紅**（我登記時就預測了）：它用 `times(MAX_ATTEMPTS)` 引用被突變的常數，自洽，殺不到常數突變；『會重做』由 `lostOnce…` 守住，『上限是 3』沒有任何案例用寫死的 3 守（上限的具體數字不是行為契約，我選擇不另外寫死）。
- 『第三次嘗試』只有 mock 單元層（元件的 `lostEveryTime…` 與工單的 `ExceedsRetries` 案例）覆蓋；真實競態案例只走到第二次嘗試。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**18 分 54 秒**；單元 **2438／0 失敗／0 錯誤／0 跳過**（Sprint 318 的 2433＋元件新增 5）；整合 **983／0 失敗／0 錯誤／0 跳過**（979＋真實競態新增 4）；Checkstyle main／test 都是 0 違規；日誌 `<<< FAILURE`／`<<< ERROR`／`Unresolved compilation`／`ClassFormatError`／`NoClassDefFoundError` 各 0 個。新類別在 verify 內確實執行：`UniqueConflictRetryTest` 5、`UniqueConstraintRaceRealStackIntegrationTest` 4。 |
| 紅燈先行（§3） | 四個案例先在未修改的程式碼上全部紅燈，原因與日誌核對一致。 |
| 突變驗證（§4.2） | 基準 Z0＋10 組，全部 `KILLED(exactly must)`，預先登記逐一吻合；12 個檔案 SHA-256 全部還原、無殘留字串。 |
| 穩定性（§4.1） | 真實競態類別連跑 3 次各 4 案例 0 失敗。 |
| IDE Problems | 全部條目診斷 **0**。唯一例外：我改寫 Transfer 單元測試後留下一個多餘的 `@SuppressWarnings("unchecked")`（警告），**在全量 verify 之後**才由診斷發現並移除；移除後 `TransferServiceTest` 重跑 15／0、診斷清空，**沒有為這一行註解重跑全量 verify**。 |
| 全庫 `catch (DataIntegrityViolationException` 審計（§1.3） | 17 處，catch 內都沒有 repository 呼叫。 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 在**所有文件定稿後**執行，結果見 §10。 |
| 沒有執行的項目 | Playwright 完整 E2E 與 `make validate-schema-doc`：本輪沒有前端、Flyway 遷移或 schema 文件變更（只改後端程式碼、測試與文件），見 §7 第 7 點。 |

## 6. 過程中的失誤與修正

1. **IDE 的 Java 語言服務把新測試類別編壞**（`NoClassDefFoundError: ContractFixture`，類別名沒有套件前綴），Maven 看到 class 比原始碼新而印 `Nothing to compile` 不重編，連跑兩次都一樣、白花兩輪。刪掉壞的 class 並 `touch` 原始碼後正常；突變驅動因此加了『改檔前後先刪被改檔案的 class 強制重編』（`purge_classes`）。
2. **計畫書第一版把『先例』連結連到錯的檔案**（把記憶檔名當成 repo 檔案連過去），用 `grep -rln "CREATE UNIQUE INDEX" src/test` 找到真正的先例檔改正。
3. **API_M10 文件第一版寫了『典型觸發是使用者連點兩下「聯絡賣家」』**——我沒有查前端有沒有這顆按鈕。commit 前自己攔到，改成不指名的『重複點擊或重送』。文件裡的『例子』也是宣稱，要有出處或不要寫。
4. **全庫審計的 shell 迴圈誤報**：把 catch 區塊之後的 7 行都抓進來，誤報 `PaymentService:185`（區塊外的下一行）；讀原碼確認 catch 內只有一個 `throw` 才排除，沒有只信腳本輸出。
5. **zsh 未加引號的 `--include=*.java` 又報 `no matches found`**（整條指令沒跑，不是『0 筆』）；之後一律加引號。
6. **工作目錄漂移**：`cd` 殘留在子目錄，之後改用絕對路徑。
7. **多餘的 `@SuppressWarnings`** 見 §5 的 IDE Problems 列。

## 7. 限制（誠實揭露）

1. **每一處只有一個競態情境**：標記維護只測『對手已寫入尚未提交的列』，沒有測『標記維護 vs 標記維護』兩個請求都走完整服務，也沒有測『標記維護 vs 訂房建立日曆列』；聊天、工單各一個；Transfer 的對手是一條原生 INSERT，不是另一次服務呼叫。
2. **Transfer 案例是服務層、不是 HTTP**：它沒有獨立端點（從 `approveStatement` 與重試端點呼叫）；因此沒有驗證 `approveStatement` 端到端的結果（讀碼推論：輸家的 `E-5014` 被 `approveStatement` 的 try／catch 吞掉，但佔位的 INSERT 已讓共用交易被標成 rollback-only，所以提交時會失敗、整個批准回滾——**未重現**）。
3. **『讓輸家成功會覆寫贏家狀態』也是讀碼推論**：我沒有重現遺失更新，只據此選擇讓輸家明確失敗。`DEF-442` 登記的是同一個方向的相鄰問題（批准／駁回的讀後寫沒有 CAS），同樣未重現。
4. **整合測試庫沒有 Flyway 的索引**：聊天、Transfer 案例自己建『只涵蓋本案例鍵』的同形索引；索引語意與 V81／V64 相同，但**沒有對 Flyway 建出來的資料庫跑**這兩個案例。
5. **工單案例有 UTC 午夜的極小窗口**：測試與請求各自取 `Instant.now()` 算日期前綴，剛好跨過 UTC 午夜（台北 08:00）的幾毫秒內會算出不同前綴而不撞號，案例會誤紅；機率可忽略，沒有為它加防護。
6. **`UniqueConflictRetry` 不保護『已在交易內』的呼叫者**：目前兩個使用者（聊天、工單）都是 controller 直接呼叫的最外層；若將來有人在別的 `@Transactional` 方法裡呼叫它們，元件只執行一次、例外原樣往外拋（行為與沒有元件時相同，不會更糟，但也不會恢復）。
7. **沒有執行 Playwright 完整 E2E 與 `make validate-schema-doc`**：沒有前端、Flyway 遷移或 schema 文件變更。依 Sprint 306 以來的慣例，仍跑了全量後端 `mvn clean verify`（見 §5）。
8. **『鎖使用者列會雙向死結』沒有驗證**：§2.2 第四項的理由是推論（取決於 Hibernate 對 PostgreSQL 產生 `FOR UPDATE` 還是 `FOR NO KEY UPDATE`），我沒有去量，只是這個方案相較其他選項沒有優勢，所以沒有採用。
9. **聊天搶輸者不會把自己的 `initialMessage` 併入**：這是既有語意（『已有對話則直接回傳』本來就忽略初始訊息），重做後仍然如此，API_M10 §1 第 2 點已明寫。

## 8. 我替使用者做的決定（可一句話推翻）

1. 『請繼續完成任務！』（無 `==>`）＝只做 `DEF-441`（含它自己列的另外三處），不碰 `DEF-419` 等待使用者處理的項目，也不做兩個『可選量測』。
2. **四處全修**（探針全部證實失效）；Transfer 的修法是『明確失敗』而不是追蹤表原本建議的『搶輸者回傳贏家』。
3. **新增 `UniqueConflictRetry`**（`infrastructure/persistence`，最多 3 次）而不是 `REQUIRES_NEW`、`ON CONFLICT` 或鎖使用者列（理由見 §2.2）。
4. 標記維護補列輸了回 `400 E-4001`（可重試）而不是整個重做——與 `bookDateRange` 一致。
5. `ChatService.createConversation`、`SupportTicketService.createTicket` 的對外簽章不變，`@Transactional` 搬進 `TransactionTemplate`，本體改為 `*Once` 私有方法。
6. `TransferService.claimTransferSlotOrExisting` 更名為 `claimTransferSlot`（原名已不是事實），『佔位輸了回傳既有記錄』的分支一併移除。
7. 整合測試庫缺的索引由案例自己建、自己移除（只涵蓋本案例鍵），不改 `application-integration-test.yml` 或實體。
8. `API_M02` v2.3、`API_M10` v1.1、`API_M18` v1.1、`API_M07` v1.1 補記契約；其他文件引用的舊版號（既有的版號漂移）照 Sprint 309／310 慣例不碰。
9. 相鄰發現（`approveStatement`／`rejectStatement` 讀後寫無 CAS）**只登記為 `DEF-442`、標明未驗證**，不順手修（延續 Sprint 316／317／318）。
10. 不執行完整 E2E 與 schema 文件守門（見 §7 第 7 點）。

## 9. 新登記的缺陷

- **`DEF-442`**（🟢 **未驗證，讀碼推論**）：`SettlementReviewer.approveStatement`／`rejectStatement` 的狀態轉換是『讀後寫』，沒有 CAS、鎖或 `@Version`——兩個管理員（或同一個管理員重複送出）同時對同一張 `PENDING_REVIEW` 的結算單批准／駁回，後寫者可能覆寫先寫者已提交的狀態。證據只有讀碼：`SettlementStatement` 沒有 `@Version`／`@DynamicUpdate`；`approveStatement` 先 `findById`、檢查狀態、再 `setStatus` ＋ `save`，沒有使用 repository 裡已存在的條件式更新 `updateStatusIfCurrent`（`SettlementReversalService` 的逆轉流程用它，Sprint 137／`DEF-138`）。**沒有用探針重現**，所以不知道實際行為。動手前先用真實競態案例重現。細節見追蹤表。

## 10. 狀態

**文件守門測試**（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`）在**所有文件定稿後**執行：4 個類別共 **14 個案例，0 失敗**（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1），`BUILD SUCCESS`。

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 319 列。本輪依使用者 2026-10-29 的長期授權（『做完一輪後，直接 Commit + Push to main』）commit 後直接 push，連同 Sprint 318 的純文件回填 commit `ef4bd309` 一起帶出；push 結果與雲端 CI 於回填 commit 補上。

本輪的 commit 範圍：生產程式碼 `UniqueConflictRetry`（新）、`RoomCalendarService`、`ChatService`、`SupportTicketService`、`TransferService`；測試 `UniqueConflictRetryTest`（新）、`UniqueConstraintRaceRealStackIntegrationTest`（新）、`RoomCalendarServiceTest`、`TransferServiceTest`、`SupportTicketServiceTest`、`ChatServiceTenantResolutionTest`、`ChatServiceUnreadCountTest`；文件 `API_M02`（v2.3）、`API_M10`（v1.1）、`API_M18`（v1.1）、`API_M07`（v1.1）、`DEFERRED_ITEMS_TRACKER.md`（`DEF-441` 完成、新登記 `DEF-442`）、`RELEASE_TRACKER.md` 與本計畫書。沒有 Flyway 遷移或前端變更。

**待使用者處理**：`DEF-419`、舊規則資料遷移（若要）、Sprint 306 的 `make down-clean` → `make up` → `make dev-admin-password`、Docker 建置快取。

**下次無 `==>` 的『請繼續』我可以直接做的**：`DEF-442`（未驗證；先用真實競態案例重現 `approveStatement`／`rejectStatement` 的讀後寫是否真的會互相覆寫，再決定要不要修）。另有兩個**可選的量測**（不是缺陷，需要你決定值不值得）：另一間店鋪的店主打九個端點、編輯頁進場守門（見 [SPRINT_318_PLAN.md](SPRINT_318_PLAN.md) §10）。
