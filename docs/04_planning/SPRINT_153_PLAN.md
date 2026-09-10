# Sprint 153 Plan — Sprint 151 §6 剩餘五項待排程項目全數處理

**Sprint**: Sprint 153
**日期**: 2026-09-10

---

## 1. 起點

Sprint 152 完成 `DEF-191` 拍板修復後，[SPRINT_151_PLAN.md](SPRINT_151_PLAN.md) §6「下一步 / Action Items」剩餘 5 個待排程項目全數處理完畢：

| # | 項目 | 來源 |
|---|------|------|
| 9 | 賣家訂單詳情頁狀態變更歷史（State Log 時間軸） | Sprint 151 §4 範圍外 |
| 11 | `/dashboard/tenants/[id]/features` 顯示頁補上配額用量 | Sprint 147 §6 範圍外 |
| 12 | 會員資料匯出／自助刪除帳戶補前端入口 | Sprint 149 §7 範圍外 |
| 13 | OAuth 登入/連結串接 | 既有 stub（Sprint 78 記錄） |
| 14 | `AnalyticsService`/`Controller` Javadoc「Mock」字樣過時 | Sprint 150 §7 範圍外 |

使用者指示「請依序推進完成每項任務」，逐項處理。過程中額外發現並修復一個既有生產缺陷（`DEF-192`，見 §3.5）。

---

## 2. 使用者決策

item 13（OAuth）經 `AskUserQuestion` 徵詢處理方式（後端 `OAuthService.exchangeCodeForUserInfo` 原為無條件拋例外的 stub，PRD 定性 P3「待商業需求觸發」）：在「實作真正交換邏輯」／「先規劃不動手」／「跳過維持待排程」三個選項中，使用者選擇「實作真正交換邏輯」，並明確認知最終仍需自行提供 Google/GitHub 真實 OAuth App 憑證（client id/secret + redirect URI）才能實際運作。

---

## 3. 實作內容

### 3.1 item 9：賣家訂單詳情頁狀態變更歷史

**後端**：`OrderService.getOrderStateLogs` 原僅 owner-or-admin 檢查，比照 Sprint 151（`DEF-188`）為 `getOrder` 補上 same-tenant 分支的理由，改用共用的 `checkOrderTenantAuthorization`——賣家/店主既然已能讀取同租戶訂單詳情、寫入狀態，沒有理由看不到同一筆訂單的狀態變更時間軸，此前的差異純屬 Sprint 151 刻意劃定的範圍邊界，非授權設計上的刻意限制。`OrderController.getOrderLogs` Javadoc 同步更新。

**前端**：`app/dashboard/orders/[id]/page.tsx` 新增狀態紀錄時間軸區塊，樣式與資料流完全比照 `(auth)/orders/[id]/page.tsx`（買家詳情頁）既有的同名區塊；`load()` 併行抓取 `getOrderLogs`，`transitionTo` 成功後重新抓取 logs 反映新增的狀態轉換記錄。

**測試**：`OrderServiceTest` 新增 2 個案例（`getOrderStateLogs_sameTenantNonOwner_passesAuthorization`、`getOrderStateLogs_bothUsersOnSystemTenant_throwsE1007`，後者確保 same-tenant 分支仍正確排除系統租戶佔位值）。`mvn -o test -Dtest=OrderServiceTest` → 62 passed（原 60 + 2）。`OrderControllerE2ETest` 12 passed 確認既有端點行為不受影響。

### 3.2 item 14：`AnalyticsService`/`Controller` Javadoc 修正

兩處 Javadoc 原寫「(Mock Implementation)」，查證程式碼本體（`grep -i "mock\|fake\|dummy\|placeholder"` 零命中）確認各方法皆透過既有 repository 查詢真實資料庫資料，非模擬資料——純文件修正，無行為變更。

### 3.3 item 11：配額用量顯示

**後端**：`FeatureToggleResponse` 新增 `quotas: List<QuotaInfo>` 欄位（與既有 `features` 分開回傳——數值配額不是布林開關，依 `DEF-167` 既有結論不應混入開關清單）。`TenantService.getFeatureToggles` 新增 `getQuotaUsage`，計數口徑刻意比照 `FeatureToggleService.checkQuotaNotExceeded` 既有呼叫端（`ProductService`/`RoomService`/`PostService`）的真正檢查條件（ACTIVE 商品/房源、PUBLISHED 貼文），確保顯示用量與實際會被攔截的用量一致；上限直接引用 `AppConstants.QUOTA_MAX_*` 常數（單一事實來源，Sprint 147 §3 既有設計）。

**前端**：`/dashboard/tenants/[id]/features` 新增「數量配額」卡片，逐項顯示「目前用量 / 上限」文字 + 簡易進度條，用量達 90% 以上變色提示。

**測試**：`TenantServiceTest` 新增 1 個案例驗證三個配額的上限與用量正確回傳。`mvn -o test -Dtest=TenantServiceTest` → 35 passed。`TenantControllerE2ETest` 14 passed 確認既有端點行為不受影響。

### 3.4 item 12：會員資料匯出／自助刪除帳戶前端入口

後端 `GET /v2/auth/me/data-export`／`DELETE /v2/auth/me` 早於 Sprint 94（AI-2428）完成，Sprint 149 §2.3 已記錄此前端入口缺口但排除在該輪掃描範圍外。

**前端**：新增 `services/auth.ts` 的 `exportMyData()`/`deleteMyAccount()` 方法；新增 `(auth)/account/page.tsx`：
- 「匯出我的資料」卡片：呼叫後端後以 `Blob` + `<a download>` 觸發瀏覽器下載 JSON 檔。
- 「刪除帳戶」卡片：僅 `role === 'BUYER'` 才顯示（後端 `UserPrivacyService.deleteMyAccount` 僅允許 BUYER 角色自助刪除，避免顯示一個必然失敗的按鈕），含二次確認流程；成功後清除本機資料並導向登入頁（後端已將該使用者全部 refresh token 加入黑名單，前端不需再呼叫 `/v2/auth/logout`）。
- 角色判斷改用 `useSyncExternalStore` 讀取 `authStore` 新增的 `getAuthRoleSnapshot`（SSR/hydration 回 null，client 端才切換為實際角色），比照 `StorefrontHeader` 既有模式避免 hydration mismatch——若直接在 render 期呼叫 `AuthService.getCurrentUser()`，SSR 輸出與 client hydration 輸出會不一致。

`StorefrontHeader` 帳號選單新增「帳戶設定」連結指向 `/account`。

**測試**：新增 `frontend/e2e/at-account-data-rights.spec.ts`（2 個案例，涵蓋匯出下載與刪除帳戶兩個操作）。**執行此測試過程中發現 `DEF-192`，見 §3.5**。

### 3.5 🔴 `DEF-192`：`TenantContextFilter` 誤跳過兩個已驗證端點（item 12 驗證過程中意外揭露）

**發現過程**：`make validate-e2e`（host 執行真實封裝後端）跑新增的 `at-account-data-rights.spec.ts` 時，「刪除帳戶」案例得到 `404`（預期 `200`），畫面顯示「找不到使用者」；「匯出資料」案例 `waitForEvent('download')` 逾時（下載事件從未觸發）。

**根因**：`TenantContextFilter.shouldNotFilter`（修復前）為 `request.getServletPath().startsWith("/v2/auth/")` 即跳過整個 filter，意圖只排除 `register`/`login`/`refresh` 三個未驗證端點。但 Sprint 94（AI-2428）新增的 `GET /v2/auth/me/data-export`／`DELETE /v2/auth/me` 同樣掛在 `/v2/auth/*` 下，且皆為**已驗證**端點——`UserPrivacyService.exportMyData`/`deleteMyAccount` 依賴 `TenantContext.getCurrentUser()` 取得使用者 ID，被誤跳過後恆為 `null`，`userRepository.findByIdForUpdate(null)` 查無資料，回傳 `E-1006`「找不到使用者」（404）。

**為何長期未被既有 `AuthControllerE2ETest` 發現**：該測試以 `@SpringBootTest`/`integration-test` profile 執行，該 profile 未設定 `server.servlet.context-path`；以 `TenantContextFilter` 自身的 DEBUG log 實測確認，該環境下 `request.getServletPath()` 對每個請求皆回傳**空字串**，使舊版 `"".startsWith("/v2/auth/")` 恆為 `false`——`shouldNotFilter` 形同沒有生效，filter 照常執行，兩個已驗證端點被「意外」正確處理，測試因而長期綠燈。但正式環境（`application.yml` 明確設有 `server.servlet.context-path: /api`）啟動的真實封裝應用程式，`getServletPath()` 行為不同，舊版判斷會 100% 重現此缺陷——只有透過 Playwright 對 `make validate-e2e`（host 執行真實封裝 JAR）才會被揭露，這正是本輪的發現路徑。

**修法**：
1. 改用明確路徑白名單（`register`/`login`/`refresh`/`oauth/login`，見 §3.6 item 13 補充），取代過寬的前綴判斷。
2. 改讀 `request.getRequestURI()` 減去 `request.getContextPath()` 而非 `getServletPath()`——前者不受 context-path 設定與否影響，兩環境行為一致，不再依賴巧合。

**連帶影響（如實記錄，非本輪刻意設計）**：`logout`／`GET /v2/auth/me`／`data-export`／`DELETE /v2/auth/me` 四個已驗證端點，此後與其他已驗證端點一樣受 `RateLimitFilter` 每租戶限流保護——`RateLimitFilter` 既有 Javadoc 排除說明原文是「`/v2/auth/**` **登入前**」，語意上從未打算涵蓋登入後端點，此前純屬套用同一段過寬判斷而被意外排除，不是刻意的排除設計。

**測試（紅燈先行且實際執行）**：`TenantContextFilterTest` 新增 8 個案例（含 GOOGLE/GITHUB OAuth 登入/連結路徑的對照組，見 §3.6）。`git stash` 還原舊版程式碼後跑新增測試：3 個案例如預期失敗（`expected: <uuid> but was: null`——分別是 data-export、DELETE /me、未驗證 login 案例本身也失敗因為連 system tenant 都沒被設定）；`git stash pop` 還原修復後重新編譯，6 個原始案例（不含後補的 OAuth 兩案例）全數通過。

已登記 `DEF-192` 於 [DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md) 已完成延後項目。

### 3.6 item 13：OAuth 登入/連結串接

**背景**：`OAuthService.exchangeCodeForUserInfo`（`handleOAuthLogin`/`linkOAuthAccount` 共用的第一步）原無條件拋 `UnsupportedOperationException`（Sprint 78 記錄的 stub，PRD 定性 P3）。`findOrCreateOAuthUser`（既有 OAuth 帳戶查找、email 既有帳號自動連結、新用戶建立）邏輯完整但先前完全無法從 public API 觸及。

**後端**：
- `application.yml` 新增 `oauth.google.client-id`/`client-secret`、`oauth.github.client-id`/`client-secret`（皆透過環境變數注入，預設空字串）、`oauth.allowed-redirect-origins`（逗號分隔的允許 origin 清單，預設 `http://localhost:3000`）。
- `OAuthService.exchangeCodeForUserInfo` 改為依 provider 分派至 `exchangeGoogleCode`/`exchangeGithubCode`：
  - **redirect_uri 白名單驗證**（Sprint 78 已記錄的既知安全考量，本輪補上）：只比對 origin（scheme+host+port），未設定允許清單時視為未啟用此保護放行（與 Stripe webhook signature 等其他選填第三方整合同樣的「空設定＝暫不啟用」慣例）。
  - **GOOGLE**：`POST https://oauth2.googleapis.com/token` 換 access_token → `GET https://www.googleapis.com/oauth2/v2/userinfo` 取得 `{id, email, name, picture}`。
  - **GITHUB**：`POST https://github.com/login/oauth/access_token` 換 access_token → `GET https://api.github.com/user`；`email` 為 `null` 時（使用者未公開信箱）另呼叫 `GET https://api.github.com/user/emails` 找 primary+verified 信箱——`User.email` 為必填唯一欄位，不能讓 `null` 流入 `findOrCreateOAuthUser`。
  - client-id/secret 未設定時回 `ErrorCode.E_1096`（新增，503），不會把空字串當真憑證送給 provider；provider API 失敗或回應缺欄位包成既有 `ErrorCode.E_9903`「外部 API 錯誤」（503）。
- `linkOAuthAccount` 的 provider_user_id 衝突檢查，原拋未分類的 `IllegalStateException`（不會被 `GlobalExceptionHandler` 的 `@ExceptionHandler(BusinessException.class)` 攔截，落到通用 500），改用 `BusinessException(ErrorCode.E_1008)`——該錯誤碼 Sprint 78 stub 時代即已預留卻從未真正拋出過。
- `GlobalExceptionHandler.mapErrorCodeToStatus`：新增 `E_1096`（503）/`E_1097`「不允許的 OAuth redirect_uri」（400，新增）；`E_1008` 原掛在 `E_1000~E_1004` 這組 UNAUTHORIZED（Sprint 78 stub 時代預留、從未真正拋出過），語意上「OAuth 帳號已綁定其他使用者」屬資料衝突而非認證失敗，移到與 `E_4106`/`E_1010` 同組 CONFLICT。
- `TenantContextFilter` 的未驗證路徑白名單（見 §3.5）追加 `/v2/auth/oauth/login`（同為未驗證登入入口，理由與 register/login/refresh 一致）；`/v2/auth/oauth/link` 需已驗證使用者，不在白名單內。

**前端**：
- 新增 `services/oauth.ts`：`isOAuthProviderConfigured(provider)`（依 `NEXT_PUBLIC_OAUTH_*_CLIENT_ID` 建置期環境變數判斷，未設定則對應按鈕不顯示，避免出現必然失敗的按鈕）、`startOAuthFlow(provider, intent)`（產生隨機 `state` 存入 `sessionStorage`，重導至 provider 真實授權頁）、`consumeOAuthState`（callback 頁驗證 state 是否相符，CSRF 防護，SPA 標準作法——state 完全存在前端 sessionStorage，後端無須也未持有 state，`OAuthDto.AuthRequest`/`LinkRequest` 刻意不新增 `state` 欄位，與 Sprint 78 review 原建議的「後端補 state 欄位」方向不同，見 §5 誠實揭露）。
- 新增 `app/oauth/callback/[provider]/page.tsx`：讀 `?code=&state=&error=`，驗證 state 後依發起時記錄的 intent 呼叫 `/v2/auth/oauth/login`（登入，成功後 `storeAuthData` + 導向首頁）或 `/v2/auth/oauth/link`（連結，成功後導回 `/account?linked=<provider>`）；三種錯誤情境（缺 code/state、provider 回傳 error、不支援的 provider 路徑）皆有對應錯誤訊息。
- `(auth)/login/page.tsx`：移除原本從未接上任何 `onClick` 的「Google/Facebook/Apple」死碼按鈕列（Facebook/Apple 後端 `OAuthProvider` 枚舉根本不存在對應值），改為 Google/GitHub 兩個真正可用的按鈕，僅在對應 client-id 已設定時顯示。
- `(auth)/account/page.tsx`：新增「連結第三方帳號」卡片，Google/GitHub 連結按鈕（同樣依設定顯示），連結成功後顯示成功提示。

**測試**：`OAuthServiceTest.java` 完全重寫（Sprint 78 版本聚焦於 stub 的 fail-closed 行為，已隨真實邏輯實作而過時），新增 13 個案例涵蓋：未設定憑證（GOOGLE/GITHUB 各 1）、redirect_uri 白名單（origin 不符/缺漏各 1）、GOOGLE 成功流程（新使用者/既有 OAuth 帳戶關聯各 1）、GITHUB 成功流程（公開 email/email 為 null 查 `/user/emails`/查無 verified email 各 1）、provider API 失敗（連線失敗/回應缺欄位各 1）、`linkOAuthAccount` 衝突拋 `E_1008`（1）與成功連結（1，含 userId 來自呼叫端而非 request body 的擁有權驗證）。`mvn -o test -Dtest=OAuthServiceTest` → 13 passed。`TenantContextFilterTest` 追加 2 個對照組案例（OAuth 登入路徑跳過 filter／連結路徑不跳過），共 8 passed。

新增 `frontend/e2e/at-oauth-callback.spec.ts`（3 個案例，見 §4 範圍外說明本輪無法測試的部分）。

---

## 4. 範圍外（刻意不做，如實揭露）

- **OAuth 登入/連結成功路徑無法端對端測試**：本測試環境未設定 `NEXT_PUBLIC_OAUTH_GOOGLE_CLIENT_ID`/`NEXT_PUBLIC_OAUTH_GITHUB_CLIENT_ID`，`/login`、`/account` 的登入/連結按鈕不會渲染，也沒有真實 provider 可完成授權流程換取真實 `code`。`at-oauth-callback.spec.ts` 僅涵蓋 callback 頁在缺少 code/state（不依賴真實 provider）時的錯誤處理；後端成功流程改以 `OAuthServiceTest`（mock `RestTemplate` 模擬 provider 回應）取得完整覆蓋。**功能要真正上線，需要使用者自行在部署環境設定真實 OAuth App 憑證**（`OAUTH_GOOGLE_CLIENT_ID`/`OAUTH_GOOGLE_CLIENT_SECRET`/`OAUTH_GITHUB_CLIENT_ID`/`OAUTH_GITHUB_CLIENT_SECRET`/`OAUTH_ALLOWED_REDIRECT_ORIGINS` 環境變數，前端另需 `NEXT_PUBLIC_OAUTH_GOOGLE_CLIENT_ID`/`NEXT_PUBLIC_OAUTH_GITHUB_CLIENT_ID`）。
- **`OAuthDto.AuthRequest`/`LinkRequest` 不新增 `state` 欄位**：Sprint 78 review 原記錄「屆時需一併補上 state 欄位」，本輪刻意採不同設計——state 完全由前端產生、存於 `sessionStorage`、於 callback 頁比對，符合 OWASP 對 SPA-based OAuth 流程的標準建議；authorization code 本身於真實 provider 端即為短時效、一次性，加上本輪新增的 redirect_uri origin 白名單，已足以防禦 code 被導向惡意網域，backend 無須也未持有 state 值。與 Sprint 78 原建議方向不同，非遺漏，已在 §3.6 說明理由。
- **`DEF-103/104/105` 三筆輸入驗證**：S135 已拍板「不排入排程」，非本輪範圍。

---

## 5. 驗證結果

### 5.1 後端

`mvn -o verify`（含 Flyway `ddl-auto=validate` 真實 Postgres + Redis 的完整單元＋整合回歸，涵蓋 item 9/11/14 + `DEF-192` 修復；item 13 的變更完成於此輪執行**之後**，另以獨立指令驗證）：

- **第一輪**：**1245 個單元測試 + 478 個整合測試，0 failed**，checkstyle-main/checkstyle-test 皆 0 違規，BUILD SUCCESS。（過程中一次因誤在此背景執行序列仍在跑時，額外手動執行了一次 `mvn -o compile`，經查證該次背景執行的編譯階段早於手動指令，不影響此結果的有效性；另有一次 `TenantControllerE2ETest` 在同批次中因與該次意外並行的 `mvn compile` 產生 classpath 競態而假性失敗 500，隔離重跑 14/14 全數通過，確認與程式碼無關。）
- item 13（OAuth）獨立驗證：`mvn -o test -Dtest=TenantContextFilterTest,OAuthServiceTest` → **21 passed / 0 failed**；`mvn -o checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test` → 0 違規。

### 5.2 前端

`npx tsc --noEmit`：0 error。`npx eslint`（對變更/新增檔案）：0 error（`services/oauth.ts`/`services/auth.ts` 既有的 `import/no-anonymous-default-export` 警告與既有檔案同型，非新增問題）。`npm run build`（Next.js production build，Turbopack）：成功，`/account`（Static）、`/oauth/callback/[provider]`（Dynamic）皆正確產生路由。

### 5.3 `make validate-e2e`（乾淨 DB + host 全棧 + Playwright，複製雲端 e2e job）

新增 `at-account-data-rights.spec.ts`（2 案例）+ `at-oauth-callback.spec.ts`（3 案例）共 5 個新案例，加上既有基準 61 個（57 passed + 4 skipped），總計 66 個測試。

**第一輪紅燈**：`at-account-data-rights.spec.ts` 2 案例皆失敗（刪除帳戶得到 404「找不到使用者」；匯出資料下載事件逾時未觸發）——即 `DEF-192`（§3.5）的實際發現過程。

**修復 `DEF-192` 後第二輪（最終）**：**62 passed / 4 skipped / 0 failed**，與既有基準完全一致（新增的 5 個案例全數通過，既有 61 個案例無回歸）；backend 以 `ddl-auto=validate` + Flyway 成功啟動，腳本明確輸出「entity 與 Flyway schema 對齊，無漂移」。

---

## 6. 下一步 / Action Items

依 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md) v2.0 §3.2，本節為必要章節。

| # | 項目 | 來源 | 狀態 | 去向 |
|---|------|------|------|------|
| 1 | item 9：賣家訂單詳情頁狀態歷史時間軸 | Sprint 151 §6 | ✅ 完成 | 詳見 §3.1 |
| 2 | item 14：`AnalyticsService`/`Controller` Javadoc 修正 | Sprint 151 §6 | ✅ 完成 | 詳見 §3.2 |
| 3 | item 11：配額用量顯示 | Sprint 151 §6 | ✅ 完成 | 詳見 §3.3 |
| 4 | item 12：會員資料匯出／自助刪除帳戶前端入口 | Sprint 151 §6 | ✅ 完成 | 詳見 §3.4 |
| 5 | `DEF-192`：`TenantContextFilter` 誤跳過已驗證端點 | 本輪驗證 item 12 時意外揭露 | ✅ 完成 | 詳見 §3.5 |
| 6 | item 13：OAuth 登入/連結真實交換邏輯 | Sprint 151 §6，經 `AskUserQuestion` 拍板 | ✅ 完成 | 詳見 §3.6，需使用者提供真實憑證才能上線，見 §4 |
| 7 | `mvn -o verify` 完整回歸 | 本輪交付前 | ✅ 完成 | 見 §5.1 |
| 8 | `make validate-e2e` 回歸確認無 schema/前端漂移 | 本輪交付前 | ✅ 完成 | 見 §5.3 |
| 9 | 設定真實 OAuth App 憑證並實際驗證登入/連結流程 | 本輪 §4 範圍外 | ⬜ 待使用者提供憑證 | 需使用者於 Google/GitHub 開發者主控台建立 OAuth App，取得 client id/secret，設定 `OAUTH_*` 環境變數 |
| 10 | `DEF-103/104/105` 三筆輸入驗證 | S135 登記 | ⬜ 待排程 | 低優先級，已拍板不排入排程 |

---

## 7. 誠實揭露總結

- **`DEF-192` 是本輪驗證 item 12 才意外揭露的既有生產缺陷**，非原定範圍：PRD §1.5.1 會員資料匯出/自助刪除帳戶兩項功能自 Sprint 94（AI-2428）完成後，因這個 filter 缺陷，經由真實封裝後端呼叫**實質上從未真正可用過**（前端此前也從未有呼叫點，兩個問題疊加使此缺陷維持沉默）；既有 `AuthControllerE2ETest` 因測試環境的 servlet path 解析巧合而長期意外綠燈，未能發現。
- `DEF-192` 的修法刻意選擇「明確路徑白名單 + `getRequestURI()`/`getContextPath()`」而非簡單移除 `shouldNotFilter`——後者雖然也能修好 data-export/deleteMyAccount，但會讓 `register`/`login`/`refresh` 這三個真正未驗證的端點意外開始共用同一個 `RateLimitFilter` 限流桶，是修一個缺陷、引入另一個迴歸的陷阱，過程中主動發現並改用更精確的修法。
- item 13（OAuth）的 `state` 欄位設計刻意偏離 Sprint 78 review 原本記錄的「後端補 state 欄位」建議，改採前端 sessionStorage 方案，已在 §3.6/§4 說明理由與取捨，非遺漏。
- OAuth 登入/連結的成功路徑本輪**無法**端對端驗證（無真實 provider 憑證），如實記錄於 §4，以後端 mock 測試 + 前端錯誤處理路徑 E2E 補足可驗證的部分；使用者已於決策時明確知悉此限制。
- `mvn -o verify` 第一輪執行期間，主控 session 一度在同一 `backend/` 工作目錄誤觸發第二個 Maven 生命週期（`mvn -o compile`），造成 `TenantControllerE2ETest` 一次性假性失敗（500），已透過隔離重跑（14/14 全數通過）證實與程式碼無關、純屬並行編譯的 classpath 競態，如實記錄於 §5.1，未隱瞞或悄悄略過。
