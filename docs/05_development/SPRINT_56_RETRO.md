# Sprint 56 Retrospective / Sprint 56 回顧會議

> **Sprint 編號**: Sprint 56
> **期間**: 2027-12-05 ~ 2027-12-18
> **回顧日期**: 2026-07-04
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 5 SP（US-001）|
| 完成 SP | 5 SP（全數）|
| 主題 | 部分退款——任意金額，運費不退 |

---

## 2. 做得好的（What went well）

- **業務決策先於實作，且問題聚焦精準**：規劃前先派調查 agent 釐清現況（gateway 層已支援金額參數、schema 現況、平行 Mock 路徑），再用 `AskUserQuestion` 針對 2 個真正需要 PO 判斷的業務問題（退款粒度、運費政策）徵詢，而非籠統地問「怎麼做部分退款」，讓決策效率高、選項具體可比較。
- **發現平行技術債但未擴大範圍處理**：探勘過程中發現 `PaymentService.processRefund` 是與本次修改路徑平行的純 Mock 退款邏輯，判斷這是既有技術債、非本次修復範圍，明確記錄後不處理，避免範圍蔓延（Rule 3 精準改動）。
- **checkstyle 超標即時修正而非放寬規則**：`refundOrderPayment` 加入驗證邏輯後 NPathComplexity 超標，選擇抽取兩個語意清楚的私有方法（`resolveRefundAmount`/`executeStripeRefund`）解決，而非調整 checkstyle 門檻或忽略警告。
- **既有測試更新而非略過**：全額退款的既有測試（UT-PAY-STRIPE-005/006）因新簽章與新的 gateway 呼叫金額（從 null 變為明確剩餘全額）而需要更新斷言，均正確識別並修正，而非刪除或跳過。

---

## 3. 待改善的（What to improve）

- **Sprint Plan 文件延後於實作完成後才補寫**：本 Sprint 因調查結果明確、PO 決策快速到位，Claude Code 直接進入實作，Sprint Plan 文件（`SPRINT_56_PLAN.md`）是在實作與測試皆完成後才回頭補寫，雖內容忠實反映實際交付，但流程順序與 AISDLC「先計劃後實作」的理想順序不符。→ 未來即使決策明確、可立即實作，仍應先產出（哪怕精簡的）Plan 文件再開始寫程式碼，維持文件與實作同步的節奏。
- **webhook 路徑的部分退款支援未同步評估**：本 Sprint 修改了同步呼叫路徑但未評估 webhook（`charge.refunded`）路徑是否也需要支援部分退款金額解析，僅記錄為誠實揭露事項。→ 若未來部分退款成為常態使用功能，應評估 webhook 路徑是否會收到需要處理的部分退款事件。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-2202f | 開放窗清除機制 | 部分更新慣例無法清回 NULL | Dev David | P4 | 待評估 |
| AI-2408 | availability reason 錯誤碼化 + i18n | 後端英文字串碼化 | Dev David | P4 | 待評估 |
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| （未立案）| 純 Mock 退款路徑（`PaymentService.processRefund`）與真 Stripe 路徑整合評估 | 兩套獨立退款邏輯的技術債，是否需要整合 | SD Marcus | P4 | 待評估 |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 55 → Sprint 56 Action Items 追蹤結果

| Action Item | 內容 | Sprint 56 達成狀態 |
|------------|------|---------------------|
| AI-2415 | 部分退款評估 | ✅ 完成（超出原「評估」範疇，PO 決策後直接完成實作）|
| AI-2202f | 開放窗清除機制 | 未啟動（P4，續留）|
| AI-2408 | availability reason 錯誤碼化 + i18n | 未啟動（P4，續留）|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S51 | 5 |
| S52 | 5 |
| S53 | 8 |
| S54 | 6 |
| S55 | 3 |
| **S56** | **5** |

> **觀察**：S56 = 5 SP，落於歷史區間內；原估 3 SP 因涉及 migration + enum + 兩個新輔助方法 + 6 個測試案例調整，規模略高於原估，已於規劃時揭露收斂為 5 SP。品質：後端單元 512 tests 0 fail（新增 4 + 更新 2）+ 真 DB 整合 419 tests 0 fail、`make validate-schema` 無漂移、catch(Exception)/@Deprecated=0。**退款機制擴充支援部分金額**，與全額退款並存。

---

## 7. 下一步

> **檢查點**：Sprint 56 已完成（US-001 正式承諾 5 SP 全數完成，本地各層驗證通過含 `make validate-schema` 無漂移 + 全量回歸 512+419 tests 0 fail）。本 Sprint commit 待累積後續徵詢時一併 push（累積 S41~S56）。下一 Sprint 候選：AI-2202f 開放窗清除機制（P4）、AI-2408 availability reason 錯誤碼化 + i18n（P4）、純 Mock 退款路徑整合評估（未立案，P4）、AI-2416 Phase D-2 分潤（P3，需 Phase D-1 上線，目前不可執行）、AI-1903 真人 live 走查（需環境，目前不可執行）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
