# Sprint 77 計劃 / Sprint 77 Plan

> **Sprint 編號**: Sprint 77
> **期間**: 2026-07-06
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-06
> **基於**: `SPRINT_75_PLAN.md`/`SPRINT_76_PLAN.md` §7「後續 Sprint 待處理清單」——`NotificationService`
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 76 收尾狀態 | ✅ 已收尾並 push | `LogisticsService` 探查 + `DEF-036` 修復完成 |
| `NotificationService` 範圍探查 | ✅ 完成，範圍擴大為 4 個 Service | `core/notification/` 目錄下共 4 個 Service：`NotificationService`（267 行）、`NotificationHistoryService`（124 行）、`NotificationPreferenceService`（119 行）、`NotificationTemplateService`（268 行），合計 19 個 public 方法 |
| 探查中發現的擁有權/租戶隔離問題 | ✅ 已逐一審視，**未發現新的擁有權/租戶檢查缺口** | 與過去 7 個 Sprint（68/70/72/73/74/75/76）連續發現真實漏洞的模式不同，詳見「1. 既有覆蓋現況」 |
| 架構層級疑慮 | 追蹤 `NotificationTemplateService` 的 `TenantContext` 信任來源時，發現 `TenantContextFilter` 對 `ADMIN` 角色的 `X-Tenant-ID` header 無驗證信任（`DEF-038`，新記錄），性質為橫跨全站的架構審查項目，非通知模組本身缺陷 | 已記入 `DEFERRED_ITEMS_TRACKER.md`，使用者已確認**不阻擋 Sprint 77**，交由後續獨立評估 |
| 探查中的誤判與釐清 | 一度懷疑 `NotificationTemplateController`/`NotificationController` 部分端點的 `hasAuthority(...)` 權限字串未定義於 `Permission.java` enum、可能導致端點被權限鎖死；以 `M09NotificationTemplateIntegrationTest` 實測驗證（`EXIT_CODE=0`，render 端點成功走到業務邏輯拋出預期 `E_8001`），推翻此推論——授權檢查實際運作正常，非缺口 | 誠實記錄推論錯誤與釐清過程，避免留下未驗證的懷疑 |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. 既有覆蓋現況（Dev David + QA Quincy 盤點）

### 1.1 `NotificationService`（`core/notification/NotificationService.java`，267 行，6 個 public 方法）

| 方法 | 既有測試涵蓋（修復前） | 擁有權/租戶檢查現況 |
|---|---|---|
| `sendNotification` | 無 | ✅ 正常——目標 `userId` 由 request 明確指定，Controller 端 `@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('notification:create')")` 限制呼叫者身分（本質為管理端代發功能，非一般使用者自助操作） |
| `broadcastNotification` | 無 | ✅ 正常——Controller 端 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 僅限平台超級管理員，`tenantId` 為呼叫者主動選擇的篩選欄位而非偽造來源 |
| `getUserNotifications` | 無 | ✅ 正常——`userId` 由 Controller 經 `TenantContext.getCurrentUser()` 取得（非 path/query 參數），不可偽造 |
| `markAsRead` | 無 | ✅ 正常——指定 `notificationIds` 時以 `.filter(n -> n.getUserId().equals(userId))` 過濾，他人通知靜默略過不受影響 |
| `deleteNotification` | 無 | ✅ 正常——`notification.getUserId().equals(userId)` 不符拋 `E_1007` |
| `getUnreadCount` | 無 | ✅ 正常，`userId` 來源同上 |

**結論**：`NotificationService` 6 個方法**先前完全零單元測試**，但擁有權/租戶檢查邏輯皆已正確實作（`userId` 一律來自可信的 `TenantContext`，非攻擊者可控參數）。本 Sprint 屬「補測試覆蓋率」而非「修復缺陷」。

### 1.2 `NotificationHistoryService`（`core/notification/NotificationHistoryService.java`，124 行，4 個 public 方法）

| 方法 | 既有測試涵蓋 | 擁有權/租戶檢查現況 |
|---|---|---|
| `createHistory` | ✅ 既有（`NotificationHistoryServiceTest`，2 個） | 內部方法，由 MQ Consumer 呼叫，無需擁有權檢查 |
| `getHistory` | ✅ 既有（2 個） | ✅ 正常，`userId` 來自 `TenantContext` |
| `markOneAsRead` | ✅ 既有（4 個，含跨使用者拒絕案例） | ✅ 正常，`history.getUserId().equals(userId)` 不符拋 `E_1007` |
| `getUnreadCount` | ✅ 既有（1 個） | ✅ 正常 |

**結論**：既有 9 個測試已涵蓋全部 4 個方法，含明確的擁有權拒絕案例，本 Sprint 不需額外補強。

### 1.3 `NotificationPreferenceService`（`core/notification/NotificationPreferenceService.java`，119 行，3 個 public 方法）

| 方法 | 既有測試涵蓋 | 擁有權/租戶檢查現況 |
|---|---|---|
| `getPreferences` | ✅ 既有（1 個） | ✅ 正常，`userId` 來自呼叫端可信參數 |
| `upsertPreference` | ✅ 既有（1 個） | ✅ 正常 |
| `isEnabled` | ✅ 既有（1 個） | ✅ 正常（內部方法，供 `sendNotification` 呼叫） |

**結論**：既有 3 個測試已涵蓋全部方法核心邏輯，本 Sprint 不需額外補強。

### 1.4 `NotificationTemplateService`（`core/notification/NotificationTemplateService.java`，268 行，6 個 public 方法）

| 方法 | 既有測試涵蓋（修復前） | 擁有權/租戶檢查現況 |
|---|---|---|
| `getTemplates` | 僅整合測試（`M09NotificationTemplateIntegrationTest`），無單元測試 | ✅ 正常，`searchTemplates` JPQL 以 `t.tenantId = :tenantId` 精確過濾 |
| `getTemplate` | 同上 | ✅ 正常，`template.getTenantId() != null && !equals(tenantId)` 拒絕跨租戶讀取；`tenantId=null`（全域模板）任何租戶皆可讀取，屬既有設計慣例（schema 以 `(tenant_id, template_code)` 複合唯一鍵支援全域模板概念），非缺口 |
| `createTemplate` | 同上 | ✅ 正常，模板一律綁定呼叫端傳入的 `tenantId`（來自 `TenantContext`），無法跨租戶建立 |
| `updateTemplate` | 同上 | ✅ 正常，同 `getTemplate` 擁有權檢查邏輯 |
| `deleteTemplate` | 同上 | ✅ 正常，同上（軟刪除） |
| `renderTemplate` | 同上 | ✅ 正常，依 `templateCode + tenantId` 複合鍵查詢，無法渲染他租戶模板 |

**結論**：`NotificationTemplateService` 6 個方法**先前完全零單元測試**（僅有 12 個整合測試涵蓋 API 契約層），擁有權/租戶檢查邏輯皆已正確實作。本 Sprint 屬「補單元測試覆蓋率」，非修復缺陷。

---

## 2. Sprint 77 目標

> **主題**: `NotificationService`/`NotificationTemplateService` 單元測試補齊（先前皆為零覆蓋）+ 全 4 個通知相關 Service 的擁有權/租戶檢查主動審視（結論：無新缺口）

---

## 3. User Story

### US-001：4 個通知相關 Service 範圍探查

> **SP**: 1 | **優先級**: P2 | **狀態**: ✅ 完成

**AC-001-1**：列出 `core/notification/` 目錄下全部 4 個 Service（`NotificationService`/`NotificationHistoryService`/`NotificationPreferenceService`/`NotificationTemplateService`）共 19 個 public 方法，逐一確認既有測試涵蓋與擁有權/租戶檢查現況。

**AC-001-2**：以「這個方法允許誰呼叫、有沒有檢查資源是否屬於呼叫者/當前租戶」角度逐一審視，特別留意非典型管道（MQ Consumer、Controller 權限設定）。

**AC-001-3**：追蹤 `TenantContext` 信任來源時發現 `TenantContextFilter` 的 `ADMIN` 跨租戶架構疑慮，依規範記錄（`DEF-038`）不擅自修改，經使用者確認為獨立架構事項、不阻擋本 Sprint。

---

### US-002：`NotificationService` 單元測試補齊

> **SP**: 3 | **優先級**: P1（測試防護網） | **狀態**: ✅ 完成

**背景**：`NotificationService` 6 個方法先前完全零單元測試，屬多 Sprint 測試強化計劃的既定排程模組。

**AC-002-1**：新增 `NotificationServiceTest.java`，涵蓋 `sendNotification`（使用者不存在、偏好停用跳過、正常發送、recipient 推導、MQ 失敗重試計數）、`broadcastNotification`（依租戶篩選、全平台、單一使用者失敗不中斷）、`getUserNotifications`（unreadOnly 分支、分頁上限）、`markAsRead`（含他人通知不受影響的擁有權驗證）、`deleteNotification`（含跨使用者 403 驗證）、`getUnreadCount`。

**AC-002-2**：測試需明確驗證既有的擁有權檢查邏輯（`deleteNotification`/`markAsRead` 的越權案例），確保未來重構不會悄悄破壞這些保護。

**AC-002-3**：`mvn test` 全數通過，因未修改生產程式碼，依全量回歸頻率政策不需執行 `mvn verify -Pintegration-test`。

---

### US-003：`NotificationTemplateService` 單元測試補齊

> **SP**: 3 | **優先級**: P1（測試防護網） | **狀態**: ✅ 完成

**背景**：`NotificationTemplateService` 6 個方法先前僅有整合測試（API 契約層），無單元測試直接驗證 Service 層邏輯與擁有權檢查。

**AC-003-1**：新增 `NotificationTemplateServiceTest.java`，涵蓋 `getTemplates`（正確帶入 tenantId）、`getTemplate`/`updateTemplate`/`deleteTemplate`（跨租戶拒絕 `E_1007`、不存在 `E_8003`、全域模板任何租戶可讀）、`createTemplate`（重複代碼拒絕 `E_6001`、變量自動提取、明確指定變量覆蓋）、`renderTemplate`（不存在、未啟用 `E_8001`、正常渲染變量替換）。

**AC-003-2**：測試需明確驗證既有的跨租戶拒絕邏輯，作為未來重構的防護網。

**AC-003-3**：`mvn test` 全數通過。

---

### 附帶項目：`TenantContextFilter` ADMIN 跨租戶架構疑慮（`DEF-038`，新記錄，已交由使用者決策）

> **SP**: 0（僅記錄，非阻塞）

探查 `NotificationTemplateService` 的租戶隔離邏輯時，追蹤 `TenantContext.getCurrentTenant()` 的信任來源，發現 `TenantContextFilter` 對 `ADMIN` 角色的 `X-Tenant-ID` header 無驗證信任，可能影響過去 9 個既有 DEF 修復（`DEF-019/023/024/026/028/029/032/033/036`）的 `isAdmin` 放行分支前提。因涉及全站架構決策（`ADMIN` 角色租戶歸屬語意），非通知模組本身缺陷，記入 `DEFERRED_ITEMS_TRACKER.md`（`DEF-038`），**使用者已確認為獨立架構審查項目，不阻擋 Sprint 77**。

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|----|
| US-001 | 4 個通知相關 Service 範圍探查 | 1 | P2 |
| US-002 | `NotificationService` 單元測試補齊 | 3 | P1 |
| US-003 | `NotificationTemplateService` 單元測試補齊 | 3 | P1 |
| 附帶 | `DEF-038` 記錄（不修復，非阻塞） | 0 | — |
| **合計** | | **7** | |

> **Velocity 參考**：本 Sprint 未發現需修復的漏洞，屬純測試補強性質，量級介於 Sprint 71（8 SP，純測試）與 Sprint 75（4 SP，探查+小修復）之間。

---

## 5. Definition of Done

- [x] US-001：完整探查 4 個通知相關 Service 共 19 個 public 方法，確認既有測試覆蓋與擁有權檢查現況；`DEF-038` 已記錄並經使用者確認不阻擋
- [x] US-002：新增 `NotificationServiceTest.java`（18 個測試），涵蓋 `sendNotification`/`broadcastNotification`/`getUserNotifications`/`markAsRead`/`deleteNotification`/`getUnreadCount`，`mvn test` 0 fail
- [x] US-003：新增 `NotificationTemplateServiceTest.java`（20 個測試），涵蓋全部 6 個方法含跨租戶拒絕驗證，`mvn test` 0 fail
- [x] 開發-編譯-測試循環：每完成一個測試檔案立即編譯 + 執行驗證，未累積
- [x] 本 Sprint**未修改任何生產程式碼**，依全量回歸頻率政策僅需 `mvn test`（全量 0 fail），不需 `mvn verify -Pintegration-test`
- [x] `make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）
- [x] `DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-038`（記入「🔴 高優先級」表格，狀態「已記錄，不排入排程，待使用者決策，非阻塞 Sprint 77」）
- [x] Sprint 77 Review / Retro / Release Notes + trackers
- [x] 本 Sprint 收尾後立即 push

---

## 6. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端測試（新檔） | `NotificationServiceTest.java`（18 個測試） |
| 後端測試（新檔） | `NotificationTemplateServiceTest.java`（20 個測試） |
| 追蹤文件 | `DEFERRED_ITEMS_TRACKER.md`（新增 `DEF-038`） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

---

## 7. 後續 Sprint 待處理清單（多 Sprint 測試強化計劃）

依風險排序，供 Sprint 78+ 規劃參考：

1. `PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`（延續自 Sprint 75/76 排程，尚未探查）
2. 待決策事項：`DEF-034`（`CmsService` 公開端點租戶範圍設計）、`DEF-037`（`ShippingTemplateService.calculateFee` 跨租戶查詢，已決策擱置）、`DEF-038`（`TenantContextFilter` ADMIN 跨租戶架構疑慮，新記錄，待業務/架構決策）、`ADMIN` 角色權限邊界（租戶內 vs 全域）盤點（延續自 Sprint 73-76 Retro，`DEF-038` 的發現與此議題高度相關，建議合併評估）
3. 新觀察（本 Sprint）：並非每個 Sprint 探查都會找到新漏洞——本 Sprint 是繼 Sprint 68/70/72/73/74/75/76 連續 7 個 Sprint 發現真實漏洞後，首次「探查後確認無新缺口」的 Sprint，證明先前的漏洞並非因為審視方法有誤而系統性存在，而是特定模組確實有缺口；同時也驗證了主動審視方法本身不會导致「為了找漏洞而製造假陽性」

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
