# API 規格文件 - M17 租戶/店鋪管理 / API Specification - M17 Tenant Management

> **文件編號**: API-M17
> **文件版本**: v2.8（Sprint 316：`DEF-438`——§2.1 實測矩陣新增一般成員 `STORE_STAFF` 欄；§4.5～§4.9、§4.13、§4.14、§5、§6 對一般成員的說明依實測校正：店員在方法安全層就被擋下，回 `E-1007`，到不了 Service 層的 `E-4031` 檢查）；v2.7（Sprint 315：`DEF-432`③——§4.3 非 `ACTIVE` 店鋪對該店鋪的有效成員回完整版，店主可編輯停權中的店鋪）；v2.6（Sprint 314：`DEF-434` 修復——§4.6 的 `status` 說的是這次請求的結果，`enabled=false` 一律 `DISABLED`；§4.4 補記店鋪描述的部分更新語意）；v2.5（Sprint 311：`DEF-429` 修復——新增 `requested_at`，待審核只認店主真的申請過；§4.5、§4.6、§4.29 同步）；v2.4（Sprint 310：§4.5 補記 `status` 的實際判定與 `DEF-429`）；v2.3（Sprint 298 補上全域角色對店主自助端點的真實後端實測矩陣，見註記 9 與 §2.1；v2.2 為 Sprint 294 校正 §4.4／§4.8／§4.9／§4.13／§4.14 與 `E-4031` 總表；v2.1 為 Sprint 292 校正 §4.6；v2.0 為 Sprint 266 依實作改寫）
> **建立日期**: 2026-04-09（v1.0）
> **最後更新**: 2026-10-30
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`TenantController`、`AdminController`、`StorefrontController`（`api/controller/`）、`TenantService`、`AdminService`（`core/tenant/`、`core/admin/`）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有真實測試佐證：[TenantServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/tenant/TenantServiceTest.java)、[AdminServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/admin/AdminServiceTest.java)、[AdminControllerE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/AdminControllerE2ETest.java)、[TenantControllerE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/TenantControllerE2ETest.java)、[TenantMemberLookupIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/TenantMemberLookupIntegrationTest.java)、[TenantMemberInviteE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/TenantMemberInviteE2ETest.java)
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **取代關係**: 本文件**取代** v1.1 全文。v1.1 的 §1-10（原 API-M17-001~007、APP-001~003）回應封包（`code: 200`／`timestamp`／`requestId` 固定出現）與實作不符；§11 存在編號衝突與章節內容物理錯位；成員管理 7 個既有端點自 Sprint 98 起從未被任何版本收錄；§12/13（Sprint 252/253 新增）內容本身正確，詳見下方修訂註記

> **⚠️ 修訂註記（Sprint 266）**：
> 1. **回應封包整體錯誤**：v1.1 多數範例寫 `"code": 200`（數字）＋固定出現的 `timestamp`／`requestId`，但實際 `ApiResponse` 是 `success: true` + `data`，`code` 是字串錯誤碼且只在失敗時出現，`requestId` 只在錯誤回應才帶入。此問題同時存在於 [API_M12_Dynamic_Pricing.md](API_M12_Dynamic_Pricing.md)；
> 2. **member-lookup 的編號衝突已解決（DEF-369）**：v1.1 總覽表把已棄用的 `POST /admin/tenants/:id/approve` 標為 `API-M17-008`，但章節標題又把同一編號用在 `GET /tenants/:id/members/lookup`。本版依 `TenantController` 宣告順序全面重新編號（見 §2），不再沿用 v1.1 任何編號；
> 3. **`API-M17-002`（現 002）內文與路徑不一致已修正**：v1.1 章節內文寫 `GET /api/v2/tenants`（缺 `/my`，全庫查無此端點），已改正為實際的 `/tenants/my`；
> 4. **`TenantController` 的 7 個成員管理端點首次收錄（DEF-377，新登記並於本輪一併解決）**：列表／邀請／我的待確認邀請／接受／拒絕／改角色／移除（`TenantController.java`:216-339）自 Sprint 98 起即存在、功能完整且有測試覆蓋，但 v1.0 起從未被任何版本的本文件描述，Sprint 248 新增 member-lookup 時雖已發現此缺口但刻意不補；
> 5. **新增 6 個 `AdminController` 端點（DEF-369）**：`GET .../{tenantId}`、`POST .../review`、`PUT .../{tenantId}/status`、`POST /admin/feature-toggles`、`DELETE .../feature-toggles/{featureKey}`、`GET .../{tenantId}/stats`；
> 6. **多處既有端點的回應欄位補齊**：`TenantDetailsResponse` 實際含 `contactPhone`／`purchaseOrderApprovalThreshold`／`updatedAt`；`FeatureToggleResponse` 實際含 `quotas`（數量配額）及每個 `features[]` 項目的 `category`／`status`／`requiresAdminReview`；`TenantUpdateRequest` 還可帶 `businessType`／`status`／`purchaseOrderApprovalThreshold`，且 `coverImageUrl` 是**完全死欄位**（DEF-193）；駁回申請的 `reason` 空白實際回 `E-9000`（Bean Validation），非 v1.1 寫的 `E-4001`
> 7. **§4.6 的 403 條件校正（Sprint 292，文件一致性檢查第五輪）**：Sprint 281（DEF-331(f)）把 `TenantService.updateFeatureToggle` 的檢查從「是該租戶有效成員」改為「是該租戶的店主（`STORE_OWNER`）」，但該次 commit 沒有同步本文件，§4.6 的失敗列仍寫「非該租戶成員」，讀起來像是 `STORE_STAFF` 也能通過。已改為如實描述；單元測試 `TenantServiceTest.updateFeatureToggle_notOwner_rejectedWithoutAudit` 佐證。本文件其他章節的同款「非該租戶成員」列本輪**未逐一核對**對應的 service 檢查是店主還是成員（Sprint 294 已逐一核對，見註記 8）
> 8. **§4.4／§4.8／§4.9／§4.13／§4.14 的權限行與 403 列、`E-4031` 總表校正（Sprint 294，補完註記 7 當時未核對的部分）**：逐一對照 `TenantService`——`updateTenant`、`inviteMember`、`lookupMemberCandidateByEmail`、`updateMemberRole`、`removeMember`（加上註記 7 的 `updateFeatureToggle`）六個寫入類方法的檢查都是 `existsByTenantIdAndUserIdAndStoreRole(…, STORE_OWNER)`，不是該租戶店主就拋 `E-4031`；只有 `getFeatureToggles`（§4.5）與 `getTenantMembers`（§4.7）是「有效成員（`ACTIVE`）」檢查。先前這五節寫「且限該租戶成員」「非該租戶成員」，讀起來像是僅為成員（如 `STORE_STAFF`）即可通過。§4.5、§4.7 本來就是成員檢查，文件正確，未動。**當時未驗證**：這些端點的 `@PreAuthorize` 先擋掉了哪些全域角色，以及 `ROLE_ADMIN` 通過註解後在 service 層的實際結果（service 只認該租戶的店主）——本註記只校正「service 層檢查的是店主不是成員」這一點；**Sprint 298 已對真實後端實測，見註記 9**
> 9. **全域角色對店主自助端點的實測矩陣（Sprint 298，補完註記 8 當時未驗證的部分）**：對真實後端（打包 JAR＋PostgreSQL＋Redis，`ddl-auto=validate`）以匿名、`BUYER`、`ADMIN`、`SUPER_ADMIN`、該租戶店主五種身分呼叫 `TenantController` 的 9 個自助端點，完整表格見 §2.1。要點：(1) **§2 總覽表的「權限」欄是 `@PreAuthorize` 的原文，不是實際可通過者**；(2) `SCOPE_store:read`／`SCOPE_store:write` 是死權限（`Permission` 枚舉沒有任何 `store:*`、簽發 JWT 不寫 `scope`／`scp` claim，讀碼確認）；(3) `ROLE_ADMIN` 通過註解，但除了 `GET /v2/tenants/my`（回自己的成員資格，空清單）之外一律被 service 層擋下（`E-4031`）；(4) `SUPER_ADMIN` 連註解都過不了（`E-1007`）——沒有角色階層，`ROLE_SUPER_ADMIN` 不等於註解清單內的 `ROLE_ADMIN`；(5) 匿名在 §4.2、§4.4 得到 403 `E-1007` 而不是 401，原因見 §2.1
> 10. **一般成員 `STORE_STAFF` 對店主自助端點的實測（Sprint 316，補完註記 9「範圍」欄留下的缺口，`DEF-438`）**：以本店的有效店員（`tenant_members.status = ACTIVE`、`store_role = STORE_STAFF`，JWT 角色 `STORE_STAFF`、`tenantId` 為該店鋪）呼叫 §2.1 的九個端點，**一律 `403 E-1007`**——方法層 `@PreAuthorize` 只放行 `SCOPE_store:*`（死權限）、`ROLE_STORE_OWNER`、`ROLE_ADMIN`，`ROLE_STORE_STAFF` 不在其中，請求在到達 Service 層之前就被擋下。先前 §4.6、§4.8、§4.9、§4.13、§4.14 寫的「一般成員亦為 403 `E-4031`」在狀態碼上對、錯誤碼上錯（`E-4031` 只有通過方法層、但不是該租戶店主者才會遇到，例如不是該店鋪成員的 `ADMIN`）；§4.5、§4.7 的「非該租戶成員」也不是店員會遇到的情境。**量測方式（兩種，結果完全一致）**：(1) 打包 JAR＋真實 PostgreSQL／Redis 的真實 HTTP——資料全由真實流程建立（店主開店 → 受邀人註冊 → 店主邀請為 `STORE_STAFF` → 受邀人接受 → 重新登入，JWT 角色與租戶已核對），店員與店主各發同樣的九個請求（一次性探針，未入庫）；(2) [TenantStaffAccessIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/TenantStaffAccessIntegrationTest.java)（真實 PostgreSQL／Redis＋完整 JWT／權限鏈，MockMvc）已把這張表機械化：店員九格一律 `403 E-1007`，店主對照組發同樣的九個請求都成功（八個 `200`、邀請 `201`），證明店員被擋是因為角色、不是路徑或請求本文寫錯。**同次一併更正**：§5 權限矩陣把「我的店鋪/功能/成員管理」對 `SUPER_ADMIN` 標 ✅，與 §2.1 自己的實測（`SUPER_ADMIN` 過不了方法層，403 `E-1007`）矛盾，已改為 ❌，並把「租戶成員」欄拆成店主與一般成員

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M17 |
| **模組名稱** | 租戶/店鋪管理 / Tenant Management |
| **描述** | 使用者申請開店、平台審核；店主管理店鋪資訊、功能開關、成員邀請；平台管理員管理所有租戶、功能開關、停權/終止；店鋪前台公開資料 |
| **API 前綴** | 後端的 context path 是 `/api`，所以下列路徑的完整網址是 `/api/v2/tenants/...`、`/api/v2/dashboard/tenants/...`、`/api/v2/admin/...`、`/api/v2/public/stores/...` |
| **多租戶隔離** | 店主自助端點的租戶檢查（`E-4031`）：寫入類（§4.4、§4.6、§4.8、§4.9、§4.13、§4.14）驗證呼叫者是該租戶的店主（`STORE_OWNER`），讀取類（§4.5、§4.7）驗證是該租戶的有效成員（Sprint 294 對照 `TenantService` 校正，見註記 8），各全域角色的實測結果見 §2.1（一般成員 `STORE_STAFF` 在方法安全層就被擋下，回 `E-1007`，到不了這兩種檢查，Sprint 316 實測）；平台管理端點（§4.17~4.30）一律 `hasRole('SUPER_ADMIN')`，Service 層不再額外篩租戶；店鋪前台（§4.15~4.16）完全匿名、只回傳 `status=ACTIVE` 的租戶 |
| **範圍外** | 無（本輪已補齊先前所有已知文件缺口） |

### 1.2 設計重點

1. **路徑必須是 `/v2`，不是 `/api/v2`**：`server.servlet.context-path` 已是 `/api`，誤寫 `@RequestMapping("/api/v2")` 會落在 `/api/api/v2/**`，與不存在的路徑同一回應（DEF-061 歷史教訓）。
2. **開店審核走 `API-M17-APP-*`（§4.22~4.24），不走已棄用的 §4.20/4.21**：審核對象是 `tenant_applications`，`Tenant` 只在核准那一刻才建立且建立即 `ACTIVE`，要求 `Tenant.status == PENDING_REVIEW` 的舊端點在生產環境永遠等不到資料，但仍存在於後端且被歷史測試引用，端點與編號保留不刪除。
3. **核准開店申請有併發防護**（DEF-114/115，Sprint 137）：用 `updateStatusIfCurrent`（`PENDING→APPROVED`/`REJECTED`）原子搶占審核權，避免兩個併發核准請求各自建立出孤兒 `Tenant`/membership；搶輸與「狀態本就非 PENDING」回同一個錯誤碼 `E-2007`。
4. **功能開關的「自助申請」與「Admin 直接設定」是兩條獨立路徑**：店主透過 §4.6（`PUT .../features/:feature`）送出的是**申請**（`requiresApproval=true` 的功能會卡在 `PENDING_APPROVAL`，不會立即啟用）；Admin 透過 §4.29（`PUT /admin/tenants/:id/features/:feature`）或 §4.26（`POST /admin/feature-toggles`）直接套用最終決定。這兩條 Admin 路徑本身也是**兩個不同的 service 方法**（`updateTenantFeatureToggle` vs `setFeatureToggle`），功能重疊但實作不同。
5. **成員邀請需雙方確認**（Sprint 98）：店主邀請（§4.8）只建立 `INVITED` 狀態的成員記錄，被邀請人需另外呼叫接受（§4.11）才正式生效；拒絕（§4.12）則移除該記錄。只有 `STORE_STAFF` 角色可透過此端點邀請（`STORE_OWNER`/`STORE_MANAGER` 等需要其他機制）。
6. **店鋪前台兩個端點刻意用相同的 404 掩蓋租戶狀態**：`slug` 不存在或租戶非 `ACTIVE` 回應相同的 `E-2000`，不區分，避免成為列舉租戶狀態的側通道。
7. **`TenantUpdateRequest.coverImageUrl` 是死欄位**：`Tenant` 實體無對應欄位，送出的值會被靜默丟棄（DEF-193）。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M17-001 | 使用者申請開店，平台審核核准/駁回 | §4.1、4.22~4.24 | ✅ 已實作 |
| US-M17-002 | 店主查詢/管理自己的店鋪資訊 | §4.2~4.4 | ✅ 已實作 |
| US-M17-003 | 店主管理功能開關（申請啟用） | §4.5~4.6 | ✅ 已實作 |
| US-M17-004 | 平台管理員查詢/管理所有租戶 | §4.17~4.30 | ✅ 已實作 |
| US-M17-005 | 店主邀請/管理店鋪成員，被邀請人接受/拒絕 | §4.7~4.14 | ✅ 已實作 |
| US-M17-009 | 訪客瀏覽店鋪前台公開頁面 | §4.15~4.16 | ✅ 已實作 |

---

## 2. 端點總覽 / Endpoint Overview

**§4.1~4.16（`TenantController`／`StorefrontController`）受 `ApiRouteDocDriftTest` 機械防漂移保護；§4.17~4.30（`AdminController`）因該類別混雜大量 M17 以外端點，不在保護範圍內，見 §7。**

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M17-001 | POST | `/v2/tenants/apply` | 申請開店 | 匿名 |
| API-M17-002 | GET | `/v2/tenants/my` | 取得我的店鋪列表 | `SCOPE_store:read`／`ROLE_STORE_OWNER`／`ROLE_ADMIN` |
| API-M17-003 | GET | `/v2/tenants/{id}` | 店鋪詳情 | 匿名（任何人） |
| API-M17-004 | PUT | `/v2/tenants/{id}` | 更新店鋪資訊 | `SCOPE_store:write`／`ROLE_STORE_OWNER`／`ROLE_ADMIN` |
| API-M17-005 | GET | `/v2/dashboard/tenants/features` | 取得功能開關狀態 | `SCOPE_store:read`／`ROLE_STORE_OWNER`／`ROLE_ADMIN` |
| API-M17-006 | PUT | `/v2/dashboard/tenants/features/{feature}` | 申請更新功能開關 | `SCOPE_store:write`／`ROLE_STORE_OWNER`／`ROLE_ADMIN` |
| API-M17-007 | GET | `/v2/tenants/{id}/members` | 成員列表 | `SCOPE_store:read`／`ROLE_STORE_OWNER`／`ROLE_ADMIN` |
| API-M17-008 | POST | `/v2/tenants/{id}/members/invite` | 邀請成員 | `SCOPE_store:write`／`ROLE_STORE_OWNER`／`ROLE_ADMIN` |
| API-M17-009 | GET | `/v2/tenants/{id}/members/lookup` | 以 email 查詢可邀請的使用者 | `SCOPE_store:read`／`ROLE_STORE_OWNER`／`ROLE_ADMIN` |
| API-M17-010 | GET | `/v2/tenants/invites/my` | 我的待確認邀請列表 | 已登入 |
| API-M17-011 | POST | `/v2/tenants/{id}/members/invite/accept` | 接受邀請 | 已登入 |
| API-M17-012 | POST | `/v2/tenants/{id}/members/invite/decline` | 拒絕邀請 | 已登入 |
| API-M17-013 | PUT | `/v2/tenants/{id}/members/{userId}/role` | 更新成員角色 | `SCOPE_store:write`／`ROLE_STORE_OWNER`／`ROLE_ADMIN` |
| API-M17-014 | DELETE | `/v2/tenants/{id}/members/{userId}` | 移除成員 | `SCOPE_store:write`／`ROLE_STORE_OWNER`／`ROLE_ADMIN` |
| API-M17-015 | GET | `/v2/public/stores/{slug}` | 店鋪前台公開資料 | 匿名 |
| API-M17-016 | GET | `/v2/public/stores/{slug}/listings` | 店鋪前台商品/房源列表 | 匿名 |
| API-M17-017 | GET | `/v2/admin/tenants` | 平台租戶列表 | `hasRole('SUPER_ADMIN')` |
| API-M17-018 | GET | `/v2/admin/tenants/{tenantId}` | Admin 查詢單一租戶 | `hasRole('SUPER_ADMIN')` |
| API-M17-019 | POST | `/v2/admin/tenants/review` | Admin 審核租戶（舊流程之一） | `hasRole('SUPER_ADMIN')` |
| ~~API-M17-020~~ | POST | `/v2/admin/tenants/{id}/approve` | ⚠️ 舊流程，生產不可達 | `hasRole('SUPER_ADMIN')` |
| ~~API-M17-021~~ | POST | `/v2/admin/tenants/{id}/reject` | ⚠️ 舊流程，生產不可達 | `hasRole('SUPER_ADMIN')` |
| API-M17-022 | GET | `/v2/admin/tenant-applications` | 待審核開店申請列表 | `hasRole('SUPER_ADMIN')` |
| API-M17-023 | POST | `/v2/admin/tenant-applications/{applicationId}/approve` | 核准開店申請 | `hasRole('SUPER_ADMIN')` |
| API-M17-024 | POST | `/v2/admin/tenant-applications/{applicationId}/reject` | 駁回開店申請 | `hasRole('SUPER_ADMIN')` |
| API-M17-025 | PUT | `/v2/admin/tenants/{tenantId}/status` | Admin 變更租戶狀態 | `hasRole('SUPER_ADMIN')` |
| API-M17-026 | POST | `/v2/admin/feature-toggles` | Admin 新建/更新功能開關 | `hasRole('SUPER_ADMIN')` |
| API-M17-027 | GET | `/v2/admin/tenants/{tenantId}/features` | Admin 取得指定店鋪完整功能目錄 | `hasRole('SUPER_ADMIN')` |
| API-M17-028 | DELETE | `/v2/admin/tenants/{tenantId}/feature-toggles/{featureKey}` | Admin 刪除功能開關 | `hasRole('SUPER_ADMIN')` |
| API-M17-029 | PUT | `/v2/admin/tenants/{id}/features/{feature}` | Admin 直接設定單一功能開關 | `hasRole('SUPER_ADMIN')` |
| API-M17-030 | GET | `/v2/admin/tenants/{tenantId}/stats` | Admin 查詢租戶統計 | `hasRole('SUPER_ADMIN')` |

**編號不沿用 v1.1**：依 `TenantController`／`StorefrontController`／`AdminController` 的宣告順序全面重新編號，不與 v1.1 任何編號對應。

### 2.1 全域角色對店主自助端點的實測矩陣（Sprint 298）

**上面總覽表的「權限」欄是 `@PreAuthorize` 的原文，不是實際可通過者。** 下表是對真實後端（打包 JAR＋PostgreSQL＋Redis，`ddl-auto=validate`）實際呼叫的結果。身分：匿名；`BUYER`（新註冊使用者）；`ADMIN`（新註冊使用者，把 `users.role` 改成 `ADMIN` 後登入）；`SUPER_ADMIN`（Flyway 種入的 `admin@nextkey.local`）；`STORE_STAFF`（Sprint 316 新增：本店的有效店員，由店主邀請、受邀人接受而來）；店主（該租戶的 `STORE_OWNER`）。格子內是 HTTP 狀態與錯誤碼：`E-1007` 是方法層 `@PreAuthorize` 擋下（權限不足），`E-4031` 是 service 層的租戶檢查（不是該租戶的店主／成員），`E-1000` 是 URL 層要求登入。

| 端點 | 匿名 | `BUYER` | `ADMIN` | `SUPER_ADMIN` | `STORE_STAFF` | 店主 |
|------|------|---------|---------|---------------|---------------|------|
| §4.2 `GET /v2/tenants/my` | 403 `E-1007` | 403 `E-1007` | **200**（空清單） | 403 `E-1007` | 403 `E-1007` | 200 |
| §4.4 `PUT /v2/tenants/{id}` | 403 `E-1007` | 403 `E-1007` | 403 `E-4031` | 403 `E-1007` | 403 `E-1007` | 200 |
| §4.5 `GET /v2/dashboard/tenants/features` | 401 `E-1000` | 403 `E-1007` | 403 `E-4031` | 403 `E-1007` | 403 `E-1007` | 200 |
| §4.6 `PUT /v2/dashboard/tenants/features/{feature}` | 401 `E-1000` | 403 `E-1007` | 403 `E-4031` | 403 `E-1007` | 403 `E-1007` | 200 |
| §4.7 `GET /v2/tenants/{id}/members` | 401 `E-1000` | 403 `E-1007` | 403 `E-4031` | 403 `E-1007` | 403 `E-1007` | 200 |
| §4.8 `POST /v2/tenants/{id}/members/invite` | 401 `E-1000` | 403 `E-1007` | 403 `E-4031` | 403 `E-1007` | 403 `E-1007` | 201 |
| §4.9 `GET /v2/tenants/{id}/members/lookup` | 401 `E-1000` | 403 `E-1007` | 403 `E-4031` | 403 `E-1007` | 403 `E-1007` | 200 |
| §4.13 `PUT /v2/tenants/{id}/members/{userId}/role` | 401 `E-1000` | 403 `E-1007` | 403 `E-4031` | 403 `E-1007` | 403 `E-1007` | 200 |
| §4.14 `DELETE /v2/tenants/{id}/members/{userId}` | 401 `E-1000` | 403 `E-1007` | 403 `E-4031` | 403 `E-1007` | 403 `E-1007` | 200 |

**怎麼讀這張表**：

- **匿名在 §4.2、§4.4 得到 403 而不是 401**：`SecurityConfig` 的 `.requestMatchers("/v2/tenants/{id}").permitAll()`（為了 §4.3 公開）不限 HTTP 方法，而且 `{id}` 也符合 `/v2/tenants/my`，所以這兩個請求在 URL 層被放行，由方法層註解擋下回 403 `E-1007`；其他路徑（`/dashboard/...`、`/tenants/{id}/members...`）不符合該樣式，落到 `anyRequest().authenticated()` 回 401 `E-1000`。依賴「401 才導向登入」的用戶端，在這兩個端點上不會被觸發。
- **`ROLE_ADMIN` 的實際效果**：通過註解，但 service 層只認該租戶的店主（寫入）或有效成員（讀取），所以不是該店鋪成員的 `ADMIN` 一律 `E-4031`。唯一的 200 是 `GET /v2/tenants/my`，它只回呼叫者自己的成員資格（這個測試用的 `ADMIN` 沒有任何成員資格，所以是空清單，沒有洩漏別人的店鋪）。平台管理員要管理店鋪走 §4.17～4.30。
- **`STORE_STAFF` 同樣過不了註解（Sprint 316）**：店員是本店的有效成員，但 `@PreAuthorize` 看的是 JWT 的角色 authority，`ROLE_STORE_STAFF` 不在 `SCOPE_store:*`／`ROLE_STORE_OWNER`／`ROLE_ADMIN` 之內，九個端點一律在方法層回 `E-1007`，到不了 Service 層的任何檢查——所以 §4.5、§4.7 的「有效成員」檢查與寫入類端點的「店主」檢查（`E-4031`）都不是店員會遇到的錯誤碼。**方法層是 §4.2、§4.5、§4.7 在店員面前唯一的一道牆**：突變實測（Sprint 316，把九個端點的註解全部放寬到 `ROLE_STORE_STAFF`）這三個端點店員會得 `200`，因為 Service 層只要求「有效成員」（§4.2 只回呼叫者自己的成員資格），而店員正是；其餘六個（§4.4、§4.6、§4.8、§4.9、§4.13、§4.14，含雖是 `GET` 的 §4.9）放寬註解後仍有 Service 層的店主檢查接著擋下，回 `E-4031`。
- **`SUPER_ADMIN` 過不了註解**：全庫沒有 `RoleHierarchy`，`ROLE_SUPER_ADMIN` 不等於註解清單內的 `ROLE_ADMIN`；`SUPER_ADMIN` 雖然擁有 `Permission` 全集，這些註解看的是角色 authority，不看權限碼。
- **`SCOPE_store:read`／`SCOPE_store:write` 無法量測**，因為沒有任何角色或 token 會帶這兩個 authority（`Permission` 枚舉沒有 `store:*`、簽發 JWT 不寫 `scope`／`scp` claim；讀碼確認，沒有實際嘗試偽造 token）。
- **沒有副作用**：非店主角色的每一次呼叫都被擋下，沒有任何一次意外成功；店主的 `invite`（201）→ `role`（200）→ `remove`（200）依序操作同一位被邀請的買家，屬於預期的狀態變化。
- **範圍**：Sprint 298 只量測了「該租戶的店主」與「不是成員的 `ADMIN`」；「同店 `STORE_STAFF`」已於 Sprint 316 補上（上表 `STORE_STAFF` 欄，見註記 10）。「另一間店鋪的店主」仍沒有在這次矩陣中量測；service 層「呼叫者不是該租戶的店主」這個分支另有 `TenantServiceTest` 的單元測試（Sprint 281、294）。

---

## 3. 資料模型 / Data Model

### 3.1 `Tenant`（表 `tenants`，摘要）

`id`／`name`／`slug`（唯一，`^[a-z0-9]+(-[a-z0-9]+)*$`）／`description`／`status`（`PENDING_REVIEW`／`ACTIVE`／`SUSPENDED`／`TERMINATED`／`REJECTED`）／`contactEmail`／`contactPhone`／`logoUrl`／`coverImageUrl`／`purchaseOrderApprovalThreshold`／`metadata`（含 `businessType`）／`createdAt`／`updatedAt`。完整欄位見 [SRD_Database_Schema.md](../SRD_Database_Schema.md)。

### 3.2 `TenantApplication`（表 `tenant_applications`）

`id`／`storeName`／`storeDescription`／`businessType`／`contactEmail`／`contactPhone`／`businessLicenseUrl`／`userId`（可為 `null`，匿名申請）／`status`（`PENDING`／`APPROVED`／`REJECTED`）／`tenantId`（核准後回填）／`reviewedAt`／`reviewedBy`／`rejectionReason`／`submittedAt`。

### 3.3 `TenantMember`（表 `tenant_members`）

`id`／`tenantId`／`userId`／`storeRole`（`STORE_OWNER`／`STORE_STAFF`／`STORE_MANAGER` 等）／`status`（`INVITED`／`ACTIVE`／`REMOVED`，PRD §8.2.3）／`joinedAt`。

### 3.4 `TenantStatus` 狀態機

```
PENDING_REVIEW ──approve（§4.23）──→ ACTIVE ──suspend（§4.25）──→ SUSPENDED ──reactivate──→ ACTIVE
                                        │                              │
                                        └──terminate（§4.25）──→ TERMINATED ←──terminate──┘
PENDING_REVIEW ──reject（§4.24）──→ REJECTED（終態，可重新申請）
```

只允許 `ACTIVE→SUSPENDED`、`SUSPENDED→ACTIVE`、`SUSPENDED→TERMINATED`（`AdminService.validateStatusTransition`）；轉 `SUSPENDED` 會連動下架該租戶所有上架中商品/房源；用 `updateStatusIfCurrent` 做 CAS 併發防護，衝突回 `E-9000`。

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `POST /v2/tenants/apply` — 申請開店

**權限**：匿名可呼叫（`principal` 可為 `null`）。

**請求**：`storeName`(必填，2-100字)、`storeDescription`(選填，≤1000字)、`businessType`(必填，`RETAIL_ONLY`/`BOOKING_ONLY`/`HYBRID`)、`contactEmail`(必填)、`contactPhone`(選填，≤20字)、`businessLicenseUrl`(選填，≤500字，禁止 `javascript:`/`data:`/`vbscript:`/`file:` 協定)

**回應**：`applicationId`／`storeName`／`businessType`／`status`／`statusDescription`／`submittedAt`／`estimatedReviewDays`

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 已登入使用者已有 `PENDING` 申請 | 409 | `E-4092` |

### 4.2 `GET /v2/tenants/my` — 取得我的店鋪列表

**權限**：`SCOPE_store:read`／`ROLE_STORE_OWNER`／`ROLE_ADMIN`。回應 `{tenants: List<TenantListResponse>}`（`tenantId`／`storeName`／`businessType`／`status`／`role`／`memberCount`／`features: Map<String,Boolean>`／`createdAt`）。

### 4.3 `GET /v2/tenants/{id}` — 店鋪詳情

**權限**：`@PreAuthorize("isAuthenticated() or true")`——**實質等同匿名可呼叫**（`or true` 恆真）。`status` 非 `ACTIVE` 時回應**精簡版**（僅 `tenantId`／`storeName`／`slug`／`businessType`／`status`／`createdAt`）。

**非 `ACTIVE` 店鋪的例外（Sprint 315，`DEF-432`③）**：呼叫者是該店鋪的**有效成員**（`tenant_members.status = ACTIVE`，店主、店員皆是）時，不論店鋪狀態（`PENDING_REVIEW`／`REJECTED`／`SUSPENDED`／`TERMINATED`）都回**完整版**——店鋪編輯表單要靠這支預填聯絡信箱、電話、描述與採購審批門檻，而 FRD US-M17-003 邊界條件允許非 `ACTIVE` 的店鋪更新（§4.4 的 `PUT` 本來就不檢查店鋪狀態，修復前只有前端按鈕與這支精簡回應擋住）。匿名、已登入但不是該店鋪成員、已被移除（`REMOVED`）、只是受邀還沒接受（`INVITED`）的人，在非 `ACTIVE` 時**仍是精簡版**，公開端點不新增任何對外暴露；`purchaseOrderApprovalThreshold` 仍只對有效成員可見（`DEF-264`）。真實 PostgreSQL＋HTTP 鏈實測（5 種店鋪狀態 x 6 種觀看者共 30 格）：`ACTIVE` 完整版公開（門檻限成員）；非 `ACTIVE` 只有店主與店員看到完整版與門檻。放寬的是**讀取**，不是寫入：非店主對非 `ACTIVE` 店鋪的 `PUT` 仍被擋下（4xx、資料不變）。

**回應**（`ACTIVE`，或呼叫者是該店鋪有效成員時的完整版）：`tenantId`／`storeName`／`slug`／`storeDescription`／`businessType`／`status`／`contactEmail`／`contactPhone`／`logoUrl`／`coverImageUrl`／`purchaseOrderApprovalThreshold`／`member{displayName,avatarUrl,joinedAt}`（店主，查無則 `null`）／`stats{listingCount,totalSales,rating}`／`createdAt`／`updatedAt`

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 租戶不存在 | 404 | `E-2000` |

### 4.4 `PUT /v2/tenants/{id}` — 更新店鋪資訊

**權限**：`SCOPE_store:write`／`ROLE_STORE_OWNER`／`ROLE_ADMIN`，且呼叫者須為該租戶的店主（`STORE_OWNER`）——僅是成員（如 `STORE_STAFF`）不夠。

**請求**（部分更新）：`storeName`(選填,2-100字)、`slug`(選填，格式同 §3.1，衝突回 `E-2011`)、`storeDescription`(選填,≤1000字)、`contactEmail`(選填)、`contactPhone`(選填,≤20字)、`logoUrl`(選填,≤500字，協定限制同 §4.1)、`coverImageUrl`(選填——**死欄位，送出必被丟棄**)、`businessType`(選填)、`status`(選填，**不建議使用**，狀態轉換應走 §4.25 以套用正確的狀態機驗證)、`purchaseOrderApprovalThreshold`(選填，≥0)

**部分更新的語意（Sprint 314，`TenantService.applyTenantUpdate` 讀碼＋`E2E-M17-004-03／04` 實測）**：欄位省略（`null`）＝不變更；`storeDescription` 送**空字串＝清空**（`PUT` 回應與之後 `GET` 的 `storeDescription` 都是 `""`，不是 `null`；Sprint 314 實測），送非空字串＝改成該值。店主端編輯表單（Sprint 314 起有「店鋪描述」欄位，≤ 1000 字）因此只在店主改過描述時才送出這個欄位；開店申請（§4.1）同理，描述留空就不送（後端存成沒填，不是空字串）。

**回應**：`tenantId`／`storeName`／`storeDescription`／`contactEmail`／`purchaseOrderApprovalThreshold`／`updatedAt`

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 呼叫者不是該租戶的店主（`STORE_OWNER`）。角色為 `STORE_STAFF` 的一般成員在方法安全層（`@PreAuthorize`）就被擋下，回 `E-1007`（Sprint 314 實測：店員在編輯表單按儲存得 403／`E-1007`）；通過方法安全層但不是該租戶店主者（例如其他店鋪的 `ADMIN`，見 §2.1）才走到 Service 層的檢查，回 `E-4031` | 403 | `E-1007`／`E-4031` |
| `slug` 與其他租戶衝突 | 409 | `E-2011` |

### 4.5 `GET /v2/dashboard/tenants/features` — 取得功能開關狀態

**權限**：`SCOPE_store:read`／`ROLE_STORE_OWNER`／`ROLE_ADMIN`，租戶由 `X-Tenant-ID`/principal 解析，解析不到回 `E-2003`。

**回應**：`tenantId`／`features: List<FeatureInfo>`（`featureKey`／`featureName`／`description`／`isEnabled`／`enabledAt`／`requestedAt`／`category`／`status`：`ACTIVE`/`PENDING`/`INACTIVE`／`requiresAdminReview`）／`quotas: List<QuotaInfo>`（`featureKey`／`featureName`／`limit`／`currentUsage`，數量型配額如 `MAX_PRODUCTS`，與布林開關分開回傳）

**`status` 的判定（Sprint 311，`DEF-429` 修復）**：已啟用 → `ACTIVE`；**需審核、未啟用、且店主送出過啟用申請（`requestedAt` 有值）→ `PENDING`**；其餘 → `INACTIVE`（從未申請、申請被駁回或撤回、停用，以及不需審核的單純停用）。`requestedAt` 只在店主送出需審核功能的啟用申請時寫入（§4.6）；管理員核准或駁回（§4.29）、店主撤回或關閉時清除；功能已啟用時回應不帶申請時間。核准開店時系統替六個功能建的預設資料列（`AdminService.initializeFeatureToggles`；三個需審核的預設 `isEnabled=false`）沒有申請時間，所以**全新店鋪沒申請過也是 `INACTIVE`，不是 `PENDING`**；Admin 功能頁的「N 項功能待審核」（`requiresAdminReview && status==='PENDING'`）因此只算店主真的申請過的。**遷移注意**：V95 新增 `requested_at` 時既有資料列一律為 `NULL`（預設列與真申請在既有資料裡無法分辨），遷移前已送出、尚未被核准的申請會變成 `INACTIVE`，店主需要重新送出。（修復前，只要有資料列、需審核、未啟用就判 `PENDING`，全新店鋪會顯示 3 項待審核，`requestedAt` 是資料列建立時間。）

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 租戶上下文不明 | 403 | `E-2003` |
| 通過方法層、但不是該租戶有效成員者（例如不是該店鋪成員的 `ADMIN`） | 403 | `E-4031` |
| 一般成員（`STORE_STAFF`）：方法層就被擋下，到不了 Service 層的成員檢查（Sprint 316 實測；先前此節沒有這一列，讀起來像店員會通過成員檢查） | 403 | `E-1007` |

### 4.6 `PUT /v2/dashboard/tenants/features/{feature}` — 申請更新功能開關

**權限**：`SCOPE_store:write`／`ROLE_STORE_OWNER`／`ROLE_ADMIN`。**自助申請**路徑（見 §1.2 第 4 點）。

**請求**：`enabled`(必填，Boolean)

**回應**：`featureKey`／`previousState`／`newState`／`status`／`statusDescription`——`requiresApproval=true` 的功能提交啟用申請後 `newState` 仍為舊值、`status="PENDING_APPROVAL"`

**`status` 的判定（Sprint 314／DEF-434）**：`status` 說的是**這次請求的結果**，只由請求的 `enabled`、功能是否需審核、請求前是否已啟用決定：

| 請求 | 條件 | `status` | `statusDescription` | `newState` |
|------|------|----------|---------------------|:----------:|
| `enabled=true` | 功能需審核，且請求前未啟用 | `PENDING_APPROVAL` | `Feature request submitted, pending platform approval` | 維持 `false` |
| `enabled=true` | 其餘（免審核功能；或需審核功能已啟用中再確認一次） | `ENABLED` | `Feature enabled` | `true` |
| `enabled=false` | 不論請求前是否啟用、功能需不需要審核 | `DISABLED` | `Feature disabled` | `false` |

修復前（`DEF-434`）：`enabled=false` 而請求前**本來就未啟用**時，回應是 `PENDING_APPROVAL`／`Feature request submitted`——不論功能需不需要審核（免審核功能連送兩次 `enabled=false`，第二次也說「已送出申請」）。實際效果只是「沒有變化」或「撤回待審核的申請」（清掉申請時間，下方），回應卻說「已送出申請」，與接著 `GET`（§4.5）看到的 `INACTIVE` 不一致。正常操作不會觸發這種請求（開關顯示為關時，點擊送的是 `enabled=true`，見 `features/page.tsx` 的 `onCheckedChange(!checked)`）；直接呼叫 API，或畫面停留在過期狀態（顯示為開、實際已被別處關掉）時再點擊才遇得到——兩種情況前端都以回應的 `newState` 更新畫面，修復前後畫面行為相同。

**申請時間（Sprint 311／DEF-429）**：送出需審核功能的啟用申請時，資料列的 `requested_at` 記下申請時間；已在待審核時再送一次，保留最早的時間（等待時間不被重置）；店主送 `enabled=false`（撤回）、關閉已核准的功能、或啟用不需審核的功能，一律清掉申請時間。畫面應以本回應的 `newState`／`status` 更新（`PENDING_APPROVAL` 表示「已送出、尚未生效」），不要用送出的 `enabled` 推測——修復前前端用請求值更新，點了需審核功能會立刻顯示開關已開、提示「已啟用」，重新整理後又變回關閉（`DEF-428`）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 租戶上下文不明 | 403 | `E-2003` |
| 呼叫者通過方法層、但不是該租戶的店主（`STORE_OWNER`），例如 `ADMIN`（Sprint 281，DEF-331(f) 起 Service 層驗證店主；此前只驗證是有效成員） | 403 | `E-4031` |
| 一般成員（`STORE_STAFF`）：方法層就被擋下，到不了 Service 層（Sprint 316 實測；先前此列寫 `E-4031`） | 403 | `E-1007` |
| `feature` 不是已知鍵或是數量配額非開關 | 500 | `E-9000` |

### 4.7 `GET /v2/tenants/{id}/members` — 成員列表

**權限**：`SCOPE_store:read`／`ROLE_STORE_OWNER`／`ROLE_ADMIN`，且限該租戶成員。

**回應**：`{members: List<TenantMemberResponse>}`（`userId`／`displayName`／`email`／`avatarUrl`／`role`／`status`：`INVITED`/`ACTIVE`/`REMOVED`／`joinedAt`）

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 通過方法層、但不是該租戶有效成員者（例如不是該店鋪成員的 `ADMIN`） | 403 | `E-4031` |
| 一般成員（`STORE_STAFF`）：方法層就被擋下，到不了 Service 層的成員檢查（Sprint 316 實測；先前此節沒有這一列，讀起來像店員會通過成員檢查） | 403 | `E-1007` |

### 4.8 `POST /v2/tenants/{id}/members/invite` — 邀請成員

**權限**：`SCOPE_store:write`／`ROLE_STORE_OWNER`／`ROLE_ADMIN`，且呼叫者須為該租戶的店主（`STORE_OWNER`）——僅是成員（如 `STORE_STAFF`）不夠。僅建立 `INVITED` 狀態記錄，需被邀請人另呼叫 §4.11 確認（見 §1.2 第 5 點）。**只允許邀請 `STORE_STAFF` 角色**。

**請求**：`userId`(必填)、`role`(必填，僅接受 `STORE_STAFF`)

**回應**：`TenantMemberResponse`（見 §4.7，狀態 `INVITED`）

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 呼叫者通過方法層、但不是該租戶的店主（`STORE_OWNER`），例如 `ADMIN` | 403 | `E-4031` |
| 一般成員（`STORE_STAFF`）：方法層就被擋下，到不了 Service 層（Sprint 316 實測；先前此列寫 `E-4031`） | 403 | `E-1007` |
| `role` 非 `STORE_STAFF` | 400 | `E-1001` |
| 該使用者已是成員或已有待確認邀請 | 409 | `E-4092` |

### 4.9 `GET /v2/tenants/{id}/members/lookup` — 以 email 查詢可邀請的使用者

**權限**：`SCOPE_store:read`／`ROLE_STORE_OWNER`／`ROLE_ADMIN`，且呼叫者須為該租戶的店主（`STORE_OWNER`）——僅是成員（如 `STORE_STAFF`）不夠。邀請表單（§4.8）只知道對方 email，PRD §9.11 的邀請端點僅接受 `userId`，本端點解析 email → `userId`。

**請求**：query 參數 `email`(必填)

**回應**：`userId`／`displayName`／`email`／`avatarUrl`

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 呼叫者通過方法層、但不是該租戶的店主（`STORE_OWNER`），例如 `ADMIN` | 403 | `E-4031` |
| 一般成員（`STORE_STAFF`）：方法層就被擋下，到不了 Service 層（Sprint 316 實測；先前此列寫 `E-4031`） | 403 | `E-1007` |
| 查無此 email 對應的使用者 | 403 | `E-2001`（罐頭訊息「租戶未啟用」與實際情境不符，前端已覆寫顯示文字） |

### 4.10 `GET /v2/tenants/invites/my` — 我的待確認邀請列表

**權限**：已登入即可（跨租戶，查呼叫者自己收到的所有邀請）。**回應**：`{invites: List<TenantInviteResponse>}`（`memberId`／`tenantId`／`tenantName`／`role`／`invitedAt`）。

### 4.11 `POST /v2/tenants/{id}/members/invite/accept` — 接受邀請

**權限**：已登入即可。將呼叫者對該租戶的 `INVITED` 記錄轉為正式成員（`ACTIVE`）。

**回應**：`TenantMemberResponse`（狀態轉為 `ACTIVE`）

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 查無待確認邀請 | 403 | `E-2002` |

### 4.12 `POST /v2/tenants/{id}/members/invite/decline` — 拒絕邀請

**權限**：已登入即可。移除該筆 `INVITED` 記錄。無回應內容。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 查無待確認邀請 | 403 | `E-2002` |

### 4.13 `PUT /v2/tenants/{id}/members/{userId}/role` — 更新成員角色

**權限**：`SCOPE_store:write`／`ROLE_STORE_OWNER`／`ROLE_ADMIN`，且呼叫者須為該租戶的店主（`STORE_OWNER`）——僅是成員（如 `STORE_STAFF`）不夠。**只能改為 `STORE_STAFF`**（與邀請同限制）；**不能變更店主（`STORE_OWNER`）的角色**。

**請求**：`{"role": "STORE_STAFF"}`

**回應**：`TenantMemberResponse`

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 呼叫者通過方法層、但不是該租戶的店主（`STORE_OWNER`），例如 `ADMIN` | 403 | `E-4031` |
| 一般成員（`STORE_STAFF`）：方法層就被擋下，到不了 Service 層（Sprint 316 實測；先前此列寫 `E-4031`） | 403 | `E-1007` |
| `role` 缺失 | 400 | `E-1001` |
| `role` 非 `STORE_STAFF` | 400 | `E-1001` |
| 目標為店主 | 403 | `E-4031` |
| 目標成員不存在 | 403 | `E-2002` |

### 4.14 `DELETE /v2/tenants/{id}/members/{userId}` — 移除成員

**權限**：`SCOPE_store:write`／`ROLE_STORE_OWNER`／`ROLE_ADMIN`，且呼叫者須為該租戶的店主（`STORE_OWNER`）——僅是成員（如 `STORE_STAFF`）不夠。**不能移除自己**。無回應內容（成功訊息於 `message` 欄位）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 呼叫者通過方法層、但不是該租戶的店主（`STORE_OWNER`），例如 `ADMIN` | 403 | `E-4031` |
| 一般成員（`STORE_STAFF`）：方法層就被擋下，到不了 Service 層（Sprint 316 實測；先前此列寫 `E-4031`） | 403 | `E-1007` |
| 移除對象是自己 | 403 | `E-4031` |

### 4.15 `GET /v2/public/stores/{slug}` — 店鋪前台公開資料

**權限**：匿名。只回傳 `status=ACTIVE` 的租戶。

**回應**：`tenantId`／`slug`／`name`／`description`／`logoUrl`

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `slug` 不存在，或租戶非 `ACTIVE` | 404 | `E-2000`（兩種情況刻意回應相同，見 §1.2 第 6 點） |

### 4.16 `GET /v2/public/stores/{slug}/listings` — 店鋪前台商品/房源列表

**權限**：匿名。查詢參數：`type`(選填，`PRODUCT`/`ROOM`)、`featured`(選填，`true` 只回精選項目)、`page`(預設0)、`size`(預設20，上限100)。回應為 Spring Data `Page`（`content`：既有公開 `ListingResponse` 形狀，見 [API_M02_Listing_Center.md](API_M02_Listing_Center.md)）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 同 §4.15 | 404 | `E-2000` |

> **貼文列表不另建端點**：`/stores/[slug]/blog` 前端取得 `slug` 對應的 `tenantId`（呼叫 §4.15）後，直接呼叫既有公開端點 `GET /v2/posts?tenantId=`（`PostController`，已 `permitAll`），不重複實作。

---

**以下 §4.17~4.30 為 `AdminController` 端點（不受本文件機械防漂移保護，見 §7）：**

### 4.17 平台租戶列表

**端點（不受機械防漂移保護，見 §7）**：GET /v2/admin/tenants

**權限**：`hasRole('SUPER_ADMIN')`。查詢參數：`status`／`businessType`(選填篩選)、`page`(預設1)、`size`(預設20)。

### 4.18 Admin 查詢單一租戶

**端點（不受機械防漂移保護，見 §7）**：GET /v2/admin/tenants/{tenantId}

**權限**：`hasRole('SUPER_ADMIN')`。**回應**：`TenantResponse`（`tenantId`／`name`／`status`／`userCount`／`listingCount`／`createdAt`／`updatedAt`）。

### 4.19 Admin 審核租戶

**端點（不受機械防漂移保護，見 §7）**：POST /v2/admin/tenants/review

**權限**：`hasRole('SUPER_ADMIN')`。**此為獨立於 §4.22~4.24 的另一條舊流程**（`adminService.reviewTenant`，直接依 `decision` 設定 `Tenant.status`，不檢查現有狀態、不初始化 Feature Toggle），與審核 `tenant_applications` 的序列是兩套不同機制。

**請求**：`tenantId`(必填)、`decision`(必填，`APPROVE`/`REJECT`)、`reason`(選填)

**回應**：`tenantId`／`status`／`reviewedAt`／`reviewedBy`

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `decision` 非 `APPROVE`/`REJECT` | 500 | `E-9000` |

### 4.20 （已棄用）舊流程核准租戶

**端點（不受機械防漂移保護，見 §7）**：POST /v2/admin/tenants/{id}/approve

**權限**：`hasRole('SUPER_ADMIN')`。要求 `Tenant.status == PENDING_REVIEW`，生產環境永遠等不到資料（見 §1.2 第 2 點），僅保留供歷史測試引用，新功能不得使用。

### 4.21 （已棄用）舊流程駁回租戶

**端點（不受機械防漂移保護，見 §7）**：POST /v2/admin/tenants/{id}/reject

同 §4.20，已棄用、生產不可達。

### 4.22 待審核開店申請列表

**端點（不受機械防漂移保護，見 §7）**：GET /v2/admin/tenant-applications

**權限**：`hasRole('SUPER_ADMIN')`。無參數，一律回傳全部 `status=PENDING` 的申請，不分頁。

### 4.23 核准開店申請

**端點（不受機械防漂移保護，見 §7）**：POST /v2/admin/tenant-applications/{applicationId}/approve

**權限**：`hasRole('SUPER_ADMIN')`。同一交易內：建立 `Tenant`（`ACTIVE`）、初始化 Feature Toggle 預設值、建立 `tenant_members`（`STORE_OWNER`）、同步 `User.role`，回寫申請狀態。用 CAS 防止併發重複核准（見 §1.2 第 3 點）。

**回應**：`applicationId`／`tenantId`／`status`／`approvedAt`

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 找不到該申請 | 404 | `E-2006` |
| 申請狀態非 `PENDING`（含併發搶輸） | 403 | `E-2007` |
| 申請的 `userId` 為 `null`（匿名申請） | 403 | `E-2008` |

### 4.24 駁回開店申請

**端點（不受機械防漂移保護，見 §7）**：POST /v2/admin/tenant-applications/{applicationId}/reject

**權限**：`hasRole('SUPER_ADMIN')`。不建立任何 `Tenant`，僅更新申請狀態與駁回原因。

**請求**：`reason`(必填，`@NotBlank`)

**回應**：`applicationId`／`status`／`rejectedAt`／`reason`

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `reason` 空白 | 400 | `E-9000`（Bean Validation 標準回應） |
| 找不到該申請 | 404 | `E-2006` |
| 申請狀態非 `PENDING`（含併發搶輸） | 403 | `E-2007` |

駁回後申請人可重新送出新申請（阻擋條件只看是否存在 `PENDING` 申請，見 §4.1）。

### 4.25 Admin 變更租戶狀態

**端點（不受機械防漂移保護，見 §7）**：PUT /v2/admin/tenants/{tenantId}/status

**權限**：`hasRole('SUPER_ADMIN')`。狀態機見 §3.4；轉 `SUSPENDED` 會連動下架該租戶全部上架中商品/房源。

**請求**：`status`(必填，限 `SUSPENDED`/`ACTIVE`/`TERMINATED`)、`reason`(轉 `TERMINATED` 時必填)

**回應**：`tenantId`／`previousStatus`／`newStatus`／`updatedAt`／`updatedBy`

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 狀態字串不在允許值域，或轉 `TERMINATED` 缺 `reason`，或已是目標狀態 | 500 | `E-9000` |
| 轉換不合法（見 §3.4） | 403 | `E-2005` |

### 4.26 Admin 新建/更新功能開關

**端點（不受機械防漂移保護，見 §7）**：POST /v2/admin/feature-toggles

**權限**：`hasRole('SUPER_ADMIN')`。與 §4.29 都是「Admin 直接設定」，但這是獨立的 service 方法（`setFeatureToggle`），新建或更新（`isEnabled` 未帶值預設 `false`）。

**請求**：`tenantId`(必填)、`featureKey`(必填)、`isEnabled`(選填)、`config`(選填)

**回應**：`toggleId`／`tenantId`／`tenantName`／`featureKey`／`isEnabled`／`config`／`enabledAt`／`disabledAt`／`createdAt`

### 4.27 Admin 取得指定店鋪完整功能目錄

**端點（不受機械防漂移保護，見 §7）**：GET /v2/admin/tenants/{tenantId}/features

**權限**：`hasRole('SUPER_ADMIN')`。回應形狀與 §4.5 相同，差異只是目標租戶來自路徑參數。

### 4.28 Admin 刪除功能開關

**端點（不受機械防漂移保護，見 §7）**：DELETE /v2/admin/tenants/{tenantId}/feature-toggles/{featureKey}

**權限**：`hasRole('SUPER_ADMIN')`。直接刪除該租戶的 toggle 記錄（非軟刪除）。無回應內容。

### 4.29 Admin 直接設定單一功能開關

**端點（不受機械防漂移保護，見 §7）**：PUT /v2/admin/tenants/{id}/features/{feature}

**權限**：`hasRole('SUPER_ADMIN')`。與 §4.6 的自助申請不同，**不經過** `requiresApproval` 待審邏輯；若該租戶從未建立過此功能的資料列，直接建立新列而非回 404。**管理員的直接設定代表申請已被處理——核准（`enabled=true`）或駁回（`enabled=false`）都會清掉店主的申請時間**（Sprint 311／DEF-429），所以核准後店主再關閉，狀態是 `INACTIVE`，不會因殘留的舊申請時間又變成待審核。

**請求**：`enabled`(必填，Boolean)

**回應**：`featureKey`／`isEnabled`／`enabledAt`

### 4.30 Admin 查詢租戶統計

**端點（不受機械防漂移保護，見 §7）**：GET /v2/admin/tenants/{tenantId}/stats

**權限**：`hasRole('SUPER_ADMIN')`。**回應**：`tenantId`／`orderCount30d`（近30天訂單數）／`activeUserCount`／`activeListingCount`／`lastOrderAt`。

---

## 5. 權限矩陣 / Permission Matrix

| 端點 | 匿名 | 店主（`STORE_OWNER`） | 一般成員（`STORE_STAFF`） | SUPER_ADMIN |
|------|:----:|:----:|:----:|:----:|
| 申請開店（§4.1） | ✅ | — | — | — |
| 我的店鋪/功能/成員管理（§4.2、4.4~4.14） | ❌ | ✅（限自己租戶） | ❌（方法層擋下，`E-1007`；見 §2.1） | ❌（方法層擋下，`E-1007`；見 §2.1） |
| 店鋪詳情（§4.3） | ✅（實質匿名） | ✅ | ✅ | ✅ |
| 待確認邀請接受/拒絕/查詢（§4.10~4.12） | ❌ | ✅（已登入即可，無租戶限制） | ✅（同左） | ✅ |
| 店鋪前台（§4.15~4.16） | ✅ | ✅ | ✅ | ✅ |
| 平台管理端點（§4.17~4.30） | ❌ | ❌ | ❌ | ✅ |

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-1001` | 400 | 角色字串缺失或不在允許值域（§4.8、4.13） |
| `E-1007` | 403 | 方法安全層（`@PreAuthorize`）擋下：角色不在註解清單內——一般成員（`STORE_STAFF`）、`BUYER`、`SUPER_ADMIN` 對店主自助端點（§4.2、§4.4～§4.9、§4.13、§4.14；實測矩陣見 §2.1）；請求到不了 Service 層的任何檢查 |
| `E-2000` | 404 | 找不到租戶，或店鋪前台 slug/狀態不符 |
| `E-2001` | 403 | 找不到使用者（罐頭文字「租戶未啟用」與實際情境不符，§4.9） |
| `E-2002` | 403 | 查無邀請/成員記錄（§4.11、4.12、4.13） |
| `E-2003` | 403 | 租戶上下文不明 |
| `E-2005` | 403 | 租戶狀態轉換不合法（§4.25） |
| `E-2006` | 404 | 找不到開店申請 |
| `E-2007` | 403 | 申請狀態非 PENDING（含併發搶輸） |
| `E-2008` | 403 | 申請無關聯帳號（匿名申請），無法授予角色 |
| `E-2011` | 409 | `slug` 與其他租戶衝突 |
| `E-4031` | 403 | 讀取類端點（§4.5、§4.7）：非該租戶成員；寫入類端點（§4.4、§4.6、§4.8、§4.9、§4.13、§4.14）：非該租戶店主（Sprint 294 逐一對照 `TenantService` 後校正）；另含嘗試變更店主角色／移除自己。一般成員（`STORE_STAFF`）過不了方法層（`E-1007`），到不了這些檢查（Sprint 316 實測） |
| `E-4092` | 409 | 已有 PENDING 開店申請，或使用者已是成員/已有待確認邀請 |
| `E-9000` | 500／400 | 未預期的狀態值/不合法請求（含 Bean Validation 標準回應） |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| `ApiRouteDocDriftTest` 無法覆蓋 `AdminController`（§4.17~4.30） | `AdminController` 混雜大量 M17 以外的端點（使用者管理、系統設定、稽核日誌、採購單審批），無法整個類別納入守門（會強制要求本文件也記載那些不相關端點）。§4.17~4.30 因此**沒有機械防漂移**，僅 §4.1~4.16（`TenantController`／`StorefrontController`）受保護 |
| `getTenantDetails`（§4.3）的 `@PreAuthorize` 寫法是程式碼smell | `isAuthenticated() or true` 恆真，效果等同 `permitAll()`，但寫法容易誤導讀者以為有登入檢查 |
| 功能開關有兩套獨立但重疊的「Admin 直接設定」路徑 | §4.26（`setFeatureToggle`）與 §4.29（`updateTenantFeatureToggle`）是兩個不同 service 方法，皆可讓 Admin 直接設定同一個 `featureKey`，未統一為單一入口 |
| `API-M17-019`（`POST /admin/tenants/review`）與 §4.23/4.24 功能重疊但邏輯不同 | `reviewTenant` 不檢查 `Tenant` 現有狀態、不初始化 Feature Toggle，與核准開店申請定位不同但都能把一個 tenant 的狀態改成 ACTIVE/REJECTED，兩套機制並存的歷史原因未見文件記載 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §4.3、§7.4.1（開店審核流程）、§9.10.2、§9.11（成員邀請）、§10（路由表）、§12.5／§12.7（店鋪前台） |
| FRD | BR-M17-001（開店審核流程） |
| 相關規格 | [API_Error_Codes.md](../API_Error_Codes.md)、[SRD_Database_Schema.md](../SRD_Database_Schema.md) |
| 實作 | `TenantController`、`AdminController`、`StorefrontController`（`api/controller/`）；`TenantService`（`core/tenant/`）、`AdminService`（`core/admin/`） |
| 契約測試 | [TenantStaffAccessIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/TenantStaffAccessIntegrationTest.java)（§2.1 的 `STORE_STAFF` 欄，Sprint 316）、[TenantServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/tenant/TenantServiceTest.java)、[AdminServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/admin/AdminServiceTest.java)、[AdminServiceIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/AdminServiceIntegrationTest.java)、[AdminControllerE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/AdminControllerE2ETest.java)、[TenantControllerE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/TenantControllerE2ETest.java)、[TenantMemberLookupIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/TenantMemberLookupIntegrationTest.java)、[TenantMemberInviteE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/TenantMemberInviteE2ETest.java) |

---

**文件狀態**：v2.0（Sprint 266 依實作全面改寫，DEF-369／377；取代 v1.1 全文）。
