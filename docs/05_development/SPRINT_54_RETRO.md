# Sprint 54 Retrospective / Sprint 54 回顧會議

> **Sprint 編號**: Sprint 54
> **期間**: 2027-11-07 ~ 2027-11-20
> **回顧日期**: 2026-07-04
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 6 SP（US-001~002）|
| 完成 SP | 6 SP（全數）+ US-003 不計點文件產出 |
| 主題 | 定價規則選取語意修正——Range 查詢 overlap 化 + 同優先級 tie-break 落實 |

---

## 2. 做得好的（What went well）

- **前置程式碼調查先行，避免規劃階段誤估**：規劃前先派調查 agent 精確定位 `findActiveRulesForDateRange` 的 JPQL、`calculatePrice`/`getEffectivePrice` 的排序邏輯、既有空斷言測試的具體位置，確認 `isRuleApplicable` 逐日邏輯本就正確、`PricingRule.createdAt` 欄位已存在可直接複用，使承諾 SP 從原估 5-8 SP 收斂至 6 SP 且未超支。
- **業務決策點正確升級徵詢**：同優先級 tie-break 屬業務語意決策（AISDLC 規範禁止 AI 自行假設），規劃前主動以 `AskUserQuestion` 列出 4 個具體選項徵詢 PO，而非依「測試標題看起來想表達的意圖」自行假設，取得明確決策後才寫入計劃並開始實作。
- **修正範圍精準、未過度設計**：只動了查詢 JPQL 一行語意 + 排序 Comparator 補一個次鍵，未觸碰 `isRuleApplicable`/`calculateAdjustment` 等逐日精準計算核心，符合「精準改動」原則（Rule 3）。
- **開發-編譯-測試循環嚴格執行**：US-001（repository 修正）與 US-002（排序修正）分開執行，每步完成立即編譯 + 跑對應測試，未累積開發，問題（若有）可即時定位到單一變更。
- **既有空斷言測試被誠實揭露而非默默略過**：發現 UT-M12-009 標題與斷言內容不符後，明確記錄「這是既有測試缺陷、非本 Sprint 新增」，並修正為真斷言，而非略過不提。

---

## 3. 待改善的（What to improve）

- **Repository 層 JPQL 語意變更的測試基礎設施薄弱**：本專案原無任何直接對 repository 查詢做真 DB 驗證的測試層（如 `@DataJpaTest`），所有既有測試皆 mock `PricingRuleRepository`，導致查詢語意本身的正確性只能透過既有的端到端 `@SpringBootTest`（`BookingControllerE2ETest`）間接驗證。→ 未來若專案內類似「查詢語意」修正頻繁出現，可評估是否值得投資一個輕量的 repository 專屬真 DB 測試基礎設施。
- **`createdAt` 精度邊界情況的決策留白**：本 Sprint 未與 PO 確認「同一奈秒內建立多條規則」這種極端邊界情況是否需要三級排序鍵，因發生機率極低予以擱置。→ 若未來人工建立規則的介面允許批次匯入（可能同時間建立大量規則），應重新評估此邊界情況。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線；Transfer.create/transfer_data + 對帳/提現介面 | SD Marcus | P3 | 待評估 |
| AI-2409 | 定價計算器統一評估 | PRODUCT/ROOM 兩套計算器分歧（Sprint 48 續留）| SD Marcus | P4 | 待評估 |
| AI-2415 | 部分退款評估 | partially_refunded 狀態 + 金額計算（Sprint 52 續留）| SD Marcus | P4 | 待評估 |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |
| AI-1908 | 檢查點 push S41~S54 | 本 Sprint 決策先規劃後 push，維持批次 push 慣例 | Dev David | P2 | 收尾檢查點 |

---

## 5. Sprint 53 → Sprint 54 Action Items 追蹤結果

| Action Item | 內容 | Sprint 54 達成狀態 |
|------------|------|---------------------|
| AI-2407 | 定價規則選取語意評估（Sprint 54 主軸）| ✅ 完成（US-001+US-002：range 查詢 overlap 修正 + tie-break 後建立者優先，PO 決策後落地）|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線後評估）|
| AI-2414 | 真金流上線 checklist 端到端人工驗證 | 未啟動（人工執行項目，非本 Sprint 範圍）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|
| AI-2415 | 部分退款評估 | 未啟動（P4，續留）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S49 | 5 |
| S50 | 8 |
| S51 | 5 |
| S52 | 5 |
| S53 | 8 |
| **S54** | **6** |

> **觀察**：S54 = 6 SP，落於歷史區間（5-8 SP）內；原估 5-8 SP 因前置調查確認 `isRuleApplicable` 逐日邏輯本就正確而收斂至下緣。品質：後端單元 508 tests 0 fail（含 UT-M12-009 修正 + UT-M12-019 新增）+ 真 DB 整合 415 tests 0 fail（含 API-M06-017 新增）、`make validate-schema` 無漂移（schema-free）、catch(Exception)/@Deprecated=0。**定價規則選取語意缺口收斂**——range 查詢語意正確化、同優先級行為明確化。

---

## 7. 下一步

> **檢查點**：Sprint 54 已完成（US-001~002 正式承諾 6 SP 全數完成 + US-003 不計點文件產出，本地各層驗證通過含 `make validate-schema` 無漂移 + 全量回歸 508+415 tests 0 fail）。本 Sprint commit 待檢查點徵詢後，連同累積待 push 之 S41~S54 commit 一併執行 `make validate-release` 並 push（嚴禁 `--no-verify`）。**定價規則選取語意缺口已收斂**（overlap 查詢 + tie-break 皆已落地）。下一 Sprint 候選：AI-2416 真實金流 Phase D-2 分潤（視 Phase D-1 上線進度）、AI-2409 定價計算器統一評估、AI-2415 部分退款評估、AI-1903 真人 live 走查（需環境）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
