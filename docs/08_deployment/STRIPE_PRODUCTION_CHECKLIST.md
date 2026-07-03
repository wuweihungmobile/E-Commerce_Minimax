# 真實金流上線 Checklist / Stripe Production Checklist

> **建立日期**: 2026-07-04
> **Sprint**: Sprint 53 US-003（AI-2414）
> **對應成果**: Sprint 49~53 真實金流 Phase A（付款）+ B（webhook 權威狀態）+ C（退款）+ D-1（Connect Express onboarding）
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
- [ ] 正式環境的 `STRIPE_PAYMENT_ENABLED` / `STRIPE_CONNECT_ENABLED` feature toggle 依上線排程開啟（預設關閉，避免未就緒時誤放行真金流）

## B. Webhook 端點

- [ ] `POST /v2/payments/webhook/stripe` 端點對外可公開存取（HTTPS，非 localhost）
- [ ] Stripe Dashboard 已註冊該端點，並勾選以下事件：
  - [ ] `checkout.session.completed`
  - [ ] `payment_intent.payment_failed`
  - [ ] `charge.refunded`
  - [ ] `account.updated`（Connect，Phase D-1 起）
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
- [ ] **Connect onboarding**（Phase D-1 起）：賣家發起 onboarding → 導向 Stripe 代管 KYC 表單（測試模式可用假資料完成）→ 完成後 `account.updated` webhook 送達 → 本地 tenant `connect_onboarding_status=COMPLETE`

## E. 正式金鑰切換

- [ ] 切換順序：先確認 A、B、C 節皆完成 → 才將 `STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET` 換為正式金鑰 → 最後才開啟 `STRIPE_PAYMENT_ENABLED`/`STRIPE_CONNECT_ENABLED` toggle
- [ ] 切換後**立即**以小額真實金額（可退款）人工驗證一次付款 + 退款，確認正式環境串接無誤
- [ ] 若切換後發現異常，toggle 可即時關閉降級回 Mock 路徑（不需重新部署）

---

## F. 已知限制（誠實揭露）

- **只做全額退款**：部分退款（partially_refunded）尚未支援（Sprint 52 Retro AI-2415，待評估）
- **代收後分潤（Transfer）尚未實作**：Phase D-1（本文件涵蓋）只做 Connect 帳戶開通；代收後 transfer 給賣家為獨立 Phase D-2（另立 Sprint），本 checklist E 節「正式金鑰切換」不代表分潤已可上線
- **confirmPayment/getPaymentStatus 仍為 stub**：Checkout 流程未使用，若未來加 Stripe Elements 直連流程需另行實作

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
