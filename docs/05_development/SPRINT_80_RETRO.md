# Sprint 80 Retrospective / Sprint 80 回顧會議

> **Sprint 編號**: Sprint 80
> **期間**: 2026-07-07
> **回顧日期**: 2026-07-07
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 16 SP（US-000 2 + US-001 2 + US-002 2 + US-003 5 + US-004 2；US-005 webhook 順延）|
| 完成 SP | 13 SP（US-000~US-004 全數完成；US-005 順延至候選）|
| 主題 | AI-2416 真實金流 Phase D-2：結算單審核通過後 transfer 分潤給賣家 |

---

## 2. 做得好的（What went well）

- **規劃初稿寫完後仍主動重新調查程式碼，及時發現架構衝突**：初稿設計「逐筆訂單付款成功即轉帳」，開始寫 migration 後才因「讀寫前先閱讀」原則重新盤點 `core/settlement/` 既有程式碼，發現完整的結算單審核狀態機早已存在且 `SettlementReviewer` Javadoc 明確記載 `APPROVED→PAID` 為既定但未實作的轉換。及時停下改架構，避免了雙抽成率口徑衝突與繞過既有審核關卡兩個實質問題，而不是硬著頭皮把初稿做完再讓使用者事後發現。
- **主動應用 DEF-038 教訓，租戶隔離從第一版就做對**：`TransferService`/`TransferController` 從設計階段就寫入租戶擁有權檢查（`checkTenantAccess`）+ 對應紅燈測試（`retryFailedTransfer_crossTenantAccess_denied`），沒有等到之後才發現漏洞回頭補。
- **開發-編譯-測試循環嚴格遵守，一個檔案就編譯測試一次**：migration → entity → repository → gateway → service → controller，每一步都個別編譯確認，US-000（既有邏輯變更）更是優先完成並確認既有測試全綠後才進入後續 US。
- **不只完成任務，順手發現並誠實記錄範圍外的新缺口**：開發過程追蹤 `SettlementReviewer` 呼叫路徑時，發現 `SettlementController` 的 admin 審核端點完全沒有租戶過濾（`DEF-040`），沒有因為「不在本 Sprint 範圍」就略過不提，而是記錄進 `DEFERRED_ITEMS_TRACKER.md` 供下一輪決策，且明確指出此缺口因本 Sprint 新增的真實 transfer 功能而風險升高。
- **checkstyle-test 沿用 Sprint 79 教訓，全量回歸前主動確認**：全量回歸第一次執行時攔到 `TransferServiceTest.java` 一個未使用 import，立即修正重跑，未讓此類已知陷阱重演。

---

## 3. 待改善的（What to improve）

- **初次規劃對既有模組的調查深度不足**：Explore agent 第一輪調查時搜尋關鍵字為「Transfer/Payout/分潤/對帳」，未包含「Settlement」，導致完整的既有結算模組被漏看，規劃文件寫完才發現。未來規劃財務/金流類功能前，應主動擴大搜尋範圍至同領域的既有 domain model 目錄清單（`ls domain/model/`），而非僅憑關鍵字比對。
- **US-005（webhook `transfer.paid`/`transfer.failed` 同步）順延**：容量評估後認為核心分潤邏輯（US-000~004）優先，`Transfer.create` 呼叫成功僅代表 Stripe 已受理、非資金已實際到帳，webhook 才是最終真相來源，此缺口需在下一輪或近期補上，不可長期擱置。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| （新，本 Sprint）| US-005：`transfer.paid`/`transfer.failed` webhook 同步 | `Transfer.create` 成功僅代表 Stripe 已受理，非資金已到帳；webhook 才是最終真相來源，優先於其他候選項目 | Dev David | 🔴 高 | Sprint 81 建議優先 |
| DEF-040 | `SettlementController` admin 審核端點無租戶過濾 | Sprint 80 開發 Transfer 分潤時發現，任一租戶 ADMIN 理論上可審核他租戶結算單並觸發其資金轉移；風險因本 Sprint 新增真實 transfer 功能而升高，建議儘早排入決策議程 | PO Victoria（決策）+ SD Marcus（設計） | 🔴 高（待決策） | 待業務/架構決策 |
| （待決策，延續自 Sprint 74-80）| `DEF-034`：`CmsService` 公開端點租戶範圍設計 | 涉及 API 簽名變更與 `SecurityConfig` 調整 | PO Victoria（決策） | 🟡 中 | 待業務決策 |
| （待決策，延續自 Sprint 76-80）| `DEF-037`：`ShippingTemplateService.calculateFee` 跨租戶查詢 | 使用者已決策擱置 | PO Victoria（已決策） | 🟢 低 | 已結案（擱置） |
| （技術債，延續自 Sprint 71-80）| `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |
| （新流程建議，本 Sprint）| 規劃金流/財務類新功能前，先 `ls domain/model/` 掃過整個目錄清單確認無同領域既有模組，不只憑關鍵字搜尋 | 避免重演本 Sprint「Settlement 模組被漏看」的規劃返工 | SA Amanda | 🟡 中 | 下一輪規劃時套用 |

---

## 5. Sprint 79 → Sprint 80 Action Items 追蹤結果

| Action Item | 內容 | Sprint 80 達成狀態 |
|------------|------|---------------------|
| `DEF-038`：`TenantContextFilter` ADMIN 跨租戶架構疑慮 | Sprint 77-79 記錄的 🔴 高優先級待決策事項 | ✅ 已修復並結案（多 Sprint 測試強化計劃完成後，作為獨立追蹤任務優先處理，`resolveEffectiveTenantId` 改為僅 `SUPER_ADMIN` 可用 header 指定任意租戶）|
| 「多 Sprint 測試強化計劃」結束後聚焦待決策項目，不再新開模組測試補齊排程 | Sprint 79 記錄的流程建議 | ✅ 已落實：DEF-038 修復完成後，PO 選定 AI-2416（真實金流 Phase D-2）為下一輪主軸，非新一輪測試補齊 |
| `DEF-034`：`CmsService` 公開端點租戶範圍設計 | Sprint 74-79 記錄待業務決策 | 未啟動（續留）|
| `DEF-037` | 使用者已決策擱置 | 已結案，無變化 |
| `RELEASE_TRACKER.md` 補齊 | Sprint 71-79 連續記錄的技術債 | 未啟動（續留）|
| AI-2416 | Phase D-2 分潤 | ✅ **本 Sprint 完成**（詳見上方）|
| AI-1903 | live 走查 | 未啟動（續留，需環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S76 | 4 |
| S77 | 7 |
| S78 | 5 |
| S79 | 5 |
| **S80** | **13**（US-000 抽成口徑統一 2 + US-001 Transfer 領域模型 2 + US-002 Gateway 整合 2 + US-003 TransferService 核心邏輯 5 + US-004 Controller API 2；US-005 webhook 順延）|

---

## 7. 下一步

> **檢查點**：Sprint 80 已完成，準備收尾並徵詢使用者同意後 push（本 Sprint 涉及真實金錢移動邏輯 + 修改既有生產抽成計算，不比照一般 Sprint 自動 push）。**下一輪規劃建議**：
> 1. **US-005 webhook 同步**（🔴 高優先級）——`Transfer.create` 成功不代表資金已到帳，這是本 Sprint 刻意順延但不可長期擱置的缺口。
> 2. **DEF-040**（🔴 高優先級，`SettlementController` admin 審核端點無租戶過濾）——本 Sprint 新增的真實 transfer 功能使此缺口的潛在風險從「查看他租戶資料」升級為「觸發他租戶資金轉移」，建議與 DEF-038 同等重視，儘早排入決策議程。
> 3. `DEF-034`/`RELEASE_TRACKER.md` 補齊等既有待決策/技術債項目續留。

---

**文件版本**: v1.0
**建立日期**: 2026-07-07
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
