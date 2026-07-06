# Sprint 79 Retrospective / Sprint 79 回顧會議

> **Sprint 編號**: Sprint 79
> **期間**: 2026-07-06
> **回顧日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 5 SP（US-001 1 + US-002 3 + US-003 1）|
| 完成 SP | 5 SP（全數）|
| 主題 | `IdempotencyService`/`FeatureToggleService` 單元測試補齊 + 修復 `DEF-039`；多 Sprint 測試強化計劃最後一輪 |

---

## 2. 做得好的（What went well）

- **Redis key 隔離問題的識別視角，延續任務指示的特別提醒**：依指示以「idempotency key 的租戶隔離」為切入點審視 `IdempotencyService`，發現 Redis key 完全未範圍化，並非泛泛檢查而找到，而是針對性審視後確認的真實缺口。
- **紅燈測試使用真實語意模擬而非固定 stub**：`IdempotencyServiceTenantIsolationTest` 用 `HashMap` 模擬 Redis SETNX/GET/SET 行為，才能真實重現「同一把 key 在不同租戶情境下互相碰撞」，比單純 mock 固定回傳值更具說服力地證實漏洞成立。
- **修復方案有清楚前例，依指示直接動手不停下確認**：修復前主動搜尋既有 `RedisCartService.getCartKey(userId, tenantId)` 前例，確認同類問題已有既定解法，屬純技術修復而非業務判斷，依任務指示直接修復。
- **`FeatureToggleService` 誠實回報乾淨，未過度解讀**：兩個方法皆一律依賴 `TenantContext`，如實記錄未發現缺口。
- **背景長時間指令的等待協議持續遵守**：紅燈測試、綠燈測試、全量 `mvn test`、`mvn verify -Pintegration-test`、`make test-db-up` 皆依執行時間長短選擇前景/背景執行，背景執行前後皆以 `ps aux` 確認程序真實狀態。

---

## 3. 待改善的（What to improve）

- **`IdempotencyService`/`FeatureToggleService` 的範圍探查在 Sprint 78 已完成初步預告（見 SPRINT_78_PLAN.md §7），本 Sprint 才正式排入**：規劃節奏上可考慮探查與修復排在同一 Sprint 的判斷時機再提前，但本次因兩個模組規模皆小，實際影響有限。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| DEF-038 | `TenantContextFilter` 對 `ADMIN` 角色的 `X-Tenant-ID` header 無驗證信任 | 涉及 `ADMIN` 角色租戶歸屬語意的架構決策，可能影響過去 9 個既有 DEF 修復的前提，需業務/架構判斷；🔴 高優先級，已連續 3 個 Sprint（77/78/79）未推進，建議儘早排入決策議程 | PO Victoria（決策）+ SD Marcus（設計） | 🔴 高（待決策） | 待業務/架構決策 |
| （待決策，延續自 Sprint 73-79）| `ADMIN` 角色權限邊界（租戶內 vs 全域）與程式碼行為的一致性盤點 | 與 `DEF-038` 高度相關，建議合併評估 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待排入（建議與 `DEF-038` 一併評估） |
| （待決策，延續自 Sprint 74-79）| `DEF-034`：`CmsService.getPageBySlug`/`getActiveBanners` 公開端點租戶範圍設計 + `/v2/cms/**` 未列入 `permitAll()` | 涉及 API 簽名變更與 `SecurityConfig` 調整，需業務/架構判斷 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待業務決策 |
| （待決策，延續自 Sprint 76-79）| `DEF-037`：`ShippingTemplateService.calculateFee` 跨租戶查詢 | 使用者已決策擱置，僅記錄不修復 | PO Victoria（已決策） | 🟢 低 | 已結案（擱置） |
| （待決策，延續自 Sprint 71-79）| `DEF-025`：`BookingService.createBooking` 的 `idempotencyKey` 參數死碼 | 使用者已決策擱置，僅記錄不修復；與本 Sprint 修復的 `IdempotencyService` Redis key 範圍化問題屬不同層級（死碼參數清潔度 vs 安全缺口），未重新開啟 | PO Victoria（已決策） | 🟢 低 | 已結案（擱置） |
| （待決策，延續自 Sprint 73-79）| `DEF-031`：`ReviewService.markHelpful` 重複投票、`createReview`/`BookingReviewService.createBookingReview` 未驗證訂單/訂房歸屬 | 業務規則疑點，使用者已決策擱置 | PO Victoria（決策） | 🟢 低 | 待業務規則確認 |
| （技術債，延續自 Sprint 71-79）| `RELEASE_TRACKER.md` 補齊 Sprint 74-78 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| （新觀察，延續自 Sprint 77-79）| `OAuthDto.AuthRequest`/`LinkRequest` 缺少 `state` 欄位（CSRF 防護） | 待未來實際串接 OAuth Provider API 時一併補上，目前功能未實作故無實際風險 | SD Marcus | 🟢 低 | 待排入（OAuth 整合實作時） |
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |
| （新流程建議，本 Sprint）| 「多 Sprint 測試強化計劃」已結束，建議下一輪盤點聚焦上述待決策項目（尤其 `DEF-038`），而非再開新一輪模組測試補齊排程 | 排程收斂節奏規劃 | PM Victoria | 🟡 中 | 下一輪規劃時評估 |

---

## 5. Sprint 78 → Sprint 79 Action Items 追蹤結果

| Action Item | 內容 | Sprint 79 達成狀態 |
|------------|------|---------------------|
| 探查階段固定加入「搜尋既有技術債/backlog 文件確認現況是否已知」步驟 | Sprint 78 記錄的流程改善建議 | ✅ 已落實：探查 `IdempotencyService` 時主動搜尋既有 `RedisCartService` 等 Redis-backed Service 的 key 設計慣例，確認修復前例後才動手 |
| 多 Sprint 測試強化計劃剩餘模組僅剩 2 個，建議合併完成並規劃整體收尾 | Sprint 78 記錄的觀察 | ✅ 已完成：本 Sprint 一次完成 `IdempotencyService` + `FeatureToggleService` 2 個模組，計劃排程清單清空，整體回顧見下方「6. 多 Sprint 測試強化計劃總回顧」 |
| `DEF-038`：`TenantContextFilter` ADMIN 跨租戶架構疑慮 | Sprint 77/78 記錄待業務/架構決策 | 未啟動（續留，非本 Sprint 範圍，本 Sprint 未涉及 `TenantContext`/`TenantContextFilter` 程式碼變更）|
| `ADMIN` 角色權限邊界一致性盤點 | Sprint 73-78 記錄待排入 | 未啟動（續留）|
| `DEF-034`：`CmsService` 公開端點租戶範圍設計 | Sprint 74-78 記錄待業務決策 | 未啟動（續留）|
| `RELEASE_TRACKER.md` 補齊 Sprint 74-78 列 | Sprint 71-78 連續記錄的技術債 | 未啟動（續留）|
| AI-2416 / AI-1903 | Phase D-2 / live 走查 | 未啟動（續留，需環境）|

---

## 6. 多 Sprint 測試強化計劃總回顧（Sprint 66-79）

> 本計劃源自 Sprint 66 對全專案 Service 層測試覆蓋率的全面盤點，目標是為先前零/低單元測試覆蓋的核心 Service 補齊測試防護網，並在過程中主動以「這個方法允許誰呼叫、有沒有檢查資源是否屬於呼叫者/當前租戶」的角度審視每個方法。本 Sprint（79）為最後一輪，排程模組全部完成。

### 6.1 規模統計

- **涵蓋 Sprint**：Sprint 66 ~ Sprint 79，共 **14 個 Sprint**。
- **補齊測試覆蓋的 Service/模組**（先前零或低單元測試覆蓋）：`AuthService`、`PaymentStateService`、`RoomCalendarService`/`BookingService`（createBooking/updateDateChange）、ERP 模組（`SupplierService`/`StockMovementService`/`PurchaseOrderService`/`InventoryService`）、`ReviewService`、`CmsService`、`ChatService`（含 `StompAuthChannelInterceptor`）、`LogisticsService`、`NotificationService`/`NotificationTemplateService`、`OAuthService`/`PromoService`、`IdempotencyService`/`FeatureToggleService`，累計 **約 20 個 Service/模組**。
- **累計發現並修復的安全漏洞**：**12 個**——`DEF-023`（Sprint 68）、`DEF-024`（Sprint 70）、`DEF-026`/`DEF-027`（Sprint 72）、`DEF-028`/`DEF-029`/`DEF-030`（Sprint 73）、`DEF-032`/`DEF-033`（Sprint 74）、`DEF-035`（Sprint 75）、`DEF-036`（Sprint 76）、**`DEF-039`（Sprint 79，本輪新發現修復）**。漏洞型態涵蓋跨租戶讀取洩漏、跨租戶寫入 IDOR、匿名保護繞過、WebSocket/STOMP 授權缺失、以及本輪新發現的 **Redis key 未做租戶/使用者範圍化**（與前述 Repository 查詢層級缺少 tenantId 過濾屬不同型態，是本計劃發現的第 2 種漏洞模式）。
- **「探查後確認乾淨」的 Sprint**：Sprint 77（`NotificationService` 系列 4 個模組）、Sprint 78（`OAuthService`/`PromoService`）、Sprint 79（`FeatureToggleService`，`IdempotencyService` 則有發現），證實審視方法並非系統性製造假陽性——約 3 個 Sprint 中有 1 個以上模組是乾淨的。
- **待決策擱置的項目（不計入已修復漏洞數）**：
  - `DEF-025`：`BookingService.createBooking` 的 `idempotencyKey` 參數死碼（技術債，使用者已決策不清理）
  - `DEF-031`：`ReviewService.markHelpful` 重複投票、訂單/訂房歸屬未驗證（業務規則疑點，待確認）
  - `DEF-034`：`CmsService` 公開端點租戶範圍設計（待業務決策）
  - `DEF-037`：`ShippingTemplateService.calculateFee` 跨租戶查詢（使用者已決策擱置）
  - `DEF-038`：`TenantContextFilter` 對 `ADMIN` 角色的跨租戶信任架構疑慮（🔴 高優先級，待業務/架構決策，可能影響過去 9 個既有 DEF 修復的前提，是本計劃結束後最需要優先處理的懸而未決事項）

### 6.2 方法論總結

1. **先探查範圍再動手**：每個 Sprint 開始前，先列出目標 Service 的完整 public 方法清單、既有測試狀況、依賴關係，評估規模後才決定測試策略，避免對死碼或無法觸及的邏輯撰寫看似完整實則無意義的測試（如 Sprint 78 `OAuthService` 的 stub 現況）。
2. **以「誰能呼叫、有無擁有權檢查」的角度逐一審視**：這是貫穿全計劃的核心審視角度，發現的 11 個 Repository 層級 IDOR/洩漏漏洞與本輪新發現的 Redis key 隔離漏洞皆由此角度找出。
3. **紅燈測試先於修復**：每個安全修復皆先撰寫紅燈測試證實漏洞成立，再動手修復，修復後轉綠，避免「自認為修好了」但實際未驗證的情況。
4. **有明確前例可循的技術修復直接動手，涉及業務判斷的一律停下確認**：本計劃後期（Sprint 78/79）依此原則加速執行，同時對 `DEF-025/031/034/037/038` 等涉及業務語意或架構決策的項目一律記錄擱置，不自行假設。
5. **誠實回報「無發現」與「有發現」同等重要**：連續多個 Sprint（77/78）確認無新缺口，證實審視方法的判斷力而非為找問題而找問題。

---

## 7. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S75 | 4（`ChatService` 探查確認無缺口 1 SP + STOMP 授權缺失修復（`DEF-035`）3 SP）|
| S76 | 4（`LogisticsService` 探查 1 SP + `DEF-036` 修復 3 SP + `DEF-037` 記錄 0 SP）|
| S77 | 7（4 個通知相關 Service 探查 1 SP + 測試補齊 6 SP，未發現新缺口）|
| S78 | 5（`OAuthService`/`PromoService` 範圍探查 1 SP + 測試補齊 4 SP，未發現新缺口）|
| **S79** | **5**（`IdempotencyService`/`FeatureToggleService` 範圍探查 1 SP + `DEF-039` 修復與測試補齊 3 SP + `FeatureToggleService` 測試補齊 1 SP）|

> **計劃結束**：多 Sprint 測試強化計劃（Sprint 66-79）至此全部完成，排程模組清單清空。

---

## 8. 下一步

> **檢查點**：Sprint 79 已完成，準備收尾並 push。多 Sprint 測試強化計劃（Sprint 66-79）全部結束。**下一輪規劃建議**：不再新開模組測試補齊排程，優先處理累積的待決策事項——尤其 `DEF-038`（🔴 高優先級，`TenantContextFilter` ADMIN 跨租戶架構疑慮）已連續 3 個 Sprint（77/78/79）未推進，影響範圍橫跨過去 9 個既有安全修復的前提假設，建議作為下一輪規劃的第一優先議程。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
