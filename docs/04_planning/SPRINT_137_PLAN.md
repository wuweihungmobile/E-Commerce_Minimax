# Sprint 137 Plan — 租戶治理併發競態技術債查證與修復

**Sprint**: Sprint 137
**日期**: 2026-09-08

---

## 1. 本輪範圍與方法論

Sprint 136 併發競態全掃找出 71 筆真實競態，僅修復 19 筆（7 high + 12 medium/financial），其餘 52 筆（`DEF-114`~`DEF-165`）登記為技術債，並**明確標註「僅通過 Workflow 自動化分析，未經主控 session 逐筆獨立複核」**（見 `SPRINT_136_PLAN.md` §1/§8）。使用者拍板本輪依序查證並修復一部分，遵照 CLAUDE.md「Workflow 子 Agent 唯讀範圍強制規則」的精神——本輪未再另開 Workflow，改由主控 session 直接逐筆重讀原始碼、獨立判斷、動手修復，避免重蹈自動化分析未經複核就排入 Sprint 的風險。

**範圍選定**：52 筆技術債涵蓋多個不相關領域（Tenant 治理、CMS、ERP、客服工單、房源日曆等）。本輪聚焦「租戶治理」主題——`AdminService`/`TenantService`/`SettlementReversalService` 中與 Tenant/TenantApplication/TenantMember 生命週期相關的候選，理由：(a) 多數方法共用同一組實體（`Tenant`/`TenantApplication`/`TenantMember`），根因高度相似（缺 `@Version`/`@DynamicUpdate`），可用同一套 CAS 手法批次處理；(b) 該主題內同時涵蓋 security_or_access 與 financial（結算逆轉雙重授權）類別，優先級較高。

**🔴 誠實揭露：查證結果與 Sprint 136 標籤有出入**。逐一重讀原始碼後：

- **9 筆確認為真並修復**：`DEF-114`/`115`/`117`/`138`/`140`/`141`/`142`/`144`/`164`。
- **1 筆確認為真並修復，範圍略超出原候選描述**：`DEF-165`（`inviteMember` 重新邀請 REMOVED 成員的競態）一併處理，因與 `DEF-140`/`142`/`164` 共用同一個 repository 與 CAS 方法，一次到位比事後補更省成本。
- **3 筆推翻**：`DEF-145`/`146`/`147`（`AdminService.approveTenant`/`rejectTenant`/`reviewTenant`）——查證後發現這三個方法的操作前提 `Tenant.status == PENDING_REVIEW` 在生產環境**完全不可達**（詳見 [[m17-tenant-application-dual-flow]]，S108/S109 已查證：`PENDING_REVIEW` 只存在於 Flyway 一次性測試固件，唯一建立 Tenant 的路徑 `approveTenantApplication` 寫死 `ACTIVE`；前端審核台已於 S109 改接活流程 `tenant-applications`，不再呼叫這三個端點）。這是**死流程上的競態**，修復對真實系統沒有效益。Sprint 136 的 Workflow 分析當時只看程式碼邏輯本身、未查證呼叫路徑是否可達，因此把這三筆判成與 `DEF-114`/`115` 同等的「真實競態」——這正是 Sprint 136 §1 自己預告的風險（「不可直接信任下表的嚴重度標籤」），本輪查證印證了這一點。已在 `DEFERRED_ITEMS_TRACKER.md` 更正註記，不修復，未來若產品決策重新啟用 (B) 流程需重新評估。

其餘 42 筆（CMS/ERP/客服工單/房源日曆等領域）仍維持登記狀態，未列入本輪範圍，留待後續 Sprint。

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

比照 Sprint 136 慣例：每完成一個邏輯修復單元，立即 `mvn compile` + 執行相關測試類別，確認通過才進入下一項；全部完成後才跑 `mvn -o verify` 全量回歸 + `checkstyle:check` + `make validate-schema`/`validate-schema-doc`。

---

## 3. 修復摘要：開店申請審核（`AdminService`）

### 3.1 `AdminService.approveTenantApplication`（DEF-114，security_or_access）

**問題**：`application.getStatus() != PENDING` 檢查與後續建立 `Tenant`/`TenantMember`/同步 `User.role` 之間沒有原子保護；兩個併發核准請求都可能通過檢查，各自建立出一個獨立 ACTIVE 狀態的 Tenant + 一筆 STORE_OWNER membership（孤兒資源）。

**修法**：新增 `TenantApplicationRepository.updateStatusIfCurrent`（`WHERE id=:id AND status=:expectedStatus` 條件式原子 UPDATE），比照 Sprint 136 §4.1/§5.1 的 claim-before-external-call 原則——**先**原子搶占審核權（PENDING→APPROVED），成功才建立 Tenant/membership；搶輸沿用既有 `E_2007`，不建立任何下游資源。

### 3.2 `AdminService.rejectTenantApplication`（DEF-115，security_or_access）

**問題**：與 3.1 同一根因，兩個併發請求（reject/reject 或與 approve 交錯）可能造成業務狀態與系統狀態矛盾。

**修法**：同一個 `updateStatusIfCurrent`（PENDING→REJECTED），搶輸沿用既有 `E_2007`。

### 3.3 `AdminService.updateTenantStatus`（DEF-117，security_or_access）

**問題**：兩個 SUPER_ADMIN 併發下達不同狀態轉換（例如租戶目前 SUSPENDED，一人要求 ACTIVE、另一人要求 TERMINATED）都可能通過 `validateStatusTransition` 的快照檢查，最後 commit 者覆寫另一邊。`Tenant` 無 `@Version`。**此方法先前完全零單元測試覆蓋**，本輪一併補上。

**修法**：新增 `TenantRepository.updateStatusIfCurrent`，搶輸沿用既有 `E_9000`（此方法既有的 `validateStatusTransition` 失敗也用此碼，語意一致）。新增 `AdminServiceTest.UpdateTenantStatus`（成功案例 + 併發搶占失敗案例）。

---

## 4. 誠實更正：`DEF-145`/`146`/`147` 是死流程上的競態，不修復

見第 1 節說明。`AdminService.reviewTenant`/`approveTenant`/`rejectTenant` 三個方法都要求 `Tenant.status == PENDING_REVIEW`，但該狀態在生產環境不可達（詳見 [[m17-tenant-application-dual-flow]]）。已在 `DEFERRED_ITEMS_TRACKER.md` 該三筆條目加註更正說明，維持「不排入排程」，理由從「規模超出可消化量」改為「死流程，修復無效益」。

---

## 5. 修復摘要：結算單逆轉（`SettlementReversalService`）

### 5.1 `SettlementReversalService.initiateReversal`（DEF-138，security_or_access）

**問題**：`statement.getStatus() != PAID` 檢查與後續 `setStatus(REVERSAL_PENDING)` + 寫入發起人/角色之間沒有原子保護。兩個併發發起請求（可能分屬 SUPER_ADMIN 與 CFO 兩種角色）都可能通過檢查，後 commit 者悄悄覆寫先前已寫入的發起人/角色，可能讓 `confirmReversal` 之後比對「確認人角色須與發起人不同」時比對到錯誤角色，削弱雙重授權的安全承諾。`SettlementStatement` 無 `@Version`。

**修法**：新增 `SettlementStatementRepository.updateStatusIfCurrent`（PAID→REVERSAL_PENDING），搶輸沿用既有 `E_5014`。

---

## 6. 修復摘要：邀請確認制成員管理（`TenantService`，`TenantMember`）

### 6.1 根因

`TenantMember` 無 `@Version` 也無 `@DynamicUpdate`，`acceptInvite`/`declineInvite`/`removeMember`/`updateMemberRole`/`inviteMember`（重新邀請分支）五個方法共用同一張表，卻各自用「讀→過濾狀態→setStatus/setStoreRole→save()」的無保護模式，存在兩種併發問題：

1. **同欄位互斥轉換**（`status`）：`acceptInvite`（INVITED→ACTIVE）與 `declineInvite`（INVITED→REMOVED）可能對同一筆邀請幾乎同時被呼叫，或 `removeMember` 與 `acceptInvite` 交錯，被移除成員因併發覆寫而「復活」。
2. **跨欄位全列覆寫**：`updateMemberRole`（改 `storeRole`）與 `removeMember`（改 `status`）交錯時，後 commit 者用自己交易開始時讀到的舊快照整列覆寫，可能讓角色變更被悄悄復原、或已移除成員的角色欄位被意外改動。

### 6.2 修法

- `TenantMember` 實體加 `@DynamicUpdate`（Sprint 136 §6 既有模式），解決問題 2（跨欄位覆寫）。
- `TenantMemberRepository` 新增兩個 CAS 方法：
  - `updateStatusIfCurrent`（`WHERE id=:id AND status=:expectedStatus`）：供 `acceptInvite`（DEF-140，INVITED→ACTIVE）、`declineInvite`（DEF-164，INVITED→REMOVED）、`removeMember`（DEF-142，讀到的當下狀態→REMOVED）、`inviteMember` 重新邀請分支（DEF-165，REMOVED→INVITED）共用，解決問題 1（同欄位互斥轉換）。
  - `updateRoleIfNotRemoved`（`WHERE id=:id AND status<>REMOVED`）：供 `updateMemberRole`（DEF-144）使用，角色變更時若成員已被併發移除則拒絕，作為 `@DynamicUpdate` 之外的第二層防護（`@DynamicUpdate` 只解決「兩邊寫不同欄位」，無法阻止「對已被移除的成員繼續寫入角色」這個業務規則）。

搶輸一律沿用各方法既有的錯誤碼（`E_2002`「Invite not found」/「Member not found」、`E_4092`「User is already a member or has a pending invite」），不新增錯誤碼。

---

## 7. 修復摘要：開店申請重複遞交（`TenantService.createApplication`，DEF-141）

**問題**：`existsByUserIdAndStatusIn(userId, [PENDING])` 檢查與 `save()` 之間是典型 TOCTOU，兩個併發送出的申請都可能通過檢查各自 INSERT，造成同一使用者有兩筆 PENDING 開店申請。

**修法**：新增 `V80__Tenant_Applications_Pending_Unique_Per_User.sql`，對 `tenant_applications(user_id)` 建立部分唯一索引 `WHERE status = 'PENDING'`（訪客 `user_id IS NULL` 不受此限制，PostgreSQL 視多個 NULL 互不相等，符合原本「僅登入使用者才檢查重複」的語意）。`createApplication` 改用 `saveAndFlush` 並捕捉 `DataIntegrityViolationException`，轉譯為既有 `E_4092`。

`make validate-schema`／`make validate-schema-doc` 皆通過，V80 未造成 entity↔migration 或文件漂移。

---

## 8. 驗證

- **編譯**：每個修復單元改動後立即 `mvn compile` 確認真實編譯（含清空 `target/maven-status` 排除假 BUILD SUCCESS 陷阱的既有教訓）。
- **checkstyle**：`mvn checkstyle:check` **0 violations**。
- **單元測試**：新增 12 個測試（`AdminServiceTest` +4：`approveTenantApplication`/`rejectTenantApplication` 各 1 個併發搶占測試 + `UpdateTenantStatus` 全新 2 個測試；`SettlementReversalServiceTest` +1；`TenantServiceTest` +7：`createApplication` 併發重複 1、`inviteMember` 併發重新邀請 1、`acceptInvite`/`declineInvite`/`removeMember`/`updateMemberRole` 併發搶占各 1）。
- **Schema 守門**：`make validate-schema`（entity↔migration）、`make validate-schema-doc`（migration↔文件）皆通過，V80 遷移與 `TenantMember` 的 `@DynamicUpdate` 註解均未造成漂移。
- **全量回歸**：`mvn -o verify`（含 failsafe 整合測試）結果見下方（本輪執行時先誤觸 `local-ci-cannot-catch-schema-validation`/`backend-integration-test-profile-needs-real-db` 已知教訓——`make test-db-up` 忘記先啟動導致第一次跑出 3 個 `SellerDashboardServiceCacheTest` context 載入失敗（`Connection to localhost:5432 refused`），與本輪程式碼變更無關；補跑 `make test-db-up` 後重新執行全量回歸）。

**最終結果**：第一次全量回歸跑出 1 個真實回歸——`M07SettlementIntegrationTest.initiateReversal_asSuperAdmin_success`（該測試 `settlementStatementRepository` 是 `@MockBean`，未 stub 新增的 `updateStatusIfCurrent` CAS，Mockito 對未 stub 的 `int` 方法預設回傳 0，被誤判為併發搶占失敗）；修復該測試（補上 stub）後，`mvn -o verify` **BUILD SUCCESS**：單元 **1167**（相對 Sprint 136 的 1156，+11，即本輪新增的併發防護測試）、整合 **477**（與 Sprint 136 持平，未新增整合測試類別，僅為既有 `M07SettlementIntegrationTest` 補 stub），0 failures/errors；checkstyle（main+test）**0 violations**。

---

## 9. 刻意不做的事（避免範圍蔓延）

- 不修復其餘 42 筆技術債（CMS/ERP/客服工單/房源日曆等領域）——本輪聚焦租戶治理主題，其餘留待後續 Sprint 依主題分批查證。
- 不修復 `DEF-145`/`146`/`147`——死流程，見第 4 節。
- 不重構 (B) 流程（`reviewTenant`/`approveTenant`/`rejectTenant`）或移除這些端點——此為既有產品決策範圍（M17 在 PRD 標 P0），非本輪範圍，見 [[m17-tenant-application-dual-flow]]。
- 不處理 `DEF-116`/`143`（`AdminService.updateTenantFeatureToggle`/`TenantService.updateFeatureToggle` 功能開關 lost update）——嚴重度較低（`other` 類別），且與本輪聚焦的 Tenant/TenantApplication/TenantMember 狀態機競態性質不同，留待未來排程。
