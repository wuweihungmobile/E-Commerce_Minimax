# Sprint 74 Retrospective / Sprint 74 回顧會議

> **Sprint 編號**: Sprint 74
> **期間**: 2026-07-06
> **回顧日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 12 SP（US-001 8 + US-002 2 + US-003 2 + US-004 0）|
| 完成 SP | 12 SP（全數）|
| 主題 | `CmsService` 11 個零覆蓋方法單元測試從零建立 + 2 項跨租戶問題修復（`DEF-032`/`DEF-033`）+ 1 項待決策事項記錄（`DEF-034`） |

---

## 2. 做得好的（What went well）

- **主動審視擁有權/租戶檢查的紀律延續到第二個 Sprint**：延續 Sprint 73 建立的方法，本 Sprint 一開始就以「這個方法允許誰呼叫、有沒有檢查資源是否屬於呼叫者/當前租戶」的角度逐一審視 `CmsService` 全部 11 個方法，一次揭露 2 項安全缺口，且都在動手寫測試之前就先完成分析。
- **正確識別「需業務判斷」與「有清楚前例可循」的邊界**：`updatePage`/`publishPage`/`updateBanner`/`publishBanner`（`DEF-032`）與 `getPages`/`getBanners`（`DEF-033`）皆有 `DEF-019/024/026/028/029` 等清楚前例可直接比照修復；而 `getPageBySlug`/`getActiveBanners` 的公開端點租戶範圍問題（`DEF-034`）因涉及 API 簽名變更、且額外發現與 `SecurityConfig` 的不一致，正確判斷為需業務判斷而非自行假設修復方案，記錄後停下等待確認，未強行套用既有模式。
- **紅燈→綠燈驗證流程比 Sprint 73 更乾淨**：本 Sprint 使用 `git stash`/`git stash pop` 精確地將生產程式碼暫時還原到修復前狀態執行紅燈測試，再還原修復後確認轉綠，6 個紅燈測試的失敗訊息（4 個 AssertionError + 2 個 NullPointerException）皆乾淨對應漏洞本質，未重演 Sprint 73 因 fixture 缺陷（`reviewType` 遺漏、stub 誤用）導致的來回修正。
- **意外發現並誠實揭露 `SecurityConfig` 與程式碼註解不一致**：探查 `DEF-034` 過程中意外發現 `/v2/cms/**` 端點雖註解「(公開)」，實際上並未列入 `SecurityConfig` 的 `permitAll()` 清單，此為原定範圍外的額外發現，已誠實記錄於 `DEF-034` 而非略過。
- **checkstyle 問題被協作即時攔截並修正**：全量回歸首次執行時因未使用的 `import` 被 checkstyle-test 攔截（與 Sprint 66 `AuthServiceTest` 同類問題），發現後立即移除、重新編譯、重新執行完整驗證流程（含重跑全量回歸），未略過或繞過檢查。
- **背景長時間指令的等待協議持續遵守**：`mvn test`（紅綠燈驗證）、`mvn verify -Pintegration-test`（執行 2 次）、`make validate-schema` 皆以背景或前景快速執行並回報 log 路徑，延續 Sprint 69-73 建立的良好習慣。

---

## 3. 待改善的（What to improve）

- **`CmsServiceTest.java` 初版未在撰寫階段自我檢查 import 使用情況**：Sprint 66 已發生過同類 checkstyle 未使用 import 問題，本 Sprint 仍重演，顯示「撰寫測試檔案後、提交驗證前，先自行檢查 import 清單是否皆有實際使用」尚未成為固定習慣，建議納入開發-編譯-測試循環的標準檢查項之一（例如撰寫完測試檔案後先執行一次 `mvn checkstyle:check` 而非只跑到 `mvn test`）。
- **探查階段對 `SecurityConfig`/`permitAll()` 清單的檢查應更早納入標準流程**：本 Sprint 是先探查「查詢邏輯有無租戶過濾」才意外發現 `SecurityConfig` 的不一致，若未來探查任何標記「(公開)」的方法時，能將「該端點是否真的在 `permitAll()` 清單中」列為標準檢查項之一，可更早發現類似落差。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| （流程改善，未立案）| 新增測試檔案後、執行完整驗證前先自行跑 `mvn checkstyle:check` | 避免未使用 import 等 checkstyle 問題到全量回歸才被攔截，減少重跑成本 | Dev David | 🟡 中 | 下個 Sprint 起 |
| （流程改善，未立案）| 探查「(公開)」端點時，將「是否真的列入 `SecurityConfig` `permitAll()`」納入標準檢查項 | 本 Sprint 意外發現 `/v2/cms/**` 註解與實際設定不一致（`DEF-034` 附帶發現） | SA Amanda + Dev David | 🟢 低 | 下次探查公開端點時 |
| DEF-034 | `CmsService.getPageBySlug`/`getActiveBanners` 公開端點租戶範圍設計 + `/v2/cms/**` 未列入 `permitAll()` | 涉及 API 簽名變更（是否比照 `PostController` 要求 `tenantId`）與 `SecurityConfig` 調整，需業務/架構判斷 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待業務決策 |
| （待決策，延續自 Sprint 73）| `ADMIN` 角色權限邊界（租戶內 vs 全域）與程式碼行為的一致性盤點 | `DEF-018/019/023/024/028/029/030/032/033` 皆採用「`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 同等視為全域放行」，與 `RolePermissionMapping.java` 註解「`ADMIN` 租戶內管理」不一致 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待排入（非緊急） |
| （技術債，未立案，延續自 Sprint 71-73）| `RELEASE_TRACKER.md` 補齊 Sprint 68-73 列 | 已連續 4 個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| DEF-031 | `ReviewService.markHelpful` 重複投票、`createReview`/`BookingReviewService.createBookingReview` 未驗證訂單/訂房歸屬 | 業務規則疑點，使用者已決策擱置 | PO Victoria（決策） | 🟢 低 | 待業務規則確認 |
| （待決策，延續自 Sprint 71-73）| `HOST` 角色 `booking:update` 權限與 `updateBooking` 修復後行為落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待決策（未變更，非本 Sprint 範圍）|
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 73 → Sprint 74 Action Items 追蹤結果

| Action Item | 內容 | Sprint 74 達成狀態 |
|------------|------|---------------------|
| 紅燈測試撰寫前先明確寫下修復前後預期執行路徑與對應 stub | Sprint 73 記錄的流程改善建議 | ✅ **本 Sprint 落實**：紅燈測試撰寫時已預先確認「修復前會走到 `save()`/後續查詢」與「修復後會在檢查點提前拋例外」兩種路徑，未重演 Sprint 73 的來回修正 |
| `ADMIN` 角色權限邊界（租戶內 vs 全域）一致性盤點 | Sprint 73 記錄待排入的非緊急議題 | 未啟動（續留，非本 Sprint 範圍，本 Sprint 沿用既有慣例） |
| `RELEASE_TRACKER.md` 補齊 Sprint 68-70 列 | Sprint 71-73 連續記錄的技術債 | 未啟動（續留，非本 Sprint 範圍）|
| `HOST` 角色 `booking:update` 權限落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | 未啟動（續留，非本 Sprint 範圍）|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S70 | 3（緊急安全修復插隊，不計入例行測試強化排程節奏）|
| S71 | 8（恢復例行測試強化排程節奏）|
| S72 | 13（ERP 模組測試強化 8 SP + 2 項安全問題處理 5 SP）|
| S73 | 14（`ReviewService` 測試強化 8 SP + 3 項安全問題處理 6 SP + 技術債記錄 0 SP）|
| **S74** | **12**（`CmsService` 測試強化 8 SP + 2 項安全問題處理 4 SP + 待決策記錄 0 SP）|

> **觀察**：S74 略低於 S72/S73，因本 Sprint 僅確認 2 項可直接修復的安全缺口（`DEF-032`/`DEF-033`，共 4 SP），第 3 項（`DEF-034`）因涉及 API 簽名/`SecurityConfig` 變更等業務判斷正確地未強行併入修復範圍，僅記錄。品質：全量回歸 **1085 tests 0 fail**（本 Sprint 新增 27 個）、`make validate-schema` 無漂移。**`CmsService` 單元測試缺口清零**——多 Sprint 測試強化計劃中首個「全數方法原本零覆蓋」的 Service，全部 11 個方法皆已有對應單元測試覆蓋；同時清償 2 項跨租戶安全缺口，延續 Sprint 68/70/72/73 建立的「主動審視、先驗證再修復」紀律。活躍高優先級延後項目維持 0（`DEF-032`/`DEF-033` 皆已修復並直接結案，未進入活躍延後清單；`DEF-034` 為中優先級待決策，非高優先級活躍缺口）。

---

## 7. 下一步

> **檢查點**：Sprint 74 已完成，準備收尾並 push。`CmsService` 測試強化與 2 項安全缺口清償告一段落，多 Sprint 測試強化計劃已完整涵蓋 Payment/Order/Booking/ERP/Review/CMS 六大核心模組。**下一 Sprint（Sprint 75+）**建議依 `SPRINT_73_PLAN.md`/`SPRINT_74_PLAN.md` 排程：`ChatService`、`LogisticsService`、`NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService` 等其餘尚未強化的模組，或優先處理本 Sprint 新增的待決策項目（`DEF-034` 公開端點租戶範圍設計 + `SecurityConfig` 落差、`ADMIN` 角色權限邊界盤點）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
