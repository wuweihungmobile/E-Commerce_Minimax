# Sprint 330 Plan — DEF-460（重複 slug 回 500）量測與修正；DEF-453（Stripe 後台退款不扣結算單）補量測

> **狀態：已完成（2026-11-06）。** §1～§2（含 §2.3、§2.4）在跑任何量測、突變之前寫成（預測先登記），其餘章節在量測與修復完成後補上；push 與雲端 CI 的結果已回填（§5、§10）。

## 1. 起點與範圍

### 1.1 起點

[Sprint 329](SPRINT_329_PLAN.md) 收尾時，使用者把總結貼回來並說「請繼續完成任務！」，**沒有 `==>` 回覆**（無 `==>` 變體；以下解讀為**推論**，不是使用者明說的）。總結結尾寫：「下次說『請繼續』我可以直接做 `DEF-453` 的補量測，或 `DEF-460`（先量重複 slug 的實際 HTTP 狀態與錯誤碼）。`DEF-455`～`DEF-459` 是較大的結構題，建議你先決定要不要做。」

- 範圍＝**`DEF-460` 與 `DEF-453` 兩項**（總結用「或」連接兩者，我解讀為兩者都屬 AI 可獨立處理；追蹤表對兩者都寫『量測題』、沒有標產品決策）。`DEF-455`～`DEF-459` 總結明寫要使用者先決定，**本輪不碰**。
- 長期授權（2026-10-29）：本輪 commit 後直接 push，連同 Sprint 329 留在本機的純文件回填 commit `9fd75a4b` 一起帶出（Sprint 329 總結寫明「刻意不 push，隨下一輪帶出」）。

### 1.2 成功標準（開工時定義）

**`DEF-460`**
1. **先量測、再決定怎麼修**：追蹤表的根因是推論（『資料庫例外沒被轉成業務錯誤』），而且只有 1 次觀察、沒有查根因。用 **Flyway 建出的資料庫**（正式環境的 schema 來源，不是 Hibernate 產生的測試庫）＋完整 HTTP／JWT／權限鏈，記錄「同店鋪」與「不同店鋪」各重複一次 slug 的實際 HTTP 狀態與錯誤碼，並直接從 `pg_constraint` 讀出實際的唯一約束。
2. **修根因，不修症狀**：若約束清單證實資料庫比應用程式的意圖更嚴，修法在遷移，不是把例外轉成 4xx（那會讓『別間店用過的 slug』變成對本店的 409，等於跨租戶洩漏他店資料的存在）。
3. 紅燈先行：把想要的行為寫成 Flyway 型整合測試，先在未修改的程式碼上跑出紅燈（預測見 §2）。
4. 突變驗證預先登記每組『恰好該紅的案例』。
5. **同族盤點**：在 Flyway 庫列出所有『單欄位唯一約束＋該表另有 `tenant_id`』的組合，逐一判斷服務層檢查是每租戶還是全域；有同樣落差的**只登記、不順手修**。

**`DEF-453`**
1. 把追蹤表上寫明『沒有實測／讀碼推論』的三項補成事實：①以 `COMPLETED` 訂單（正式環境被結算單認領的狀態）重做 Stripe `charge.refunded`；②部分退款的 `charge.refunded` 會不會把付款整筆標成 `REFUNDED`；③『出貨後的退款只有 Stripe 後台』這句話對照**所有**退款相關端點與服務重查。
2. 量測結論決定下一步：修法牽涉部分退款語意與雙路徑防重複扣款，**是設計題**；本輪預設只量測並更新追蹤表（含有證據的建議），不改金流行為，除非量測證明有不需產品決策的小修法。

**共通**
6. 全量後端 `mvn -o clean verify`（全新資料庫）通過、Checkstyle 0、`make validate-schema-doc` 通過；push 後雲端 CI 全綠並核對整合測試數。
7. 過程中發現的相鄰問題**只登記、不順手修**（延續 Sprint 316～329 的做法）。

### 1.3 動手前用工具核對的事實（Part A：`DEF-460`）

以下每一列都是本輪讀原始碼或指令輸出得到的，不是記憶。

| 說法 | 核對結果 |
|---|---|
| `faq_categories.slug` 的唯一約束原本是什麼 | `V26__Create_Faq_Categories_Table.sql`：`slug VARCHAR(100) NOT NULL UNIQUE`——**行內、沒有命名**的 UNIQUE |
| `faq_articles.slug` | `V27__Create_Faq_Articles_Table.sql` 第 17 行：`UNIQUE(slug)`——**表層、沒有命名** |
| V29 想做什麼 | `V29__Add_Tenant_Id_To_Knowledge_Categories_Faq_Categories_Faq_Articles.sql`：註解『更新 unique constraint 加入 tenant_id』，對三張表各做 `DROP CONSTRAINT IF EXISTS uk_<表>_slug`，再 `ADD CONSTRAINT uk_<表>_tenant_slug UNIQUE (tenant_id, slug)` |
| V29 刪的名字對不對 | `knowledge_categories`：V23 的約束**有命名** `uk_knowledge_categories_slug`，名字吻合。`faq_categories`、`faq_articles`：V26／V27 的約束沒命名，**推論**（PostgreSQL 的預設命名規則）它們叫 `faq_categories_slug_key`／`faq_articles_slug_key`，V29 刪的 `uk_faq_categories_slug`／`uk_faq_articles_slug` 不存在，`IF EXISTS` 讓它靜默略過——**這一格是預測，不是事實，§3 用 `pg_constraint` 實測** |
| `knowledge_articles` | `V24` 的 `uk_knowledge_articles_slug UNIQUE (tenant_id, slug)` 從建表就是每租戶，V29 沒碰它 |
| 實體註解 | `FaqCategory.slug`、`FaqArticle.slug` 都是 `@Column(nullable = false, unique = true, length = 100)`——Hibernate 建出的測試庫也會有『全域唯一』 |
| 服務層的意圖 | `FaqService` 的 `createCategory`、`updateCategory`、`createArticle`、`updateArticle` 四處都先用 `existsByTenantIdAndSlug` 檢查，訊息『…slug already exists **for this tenant**』；`getCategoryBySlug` 以 `(tenantId, slug)` 查詢——意圖是**每租戶唯一** |
| 重複時丟什麼錯誤碼 | 四處都是 `ErrorCode.E_3001`。`E_3001` 的名稱是『無效的刊登類型』，`GlobalExceptionHandler.mapErrorCodeToStatus` 把它對到 **422**（與 `E_3002`、`E_2010` 同組）。同樣的寫法在 `KnowledgeBaseService` 有 6 處。專案其他模組遇到這種誤用時改成專用碼並對到 409（`E_8013` 頁面代稱重複、`E_8014` 分類代稱重複、`E_8017` 媒體分類名稱重複） |
| 資料庫唯一約束違反怎麼處理 | `GlobalExceptionHandler` 沒有 `DataIntegrityViolationException` 的處理器（該檔對這個字串零命中），落入 `handleGenericException` → `500 E-9900` |
| 動手前有沒有 FAQ 的 HTTP 層測試 | 動手前 `grep -rln "v2/faqs" backend/src/test` 零命中；`FaqServiceTest` 是單元測試（mock repository，看不到資料庫約束） |
| 最新遷移編號 | `V95__Add_Requested_At_To_Tenant_Feature_Toggles.sql`；本輪新遷移用 **V96** |
| 前端是否依賴 `E-3001` | `grep -rn "E-3001" frontend/src` 零命中 |

## 2. 量測設計與預測（跑之前登記）

### 2.1 做法

新類別 `FaqKnowledgeSlugPerTenantFlywayIntegrationTest`（`integration` 套件），沿用 `PlatformStatsCorruptRowFlywayIntegrationTest` 的做法：`@DynamicPropertySource` 先砍掉再建一個獨立資料庫 `s330_faq_knowledge_slug_per_tenant`，`spring.flyway.enabled=true`＋`ddl-auto=validate`，Spring 啟動時 Flyway 從 V1 實跑到最新；兩間店鋪＋兩位 `STORE_OWNER`（用 `JwtTokenService` 簽 token，與 `ContractFixture` 同形），全部走 MockMvc 的完整安全鏈（JWT、`@PreAuthorize("hasAuthority('faq:create')")`、租戶脈絡）。

Hibernate 產生的一般整合測試庫**不能用**：它的實體有 `unique = true`，會產生『全域唯一』，與 Flyway 庫不同（見記憶 `flyway-db-vs-hibernate-test-db-differences`）。

### 2.2 案例與預測（未修改的程式碼）

| 案例 | 做什麼 | 預測 |
|---|---|---|
| K0 約束清單 | 讀 `pg_constraint`，列出四張表（`faq_categories`、`faq_articles`、`knowledge_categories`、`knowledge_articles`）的 UNIQUE 約束名稱與欄位 | `faq_categories`、`faq_articles` 各**兩條**：一條單欄位 `(slug)`（名字推論為 `<表>_slug_key`）＋一條 `(tenant_id, slug)`；`knowledge_categories`、`knowledge_articles` 各**一條** `(tenant_id, slug)`。**紅**（斷言『沒有單欄位 slug 約束』） |
| F1 | 店鋪 A 建 FAQ 分類 slug=X → 店鋪 B 建同一個 slug=X | A：201。**B：500 `E-9900`**（單欄位約束擋下，例外沒有被轉成業務錯誤）。**紅** |
| F2 | 同 F1，對象是 FAQ 文章（每間店鋪先各建一個不同 slug 的分類） | 同 F1：B 的文章 **500 `E-9900`**。**紅** |
| F3 | 店鋪 A 連續建兩次相同 slug 的 FAQ 分類 | 第二次：**422 `E-3001`**（服務層 `existsByTenantIdAndSlug` 先擋下，錯誤碼誤用）；資料庫只有 1 列。斷言只要求『4xx＋只有 1 列』，**不**釘死錯誤碼（錯誤碼誤用是另一個缺陷，只登記）。**綠** |
| K1（對照組） | 店鋪 A、B 各建同 slug 的知識庫分類 | 兩次都 201。**綠** |
| K2（對照組） | 店鋪 A、B 各建同 slug 的知識庫文章 | 兩次都 201。**綠** |

**總預測**：紅燈集中在 K0、F1、F2（都是 `faq_*` 兩張表的跨店鋪重複），F3 與兩個知識庫對照組全綠。如果 F1／F2 不是 500（例如 B 成功），表示 S329 的觀察另有原因（例如當時兩次建立其實在同一個租戶），根因判斷要重寫。如果 K1／K2 紅，表示知識庫也有同樣落差，範圍要擴大。

### 2.3 `DEF-453` 補量測：動手前核對的事實與探針預測（Part B）

**動手前用工具核對的事實（讀原始碼／指令輸出，不是記憶）**

| 說法 | 核對結果 |
|---|---|
| 『出貨後的退款只有 Stripe 後台』（`DEF-453` 登記時自己標了『沒有逐一查過所有退款相關端點』） | 這輪逐一查了。生產碼裡會發起退款的入口：①`POST /v2/orders/{id}/refund`（`OrderPaymentController.mockRefund` → `refundOrderPayment`）；②取消後的自動退款（`refundOrderPaymentAsSystem`）；③`POST /v2/bookings/{id}/payments/refund`（訂房人工退款）；④訂房自動退款；⑤**`POST /v2/payments/refund`（`PaymentController` → `PaymentService.processRefund`）**；⑥Stripe `charge.refunded` webhook。①②只接受訂單狀態 `PAID`／`CANCELLED`／`REFUNDING`（`OrderStateMachine.canRefund`），所以出貨後（`SHIPPED`／`DELIVERED`／`COMPLETED`）的訂單這兩個入口都拒絕；⑤**登記時沒有發現**：它只改本地狀態、不呼叫 Stripe，明確拒絕 Stripe 付款與訂房付款，對訂單狀態**沒有限制** |
| 誰會呼叫結算調整服務 | `grep -rln "SettlementAdjustmentService\|settlementAdjustmentService" backend/src/main/java`：只有 `SettlementReviewer`、`SettlementAdjustmentService` 自己、`PaymentStateService`、`SettlementStatementRepository`。`PaymentService`（⑤）與 `PaymentWebhookService`（⑥）都沒有 |
| 退貨流程有沒有退款步驟 | 退貨域（`core/returns`、`domain/model/returns`、`domain/repository/returns`、`api/controller/returns`、`api/dto/returns` 的 `ReturnRequestService`、`ReturnRequest`、`ReturnRequestItem`、兩個 repository、`ReturnRequestController`、`ReturnDto`）逐檔 `grep -ci refund` 全是 **0**。也就是說程式裡退貨沒有任何退款動作；『出貨後的錢怎麼退回買家』在程式裡沒有答案（這是事實；『所以實務上只能在 Stripe 後台操作』是推論） |
| `charge.refunded` webhook 看不看金額 | `PaymentWebhookService.dispatch` 第 88～92 行：只取 `payment_intent` 與 `refunds.data[0].id`，**不讀** `amount_refunded`、`refunded`、`amount`；`markStripeRefunded(paymentIntentId, refundId)` 的簽名本身就沒有金額參數 |
| Mock 付款在正式環境存不存在 | `PaymentStateService.requireMockPaymentAllowed`：只要該店鋪**沒有**啟用 `STRIPE_PAYMENT_ENABLED` 就允許。所以⑤的適用對象（非 Stripe 的訂單付款）在沒啟用真實金流的店鋪是存在的；它們沒有真實金流 |

**探針（一次性，暫時插進 `RefundSettlementAdjustmentFailureRealStackIntegrationTest`，跑完用備份還原並核對 SHA-256，不入庫）與預測**

固件裡訂單被結算單認領是**合成**的（`settled_statement_id` 用 SQL 寫入；`COMPLETED` 狀態也用 SQL 設定），結算單用該類別既有的 `seedStatement`。Stripe 付款走該類別既有的 `stripePaid`（真實的 `checkout.session.completed` webhook）。

| 探針 | 做什麼 | 預測 |
|---|---|---|
| P1 | `COMPLETED`、被認領的 Stripe 訂單，收到**全額** `charge.refunded` | 付款 `REFUNDED`、`refunded_amount=0.00`、`stripe_refund_id` 有值；**訂單仍是 `COMPLETED`**（`canRefund` 為假，沒有狀態紀錄）；結算單 `total_refunds` 與 `net_settlement_amount` 不變；調整單 0 筆；有 `STRIPE_PAYMENT_REFUNDED_WEBHOOK` 稽核；Stripe 閘道沒被呼叫 |
| P2 | `PAID`、被認領的 Stripe 訂單，收到**部分**退款的 `charge.refunded`（`amount_refunded` 只有訂單金額的 1/3，`refunded=false`） | 付款整筆 `REFUNDED`、訂單整筆 `REFUNDED`（狀態紀錄原因『Stripe refund webhook (charge.refunded)』）、`refunded_amount=0.00`；結算單不變、調整單 0 筆——也就是**部分退款被當成全額退款** |
| P3 | `COMPLETED`、被認領的 **Mock 付款**訂單，ADMIN 呼叫 `PaymentService.processRefund` | 付款 `REFUNDED`；訂單仍 `COMPLETED`；結算單不變、調整單 0 筆；有 `PAYMENT_REFUNDED` 稽核 |

**總預測**：三個探針的結算單都**完全沒被動到**。如果 P1 的訂單變成 `REFUNDED`，表示我對 `canRefund` 的讀法有誤；如果 P2 的付款不是 `REFUNDED`（例如變成 `PARTIALLY_REFUNDED`），表示 webhook 其實有金額邏輯，我沒讀到。

### 2.4 突變驗證規格（`DEF-460` 守門類別；跑之前登記，驅動腳本內的 `must` 與此表相同）

守門類別有 6 個案例（K0、F1、F2、F3、K1、K2）。突變都落在 V96 遷移這個 classpath 資源（Flyway 庫每次砍掉重建，所以不需要重編）；每組跑完還原並用 SHA-256 核對。判決＝失敗集合與 `must` **恰好**相同，且 `ran=6`。

| 組 | 突變 | `must`（預測恰好紅的案例） | 為什麼 |
|---|---|---|---|
| Z0 | 無突變（基準） | 無 | 6 個全綠 |
| M1 | V96 清空（等同修復前） | K0、F1、F2 | 回到 §3 的紅燈基準 |
| M2 | 只刪 `faq_categories` 的全域約束 | K0、F2 | 文章的全域約束還在 |
| M3 | 只刪 `faq_articles` 的全域約束 | K0、F1 | 分類的全域約束還在；F2 各店先建不同 slug 的分類，所以不會撞到分類的約束 |
| M4 | 分類那行的約束名稱寫錯（`faq_category_slug_key`，`IF EXISTS` 靜默略過）——**這就是 V29 原本的錯誤** | K0、F1 | 若這個類別在 V29 當時就存在，會抓到那個錯誤 |
| M5 | 多刪 `uk_faq_categories_tenant_slug`（把每租戶唯一也刪了） | K0 | F3 靠服務層預檢仍會擋，所以只有 K0 看得到『缺少（tenant_id, slug）』 |
| M6 | 多刪 `uk_knowledge_articles_slug` | K0 | K2 只檢查兩間店鋪都能建立，不受影響 |
| C1（控制組） | 多加一個與唯一性無關的普通索引 | 無 | K0 只看唯一索引，不該被普通索引誤觸 |

**已知抓不到的突變（誠實揭露，不是測試遺漏）**：把兩個 FAQ 實體的 `unique = true` 放回去（E1）。Flyway 庫用 `ddl-auto=validate`，不看實體上的唯一性註解，所以這個守門類別看不到它；這個變更只影響 Hibernate 產生的測試資料庫（§5 說明怎麼驗證）。

## 3. 量測結果

### 3.1 `DEF-460`：未修改的程式碼，Flyway 全新資料庫

指令：`mvn -o test-compile failsafe:integration-test -Dit.test=FaqKnowledgeSlugPerTenantFlywayIntegrationTest`。結果 **6 案例、3 紅（K0、F1、F2）**，與 §2.2 的預測逐格吻合，包括約束名稱。

| 案例 | 預測 | 實測 |
|---|---|---|
| K0 約束清單 | 紅；`faq_*` 各兩條（單欄位 `(slug)` 推論名為 `<表>_slug_key`＋`(tenant_id, slug)`），`knowledge_*` 各一條 `(tenant_id, slug)` | **紅**。實際唯一索引：`faq_categories.faq_categories_slug_key(slug)`、`faq_categories.uk_faq_categories_tenant_slug(tenant_id,slug)`、`faq_articles.faq_articles_slug_key(slug)`、`faq_articles.uk_faq_articles_tenant_slug(tenant_id,slug)`、`knowledge_categories.uk_knowledge_categories_tenant_slug(tenant_id,slug)`、`knowledge_articles.uk_knowledge_articles_slug(tenant_id,slug)` |
| F1 FAQ 分類跨店 | A 201、B 500 `E-9900` | A 201、B **`500 E-9900`**（`發生未預期的錯誤`） |
| F2 FAQ 文章跨店 | 同 F1 | A 201、B **`500 E-9900`** |
| F3 FAQ 分類同店重複 | 422 `E-3001`、只有 1 列 | 201，然後 **`422 E-3001`**。回應文字是『**無效的刊登類型**』（`ErrorCode` 的預設文字；服務層丟的 "Category slug already exists for this tenant" 沒有出現在回應裡），資料庫 1 列 |
| K1 知識庫分類跨店 | 201／201 | 201／201 |
| K2 知識庫文章跨店 | 201／201 | 201／201 |

**結論**：Sprint 329 追蹤表的推論只對一半。『資料庫例外沒被轉成業務錯誤』屬實（`GlobalExceptionHandler` 沒有 `DataIntegrityViolationException` 的處理器，落入 `500 E-9900`），但**觸發它的不是同店鋪的重複，而是 V29 沒刪掉的全域舊約束**：S329 當時觀察到的『第二間店鋪建立同一個 slug 回 500』，正是這條約束造成的。所以修法在遷移，不是把例外轉成 4xx（理由見 §8 第 1 點）。

### 3.2 `DEF-453`：一次性探針（原文在附錄 A）

指令：`mvn -o test-compile failsafe:integration-test "-Dit.test=RefundSettlementAdjustmentFailureRealStackIntegrationTest#probeDef453*"`，3 個探針、0 失敗。**三個探針的預測全部吻合。**

| 探針 | 預測（§2.3） | 實測 |
|---|---|---|
| P1 `COMPLETED`、被認領的 Stripe 訂單收到全額 `charge.refunded` | 付款 `REFUNDED`／`refunded_amount=0.00`／有 `stripe_refund_id`；訂單仍 `COMPLETED`；結算單、調整單不變；有 webhook 稽核；閘道 0 次呼叫 | 全部吻合：付款 `REFUNDED`、`refunded_amount=0.00`、`stripe_refund_id=re_probe_p1`；訂單 `COMPLETED`、狀態紀錄沒有新增；結算單 `total_refunds=0.00`、`net=9000.00` 不變；調整單 0 筆；稽核 `STRIPE_PAYMENT_REFUNDED_WEBHOOK`；閘道 0 次 |
| P2 `PAID`、被認領的 Stripe 訂單收到**部分**退款（1/3、`refunded=false`）的 `charge.refunded` | 付款整筆 `REFUNDED`、訂單整筆 `REFUNDED`、`refunded_amount=0.00`、結算單不變 | 全部吻合：訂單金額 30000 分、只退 10000 分，付款 `REFUNDED`、訂單 `PAID→REFUNDED`（原因『Stripe refund webhook (charge.refunded)』）、`refunded_amount=0.00`、結算單不變、調整單 0 筆。**部分退款被當成全額退款** |
| P3 `COMPLETED`、被認領的 Mock 付款訂單，ADMIN 呼叫 `PaymentService.processRefund` | 付款 `REFUNDED`、訂單仍 `COMPLETED`、結算單不變、有 `PAYMENT_REFUNDED` 稽核 | 全部吻合：付款 `REFUNDED`、`refunded_amount=0.00`、訂單 `COMPLETED`、結算單與調整單不變、稽核 `PAYMENT_REFUNDED` |

**結論**：
1. `DEF-453` 的情境 (B) 對正式環境真實會被認領的 `COMPLETED` 訂單成立，而且結果比登記時寫的更糟——**付款顯示 `REFUNDED`，訂單卻還是 `COMPLETED`**，結算單不扣，帳實對不上（P1）。
2. **部分退款沒有被區分**：webhook 不讀金額，Stripe 後台只退 1/3，系統也記成整筆退款；若訂單當時是 `PAID`／`CANCELLED`／`REFUNDING`，訂單也整筆變 `REFUNDED`（P2）。
3. 程式裡**沒有任何一個入口**能把已出貨（`SHIPPED`／`DELIVERED`／`COMPLETED`）的 Stripe 訂單退款：訂單退款端點與自動退款只收 `PAID`／`CANCELLED`／`REFUNDING`，`POST /v2/payments/refund` 明確拒絕 Stripe 付款，退貨域完全沒有退款步驟（§2.3）。**所以 Stripe 後台加 webhook 是已出貨訂單唯一能把錢退回買家的路徑**（這是從上述事實推出的結論；『實務上營運真的這樣做』沒有數據）。
4. 另一條不扣結算單的退款路徑：`POST /v2/payments/refund`（P3）。它只適用非 Stripe、非訂房的付款，也就是沒有真實金流的 Mock 付款（`requireMockPaymentAllowed` 在店鋪沒啟用 Stripe 時允許），沒有真實金額影響，但結算單會多算。

### 3.3 `DEF-460` 同族盤點（第一版：單欄位唯一索引且該表有 `tenant_id`）

在 §3.1 建好的 Flyway 庫（V95，修復前）上查 `pg_index`：`indisunique`、非主鍵、`indnatts = 1`、該表有 `tenant_id` 欄位，共 11 列。逐列對照服務層的檢查範圍：

| 表．索引 | 欄位 | 服務層怎麼檢查／產生 | 判定 |
|---|---|---|---|
| `faq_categories.faq_categories_slug_key` | `slug` | `existsByTenantIdAndSlug`（每租戶） | **資料庫比服務層更嚴＝`DEF-460`** |
| `faq_articles.faq_articles_slug_key` | `slug` | 同上 | **同上＝`DEF-460`** |
| `cms_pages.cms_pages_slug_key` | `slug` | `CmsService.createPage` 用 `contentPageRepository.findBySlug(slug)`（全域） | 一致 |
| `posts.posts_slug_key` | `slug` | `PostService` 用 `postRepository.existsBySlug(slug)`（全域，第 456、464 行） | 一致 |
| `users.users_email_key` | `email` | `AuthService` 用 `userRepository.existsByEmail`（全域，第 56 行） | 一致 |
| `support_tickets.support_tickets_ticket_number_key` | `ticket_number` | `countByTicketNumberStartingWith(prefix)`（全域計數） | 一致 |
| `return_requests.return_requests_return_number_key` | `return_number` | `RMA-{日期}-{8 位隨機 hex}`；`findByReturnNumber(String)` 是全域查詢 | 一致 |
| `purchase_orders.purchase_orders_po_number_key` | `po_number` | `PO-{日期}-{6 位隨機數}`，全平台編號（撞號重試見 `DEF-297`） | 一致 |
| `promo_codes.promo_codes_code_key` | `code` | 所有查詢都帶 `AndTenantId`（每租戶）；**生產碼沒有建立優惠碼的地方**（`save`／建構子／`INSERT INTO promo_codes` 零命中） | 潛在落差，目前沒有失敗路徑＝`DEF-464` |
| `tenant_applications.idx_tenant_applications_pending_user_unique` | `user_id`（`WHERE status = 'PENDING'` 部分索引） | 一位使用者最多一件待審申請；**沒有逐一讀碼** | 非代稱類的一對一約束，不適用 |
| `transfers.idx_transfers_settlement_statement_id` | `settlement_statement_id` | 一張結算單最多一筆撥款；**沒有逐一讀碼** | 同上 |

**擴大版**（修復後，同一個 Flyway 庫，V96 已套用）：改查『唯一索引不含 `tenant_id`、但表有 `tenant_id`』，不限單欄位，共 10 列。與第一版相比：FAQ 兩條全域索引消失（V96 的效果）；多出的一列是 `conversations.idx_conversations_active_pair_unique`（`(initiator_id, recipient_id) WHERE is_active = true`），`ChatService.createConversationOnce` 的『已有對話』檢查是 `findByInitiatorIdAndRecipientIdAndIsActiveTrue(userId, recipientId)`，同樣不帶租戶，**一致**。所以整個盤點只找到 `DEF-460` 這一個『資料庫比服務層更嚴』的缺陷，加上 `DEF-464` 一個潛在落差。盤點只回答這一個方向（反方向——資料庫沒有約束、只靠服務層預檢——沒有盤點，見 §7）。

## 4. 修復與驗證（`DEF-460`）

### 4.1 修改

- **`V96__Drop_Leftover_Global_Slug_Unique_On_Faq.sql`（新）**：`ALTER TABLE faq_categories DROP CONSTRAINT IF EXISTS faq_categories_slug_key;` 與 `ALTER TABLE faq_articles DROP CONSTRAINT IF EXISTS faq_articles_slug_key;`。約束名稱是 §3.1 在 Flyway 全新資料庫上實測得到的；檔頭註解寫明成因（V29 猜錯名字、`IF EXISTS` 靜默略過）。
- **`FaqCategory`、`FaqArticle`（實體）**：移除 `slug` 欄位上的 `unique = true`，改在 `@Table` 宣告 `uniqueConstraints`（`uk_faq_categories_tenant_slug`／`uk_faq_articles_tenant_slug`，`tenant_id`＋`slug`，名稱與 V29 一致）。原本的 `unique = true` 會讓 Hibernate 建出的測試資料庫也變成全平台唯一，等於把同一個錯誤再宣告一次。
- **沒有改**：`FaqService`（服務層的檢查本來就是每租戶）、`GlobalExceptionHandler`、知識庫的任何程式。
- **新測試 `FaqKnowledgeSlugPerTenantFlywayIntegrationTest`**（6 案例）：§2 的 K0、F1、F2、F3、K1、K2。

### 4.2 修復後的守門類別

6 案例、**0 失敗**。K0 讀到的唯一索引：`faq_categories.uk_faq_categories_tenant_slug(tenant_id,slug)`、`faq_articles.uk_faq_articles_tenant_slug(tenant_id,slug)`、`knowledge_categories.uk_knowledge_categories_tenant_slug(tenant_id,slug)`、`knowledge_articles.uk_knowledge_articles_slug(tenant_id,slug)`——兩條單欄位 slug 索引已不存在。F1、F2 兩間店鋪都 `201`；F3 同店鋪重複仍是 `422 E-3001`（本輪沒改，見 `DEF-462`）。

### 4.3 突變驗證結果（§2.4 預先登記；全部吻合）

驅動 `mut330.sh`＋`mut330_parse.py`（備份 → 套用 → 實跑 → 從 failsafe XML 取失敗集合 → 還原 → SHA-256）；每組 `ran=6`。

| 組 | 失敗集合 | 預先登記的 `must` | 判決 |
|---|---|---|---|
| Z0 基準 | （無） | （無） | 綠，符合 |
| M1 V96 清空 | K0、F1、F2 | K0、F1、F2 | **恰好吻合** |
| M2 只刪分類的約束 | K0、F2 | K0、F2 | **恰好吻合** |
| M3 只刪文章的約束 | K0、F1 | K0、F1 | **恰好吻合** |
| M4 分類那行名稱寫錯（V29 原本的錯誤） | K0、F1 | K0、F1 | **恰好吻合** |
| M5 多刪 `uk_faq_categories_tenant_slug` | K0 | K0 | **恰好吻合**（F3 靠服務層預檢仍綠，所以只有 K0 看得到） |
| M6 多刪 `uk_knowledge_articles_slug` | K0 | K0 | **恰好吻合** |
| C1 控制組（多一個普通索引） | （無） | （無） | 綠，符合 |

驅動結束後 V96 的 SHA-256 與原版相同（`6d1c2cee…0d65`），我另外獨立重算一次確認。M4 重現的就是 V29 當年的錯誤：若這個類別在 V29 的時候就存在，會在 K0 與 F1 當場紅。

**抓不到的（已知）**：E1——把兩個 FAQ 實體的 `unique = true` 放回去。Flyway 庫用 `ddl-auto=validate`，不檢查唯一性註解，所以守門類別看不到它（§2.4）；§5 記錄我怎麼驗證這個變更。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 守門類別（Flyway 庫） | 未修改的程式碼 6 案例 3 紅（K0、F1、F2，預測吻合）→ 修復後 6／6 綠 |
| 突變驗證 | 8 組（Z0、M1～M6、C1）全部與預先登記相同；V96 還原後 SHA-256 一致 |
| `mvn checkstyle:check`（pre-commit 同一條指令） | 0 violations |
| `make validate-schema-doc` | 通過（套用 96 個遷移後 SRD 的 DDL 與實際 schema 一致，不需要同步文件） |
| `DEF-453` 探針 | 3 個探針預測全部吻合；測試類別還原後 SHA-256 與探針前相同，`git status` 該檔不在修改清單，含探針的舊 class 與報告已刪 |
| 全量後端 `mvn -o clean verify`（全新資料庫：先 `docker rm -f -v` 再 `make test-db-up`） | **BUILD SUCCESS（20 分 11 秒）**：單元 **2482／0**（0 失敗、0 錯誤、0 跳過）、整合 **1033／0**（0 失敗、0 錯誤、0 跳過；Sprint 329 雲端為 1027，多出的 6 個是本輪的守門類別）；Checkstyle main 與 test 皆 **0 violations** |
| 實體註解變更（E1）的驗證 | 全量 verify 在全新容器上讓 Hibernate 依實體建表，事後用 `pg_get_indexdef` 讀 `nextkeytest`：`faq_categories.uk_faq_categories_tenant_slug → btree (tenant_id, slug)`、`faq_articles.uk_faq_articles_tenant_slug → btree (tenant_id, slug)`，兩張表**沒有**單欄位 slug 唯一索引。對照：註解沒改的 `knowledge_categories` 在同一個庫是 `uk_gbvapl2qo99ypgv6b3tlli57x → btree (slug)`，也就是 Hibernate 確實會把 `unique = true` 變成單欄位唯一索引（所以 FAQ 兩表修改前也是這個形狀——這一點是類比推論，我沒有建修改前的 schema 來看）。這是一次性查驗，不是自動化測試 |
| 同族盤點（擴大版：所有不含 `tenant_id`、但表有 `tenant_id` 的唯一索引，不限單欄位） | V96 之後共 10 列；與第一版相比 FAQ 兩條消失，多出 `conversations.idx_conversations_active_pair_unique`（`(initiator_id, recipient_id) WHERE is_active`）——`ChatService` 的『已有對話』檢查 `findByInitiatorIdAndRecipientIdAndIsActiveTrue` 同樣不帶租戶，一致。沒有新增缺陷 |
| push 與雲端 CI | push 範圍 `96f8afac..d6607a81`（依 2026-10-29 長期授權直接 push，連同 Sprint 329 留在本機的純文件回填 commit `9fd75a4b`）。pre-commit 與 pre-push 各一次通過，沒用 `--no-verify`；pre-push 的 schema 守門在乾淨 Flyway 庫啟動 backend（含 V96），『entity 與 Flyway schema 對齊，無漂移』。雲端 CI run [42159216459](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/42159216459)：三個 job 皆 success（單元 2 分 23 秒、前端 1 分 46 秒、整合 14 分 19 秒；整個 run 16 分 49 秒），以 `gh run view --json` 確認 headSha `d6607a81` 與本機 commit 一致，並下載整合 job 的完整雲端日誌（約 84 MB）核對：整合 **1033／0／0／0**（141 個類別、沒有失敗行）、新守門類別 6／0／0／0 與本機一致、Checkstyle main 與 test 皆 0 違規 |
| Playwright／前端 | **沒跑**：本輪沒有前端變更（`git status` 只有後端、遷移與文件） |

## 6. 過程中的失誤與修正

1. **測試輔助方法名稱蓋掉靜態匯入**：第一版把輔助方法命名為 `post`，類別內宣告的同名方法遮蔽了靜態匯入的 `MockMvcRequestBuilders.post`，第一次編譯就失敗；改名 `postJson`。編譯器當場抓到，沒有進到測試。
2. **IDE 的 Java 服務搶先編譯了改過的實體**：改完實體後 `mvn test-compile` 回報 `Nothing to compile - all classes are up to date`，class 的修改時間比原始碼晚 1 秒。為避免用到 IDE 編出的 class，我刪掉兩個實體的 class 逼 Maven 用 javac 重編，確認日誌出現 `Compiling 387 source files`（主程式）與 `Compiling 328 source files`（測試）。
3. **DEF-453 探針是被 IDE 預先編譯的 class 執行的**：探針那次 Maven 也回報 `Nothing to compile`；輸出裡有 `PROBE>` 標籤，證明探針方法確實跑了。還原原檔後我刪掉該測試類別的 class，不讓含探針的 class 留在 `target`。
4. **追蹤表初稿誤用了記憶連結語法 `[[…]]`**（repo 文件不該引用我的私有記憶檔）：commit 前自審抓到，改成指向 `SPRINT_319_PLAN.md`。
5. **突變驅動跑的期間 V96 是突變版本**：我因此暫停了所有會讀遷移目錄的檢查（`validate-schema-doc`），等驅動還原、SHA-256 核對後才跑。
6. **沒有任何預測落空**：這輪的預測（約束名稱、五個 HTTP 結果、三個探針的資料庫狀態、八組突變的失敗集合）全部吻合。預測都來自讀碼，量測只是把它們從推論變成事實；比起『預測全中』，更有價值的是 §3.1 與 §3.2 修正了登記時的說法（`DEF-460` 的根因、`DEF-453` 的出貨後退款途徑）。

## 7. 限制（誠實揭露）

1. **實體註解沒有自動化測試守住**（E1，§4.3）。Flyway 型守門類別看不到它；其他測試裡，碰到 FAQ 實體的只有 `FaqServiceTest`（mock）與 `ViewCountConcurrencyIntegrationTest`（slug 都帶唯一戳記：`faq-cat-<stamp>`、`faq-art-<stamp>`），都不會在兩間店鋪建立同 slug 的 FAQ 資料列；除了本輪的守門類別，也沒有其他測試呼叫 `/v2/faqs`（`grep -rln "v2/faqs" backend/src/test` 只命中守門類別）。
2. **同店鋪的併發重複建立沒有重現**（`DEF-462` (b)）：『撞唯一約束 → `500 E-9900`』的機制由 F1 實測，兩個同店鋪請求交錯的情境是讀碼推論。
3. **`E-3001` 的其餘 9 處前置檢查沒有逐一實測**（`DEF-462` (a)）：只實測了 FAQ 分類（F3）。
4. **`DEF-453` 探針的固件是合成的**：訂單的 `COMPLETED` 狀態與 `settled_statement_id` 用 SQL 寫入；webhook 內容是手寫的 JSON，只包含 handler 會讀的欄位加上 `amount`、`amount_refunded`、`refunded`，不是 Stripe 的真實事件；從沒對真實 Stripe 驗證。Stripe 後台退款在營運上實際發生的頻率沒有數據。
5. **同族盤點的範圍**：只看唯一索引、只看有 `tenant_id` 的表（§3.3 第一版單欄位、擴大版不限寬度）。盤點只回答『資料庫比服務層更嚴』這一個方向；反方向（資料庫沒有約束、只靠服務層預檢）沒有盤點。`tenant_applications`、`transfers` 兩列沒有逐一讀碼，只按索引名稱與形狀判斷是一對一約束。
6. **本輪沒有改任何金流程式**：`DEF-453` 仍然開著，Stripe 後台退款不扣結算單的問題在這個 commit 之後依然存在。
7. **沒跑 Playwright 完整 E2E**：沒有前端變更。

## 8. 我替使用者做的決定（可一句話推翻）

1. **`DEF-460` 修根因（遷移），不把例外轉成 4xx。** 理由：①若把『別間店鋪用過的 slug』轉成 `409`，本店鋪的使用者就能從回應推知別間店鋪有這個 slug（跨租戶洩漏），而且合法的使用被擋掉；②`V29` 的註解、服務層的訊息（『…already exists **for this tenant**』）、`getCategoryBySlug` 的 `(tenantId, slug)` 查詢、以及同模組的知識庫行為（K1、K2 實測）全都指向『每店鋪唯一』。要推翻：說『FAQ slug 要全平台唯一』，那就改服務層成全域檢查並回 `409`，同時刪掉 `V29` 新增的 `uk_faq_*_tenant_slug`。
2. **同店鋪重複的回應（`422 E-3001`『無效的刊登類型』）本輪不改**，登記 `DEF-462`。理由：新增專用錯誤碼是對外契約變更，要連同 `KnowledgeBaseService` 的 6 處、FAQ 的 4 處與併發轉譯一起改，才不會留下半套。
3. **`KnowledgeCategory` 實體的 `unique = true` 不改**，登記 `DEF-463`。理由：沒有正式環境症狀，改它會換掉既有測試資料庫的仲裁約束。
4. **V96 用 `DROP CONSTRAINT IF EXISTS`（不是無條件刪除）。** 理由：名稱已實測、可重跑；若名稱將來不吻合，K0 會紅，不會再靜默略過。要推翻：改成不帶 `IF EXISTS`，名稱不符時遷移直接失敗、應用程式起不來。
5. **`DEF-453` 升為 🟠，本輪不修。** 理由：P1／P2 是金額記帳錯誤，且依 §3.2 這條 webhook 是已出貨 Stripe 訂單把錢退回買家的唯一程式路徑；不修的理由是它動到金流記帳、與 API 路徑的競態、部分退款語意，需要自己的紅燈、競態測試與突變驗證，值得獨立一個 Sprint。
6. **`promo_codes.code` 的落差只登記為 `DEF-464`，不處理。** 理由：生產碼沒有任何建立優惠碼的地方，沒有失敗路徑。
7. **Mock 退款端點（P3）要不要也扣結算單**：留給 `DEF-453` 修復時決定，本輪不動。

## 9. 新登記與更新的缺陷

| 缺陷 | 變更 |
|---|---|
| `DEF-460` | 🟡 → ✅：量測、根因（V29 靜默略過）、V96＋實體註解、守門類別、突變驗證 |
| `DEF-453` | 🟡 → 🟠：補量測（P1～P3，預測全中）、退款入口全表、建議改為有既有語意可循的設計；仍未修 |
| `DEF-462`（新） | 🟡：FAQ／知識庫『slug 已存在』用錯誤碼 `E-3001`（`422`『無效的刊登類型』，F3 實測）；FAQ 建立無併發轉譯（讀碼推論） |
| `DEF-463`（新） | 🟢：`KnowledgeCategory` 實體 `unique = true` 與 V29 不一致（只影響 Hibernate 產生的測試庫） |
| `DEF-464`（新） | 🟢：`promo_codes.code` 全平台唯一 vs 每店鋪查詢（目前沒有建立路徑，潛在） |

## 10. 狀態

- **狀態**：實作、守門類別、突變驗證、全量 verify、文件均完成；功能 commit `d6607a81` 已 push，雲端 CI 全綠（§5）。本回填 commit 只改文件，不 push，隨下一輪帶出。收尾的 Docker 清理已做（刪 6 個匿名卷、清 1 個懸空映像；5 個具名卷與 3 個核准基底映像保留）。
- **`DEF-460`** 已修（✅）；**`DEF-453`** 已補量測、升為 🟠，**未修**（沒有改任何金流程式）；新登記 **`DEF-462`**（FAQ／知識庫重複 slug 的錯誤碼與 FAQ 併發轉譯）、**`DEF-463`**（`KnowledgeCategory` 實體註解）、**`DEF-464`**（`promo_codes.code` 潛在落差）。
- **下次說『請繼續』我可以直接做**：①`DEF-453` 修復——先寫紅燈（全額／部分／重送／`COMPLETED` 訂單／API 與 webhook 兩種到達順序／API 回滾後 webhook 補記），設計見追蹤表右欄（沿用 `refundOrderPaymentCore` 的既有語意，以 `charge.amount_refunded` 當累計真相來源）；②`DEF-462`——需要先決定新錯誤碼的編號與訊息（對外契約，要與 API 文件的錯誤碼表同步）。
- **仍然等你決定／建議你先決定**：`DEF-455`～`DEF-459`（較大的前端結構題，Sprint 329 總結已寫明建議你先決定要不要做）；其餘『還等你決定』的項目與 Sprint 329 相同，本輪沒有碰。`DEF-454` 仍順延。

## 附錄 A：`DEF-453` 探針輸出原文

探針是一次性的：暫時插進 `RefundSettlementAdjustmentFailureRealStackIntegrationTest`（三個測試方法加兩個輔助方法），跑完用備份還原並核對 SHA-256（`a88b58e0…31a8`，還原後與探針前相同；`git status` 該檔不在修改清單），未入庫。固件裡訂單被結算單認領是**合成**的（`settled_statement_id` 與 `COMPLETED` 狀態用 SQL 寫入）；webhook 內容是手寫 JSON（`charge.refunded`，含 `payment_intent`、`amount`、`amount_refunded`、`refunded`、`refunds.data[0]`）。以下每筆輸出的內容沒改，只拿掉 `PROBE> ` 前綴；`stripeRefundCalls` 是 Stripe 閘道 mock 的 `processRefund` 被呼叫的次數。探針的執行順序是 JUnit 決定的（P2、P1、P3）。

```text
[P2-0 退款前（PAID、已被結算單認領）訂單金額(分)=30000] order=PAID
    payment={status=SUCCESS, refunded_amount=0.00, stripe_refund_id=null}
    stateLog=[null->CREATED by=cf4713e5-4e14-4f3c-bdc7-b253580eb826 reason=Order created from cart, CREATED->PAID by=null reason=Stripe payment webhook success, session=cs_test_d3c20d54-448b-4c34-b7d8-0208d104675e]
    statement(total_refunds=0.00,net=9000.00)
    adjustments=0
    paymentAudits=[STRIPE_PAYMENT_SUCCEEDED_WEBHOOK]
    stripeRefundCalls=0
[P2-1 部分退款(1/3, refunded=false) charge.refunded 到達] order=REFUNDED
    payment={status=REFUNDED, refunded_amount=0.00, stripe_refund_id=re_probe_p2}
    stateLog=[null->CREATED by=cf4713e5-4e14-4f3c-bdc7-b253580eb826 reason=Order created from cart, CREATED->PAID by=null reason=Stripe payment webhook success, session=cs_test_d3c20d54-448b-4c34-b7d8-0208d104675e, PAID->REFUNDED by=null reason=Stripe refund webhook (charge.refunded), refundId=re_probe_p2]
    statement(total_refunds=0.00,net=9000.00)
    adjustments=0
    paymentAudits=[STRIPE_PAYMENT_SUCCEEDED_WEBHOOK, STRIPE_PAYMENT_REFUNDED_WEBHOOK]
    stripeRefundCalls=0
[P1-0 退款前（COMPLETED、已被結算單認領）] order=COMPLETED
    payment={status=SUCCESS, refunded_amount=0.00, stripe_refund_id=null}
    stateLog=[null->CREATED by=6b754133-578e-4d72-9706-eea05a40c1fd reason=Order created from cart, CREATED->PAID by=null reason=Stripe payment webhook success, session=cs_test_70d601bc-0292-418f-98fb-e4c95b00a839]
    statement(total_refunds=0.00,net=9000.00)
    adjustments=0
    paymentAudits=[STRIPE_PAYMENT_SUCCEEDED_WEBHOOK]
    stripeRefundCalls=0
[P1-1 全額 charge.refunded 到達] order=COMPLETED
    payment={status=REFUNDED, refunded_amount=0.00, stripe_refund_id=re_probe_p1}
    stateLog=[null->CREATED by=6b754133-578e-4d72-9706-eea05a40c1fd reason=Order created from cart, CREATED->PAID by=null reason=Stripe payment webhook success, session=cs_test_70d601bc-0292-418f-98fb-e4c95b00a839]
    statement(total_refunds=0.00,net=9000.00)
    adjustments=0
    paymentAudits=[STRIPE_PAYMENT_SUCCEEDED_WEBHOOK, STRIPE_PAYMENT_REFUNDED_WEBHOOK]
    stripeRefundCalls=0
[P3-0 退款前（COMPLETED、Mock 付款、已被結算單認領）] order=COMPLETED
    payment={status=SUCCESS, refunded_amount=0.00, stripe_refund_id=null}
    stateLog=[null->CREATED by=37ccfadc-2a4b-4d00-b00c-82e80157d781 reason=Order created from cart, CREATED->PAID by=37ccfadc-2a4b-4d00-b00c-82e80157d781 reason=Mock payment success]
    statement(total_refunds=0.00,net=9000.00)
    adjustments=0
    paymentAudits=[ORDER_PAYMENT_MOCK_SUCCESS]
    stripeRefundCalls=0
[P3-1 PaymentService.processRefund 之後] order=COMPLETED
    payment={status=REFUNDED, refunded_amount=0.00, stripe_refund_id=null}
    stateLog=[null->CREATED by=37ccfadc-2a4b-4d00-b00c-82e80157d781 reason=Order created from cart, CREATED->PAID by=37ccfadc-2a4b-4d00-b00c-82e80157d781 reason=Mock payment success]
    statement(total_refunds=0.00,net=9000.00)
    adjustments=0
    paymentAudits=[ORDER_PAYMENT_MOCK_SUCCESS, PAYMENT_REFUNDED]
    stripeRefundCalls=0
```
