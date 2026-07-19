# Sprint 98 Plan — 店鋪成員邀請確認制 + Sprint 97 遺漏修復

**Sprint**: Sprint 98
**日期**: 2026-07-19
**主題**: PRD §7.4/§8.2.3/§9.11 明訂的 `tenant_members` 邀請確認狀態機（INVITED/ACTIVE/REMOVED），第六輪 PRD 全文掃描發現的第六個缺口——經使用者確認需要修復；過程中同時發現並修復 Sprint 97 的一個遺漏（核准開店申請後 `User.role` 未同步更新）。

---

## 1. 缺口盤點結果（背景，含使用者確認流程）

第六輪掃描（孤兒錯誤碼、零呼叫死碼、Entity 欄位、雙階段流程斷點四種判讀技巧）沒有找到「PRD 明確要求但完全沒做」的清楚缺口，只找到一個需要產品判斷的模糊地帶：

- **PRD §8.2.3** `tenant_members` schema 明訂 `status ENUM: INVITED / ACTIVE / REMOVED`。
- **PRD §9.11** API 表格明訂 `POST /api/v2/tenants/:id/members/invite`「邀請成員加入店鋪」。
- **程式碼現況**：`TenantMember` Entity 完全沒有 `status` 欄位；`TenantService.addMember()` 程式碼註解自稱「Phase 1 - direct add, no email invite」，StoreOwner 新增員工單方直接生效，被邀請人沒有接受/拒絕的機會；端點路徑是 `/tenants/{id}/members` 而非 PRD 指定的 `/members/invite`。

此為「schema 定義了中間狀態，但業務邏輯從未使用該狀態」的模式（與 Sprint 96/97 相似），但是否構成缺口涉及產品判斷（被邀請人是否應有拒絕權利屬於信任模型設計，非單純技術疏漏）。**已透過 AskUserQuestion 向使用者確認：改為兩階段邀請確認制**。

## 2. 實作內容

1. **Migration**（`V69__Add_Status_To_Tenant_Members.sql`）：新增 `tenant_members.status`（VARCHAR + CHECK constraint，預設 `ACTIVE`——既有紀錄皆為直接新增即生效，回填為 ACTIVE 不影響現況）與 `invited_at` 欄位。
2. **`TenantMember` Entity**：新增 `MemberStatus { INVITED, ACTIVE, REMOVED }`、`status`（`@Builder.Default` = ACTIVE，維持 Sprint 97 approveTenantApplication 直接建立 StoreOwner 的既有行為不受影響）、`invitedAt`。`@PrePersist` 邏輯調整：`joinedAt` 只在 status=ACTIVE 時才自動填入（INVITED 狀態下應為 null，等 accept 時才填）。
3. **`TenantService` 重寫**：
   - `inviteMember`（原 `addMember`）：建立 `status=INVITED` 紀錄；若曾被移除/拒絕過（REMOVED），更新既有紀錄而非新增（`(tenant_id, user_id)` 有 UNIQUE 約束）；邀請角色為 STORE_OWNER 時拒絕（僅能透過 Sprint 97 的開店審核流程產生）。
   - `acceptInvite`/`declineInvite`（新增）：被邀請人本人才能操作；接受 → ACTIVE + 填入 joinedAt；拒絕 → 軟刪除為 REMOVED（保留紀錄供未來重新邀請）。
   - `getMyPendingInvites`（新增）：跨租戶查詢我的待確認邀請列表。
   - `removeMember`：**改為軟刪除**（狀態轉為 REMOVED，不再 hard delete）——若維持硬刪除，PRD 定義的 REMOVED 狀態將永遠無法被觸發，等於在修復缺口的同時引入新的「孤兒 enum 值」，故一併修正。
   - `getTenantMembers`/`updateMemberRole`：排除已 REMOVED 的紀錄（視為不存在）。
4. **`TenantController`**：`/tenants/{id}/members` POST 改為 `/tenants/{id}/members/invite`（比對後確認全庫無任何前端呼叫此端點，可直接變更路徑無相容性風險）；新增 `GET /tenants/invites/my`、`POST /tenants/{id}/members/invite/accept`、`POST /tenants/{id}/members/invite/decline`。
5. **⚠️ 額外發現並修復（Sprint 97 遺漏）**：實作邀請流程時，為了讓 StoreOwner 測試帳號正確通過 `@PreAuthorize("hasAuthority('ROLE_STORE_OWNER')")`，追查 `RolePermissionMapping.getAuthorities()` 才發現 `AdminService.approveTenantApplication()`（Sprint 97）雖然正確建立了 `tenant_members` 的 STORE_OWNER 紀錄，卻從未同步更新申請人的 `User.role` 欄位——而 `AuthService.login()` 產生 JWT 的 `role` claim完全來自 `User.role`（非動態查詢 `tenant_members`）。這代表 Sprint 97 修復後，被核准的使用者即使重新登入，JWT 仍然是舊角色（如 BUYER），永遠拿不到 `ROLE_STORE_OWNER` 權限，實質上仍無法管理自己剛核准的店鋪——PRD §7.4.1 明文要求「新 Token 的 JWT Payload 內 roles 陣列將包含 StoreOwner」。已在 `approveTenantApplication` 補上 `user.setRole(STORE_OWNER)` 並儲存，同時補強 Sprint 97 的單元測試與 E2E 測試斷言驗證此欄位正確更新。

## 3. 測試

- `TenantServiceTest`：新增 `MemberInviteTests`（10 項）——邀請成功、非 StoreOwner 拒絕、對象已是成員拒絕、對象曾被移除時重用既有紀錄、邀請角色不可為 STORE_OWNER、接受成功、找不到邀請、拒絕成功、我的邀請列表、移除成員軟刪除。
- `AdminServiceTest`：更新既有 `approveTenantApplication_success` 測試，新增驗證 `userRepository.save` 被呼叫且角色為 STORE_OWNER。
- `TenantMemberInviteE2ETest`（新檔，4 項，真實 DB+JWT）：**完整迴路**（邀請→被邀請人可見→接受→真正成為 ACTIVE 成員）、拒絕邀請不成為成員、移除成員軟刪除且列表隱藏、曾被移除者重新邀請時重用既有紀錄。
- `TenantApplicationReviewE2ETest`（Sprint 97 既有檔案）：補強斷言驗證 `User.role` 正確同步為 STORE_OWNER。

## 4. 驗證結果

- 單元測試：`TenantServiceTest`+10、`AdminServiceTest` 既有測試更新，皆 0 fail
- 整合測試：`TenantMemberInviteE2ETest` 4 tests + `TenantApplicationReviewE2ETest` 5 tests（含新斷言），0 fail
- Checkstyle 0 violations、PMD 0 violations
- 全量回歸 `mvn verify -Pintegration-test`：詳見 commit 訊息
- `make validate-schema`：**含新 migration V69**，已驗證 entity 與 Flyway schema 對齊無漂移

## 5. 範圍外（延後）

- `tenant_members.role` 欄位的 PRD 字面值（`SELLER`/`HOST`）與現行 `StoreRole` enum（`STORE_MANAGER`）不一致——與本次邀請確認制修復性質不同，非本輪必要項目。
- 邀請通知（Email/站內信）：M09 通知系統（Phase 2）尚未上線前，被邀請人僅能透過 `GET /tenants/invites/my` 主動查詢，無主動推播，比照 Sprint 96 MaintenanceWarnings 的既有替代方案模式。
