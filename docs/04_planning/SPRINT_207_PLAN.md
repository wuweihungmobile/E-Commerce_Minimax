# Sprint 207 Plan — DEF-285 盤點的前置步驟，順帶修復 Stripe 退款冪等鍵（DEF-288）

**Sprint**: Sprint 207
**日期**: 2026-09-27

## 1. 起點

Sprint 203～206 收尾後，使用者回覆「需要我操作的部分，先行跳過」：維運三件事（前方代理 `X-Forwarded-Proto`、staging CSP、Sprint 198 的 `max-age=0`）與 Stripe 測試模式走查都需要人操作，本輪跳過。

剩下三項新登記的待辦，追蹤表都標明**需要使用者決定**（`DEF-284` 改 API 狀態碼、`DEF-285` 產品決策、`DEF-287` 入口的設計），我不替使用者決定。

`DEF-285` 的追蹤表列有一句「**先做的一步**：逐一列出所有 POST/PUT/DELETE 端點，標出哪些有冪等保護、哪些會因重送而重複寫入（訂單建立是最該優先查的）」——這一步不需要任何決定，本輪做它。

## 2. DEF-285 盤點（範圍與結論）

**範圍**：後端共 174 個寫入端點（POST 98／PUT 45／DELETE 30／PATCH 1）。**本輪只查了會動到錢或庫存的關鍵路徑**（下表），其餘約 160 個未逐一核對。

| 端點 | 重送保護 | 依據與限制 |
|------|----------|------------|
| `POST /v2/orders`（PRODUCT） | **API 層沒有**。前端送出中會停用按鈕（`checkout/product/page.tsx` 的 `submitting`）；後端有一個**意外的、機率性的**保護 | 見下方 §2.1 |
| `POST /v2/checkout/mixed` | 選帶 `Idempotency-Key`；前端**有送**（`checkout/mixed/page.tsx:152`） | 標頭名無 `X-` 前綴（與 PRD 不同，已記於 DEF-285） |
| `POST /v2/bookings` | 選帶；前端**有送** | 同上 |
| `POST /v2/orders/{id}/pay/checkout` | Stripe 鍵 `ORDER-CHECKOUT-<orderId>`＋`payments.idempotency_key` 唯一索引（V79）＋`saveAndFlush` 捕捉違反約束 | 程式碼閱讀，未執行 |
| `POST /v2/orders/{id}/refund` | **併發**：`applyRefundIfUnchanged` 以 CAS 佔用額度，敗方被拒。**重送**：本身無鍵（合法的第二次部分退款與重送無法區分）；**送給 Stripe 的鍵有缺陷 → DEF-288** | 見 §3 |
| `POST /v2/payments/refund` | 只改本地狀態（`PaymentService.processRefund` 標為 Mock，不碰 Stripe）；以 `updateStatusIfCurrent` 原子轉換 | 程式碼閱讀 |

### 2.1 `POST /v2/orders` 的併發重複下單：**推論，未重現**

`createOrderFromCart` 先讀購物車、建單、預扣庫存，最後才逐項 `removeItem`。兩個併發請求（兩個分頁、或客戶端／代理在逾時時重送）都可能讀到同一份購物車。目前擋住第二筆的，是 `RedisCartService.removeItem` 對「已不存在的項目」拋 `CartItemNotFoundException`，讓輸家的整個交易回滾——但那個方法是**先 `get` 再 `delete` 的非原子檢查**，兩個請求若在同一個 Redis 往返內都通過檢查，就可能雙雙成立。

**為什麼沒有重現**：整合測試的購物車是 `IntegrationTestConfiguration` 裡的 in-memory 假物件，模擬不到真 Redis 的這個時間窗；要驗必須以真實 Redis 做時序測試。我判斷機率低（窗口約一個 Redis 往返），且後果是兩筆都未付款的 `CREATED` 訂單，不是重複扣款，所以**只登記、未修**。是否處理與怎麼處理（伺服器端序列化、或要求冪等鍵）併入 DEF-285 的產品決策。

## 3. 新發現：DEF-288（已修）

**問題**：`PaymentGatewayFactory.processRefund` 把 Stripe 冪等鍵固定組成 `refund-<PaymentIntent id>`，與金額、次數無關——同一筆付款的**每一次**部分退款都送同一把鍵。

**驗證了什麼、沒驗證什麼**：
- ✅ **已驗證（本機）**：紅燈測試讓 service → factory → 真實 `StripePaymentGateway` 全走真的、只在 HTTP 層以 WireMock 攔截，連續兩次部分退款送給 Stripe 的 `Idempotency-Key` **完全相同**（兩種情境都紅：同金額、不同金額）。
- ⚠️ **未驗證（沒有金鑰）**：Stripe 對「同鍵」的反應。依 Stripe 文件：相同鍵＋相同參數會回傳第一次的結果、不建立新退款；相同鍵＋不同參數回錯誤；鍵約 24 小時後失效。若文件所述屬實，後果是——不同金額的第二次部分退款**在 24 小時內會失敗**（可見、安全的方向）；**同金額**的第二次部分退款會**靜默不退**，本地卻已累計兩次（本地記錄與 Stripe 實際退款不符）。
- **為什麼既有測試看不到**：`PaymentStateServiceStripeTest` 把 factory 整個 mock 掉，UT-PAY-STRIPE-009（第二次部分退款）只驗狀態轉換，鍵是在 factory 內組出來的，測試從頭到尾看不到。這是「測試以固件繞過同一段邏輯」的又一例。

**修法**：鍵綁定「這一次邏輯退款」＝ `refund-<pi>-<退款前累計已退額>-<本次金額>`（兩者皆 `setScale(2)`）。
- 不同的邏輯退款 → 不同的鍵（第二次部分退款不再被當成重送）。
- 「Stripe 失敗、本地交易回滾後重試同一筆退款」→ **相同的鍵**，仍由 Stripe 替我們去重（也涵蓋「Stripe 已受理、但本地提交失敗」的情境）。
- 累計額取自呼叫端 CAS 前讀到的 `previousRefundedAmount`，不依賴記憶體物件的中途狀態。
- `PaymentGatewayFactory.processRefund` 的鍵改由呼叫端傳入；**舊的四參數簽章刻意移除而非保留多載**，遺漏的呼叫端會在編譯期失敗（僅 `PaymentStateService` 一個呼叫端）。

**不採用的替代案**：只用金額當鍵——連續兩次「同金額」部分退款仍同鍵，突變驗證證實會被測試抓到（見 §5）。

## 4. 實作內容

- `PaymentStateService.executeStripeRefund`：組冪等鍵並傳給 factory。
- `PaymentGatewayFactory.processRefund`：新增 `idempotencyKey` 參數（取代固定鍵）。
- 測試：新增 `PaymentRefundIdempotencyKeyTest`（3 案例）；`PaymentStateServiceStripeTest`／`PaymentStateServiceTest` 對 `processRefund` 的 stub 與 verify 改為五參數（僅簽章，語意不變）。
- 文件：[STRIPE_PRODUCTION_CHECKLIST.md](../08_deployment/STRIPE_PRODUCTION_CHECKLIST.md) §D 部分退款項目補上「連續兩次」的走查要求。

## 5. 測試與突變驗證

| 測試 | 守什麼 |
|------|--------|
| `UT-REFUND-KEY-001`（WireMock） | 連續兩次**同金額**部分退款 → Stripe 收到的兩把鍵不同 |
| `UT-REFUND-KEY-002` | 連續兩次**不同金額**部分退款 → 鍵不同 |
| `UT-REFUND-KEY-003` | Stripe 失敗後重試同一筆退款 → 兩次請求的鍵**相同** |

**紅燈先行**：修復前 001、002 紅（`Found duplicate(s)`），003 綠（舊行為本來就是固定鍵，003 是防止修成「每次隨機」的護欄）。

**突變驗證**（暫時破壞實作、跑測試、還原並以行數與 `git diff` 確認）：

| 突變 | 結果 |
|------|------|
| 鍵恆為 `refund-<pi>`（修復前行為） | 001、002 紅 |
| 鍵只含金額（偷懶修法） | 001 紅（002 因金額不同而綠——正是要有 001 的原因） |
| 鍵每次隨機 | 003 紅 |

**3 種全被抓到。**

## 6. 驗證結果

- **後端** `mvn -o clean verify`（真實 postgres／redis）：**單元 1774**（Sprint 206 收尾 1771，+3 即 `PaymentRefundIdempotencyKeyTest`）、**整合 565**（不變），**0 failures／0 errors／0 skipped**；checkstyle（main＋test）**0 violations**；`BUILD SUCCESS`，12:32。
- **針對性回歸**（改簽章前後）：付款相關 5 個測試類別共 94 個測試全綠（`PaymentRefundIdempotencyKeyTest`、`PaymentStateServiceStripeTest`、`PaymentStateServiceTest`、`StripePaymentGatewayTest`、`PaymentServiceRefund*Test`）。
- **未執行**：E2E（`make validate-e2e`，本輪未動前端）與 `make validate-release` 完整守門（屬 push 流程）；本輪沒有 Flyway 遷移。
- **對真實 Stripe 的任何驗證：沒有**（見 §3、§7）。

## 7. 範圍外（延後）

- **`POST /v2/orders` 併發重複下單**（§2.1）：推論、未重現；處理方式併入 `DEF-285`。
- **其餘約 160 個寫入端點**未核對冪等性。
- **對真實 Stripe 的驗證**：需要人在測試模式跑——`STRIPE_PRODUCTION_CHECKLIST.md` §D 的「部分退款」項目已改為要求連續兩次（同一筆訂單、24 小時內）。這是驗證上述 Stripe 行為推論與本修復的唯一方式。
- 上線前已在飛行中的退款：鍵的格式改了，部署當下「已送出但未確認」的退款若被重試，不會與舊鍵去重（窗口極小，僅提及）。

## 8. Push

**尚未 push。** Sprint 203～207 共 5 個本機 commit。本輪修復雖屬金流，但 push 會連同 Sprint 203～206（新功能與文件）一併推出，不在 2026-07-08「金流／安全 commit 免確認 push」授權的範圍內，故仍等使用者決定。

## 9. 下一步

仍待使用者決定：`DEF-285`（產品決策，併入 §2.1）、`DEF-287`（結算單手動觸發入口）、`DEF-284`（`E_1005` 回 404）、真實寄信服務、push；仍在使用者手上：維運三件事、Stripe 測試模式走查。
