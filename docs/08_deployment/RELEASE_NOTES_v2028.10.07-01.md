# Release Notes - v2028.10.07-01 (Sprint 77)

**發布日期**: 2028-10-07（規劃）／實作完成 2026-07-06
**發布類型**: 🔍 探查 + 🧪 測試補強（schema-free；後端聚焦，無前端變動；**未修改任何生產程式碼**）
**Sprint**: Sprint 77（多 Sprint 測試強化計劃）
**狀態**: ✅ 已 push

> Sprint 77 主題：探查 `core/notification/` 目錄下全部 4 個 Service（`NotificationService`/`NotificationHistoryService`/`NotificationPreferenceService`/`NotificationTemplateService`，共 19 個 public 方法），逐一以「誰可以呼叫、有無檢查資源歸屬」角度審視。與過去 7 個連續 Sprint（68/70/72/73/74/75/76）皆發現真實安全漏洞不同，**本 Sprint 確認無新的擁有權/租戶檢查缺口**；為 `NotificationService`/`NotificationTemplateService`（先前完全零單元測試）補齊 38 個單元測試。探查過程中另發現一項跨模組的架構層級疑慮（`DEF-038`：`TenantContextFilter` 對 `ADMIN` 角色的 `X-Tenant-ID` header 無驗證信任），已記錄交由使用者決策，不影響本次發布。

---

## 🔍 範圍探查（US-001）

- **`NotificationService`（267 行）6 個 public 方法**：`sendNotification`/`broadcastNotification` 限 `SUPER_ADMIN` 呼叫；`getUserNotifications`/`markAsRead`/`deleteNotification`/`getUnreadCount` 的 `userId` 皆來自可信的 `TenantContext`，`deleteNotification`/`markAsRead` 已有明確擁有權比對。全數方法先前**零單元測試**。
- **`NotificationHistoryService`（124 行）4 個方法** / **`NotificationPreferenceService`（119 行）3 個方法**：既有測試（9+3 個）已完整涵蓋，含明確跨使用者拒絕案例，本 Sprint 未補強。
- **`NotificationTemplateService`（268 行）6 個 public 方法**：`getTemplate`/`updateTemplate`/`deleteTemplate` 皆正確拒絕跨租戶存取，`createTemplate`/`renderTemplate` 皆綁定/依賴呼叫端可信的 `tenantId`。先前僅有 12 個整合測試（API 契約層），無單元測試。

## 🧪 測試補強（US-002 + US-003）

- **`NotificationServiceTest.java`（新檔，18 個測試）**：涵蓋 `sendNotification`（使用者不存在、偏好停用跳過、正常發送、recipient 推導、MQ 失敗重試計數）、`broadcastNotification`（依租戶篩選、全平台、單一使用者失敗不中斷整體）、`getUserNotifications`（unreadOnly 分支、分頁上限）、`markAsRead`（含他人通知不受影響的擁有權驗證）、`deleteNotification`（含跨使用者 403 驗證）、`getUnreadCount`。
- **`NotificationTemplateServiceTest.java`（新檔，20 個測試）**：涵蓋 `getTemplates`（正確帶入 tenantId）、`getTemplate`/`updateTemplate`/`deleteTemplate`（跨租戶拒絕 `E_1007`、不存在 `E_8003`、全域模板任何租戶可讀）、`createTemplate`（重複代碼拒絕 `E_6001`、變量自動提取/覆蓋）、`renderTemplate`（不存在、未啟用 `E_8001`、正常渲染變量替換）。

## 📝 新記錄，非本次修復範圍：`DEF-038`（`TenantContextFilter` ADMIN 跨租戶架構疑慮）

- 追蹤 `NotificationTemplateService` 的 `TenantContext` 信任來源時，發現 `TenantContextFilter` 對 `role=ADMIN` 的使用者，只要請求帶 `X-Tenant-ID` header 就直接信任其值，未驗證該 `ADMIN` 是否真的被授權管理該租戶；此機制可能影響過去 `DEF-019/023/024/026/028/029/032/033/036` 共 9 個既有 IDOR 修復的「admin 可跨租戶放行」前提。因涉及全站 `ADMIN` 角色租戶歸屬語意的架構決策，記入 `DEFERRED_ITEMS_TRACKER.md`（`DEF-038`）後**使用者已確認為獨立架構審查項目，不阻擋本次發布，本 Sprint 未修改程式碼**。

## 測試 / 驗證 ✅

- **`NotificationServiceTest`**：18 tests，0 fail。
- **`NotificationTemplateServiceTest`**：20 tests，0 fail。
- **`M09NotificationTemplateIntegrationTest`（探測性重跑，用於釐清權限字串疑慮）**：EXIT_CODE=0，確認授權檢查運作正常。
- **後端全量單元回歸**（`mvn test`）：**0 fail**（含本 Sprint 新增 38 個）。
- **驗證方式選擇**：依全量回歸頻率政策，本 Sprint 未修改任何生產程式碼（純新增測試），僅需 `mvn test`，不需 `mvn verify -Pintegration-test`。
- **schema 漂移守門**：`make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更），EXIT_CODE=0。

## 技術決策 / 已知限制 ⚠️

- **本 Sprint 未修改任何生產程式碼**：探查確認 4 個 Service 的擁有權/租戶檢查邏輯皆已正確實作，本次發布純為測試覆蓋率補強。
- **`DEF-038` 僅記錄不修復**：涉及 `ADMIN` 角色租戶歸屬語意的全站架構決策，且可能影響過去 9 個既有安全修復的前提假設，使用者已確認交由後續獨立評估，不阻擋本次發布。
- **`NotificationTemplateService` 全域模板（`tenantId=null`）寫入權限未收斂**：schema 設計已預留但目前無任何 API 路徑會產生此類資料，非活躍可利用缺口，僅記錄觀察供未來啟用該功能前參考。
- **無前端變動**：本 Sprint 純後端測試補強。

## 資料庫遷移 🗄️

- 無（schema-free；本 Sprint 未新增/修改任何 Entity 或 Repository 方法）。

## 內含 Commit（Sprint 77）

| US / 項目 | 說明 |
|----------|------|
| Sprint 77 Plan | 4 個通知相關 Service 範圍探查 + 單元測試補齊計劃（3 US / 7 SP）|
| US-001 | 4 個通知相關 Service 範圍探查（無程式碼變更，`DEF-038` 記錄）|
| US-002 | `NotificationServiceTest.java` 新增 18 個測試 |
| US-003 | `NotificationTemplateServiceTest.java` 新增 20 個測試 |
| Sprint 77 收尾 | Review / Retro / Release Notes + trackers（含 `DEF-038` 新增）|

> 實際 commit hash 詳見 git log（依 Sprint 慣例於收尾 commit 訊息中記錄）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**基於**: AISDLC v0.09 Release Management Workflow
