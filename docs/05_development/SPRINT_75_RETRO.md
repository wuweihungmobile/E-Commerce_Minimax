# Sprint 75 Retrospective / Sprint 75 回顧會議

> **Sprint 編號**: Sprint 75
> **期間**: 2026-07-06
> **回顧日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 4 SP（US-001 1 + US-002 3）|
| 完成 SP | 4 SP（全數）|
| 主題 | `ChatService` 範圍探查確認既有覆蓋完整 + STOMP SUBSCRIBE 授權缺失修復（`DEF-035`，新發現） |

---

## 2. 做得好的（What went well）

- **探查優先、不預設有缺口**：延續「先探查、後動手」的紀律，本 Sprint 探查後誠實得出「`ChatService` 本身已無缺口」的結論，未為了維持「每個 Sprint 都要找到並修一個漏洞」的慣性而勉強套用既有修復模式，正確識別 6 個方法皆已透過 `findByIdAndUserId` 做好 participant-scoping。
- **依指示延伸檢查範圍，未侷限於單一類別**：使用者特別提醒 `ChatService` 涉及 WebSocket/STOMP，本 Sprint 據此主動延伸檢查 `WebSocketConfig`/`StompAuthChannelInterceptor`，發現先前 5 個 Sprint（68/70/72/73/74）都未觸及的新型態缺口——不是 Service 方法本身少了租戶檢查，而是訊息代理層的 SUBSCRIBE 完全沒有授權機制，證明「主動審視」的紀律需要涵蓋目標 Service 的完整依賴鏈，而非僅止於該 Service 類別本身。
- **紅燈測試設計預先考慮修復後的短路路徑**：撰寫紅燈測試時已預期修復後「未認證案例會在取得 userId 階段短路，不會查到 repository」，測試轉綠後只需移除 1 個變多餘的 stub 即完成，未發生大幅來回修正。
- **修復範圍精準，抵抗「順便多修一點」的誘惑**：探查過程中另外觀察到 CONNECT 對缺失/無效 JWT 仍放行連線的設計，判斷本次 SUBSCRIBE 層授權檢查已足以完整阻斷風險後，明確選擇不擴大修改 CONNECT 邏輯，符合 Rule 3（精準改動）。
- **背景長時間指令的等待協議持續遵守**：本 Sprint 首次遇到「新增建構子依賴後尚未加邏輯」的中間態需要驗證，`mvn test`（紅燈/綠燈各一次）、`mvn verify -Pintegration-test`、`make validate-schema` 皆以背景執行並回報 log 路徑，且每次回報前都先以 `ps aux`/`ps -p` 確認程序真實狀態，延續 Sprint 69-74 建立的良好習慣。

---

## 3. 待改善的（What to improve）

- **多 Sprint 測試強化計劃的「每 Sprint 補測試」預設可能需要調整**：本 Sprint 是計劃執行以來首次出現「目標 Service 不需要新增測試」的情況，顯示先前 Sprint 排程（`SPRINT_73_PLAN.md`/`SPRINT_74_PLAN.md` §7）預設每個 Service 都需要類似規模的測試補強工作量，未來規劃 Sprint 76+ 清單中的 `LogisticsService`/`NotificationService` 等模組時，應先做輕量探查再估點數，避免 SP 估計失準。
- **STOMP/WebSocket 授權檢查應納入未來新功能的標準檢查項**：`DEF-035` 屬於「新功能（Phase 2 STOMP 廣播）忘記加訂閱端授權」的模式，與先前 Sprint 發現的「新功能忘記加租戶/擁有權檢查」屬同一系統性問題但發生在不同層級（訊息代理而非 Service 方法）。建議未來設計任何新的 STOMP `@MessageMapping`/訂閱目的地時，在設計階段就將「SUBSCRIBE 時是否驗證訂閱者對目的地資源的存取權」列為標準檢查項，而非仰賴事後測試強化 Sprint 才發現。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| （流程改善，未立案）| Sprint 76+ 排程前先做輕量探查再估點數 | 避免重演本 Sprint「預估 8 SP 量級、實際僅 4 SP」的估點落差 | PM Victoria + SA Amanda | 🟡 中 | Sprint 76 規劃時 |
| （流程改善，未立案）| STOMP/WebSocket 新訂閱目的地的授權檢查納入設計階段標準檢查項 | 避免重演 `DEF-035`「REST 層有檢查、訊息代理層沒有」的落差 | SD Marcus + Dev David | 🟡 中 | 下次新增 STOMP 功能時 |
| （待決策，延續自 Sprint 74）| `DEF-034`：`CmsService.getPageBySlug`/`getActiveBanners` 公開端點租戶範圍設計 + `/v2/cms/**` 未列入 `permitAll()` | 涉及 API 簽名變更與 `SecurityConfig` 調整，需業務/架構判斷 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待業務決策 |
| （待決策，延續自 Sprint 73-74）| `ADMIN` 角色權限邊界（租戶內 vs 全域）與程式碼行為的一致性盤點 | `DEF-018/019/023/024/028/029/030/032/033` 皆採用「`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 同等視為全域放行」，與 `RolePermissionMapping.java` 註解「`ADMIN` 租戶內管理」不一致 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待排入（非緊急） |
| （技術債，未立案，延續自 Sprint 71-74）| `RELEASE_TRACKER.md` 補齊 Sprint 68-74 列 | 已連續 5 個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| DEF-031 | `ReviewService.markHelpful` 重複投票、`createReview`/`BookingReviewService.createBookingReview` 未驗證訂單/訂房歸屬 | 業務規則疑點，使用者已決策擱置 | PO Victoria（決策） | 🟢 低 | 待業務規則確認 |
| （待決策，延續自 Sprint 71-74）| `HOST` 角色 `booking:update` 權限與 `updateBooking` 修復後行為落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待決策（未變更，非本 Sprint 範圍）|
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 74 → Sprint 75 Action Items 追蹤結果

| Action Item | 內容 | Sprint 75 達成狀態 |
|------------|------|---------------------|
| 新增測試檔案後、執行完整驗證前先自行跑 `mvn checkstyle:check` | Sprint 74 記錄的流程改善建議 | ✅ 本 Sprint 未觸發同類問題（`StompAuthChannelInterceptorTest.java` import 皆有實際使用），無需驗證此建議是否落實 |
| 探查「(公開)」端點時，將「是否真的列入 `SecurityConfig` `permitAll()`」納入標準檢查項 | Sprint 74 記錄的流程改善建議 | 不適用（本 Sprint 未探查公開端點類型的方法） |
| `DEF-034`：`CmsService` 公開端點租戶範圍設計 | Sprint 74 記錄待業務決策 | 未啟動（續留，非本 Sprint 範圍） |
| `ADMIN` 角色權限邊界（租戶內 vs 全域）一致性盤點 | Sprint 73-74 記錄待排入的非緊急議題 | 未啟動（續留，非本 Sprint 範圍，本 Sprint 沿用既有慣例） |
| `RELEASE_TRACKER.md` 補齊 Sprint 68-73 列 | Sprint 71-74 連續記錄的技術債 | 未啟動（續留，非本 Sprint 範圍）|
| `HOST` 角色 `booking:update` 權限落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | 未啟動（續留，非本 Sprint 範圍）|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S71 | 8（恢復例行測試強化排程節奏）|
| S72 | 13（ERP 模組測試強化 8 SP + 2 項安全問題處理 5 SP）|
| S73 | 14（`ReviewService` 測試強化 8 SP + 3 項安全問題處理 6 SP + 技術債記錄 0 SP）|
| S74 | 12（`CmsService` 測試強化 8 SP + 2 項安全問題處理 4 SP + 待決策記錄 0 SP）|
| **S75** | **4**（`ChatService` 探查確認無缺口 1 SP + STOMP 授權缺失修復（`DEF-035`）3 SP）|

> **觀察**：S75 明顯低於 S71-S74，因 `ChatService` 本身經探查後確認已無缺口、無需重複補測試，實際工作量僅為 STOMP 層單一新缺口的修復，與 S70（3 SP，單一緊急安全修復）量級相近。品質：全量回歸 **1083 tests 0 fail**（本 Sprint 新增 6 個）、`make validate-schema` 無漂移。**多 Sprint 測試強化計劃首次出現「目標 Service 本身無缺口」的結果**，證明先前 5 個連續 Sprint（68/70/72/73/74）發現真實漏洞並非審視方法本身的偏誤，而是逐案例判斷的結果；本 Sprint 同時驗證「審視範圍應涵蓋 Service 的完整依賴鏈（含訊息代理層）」的必要性。活躍高優先級延後項目維持 0（`DEF-035` 已修復並直接結案，未進入活躍延後清單）。

---

## 7. 下一步

> **檢查點**：Sprint 75 已完成，準備收尾並 push。`ChatService` 探查與 STOMP 授權缺失清償告一段落，多 Sprint 測試強化計劃已完整涵蓋 Payment/Order/Booking/ERP/Review/CMS/Chat 七大核心模組。**下一 Sprint（Sprint 76+）**建議依 `SPRINT_74_PLAN.md`/`SPRINT_75_PLAN.md` 排程：`LogisticsService`、`NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService` 等其餘尚未強化的模組（建議先做輕量探查再估點數），或優先處理待決策項目（`DEF-034` 公開端點租戶範圍設計、`ADMIN` 角色權限邊界盤點）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
