# Sprint 88 Retrospective / Sprint 88 回顧會議

> **Sprint 編號**: Sprint 88
> **期間**: 2026-07-09
> **回顧日期**: 2026-07-09
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 8 SP（PRODUCT 商品訂單結帳流程前端串接，含探查中併入的兩項關聯缺口） |
| 完成 SP | 8 SP |
| 主軸 | 補齊 Sprint 87 retro 發現並提高優先度的既有缺口——前端從未呼叫 `POST /v2/orders` 建立 PRODUCT 訂單，使地址簿等既有後端能力缺乏真實購物流程可用場景。使用者已授權「三者最佳化順序」（M18/PRODUCT結帳/M16-M07表單）中優先排定的第一項 |

---

## 2. 做得好的（What went well）

- **動手前先探查付款串接的實際機制，避免重複造輪子或引入不必要的相依套件**：探查發現後端 Stripe 整合走的是 Hosted Checkout Session 整頁重定向，而非 Stripe.js/Elements 客戶端表單——確認後直接沿用既有 `services/payment.ts` 的 `createCheckoutSession`/`confirmCheckoutReturn`，未新增任何前端 Stripe 套件相依。
- **主動核實 Explore agent 的探查結論，發現並修正一處誤判，避免重複實作既有頁面**：初步探查報告聲稱 Stripe 回跳頁面 `/orders/{id}/payment/success`、`.../cancel` 完全不存在，動手前實際 `ls`/`Read` 確認後發現兩頁**皆已存在且實作完整**（含 `data-testid` 供既有 e2e 測試使用，成功頁刻意採「直接讀 `window.location.search`」而非 `useSearchParams()` 以避開 Next.js Suspense 邊界要求）。若未核實直接依報告動手，會產生重複/衝突的實作。
- **識別並主動處理因本次工作而從「休眠風險」轉為「真實風險」的兩個關聯缺口，而非視而不見**：探查發現的混合購物車誤處理、庫存從未扣減兩個既有缺口，此前都因為前端從未呼叫 PRODUCT 訂單建立 API 而處於休眠狀態，不構成真實風險；但本 Sprint 一旦補上前端串接、變成真實可用的購物路徑，兩者都會從理論缺口變成可被真實觸發的問題。混合購物車的過濾修法明確無業務歧義，直接動手修復；庫存扣減涉及範圍與設計選擇（是否併入、reserve 時點等），主動詢問使用者取得裁示後才動手，未自行假設。
- **善用既有但從未被使用的原語，而非重新設計**：`ProductInventory` entity 的 `reserve`/`release`/`deductStock`/`hasAvailableStock`（含 `@Version` 樂觀鎖）早已存在且設計完整，僅 M16 ERP 模組在用；本次直接重用而非另起一套庫存機制，符合本專案「先讀後寫」的既有慣例（比照 Sprint 86 重用孤兒 `CreditNote` entity 的模式）。

## 3. 待改善的（What to improve）

- **退款回補庫存（restock-on-refund）留有已知缺口，需在未來 Sprint 明確規劃**：本次庫存機制僅覆蓋「預扣→扣帳」單向流程與「取消前尚未付款→釋放」，已付款訂單走退款流程時不會自動回補庫存，已記錄為 DEF-044，但目前僅有問題描述、沒有明確的下一步設計方向（回補時點、是否需與供應鏈退貨流程整合等仍待業務判斷）。
- **ROOM 訂房結帳流程的對稱缺陷（DEF-043）評估後判斷範圍過大、暫不處理，但兩條结帳流程長期分裂（`/checkout` 走 `/v2/bookings`、`/checkout/product` 走 `/v2/orders`）的架構一致性問題尚未有整體評估**：兩條路徑各自維護購物車清空邏輯，未來若再新增第三種商品類型或结帳情境，重複踩雷風險仍存在，值得在技術債清單中提高關注度而非僅記錄單一缺陷。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------|------|--------|--------|--------|
| DEF-043 | ROOM 訂房結帳流程建立預訂成功後清空整個購物車（含混雜的 PRODUCT 項目） | 與本 Sprint 修復的 PRODUCT 側同類缺陷，範圍需獨立評估 | Dev David | 🟡 中 | 待排入 |
| DEF-044 | 已付款訂單退款/取消時無庫存自動回補 | 需業務決策回補時點與供應鏈整合方式 | PM Victoria | 🟡 中 | 待排入 |
| （前端候選，延續自 Sprint 85/86） | M16 採購審批門檻設定表單 + SuperAdmin 審批清單頁面；M07 結算逆轉發起/確認表單 | 後端 API 已完成，前端尚未實作，使用者已排定為「三者最佳化順序」第二項 | Dev David | 🟡 中 | 待排入 |
| （PRD Phase 2-B，延續） | M18 客服工單子系統 | 連續多 Sprint 列為候選，使用者已排定為「三者最佳化順序」第三項 | PM Victoria | 🟡 中 | 待排入 |
| （技術債，延續自 Sprint 71-87） | `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |

---

## 5. Sprint 87 → Sprint 88 Action Items 追蹤結果

| Action Item | 內容 | Sprint 88 達成狀態 |
|------------|------|---------------------|
| PRODUCT 商品訂單結帳流程串接 | Sprint 87 retro 提高優先度的候選項目 | ✅ **已完成**：後端修復混合購物車+庫存串接、前端新增 `/checkout/product` 頁面 + 購物車分流 |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S85 | 5 |
| S86 | 13 |
| S87 | 5 |
| **S88** | **8**（PRODUCT 結帳串接 + 混合購物車修復 + 庫存預扣/扣帳/釋放） |

---

## 7. 下一步

> **檢查點**：Sprint 88 已完成，PRODUCT 商品訂單結帳流程前端串接落地，目前無高優先級（🔴）延後項目。使用者已授權「三者最佳化順序」，本 Sprint 為第一項，**下一輪依序進行**：
> 1. 前端候選：M16 採購審批門檻設定表單 + SuperAdmin 審批清單頁面、M07 結算逆轉發起/確認表單（後端 API 已完成）。
> 2. PRD Phase 2-B 項目：M18 客服工單子系統。
> 3. 技術債：`RELEASE_TRACKER.md` 補齊 Sprint 74+ 列；DEF-043/DEF-044 排程評估。

---

**文件版本**: v1.0
**建立日期**: 2026-07-09
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
