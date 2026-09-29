# Sprint 219 Plan — 啟用排程（DEF-305）、未付款訂單 24 小時逾時自動取消（DEF-302）

**Sprint**: Sprint 219
**日期**: 2026-09-30

## 1. 起點

使用者對 Sprint 216 登記的 DEF-302（沒有未付款訂單逾時取消）回覆「24 小時」。實作需要排程；規劃時查證發現全專案從未啟用排程（Sprint 217 登記為 DEF-305），所以兩項一起處理：先讓排程真的會跑，再放上第一個新排程。

## 2. DEF-305：排程從未執行

`grep -rl EnableScheduling` 全庫零命中、`git log -S EnableScheduling` 查無紀錄——自第一個 commit 起就沒有。Spring 不會自行處理 `@Scheduled`，所以：

| 排程 | 影響 |
|------|------|
| `SettlementGenerator.generateWeeklyStatements`（週一 00:00 台灣時間） | 每週結算單從未自動產生（DEF-287 之後才有管理員手動觸發端點） |
| `NotificationConsumerService.consumeNotifications`／`processRetryQueue` | Redis `notification:stream` 只增不減：單筆通知永遠停在 `isSent=false`，廣播通知（只推佇列、不直接寫庫）從未出現在任何人的通知列表 |

### 2.1 啟用前必須先處理的兩個問題

1. **啟用會讓單筆通知變兩筆**：`NotificationService.sendNotification` 先寫一列 `isSent=false`（預備狀態）再推佇列，消費者處理時又新增一列 `isSent=true`。修法：訊息帶上預建列的 id（`NotificationMessage.notificationId`），消費者更新那一列；廣播沒有預建列，照舊新增。預建列與入佇在同一個 `@Transactional` 方法裡，訊息推進 Redis 時預建列的交易可能還沒提交（BRPOP 一有訊息就醒來），找不到列時不新增重複列、改走既有重試機制（約 5 秒後、最多 3 次）。
2. **`@Scheduled` 會落到 WebSocket 的排程器上**：專案有 STOMP 設定時，Spring Boot 不會建立自己的 `taskScheduler`，`@Scheduled` 用的是 STOMP 的 `messageBrokerTaskScheduler`。**冒煙測試實測**：日誌執行緒名是 `MessageBroker-3`，池大小等於 CPU 核數（容器限 1 核時只有 1 條）。通知消費者每輪最多阻塞 1 秒、週結算可能跑很久，共用會拖慢 WebSocket 心跳。改由 `SchedulingConfig` 實作 `SchedulingConfigurer`，使用專屬的 `app-scheduler-*` 執行緒池（預設 4 條，`APP_SCHEDULING_POOL_SIZE`）。

### 2.2 `SchedulingConfig`

`@EnableScheduling`＋`@ConditionalOnProperty(app.scheduling.enabled, 預設 true)`。整合測試 profile（`application-integration-test.yml`）關掉它，避免背景任務碰到測試資料與 mock 的 Redis；正式環境緊急時可設 `APP_SCHEDULING_ENABLED=false`。E2E 與 schema 守門使用 `test` profile，排程照常啟用。

## 3. DEF-302：未付款訂單 24 小時自動取消

`OrderTimeoutService`（每 5 分鐘一輪，首次延遲 1 分鐘）把建立超過 24 小時仍是 `CREATED` 的訂單逐張取消。每張各自一個交易，一張失敗不影響其他張；每輪最多處理 20 批（每批 100 張）以便首次啟用時消化累積的舊單，整批沒進展就停止。

取消動作是 `OrderService.cancelExpiredUnpaidOrder`，補償與買家自己取消完全相同（共用 S218 的 `compensateCancellation`：釋放預留、退還優惠券），操作者為系統（狀態紀錄與稽核的使用者為 null，理由「Unpaid order timed out」）。

**把關在同一條 UPDATE 裡**（`OrderRepository.cancelIfExpiredUnpaid`）：`status = CREATED AND createdAt < cutoff`，並排除「已有成功付款」與「24 小時內開始過 Stripe 結帳」的訂單。與買家付款的 CAS（CREATED→PAID）搶同一個狀態，恰好一邊成功。

**為什麼排除進行中的 Stripe 結帳**：Stripe Checkout 工作階段預設建立後 24 小時才到期（程式碼沒有自訂 `expires_at`）。訂單在 24 小時就取消、而買家 23 小時才點「前往付款」，買家仍可能在之後付款成功，結果錢收了、訂單卻已取消——而 `markStripePaymentSucceeded` 對不可付款的訂單只會把付款標成功、不會退款。取消條件因此是「訂單超過 24 小時，**且**最近一次結帳工作階段也已超過 24 小時」（結帳 PROCESSING 的付款從來不會被 `checkout.session.expired` 清掉，Stripe webhook 沒處理這個事件，所以用 `payments.created_at` 判斷）。

## 4. 測試

- **`OrderTimeoutIntegrationTest`**（新，8 案例，真實 PostgreSQL＋真實 Redis 購物車）：25 小時前建立 → 取消且預留釋放、優惠券額度退還、狀態紀錄與稽核記為系統操作；23 小時前 → 不動；已付款 → 不動；Stripe 結帳 1 小時前開始 → 不取消、工作階段也過期後取消；已有成功付款的 CREATED 訂單 → 不取消；被取消後買家再付款 → E-5011；重複執行只補償一次；**`cancelExpiredUnpaidOrder` 直接呼叫時 UPDATE 自己把關**（未逾時／已付款／近期結帳／已有成功付款皆回 false）——候選查詢只是挑人，買家付款與開始結帳可能發生在「挑出來」與「取消」之間，這個案例才碰得到 UPDATE 本身的條件。
- **突變驗證**：UPDATE 的 PROCESSING 條件恆假 → 直接呼叫案例紅；SUCCESS 條件恆假 → 紅；逾時判斷反向 → 5 個案例紅；拿掉補償 → 3 個案例紅。**過程中有兩次突變寫壞了 JPQL（括號不平衡、留下未使用的參數），整個 Spring context 起不來、所有案例報錯**——那不是「被抓到」，是突變本身無效，重做並先確認突變只影響語意才算數。第一次做 UPDATE 的突變時 7 個案例（當時的總數）中沒有任何一個失敗，才發現原本的測試只走「候選查詢＋UPDATE」兩層、UPDATE 那層從未被單獨碰到，補上直接呼叫案例。
- **`SchedulingConfigTest`**（新，8 案例）：開關預設啟用／true 啟用／false 不註冊；`SchedulingConfig` 是 Spring 掃得到的 `@Configuration`；每個 `@Scheduled` 方法所在的類別都是會被掃描的元件；已知的四個排程都還在；**探針元件的 `@Scheduled` 方法真的會被執行且跑在 `app-scheduler-*` 池上**（拿掉 `@EnableScheduling` 或拿掉專屬排程器都會紅）；關閉時探針完全不執行。
- **`NotificationPipelineRedisIntegrationTest`**（新，3 案例，真實 Redis＋生產相同的序列化器，只 mock 資料庫）：單筆通知經 LPUSH／BRPOP 與 JSON 往返後，消費者更新預建列（同一個實體、`isSent=true`、歷史一筆）；廣播照舊新增；預建列尚未提交時不新增重複列、進重試佇列，列出現後只更新一次。既有 `NotificationProduceConsumeTest` 用記憶體佇列取代 Redis——這是第一次在真實 Redis 上跑這條管線。
- `NotificationConsumerServiceTest`（3→6）、`NotificationServiceTest`（改為驗證帶預建列 id 的送出）、`OrderTimeoutServiceTest`（新，5 案例：截止時間、批次、失敗隔離、無進展停止、每輪批次上限）、`OrderServiceTest`（73→75：搶到取消的補償與系統操作紀錄、沒搶到什麼都不做）。
- **真實應用冒煙測試**（本機起後端、`make test-db-up` 的 PostgreSQL＋Redis，`ORDER_TIMEOUT_INITIAL_DELAY_MS=8000`）：第一次啟動，排程 8 秒後在 `MessageBroker-3` 上取消 4 張測試庫裡的舊未付款訂單——**這是專案第一次真的執行排程**，也順帶證實了 §2.1 的執行緒池問題；改用專屬池後重啟，`app-scheduler-2` 取消了一張新塞的 26 小時前訂單；經真實 API 送單筆通知 → 資料庫只有 1 列、`is_sent=true`、佇列清空、歷史 1 筆；廣播給 121 位使用者 → 121 列、121 位不同使用者、全部已送出、每人恰 1 列、日誌無錯誤／DLQ。

## 5. 驗證結果

- 全量 `mvn -o clean verify`（`make test-db-up` 已啟動真實 postgres/redis）：單元 **1841**（+18）／整合 **617**（+11）／0 failures／0 errors／0 skipped；但 checkstyle（main）擋下 1 個錯誤——`SchedulingConfig` 的 `jakarta.annotation.PreDestroy` import 順序（`BUILD FAILURE`，12 分 47 秒）。修正 import 順序（不影響行為）後重跑 checkstyle（main＋test）0 violations、PMD 通過、`BUILD SUCCESS`；沒有為此再跑一次全量。
- 未變更 entity／migration；前端沒有改動；新增的是後端設定屬性（皆有預設值，不設定行為即為預設）。

## 6. 已知限制與決策

- **通知消費速度上限約每秒 1 則**：消費者每輪只彈出一則訊息，`fixedDelay = 1000`。冒煙測試廣播 121 則花了約 2 分鐘。廣播給數千位使用者要花一小時以上。本輪不改（不在需求內；目前只有管理員 API 會發通知，前端零呼叫點），登記為低優先。修法是每輪迴圈彈出至多 N 則。
- **啟用排程會讓正式環境的既有行為改變**（部署前請知道）：第一個台灣時間週一 00:00 自動產生結算單，且依 Sprint 195 的語意納入所有「已完成且尚未結算」的訂單、不限下單週；Redis 裡累積的通知訊息會被消費。詳見 [SETTLEMENT_JOB_RUNBOOK.md](../08_deployment/SETTLEMENT_JOB_RUNBOOK.md) 開頭的更正。想先人工確認：部署時設 `APP_SCHEDULING_ENABLED=false`。
- **正式環境若已累積付不了款的 CREATED 訂單（DEF-298 時期）**：啟用後第一輪會把超過 24 小時的全部取消並釋放預留庫存與優惠券額度，這是預期行為；每輪至多 2,000 張，其餘下一輪繼續。
- **沒有通知買家**：PRD US-014 提到逾時後通知買家並提供重試連結。使用者只決定了 24 小時，通知是否送、送什麼是產品決策；管線現在能運作了，接上只是呼叫 `NotificationService`，本輪沒做。
- **訂房（Booking）不在本輪範圍**：訂房目前沒有付款步驟（DEF-303 (1)），逾時取消要等訂房付款存在之後才有意義，否則會把使用者剛訂好、根本無法付款的訂房在 24 小時後取消。
- **付款成功但訂單已不可付款仍不會自動退款**：這是既有缺口（例如管理員取消訂單的同時買家完成了 Stripe 付款）。本輪只是避免「逾時取消」製造更多這種情況（見 §3 的排除條件）。
- 冒煙測試使用共用的測試資料庫：原本有 26 張 CREATED 訂單，其中 4 張超過 24 小時被第一次排程取消（其餘 22 張較新）；那是歷次測試留下的資料，無影響。
