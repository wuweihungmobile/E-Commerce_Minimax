# Sprint 205 Plan — M07 真實金流：現況核對與上線文件更正（PRODUCT_BACKLOG #10）

**Sprint**: Sprint 205
**日期**: 2026-09-27

## 1. 起點與缺口盤點

使用者在 Sprint 203 開頭要求「依照建議，繼續完成任務！做完後，依序完成ＡＢＣ」；本輪是 **B**：M07 真實金流（`PRODUCT_BACKLOG.md` #10「Stripe 真串接 + 分帳／退款／提現」，列為 13 SP 的 P3 大項）。

### 1.1 核心發現：這一項在程式層早就做完了

Sprint 203 盤點時已發現 backlog 的敘述過時；本輪逐項讀程式碼確認：

| backlog 寫的範圍 | 實際（程式碼查證） | Sprint |
|-----------------|-------------------|--------|
| Stripe 付款 | `StripePaymentGateway`、`PaymentStateService.initiateStripeCheckout`（Checkout Session） | 49~53 |
| Webhook 權威狀態 | `PaymentWebhookService` 處理 `checkout.session.completed`、`payment_intent.payment_failed`、`charge.refunded`、`account.updated`、`transfer.reversed`；`StripeSignatureVerifierService` 驗簽；`processed_stripe_events` 冪等（V60）；prod 缺 `STRIPE_WEBHOOK_SECRET` 時 fail-fast（DEF-262，Sprint 188） | 49~53、188 |
| 退款 | `PaymentStateService.refundOrderPayment`，**含部分退款**（`PARTIALLY_REFUNDED`） | 52、**56** |
| 分帳（Connect） | `TenantStripeConnectService`（Express onboarding）、`tenants` 4 個 Connect 欄位（V62） | 53 |
| 提現／撥款 | `TransferService`：結算單核准後 Transfer 撥款給賣家、失敗／略過可重試、`transfer.reversed` 處理；`transfers` 表（V64） | **80** |

**這一項沒有剩下需要 AI 實作的程式工作。** 剩下的全是只有人能做的事。

### 1.2 AI 做不到、必須使用者做的事（誠實界線）

依 [STRIPE_PRODUCTION_CHECKLIST.md](../08_deployment/STRIPE_PRODUCTION_CHECKLIST.md) §「為什麼需要這份 Checklist」：

1. Stripe Dashboard 操作：生成／輪替金鑰、註冊 webhook 端點、Connect Platform Profile 送審。
2. 以 Stripe **測試模式**金鑰＋測試卡號跑一次端到端（付款、退款、部分退款、Connect onboarding、Transfer 撥款、`transfer.reversed`）。
3. 正式金鑰切換與小額真實金額驗證。

我沒有 Stripe 金鑰，也沒有 Stripe mock（引入 `stripe-mock` 需新增 Docker image，依 [DOCKER_POLICY.md](../08_deployment/DOCKER_POLICY.md) 須先經人工核准清單）。**本輪沒有對 Stripe 做任何執行期驗證。**

### 1.3 我能做的事：讓上線清單與程式一致

核對上線清單與相關 Runbook 時，發現它們與程式不符，而**這兩份是使用者上線時會照著做的文件**：

| 文件 | 過時／錯誤 | 證據 |
|------|-----------|------|
| STRIPE_PRODUCTION_CHECKLIST §F | 「只做全額退款」「Transfer 尚未實作」——寫完後不久就過時（Sprint 56、80 已完成），從未回頭更新 | `PaymentStateService`（Sprint 56 部分退款）、`TransferService`（Sprint 80） |
| STRIPE_PRODUCTION_CHECKLIST §A、§B、§D、§E | 缺 `STRIPE_TRANSFER_ENABLED` 開關（逐租戶）、缺 `transfer.reversed` webhook 事件、缺撥款與部分退款的走查項目、切換順序沒有把「會真的撥款」放最後 | `TransferService`（`STRIPE_TRANSFER_ENABLED`）、`PaymentWebhookService`（`transfer.reversed`） |
| SETTLEMENT_JOB_RUNBOOK | **UTC** 觸發時間（實為台灣時間，Sprint 194）；環境變數 `SETTLEMENT_TRIGGER_MODE`（程式碼零讀取）；以 `scheduling.settlement.cron` 覆蓋 cron（cron 寫死在 `@Scheduled` 註解，不可由設定覆蓋）；「手動觸發／透過 admin 介面補建結算單」（**不存在**）；結算範圍「上週一～上週日」（Sprint 195 起是所有已完成且未結算訂單） | `SettlementGenerator`（`@Scheduled(cron = "0 0 0 ? * MON", zone = BusinessTime.ZONE_ID)`）、`generateWeeklyStatements` 只被排程呼叫、全庫 grep `SETTLEMENT_TRIGGER_MODE` 零筆（`docs/` 內僅 Runbook 自己） |

**新發現的實際缺口（不是文件問題）**：**沒有手動觸發或補產結算單的入口**，只有每週一的排程。排程當週某租戶失敗或漏產，只能等下週或直接操作資料庫；上線走查撥款也得等到週一。登記為 `DEF-287`（🟡）。

## 2. 使用者決策

本輪沒有新的使用者決策：範圍由「依序完成 ABC」與 Sprint 203 的提問確定。**`DEF-287` 是否要做、怎麼做（Admin 端點？Actuator？）需要使用者決定**，本輪只登記、不動。

## 3. 實作內容

**未動任何 `src/main`／`src/test`／前端。** 只更新文件：

- [STRIPE_PRODUCTION_CHECKLIST.md](../08_deployment/STRIPE_PRODUCTION_CHECKLIST.md)：檔頭補現況與更新日期；§A 加 `STRIPE_TRANSFER_ENABLED`；§B 加 `transfer.reversed`；§D 加部分退款、Transfer 撥款（含反向驗證：Connect 未就緒時不撥款）、`transfer.reversed`；§E 切換順序把「逐租戶開 `STRIPE_TRANSFER_ENABLED`」放最後；§F 依程式現況重寫（部分退款與 Transfer 已實作，並列出真實限制）。
- [SETTLEMENT_JOB_RUNBOOK.md](../08_deployment/SETTLEMENT_JOB_RUNBOOK.md)：檔頭加更正表（五項與程式不符的說法），**其餘章節標註「未重新核對」**，不重寫。
- [PRODUCT_BACKLOG.md](PRODUCT_BACKLOG.md)：#10 已於 Sprint 203 更正，本輪不再改。
- [DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md)：登記 `DEF-287`。

## 4. 測試

不適用（純文件）。文件中每一條事實陳述都對照過程式碼（見 §1.1、§1.3 證據欄）；`ErrorCodeDocDriftTest` 不受影響。

## 5. 驗證結果

- 純文件變更；未執行測試（沒有可執行的內容）。`git diff --stat` 僅 `docs/`。
- **未驗證**：Stripe 端的任何實際行為（見 §1.2）；Runbook 未被更正的章節（§3.4 驗證 SQL、§4 異常處理等）與程式的一致性。

## 6. 範圍外（延後）

- **以 Stripe 測試模式跑一次端到端走查**：使用者（或有 Stripe 帳號者）依清單 §D 執行，並填 §G 走查結果。
- **`DEF-287`**：結算單手動觸發／補產入口。
- **SETTLEMENT_JOB_RUNBOOK 全面重寫**：需先決定是否新增觸發入口，否則 Runbook 沒有可寫的正確手動步驟。
- **`stripe_refund_id` 只存最後一次退款的 id**（程式註解已記載）——多次部分退款的追溯只能到 Stripe 端查；是否要改成獨立退款表屬設計變更，未評估。

## 7. Push

**尚未 push。** 使用者未授權；Sprint 203～206 完成後一併詢問。純文件變更。

## 8. 下一步

**C**：`DEF-283`（Tomcat 連接器層拒絕 `%2f`／`%5C` 回 HTML 頁）。使用者已選「先實測可行性，再決定」：以真實 Tomcat 實測自訂 `ErrorReportValve` 是否能輸出 JSON＋標頭且不放寬任何 Tomcat 安全預設；做不到就改為只文件化並結案。維運三件事（`X-Forwarded-Proto`、staging CSP、Sprint 198 若已部署則送 `max-age=0`）仍在使用者手上。
