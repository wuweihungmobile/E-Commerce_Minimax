# Sprint 210 Plan — 成員角色指派白名單缺口（DEF-289）

**Sprint**: Sprint 210
**日期**: 2026-09-27

## 1. 起點

Sprint 203～209 收尾後，`DEFERRED_ITEMS_TRACKER.md` 已無 AI 可獨立處理的活躍待辦（維運三件事、Stripe 走查、SMTP 憑證皆需使用者操作環境，見 `myTodoList.md`）。使用者以 `AskUserQuestion` 選擇「開新 Sprint，自選掃描角度找新缺陷」，延續 Sprint 182 起「自選掃描角度」的既有模式。

## 2. 本輪掃描角度：成員角色指派端點是否落實既有角色白名單決策

Sprint 130~209 已系統性涵蓋 IDOR、Mass Assignment、XSS/URL 白名單、輸入驗證、分頁、認證、金額竄改、狀態偽造、公開端點回傳範圍、背景排程、設定宣告與讀取不一致、金額精度捨入、時區/日期邊界、安全標頭、容器層錯誤分派、前後端契約漂移、併發競態、動態定價、通知/SKU 租戶檢查、Webhook 冪等、結算單入口、真實寄信服務等角度。

本輪先嘗試多個角度（Stripe webhook 事件去重併發競態、GDPR 帳戶刪除資料保留範圍、媒體上傳內容驗證）逐一查證，皆已由既有防護（雙層冪等、下游狀態轉移天生冪等、DEF-096/097/101/221/254 的白名單與路徑穿越防護）妥善涵蓋，未發現新缺口。最終在逐一核對 `TenantService` 所有成員異動方法（`inviteMember`/`acceptInvite`/`declineInvite`/`updateMemberRole`/`removeMember`）時，發現 `updateMemberRole` 與 `inviteMember` 對「可指派的角色」執行不一致的規則。

### 2.1 發現：`updateMemberRole` 缺少 `inviteMember` 既有的角色白名單

`TenantMember.StoreRole` 有三個列舉值：`STORE_OWNER`／`STORE_STAFF`／`STORE_MANAGER`。`inviteMember` 透過 `parseInviteRole` 明確限制：

> STORE_OWNER 僅能透過開店審核流程產生（Sprint 97）；STORE_MANAGER 在 `User.UserRole` 沒有對應值也未定義於 `RolePermissionMapping`，邀請後 JWT 永遠拿不到對應權限，等同重現 Sprint 98/99 修復的「角色授予未同步 JWT」問題，故一併拒絕。

但 `updateMemberRole`（`PUT /v2/tenants/{id}/members/{userId}/role`）只解析 `TenantMember.StoreRole.valueOf(newRole)` 是否為合法列舉值，只擋「把*既有* Owner 的角色改掉」，完全沒有擋「把*任何*角色*改成* STORE_OWNER/STORE_MANAGER」——同一份業務規則，兩個修改成員角色的端點只有一個落實。

### 2.2 影響範圍查證（結論：今天無可外部利用的攻擊路徑，但違反文件明訂不變量）

逐一排除可能的攻擊鏈：

| 檢查點 | 結果 |
|---|---|
| 呼叫者是否需先是該租戶既有 STORE_OWNER？ | 是（`existsByTenantIdAndUserIdAndStoreRole` 租戶範圍正確，非跨租戶 IDOR——STORE_OWNER 之外沒有其他任何角色能呼叫此端點） |
| 是否同步 `User.role`／JWT 授權？ | 否（不像 `acceptInvite` 會同步 `STORE_STAFF`），意味著被改成 `TenantMember.storeRole=STORE_OWNER` 的成員，JWT 仍是原本角色，拿不到 `ROLE_STORE_OWNER` 授權 |
| 全庫是否有其他授權檢查依賴 `TenantMember.storeRole` 而非 JWT？ | 只有 `inviteMember`/`updateMemberRole`/`removeMember` 彼此三者，而這三者的 Controller 層 `@PreAuthorize` 都先要求 JWT 的 `ROLE_STORE_OWNER`/`ROLE_ADMIN`/`SCOPE_store:write`——被假冒 Owner 的成員因 JWT 未同步而過不了這一關 |
| `removeMember` 是否會被利用來逐出原始 Owner？ | 否，`removeMember` 對任何 `storeRole==STORE_OWNER` 的列一律拒絕移除（含被假冒的那一個） |
| 前端是否有呼叫點？ | 無（`grep updateMemberRole` 全庫零前端呼叫，`STORE_MANAGER` 只出現在純顯示用的角色標籤對照表，不是可選項） |

**結論**：今天沒有可外部利用的跨租戶或權限提升路徑。真正的風險是兩點：(1) 違反平台自己宣告的「STORE_OWNER 只能經審核流程產生」不變量，產生一個未經審核、資料不一致的第二位「店主」列；(2) 把成員指派成 `STORE_MANAGER` 這個死角色會讓該成員悄悄喪失原本 `STORE_STAFF` 的所有實際權限、且不會有任何錯誤提示，是一個現有 Owner 光靠合法操作介面（若未來補上這個 UI）就能誤觸的功能性陷阱。與 `DEF-241`（Sprint 180 記錄的全域模板租戶檢查缺口）同一類「今天沒有攻擊路徑，但違反文件明訂不變量」的個案，差別是本次修法成本低（與既有 `parseInviteRole` 邏輯對稱），直接修復而非僅記錄不排入排程。

## 3. 修復

`TenantService.updateMemberRole`：角色解析成功後、變更既有 Owner 角色檢查之前，新增守門：

```java
if (storeRole != TenantMember.StoreRole.STORE_STAFF) {
    throw new BusinessException(ErrorCode.E_1001, "Only STORE_STAFF is a valid role for this endpoint");
}
```

與 `parseInviteRole` 共用相同的 `ErrorCode.E_1001`，行為對稱。未抽共用方法：`parseInviteRole` 對空白值另有預設回傳 `STORE_STAFF` 的行為，`updateMemberRole` 的 `newRole` 是必填參數、無此語意，兩者不完全相同，維持各自獨立以免引入不必要的抽象。

## 4. 測試

**紅燈先行**：`TenantServiceTest` 新增 `updateMemberRole_newRoleStoreOwner_rejected`／`updateMemberRole_newRoleStoreManager_rejected` 兩案例。首次針對未修復程式碼執行，皆確認失敗（`Expected BusinessException to be thrown, but nothing was thrown`），證實缺口成立。為避免測試因 Mockito 對未 stub 方法的預設回傳值（`updateRoleIfNotRemoved` 未 stub 時回傳 `int` 預設值 `0`）而「巧合地」拋出不相關的 `E_2002`、造成假紅燈/假綠燈，改用 `lenient()` stub 完整鋪好「若未攔截、流程會走到底並成功」的路徑，確保測試只鎖住新的角色白名單檢查本身。

**突變驗證**：暫時移除新增的白名單檢查、重新編譯執行，兩案例皆正確變回紅燈（`Expected ... but nothing was thrown`）；還原後重新確認轉綠。

**回歸**：`TenantServiceTest` 全類別 42 tests 0 fail；`mvn -o test`（全量單元測試，真實 postgres/redis，`make test-db-up`）與 `checkstyle:check` 詳見 §5。

## 5. 驗證結果

- `TenantServiceTest`：42 tests，0 fail（含本輪新增 2 案例）
- checkstyle（main+test）：0 violations
- 全量單元測試 `mvn -o test`（`make test-db-up` 已啟動真實 postgres/redis）：**1790**（+2）／0 failures／0 errors／0 skipped，`BUILD SUCCESS`（1:46 min）
- 全量驗證 `mvn -o verify`（含 failsafe 整合測試、checkstyle、PMD）：整合 **568**（持平，本次未新增整合測試）／0 failures／0 errors；checkstyle（main+test）0 violations；PMD 通過；`BUILD SUCCESS`（9:47 min）
- 未變更 entity/migration，未跑 `make validate-schema`
- 未跑 E2E／`make validate-release`

## 6. 後續

- 本項不需要使用者操作，已完整修復並驗證。
- `myTodoList.md` 記載的三項人工待辦（真實寄信服務憑證、維運三件事、Stripe 測試模式走查）狀態不變，仍在使用者手上。
