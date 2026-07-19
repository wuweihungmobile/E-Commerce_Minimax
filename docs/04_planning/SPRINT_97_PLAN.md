# Sprint 97 Plan — 開店申請 → Admin 審核 → StoreOwner 授權 端到端斷點修復

**Sprint**: Sprint 97
**日期**: 2026-07-19
**主題**: PRD §7.4.1/§9.10.2/§12.1 明訂的「Buyer → StoreOwner 角色授予流程」P0 使用者旅程，重新全面比對 PRD 全文後發現的第五個、也是目前為止**最嚴重**的落地缺口——96 個 Sprint 以來，這條核心的「網友開店」自助流程在提交申請後永遠卡住，從未真正貫通。

---

## 1. 缺口盤點結果（背景，含嚴重度說明）

比照前四輪方法論（孤兒錯誤碼、零呼叫死碼、Entity 欄位存在但從未賦值），本輪同時做三項分析後，逐段核對 PRD 全文找到本缺口。

**與前四輪不同：這不是「規格沒做」，而是「兩段各自完整的程式碼互不相通」**：

- `TenantController.createApplication()` → `TenantService.createApplication()`：只會寫入 **`tenant_applications`** 表（`TenantApplication` entity），完全不會建立 `tenants` 表記錄。
- `AdminController.approveTenant()/rejectTenant()/reviewTenant()` → `AdminService` 對應方法：只會對**已存在**的 `Tenant` 實體（status = `PENDING_REVIEW`）做狀態更新。
- **全庫 grep 找不到任何程式碼路徑會從 `TenantApplication` 建立出一個 `Tenant`**——`TenantApplication.tenantId`（回填欄位）、`ApplicationStatus.APPROVED/REJECTED/SUSPENDED`（三個列舉值）全部從未被賦值，全庫僅使用過 `PENDING`。
- 佐證：`TenantControllerE2ETest`/`AdminControllerE2ETest` 現有測試，凡牽涉 StoreOwner/已審核 Tenant 的情境，全部用測試固件直接 `tenantRepository.save(Tenant.builder()....status(ACTIVE)...)` + `tenantMemberRepository.save(...)` 手動塞資料繞過申請流程——**沒有任何測試曾經驗證過「apply → admin 看到 → 審核通過 → 真正變成 StoreOwner」這條完整迴路**，這正是這個斷點存活 96 個 Sprint 未被發現的原因。
- **PRD 依據**：§7.4.1「Buyer → StoreOwner 角色授予流程」明文寫觸發條件為「Buyer 提交開店申請並經 Admin 審核通過」，資料表變更需為「①產生新 `tenants` 紀錄；②`tenant_members` 新增紀錄設角色為 StoreOwner」——這正是目前完全缺失的部分。

**嚴重度**：這是全平台「網友如何成為賣家」這條 PRD 明訂 P0 使用者旅程的唯一自助入口，目前完全無法在真實流程中走通（僅能靠工程師手動塞資料庫繞過）。判定為本輪至今最高優先級的修復項目。

## 2. 規格落差與工程決策（PRD 本身對這點的規格是明確的，以下為純工程執行細節）

1. **保留既有 `reviewTenant`/`approveTenant`/`rejectTenant`（操作既有 `Tenant` 記錄）完全不動**：這組端點本身邏輯正確，只是在正常流程中永遠等不到資料（因為沒有東西會把 `Tenant` 建到 `PENDING_REVIEW` 狀態）。依 Rule 3（精準改動），新增專屬的 `TenantApplication` 審核端點來補上真正缺失的橋接，不觸碰、不重構這組舊端點。
2. **新增端點路徑**：`GET /v2/admin/tenant-applications`（列表）、`POST /v2/admin/tenant-applications/{id}/approve`、`POST /v2/admin/tenant-applications/{id}/reject`——與既有 `/v2/admin/tenants/*` 並列而非覆蓋，明確區分「審核申請」與「管理既有租戶」兩件事。
3. **核准時的資料表變更**（PRD §7.4.1 逐字規格）：建立 `Tenant`（`name`=申請店名、`slug` 依既有 `PostService`/`PostCategoryService` 慣例產生並確保唯一、`status=ACTIVE`）→ 呼叫既有 `initializeFeatureToggles()` 初始化 6 個 Feature Toggle → 於 `tenant_members` 建立該申請人的 `STORE_OWNER` 記錄（不透過需要「已有 StoreOwner」才能呼叫的既有 `addMember()`，因為這是第一位成員，改直接寫入）→ 回填 `TenantApplication.tenantId`/`status=APPROVED`/`reviewedAt`/`reviewedBy`。
4. **新增 3 個錯誤碼**（`E_2006` 找不到開店申請、`E_2007` 開店申請狀態非「待審核」、`E_2008` 訪客身份的開店申請無法核准）：不重用語意不準確的既有 `E_2000`/`E_2005`（訊息文字寫「租戶」而非「申請」），維持錯誤訊息對 Admin 使用者的準確性。
5. **`reviewedBy` 改為真實記錄操作者 UUID**：既有 `approveTenant`/`rejectTenant` 因是 Mock Implementation 風格，`reviewedBy` 一律寫死字串 `"SYSTEM_ADMIN"`。新端點透過 `@AuthenticationPrincipal UserPrincipal` 取得真正的 Admin UUID 寫入 `TenantApplication.reviewedBy`（該欄位型別本就是 `UUID`），比舊端點的做法更正確、不引入回歸風險（新端點不觸碰舊程式碼）。

## 3. 實作內容

1. **`ErrorCode`**：新增 `E_2006`/`E_2007`/`E_2008`，並於 `GlobalExceptionHandler.mapErrorCodeToStatus` 分別對應 404/400/400。
2. **`AdminService`**：新增 `getPendingTenantApplications()`、`approveTenantApplication(applicationId, reviewedBy)`、`rejectTenantApplication(applicationId, reviewedBy, request)`，注入 `TenantApplicationRepository`/`TenantMemberRepository`。
3. **`AdminDto`**：新增 `TenantApplicationSummaryResponse`/`TenantApplicationListResponse`/`TenantApplicationApproveResponse`/`TenantApplicationRejectRequest`/`TenantApplicationRejectResponse`。
4. **`AdminController`**：新增 3 個端點（`GET/POST .../tenant-applications[...]`），皆 `hasRole('SUPER_ADMIN')`。
5. **測試**：
   - `AdminServiceTest`：新增 `TenantApplicationReviewTests`（8 項）——列表、核准成功（驗證 Tenant/TenantMember/Application 三處寫入）、申請不存在、非待審核狀態、Guest 申請無法核准、駁回成功、駁回非待審核狀態。
   - `TenantApplicationReviewE2ETest`（新檔，5 項，真實 DB + 真實 JWT）：**完整迴路**（Buyer 申請 → Admin 列表可見 → 核准 → 驗證真正的 Tenant 已建立且 Buyer 已成為 StoreOwner）、駁回不建立 Tenant、重複核准 400、Guest 申請核准 400、非 Admin 查詢 403。

## 4. 驗證結果

- 單元測試：`AdminServiceTest` +8，0 fail
- 整合測試：`TenantApplicationReviewE2ETest` 5 tests 0 fail（含端到端完整迴路驗證）
- Checkstyle 0 violations、PMD 0 violations
- 全量回歸 `mvn verify -Pintegration-test`：詳見 commit 訊息
- `make validate-schema`：無 migration，schema-free（`tenants`/`tenant_members`/`tenant_applications` 欄位早已存在）

## 5. 範圍外（延後）

- `E_4091`（店鋪名稱已被使用）孤兒錯誤碼／`tenants.name` 唯一性檢查（PRD §8.2.1）——與本次核心斷點修復性質不同（防呆而非功能缺失），且目前用 slug（已有唯一性保證）而非 name 作為實質唯一鍵，非本輪必要項目，留待後續評估是否需要對 name 也加防呆訊息。
- Guest（未登入）提交的開店申請目前無法被核准（`E_2008`）：PRD 未定義 Guest 申請後續如何轉換為可核准狀態（例如要求先完成註冊綁定），維持現狀由 Admin 駁回並請使用者以登入身份重新提交，未來若 PRD 補充規格再處理。
- JWT 重新授權（PRD §7.4.1「系統強制用戶重新授權，新 Token 含 StoreOwner role」）：現有登入/refresh 流程已能在下次登入時取得最新角色（`AuthService` 每次登入查詢當下 `TenantMember`），不需要新增「核准後主動踢掉舊 token」機制，維持現狀。
