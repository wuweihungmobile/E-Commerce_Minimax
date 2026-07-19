# Sprint 99 Plan — StoreStaff 角色同步 + RefreshToken 租戶解析 + ERP Feature Toggle 三項修復

**Sprint**: Sprint 99
**日期**: 2026-07-19
**主題**: 第七輪 PRD 全文掃描找到三個明確、可獨立驗證的缺口，皆為既有 Sprint 97-98 判讀技巧的延伸應用，PRD 要求明確，無業務判斷分歧，直接修復。

---

## 1. 缺口盤點結果

延續「角色/權限授予類流程需要雙邊確認」（Sprint 98 新增判讀技巧）繼續掃描，找到三項：

### 缺口一：acceptInvite 未同步 User.role（StoreStaff 版的 Sprint 97/98 孿生問題）
- **PRD 依據**：§7.4/§7.4.1（JWT roles 陣列須反映新角色）、§7.3 RBAC 矩陣（StoreStaff 有獨立權限集合）。
- **程式碼現況**：`TenantService.acceptInvite()`（Sprint 98 新增）只把 `tenant_members.status` 改為 ACTIVE，從未呼叫 `user.setRole(STORE_STAFF)`。全庫 `.setRole(` 呼叫只有 Sprint 98 修的 `approveTenantApplication` 一處，`acceptInvite` 完全沒有對應修正——Sprint 98 修 StoreOwner 版本時，沒有連帶檢查同一個 Sprint 剛新增的 StoreStaff 邀請流程是否也有一樣的問題。

### 缺口二：RefreshToken 換發流程的租戶解析邏輯與 login() 不一致
- **PRD 依據**：§7.4.1（「系統強制用戶重新授權（**或以 Refresh Token 換發新建 Access Token**）」皆須讓新 Token 帶正確 `tenant_id` Claim）。
- **程式碼現況**：`AuthService.login()` 在 `user.getTenantId() == null` 時會 fallback 查詢 `tenant_members` 找出正確租戶；但 `AuthService.refreshToken()` 在同樣情況下直接 fallback 到 `SYSTEM_TENANT_ID`，未比照 login() 查詢。由於全庫從未有任何流程呼叫 `user.setTenantId(...)`（唯一一次 `.setTenantId(` 作用在 `TenantApplication`，與 `User` 無關），任何 `user.tenantId` 為 null 的使用者透過「換發 Token」而非「重新登入」取得新 Access Token，都會拿到錯誤的 `SYSTEM_TENANT_ID`。

### 缺口三：ERP_ENABLED Feature Toggle 從未在採購單建立流程中被檢查
- **PRD 依據**：§7.5「Feature Toggle × RBAC 整合規範」：「StoreOwner M16 ERP RW* | ERP_ENABLED | Service 層在建採購單前查詢」。
- **程式碼現況**：`RETAIL_ENABLED`（ProductService）、`BOOKING_ENABLED`（RoomService）、`DYNAMIC_PRICING_ENABLED`（PricingService）、`CMS_ENABLED`（PostController）皆已正確檢查對應 Toggle，唯獨 `PurchaseOrderService.createPurchaseOrder()` 全程沒有呼叫 `FeatureToggleService.checkFeatureEnabled("ERP_ENABLED")`。

## 2. 實作內容

1. **`TenantService.acceptInvite`**：接受邀請時，若 `member.getStoreRole() == STORE_STAFF`，同步 `user.setRole(STORE_STAFF)` 並儲存。
2. **`TenantService.parseInviteRole`**：邀請角色限縮為僅 `STORE_STAFF`（連帶拒絕 `STORE_MANAGER`）——`STORE_MANAGER` 在 `User.UserRole` 沒有對應值、`RolePermissionMapping` 也未定義其權限集合（PRD 全文亦未提及此角色），邀請後即使同步也拿不到任何實質權限，等同重現本輪要修的同一類問題，故一併拒絕（非業務判斷，純屬「沒有對應授權路徑的角色不該被允許邀請」的技術結論）。
3. **`AuthService`**：抽出共用私有方法 `resolveTenantForUser(User)`，`login()`/`refreshToken()` 皆改用同一套邏輯（優先 `user.tenantId`，缺失時查 `tenant_members`，皆查無資料才退回 `SYSTEM_TENANT_ID`）。
4. **`PurchaseOrderService`**：注入 `FeatureToggleService`，`createPurchaseOrder()` 開頭呼叫 `checkFeatureEnabled("ERP_ENABLED")`，比照其他模組既有慣例。
5. **測試修正**：`M16ErpIntegrationTest`（既有整合測試）的固定租戶種子資料補上 `ERP_ENABLED=true` 的 `tenant_feature_toggles` 列（原本靠 raw SQL 直接種 `tenants` 表，未經過 `initializeFeatureToggles`，因此沒有這筆 toggle，新檢查會導致既有測試全數失敗）。

## 3. 測試

- `TenantServiceTest`：`acceptInvite_success_activatesMembership` 補上斷言驗證 `userRepository.save` 呼叫且角色為 `STORE_STAFF`；新增 `inviteMember_roleStoreManager_rejected`。
- `AuthServiceRefreshTokenTest`：既有「null tenant → SYSTEM_TENANT」測試補上 `tenant_members` 空清單 stub（行為不變，只是現在會先查詢）；新增 `refreshToken_nullTenantButHasMembership_resolvesFromTenantMember` 驗證新行為（有 tenant_members 關聯時優先使用，不退化為 SYSTEM_TENANT_ID）。
- `PurchaseOrderServiceTest`：新增 `createPurchaseOrder_erpFeatureDisabled_throwsAndDoesNotSave`。

## 4. 驗證結果

- 單元測試：`TenantServiceTest`/`AuthServiceRefreshTokenTest`/`PurchaseOrderServiceTest` 皆 0 fail
- Checkstyle 0 violations、PMD 0 violations
- 全量回歸 `mvn verify -Pintegration-test`：詳見 commit 訊息（含 `M16ErpIntegrationTest` 種子資料修正後的完整驗證）
- `make validate-schema`：無 migration，schema-free

## 5. 範圍外（延後）

- `STORE_MANAGER` 角色若未來需要真正支援，須先在 `User.UserRole` 新增對應值並在 `RolePermissionMapping` 定義其權限集合，屬於較大的角色體系擴充，非本輪範圍。
- 其餘 Feature Toggle（`PROMO_ENABLED` 等）是否也有類似遺漏，本輪僅針對第七輪掃描發現的 `ERP_ENABLED` 修復，未做全 Feature Toggle 覆蓋掃描。
