# Sprint 78 Retrospective / Sprint 78 回顧會議

> **Sprint 編號**: Sprint 78
> **期間**: 2026-07-06
> **回顧日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 5 SP（US-001 1 + US-002 2 + US-003 2）|
| 完成 SP | 5 SP（全數）|
| 主題 | `OAuthService`/`PromoService` 單元測試補齊 + 擁有權/租戶檢查與 OAuth 特有安全考量主動審視 |

---

## 2. 做得好的（What went well）

- **發現規模遠小於預期後即時調整策略，未硬套原定計劃**：`OAuthService` 探查後發現核心邏輯（`exchangeCodeForUserInfo`）是無條件拋例外的 stub，沒有勉強比照過去 Sprint（如 Sprint 77 補 38 個測試）的規模硬寫測試，而是誠實評估「目前只能測到 fail-closed 行為」，並把節省下來的時間用於一併完成 `PromoService`，符合 Rule 2（簡潔優先）與任務指示「規模與過去 Sprint 相當且問題有前例可循可直接動手，不需每次都停下來等待回覆」。
- **識別死碼並找到既有前例佐證非新發現**：透過 `grep` 全庫搜尋確認 `TECHNICAL_DEBT_TODO_SCAN.md`/`PRODUCT_BACKLOG.md` 已記錄此 stub 現況，並比對 `OrderServiceTest.createBooking_validDates_throwsUnsupported`（Sprint 69 對 `RoomBookingService` 同類 stub 的既有測試模式）確認這是本專案已有慣例的處理方式，而非需要自行發明新規則，符合 Rule 11（配合程式碼庫慣例）。
- **誠實回報「無新缺口」，連續第 2 個 Sprint 維持該結論**：延續 Sprint 77 的誠實回報精神，未因「已經連續一個 Sprint 沒發現問題」而產生要找出問題的壓力去過度解讀正常程式碼。
- **環境問題與程式碼迴歸正確區分**：`mvn test`（未啟動 test DB）出現 3 個 `SellerDashboardServiceCacheTest` Error 時，透過分析錯誤訊息（`Failed to load ApplicationContext`，與 `OAuthService`/`PromoService` 完全無關的模組）快速判斷為已知環境陷阱，`make test-db-up` 後重跑確認 0 fail，未誤判為本 Sprint 迴歸而浪費時間排查不存在的問題。
- **背景長時間指令的等待協議持續遵守**：`mvn test`（單檔）、`mvn test`（全量，兩輪）、`make validate-schema` 皆視執行時間長短適當選擇前景/背景執行，背景執行前後皆以 `ps aux` 確認程序真實狀態，延續 Sprint 69-77 建立的良好習慣。

---

## 3. 待改善的（What to improve）

- **測試撰寫前若能更早搜尋技術債紀錄，可少走一輪確認流程**：探查 `OAuthService` 時是在讀完程式碼、發現 `UnsupportedOperationException` 後才回頭搜尋 `TECHNICAL_DEBT_TODO_SCAN.md`/`PRODUCT_BACKLOG.md` 確認是否為已知現況。未來遇到程式碼中出現 `throw new UnsupportedOperationException(...)`/`// 模擬實現` 之類的明顯 stub 標記時，可以把「搜尋既有技術債文件」提前到探查階段的固定步驟，而非讀完程式碼才想到查證。
- **多 Sprint 測試強化計劃排程清單持續縮小，但尚未規劃收斂節奏**：本 Sprint 完成後剩餘模組僅剩 `IdempotencyService`、`FeatureToggleService` 兩個，建議 Sprint 79 規劃時一併評估這兩個模組是否可合併為單一 Sprint 完成，並開始規劃「多 Sprint 測試強化計劃」整體收尾的時間點。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| （流程改善，本 Sprint 新增）| 探查階段固定加入「搜尋既有技術債/backlog 文件確認現況是否已知」步驟 | 避免像本 Sprint 一樣讀完程式碼才回頭查證 stub 是否為已知現況 | Dev David + QA Quincy | 🟢 低 | Sprint 79 探查階段起 |
| （新觀察，本 Sprint）| 多 Sprint 測試強化計劃剩餘模組僅剩 2 個（`IdempotencyService`/`FeatureToggleService`），建議規劃合併完成與整體收尾時間點 | 排程收斂節奏規劃 | PM Victoria | 🟡 中 | Sprint 79 規劃 |
| DEF-038 | `TenantContextFilter` 對 `ADMIN` 角色的 `X-Tenant-ID` header 無驗證信任 | 涉及 `ADMIN` 角色租戶歸屬語意的架構決策，可能影響過去 9 個既有 DEF 修復的前提，需業務/架構判斷；🔴 高優先級，建議儘早排入決策議程 | PO Victoria（決策）+ SD Marcus（設計） | 🔴 高（待決策） | 待業務/架構決策 |
| （待決策，延續自 Sprint 73-77）| `ADMIN` 角色權限邊界（租戶內 vs 全域）與程式碼行為的一致性盤點 | 與 `DEF-038` 高度相關，建議合併評估 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待排入（建議與 `DEF-038` 一併評估） |
| （待決策，延續自 Sprint 74-77）| `DEF-034`：`CmsService.getPageBySlug`/`getActiveBanners` 公開端點租戶範圍設計 + `/v2/cms/**` 未列入 `permitAll()` | 涉及 API 簽名變更與 `SecurityConfig` 調整，需業務/架構判斷 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待業務決策 |
| （技術債，未立案，延續自 Sprint 71-77）| `RELEASE_TRACKER.md` 補齊 Sprint 74-78 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| （新觀察，延續自 Sprint 77）| `OAuthDto.AuthRequest`/`LinkRequest` 缺少 `state` 欄位（CSRF 防護） | 待未來實際串接 OAuth Provider API 時一併補上，目前功能未實作故無實際風險 | SD Marcus | 🟢 低 | 待排入（OAuth 整合實作時） |
| DEF-037 | `ShippingTemplateService.calculateFee` 跨租戶查詢 | 使用者已決策擱置，僅記錄不修復 | PO Victoria（已決策） | 🟢 低 | 已結案（擱置） |
| DEF-031 | `ReviewService.markHelpful` 重複投票、`createReview`/`BookingReviewService.createBookingReview` 未驗證訂單/訂房歸屬 | 業務規則疑點，使用者已決策擱置 | PO Victoria（決策） | 🟢 低 | 待業務規則確認 |
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 77 → Sprint 78 Action Items 追蹤結果

| Action Item | 內容 | Sprint 78 達成狀態 |
|------------|------|---------------------|
| 靜態分析與預期行為矛盾時，優先以實測驗證而非停留在推論階段 | Sprint 77 記錄的流程改善建議 | ✅ 已落實：`OAuthService` 探查中對 stub 現況的判斷，直接以背景執行 `OAuthServiceTest`/`PromoServiceTest`（含全量 `mvn test`）實測驗證 fail-closed 行為與無副作用，未停留在單純程式碼閱讀推論 |
| 多 Sprint 測試強化計劃排程清單的模組數量持續累積 | Sprint 77 記錄的觀察 | ✅ 已縮減：本 Sprint 完成 `OAuthService` + `PromoService` 2 個模組，剩餘清單由 4 個縮減為 2 個（`IdempotencyService`/`FeatureToggleService`）|
| `DEF-038`：`TenantContextFilter` ADMIN 跨租戶架構疑慮 | Sprint 77 記錄待業務/架構決策 | 未啟動（續留，非本 Sprint 範圍，本 Sprint 未涉及 `TenantContext`/`TenantContextFilter` 相關程式碼）|
| `ADMIN` 角色權限邊界（租戶內 vs 全域）一致性盤點 | Sprint 73-77 記錄待排入的非緊急議題 | 未啟動（續留，非本 Sprint 範圍）|
| `DEF-034`：`CmsService` 公開端點租戶範圍設計 | Sprint 74-77 記錄待業務決策 | 未啟動（續留，非本 Sprint 範圍）|
| `RELEASE_TRACKER.md` 補齊 Sprint 68-77 列 | Sprint 71-77 連續記錄的技術債 | 未啟動（續留，非本 Sprint 範圍）|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S74 | 12（`CmsService` 測試強化 8 SP + 2 項安全問題處理 4 SP + 待決策記錄 0 SP）|
| S75 | 4（`ChatService` 探查確認無缺口 1 SP + STOMP 授權缺失修復（`DEF-035`）3 SP）|
| S76 | 4（`LogisticsService` 探查 1 SP + `DEF-036` 修復 3 SP + `DEF-037` 記錄 0 SP）|
| S77 | 7（4 個通知相關 Service 探查 1 SP + `NotificationService` 測試補齊 3 SP + `NotificationTemplateService` 測試補齊 3 SP，`DEF-038` 記錄 0 SP）|
| **S78** | **5**（`OAuthService`/`PromoService` 範圍探查 1 SP + `OAuthService` 測試補齊 2 SP + `PromoService` 測試補齊 2 SP，未發現需修復缺陷）|

> **觀察**：S78 是多 Sprint 測試強化計劃啟動以來（Sprint 66 起）連續第 2 個「探查後確認無新的擁有權/租戶檢查缺口」的 Sprint（前一次為 S77）。與 S77 不同的是，S78 額外發現一個模組本身的規模判斷結果（`OAuthService` 核心邏輯為未完成 stub），促成同一 Sprint 內完成 2 個模組。品質：`mvn test` 全量 0 fail（本 Sprint 新增 21 個：`OAuthServiceTest` 4 + `PromoServiceTest` 17）、`make validate-schema` 無漂移。多 Sprint 測試強化計劃累計發現並修復 **12 個安全缺口**（`DEF-023/024/026/027/028/029/030/032/033/035/036`，`DEF-025/031/034/037` 為記錄擱置不計入），本 Sprint 未新增待決策項目。

---

## 7. 下一步

> **檢查點**：Sprint 78 已完成，準備收尾並 push。`OAuthService`/`PromoService` 測試覆蓋率補齊，多 Sprint 測試強化計劃剩餘模組縮減為 `IdempotencyService`、`FeatureToggleService` 2 個。**下一 Sprint（Sprint 79）**建議：(1) 評估是否可合併完成剩餘 2 個模組並規劃整體收尾；(2) 優先處理待決策項目——尤其 `DEF-038`（🔴 高優先級，`TenantContextFilter` ADMIN 跨租戶架構疑慮）已連續 2 個 Sprint（77/78）未推進，且與延續多個 Sprint 的「`ADMIN` 角色權限邊界盤點」議題高度相關，建議儘早排入決策議程，因其影響範圍橫跨過去 9 個既有安全修復的前提假設。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
