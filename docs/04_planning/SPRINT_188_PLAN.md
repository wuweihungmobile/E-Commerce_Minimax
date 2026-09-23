# Sprint 188 Plan — 背景排程/非 REST 呼叫路徑審查（DEF-261/DEF-262）

**Sprint**: Sprint 188
**日期**: 2026-09-23

## 1. 起點

多 Sprint 安全強化循環（Sprint 66 起）長期只掃描 REST/WebSocket 呼叫路徑，原始計劃收尾時即記錄「排程任務/訊息佇列/webhook 等非 REST 呼叫路徑」是尚未系統性審視過的角落（見 memory `e-commerce-multi-sprint-test-loop`）。本輪自選掃描角度：全庫所有 `@Scheduled` 背景任務與外部 webhook 入口的正確性與安全性。

`grep -rln "@Scheduled" backend/src/main/java` 找到僅 2 個檔案：`SettlementGenerator`（週結算單生成）、`NotificationConsumerService`（Redis List 通知消費 + 重試佇列）。逐行審查後延伸至通知消費服務相依的 Stripe webhook 簽章驗證入口（`StripeWebhookController`/`StripeSignatureVerifierService`/`PaymentWebhookService`），因其同屬「非典型 REST 呼叫路徑」（外部伺服器回呼，非使用者操作）且與排程/佇列機制共享「無使用者 session、無法用一般 IDOR 檢查框架審視」的特性。

## 2. 調查方法與結果

本輪未派出背景 agent，由主控 session 直接逐檔審查（範圍小、已知檔案僅 2+3 個，直接讀碼效率高於協調 agent）：

- `SettlementGenerator.generateWeeklyStatements`／`generateStatementForTenant`：逐租戶迴圈呼叫，正確使用 `tenant.getId()` 隔離；`generateStatementForTenant` 雖為 `public`，但全庫 `grep` 確認**無任何 Controller 曝露此方法**，僅排程與既有整合測試呼叫，無可利用路徑。`getStatementsByTenant`/`getStatementById` 兩個查詢方法正確使用 `TenantContext.getCurrentTenant()`。**確認無缺陷**。
- `NotificationConsumerService`：發現 🟡 `DEF-261`——`handleFailedMessage` 的重試佇列 key 僅以 `"notification:retry:" + retryCount`（重試層級 1~3）命名，**未以訊息本身區分**。當兩則不同訊息在同一秒內都失敗並落在同一重試層級時，後寫入的 `redisTemplate.opsForValue().set(...)` 會直接覆蓋先前訊息的序列化內容，先前訊息永久遺失（`processRetryQueue` 之後讀到的只會是後者），且不會產生任何錯誤或日誌可供追查——屬靜默資料遺失，非拋例外的明確失敗。`moveToDeadLetterQueue` 的 DLQ key 已正確以 `messageId` 命名，僅重試佇列這一段有此問題。
- 延伸審查 Stripe webhook 入口：`StripeWebhookController` 的 `/v2/payments/webhook/stripe` 端點在 `SecurityConfig` 為 `permitAll()`（Stripe 回呼不帶 JWT，Sprint 160 DEF-202 既有修復）。`StripeSignatureVerifierService.verify()` 的 HMAC-SHA256 簽章驗證是此端點唯一的身分驗證機制；但其實作在 `webhookSecret` 為空字串時**直接 `return`，完全跳過驗證**（程式碼註解與既有測試 `StripeWebhookReachabilityTest` 皆明確承認此為刻意的「測試模式」設計）。`application.yml` 的 `stripe.webhook-secret: ${STRIPE_WEBHOOK_SECRET:}` 預設值即為空字串。**發現 🔴 `DEF-262`**：docker-compose.yml（其註解自稱「正式環境：docker compose -f docker-compose.yml up -d」）的 backend service 環境變數區塊完全沒有傳遞 `STRIPE_WEBHOOK_SECRET`／`STRIPE_SECRET_KEY`，且 `SPRING_PROFILES_ACTIVE` 預設即為 `prod`。若照專案自己文件記載的方式部署且忘記另外注入這兩個環境變數（在此 compose 檔案原本的設計下，兩者事實上不存在管道可注入），`/v2/payments/webhook/stripe` 會在完全無簽章驗證的狀態下對外開放——任何人皆可 POST 偽造的 Stripe event payload（`checkout.session.completed`/`charge.refunded`/`account.updated`/`transfer.reversed`），`PaymentWebhookService.handleEvent` 除了 event id 去重外不做任何額外真偽查核，會直接把偽造事件當作權威付款狀態來源處理——可偽造任意訂單「已付款」、偽造退款、偽造 Stripe Connect KYC 完成狀態。與 `DEF-251`（Sprint 183，`JWT_SECRET` 寫死不安全預設值且無啟動期驗證）屬同一模式家族。

## 3. 修復範圍與實作

### DEF-261：通知重試佇列 key 碰撞

`NotificationConsumerService`：
- 新增常數 `RETRY_KEY_PREFIX = "notification:retry:"`。
- `handleFailedMessage`：key 由 `RETRY_KEY_PREFIX + retryCount` 改為 `RETRY_KEY_PREFIX + message.getMessageId()`，每則訊息各自獨立。
- `processRetryQueue`：原本假設固定 3 個重試層級 key 逐一檢查（`for (int retryCount = 1; retryCount <= MAX_RETRY_COUNT; ...)`），既有假設已隨 key 設計變更而失效，改用 `redisTemplate.keys(RETRY_KEY_PREFIX + "*")` 掃描所有待重試訊息（比照既有 `RefreshTokenService.blacklistAllRefreshTokens` 的 `keys(pattern)` 慣例），逐一取出、刪除、重新處理，移除原本已無意義的 `retryCount` 層級比對邏輯。

### DEF-262：Stripe webhook secret 缺失時 fail-fast

比照 `JwtTokenService`（DEF-251）的既有先例，於 `StripeWebhookController` 建構子新增 fail-fast 檢查，但**刻意加上 prod profile 條件**而非無條件拒絕——因為（不同於 JWT_SECRET）`stripe.webhook-secret` 留空對 dev/test/integration-test 環境是合理且必要的設計（無真實 Stripe 帳號時仍需能手動測試，見既有 `StripeWebhookReachabilityTest`），無條件拒絕會讓所有非 prod 環境無法啟動：

- 建構子新增 `@Value("${spring.profiles.active:}") String activeProfiles` 參數，若判定為 `prod` profile（逗號分隔比對，大小寫不敏感）且 `stripeWebhookSecret` 為 null/空白，丟出 `IllegalStateException`。
- 呼叫端調查後確認 docker-compose.yml 的 `SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}` 才是專案自己文件記載的正式部署方式（`docker compose -f docker-compose.yml up -d`），CI/測試/開發皆各自明確覆寫為 `integration-test`/`test`/`dev`，不受此 fail-fast 影響（`grep` 確認 CI workflow 與 `docker-compose.test.yml`/`docker-compose.override.yml` 均已個別設定對應 profile）。

**Docker 配置變更（經 `AskUserQuestion` 徵詢使用者確認後執行，非自行判斷）**：發現若只加上述 fail-fast，會讓 docker-compose.yml 原本記載的「正式環境」部署方式直接開不起來——因為該檔案的 backend service 環境變數區塊從未傳遞 `STRIPE_WEBHOOK_SECRET`／`STRIPE_SECRET_KEY`（對照既有已傳遞的 `JWT_SECRET` 那行）。使用者確認後，於 `docker-compose.yml` backend service 環境變數區塊比照 `JWT_SECRET=${JWT_SECRET:-...}` 既有寫法，新增：
```yaml
- STRIPE_WEBHOOK_SECRET=${STRIPE_WEBHOOK_SECRET:-}
- STRIPE_SECRET_KEY=${STRIPE_SECRET_KEY:-sk_test_placeholder}
```
純新增 env var 傳遞，未變更任何 image tag／volume／healthcheck／資源限制，`docker compose -f docker-compose.yml config -q` 驗證語法正確。此變更本身不改變本機/CI/dev 任何既有行為（這些管道皆已各自覆寫 profile 為非 prod），僅讓「未來若真的用這份 compose 檔案部署到 prod」時，操作者只要在自己的 `.env`／shell 設定 `STRIPE_WEBHOOK_SECRET` 真實值即可正常啟動，而非過去那種「不論有沒有設定都靜默通過」的狀態。

## 4. Checkstyle 插曲

第一次 `mvn -o clean verify`（本輪第二次跑，第一次意外撞到 `checkstyle-suppressions.xml` DTD 解析的暫時性 DNS 失敗，與程式碼無關，見 §8）在 `checkstyle-test` 階段抓到 `NotificationConsumerServiceTest.java` 有 1 個真實違規：`import static org.mockito.Mockito.doAnswer;` 未使用（測試中改用 `lenient().doAnswer(...)` 實例方法呼叫，並非靜態匯入的 `Mockito.doAnswer(...)`）。移除該行未使用匯入後，`checkstyle-test` 與其餘全套測試皆綠燈。

## 5. 測試

**DEF-261 紅燈驗證**：`NotificationConsumerServiceTest` 新增 `handleFailedMessage_twoDifferentMessagesAtSameRetryLevel_mustNotOverwriteEachOther`。以真實會保存 key/value 的假 Redis String store（取代單純 mock 驗證呼叫次數）餵入兩則不同 `messageId` 的訊息，各自失敗一次進入同一重試層級；修復前執行確認**紅燈**（`fakeRedisStringStore` 只剩後寫入的那則，前一則遺失）；修復後重跑轉綠燈（兩則訊息的序列化內容皆能在 store 中找到）。

**DEF-262 驗證**：新增 `StripeWebhookControllerTest`（純建構子單元測試，無需 Spring context），5 個案例：prod + 空字串 secret 拒絕、prod + null secret 拒絕、prod 夾在多重 profile 中（如 `"metrics,prod"`）仍拒絕、prod + 已設定 secret 正常啟動、非 prod（`integration-test`/`dev`/空字串）+ 空 secret 皆正常啟動（不可回歸既有 `StripeWebhookReachabilityTest` 仰賴的 `integration-test` 行為）。

**全量回歸**：`make test-db-up`（真實 postgres/redis）後執行 `mvn -o clean verify`，結果見 §6。

## 6. 驗證結果

`mvn -o clean verify`（真實 postgres/redis）：**BUILD SUCCESS**，**1641 個單元測試（+6）+ 486 個整合測試（持平），0 failed**；checkstyle（main+test）**0 違規**。`docker compose -f docker-compose.yml config -q` 語法驗證通過。

## 7. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-261`：✅ 已修復（Sprint 188）。
- 新增 `DEF-262`：✅ 已修復（Sprint 188）。

## 8. 誠實揭露總結

- 本輪未派出背景唯讀 agent（範圍明確限定在 2 個 `@Scheduled` 檔案 + 3 個 webhook 相關檔案，直接讀碼比協調 agent 更有效率），與近期多數 Sprint「2 個背景 agent」的慣例不同，特此註明並非遺漏或偷工。
- `SettlementGenerator` 逐租戶結算迴圈與其查詢方法審查後**確認無缺陷**，是本輪唯一的「查了但沒找到問題」的負向結果。
- `DEF-262` 的修復範圍超出應用程式碼本身，延伸至 `docker-compose.yml` 基礎設施變更——這類變更依 `CLAUDE.md` 明文規定需要使用者明確授權才能進行，本輪透過 `AskUserQuestion` 徵詢並取得使用者同意（選擇「補上 env 傳遞」選項）後才動手，過程可於本次對話紀錄查證，非自行判斷。
- `DEF-262` 的 fail-fast 刻意加上 prod profile 條件（不同於 `DEF-251` 的無條件拒絕），因兩者情境不同：`JWT_SECRET` 沒有任何環境有理由留空，`stripe.webhook-secret` 則是 dev/test 環境的合理設計，若比照 `DEF-251` 無條件拒絕會直接打斷所有本機開發與既有 `StripeWebhookReachabilityTest`（`integration-test` profile）。此為刻意的差異化判斷，非疏忽少做防護。
- `LinePay webhook`（`handleLinePayWebhook`）維持既有 stub 狀態未觸碰——該端點本輪確認仍是零簽章驗證機制的未串接 stub，且依 `SecurityConfig` 仍需要認證（非 `permitAll`），無實際可利用路徑，比照 Sprint 160 DEF-202 既有決策不予處理。
