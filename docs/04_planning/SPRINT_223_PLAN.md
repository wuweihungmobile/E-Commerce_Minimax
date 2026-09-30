# Sprint 223 Plan — 訂房付款前端首次對真實後端走訪：打包版後端的購物車與帶冪等鍵訂房都回 500（DEF-313）

**Sprint**: Sprint 223
**日期**: 2026-09-30

## 1. 起點

使用者貼上 Sprint 217～222 總結，並說「請繼續完成任務！」（沒有指定新項目；「繼續」的解讀為**推論**，沿用 Sprint 216 的做法：不動需要使用者決定的項目，做 AI 能獨立處理的事）。

總結列出的待決定項目——`DEF-311`（訂房未付款逾時）、`DEF-312`（已付款訂房取消退款）、`DEF-308`、`DEF-303` (5)——都是「要不要自動退款／要不要把 24 小時套用到訂房」的產品決策，其中涉及真實金錢移動，本輪不動。

剩下的是總結自己承認的缺口：

- 最後一個 docs-only commit（`35f8caf`）的雲端 CI 沒等 → 已確認成功（run 36653249008）。
- Sprint 222 的 22 個訂房 Playwright 案例全程 mock，沒有對真實後端走過，也沒跑 `make validate-e2e`；「沒有人在真實瀏覽器點過這條流程」。

## 2. 查證與發現

### 2.1 為什麼真實後端 E2E 一直沒人跑

pre-push（v6）只跑 `make validate-push`（backend 單元＋schema 漂移＋frontend 快檢），雲端 push 觸發的 CI 不含 Playwright。`make validate-e2e` 是唯一對「打包 JAR＋真實 Postgres／Redis＋Playwright」全棧的關卡，得手動跑；Sprint 217～222 都沒跑。

### 2.2 全套 `validate-e2e`：85 通過／4 略過／1 失敗

- schema 對齊：backend 以 `ddl-auto=validate` 對 Flyway 重建的乾淨 DB 啟動成功。
- 唯一失敗是 `E2E-M11-009`（訂房結帳流程）。它是軟斷言測試：購物車是空的，按下「確認預訂」後 `waitForResponse('/v2/bookings')` 一定空等滿 15 秒（沒有請求可等），再加上註冊登入約 8 秒。
- **是不是 Sprint 222 的回歸？不是。** Sprint 222 對結帳頁只改了「預訂成功」畫面，空購物車走不到那裡；單獨重跑 3 次全過、耗時 22.0～23.9 秒（上限 30 秒），全套 2 個 worker 並行時才逾時。`E2E-M11-011` 是同樣寫法（剛好沒超時）。兩者改給 60 秒，語意不變。
- 上輪「22 個訂房 spec 全過」只涵蓋 `at-room-booking`／`at-booking-payment` 兩支檔案，沒有跑全套——這是上輪總結沒說清楚的地方。

### 2.3 訂房付款走訪（打包 JAR＋真實 Postgres／Redis＋真實 Chromium）

**靜態契約比對**（前端 `bookingPayment.ts`／`BookingPaymentCard` 對後端 `BookingPaymentController`／DTO）：四條路徑、`sessionId` 參數名、`CheckoutSessionResponse`、`OrderPaymentStateDto` 的欄位都對得上；生產權限表的 BUYER 有 `booking:read`／`booking:create`。`payments` 在真實 Flyway schema 有兩個獨立可空 FK（`order_id`、`booking_id`），唯一約束只有 `idempotency_key` 的部分唯一索引，`bookings.status`／`payments.status` 沒有 CHECK——訂房付款的寫入與之相容（測試 DB 由 Hibernate 產生、看不到這些約束，所以特地查過）。

**資料前提全由真實流程建立**（與 `at-m17-002` 同一套）：店主註冊→驗證 Email（連結從後端日誌取）→`POST /v2/tenants/apply`→管理員核准→**核准後 `BOOKING_ENABLED` 預設 false，需管理員開啟**（設計，不是缺陷；沒開建房源回 403 `E-2004`）→店主重新登入（JWT 才帶新租戶）→建立 ROOM 房源。

**付款主流程全部正確**：建立訂房（CREATED、6,400 TWD）→詳情頁付款卡片→真實瀏覽器點「確認付款（模擬）」→付款成功卡片（交易編號、付款時間）→重新載入仍是已付款；狀態端點 `PAID`／`SUCCESS`；重複付款 422 `E-5011`；Stripe 未啟用 400 `E-6002`；資料庫只有一筆 `MOCK`／`SUCCESS`；別的買家查詢與付款都是 403；Stripe 成功頁缺 `session_id` 只顯示「缺少付款工作階段資訊」，不誤報成功。

**但走訪中出現兩處 500**——建立訂房（帶 `Idempotency-Key`，前端結帳頁每次都帶）與房間加入購物車。

### 2.4 DEF-313：兩個同名的 `redisTemplate` bean

後端日誌：

```
SerializationException: Could not write JSON: Java 8 date/time type `java.time.LocalDate` not supported by default
  at IdempotencyService.markCompleted → BookingController.createBooking        （訂房；購物車那條是 java.time.Instant）
```

- `RedisConfig`（初始 commit）與 `RedisStreamConfig`（Sprint 14）各宣告一個同名 `@Bean redisTemplate`；前者的 ObjectMapper 有 `JavaTimeModule`，後者用 `new GenericJackson2JsonRedisSerializer()`，沒有。
- 2026-06-12（`ab3a23f`「啟用 bean definition overriding 解決 RedisTemplate 衝突」）有人遇到「bean 重複」的啟動錯誤，**用 `allow-bean-definition-overriding: true` 消音，沒有修同名**。兩個定義從此靜默互相覆蓋、後註冊者勝。
- 目錄式 classpath（IDE、`mvn test`）按檔名排序掃描：`mq` < `redis`，`RedisConfig` 後註冊而勝出，一切正常。JAR 依項目排列順序，實測 `RedisConfig.class` 排在 `RedisStreamConfig.class` 前，串流版勝出——順序取決於建置機器，不是可依賴的性質。
- 影響：`RedisCartService.addItem`（`CartItemData` 含 `Instant`）→ **購物車加入任何商品／房間都 500**；`IdempotencyService.markCompleted`（回應含 `LocalDate`）→ 帶冪等鍵的 `POST /v2/bookings` 與 `POST /v2/checkout/mixed` 500，而且**訂房已寫入、日曆已鎖之後才失敗**，使用者重試會再產生一筆。前端每次送出都帶冪等鍵，所以真實使用者幾乎必中。
- 為什麼所有測試都看不到：後端整合測試（`IntegrationTestConfiguration`）把 Redis 整個換成 mock；前端訂房／購物車 E2E 全是 `page.route` mock 或空購物車的軟斷言（乾淨 DB 沒有商品，走不到加入購物車）。**唯一同時具備「打包 JAR＋真實 Redis＋真的建立資料」的只有真實後端走訪。**
- 盤點：全庫 `@Bean` 方法名只有 `redisTemplate` 重複。注入 `RedisTemplate<String, Object>` 的有 6 個服務：冪等（`IdempotencyService`）、購物車（`RedisCartService`）、Refresh Token（`RefreshTokenService`）、分散式鎖（`RedisLockService`）、通知生產者與消費者。`RefreshTokenService` 的 Lua 以「參數也經同一個序列化器」比對（與序列化格式無關）、鎖與通知只存字串，實際受害的只有把 `java.time` 存進去的購物車與冪等回應。（`LoginAttemptService` 用的是 `StringRedisTemplate`，不在其中。）
- 通知服務（`NotificationProducerService`／`ConsumerService`）自 DEF-013 起只用 List／Value 操作，不需要另一個序列化器；它們的真實 Redis 測試 `NotificationPipelineRedisIntegrationTest` 本來就是用 `new RedisConfig().redisTemplate(...)`。

### 2.5 順手發現

- **結帳頁沒通知 Header 更新購物車徽章**：訂房成功後結帳頁有刪掉購物車裡的房間，卻沒呼叫 `notifyCartChanged()`，右上角徽章停在「1」（截圖看到）。
- **`DEF-315`**：換成單一 template 後，檢查打包 JAR 上 Refresh Token 輪替（Sprint 213）與登入鎖定（Sprint 214）。鎖定正常；輪替在「同一秒內簽發」時失效——Refresh Token 沒有 `jti`，同一秒簽發的兩顆位元組相同（`R1 === R2`），輪替後 `storeRefreshToken` 又把同一個 key 寫回 `valid`。間隔 1.1 秒後 token 不同，重放被擋（401 `E-1003`）。與 template 無關，換之前就存在。只登記。
- **`DEF-314`**：`validate-e2e` 的 `npm ci` 印出「12 vulnerabilities（1 critical）」。`npm audit --omit=dev`：`next` 16.2.2（`package.json` 精確釘定）有 27 則公告（含 critical 的兩則 RCE、high 的 Middleware／Proxy bypass 與 Server Components DoS、moderate 的「使用 CSP nonce 的 App Router XSS」——本專案的嚴格 nonce CSP 就掛在 `proxy.ts`），`axios` 1.15.0 有 28 則。CI 的 `npm audit` 是 `continue-on-error: true` 且不是 push 觸發的 workflow，所以沒人被擋下。只登記，排入 Sprint 224。

## 3. 修復

- `RedisStreamConfig`：刪除重複的 `redisTemplate` bean，只留通知佇列的 key 常數（類別上寫明原因）。
- `application.yml`：拿掉 `spring.main.allow-bean-definition-overriding`（預設 false），同名 bean 之後會在啟動時就失敗；註解說明為什麼刻意不設。`application-integration-test.yml` 為了 mock 覆蓋而保留自己的開關。
- 前端：結帳頁移除購物車房間後呼叫 `notifyCartChanged()`；Header 徽章加 `data-testid="header-cart-count"`。
- `at-m11-cart-checkout.spec.ts`：`E2E-M11-009`／`E2E-M11-011` 各加 `test.setTimeout(60_000)`（註解寫明實測數字）。

## 4. 測試

**紅燈先行**（修復前）：

- `RedisTemplateBeanWiringTest`（2 案例）：`ApplicationContextRunner` 以「兩種註冊順序」建立 `RedisConfig` 與 `RedisStreamConfig`，`redisTemplate` 必須能往返 `LocalDate`／`Instant`。`RedisConfig` 先註冊的案例拋出與正式日誌**同一句**「Java 8 date/time type `java.time.LocalDate` not supported」；反向順序通過——證明勝負只取決於順序。
- `BeanNameUniquenessTest`（3 案例）：掃全部正式程式的 `@Bean` 方法名不可重複（修復前列出 `redisTemplate` 的兩個宣告者）；另兩個「守門自證」案例確認偵測器真的找得到重名與 `@Bean(name=…)` 的明確名稱（避免變成永遠綠的空殼）。

**永久的真實後端 E2E** `at-booking-payment-real.spec.ts`（6 案例，不 mock）：BPAYR-01 帶冪等鍵訂房 201＋同一把鍵重放回同一筆且只建一筆；02 詳情頁真實付款、重複付款 422、Stripe 未啟用 400；03 房間加購物車→結帳→付款、徽章消失；04 別的買家 403；05 商品加購物車→商品結帳→訂單 `PAID`；06 合併結帳（商品＋房間，帶冪等鍵）→訂單與訂房都 `PAID`。

**突變驗證**（兩處都轉紅、已還原）：

1. 還原 `RedisStreamConfig`＋`application.yml` 到修復前、重打 JAR → BPAYR-01 回 `500 E-9900`（Expected 201）。
2. 拿掉 `notifyCartChanged()` → BPAYR-03 在徽章斷言轉紅（Expected 0、Received 1）。

## 5. 驗證結果

- 後端 `mvn -o clean verify`：單元 **1877**（+5）／整合 **641**／0 失敗／checkstyle 0／PMD 通過（14 分 20 秒）。
- 前端 `tsc --noEmit` 乾淨；eslint 0 error（`checkout/page.tsx` 3 個既有 warning 非本輪造成）。
- 修復後對打包 JAR 的走訪：帶冪等鍵訂房 201、重放 200 且同一個 id、資料庫只有一筆、加入購物車成功、結帳完成畫面付款成功、商品結帳與合併結帳兩邊都 `PAID`；`at-booking-payment-real` 6 案例全過。
- 修復後對打包 JAR 的 Refresh Token／登入鎖定：見 §2.5（鎖定正常；輪替僅有 DEF-315 的既有邊緣情況）。
- `make validate-e2e`（全套、乾淨 DB、打包 JAR）：第一次（修復前）85 通過／4 略過／**1 失敗**（`E2E-M11-009`，§2.2）；修復後 **96 個測試：92 通過／4 略過（與基準相同的條件式略過）／0 失敗**，3.8 分鐘，`E2E_EXIT=0`。backend 對乾淨 Flyway 資料庫以 `ddl-auto=validate` 啟動成功——也證明拿掉覆蓋開關後主程式沒有其他重名 bean。

## 6. 只登記、未修的項目

- **`DEF-314`**（前端相依套件 critical／high 漏洞）：排入 Sprint 224。
- **`DEF-315`**（同一秒簽發的 Refresh Token 相同）：修法小（加隨機 `jti`，驗證時不強制），但動到認證 token 格式，宜單獨一輪。
- 仍等使用者決定，本輪未動：`DEF-311`（訂房未付款逾時，建議 `bookings.payment_due_at` 只對新訂房生效）、`DEF-312`（已付款訂房取消退款，PRD Q14 有規則，**啟用 Stripe 前必決**）、`DEF-308`、`DEF-303` (5)。

## 7. 決策與已知限制

- **兩個同名 bean 選 `RedisConfig`、刪除另一個**（不改名保留）：`RedisConfig` 較早（初始 commit）、較多測試依賴、在開發與測試環境實際生效；通知服務不需要另一個序列化器。
- **拿掉覆蓋開關而不是保留**：那個開關就是「同名 bean 不會在啟動時報錯」的根源；保留它，下一個同名 bean 仍會靜默地讓行為取決於註冊順序。拿掉後打包 JAR 與 `validate-e2e` 都能正常啟動，代表主程式沒有其他重名 bean。
- **切換序列化器對既有資料的影響**：JAR 上原本贏的串流版與 `RedisConfig` 的 default typing 設定不同，切換後已存在 Redis 的購物車／冪等資料可能讀不回來。目前沒有已上線的資料（Stripe 尚未啟用），購物車與冪等鍵都有 TTL；日後若有正式環境需要留意。
- `at-booking-payment-real` 只涵蓋 Mock 付款；Stripe 路徑（含合併結帳 Stripe 模式仍分開付款）仍未對真實 Stripe 驗證。E2E 只在本機守門執行（雲端 push CI 不含 Playwright）。
- 只確認了 `@Bean` 重名這一類「只在 JAR 才壞」的問題，沒有系統性掃描其他類別。

## 8. 後續

- Sprint 224：`DEF-314`（升級 `next`、`axios`，以 tsc、lint、build、全套 `validate-e2e` 驗證，含安全標頭／CSP spec）。
- 等使用者決定：`DEF-311`／`DEF-312`／`DEF-308`／`DEF-303` (5)。
