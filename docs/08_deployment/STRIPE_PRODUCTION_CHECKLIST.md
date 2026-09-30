# 真實金流上線 Checklist / Stripe Production Checklist

> **建立日期**: 2026-07-04
> **Sprint**: Sprint 53 US-003（AI-2414）
> **對應成果**: Sprint 49~53 真實金流 Phase A（付款）+ B（webhook 權威狀態）+ C（退款）+ D-1（Connect Express onboarding）；**Sprint 56 部分退款**；**Sprint 80 Phase D-2（結算單核准後 Transfer 撥款給賣家）**
> **最後更新**: 2026-09-27（Sprint 205）——核對程式現況後更正 §F 已過時的「已知限制」（部分退款與 Transfer 早已實作），並補上 D-2 分潤的上線項目（`STRIPE_TRANSFER_ENABLED`、`transfer.reversed`、撥款走查）。**本文件仍是「人工逐項確認」清單，AI 不能代替 Dashboard 操作與測試模式端到端走查。**
> **用途**: 供人工在**正式上線 Stripe 真金鑰前**逐項確認，避免測試模式設定誤帶入生產
> **維護者**: QA Quincy + Dev David + Claude Code

---

## 🔴 為什麼需要這份 Checklist（誠實界線）

**AI 無法代替人類完成以下事項**：Stripe Dashboard 操作（生成/輪替金鑰、註冊 webhook 端點、審核 Connect Platform Profile）與「用真實測試模式金鑰跑一次端到端」皆需人工登入 Stripe Dashboard 與實際點擊操作。本文件是**檢查清單**，不是自動化腳本；每一項打勾都代表人工已確認。

---

## A. 金鑰與環境變數

- [ ] `STRIPE_SECRET_KEY` 已於正式環境設為正式金鑰（`sk_live_...`），**非** `sk_test_...` 或預設 `sk_test_placeholder`
- [ ] `STRIPE_WEBHOOK_SECRET` 已設定為 Stripe Dashboard 該 webhook 端點對應的 signing secret（`whsec_...`），**非空字串**（空字串會跳過驗簽，測試模式限定）
- [ ] 金鑰透過密鑰管理（環境變數 / secret manager）注入，**未**寫入任何 commit 到的檔案（`application.yml` 僅保留 `${STRIPE_SECRET_KEY:sk_test_placeholder}` 佔位）
- [ ] 正式環境的 `STRIPE_PAYMENT_ENABLED` / `STRIPE_CONNECT_ENABLED` / `STRIPE_TRANSFER_ENABLED`（Sprint 80 起，分潤撥款，**逐租戶**開關）feature toggle 依上線排程開啟（預設關閉，避免未就緒時誤放行真金流）

## B. Webhook 端點

- [ ] `POST /v2/payments/webhook/stripe` 端點對外可公開存取（HTTPS，非 localhost）
- [ ] Stripe Dashboard 已註冊該端點，並勾選以下事件：
  - [ ] `checkout.session.completed`
  - [ ] `payment_intent.payment_failed`
  - [ ] `charge.refunded`
  - [ ] `account.updated`（Connect，Phase D-1 起）
  - [ ] `transfer.reversed`（Phase D-2 起：Stripe 收回已撥款項時，本地 Transfer 標為 `REVERSED`、結算單回 `FAILED`）
- [ ] Dashboard 顯示該端點最近測試事件回應 `200 OK`（非 4xx/5xx）

## C. Stripe Connect Platform Profile（Phase D-1 起適用）

- [ ] Stripe Dashboard 帳號已完成 Connect Platform Profile 平台資料送審（非測試模式限定，正式收款需審核通過）
- [ ] 確認採用 **Express** 帳戶類型（非 Standard/Custom），與本平台架構決策一致
- [ ] Connect onboarding 的 `refresh_url`/`return_url` 指向正式環境網域（非 `localhost:3000`）

## D. 測試模式端到端人工驗證（上線前，於 Stripe 測試模式執行）

> 以下使用 Stripe 測試模式金鑰 + 測試卡號（`4242 4242 4242 4242`）執行，**不涉及真實金錢**。

- [ ] **付款成功**：下單 → Checkout 重導 → 測試卡付款成功 → webhook `checkout.session.completed` 送達 → 本地 Payment=SUCCESS、Order=PAID
- [ ] **付款失敗**：下單 → Checkout 使用拒絕測試卡（`4000 0000 0000 0002`）→ webhook `payment_intent.payment_failed` 送達 → 本地 Payment=FAILED
- [ ] **退款**：對已付款訂單觸發退款 → Stripe Dashboard 顯示退款成功 → webhook `charge.refunded` 送達（或 service 主動退款路徑）→ 本地 Payment/Order=REFUNDED
- [ ] **部分退款**（Sprint 56）：對已付款訂單退一部分金額 → 本地 Payment=`PARTIALLY_REFUNDED`、**Order 狀態不變**（訂單持續履約）；再退至累計全額 → Payment/Order=`REFUNDED`。運費不參與部分退款（PO 決策 2026-07-04）。退款金額小數位數超過 2 位會被拒絕（`E-6009`，Sprint 192）
  - **必須在同一筆訂單上連續退兩次，且兩次間隔在 24 小時內**，並到 Stripe Dashboard 確認出現**兩筆**退款、金額各如預期（Sprint 207，DEF-288）。原本送給 Stripe 的冪等鍵對同一筆付款恆相同，依 Stripe 文件第二次會被拒或被當成重送而不建立新退款；修復後鍵已綁定累計已退額，**但這個行為只在本機以 WireMock 驗證過送出的鍵，從未對真實 Stripe 驗證**。建議兩次都試：一次金額不同、一次金額相同（例如先退 100 再退 100）。若兩次退款在 Dashboard 只看到一筆，本修復無效，請回報
- [ ] **取消已付款訂單 → 自動退款**（Sprint 226，DEF-303 (5)；此前只有管理員呼叫退款 API 才會動錢）：對已付款訂單按「取消訂單」→ 訂單先變 `REFUNDING` → **約 1 分鐘內**（`APP_REFUND_CHECK_INTERVAL_MS`，預設 60000）排程經 Stripe 全額退回 → Dashboard 出現退款、本地 Payment/Order=`REFUNDED`。若 Stripe 拒絕，訂單維持 `REFUNDING`、稽核紀錄出現 `AUTO_REFUND_FAILED`（含 Stripe 的錯誤原因），並以 5、10、20… 分鐘（上限 6 小時）退避重試。**尚未對真實 Stripe 驗證**：Stripe 對「同冪等鍵」的反應與退款延遲都只依文件推論
- [ ] **付款成功時訂單已被取消**（DEF-308）：發起 Checkout 後先不付款，到另一個分頁取消該訂單，再回 Checkout 用測試卡付款 → webhook 送達後訂單應由 `CANCELLED` 轉 `REFUNDING`（狀態紀錄註明「Payment received after cancellation」、稽核 `STRIPE_PAYMENT_ORDER_NOT_PAYABLE`），隨後自動全額退回
- [ ] **Connect onboarding**（Phase D-1 起）：賣家發起 onboarding → 導向 Stripe 代管 KYC 表單（測試模式可用假資料完成）→ 完成後 `account.updated` webhook 送達 → 本地 tenant `connect_onboarding_status=COMPLETE`
- [ ] **分潤撥款 Transfer**（Phase D-2，Sprint 80）：租戶 Connect 為 `COMPLETE` 且該租戶 `STRIPE_TRANSFER_ENABLED` 開啟 → Admin 核准結算單（`APPROVED`）→ 後端呼叫 Stripe Transfer → 本地 Transfer=`COMPLETED`、結算單=`PAID`，Stripe Dashboard 可見對應 transfer。**反向也要驗**：Connect 未就緒或 toggle 關閉時，Transfer 應為 `SKIPPED_ONBOARDING_INCOMPLETE`（不撥款），補齊條件後可由管理端重試（`TransferController`）
- [ ] **`transfer.reversed`**：在 Dashboard 收回一筆測試 transfer → webhook 送達 → 本地 Transfer=`REVERSED`、結算單回 `FAILED`（**不會自動重新分潤，也不處理資金收回**，見 §F）
> ⚠️ **結算單的來源**：結算單只由每週一 00:00（**台灣時間**）的排程產生，**目前沒有手動觸發或補產的入口**（見 `DEFERRED_ITEMS_TRACKER.md` DEF-287）。測試模式走查上面兩個撥款項目時，需等到排程執行，或使用已存在的結算單；**不要照 [SETTLEMENT_JOB_RUNBOOK.md](SETTLEMENT_JOB_RUNBOOK.md) 的手動觸發步驟操作，那些步驟已與程式不符**（見該文件檔頭更正）。

## E. 正式金鑰切換

- [ ] 切換順序：先確認 A、B、C 節皆完成 → 才將 `STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET` 換為正式金鑰 → 再開啟 `STRIPE_PAYMENT_ENABLED`/`STRIPE_CONNECT_ENABLED` toggle → **最後才逐租戶開啟 `STRIPE_TRANSFER_ENABLED`**（會真的把錢撥給賣家；先確認該租戶 Connect 已 `COMPLETE`、且至少一筆小額結算單以測試模式走查過 §D）
- [ ] 切換後**立即**以小額真實金額（可退款）人工驗證一次付款 + 退款，確認正式環境串接無誤
- [ ] 若切換後發現異常，toggle 可即時關閉降級回 Mock 路徑（不需重新部署）
  - ⚠️ **Sprint 226 起**：關閉 `STRIPE_PAYMENT_ENABLED` 只影響**新的付款**；已在 Stripe 收下的款項，退款仍一律經 Stripe（付款方式為 STRIPE 的付款不會因為 toggle 被關掉而只在本地標成已退款）。自動退款依賴排程，`APP_SCHEDULING_ENABLED=false` 會連它一起關閉——此時取消已付款訂單只會停在 `REFUNDING`，需管理員手動退款

---

## F. 已知限制（誠實揭露）

> **Sprint 205 更正**：本節原有兩條（「只做全額退款」「Transfer 尚未實作」）在撰寫後不久就過時了——部分退款於 Sprint 56、Transfer 於 Sprint 80 完成，但本節從未回頭更新。以下已依程式現況重寫。

- **部分退款已支援**（Sprint 56）：累計退款達全額才轉 `REFUNDED`。**限制**：`payments.stripe_refund_id` 只存**最後一次**退款的 id（程式註解已誠實記載），多次部分退款只能靠 Stripe 端查全部退款。
- **分潤 Transfer 已實作**（Sprint 80）：結算單核准後撥款；失敗或略過可由管理端重試。**限制**：
  - `transfer.reversed` 只把 Transfer 標為 `REVERSED`、結算單回 `FAILED`，**不自動重新分潤、不處理資金收回**（人工處理）。
  - **沒有手動觸發／補產結算單的入口**，只有每週一 00:00（台灣時間）的排程（DEF-287）。排程當週某租戶失敗或漏產，目前只能等下週或直接操作資料庫。
  - **未以真實（或測試模式）Stripe 端到端走查過**——這是 §D 的人工項目，AI 無法代替。
- **`confirmPayment` 仍是 stub**（恆回成功，Checkout 流程未使用它）；**`getPaymentStatus` 讀本地資料庫而非向 Stripe 查詢**。若未來加 Stripe Elements 直連流程需另行實作。
- **金額換算**：Stripe 以「分」為單位，程式以 `amount × 100` 取 `longValue()`（截斷）換算；Sprint 192（DEF-267）已在退款入口拒絕小數位數超過 2 位的金額，避免與資料庫四捨五入產生一分錢差異。

---

## G. 走查結果記錄

| 項目 | 結果（✅/❌/N/A） | 備註 | 日期 |
|------|------------------|------|------|
| （逐節填寫） | | | |

**執行人**: ＿＿＿＿　**環境**: staging / production

---

**文件版本**: v1.0
**建立者**: QA Quincy + Dev David + Claude Code
**基於**: AISDLC v0.09
