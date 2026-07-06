# Sprint 77 Review / Sprint 77 評審會議

> **Sprint 編號**: Sprint 77
> **期間**: 2026-07-06
> **評審日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: `NotificationService`/`NotificationTemplateService` 單元測試補齊（先前皆為零覆蓋）+ 全 4 個通知相關 Service 的擁有權/租戶檢查主動審視

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 4 個通知相關 Service 範圍探查 | 1 | ✅ 完成 |
| US-002 | `NotificationService` 單元測試補齊 | 3 | ✅ 完成 |
| US-003 | `NotificationTemplateService` 單元測試補齊 | 3 | ✅ 完成 |

**7 SP 全數完成**。本 Sprint 延續多 Sprint 測試強化計劃，探查 `core/notification/` 目錄下全部 4 個 Service（`NotificationService`/`NotificationHistoryService`/`NotificationPreferenceService`/`NotificationTemplateService`，共 19 個 public 方法）。與過去 7 個連續 Sprint（68/70/72/73/74/75/76）皆發現真實安全漏洞不同，本 Sprint**逐一審視全部方法後確認無新的擁有權/租戶檢查缺口**——`NotificationService`/`NotificationTemplateService` 雖先前完全零單元測試，但其擁有權/租戶檢查邏輯本身已正確實作（`userId`/`tenantId` 皆來自可信的 `TenantContext`，非攻擊者可控參數）。探查過程中另發現一項跨模組的架構層級疑慮（`DEF-038`），已記錄交由使用者決策，不影響本 Sprint 收尾。

---

## 2. 交付內容

### 探查結論（US-001）

- **`NotificationService`（267 行）6 個 public 方法**：`sendNotification`/`broadcastNotification` 由 Controller 端 `@PreAuthorize` 限制為 `SUPER_ADMIN` 專用管理功能，非一般使用者自助操作；`getUserNotifications`/`markAsRead`/`deleteNotification`/`getUnreadCount` 的 `userId` 皆由 Controller 經 `TenantContext.getCurrentUser()` 取得而非攻擊者可控的 path/query 參數，`deleteNotification`/`markAsRead` 皆有明確的擁有權比對（`getUserId().equals(userId)`）。
- **`NotificationHistoryService`（124 行）4 個 public 方法**：既有 9 個測試已完整涵蓋，含明確的跨使用者拒絕案例（`markOneAsRead_differentUser_throwsBusinessException`），本 Sprint 未發現需補強之處。
- **`NotificationPreferenceService`（119 行）3 個 public 方法**：既有 3 個測試已涵蓋核心邏輯，本 Sprint 未發現需補強之處。
- **`NotificationTemplateService`（268 行）6 個 public 方法**：先前僅有 12 個整合測試（`M09NotificationTemplateIntegrationTest`，涵蓋 API 契約層），無單元測試直接驗證 Service 層邏輯；`getTemplate`/`updateTemplate`/`deleteTemplate` 皆以 `template.getTenantId() != null && !equals(tenantId)` 正確拒絕跨租戶存取，`createTemplate` 一律綁定呼叫端傳入的 `tenantId`，`renderTemplate` 依 `templateCode + tenantId` 複合鍵查詢無法渲染他租戶模板。`tenantId=null` 的「全域模板」任何租戶皆可讀寫，經確認為 schema 設計本身支援的既有慣例（`(tenant_id, template_code)` 複合唯一鍵），非缺口。

### 探查過程中的誤判與釐清

- 曾懷疑 `NotificationTemplateController`（create/update/delete/render）與 `NotificationController.send` 使用的 `hasAuthority('notification_template:xxx')`/`hasAuthority('notification:create')` 權限字串未定義於 `Permission.java` enum，可能導致這些端點被權限鎖死、無人可呼叫。以背景執行 `mvn test -Dtest=M09NotificationTemplateIntegrationTest -Pintegration-test` 實測驗證（`EXIT_CODE=0`，一般 `BUYER` 角色使用者呼叫 render 端點成功走到業務邏輯並拋出預期的 `E_8001 Template is not active`），證實授權檢查實際運作正常，先前推論有誤。誠實記錄此推論錯誤與釐清過程，避免留下未經驗證的懷疑污染後續 Sprint 判斷。

### 新記錄（非本 Sprint 修復範圍）：`DEF-038`（`TenantContextFilter` ADMIN 跨租戶架構疑慮）

- 追蹤 `NotificationTemplateService` 的 `TenantContext.getCurrentTenant()` 信任來源時，發現 `TenantContextFilter.resolveEffectiveTenantId()`（`backend/src/main/java/com/nextkey/ecommerce/api/filter/TenantContextFilter.java:84-92`）對 `role` 為 `ADMIN` 的使用者，只要請求帶有 `X-Tenant-ID` header 就直接信任其值作為當前租戶，未驗證該 `ADMIN` 是否真的隸屬/被授權管理該目標租戶。
- 兩輪獨立探查對「這是否為刻意設計」給出矛盾線索：`RolePermissionMapping.java` 註解稱 `ADMIN` 為「租戶內管理」，但過去 `DEF-019/023/024/026/028/029/032/033/036` 等 9 個既有 IDOR 修復的 `isAdmin`/`checkXxxOwnership` 判斷邏輯，皆把 `ROLE_ADMIN` 與 `ROLE_SUPER_ADMIN` 並列為「可跨租戶放行」的角色，形成「若 `ADMIN` 帳號可偽造 `X-Tenant-ID`，則可能通過這 9 個既有擁有權檢查的『本租戶』或『admin』分支」的架構層級疑慮。
- 全庫搜尋未發現任何既有 DEF、SOP 文件或測試明確審視/驗證過這個機制；`git log` 顯示 `TenantContextFilter.java` 核心邏輯自初始 commit 以來從未被修改或納入後續多輪租戶 IDOR 排查範圍。
- 因涉及全站架構決策（`ADMIN` 角色的租戶歸屬語意應為何），非通知模組本身的缺陷，記入 `DEFERRED_ITEMS_TRACKER.md`（`DEF-038`），**使用者已確認此為獨立架構審查項目，不阻擋 Sprint 77，本 Sprint 未深入調查全站受影響端點清單，也未變更程式碼**。

### 文件

- **`SPRINT_77_PLAN.md`**（新檔）：本 Sprint 計劃，含前置範圍探查、既有測試覆蓋現況、US-001/002/003 完整 AC。
- **`DEFERRED_ITEMS_TRACKER.md`**：新增 `DEF-038`（記入「🔴 高優先級」表格，狀態「已記錄，不排入排程，待使用者決策」），文件版本 v2.24 → v2.25。
- **`RELEASE_NOTES_v2028.10.07-01.md`**（新檔）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 開發-編譯-測試循環 | ✅ `NotificationServiceTest.java` 撰寫 → 編譯 → 執行（18 tests 0 fail）→ `NotificationTemplateServiceTest.java` 撰寫 → 編譯 → 執行（20 tests 0 fail），未累積 |
| `NotificationServiceTest`（新檔） | ✅ 18 tests，0 fail |
| `NotificationTemplateServiceTest`（新檔） | ✅ 20 tests，0 fail |
| `M09NotificationTemplateIntegrationTest`（探測性重跑，驗證權限釐清） | ✅ EXIT_CODE=0 |
| 後端全量單元回歸（`mvn test`） | ✅ **0 fail**（含本 Sprint新增 38 個） |
| `make validate-schema` | ✅ 無漂移（本 Sprint 無 entity/migration 變更），EXIT_CODE=0，backend 啟動成功、entity 與 Flyway schema 對齊 |
| 驗證方式選擇 | 依全量回歸頻率政策：本 Sprint **未修改任何生產程式碼**（純新增測試），故僅需 `mvn test`，不需 `mvn verify -Pintegration-test` |

---

## 4. 誠實揭露（Rule 12）

1. **本 Sprint 未發現新漏洞，與過去 7 個連續 Sprint 的模式不同**：Sprint 68/70/72/73/74/75/76 皆在探查階段發現至少 1 項真實的擁有權/租戶檢查缺口。本 Sprint 逐一審視 4 個 Service 共 19 個 public 方法後，確認擁有權/租戶檢查邏輯皆已正確實作，不強行製造發現以符合先前模式，如實記錄「探查後確認無新缺口」的結論。
2. **曾產生一個後來被實測推翻的誤判**：基於 `Permission.java` enum 靜態分析，一度懷疑通知模板管理端點因權限字串未定義而被鎖死，此推論在協調者要求下以實際整合測試驗證後證實錯誤（授權檢查正常運作，`BUYER` 角色成功呼叫 render 端點）。如實記錄此推論錯誤的過程，而非事後隱去不提。
3. **`DEF-038` 的發現具有跨越 9 個既有 DEF 的潛在影響範圍，但本 Sprint 依規範僅記錄不擴大調查**：雖然此發現可能意味著過去 9 個 IDOR 修復的「admin 放行」前提並不如預期堅固，但由於（a）性質是全站架構層級的授權設計問題，非通知模組本身缺陷，（b）修復方案涉及「`ADMIN` 角色應為租戶內管理員還是平台級角色」的業務判斷，且兩輪獨立探查對此給出矛盾證據，本 Sprint 選擇如實記錄疑慮範圍與潛在影響，交由使用者決策後續處理方式，未擅自擴大調查或修改程式碼，符合 Rule 3（精準改動）與 Rule 1（不清楚時提問而非猜測）。
4. **`NotificationTemplateService` 的「全域模板」（`tenantId=null`）任何租戶皆可寫入，未列為缺口**：探查中注意到 `updateTemplate`/`deleteTemplate` 對 `tenantId=null` 的模板不做租戶比對，理論上任何持有權限的租戶皆可修改/刪除全域模板。但目前系統**沒有任何 API 路徑或 seed 資料會產生 `tenantId=null` 的模板**（`createTemplate` 一律綁定呼叫端的具體 `tenantId`），此為 schema 設計已預留但尚未啟用的功能空間，非活躍可利用的缺口，故未列為 `DEF`，僅在此誠實記錄觀察供後續 Sprint 參考。

---

## 5. Demo 重點

- **驗證「主動審視方法不會製造假陽性」**：延續 Sprint 68-76 建立的「這個方法允許誰呼叫、有沒有檢查資源歸屬」審視方法，本 Sprint 首次得出「無新缺口」的誠實結論，證明先前連續 7 個 Sprint 發現漏洞並非審視方法本身有問題導致的系統性誤判，而是各模組確實各自存在缺口。
- **推論錯誤的即時修正**：對權限字串定義的懷疑，透過實際執行整合測試（而非持續依賴靜態程式碼分析）快速釐清，避免將未經驗證的懷疑帶入後續修復工作。
- **架構層級疑慮的正確處理路徑**：`DEF-038` 涉及全站授權模型的根本設計問題，明確識別其超出單一 Sprint／單一模組的範疇後，選擇記錄與交付決策而非擅自修改，避免大範圍變更未經業務確認即上線的風險。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
