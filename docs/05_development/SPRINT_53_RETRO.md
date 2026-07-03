# Sprint 53 Retrospective / Sprint 53 回顧會議

> **Sprint 編號**: Sprint 53
> **期間**: 2027-10-24 ~ 2027-11-06
> **回顧日期**: 2026-07-04
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 8 SP（US-001~002）|
| 完成 SP | 8 SP（全數）+ US-003/US-004 不計點附屬產出 |
| 主題 | 真實金流 Phase D-1——Stripe Connect Express 帳戶 onboarding |

---

## 2. 做得好的（What went well）

- **PO 授權下的高效範圍決策**：面對「原規劃 10 SP 高於歷史區間」的容量衝突，經 PO 明確授權後，Claude Code 直接依工程判斷下修範圍（US-003/US-004 降不計點），無需逐項往返徵詢，加快規劃到開發的轉換速度。
- **前置技術調查先行避免誤估**：Sprint 規劃前先派兩支調查 agent 分別確認 Connect 現況與定價規則現況，及早發現「AI-2407 規模被低估」「AI-1903 文件已存在」兩項認知落差，避免規劃階段就決策錯誤範圍。
- **不重複造輪子**：發現 `BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md` 已於 Sprint 41 建立後，選擇更新既有文件而非建立重複檔案，符合「精準改動」原則。
- **架構複用度高**：Connect onboarding 完全複用既有 `PaymentGateway` 抽象層 + tenant-scoped feature toggle 機制，未對既有付款閉環（Phase A/B/C）造成任何架構變動或回歸風險。
- **測試 seeding 陷阱即時發現即時修復**：`make test` 全量回歸時發現既有 raw SQL tenant 種子（`TestDatabaseInitializer`/`M16ErpIntegrationTest`）未涵蓋新增 NOT NULL 欄位，立即定位根因（`ddl-auto=update` 無 DB 端 DEFAULT）並修正，未讓問題累積到後續 Sprint。

---

## 3. 待改善的（What to improve）

- **entity 新增 NOT NULL 欄位時，應主動盤點 raw SQL 種子點**：本 Sprint 因為此問題導致額外一輪除錯（先發現整合測試失敗、才回溯到 Hibernate ddl-auto=update 與 Flyway DEFAULT 的落差）。→ 未來新增 tenants/orders 等常被 raw SQL 種子的核心表格 NOT NULL 欄位時，應在寫 migration 的同時就搜尋 `grep -rn "INSERT INTO {table}"` 一併檢查測試種子。
- **US-003/US-004 命名與既有文件的對應關係前置確認不足**：Sprint 規劃階段誤以為 AI-1903 走查腳本需要「從零撰寫」，執行時才發現 Sprint 41 已有文件。→ 未來規劃涉及「文件產出」的 US 時，應先 grep 既有 docs 目錄確認是否已有同範圍文件，而非只看 Retro 的一句話描述。
- **Phase D 拆分粒度回顧**：原始 backlog 把「分帳/提現」視為單一 AI-2413 項目，執行中才拆分為 D-1（onboarding）/D-2（分潤）。→ 未來大型金流項目規劃時，可在 spike 階段就先拆好子階段 ID（比照 Phase A/B/C 的模式），避免執行中才發現範圍過大需要拆分。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線；Transfer.create/transfer_data + 對帳/提現介面 | SD Marcus | P3 | 待評估 |
| AI-2407 | 定價規則選取語意評估（Sprint 54 主軸）| range 查詢 containment→overlap 修正 + 同優先級 tie-break 業務語意決策（需 PO/SD 先決策）| SD Marcus | P2 | Sprint 54 |
| AI-2414 | 真金流上線 checklist 端到端人工驗證 | STRIPE_PRODUCTION_CHECKLIST.md 已產出，實際測試模式端到端驗證需使用者執行 | QA Quincy + Dev David | P2 | 上線前 |
| AI-1903 | 買家閉環 live 走查（真人）| 含本 Sprint 新增真 Stripe toggle 路徑；需 live 環境 | QA Quincy | P1 | 需 live 環境 |
| AI-2415 | 部分退款評估 | partially_refunded 狀態 + 金額計算（Sprint 52 續留）| SD Marcus | P4 | 待評估 |

---

## 5. Sprint 52 → Sprint 53 Action Items 追蹤結果

| Action Item | 內容 | Sprint 53 達成狀態 |
|------------|------|---------------------|
| AI-1908 | 檢查點 push S41~S52 | ✅ 完成（確認 origin/main HEAD 已對齊 S52 收尾 commit，push 債已清償；RELEASE_TRACKER.md 過時「⏳ 待 push」狀態於本 Sprint 一併修正）|
| AI-2414 | 真金流上線 checklist | ✅ 完成（文件產出）；實際人工驗證續留 |
| AI-2413 | Phase D 分帳/提現 | 🟡 部分完成——拆分為 D-1（onboarding，✅ 本 Sprint 完成）+ D-2（分潤，續留 AI-2416）|
| AI-2407 | 定價規則語意評估 | 🟡 續留（前置調查發現規模超出小型修正，延後 Sprint 54 主軸）|
| AI-1903 | 買家閉環 live 走查（真人）| 🟡 續留（文件已更新反映真 Stripe 路徑，真人執行待 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S48 | 8 |
| S49 | 5 |
| S50 | 8 |
| S51 | 5 |
| S52 | 5 |
| **S53** | **8** |

> **觀察**：S53 = 8 SP，貼齊歷史高點但未超支（原規劃 10 SP，經範圍決策下修）。品質：後端單元新增 12（TC-S007~010 + UT-CONNECT-001~006 + UT-WH-007~008）+ 真 DB 整合 4（IT-CONNECT）+ **全量回歸 536 tests 0 fail**、validate-schema V62 無漂移、catch(Exception)/@Deprecated=0。**真實金流閉環擴展至賣家 onboarding**（付款+權威狀態+退款+Connect 帳戶開通皆真實）。Phase D-2（分潤）+ 定價語意（AI-2407）+ 上線人工驗證 + live 走查待排。

---

## 7. 下一步

> **檢查點**：Sprint 53 已完成（US-001+US-002 + US-003/US-004 不計點產出，本地各層驗證通過含 validate-schema 無漂移 + 全量回歸 536/0 fail）。**push 債已於本 Sprint 前清償**（確認 S41~S52 已同步 origin/main），本 Sprint commit 待檢查點徵詢後執行 `make validate-release` 並 push（嚴禁 --no-verify）。**真實金流閉環擴展**（付款/權威狀態/退款/Connect onboarding 皆真實），剩代收後分潤（Phase D-2）+ 上線人工驗證 + 真人 live 走查。Sprint 54 建議：**AI-2407 定價規則選取語意評估為主軸**（range 查詢修正 + tie-break 業務語意決策，需先徵詢 PO/SD 決策）；AI-2416 Phase D-2 分潤（視 Phase D-1 上線進度）；AI-2414/AI-1903 人工驗證項目（需使用者於 live/測試模式親自執行，非 Claude Code 可代辦）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
