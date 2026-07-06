# Sprint 76 Retrospective / Sprint 76 回顧會議

> **Sprint 編號**: Sprint 76
> **期間**: 2026-07-06
> **回顧日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 4 SP（US-001 1 + US-002 3）|
| 完成 SP | 4 SP（全數）|
| 主題 | `LogisticsService` 範圍探查 + 除 `createLogistics` 外其餘 6 個方法的租戶擁有權檢查修復（`DEF-036`，新發現）|

---

## 2. 做得好的（What went well）

- **不因單一方法已修復就跳過同檔案其餘方法**：使用者任務指派時特別提醒「`checkOrderTenant` 已存在不代表全部方法都安全」，本 Sprint 確實逐一列出 `LogisticsService` 全部 7 個 public 方法檢查，而非僅確認 `createLogistics` 有防護就結案，精準命中先前 40 個 Sprint（Sprint 36 `DEF-019` 修復 → Sprint 76）都未觸及的缺口。
- **紅燈測試用實際還原程式碼證明，而非憑空假設**：撰寫 `LogisticsServiceTenantAccessTest` 後，用 `git stash` 暫時將 `LogisticsService.java` 還原至修復前版本，實際執行測試觀察到 5 個跨租戶案例確實未被攔截、3 個成功案例觸發 `UnnecessaryStubbingException`，兩種失敗型態都精準對應「修復前完全沒有攔截」的漏洞本質，確保紅燈是「真紅」。
- **修復範圍精準，抵抗「順便修正 `E_7000` 誤用」的誘惑**：探查中另外發現 `getLogistics`/`trackLogistics`/`getTrackingDetail`/`updateLogisticsStatus` 仍沿用語意錯誤的 `E_7000`（官方定義為「找不到供應商」）而非 `DEF-011` 已修正的 `E_7500`，判斷此為錯誤碼語意問題而非本 Sprint 鎖定的擁有權範圍後，明確選擇僅記錄觀察、不擴大修改，符合 Rule 3（精準改動）。
- **附帶檢查發現的低風險項目正確走「記錄不修復」路徑**：`ShippingTemplateService.calculateFee` 的跨租戶查詢缺口因涉及「是否要支援買家跨租戶比價試算」的業務判斷，依規範記入 `DEFERRED_ITEMS_TRACKER.md`（`DEF-037`）並停下確認，而非自行假設答案直接修復或直接忽略，使用者確認後正式擱置。
- **背景長時間指令的等待協議持續遵守**：本 Sprint `mvn test`（紅燈/綠燈）、`mvn verify -Pintegration-test`（26 分鐘）、`make validate-schema` 皆以背景執行並回報 log 路徑，每次回報前都先以 `ps aux` 確認程序真實狀態，延續 Sprint 69-75 建立的良好習慣；`mvn verify -Pintegration-test` 完成後由協調者輪詢確認 **BUILD SUCCESS，1091 tests 0 fail** 才繼續收尾流程。

---

## 3. 待改善的（What to improve）

- **既有測試「有測試」不等於「測試完整」的落差需要更早發現**：`LogisticsServiceCancelTest`（`DEF-011`）看起來像是 `cancelLogistics` 已有完整測試覆蓋，但實際上只涵蓋錯誤碼行為、完全未涵蓋擁有權面向。未來探查既有測試檔案時，應明確標註「此測試涵蓋的面向」而非僅以「有測試檔案存在」判斷方法已受良好保護，避免產生錯誤的安全感。
- **多 Sprint 測試強化計劃排程清單的模組數量持續累積**：Sprint 75 Retro 已記錄 `NotificationService`/`PromoService`/`OAuthService`/`IdempotencyService`/`FeatureToggleService`/`NotificationTemplateService` 六個待處理模組，本 Sprint 未縮減此清單（`LogisticsService` 為 Sprint 75 排定項目，非新增）。建議 Sprint 77 規劃時評估是否需要調整排程節奏或分批並行處理，避免待處理清單無限累積。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| （流程改善，未立案）| 探查既有測試檔案時明確標註「涵蓋面向」而非僅記錄「存在測試」| 避免重演本 Sprint `LogisticsServiceCancelTest` 看似完整、實際遺漏擁有權面向的落差 | SA Amanda + QA Quincy | 🟡 中 | Sprint 77 探查階段起 |
| （流程改善，延續自 Sprint 75）| Sprint 77+ 排程前先做輕量探查再估點數 | 避免估點落差 | PM Victoria + SA Amanda | 🟡 中 | Sprint 77 規劃時 |
| DEF-037 | `ShippingTemplateService.calculateFee` 跨租戶查詢 | 使用者已決策擱置，僅記錄不修復 | PO Victoria（已決策） | 🟢 低 | 已結案（擱置） |
| （待決策，延續自 Sprint 74-75）| `DEF-034`：`CmsService.getPageBySlug`/`getActiveBanners` 公開端點租戶範圍設計 + `/v2/cms/**` 未列入 `permitAll()` | 涉及 API 簽名變更與 `SecurityConfig` 調整，需業務/架構判斷 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待業務決策 |
| （待決策，延續自 Sprint 73-75）| `ADMIN` 角色權限邊界（租戶內 vs 全域）與程式碼行為的一致性盤點 | `DEF-018/019/023/024/028/029/030/032/033/036` 皆採用「`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 同等視為全域放行」，與 `RolePermissionMapping.java` 註解「`ADMIN` 租戶內管理」不一致 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待排入（非緊急） |
| （技術債，未立案，延續自 Sprint 71-75）| `RELEASE_TRACKER.md` 補齊 Sprint 68-76 列 | 已連續 6 個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| （新觀察，本 Sprint）| `LogisticsService`/`getLogistics`/`trackLogistics`/`getTrackingDetail`/`updateLogisticsStatus` 仍用語意錯誤的 `E_7000`（供應商）而非 `E_7500`（物流） | 與 `DEF-011` 同一類錯誤碼誤用問題，非安全缺口 | Dev David | 🟢 低 | 待排入（非緊急，非本計劃範圍） |
| DEF-031 | `ReviewService.markHelpful` 重複投票、`createReview`/`BookingReviewService.createBookingReview` 未驗證訂單/訂房歸屬 | 業務規則疑點，使用者已決策擱置 | PO Victoria（決策） | 🟢 低 | 待業務規則確認 |
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 75 → Sprint 76 Action Items 追蹤結果

| Action Item | 內容 | Sprint 76 達成狀態 |
|------------|------|---------------------|
| Sprint 76+ 排程前先做輕量探查再估點數 | Sprint 75 記錄的流程改善建議 | ✅ 已落實：先完整探查 `LogisticsService`（+附帶 `ShippingTemplateService`）確認缺口範圍與規模後才估點（4 SP），與探查結論一致 |
| STOMP/WebSocket 新訂閱目的地的授權檢查納入設計階段標準檢查項 | Sprint 75 記錄的流程改善建議 | 不適用（本 Sprint 未涉及 STOMP/WebSocket 功能）|
| `DEF-034`：`CmsService` 公開端點租戶範圍設計 | Sprint 74-75 記錄待業務決策 | 未啟動（續留，非本 Sprint 範圍）|
| `ADMIN` 角色權限邊界（租戶內 vs 全域）一致性盤點 | Sprint 73-75 記錄待排入的非緊急議題 | 未啟動（續留，本 Sprint 沿用既有慣例，`DEF-036` 修復同樣採用「本租戶 or admin 全域放行」模式）|
| `RELEASE_TRACKER.md` 補齊 Sprint 68-74 列 | Sprint 71-75 連續記錄的技術債 | 未啟動（續留，非本 Sprint 範圍）|
| `HOST` 角色 `booking:update` 權限落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | 未啟動（續留，非本 Sprint 範圍）|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S72 | 13（ERP 模組測試強化 8 SP + 2 項安全問題處理 5 SP）|
| S73 | 14（`ReviewService` 測試強化 8 SP + 3 項安全問題處理 6 SP + 技術債記錄 0 SP）|
| S74 | 12（`CmsService` 測試強化 8 SP + 2 項安全問題處理 4 SP + 待決策記錄 0 SP）|
| S75 | 4（`ChatService` 探查確認無缺口 1 SP + STOMP 授權缺失修復（`DEF-035`）3 SP）|
| **S76** | **4**（`LogisticsService` 探查 1 SP + `DEF-036` 修復 3 SP + `DEF-037` 記錄 0 SP）|

> **觀察**：S76 與 S75 同為 4 SP 量級，皆屬「探查範圍集中、修復模式有清楚前例」的情況——`DEF-036` 直接沿用 `DEF-019`/`DEF-024`/`DEF-028`/`DEF-032` 已驗證的 `checkOrderTenant`/tenant-based 修復模式，套用到 6 個呼叫點。品質：全量回歸 **1091 tests 0 fail**（本 Sprint 新增 8 個 + 更新既有 3 個）、`make validate-schema` 無漂移。多 Sprint 測試強化計劃累計發現並修復 **12 個安全缺口**（`DEF-023/024/026/027/028/029/030/032/033/035/036`，DEF-025/031/034/037 為記錄擱置不計入）。活躍高優先級延後項目維持 0（`DEF-036` 已修復並直接結案）。

---

## 7. 下一步

> **檢查點**：Sprint 76 已完成，準備收尾並 push。`LogisticsService` 測試強化與擁有權缺口清償告一段落，多 Sprint 測試強化計劃已完整涵蓋 Payment/Order/Booking/ERP/Review/CMS/Chat/Logistics 八大核心模組。**下一 Sprint（Sprint 77+）**建議依 `SPRINT_75_PLAN.md`/`SPRINT_76_PLAN.md` 排程：`NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService` 等其餘尚未強化的模組（建議先做輕量探查再估點數），或優先處理待決策項目（`DEF-034` 公開端點租戶範圍設計、`ADMIN` 角色權限邊界盤點）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
