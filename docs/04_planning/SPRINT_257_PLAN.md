# Sprint 257 計畫：補齊 DEF-358(b)（`/auth/profile` 個人資料編輯表單）

## 1. 起點與範圍

承接 Sprint 256：DEF-358 使用者已核准 4 項全做，依「由小到大、依耦合度排列」排入 Sprint 256 起依序處理：

1. ✅ Sprint 256：(c) `/blog/category/[id]` 貼文分類篩選頁
2. **Sprint 257（本輪）**：(b) `/auth/profile` 個人資料編輯表單
3. Sprint 258：(d) `/admin` 管理台總覽首頁
4. Sprint 259：(a) `/products`、`/rooms` 跨店鋪商品/房源列表

PRD 規格：§10.1.1 `/auth/profile`（M03）——「全庫查無個人資料編輯（姓名/電話等）功能。`/account` 頁僅提供個資下載與刪除帳戶（GDPR 自助），非編輯個人資料」。

## 2. 讀碼確認範圍

**User 實體可編輯欄位**：`fullName`／`phone` 符合缺口原文字面（「姓名/電話等」）。`email`／`role`／`tenantId`／`status`／`emailVerified`／KYC 系列皆為系統管理欄位，不開放自助編輯。`email` 變更牽涉 `emailVerified` 連動重置與重新驗證流程（DEF-252/253），列為獨立、需另外產品決策的項目，不在本輪範圍（**範圍縮小點，誠實揭露**：PRD §13.4 僅寫「更新非敏感欄位」未逐一列舉，`avatarUrl` 技術上也可歸類為非敏感欄位，但缺口原文只明確提到「姓名/電話」，為求範圍精準不擅自擴大）。

**既有端點與路由現況**：
- `GET /v2/auth/me`（`AuthController`）已存在，回傳 `UserInfoResponse`（含 `profile.displayName`/`phone`/`avatarUrl`），但**前端從未呼叫過**——`AuthService.getCurrentUser()`（前端同名方法）只是讀 `localStorage` 的登入快照，沒有 `phone` 欄位。新表單預填必須新增一次即時 API 呼叫，不能只靠本機快取。
- PRD §9.2/§13.4 原規劃路徑是 `/api/users/me`，但 `GET` 實際早已落地在 `/v2/auth/me`（非 `/v2/users/me`，全庫無 `UserController`）。延續既有路徑在同一個 Controller 加 `PUT`，與現況一致性最高。
- **權限模型決策**：`AddressController` 等資源端點用 `@PreAuthorize("hasAuthority('user:update')")`，但 `user:update` 權限 `STORE_STAFF`／`CFO`／`GUEST` 皆未持有——若套用這套慣例，店鋪員工/財務長將無法編輯自己的姓名電話。既有 `GET/DELETE /v2/auth/me` 完全不用 `@PreAuthorize`（只要求合法 JWT），語意上「編輯自己的基本資料」更貼近這一組，不是通用資源權限模型。新端點比照 `/me` 系列，不加 `@PreAuthorize`。
- **Service 歸屬**：沒有放進 `AuthService`（會員核心認證流程），而是放進已經持有 `UserRepository`＋`AuditService` 的 `UserPrivacyService`（PRD §1.5.1 會員自助資料權利，目前已有 `exportMyData`/`deleteMyAccount`）——語意上「自助更新個人資料」與「自助資料權利」同一家族，且可避免在 `AuthService` 建構子新增依賴牽動 4 個既有手動建構 `new AuthService(...)` 的測試檔案（Rule 3：精準改動）。Controller 呼叫順序：`userPrivacyService.updateMyProfile(request)` 寫入後，**重新呼叫** `authService.getCurrentUser(...)` 取得最新快照回應，兩個既有 Service 各司其職、零建構子變動。
- **併發**：`User` 已有 `@DynamicUpdate`（DEF-136 既有防護，只 UPDATE 實際被 setter 改動的欄位），用一般 `findById`+setter+`save()` 即可，不需要 `findByIdForUpdate` 悲觀鎖（不是像刪除帳戶那種需要跨步驟保持一致性的操作）。
- **稽核**：比照 `deleteMyAccount` 既有呼叫 `AuditService.record` 的慣例，新增 `action="USER_PROFILE_UPDATED"`，`actorUserId` 為本人（自助操作）。

**頁面落點**：PRD 明確把 `/auth/profile` 列為本缺口的目標路由（非「併入 `/account`」），延續 Sprint 256「在 PRD 指定路徑實作」的一致做法，不merge 進 `/account`（避免該頁 Card 無限疊加、兩個頁面語意本就不同：帳戶安全/GDPR vs 個人資料）。前端目錄放在 `(auth)` route group 下的巢狀字面路徑 `(auth)/auth/profile/page.tsx`（route group 不影響 URL，巢狀字面段落在本專案已有 `(auth)/bookings/[id]` 先例），沿用 `(auth)/layout.tsx` 共用的 Header/Footer 版型與頁面自身的 `StorefrontShell`，與 `/account` 一致。`/account` 新增一張「個人資料」Card 連結過去，回頭讓新頁面可達（比照 Sprint 256 幫 `/blog/[slug]` 分類徽章補連結的做法）。

**表單欄位元件慣例**：`(auth)/addresses` 頁面是同一 route group 內最接近的「簡單文字表單」範例（`Label`+`Input`+`Button onClick`，非 native form submit，天然避開 `StorefrontHeader` 搜尋表單的 submit 按鈕選擇器碰撞問題），新頁面直接沿用。

## 3. 實作

**後端**：
- 新增 `UpdateProfileRequest`（`fullName`/`phone` 皆選填，`@Size(min=1,max=255)`/`@Size(max=255)`，`null` 代表不變更，比照 `AddressDto.UpdateRequest` 的 PATCH 語意；max 255 對齊 `User` entity 實際欄位長度，非憑空訂定）。
- `UserPrivacyService` 新增 `updateMyProfile(UpdateProfileRequest)`：`TenantContext.getCurrentUser()` 取得呼叫者 → `findById` → 僅對非 null 欄位呼叫 setter → `save()` → `AuditService.record("USER_PROFILE_UPDATED", ...)`（oldValue/newValue 各記錄變更前後的 `fullName`/`phone` 摘要字串）。
- `AuthController` 新增 `PUT /v2/auth/me`：呼叫 `userPrivacyService.updateMyProfile(request)` 後，接著呼叫既有 `authService.getCurrentUser(...)` 組回應，回傳型別與既有 `GET /me` 相同（`UserInfoResponse`），前端可直接重用解析邏輯。無需新增 `ErrorCode`（`@Valid` 失敗走既有 `E_9000` 通用驗證錯誤路徑，使用者不存在走既有 `E_1006`）；`SecurityConfig` 無需改動（`/v2/auth/**` 本就非 permitAll，`anyRequest().authenticated()` 已涵蓋）。

**前端**：
- `lib/api.ts` 新增 `API_ENDPOINTS.auth.me`；`services/auth.ts` 新增 `fetchCurrentUser()`（`GET`）／`updateProfile()`（`PUT`）與對應的 `UserInfoResponse`/`UpdateProfileRequest` 型別。
- 新增 `frontend/src/app/(auth)/auth/profile/page.tsx`：載入中/載入失敗/表單三態，`handleSave` 呼叫 `updateProfile`，成功後以回應資料重新同步表單狀態並顯示成功訊息。
- `(auth)/account/page.tsx` 新增「個人資料」Card，連結至 `/auth/profile`（`data-testid="account-edit-profile"`）。

## 4. 測試

**後端單元測試**（`UserPrivacyServiceTest`，新增 4 案例）：兩欄位皆更新＋稽核呼叫斷言、只給姓名/只給電話驗證 PATCH 語意（另一欄不受影響）、使用者不存在拋 `E_1006`。**突變驗證**：把兩個 `if (xxx != null)` guard 移除（變成無條件覆寫），重跑：剛好 2 個驗證 PATCH 語意的案例轉紅，其餘不受影響；還原後恢復 10/10 全綠。

**後端整合測試**（`AuthControllerE2ETest`，新增 3 案例，真實 PostgreSQL）：兩欄位皆更新且回應反映最新值、只給電話時姓名不變、姓名帶空字串回 400 且資料庫原值未被清空（直接查 DB 驗證，非只看回應狀態碼）。

**前端**：`npm run type-check`／`npx eslint`／`npm run build` 皆通過，`/auth/profile` 確實出現在建置輸出路由清單（64 條，+1）。新增 `frontend/e2e/at-profile-edit-real.spec.ts`（真實後端，不 mock）4 案例：表單預填現有姓名＋更新後重新整理持久化、只更新電話驗證 PATCH 語意、姓名清空後端拒絕且前端正確顯示錯誤不崩潰、從 `/account` 可導航至新頁面。

## 5. 全量回歸結果

**Checkstyle 修正一次**：首次跑 `make validate-release` 在 act 的 Backend Unit Tests job 失敗——`UpdateProfileRequest.java` 兩處 `@Size(max = 255)` 觸發 checkstyle `MagicNumber` 規則（本機 `mvn compile`/`mvn test` 不會跑 checkstyle，才沒有提早發現）。改用具名常數（`FULL_NAME_MAX_LENGTH`/`PHONE_MAX_LENGTH`，比照既有 `AddressDto` 慣例）後，`mvn checkstyle:check`／`checkstyle:check@checkstyle-test`／`pmd:check` 三者皆 0 違規。

**文件漂移守門一次**：修正 checkstyle 後第二次跑，在 act 的 Backend Unit Tests job 被 `ApiRouteDocDriftTest` 擋下——新增的 `PUT /v2/auth/me` 只存在於 Controller，`docs/02_architecture/api/API_M03_Auth.md` 沒有對應宣告行。補上 §14（端點章節、Request/Response、錯誤碼表）、總覽表新增一行、修訂紀錄新增 v2.1 後，`ApiRouteDocDriftTest` 2/2 通過。

**本機 `mvn -o clean verify`**（真實 PostgreSQL/Redis）：BUILD SUCCESS，單元 **2221**／整合 **837**（+3）／**0 失敗**；checkstyle（main+test）0 違規；PMD 通過。

**突變驗證**：暫時移除 `updateMyProfile` 的兩個 `if (xxx != null)` guard（變成無條件覆寫），重跑 `UserPrivacyServiceTest`：剛好 2 個驗證 PATCH 語意的案例轉紅（`updateMyProfile_onlyFullNameProvided_leavesPhoneUnchanged`／`updateMyProfile_onlyPhoneProvided_leavesFullNameUnchanged`），其餘 8 個不受影響；還原後重跑恢復 10/10 全綠。

**`make validate-release` 偶發失敗一次（非本輪程式碼問題）**：第三次跑時 act／schema-gate 皆通過，僅 Playwright E2E 層 `at-account-data-rights.spec.ts`（既有檔案，本輪未修改）2 個案例逾時——錯誤快照顯示頁面呈現未登入狀態、`Authentication required`，符合既有已知模式（見專案記憶 `e2e-gate-flake-rate-limiters`：登入/註冊共用的 IP 限流器在整套 166 個規格平行執行時偶發超過額度，與本輪新增的程式碼無因果關係）。重跑第四次：**162 passed（+4，與新增的 AT-PROFILE-EDIT-REAL 四案例一致）／4 skipped／0 failed**（5.8 分），act（backend checkstyle/compile/單元/整合測試＋打包）BUILD SUCCESS，schema 對齊無漂移。已寫入 FULL 驗證記錄，30 分內可直接 push。
