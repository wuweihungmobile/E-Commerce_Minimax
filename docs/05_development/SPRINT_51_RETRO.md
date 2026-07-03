# Sprint 51 Retrospective / Sprint 51 回顧會議

> **Sprint 編號**: Sprint 51
> **期間**: 2027-09-26 ~ 2027-10-09
> **回顧日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 5 SP（US-001~002）|
| 完成 SP | 5 SP（全數）|
| 主題 | 真實金流 Phase B——webhook 事件驅動的權威付款狀態 |

---

## 2. 做得好的（What went well）

- **緊接 Phase A 補上關鍵缺口**：S50 Retro 標記「webhook 權威狀態上線前必要」，S51 立即接手，避免「Stripe 已收款但本地未 PAID」的對帳缺口長期存在。分階段路線（S49 評估）+ 逐階段緊接執行，節奏穩健。
- **雙路徑共用核心杜絕分歧**：回跳（Phase A）與 webhook（Phase B）共用 `markStripePaymentSucceeded`——同一冪等邏輯，兩條路徑不會產生不一致狀態。這是「抽共用而非各寫一套」的正確取捨。
- **雙層冪等到位**：event id 去重（V60）+ 狀態轉移冪等（已 PAID no-op）。Stripe 明確要求 webhook 去重，實作忠實採納；單元 UT-WH-003 編碼重送 skip 不變量。
- **webhook 回 2xx 的正確工程**：除簽章無效外一律回 2xx，避免 Stripe 無限重送風暴——這是 webhook 消費者的關鍵正確性，容易被忽略但本次明確處理。
- **後端聚焦、複用既有驗簽**：webhook 為 server-side，無前端變動；複用 S14/S21 已真接線的 HMAC-SHA256 驗簽，只補事件解析與 dispatch。

---

## 3. 待改善的（What to improve）

- **失敗事件路徑為 best-effort**：payment_intent.payment_failed 依 pi id 找 Payment，但 Checkout 惰性建 pi 使 stripePaymentIntentId 常為 null → 失敗事件可能找不到 Payment。→ 改善：未來（Phase C 或補強）於 createCheckoutSession 設 `payment_intent_data.metadata.order_id`，或改監聽 `checkout.session.expired`（session id 可靠），使失敗/過期路徑同樣可靠。
- **webhook 整合測試以單元 + 樣本 payload 為主**：未做「真 DB seed 訂單 → POST webhook → 驗 DB 狀態」的完整整合測試（成本較高）。→ 單元（dispatch）+ 整合回歸（context 載入 + mock 不退步）已覆蓋主要風險；完整 webhook→DB 整合可於上線前補強。
- **並發重送的 PK 競態**：existsById → dispatch → save 之間，理論上並發重送可能兩者都通過 existsById 再撞 PK。→ 極端邊界；PK 約束會讓第二者 save 拋錯（controller 回 2xx 吞掉），狀態冪等保障不重複副作用。可接受，記錄備查。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1908 | 檢查點徵詢後 push S41~S51 | 通過完整 validate-release 後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-2412 | 真實金流 Phase C：退款真串接 | Refund.create + charge.refunded webhook + 清理既有 refund stub；順帶補 pi metadata 使失敗路徑可靠 | Dev David | P3 | 待排 |
| AI-2413 | 真實金流 Phase D：分帳/提現 | Stripe Connect 或手動；賣家 onboarding/KYC | SD Marcus | P3 | 待 PO 決策 |
| AI-2414 | 真金流上線 checklist | STRIPE_WEBHOOK_SECRET 配置、公開 webhook 端點、Stripe 測試模式端到端人工驗證、真金鑰注入 | QA Quincy + Dev David | P2 | 上線前 |
| AI-2407 | 定價規則選取語意評估 | bestRule priority + range 查詢 | SD Marcus | P3 | 待評估 |
| AI-1903 | 買家閉環 live 走查（真人）| live 環境跨角色資料流（含真金流測試模式驗證）| QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 50 → Sprint 51 Action Items 追蹤結果

| Action Item | 內容 | Sprint 51 達成狀態 |
|------------|------|---------------------|
| AI-2411 | 真實金流 Phase B webhook 驅動狀態 | ✅ 完成（webhook 事件解析 + 權威狀態 + V60 去重 + 雙層冪等）|
| AI-1908 | 檢查點 push | 🟡 續留（S41~S51 累積 11 Sprint）|
| AI-2412/2413 | 金流 Phase C/D | 🟡 續留 |
| AI-2407 | 定價規則語意評估 | 🟡 續留（P3）|
| AI-1903 | 買家 live 走查 | 🟡 續留（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S46 | 8 |
| S47 | 7 |
| S48 | 8 |
| S49 | 5 |
| S50 | 8 |
| **S51** | **5** |

> **觀察**：S51 = 5 SP，後端聚焦（webhook server-side）。品質：後端單元 9 + 真 DB 整合 21（mock/Phase A 不退步）、validate-schema 無漂移（V60）、validate-e2e **54 passed/0 fail**（持平）、catch(Exception)/@Deprecated=0。**真實金流權威狀態落地**——webhook 補上 Phase A 回跳未達的缺口，上線前必要項達成。Phase C（退款）/ D（分帳）+ 上線 checklist 待排。

---

## 7. 下一步

> **檢查點**：Sprint 51 已完成（US-001+US-002 `8321f2f` + 收尾，本地各層驗證通過含 validate-schema 無漂移 + validate-e2e 54/6/0）。**push 債已累積 S41~S51（11 Sprint）**，建議於檢查點徵詢後執行完整 `make validate-release` 並一次 push（AI-1908；嚴禁 --no-verify）。**真實金流付款閉環（Phase A 卡片付款 + Phase B 權威狀態）已具上線基礎**。Sprint 52 建議：AI-2412 Phase C 退款、AI-2414 真金流上線 checklist（含端到端人工驗證）、AI-2413 Phase D 分帳、AI-2407 定價語意、AI-1903 真人 live 走查。**強烈建議：11 Sprint push 債宜盡快清償——尤其含真實金流程式碼。**

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
