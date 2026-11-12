# Sprint 342 計畫書：Stripe `charge.refunded` webhook 的退款記帳（`DEF-453`）

> 日期：2026-11-13。範圍與做法的每一個「推論」「決定」都標明出處，可一句話推翻（§11）。預測在跑之前寫下（§3），跑完逐條對答案（§4）。

## 1. 背景與授權

### 1.1 使用者的訊息

貼回 Sprint 341 的總結，加一句「請繼續完成任務！」。**沒有任何 `==>` 回覆**，總結裡也**沒有點名下一項要做什麼**、沒有「需要你決定」的清單（總結自己寫「這輪沒有需要用互動選項問你的」）。

### 1.2 我的解讀（推論，不是使用者說的）

沿用本專案「貼回總結＋『請繼續』（無 `==>`）」的既有做法（記憶 `inline-decision-replies-mean-proceed`）：範圍＝**總結自己列為「沒做」、而且我能獨立處理、追蹤表沒標「要使用者決定」**的項目；不替使用者做產品／部署決策。

總結「沒做」的清單逐項判斷（讀了追蹤表整列與相關程式碼，不是憑印象）：

| 項目 | 判斷 | 理由 |
|---|---|---|
| `DEF-500` ② 跨店鋪列表的篩選／搜尋／排序 | 不做（要你決定） | 追蹤表原文「後續是新功能，需要產品決定」；分類是店鋪內的概念，跨店鋪沒有明確語意 |
| `DEF-500` ③ 卡片的店鋪名稱不連到店鋪 | 不做（設計取捨） | 整張卡片本身就是指向文章的 `<Link>`（`PlatformBlogFeed.tsx`），店鋪名稱要連結就得處理巢狀連結（stretched-link 之類），是 UI 設計決定 |
| `DEF-500` ④ 大量貼文下的列表查詢耗時沒量測 | **量測題，我能做** | 追蹤表建議欄第一步就是「先量測」。**本輪沒做**，原因與順序見 §1.3 |
| `DEF-500` ⑤ 舊網址 `/blog/{slug}` 導向頁仍是登入者專用 | 不做（要你決定） | 舊網址沒有店鋪、文章代稱只在單一店鋪內唯一，匿名訪客要能用就得決定「同名代稱怎麼處理」；改版前匿名訪客本來就打不開，不是退步 |
| `DEF-502` 沒有代理時的共用限流桶 | 不做（要你的部署資訊） | 🟡，總結已寫成「上線前你要做的」 |
| `DEF-503` sitemap 超過 2 萬筆 | 不做 | 現在資料量遠低於上限，做了是推測性功能（Rule 2） |
| `DEF-504` 404 的第一份 HTML 是空殼 | 不做 | Next 框架行為，沒有改變它的辦法 |
| `DEF-505` 分類／排序是按鈕 | 不做 | 刻意取捨，登記以免被當遺漏 |
| 「沒用真的搜尋引擎／沒量真實網路首屏」 | 不做 | 沒有公開網域；首屏量測是效能量測，不是缺陷 |

### 1.3 本輪實際做的：`DEF-453`

追蹤表上有一項不在 S341 總結裡、但**我自己在 Sprint 330 寫下「下次說『請繼續』我可以直接做修復」**的項目：`DEF-453`（🟠，金額記帳錯誤）。之後 Sprint 331～341 都被你用 `==>` 指定的工作取代，它一直沒被做。它不需要產品決策（追蹤表建議欄寫明「修法有既有語意可循」；唯一的小取捨 P3 見 §11 第 7 點），而且是目前所有「AI 能獨立處理」的項目裡**唯一涉及金額記帳**的，所以排在 `DEF-500` ④（效能量測）之前。

> 這個排序是我的判斷：把 `DEF-453` 放在總結列出的 `DEF-500` ④ 前面，理由是它是金額記帳錯誤、且已經等了 11 輪。若你要先做 ④，一句話即可。

## 2. 動手前用工具核對的事實（讀原始碼／指令輸出，不是記憶）

| 說法 | 核對結果 |
|---|---|
| 追蹤表 `DEF-453` 描述的程式碼現在還是那個樣子 | `git log -- backend/src/main/java/com/nextkey/ecommerce/core/payment/` 最近一筆是 S327（`3e03d234`，`DEF-452`），Sprint 330 以後沒有任何 commit 動過；`PaymentStateService.markStripeRefunded`（第 588～624 行）仍只做 `markRefundedIfNotAlready`＋訂單轉 `REFUNDED`＋狀態紀錄＋稽核，**沒有金額、沒有 `refunded_amount`、沒有呼叫調整單、沒有訂房分支** |
| webhook 讀不讀金額 | `PaymentWebhookService.dispatch`（第 88～93 行）只取 `payment_intent` 與 `refunds.data[0].id` |
| 誰呼叫調整單服務 | `grep` 結果：`PaymentStateService` 的三個 API 退款路徑（訂單核心、訂房系統退款、訂房人工退款）；`PaymentWebhookService`、`PaymentService.processRefund` 都沒有 |
| 未結算的訂單日後怎麼被扣 | `SettlementGenerator.buildRefundedAmountMap`（第 245～255 行）讀 `Payment.refundedAmount`，所以**即使訂單還沒被任何結算單認領，webhook 不記 `refunded_amount` 也會讓日後結算時多付**（追蹤表只講了「已被認領」的情況） |
| 還有誰依賴「webhook 不寫 `refundedAmount`」 | `AnalyticsService.refundedMoney`（第 73～80 行）：`REFUNDED` 取付款全額、否則取 `refundedAmount`。修法把 `refundedAmount` 記準後兩種狀態仍給出同樣的數字；但它旁邊的註解會變成不實，修完要同步更正 |
| webhook controller 對失敗的處理 | `StripeWebhookController.handleStripeWebhook`（第 98～104 行）：`catch (RuntimeException)` → 記錄後**仍回 2xx**（註解「避免 Stripe 無限重送」）。意思是：webhook 的處理一旦失敗，交易回滾、事件沒被記為已處理、**Stripe 也不會重送**。這不是 `DEF-453` 的範圍，但修法會讓 webhook 多一個可能失敗的步驟（調整單），見 §8 與 §10（新登記） |
| `payments.stripe_payment_intent_id` 有沒有唯一索引 | 沒有（V59 只加欄位）；`findByStripePaymentIntentId` 回 `Optional`，沿用既有假設 |
| `payments.refunded_amount` 的預設與空值 | V63：`DECIMAL(12,2) NOT NULL DEFAULT 0` |
| repo 既有的悲觀鎖寫法 | `OrderRepository.findByIdForUpdate`、`UserRepository.findByIdForUpdate`：`@Lock(PESSIMISTIC_WRITE)` ＋ `@Query("SELECT … WHERE …")`；沒有設鎖逾時提示 |

## 3. 成功標準、測試設計與預測（跑之前登記）

### 3.1 成功標準

1. Stripe `charge.refunded` 事件以 **`amount_refunded`（Stripe 端的累計值）當真相來源**，與本地 `payments.refunded_amount` 的**差額**才是本次退款：差額 ≤ 0 → 什麼都不做（涵蓋事件重送、API 路徑已先記帳、亂序到達的舊事件）；差額 > 0 → 記帳並在**同一個交易**扣結算單。
2. 全額（累計達付款金額）→ 付款 `REFUNDED`、`refunded_amount`＝付款金額；部分 → 付款 `PARTIALLY_REFUNDED`、訂單狀態不變。
3. 已出貨／已完成的訂單（`COMPLETED` 等）：付款與結算單正確，訂單狀態沿用既有 `canRefund` 規則（不動）。
4. 訂房付款有同樣的記帳。
5. 與 API 退款路徑**不重複扣、不互相蓋過**，不論誰先到、不論兩者併發。
6. 資料修好後重送同一事件會收斂（記帳是原子的，失敗不留半套）。
7. 修復前先對未修改的程式碼跑出紅燈（§4）；修復後全綠；突變驗證擊殺；完整後端 `mvn verify` 全綠。

### 3.2 測試設計

新類別 `StripeRefundWebhookAccountingRealStackIntegrationTest`：真實 PostgreSQL＋真實 Redis，走真實的 `PaymentWebhookService`／`PaymentStateService`／`SettlementAdjustmentService`（含交易代理）；只有 Stripe 閘道以 `@MockBean` 取代。固件沿用 Sprint 327 `RefundSettlementAdjustmentFailureRealStackIntegrationTest` 與 Sprint 330 探針的做法（真實 `checkout.session.completed` webhook 讓訂單 `PAID`；結算單與「被結算單認領」用 SQL 種；`COMPLETED` 用 SQL 設）。webhook 內容是手寫的 `charge.refunded` JSON（含 `payment_intent`、`amount`、`amount_refunded`、`refunded`、`refunds.data[0]`）。

| 編號 | 情境 | 修復後的期望 |
|---|---|---|
| R0（對照） | 未結算的 `PAID` Stripe 訂單收到全額 | 付款 `REFUNDED`、訂單 `REFUNDED`（狀態紀錄 1 筆）、webhook 稽核 1 筆 |
| R1 | 同 R0，看金額 | `refunded_amount`＝訂單金額 |
| P1 | `COMPLETED`、被 `PENDING_REVIEW` 結算單認領，全額 | 付款 `REFUNDED`、`refunded_amount`＝總額、結算單 `total_refunds`＝總額且淨額少總額、無調整單、訂單仍 `COMPLETED` |
| P1b | 同 P1，結算單 `PAID` | 結算單不動、產生 1 筆調整單（金額＝−總額） |
| P2 | `PAID`、被 `PENDING` 結算單認領，**部分**（1/3） | 付款 `PARTIALLY_REFUNDED`、`refunded_amount`＝1/3、訂單仍 `PAID`（沒有 `REFUNDED` 狀態紀錄）、結算單只扣 1/3 |
| P3 | 先 1/3 再全額（兩個事件） | 最終 `REFUNDED`、`refunded_amount`＝總額、結算單合計只扣總額、訂單 `REFUNDED` |
| P4 | 亂序：先全額、後到的舊事件（累計 1/3） | 舊事件不動任何東西；結算單只扣總額一次 |
| P5 | 重送：同事件 id 兩次、不同 id 同累計各一次 | 結算單只扣一次 |
| A1（對照） | API 全額退款完成後，webhook 重述同一筆 | 結算單只扣一次、沒有多餘的狀態紀錄 |
| A2 | API 部分退款（100）完成後 webhook（累計 100） | 付款維持 `PARTIALLY_REFUNDED`、`refunded_amount`＝100、訂單仍 `PAID`、結算單只扣 100 |
| A3 | `DEF-452` 情境 A：自動退款因調整單失敗而回滾（Stripe 已退）→ 資料修好 → webhook 到達 → 排程再跑 | 訂單 `REFUNDED`、結算單被扣、**Stripe 只被呼叫 1 次**（排程不再重複退款） |
| B1 | 訂房 Stripe 付款被 `PENDING` 結算單認領，全額 | 付款 `REFUNDED`、`refunded_amount`＝總額、結算單被扣 |
| B2 | 訂房部分（1/3）、結算單 `PAID` | 付款 `PARTIALLY_REFUNDED`、調整單 1 筆（−1/3） |
| E1 | 付款仍是 `PROCESSING`（成功事件還沒處理）收到 `charge.refunded` | 不變（記警告） |
| E2 | `amount_refunded` 缺漏／非數字／負數 | 不變 |
| E3（對照） | `payment_intent` 找不到付款 | 不丟例外、不變、事件仍記為已處理 |
| C1 | 兩條執行緒同時送累計 1/3 與累計全額（重複 8 輪） | 最終 `REFUNDED`、`refunded_amount`＝總額、結算單合計只扣總額、`REFUNDED` 狀態紀錄恰 1 筆 |
| C2 | API 部分退款的交易還沒提交時，webhook（累計＝該次金額）同時到達 | webhook 在 API 提交前**被擋住**（沒完成）；提交後它看到新的累計、不再扣；付款維持 `PARTIALLY_REFUNDED`、結算單只扣一次 |
| X1 | webhook 記帳途中失敗（結算單金額溢位）→ 資料修好 → 重送**同一個事件** | 第一次丟例外且什麼都沒記（付款仍 `SUCCESS`、`refunded_amount` 0、事件沒被標為已處理）；重送後記帳恰一次 |

### 3.3 對未修改程式碼的預測（跑之前登記）

- **綠**：R0、A1、E3（共 3 個對照，證明固件本身可用：真實 Stripe 付款流程、結算單認領、API 退款、未知付款的處理）。
- **紅**：其餘 16 個。理由分兩類：(a) 舊碼不記 `refunded_amount`、不呼叫調整單（R1、P1、P1b、P3、P4、P5、A3、B1、B2、C1、X1）；(b) 舊碼把任何 `charge.refunded` 都當成全額、與付款目前狀態無關（P2：整筆 `REFUNDED`＋訂單 `REFUNDED`；A2：API 部分退款後 webhook 把 `PARTIALLY_REFUNDED` 翻成 `REFUNDED`；E1：`PROCESSING` 被翻成 `REFUNDED`；E2：缺漏金額也被翻成 `REFUNDED`；C2：同 A2，且舊碼在 API 提交前也會被擋住再翻）。
- **預測落空的標準**：任何一個「綠」變紅，代表固件有問題，要先修固件；任何一個「紅」變綠，代表我對舊碼的讀法有誤，要回頭查。

## 4. 實測與發現

### 4.1 修改前的基準（未修改的生產碼；`mvn -o test-compile failsafe:integration-test -Dit.test=StripeRefundWebhookAccountingRealStackIntegrationTest`）

`Tests run: 19, Failures: 16, Errors: 0, Skipped: 0`（137.9 秒）。**綠燈集合恰好是預測的三個對照（R0、A1、E3），紅燈是其餘 16 個，預測全部吻合**——對照組全綠證明固件本身可用（真實的 Stripe 付款流程、結算單認領、API 退款、未知付款的處理都走得通）。

紅燈的原因逐一讀了失敗訊息（不是只看紅綠），分兩類，與 §3.3 的預測相同：

| 類別 | 案例 | 失敗訊息（節錄） |
|---|---|---|
| (a) 不記 `refunded_amount`、不扣結算單、不建調整單 | R1、P1、P1b、P3、P4、P5、A3、B1、B2、C1 | `expected: 300.00 but was: 0.00`（`refunded_amount`）；`expected: 8700.00 but was: 9000.00`（結算單淨額）；`expected: 1 but was: 0`（調整單）；B1 訂房 `3000.00 vs 0.00` |
| (a') 記帳失敗不丟例外 | X1 | `記帳失敗必須讓整個事件處理失敗，而不是吞掉 — Expecting actual not to be null`（舊碼根本不碰結算單，所以不會失敗） |
| (b) 把任何 `charge.refunded` 都當成全額、與付款目前狀態無關 | P2、A2、C2、E1、E2 | P2：`PARTIALLY_REFUNDED` 實得 `REFUNDED`、訂單 `PAID` 實得 `REFUNDED`；A2／C2：同樣；E1：`PROCESSING` 實得 `REFUNDED`；E2：`SUCCESS` 實得 `REFUNDED`（金額欄位缺漏／非數字／負數／帶小數四種都被當成全額） |

### 4.2 比追蹤表登記的更廣：連「本系統 API 發起的部分退款」也會被 webhook 翻成整筆退款（新發現，A2／C2）

Sprint 330 的 P2 只量到「Stripe 後台只退 1/3」被當成全額。**A2 量到的是正常路徑**：買家／管理員透過本系統的 `refundOrderPayment` 做部分退款（付款 `PARTIALLY_REFUNDED`、訂單 `PAID`、結算單扣那一筆），Stripe 在退款成功後送來的 `charge.refunded`（Stripe 文件：部分退款也會觸發）讓舊碼的 `markRefundedIfNotAlready`（`WHERE status <> 'REFUNDED'`）把付款翻成 `REFUNDED`、並把訂單整筆轉 `REFUNDED`，還多留一筆 webhook 稽核。**也就是說：只要啟用真實 Stripe，任何一次經本系統發起的部分退款，之後都會被自己的 webhook 弄成整筆退款。**

> 限定（誠實）：這是用 Mockito 取代的 Stripe 閘道＋手寫的 `charge.refunded` JSON 量到的，事件形狀是依 Stripe 文件（部分退款也送 `charge.refunded`、`amount_refunded` 是累計值）。**修復從未對真實 Stripe 驗證**（沒有測試金鑰環境）。

C2 的第一條斷言（「webhook 在 API 提交前不該完成」）在舊碼就通過：舊碼的 `UPDATE … WHERE status <> 'REFUNDED'` 同樣會等 API 交易持有的列鎖（預測 §3.3 已寫「舊碼也會被擋住再翻」）；舊碼紅在等完之後的結果（`REFUNDED`），不是紅在有沒有被擋。


### 4.3 一次性探針：webhook 記帳失敗時，controller 回什麼、事件有沒有被記下（`DEF-506`）

**動機**：修法讓 webhook 多了一個會失敗的步驟（調整單），而 `StripeWebhookController` 對 `RuntimeException` 的處理是「記錄後仍回 2xx」（§2 讀碼）。X1 證明**服務層**失敗會整個回滾、事件沒被標成已處理；但真正的 HTTP 回應是 controller 決定的——讀碼說是 200，沒有量過。

**預測（跑之前登記）**：把 X1 的情境（結算單金額溢位使記帳失敗）改經真實的 `StripeWebhookController.handleStripeWebhook` 送進去：①回 **200**（body 是 `Webhook received`）；②付款仍 `SUCCESS`、`refunded_amount` 0.00、訂單仍 `PAID`；③`processed_stripe_events` 沒有這個事件（所以**如果** Stripe 重送，仍能收斂）；④但 Stripe 看到的是 2xx，**不會重送**——失敗的唯一痕跡是一行 `Failed to process Stripe webhook event (returning 2xx to avoid retry storm)` 的 ERROR 日誌。若 ① 不是 200，代表我讀碼有誤。

**做法**：暫時在新測試類別裡加一個方法（探針，不入庫；類別是新檔、沒有 git 版本可還原，所以先備份並記 SHA-256，跑完用備份還原再核對）。

**結果：四項預測全部吻合。**

```text
ERROR … StripeWebhookController : Failed to process Stripe webhook event (returning 2xx to avoid retry storm): JDBC exception executing SQL [UPDATE settlement_statements …
PROBE> status=200 OK body=ApiResponse(success=true, code=null, message=Webhook received, data=OK, errors=null, …)
PROBE> payment=SUCCESS refunded=0.00 order=PAID processedEvent=0 statementTotalRefunds=999999999999.00
```

也就是說：**記帳失敗時 Stripe 收到 200、不會重送；退款在 Stripe 已經發生、本地什麼都沒記，唯一的痕跡是一行 ERROR 日誌。** 事件沒被標成已處理，所以「有人手動從 Stripe Dashboard 重送該事件」仍能收斂（X1 已證明）。這不是 `DEF-453` 的範圍（那個吞例外的政策涵蓋所有 webhook 事件類型，包含付款成功），也牽涉營運面的取捨（回 5xx 會讓 Stripe 重送最多數天、持續失敗的端點可能被 Stripe 停用），所以只登記為 `DEF-506`、不改 controller。

探針方法原文（暫時加在 `StripeRefundWebhookAccountingRealStackIntegrationTest` 裡，已移除；`probeController` 是 `@Autowired StripeWebhookController`）：

```java
@Test
void probeDef506_failingAccountingThroughTheController() {
    String pi = newPi();
    UUID orderId = stripePaidOrder(pi);
    BigDecimal total = orderTotal(orderId);
    UUID statementId = seedStatement("PENDING_REVIEW", NEAR_OVERFLOW);   // 讓記帳必然失敗（同 X1）
    settleOrder(orderId, statementId);
    String eventId = "evt_" + UUID.randomUUID();
    String event = chargeRefunded(eventId, pi, cents(total), cents(total));
    TenantContext.clear();
    SecurityContextHolder.clearContext();
    ResponseEntity<?> response = probeController.handleStripeWebhook(event, null);   // 簽章：測試環境 secret 為空 → 跳過驗證
    System.out.println("PROBE> status=" + response.getStatusCode() + " body=" + response.getBody());
    System.out.println("PROBE> payment=" + paymentStatus(orderId) + " refunded=" + paymentRefundedAmount(orderId)
            + " order=" + orderStatus(orderId) + " processedEvent=" + processedEventCount(eventId)
            + " statementTotalRefunds=" + statementRow(statementId).get("total_refunds"));
}
```

## 5. 實作

### 5.1 改了什麼

| 檔案 | 變更 |
|---|---|
| `domain/repository/PaymentRepository.java` | 新增 `findByStripePaymentIntentIdForUpdate`（`@Lock(PESSIMISTIC_WRITE)`，寫法比照 `OrderRepository.findByIdForUpdate`）；**移除** `markRefundedIfNotAlready`——修法後沒有任何呼叫者，而它的語意（任何 `charge.refunded` 都標成 `REFUNDED`）正是缺陷本身 |
| `core/payment/PaymentStateService.java` | `markStripeRefunded(pi, refundId)` → `markStripeRefunded(pi, refundId, refundedTotal)`；邏輯見 §3.1。為了通過 Checkstyle（第一版 `NPathComplexity` 768，上限 200）拆成 `lockRecordablePayment`、`cappedAtPaymentAmount`、`isRefundRecordable`、`recordWebhookRefundOnOrder`、`recordWebhookRefundOnBooking` |
| `core/payment/PaymentWebhookService.java` | `charge.refunded` 讀 `amount_refunded`、換算成主單位（÷100，沿用 `StripePaymentGateway` 全程 ×100 的慣例）；缺漏、非整數、負數、超出 `long` 一律傳 `null`（不知道退了多少，就不能記帳） |
| `core/analytics/AnalyticsService.java` | **只改註解**：原本說「webhook 路徑不寫 `refundedAmount`」，現在不實；程式碼不動（舊資料仍可能是 `REFUNDED`／`0.00`，所以全額退款仍以付款金額計） |
| 測試 | 新增真實棧類別 `StripeRefundWebhookAccountingRealStackIntegrationTest`（19 案例）；`PaymentStateServiceTest`、`PaymentStateServiceStripeTest`、`PaymentWebhookServiceTest` 中編碼舊行為的案例改寫（舊案例例如「已是 REFUNDED → 冪等」「沒有金額也整筆 REFUNDED」本身就是缺陷的形狀）；`AnalyticsServiceTest` 只改一句斷言訊息；`RefundSettlementAdjustmentFailureRealStackIntegrationTest` 只改類別註解（原本寫「DEF-453 未修」） |
| 文件 | `STRIPE_PRODUCTION_CHECKLIST.md` 新增兩個人工確認項目；`DEFERRED_ITEMS_TRACKER.md`；`RELEASE_TRACKER.md` |

### 5.2 為什麼這樣設計

1. **以 Stripe 端的累計值當真相，只記差額。** `charge.refunded` 的 `amount_refunded` 是累計值（部分退款也會觸發這個事件）。對累計值取差額，「事件重送」「本系統 API 已先記帳」「亂序到達的舊事件」三種情況都落在差額 ≤ 0，自然什麼都不做——不需要事件順序的保證，也不需要另外存「這個事件處理過了嗎」（`processed_stripe_events` 的去重仍在，是第一層）。
2. **用悲觀鎖，不用 compare-and-swap 重試。** 差額必須從「鎖住之後」讀到的最新值算。CAS 失敗後要重讀、重試、決定上限與耗盡時怎麼辦；鎖的範圍只是單一付款列、交易很短，repo 已有 `findByIdForUpdate` 的前例（Rule 11）。API 退款那一端維持 CAS 不動。
3. **先鎖付款列，之後才碰結算單列。** API 退款在 compare-and-swap 時就持有付款列的鎖直到交易結束（Stripe 呼叫在這段時間內），結算單是它**之後**才更新的。webhook 若不先鎖付款列，會讀到 API 尚未提交的舊值、把同一筆退款再記一次、再扣一次結算單——**這是實測到的失敗**（§6.2：拿掉鎖的兩個突變 M12／M13 下，C2 的結算單是 200 而不是 100）。我原本也推測可能**死結**（webhook 先拿到結算單的鎖、API 隨後也要更新結算單、webhook 提交時的付款列更新又在等 API），§6.1 第 4 點把它登記成「死結或重複扣款」；**實測沒有死結**（日誌 0 次 `deadlock detected`），M13 日誌的 Hibernate SQL 時間戳顯示 webhook 先執行 `update payments`（卡約 1.5 秒＝等 API 提交）、之後才 `UPDATE settlement_statements`——原因（**推論**，讀 `SettlementStatementRepository.applyRefundDeduction` 的 `@Modifying(flushAutomatically = true)`）是它執行前會先把髒的付款實體 flush 出去，所以 webhook 在碰結算單之前就已經卡在付款列上。因此「鎖的必要性」是被量到的（重複扣款），「避免死結」不是。
4. **訂單只在「全額、且 `canRefund`」時才轉 `REFUNDED`；已出貨的訂單狀態不動。** 沿用既有規則（追蹤表建議欄的原話）。結算單一律扣差額——已出貨訂單的錢確實退給了買家。
5. **訂房只記帳、狀態不動。** 不知道這筆退款的業務意圖（要不要取消訂房、要不要釋放日曆是產品決定），所以只把錢記對。
6. **記帳失敗不吞例外。** 與 Sprint 327（`DEF-452`）對退款路徑的結論相同：吞掉例外救不了 rollback-only 的交易，只會讓真正的失敗原因消失。但 **controller 仍會把例外吞掉並回 2xx**（§2，登記為 `DEF-506`），所以「事件沒被標成已處理、Stripe 重送時會收斂」目前只在**有人重送**（Stripe Dashboard 手動重送、或 controller 改成回 5xx）時成立。
7. **付款必須是已收款狀態（`SUCCESS`／`PARTIALLY_REFUNDED`／`REFUNDED`）才記退款。** `PROCESSING`／`PENDING`／`FAILED` 的付款沒有款項可退；舊碼會把它們標成 `REFUNDED`。`REFUNDED` 也放行是刻意的：舊碼留下的 `REFUNDED`／`0.00` 資料列，在該事件被重送時會被補記（見 §8.2 第 6 點）。
8. **累計值超過付款金額時以付款金額為上限**（記錯誤日誌）：不會多扣結算單。
9. **稽核動作名稱 `STRIPE_PAYMENT_REFUNDED_WEBHOOK` 不變**，舊值／新值改成 `refunded=…`／`refunded=…,status=…`（與 API 退款的稽核同格式；舊碼是 `null`／`REFUNDED`）。只有「真的記了帳」才留一筆稽核；no-op 不留。

## 6. 突變驗證

### 6.1 預先登記（跑之前寫下；與驅動腳本 `mutate_s342.py` 內的 `must` 欄位相同）

驅動：備份 → 套用（每個突變點在檔案裡必須恰好出現一次，乾跑已驗證）→ 刪被改檔案的 `.class` 逼 Maven 重編 → 同一次 `mvn -o test-compile surefire:test … failsafe:integration-test …` 跑**單元（`PaymentStateServiceTest`、`PaymentStateServiceStripeTest`、`PaymentWebhookServiceTest`）＋真實棧（`StripeRefundWebhookAccountingRealStackIntegrationTest`）**→ 還原並核對 SHA-256 → 從 surefire／failsafe 的 XML 取失敗集合，與 `must` 比對。判決：`KILLED(exactly must)`＝失敗集合恰好等於登記；`+extra`＝多紅（要看是不是合理的連帶）；`MISSING-must`＝登記要紅的沒紅；`SURVIVED`＝沒有任何案例變紅。

**登記時就預測的特殊情況**（不是看到結果才解釋）：

1. **M1b（訂房結算調整傳累計值）原本沒有任何案例會抓到**——訂房案例的「已記金額」都是 0，差額恰好等於累計值。這是我在設計突變時推演出來的測試缺口，**先補了單元案例 UT-PAY-STATE-034b**（已記 1000、Stripe 說累計 3000，調整金額必須是 2000）才登記。
2. **M11（吞掉結算調整的例外）在真實棧預期存活**：例外一穿出調整服務的交易代理，共用交易就被標成 rollback-only（Sprint 327 實測）；吞掉例外救不了交易，只會讓提交時改丟別的例外，X1 仍然看到「丟例外＋什麼都沒記」。只有單元（mock 沒有交易代理）殺得到——登記為 `[UT settlementAdjustmentFailure_propagates]`，X1 不在其中。
3. **M25（拿掉負數檢查）外部等價**：負數的差額本來就是 no-op；只有單元的 `verify(never()).findByStripePaymentIntentIdForUpdate` 殺得到（Sprint 245 同款：防禦縱深的前置檢查）。
4. **M12／M13（不鎖付款）的真實棧紅燈機制是「死結或重複扣款」，不是單純的狀態不對**：沒有鎖時 webhook 讀到 API 尚未提交的舊值、先去更新結算單（API 這時還沒碰結算單），API 隨後也要更新結算單、webhook 的付款列更新又在等 API 的鎖——兩邊互等（PostgreSQL 偵測死結後中止其中一個），或是兩邊各扣一次。登記只寫 C2 紅，不寫是哪一種。
5. **M8（拿掉 `canRefund` 閘門）P1b 不會紅**：P1b 沒斷言訂單狀態，所以 `must` 只登記有斷言的 UT-030b 與 P1。
6. **機率性的案例不登記**：C1（兩條執行緒各送一個事件）在 M1／M2／M21 下是否紅取決於哪個事件先拿到鎖，所以不放進 `must`；若紅了會出現在 `extra`。

| 編號 | 突變 | 檔案 | 預期變紅（must） |
|---|---|---|---|
| M1 | order settlement receives the cumulative total instead of the difference | `PaymentStateService.java` | UT cumulativeRefund_settlementAdjustedByTheDifferenceOnly；IT p3_cumulativeRefunds_accountOnlyTheDifference |
| M1b | booking settlement receives the cumulative total instead of the difference | `PaymentStateService.java` | UT bookingPayment_cumulativeRefund_settlementAdjustedByTheDifferenceOnly |
| M2 | remove the 'difference <= 0 means nothing to record' guard | `PaymentStateService.java` | UT noNewRefund_changesNothing；IT a1_control_apiRefundThenWebhook_isANoOp；IT a2_apiPartialRefundThenWebhook_doesNotTurnItIntoAFullRefund；IT p4_outOfOrderEvents_anOlderCumulativeNeverMovesTheRefundBackwards；IT p5_redelivery_neverDeductsTwice；IT c2_webhookArrivingWhileTheApiRefundIsInFlight_waitsAndThenFindsNothingToDo |
| M3 | fully refunded needs strictly more than the payment amount | `PaymentStateService.java` | UT fullRefund_recordsPaymentOrderSettlementAndAudit；UT fullRefundOfACompletedOrder_settlementStillAdjusted_orderUntouched；UT refundingOrder_webhookCompletesRefund；IT r0_control_fullRefundOfAnUnsettledPaidOrder_keepsExistingStateTransitions；IT p1_fullRefundOfACompletedOrder_deductsFromAPendingStatement；IT b1_fullRefundOfABookingPayment_deductsFromTheStatement |
| M4 | every refund event is treated as a full refund (the pre-fix behaviour) | `PaymentStateService.java` | UT partialRefund_isNotTreatedAsAFullRefund；UT bookingPayment_adjustsTheBookingSettlement；IT p2_partialRefund_isNotTreatedAsAFullRefund；IT b2_partialRefundOfABookingPayment_createsAnAdjustment |
| M5 | payment status is always REFUNDED (order logic intact) | `PaymentStateService.java` | UT partialRefund_isNotTreatedAsAFullRefund；UT bookingPayment_adjustsTheBookingSettlement；IT p2_partialRefund_isNotTreatedAsAFullRefund；IT b2_partialRefundOfABookingPayment_createsAnAdjustment |
| M6 | refunded_amount is not advanced | `PaymentStateService.java` | UT fullRefund_recordsPaymentOrderSettlementAndAudit；UT partialRefund_isNotTreatedAsAFullRefund；IT r1_fullRefundOfAnUnsettledOrder_recordsTheRefundedAmount；IT p1_fullRefundOfACompletedOrder_deductsFromAPendingStatement；IT p2_partialRefund_isNotTreatedAsAFullRefund |
| M7 | a partial refund also flips the order (fullyRefunded condition removed) | `PaymentStateService.java` | UT partialRefund_isNotTreatedAsAFullRefund；IT p2_partialRefund_isNotTreatedAsAFullRefund |
| M8 | canRefund gate removed (a shipped order flips to REFUNDED) | `PaymentStateService.java` | UT fullRefundOfACompletedOrder_settlementStillAdjusted_orderUntouched；IT p1_fullRefundOfACompletedOrder_deductsFromAPendingStatement |
| M9 | order settlement adjustment not called | `PaymentStateService.java` | UT fullRefund_recordsPaymentOrderSettlementAndAudit；UT fullRefundOfACompletedOrder_settlementStillAdjusted_orderUntouched；UT partialRefund_isNotTreatedAsAFullRefund；UT cumulativeRefund_settlementAdjustedByTheDifferenceOnly；IT p1_fullRefundOfACompletedOrder_deductsFromAPendingStatement；IT p1b_fullRefundOfACompletedOrder_createsAnAdjustmentForAPaidStatement；IT p2_partialRefund_isNotTreatedAsAFullRefund；IT p3_cumulativeRefunds_accountOnlyTheDifference；IT a3_autoRefundRolledBack_thenWebhookRepairsTheAccounting |
| M10 | booking settlement adjustment not called | `PaymentStateService.java` | UT bookingPayment_adjustsTheBookingSettlement；UT bookingPayment_cumulativeRefund_settlementAdjustedByTheDifferenceOnly；IT b1_fullRefundOfABookingPayment_deductsFromTheStatement；IT b2_partialRefundOfABookingPayment_createsAnAdjustment |
| M11 | settlement adjustment failure swallowed (real stack equivalent: the failed transaction still rolls back) | `PaymentStateService.java` | UT settlementAdjustmentFailure_propagates |
| M12 | service looks the payment up without the lock (plain query) | `PaymentStateService.java` | UT fullRefund_recordsPaymentOrderSettlementAndAudit；IT c2_webhookArrivingWhileTheApiRefundIsInFlight_waitsAndThenFindsNothingToDo |
| M13 | repository: @Lock removed (the 'ForUpdate' query becomes a plain select) | `PaymentRepository.java` | IT c2_webhookArrivingWhileTheApiRefundIsInFlight_waitsAndThenFindsNothingToDo |
| M14 | payment status gate removed (a payment never confirmed paid can record a refund) | `PaymentStateService.java` | UT paymentNeverConfirmedPaid_recordsNothing；IT e1_refundEventForAPaymentThatWasNeverConfirmedPaid_changesNothing |
| M15 | no cap at the payment amount | `PaymentStateService.java` | UT refundedTotalAbovePaymentAmount_isCappedAtThePaymentAmount |
| M16a | parse: negative amount_refunded accepted | `PaymentWebhookService.java` | UT-WH chargeRefunded_unusableAmount_passesNull(String)[3] |
| M16b | parse: forgot to convert minor units to major units | `PaymentWebhookService.java` | UT-WH chargeRefunded_marksRefunded；IT p2_partialRefund_isNotTreatedAsAFullRefund |
| M16c | parse: non-integral numbers (100.5) accepted | `PaymentWebhookService.java` | UT-WH chargeRefunded_unusableAmount_passesNull(String)[2]；IT e2_unusableAmountRefunded_changesNothing |
| M17 | audit row carries no tenant | `PaymentStateService.java` | UT fullRefund_recordsPaymentOrderSettlementAndAudit；UT bookingPayment_adjustsTheBookingSettlement |
| M19 | refund id overwritten with null when the event has none | `PaymentStateService.java` | UT nullRefundId_keepsExistingRefundId |
| M21 | difference computed against zero instead of the recorded amount | `PaymentStateService.java` | UT noNewRefund_changesNothing；UT cumulativeRefund_settlementAdjustedByTheDifferenceOnly；IT a1_control_apiRefundThenWebhook_isANoOp；IT a2_apiPartialRefundThenWebhook_doesNotTurnItIntoAFullRefund；IT p3_cumulativeRefunds_accountOnlyTheDifference；IT p4_outOfOrderEvents_anOlderCumulativeNeverMovesTheRefundBackwards；IT p5_redelivery_neverDeductsTwice；IT c2_webhookArrivingWhileTheApiRefundIsInFlight_waitsAndThenFindsNothingToDo |
| M25 | negative amount check removed (externally equivalent: a negative difference is a no-op anyway) | `PaymentStateService.java` | UT negativeAmount_changesNothing |
| M26 | null amount check inverted (null reaches the lookup) | `PaymentStateService.java` | UT nullAmount_changesNothing；IT e2_unusableAmountRefunded_changesNothing |


### 6.2 結果（驅動輸出原文在 scratchpad，不入庫；以下數字由驅動輸出檔以腳本取出）

基準 Z0：**131 案例（單元 3 類別 112＋真實棧 19）全綠、0 紅**。24 個突變（每個約 1～3 分鐘，含 Spring 啟動；全程 56.6 分鐘）：**14 個「恰好吻合登記」、10 個「吻合登記＋多紅」，0 個 `MISSING-must`、0 個倖存、0 個無效**。還原後用 SHA-256（三個檔案與突變前備份逐一相同）、`cmp`、殘留字串 `grep`（`mutated`、`&& false`、`if (false &&`）三重確認沒有任何突變留在工作樹。

| 編號 | 突變 | 紅燈數 | 判決 | 備註 |
|---|---|---|---|---|
| M1 | order settlement receives the cumulative total instead of the difference | 3 | 擊殺（吻合登記＋多紅） | 多紅 1：C1。登記時標為機率性（取決於兩個事件哪個先拿到鎖），這次紅了。 |
| M1b | booking settlement receives the cumulative total instead of the difference | 1 | 擊殺（恰好吻合登記） | 只有新補的 UT-PAY-STATE-034b 擊殺——登記第 1 點：這個突變在補案例之前沒有任何案例會抓到。 |
| M2 | remove the 'difference <= 0 means nothing to record' guard | 10 | 擊殺（吻合登記＋多紅） | 多紅 1：C1（機率性）。擊殺面：4 個參數列、A1、A2、P4、P5、C2。 |
| M3 | fully refunded needs strictly more than the payment amount | 18 | 擊殺（吻合登記＋多紅） | 多紅 12：所有斷言「全額」結果（付款 REFUNDED、訂單轉換、結算扣款、累計額）的案例都連帶紅，是「所有成功案例都受影響」型突變的預期多紅。 |
| M4 | every refund event is treated as a full refund (the pre-fix behaviour) | 4 | 擊殺（恰好吻合登記） | 恰好吻合。 |
| M5 | payment status is always REFUNDED (order logic intact) | 4 | 擊殺（恰好吻合登記） | 恰好吻合。 |
| M6 | refunded_amount is not advanced | 20 | 擊殺（吻合登記＋多紅） | 多紅 15：所有斷言 `refunded_amount` 的案例。 |
| M7 | a partial refund also flips the order (fullyRefunded condition removed) | 2 | 擊殺（恰好吻合登記） | 恰好吻合。 |
| M8 | canRefund gate removed (a shipped order flips to REFUNDED) | 3 | 擊殺（吻合登記＋多紅） | 多紅 1（未登記）：`orderAlreadyRefunded_…_noDuplicateTransition`——拿掉 `canRefund` 後，已經 `REFUNDED` 的訂單會被再轉一次、多一筆狀態紀錄，這個案例本來就是守這個。P1b 如登記所預測**沒有**紅（它沒斷言訂單狀態）。 |
| M9 | order settlement adjustment not called | 15 | 擊殺（吻合登記＋多紅） | 多紅 6：所有斷言結算調整呼叫的案例（含 `settlementAdjustmentFailure_propagates`：呼叫被拿掉，例外自然不會出現）。 |
| M10 | booking settlement adjustment not called | 4 | 擊殺（恰好吻合登記） | 恰好吻合。 |
| M11 | settlement adjustment failure swallowed (real stack equivalent: the failed transaction still rolls back) | 1 | 擊殺（恰好吻合登記） | 只有單元 `settlementAdjustmentFailure_propagates` 擊殺；**X1 如登記所預測沒有紅**（真實棧上吞掉例外仍然回滾）。 |
| M12 | service looks the payment up without the lock (plain query) | 15 | 擊殺（吻合登記＋多紅） | 多紅 13：其中 12 個單元案例是 mock 副作用（單元 stub 的是鎖定版查詢，突變改呼叫另一個方法 → 查不到付款），不是行為擊殺；真實棧的 C1、C2 才是行為擊殺（C2：結算單 200 而不是 100＝重複扣款）。 |
| M13 | repository: @Lock removed (the 'ForUpdate' query becomes a plain select) | 2 | 擊殺（吻合登記＋多紅） | C2 擊殺（結算單 200 而不是 100）；C1 多紅（兩個 webhook 併發）。單元層因為 mock repository 看不到這個突變，如預期。 |
| M14 | payment status gate removed (a payment never confirmed paid can record a refund) | 4 | 擊殺（恰好吻合登記） | 恰好吻合。 |
| M15 | no cap at the payment amount | 1 | 擊殺（恰好吻合登記） | 恰好吻合；只有單元殺得到（真實棧不會有 Stripe 回報超過付款金額）。 |
| M16a | parse: negative amount_refunded accepted | 1 | 擊殺（恰好吻合登記） | 恰好吻合；只有單元殺得到（負數在狀態服務那端本來就被擋）。 |
| M16b | parse: forgot to convert minor units to major units | 8 | 擊殺（吻合登記＋多紅） | 多紅 6：忘了 ÷100 後，金額被截成付款金額（等於全額），所有部分退款的案例都紅。 |
| M16c | parse: non-integral numbers (100.5) accepted | 2 | 擊殺（恰好吻合登記） | 恰好吻合。 |
| M17 | audit row carries no tenant | 2 | 擊殺（恰好吻合登記） | 恰好吻合。 |
| M19 | refund id overwritten with null when the event has none | 1 | 擊殺（恰好吻合登記） | 恰好吻合。 |
| M21 | difference computed against zero instead of the recorded amount | 13 | 擊殺（吻合登記＋多紅） | 多紅 2：訂房累計案例（同一個差額也用在訂房入口）與 C1；前者我登記時漏列，不是異常。 |
| M25 | negative amount check removed (externally equivalent: a negative difference is a no-op anyway) | 1 | 擊殺（恰好吻合登記） | 恰好吻合；外部等價，只有單元的 `verify(never()).findByStripePaymentIntentIdForUpdate` 殺得到（登記第 3 點）。 |
| M26 | null amount check inverted (null reaches the lookup) | 2 | 擊殺（恰好吻合登記） | 恰好吻合。 |

**多紅的 10 組都查過原因，全是同一個突變的連帶，沒有一個是無關的失敗**（上表備註；M3、M6、M9、M12、M16b 的多紅清單最長，是「所有成功案例都受影響」型突變的預期）。登記的特殊預測對答案：

1. **M1b**：✅ 只有新補的 UT-PAY-STATE-034b 擊殺（補之前沒有任何案例會抓到，是設計突變時推演出的缺口）。
2. **M11**：✅ 真實棧存活（X1 沒紅），只有單元擊殺。
3. **M25**：✅ 外部等價，只有單元擊殺。
4. **M12／M13 的真實棧機制**：❌ **部分落空**。登記是「死結或重複扣款」，實測是**重複扣款**（C2 的結算單 `expected: 100.00 but was: 200.00`），日誌 0 次 `deadlock detected`。SQL 時間戳顯示 webhook 先 `update payments`（卡 1.5 秒）再 `UPDATE settlement_statements`，推論原因是 `applyRefundDeduction` 的 `@Modifying(flushAutomatically = true)`（§5.2 第 3 點，已據此更正設計理由的措辭）。
5. **M8 與 P1b**：✅ P1b 沒紅；多出一個我沒登記的案例（`orderAlreadyRefunded…`）也紅，它守的就是這個。
6. **C1 的機率性**：C1 在 M1、M2、M3、M6、M9、M12、M13、M21 共 8 個突變下都紅（我登記時當成「可能」）。這是**每個突變各跑一次**的觀察，不是「C1 一定會抓到」的保證——C1 靠的是兩個事件誰先拿到鎖的交錯，8 輪裡至少有一輪出現不利順序的機率很高，但沒有量化。

## 7. 後置驗證

### 7.1 本機完整驗證（突變驗證與探針都結束、原始碼還原核對之後；用全新的測試資料庫容器）

| 項目 | 預測（跑之前登記） | 結果 |
|---|---|---|
| 後端 `mvn -o clean verify`（單元＋整合＋Checkstyle＋PMD） | 整合 1055＋19＝**1074／0**；單元只能預測本輪新增 **+23** | **BUILD SUCCESS（20 分 10 秒）**：單元 **2567／0／0 跳過**、整合 **1074／0／0 跳過**（預測吻合）、Checkstyle（main、test）各 0 違規、PMD 通過；`clean` 之後主程式 388 個、測試碼 334 個原始檔都真的重編（日誌有 `Compiling 388 source files` 與 `Compiling 334 source files`） |
| 本輪的新增案例數 | +23 單元、+19 整合 | `markStripeRefunded` 單元案例 7→**23**（+16；用 `git show HEAD:` 數舊的 `@Test` 與驗證日誌核對）、`PaymentWebhookServiceTest` 10→**17**（+7）＝**+23**；整合新類別 **19** |
| 單元的絕對基準 | — | Sprint 341 沒有在本機跑完整單元套件、雲端單元 job 又是安靜模式（不印總數），所以**說不出 Sprint 341 結束時的單元總數**。由本輪實測 2567 扣掉 +23 推得 HEAD 為 2544（**推算，沒有量**；Sprint 340 本機實測是 2505，差 39 是 Sprint 341 的新增，沒有逐一對過） |
| 重點類別在全量 verify 裡的結果 | — | `StripeRefundWebhookAccountingRealStackIntegrationTest` 19／0、`RefundSettlementAdjustmentFailureRealStackIntegrationTest` 5／0（只改註解）、`PaymentStateServiceTest$MarkStripeRefundedAccounting` 23／0、`PaymentWebhookServiceTest` 17／0；日誌 0 次 `Connection refused` |
| Checkstyle（單獨先跑） | 0 | main、test 各 0 違規；**第一版 main 被擋**（`NPathComplexity` 768，§9 第 2 點），拆方法後通過 |

## 8. 行為變更與已知限制

### 8.1 行為變更

| 情境 | 之前 | 現在 | 影響誰 |
|---|---|---|---|
| Stripe 後台對**已出貨（`COMPLETED`）**訂單全額退款，訂單已被結算單認領 | 付款 `REFUNDED`、`refunded_amount` 0.00；訂單與結算單都不動 | 付款 `REFUNDED`、`refunded_amount`＝總額；結算單被扣（`PENDING`／`PENDING_REVIEW` 直接扣款，已核准／已撥款產生調整單）；**訂單仍 `COMPLETED`**（沿用 `canRefund` 規則，DEF-508） | 賣家的結算金額、對帳 |
| **部分**退款（Stripe 後台） | 付款整筆 `REFUNDED`、訂單整筆 `REFUNDED` | 付款 `PARTIALLY_REFUNDED`、`refunded_amount`＝累計值、訂單不動、結算單只扣差額 | 買家／賣家／訂單頁 |
| **本系統 API 發起的部分退款**之後，Stripe 送來的 `charge.refunded` | 付款與訂單被翻成整筆 `REFUNDED`（§4.2） | no-op（本地已記帳） | 啟用真實 Stripe 後的每一次部分退款 |
| 未被結算單認領的訂單收到全額退款 | `refunded_amount` 0.00 | 記全額（日後 `SettlementGenerator` 讀這個欄位，才不會多付給賣家） | 週結算 |
| 訂房的 Stripe 付款被後台退款 | 付款 `REFUNDED`、沒有任何結算調整 | 付款記帳＋訂房結算調整；**訂房狀態不動** | 賣家（訂房結算） |
| `amount_refunded` 缺漏／非整數／負數 | 被當成全額退款 | 忽略（警告日誌）；事件仍被標為已處理 | 異常事件 |
| 付款尚未確認收款（`PROCESSING`／`PENDING`／`FAILED`）收到退款事件 | 被標成 `REFUNDED` | 忽略（警告日誌） | 事件亂序（罕見） |
| Stripe 說的累計值超過付款金額 | （n/a） | 以付款金額為上限（錯誤日誌） | 異常資料 |
| 記帳（含結算調整）失敗 | （n/a：舊碼不碰結算單） | 例外往外丟、整個交易回滾、事件沒被標成已處理；**但 controller 仍回 200，Stripe 不會重送**（§4.3，`DEF-506`） | 營運 |
| 稽核 `STRIPE_PAYMENT_REFUNDED_WEBHOOK` | 一筆（舊值 `null`、新值 `REFUNDED`） | 每次**真的記帳**一筆（舊值 `refunded=…`、新值 `refunded=…,status=…`）；no-op 不留 | 稽核查詢 |
| 對外 API | — | 沒有變動（`/v2/payments/webhook/stripe` 的簽章驗證與回應不變） | — |

### 8.2 已知限制（誠實列出）

1. **從未對真實 Stripe 驗證。** 事件形狀依 Stripe 文件（部分退款也送 `charge.refunded`、`amount_refunded` 是累計值）；閘道在所有測試裡都是 mock。已在 `STRIPE_PRODUCTION_CHECKLIST.md` 加兩項需要人工在 Dashboard 確認的項目。
2. **webhook 記帳失敗時 Stripe 不會重送**（`DEF-506`，§4.3 實測）：失敗的唯一痕跡是 ERROR 日誌。這個政策涵蓋所有事件類型（包含付款成功），不是這輪新增的，但這輪讓 webhook 多了一個會失敗的步驟。
3. **已出貨訂單與訂房的狀態不動**（`DEF-508`）：付款 `REFUNDED`、結算單已扣，但訂單仍 `COMPLETED`、訂房仍 `PAID`。營收報表不受影響（`AnalyticsService` 的淨營收＝營收−退款，退款依付款狀態取全額，單元測試涵蓋），只有狀態標籤與金額不一致。
4. **Mock 退款端點 `POST /v2/payments/refund`（P3）維持現狀**（`DEF-507`）：成功時仍不記 `refunded_amount`、不扣結算單。
5. **`payments.stripe_refund_id` 仍只存最後一次退款的 id**（既有限制，S205 文件已載）。
6. **舊資料不回填。** 舊碼留下的 `REFUNDED`／`refunded_amount 0.00` 資料列，只有在它的 `charge.refunded` 事件被**重送**時才會被補記（`REFUNDED` 狀態被放行，差額＝全額 → 記帳並扣結算單）。這既是修復也是風險：若營運上曾手動調整過那張結算單，重送會再扣一次。目前沒有生產資料（Stripe 從未在生產使用），所以是理論風險。
7. **金額換算沿用閘道的 ×100 慣例**：對零小數幣別（JPY 等）不正確——既有限制，這輪沒有擴大也沒有修；`payments.currency` 預設 TWD。
8. **沒有設定鎖逾時**（沿用 repo 既有悲觀鎖的寫法）：若某個 API 退款交易卡住，同一筆付款的 webhook 會一直等到它結束。理論上的限制，沒有量過。
9. **真實棧 C1／C2 靠執行緒交錯與 1.5 秒的等待窗**：C2 斷言「webhook 在 API 提交前不該完成」，機器再慢也只會讓等待更久、判準不變；C1 是兩條執行緒搶鎖、重複 8 輪，**不是**「保證每次都排出不利順序」。突變驗證顯示 C1 在 8 個突變下都紅（§6.2），但那是每個突變各跑一次的觀察。
10. **這輪沒有跑 E2E 守門與前端測試**：沒有前端變更；後端由全量 `mvn verify`、24 個突變與雲端 CI 覆蓋。

## 9. 失誤與修正（誠實列出）

1. **突變點不唯一，乾跑抓到**：`boolean fullyRefunded = …` 與 `payment.setRefundedAmount(newRefundedAmount);` 在 `refundOrderPaymentCore`、訂房退款方法裡也有一份，第一版規格會突變到不該動的地方。乾跑（只套用、不執行、立刻還原）的驗證「每個 `old` 恰好出現一次」就是為此，補上相鄰一行後通過。
2. **Checkstyle 擋下第一版**：`markStripeRefunded` 把所有檢查寫在同一個方法，`NPathComplexity` 768（上限 200）。commit 前自己跑 Checkstyle 才發現；拆成五個小方法，0 違規。
3. **兩次把沒有量到的鎖順序寫成理由**：先在 `PaymentRepository` 註解寫「兩邊拿鎖的順序一致（先付款列、再結算單列），不會死結」——我只核對過「付款列先於結算單列」，改成只陳述核對過的；之後又在計畫書 §5.2 把「避免死結」當成設計理由，突變驗證的真實失敗是**重複扣款**、日誌 0 次死結，據 SQL 時間戳更正（§5.2 第 3 點）。
4. **引用的行號不準**：計畫書 §2 把 controller 的 `catch` 寫成第 90～96 行，實際是第 98～104 行；寫完回頭對來源時更正。
5. **「該先想到的測試缺口」晚了一步**：M1b（訂房結算調整傳累計值）沒有任何案例會抓到，是設計突變時才推演出來的；寫訂房案例時就該想到「已記金額非零」。補了 UT-PAY-STATE-034b 才登記。
6. **登記時只量了一半**：Sprint 327／330 登記與補量測 `DEF-453` 時，探針只涵蓋「Stripe 後台退款」，沒有「先走本系統 API 退款、再收 webhook」的案例，所以漏掉最常見的路徑也受影響（A2，§4.2）。這輪的紅燈先行測試把兩個方向都放進去才量到。
7. **單元測試的本機基準不明**：Sprint 341 沒有在本機重跑後端完整套件，雲端單元 job 又是安靜模式（不印總數），所以這輪的單元總數只能報「本輪新增 +23 與實測總數」，說不出 Sprint 341 結束時的絕對基準（§7.1）。
8. **預測落空一項**：M12／M13 在真實棧的機制預測為「死結或重複扣款」，實際只有重複扣款（§6.2 第 4 點）。
9. **誤呼叫寫了一個工作目錄外的檔案**：commit 前預檢 secret 掃描的那一輪，我多送了一個沒有目的的 `Write` 呼叫，在 `/private/tmp/claude-501/` 底下（不是 scratchpad、也不是專案目錄）建了一個 2 位元組的檔案——違反 CLAUDE.md「絕不寫到工作目錄外」。發現後立刻刪除，並確認連帶建立的空目錄也已移除（`/private/tmp/claude-501/` 底下只剩本專案的目錄）；沒有進任何 commit，也沒有影響專案檔案。

## 10. 追蹤表的變動

| 編號 | 變動 |
|---|---|
| `DEF-453` | 🟠→✅：webhook 以累計金額的差額記帳（付款、訂單、訂房、結算單）；P1／P2 修復；**新發現**：連本系統 API 發起的部分退款，之後 webhook 也會把它翻成整筆（A2，已修）。**P3（Mock 退款端點）拆為 `DEF-507`** |
| `DEF-506`（新） | 🟡 webhook 處理失敗時 controller 仍回 2xx，Stripe 不會重送；記帳失敗的唯一痕跡是 ERROR 日誌（§4.3 實測）。需要營運面的決定 |
| `DEF-507`（新） | 🟢 `POST /v2/payments/refund`（Mock 付款）成功時不記 `refunded_amount`、不扣結算單、任何金額都整筆 `REFUNDED`（`DEF-453` 的 P3；Sprint 330 實測、本輪讀碼再確認） |
| `DEF-508`（新） | 🟢 已出貨訂單／訂房被 Stripe 後台全額退款後，訂單（`COMPLETED`）與訂房（`PAID`）的狀態不動——付款 `REFUNDED`、結算單已扣，但狀態標籤不一致；退貨／退款的狀態語意是產品決定 |

## 11. 我替使用者做的決定（可一句話推翻）

1. **本輪先做 `DEF-453`，沒有先做總結列的 `DEF-500` ④（列表查詢量測）**（§1.3）：它是金額記帳錯誤、已等 11 輪。推翻方式：告訴我先做 ④。
2. **悲觀鎖，不用 compare-and-swap 重試**（§5.2 第 2 點）。
3. **以累計值的差額記帳；累計值超過付款金額時以付款金額為上限。**
4. **已出貨（`canRefund` 為假）訂單的狀態不動，只記付款與結算單**（沿用既有規則；`DEF-508` 登記）。推翻方式：決定「Stripe 後台全額退款一律把訂單轉 `REFUNDED`」——那要先決定退貨流程與狀態語意。
5. **訂房只記帳、狀態不動**（不知道業務意圖：要不要取消訂房、釋放日曆）。
6. **付款不是已收款狀態（`PROCESSING`／`PENDING`／`FAILED`）就忽略退款事件**（舊碼會標成 `REFUNDED`）。推翻方式：改成放行並標 `REFUNDED`（不建議：沒確認收過款的付款沒有款項可退）。
7. **Mock 退款端點（P3）維持現狀**（`DEF-507`）：沒有真實金額；而且要先決定部分金額的語意（現況是任何金額都整筆 `REFUNDED`）。追蹤表原本就寫「小取捨，可一句話決定」。
8. **不改 controller「吞例外回 2xx」的政策**（`DEF-506`）：涵蓋所有事件類型、牽涉營運取捨（回 5xx 會讓 Stripe 重送最多數天，持續失敗的端點可能被停用）。
9. **移除 `PaymentRepository.markRefundedIfNotAlready`**（修法後沒有呼叫者，它的語意就是缺陷本身）。
10. **稽核的舊值／新值格式改成 `refunded=…`／`refunded=…,status=…`**（與 API 退款同格式）；動作名稱 `STRIPE_PAYMENT_REFUNDED_WEBHOOK` 不變。
11. **`REFUNDED` 狀態的舊資料列在事件重送時補記**（§8.2 第 6 點）。
12. **在上線檢查清單新增兩個人工確認項目**（`STRIPE_PRODUCTION_CHECKLIST.md`）——AI 不能代替 Dashboard 操作。
13. **只跑後端的完整驗證，不跑 E2E 與前端測試**（沒有前端變更）。

## 12. 狀態

| 項目 | 狀態 |
|---|---|
| `DEF-453` webhook 退款記帳（付款、訂單、訂房、結算單） | ✅ 已修復並驗證（§4～§7）；含新發現（連本系統 API 發起的部分退款也會被 webhook 翻成整筆，§4.2） |
| 突變驗證 | ✅ 24／24 擊殺（14 個恰好吻合登記、10 個吻合並多紅，0 倖存；§6） |
| 完整後端驗證 | ✅ `mvn -o clean verify`：單元 2567／0、整合 1074／0、Checkstyle 0 違規、PMD 通過（§7.1） |
| 新登記 | `DEF-506`（🟡 webhook 失敗被吞）、`DEF-507`（🟢 Mock 退款端點，原 P3）、`DEF-508`（🟢 已出貨訂單／訂房狀態不動） |
| 後端＋測試 commit | `abfe03d7` |
| 文件 commit | 計畫書、追蹤表、發布追蹤、上線檢查清單（commit 內容；hash 見 `git log`） |
| push 與雲端 CI | 待 push（連同 Sprint 341 的回填 commit `3fdfa78d`）；push 後回填 |
| Docker 收尾 | 待 push 與雲端 CI 之後 |
| 順延、沒做 | `DEF-500` ④（跨店鋪列表的查詢量測；我能獨立做，順延到下一輪）、②③⑤、`DEF-502`～`DEF-505`（產品／部署決策或框架限制，§1.2） |
| 要你做的 | ①`DEF-506`：決定 webhook 失敗時要不要回 5xx（§11 第 8 點）；②`DEF-508`：出貨後後台全額退款，訂單／訂房要變成什麼狀態；③上線前在 Stripe 測試模式依 `STRIPE_PRODUCTION_CHECKLIST.md` 新增的兩項人工確認 |
