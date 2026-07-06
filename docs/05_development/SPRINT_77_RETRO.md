# Sprint 77 Retrospective / Sprint 77 回顧會議

> **Sprint 編號**: Sprint 77
> **期間**: 2026-07-06
> **回顧日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 7 SP（US-001 1 + US-002 3 + US-003 3）|
| 完成 SP | 7 SP（全數）|
| 主題 | `NotificationService`/`NotificationTemplateService` 單元測試補齊 + 全 4 個通知相關 Service 擁有權/租戶檢查主動審視 |

---

## 2. 做得好的（What went well）

- **誠實回報「無新缺口」而非為了符合模式硬找問題**：連續 7 個 Sprint（68/70/72/73/74/75/76）都發現真實安全漏洞，本 Sprint 逐一審視 4 個 Service 共 19 個 public 方法後如實確認無新缺口，未受先前模式影響而過度解讀正常程式碼為缺陷，符合 Rule 12（大聲失敗，但也包含大聲承認「沒有失敗」）。
- **推論錯誤即時以實測釐清，不帶著懷疑往下走**：對權限字串定義的靜態分析懷疑，在協調者要求下改以背景執行既有整合測試驗證，快速且明確地推翻了錯誤推論，避免浪費後續 Sprint 時間調查一個實際上不存在的問題。
- **架構層級疑慮（`DEF-038`）正確辨識範疇並停止擴大調查**：發現 `TenantContextFilter` 的 `ADMIN` 跨租戶信任機制可能影響過去 9 個既有 DEF 修復的前提後，沒有因為發現「聽起來很嚴重」就自行擴大調查全站受影響範圍或動手修改，而是評估其超出本 Sprint（甚至超出單一模組）的範疇後，記錄清楚並交由使用者決策，符合 Rule 1（不清楚時提問而非猜測）與 Rule 3（精準改動）。
- **背景長時間指令的等待協議持續遵守**：本 Sprint `mvn test-compile`、`mvn test -Dtest=NotificationServiceTest`、`mvn test -Dtest=NotificationTemplateServiceTest`、`mvn test`（全量）、`mvn test -Dtest=M09NotificationTemplateIntegrationTest -Pintegration-test`（探測性驗證）、`make validate-schema` 皆視執行時間長短適當選擇前景/背景執行，背景執行前後皆以 `ps aux` 確認程序真實狀態，延續 Sprint 69-76 建立的良好習慣。
- **多個子 Agent 平行探查提高效率**：本 Sprint 派遣多個背景 Agent 分別探查 Controller/Consumer 呼叫鏈、ADMIN 角色定位、`Permission` enum 定義，在主線程繼續閱讀原始碼的同時平行取得多方視角，最終交叉比對出矛盾線索（`RolePermissionMapping` 註解 vs 實際程式碼慣例），催生了 `DEF-038` 的發現。

---

## 3. 待改善的（What to improve）

- **對權限字串的靜態分析推論應更早以實測驗證，而非停留在程式碼閱讀階段**：探查過程中基於 `Permission.java` enum 內容推論部分端點被權限鎖死，此推論僅靠靜態分析與多個子 Agent 交叉確認，未在第一時間執行實際測試驗證，直到協調者主動要求才用背景測試釐清。未來遇到「程式碼推論與預期行為矛盾」的情況，應更早採用「實測優先於推論」的原則，縮短懷疑停留在未驗證狀態的時間。
- **多 Sprint 測試強化計劃排程清單的模組數量持續累積**：Sprint 76 Retro 記錄的 `PromoService`/`OAuthService`/`IdempotencyService`/`FeatureToggleService` 四個待處理模組，本 Sprint（聚焦 `NotificationService`/`NotificationTemplateService`）未縮減此清單。建議 Sprint 78 規劃時評估是否需要調整排程節奏。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| （流程改善，本 Sprint 新增）| 靜態分析與預期行為矛盾時，優先以實測驗證而非停留在推論階段 | 避免重演本 Sprint 權限字串死碼推論被實測推翻、耗費多輪子 Agent 交叉確認的過程 | Dev David + QA Quincy | 🟡 中 | Sprint 78 探查階段起 |
| DEF-038 | `TenantContextFilter` 對 `ADMIN` 角色的 `X-Tenant-ID` header 無驗證信任 | 涉及 `ADMIN` 角色租戶歸屬語意的架構決策，可能影響過去 9 個既有 DEF 修復的前提，需業務/架構判斷 | PO Victoria（決策）+ SD Marcus（設計） | 🔴 高（待決策） | 待業務/架構決策 |
| （待決策，延續自 Sprint 73-77）| `ADMIN` 角色權限邊界（租戶內 vs 全域）與程式碼行為的一致性盤點 | `DEF-018/019/023/024/028/029/030/032/033/036` 皆採用「`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 同等視為全域放行」，與 `RolePermissionMapping.java` 註解「`ADMIN` 租戶內管理」不一致；`DEF-038` 的發現與此議題高度相關，建議合併評估 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待排入（建議與 `DEF-038` 一併評估） |
| （待決策，延續自 Sprint 74-76）| `DEF-034`：`CmsService.getPageBySlug`/`getActiveBanners` 公開端點租戶範圍設計 + `/v2/cms/**` 未列入 `permitAll()` | 涉及 API 簽名變更與 `SecurityConfig` 調整，需業務/架構判斷 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待業務決策 |
| （技術債，未立案，延續自 Sprint 71-76）| `RELEASE_TRACKER.md` 補齊 Sprint 74-76 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| （新觀察，本 Sprint）| `NotificationTemplateService` 的「全域模板」（`tenantId=null`）任何租戶皆可寫入，目前無實際可利用路徑 | schema 設計已預留但尚未啟用的功能空間，非活躍缺口，若未來啟用全域模板功能需重新評估寫入權限 | SD Marcus | 🟢 低 | 待排入（非緊急，功能啟用前需重新評估） |
| DEF-037 | `ShippingTemplateService.calculateFee` 跨租戶查詢 | 使用者已決策擱置，僅記錄不修復 | PO Victoria（已決策） | 🟢 低 | 已結案（擱置） |
| DEF-031 | `ReviewService.markHelpful` 重複投票、`createReview`/`BookingReviewService.createBookingReview` 未驗證訂單/訂房歸屬 | 業務規則疑點，使用者已決策擱置 | PO Victoria（決策） | 🟢 低 | 待業務規則確認 |
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 76 → Sprint 77 Action Items 追蹤結果

| Action Item | 內容 | Sprint 77 達成狀態 |
|------------|------|---------------------|
| 探查既有測試檔案時明確標註「涵蓋面向」而非僅記錄「存在測試」 | Sprint 76 記錄的流程改善建議 | ✅ 已落實：本 Sprint「1. 既有覆蓋現況」逐一標註 `NotificationHistoryService`/`NotificationPreferenceService` 既有測試涵蓋的具體案例（如「含跨使用者拒絕案例」），而非僅記錄「有測試」 |
| Sprint 77+ 排程前先做輕量探查再估點數 | Sprint 75-76 記錄的流程改善建議 | ✅ 已落實：先完整探查 4 個 Service 共 19 個方法確認範圍與規模後才估點（7 SP），探查結論與最終規劃一致 |
| `DEF-034`：`CmsService` 公開端點租戶範圍設計 | Sprint 74-76 記錄待業務決策 | 未啟動（續留，非本 Sprint 範圍）|
| `ADMIN` 角色權限邊界（租戶內 vs 全域）一致性盤點 | Sprint 73-76 記錄待排入的非緊急議題 | 部分推進：本 Sprint 探查 `TenantContextFilter` 時意外發現與此議題高度相關的具體技術細節（`DEF-038`），提供了此議題決策所需的具體證據，建議 Sprint 78 一併評估 |
| `RELEASE_TRACKER.md` 補齊 Sprint 68-76 列 | Sprint 71-76 連續記錄的技術債 | 未啟動（續留，非本 Sprint 範圍）|
| `HOST` 角色 `booking:update` 權限落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | 未啟動（續留，非本 Sprint 範圍）|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S73 | 14（`ReviewService` 測試強化 8 SP + 3 項安全問題處理 6 SP + 技術債記錄 0 SP）|
| S74 | 12（`CmsService` 測試強化 8 SP + 2 項安全問題處理 4 SP + 待決策記錄 0 SP）|
| S75 | 4（`ChatService` 探查確認無缺口 1 SP + STOMP 授權缺失修復（`DEF-035`）3 SP）|
| S76 | 4（`LogisticsService` 探查 1 SP + `DEF-036` 修復 3 SP + `DEF-037` 記錄 0 SP）|
| **S77** | **7**（4 個通知相關 Service 探查 1 SP + `NotificationService` 測試補齊 3 SP + `NotificationTemplateService` 測試補齊 3 SP，`DEF-038` 記錄 0 SP）|

> **觀察**：S77 是多 Sprint 測試強化計劃啟動以來（Sprint 66 起）首次「探查後確認無新的擁有權/租戶檢查缺口」的 Sprint，屬純測試覆蓋率補強性質，未修改任何生產程式碼。品質：`mvn test` 全量 0 fail（本 Sprint 新增 38 個：`NotificationServiceTest` 18 + `NotificationTemplateServiceTest` 20）、`make validate-schema` 無漂移。多 Sprint 測試強化計劃累計發現並修復 **12 個安全缺口**（`DEF-023/024/026/027/028/029/030/032/033/035/036`，`DEF-025/031/034/037` 為記錄擱置不計入），新增 1 個待決策架構項目（`DEF-038`，🔴 高優先級但非阻塞）。

---

## 7. 下一步

> **檢查點**：Sprint 77 已完成，準備收尾並 push。`NotificationService`/`NotificationTemplateService` 測試覆蓋率補齊，多 Sprint 測試強化計劃已完整涵蓋 Payment/Order/Booking/ERP/Review/CMS/Chat/Logistics/Notification 九大核心模組。**下一 Sprint（Sprint 78+）**建議依 `SPRINT_76_PLAN.md`/`SPRINT_77_PLAN.md` 排程：`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService` 等其餘尚未強化的模組（建議先做輕量探查再估點數），或優先處理待決策項目——尤其 `DEF-038`（`TenantContextFilter` ADMIN 跨租戶架構疑慮）與延續多個 Sprint 的「`ADMIN` 角色權限邊界盤點」議題高度相關，建議合併評估並儘早排入決策議程，因其影響範圍橫跨過去 9 個既有安全修復的前提假設。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
