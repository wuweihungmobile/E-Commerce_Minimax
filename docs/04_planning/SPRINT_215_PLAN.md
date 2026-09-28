# Sprint 215 Plan — OAuth 登入沒有跟上密碼登入的不變量（DEF-296）

**Sprint**: Sprint 215
**日期**: 2026-09-28

## 1. 起點

Sprint 214 收尾後（已 push、雲端 CI 全綠），`DEFERRED_ITEMS_TRACKER.md` 沒有 AI 可獨立處理的活躍待辦（`myTodoList.md` 三項仍需使用者操作實際環境；DEF-292／294／295 皆標「不排入排程」、等使用者決定）。使用者貼上 Sprint 214 總結並說「請繼續完成任務」，沒有指定新的項目。

這裡「繼續」的解讀是**推論**：延續 Sprint 210～214 的既有模式，自選一個掃描角度找新缺陷；人工項目（SMTP／維運／Stripe）依先前約定跳過，DEF-294／295 是否要修仍待使用者決定，本輪不動。

## 2. 掃描角度與查證

角度：**同一個不變量是否在每一個入口都落實**（Sprint 210 在成員角色指派上用過的做法），這次套在「登入」上。全庫簽發 JWT 的地方只有兩處：`AuthService.generateAuthResponse`（密碼登入、換發）與 `OAuthService.generateAuthResponse`（Google／GitHub 登入）。後者是前者的複本，逐項對照後發現複本沒有跟上前者後來的修正。

| 不變量 | 密碼登入（`AuthService`） | OAuth 登入（修復前） | 查證方式 |
|---|---|---|---|
| 只有 `ACTIVE` 帳號能取得 session | `findByEmailAndStatus(email, "ACTIVE")`；換發也檢查 | **完全不看狀態** | 紅燈實測 |
| 簽發的 refresh token 要登記到 Redis | `storeRefreshToken` | **沒有登記** | 紅燈實測（真 Redis） |
| JWT 租戶：`tenantId` 為 null 時看 `tenant_members`（Sprint 99） | `resolveTenantForUser` | **直接退回 SYSTEM** | 紅燈實測 |
| 新帳號不帶任何租戶（DEF-244） | `register` 不寫 `tenantId` | **新 OAuth 使用者寫死 SYSTEM 租戶** | 紅燈實測 |
| 更新 `lastLoginAt` | 有 | 沒有 | 讀程式碼（次要） |
| 憑 email 自動連結既有帳號前，email 必須已驗證 | 不適用 | GitHub 只取 verified 信箱；**Google 沒檢查 `verified_email`** | 紅燈實測（Google 真實回應形狀為文件推論，見 §2.5） |

### 2.1 這條路徑今天有沒有人走？

**目前沒有，但一設定就會有。** Sprint 153 已把 OAuth 前後端完整接好（`frontend/src/services/oauth.ts`、`/oauth/callback/[provider]`、`OAuthController`），只是 `OAUTH_GOOGLE_CLIENT_ID` 等環境變數預設為空：後端回 `E-1096`、前端不顯示按鈕。所以下列後果都是「使用者一旦設定憑證就立即生效」，性質與 Sprint 211（真實 SMTP 上線使時序側通道成真）相同。本輪沒有、也無法查證正式環境是否已經設定過憑證。

### 2.2 停權帳號可用 OAuth 繞過停權

`AdminService.updateUserStatus` 把使用者改成 `SUSPENDED` 後：密碼登入回 `E-1001`、換發回 `E-1004`。但只要該帳號曾連結 Google／GitHub（或 Google／GitHub 帳號的 email 與它相同），`OAuthService.handleOAuthLogin` 照樣簽發 access token 與 refresh token；`JwtAuthenticationFilter` 不逐請求檢查使用者狀態，所以這個 access token 在 15 分鐘內完全可用，而且可以無限次重新 OAuth 登入拿新的——**停權對有連結第三方帳號的人等於沒有效果**。

紅燈實測：已連結 Google 的 `SUSPENDED` 帳號 OAuth 登入，未擋下。

### 2.3 OAuth 登入者每 15 分鐘被強制登出

`RefreshTokenService.isRefreshTokenValid` 要求 Redis 裡有該 token 的 `VALID` 記錄，而 `OAuthService` 簽發後從沒呼叫 `storeRefreshToken`。所以 OAuth 登入拿到的 refresh token **第一次換發就被判定為「已撤銷」**。前端 `lib/axios.ts` 在 access token 到期（15 分鐘）後收到 401 → 換發失敗 → 清掉 token 導回 `/login`。

紅燈實測（真 Redis＋真 JWT）：OAuth 登入後立刻換發，得到 `E-1003 Refresh token has been revoked`；同一個鷹架下的**對照組**（密碼登入後換發）成功，證明失敗不是鷹架造成的。

### 2.4 OAuth 註冊者開店後，JWT 租戶永遠是 SYSTEM

兩個分歧疊在一起：

1. `findOrCreateOAuthUser` 新建使用者時寫死 `tenantId = SYSTEM`。Sprint 182（DEF-244）之後密碼註冊已不寫任何租戶。
2. 開店核准（`AdminService.approveTenantApplication`）只建 `tenant_members` 並把角色改成 `STORE_OWNER`，**不改 `User.tenantId`**；而兩條登入路徑都是「`tenantId` 非 null 就直接用」。

結果：透過 OAuth 註冊的人開店後，每次登入（不論哪條路徑）JWT 租戶都是 SYSTEM，而不是自己的店。另外，以密碼註冊（`tenantId` 為 null）、開店後才連結 Google 的店主，OAuth 登入時也會拿到 SYSTEM——因為 OAuth 的複本沒有 Sprint 99 補上的 `tenant_members` 回退。

紅燈實測：兩種情境 JWT 的 `tenantId` 都是 `00000000-0000-0000-0000-000000000001`。

**已查證到此為止的後果**：店主的後台請求落在 SYSTEM 租戶，而不是自己的店。**未查證（推論）**：多個這樣的店主會共用同一個 SYSTEM 租戶上下文而可能互相看到對方的資料，以及 `STORE_OWNER` 權限在 SYSTEM 租戶底下能碰到哪些平台層級內容（例如 V29 遷移放在 SYSTEM 租戶的知識庫／FAQ 分類）。`OrderService`／`LogisticsService` 已有「SYSTEM 租戶不代表同一家店」的防護，其他模組沒有逐一查。修復後這條路徑不再產生這種狀態，所以沒有再往下追。

### 2.5 Google 未驗證的 email 可被拿來接管既有帳號

`findOrCreateOAuthUser` 找不到既有的 OAuth 連結時，會**憑 email 自動連結到同 email 的既有帳號**並直接登入，新帳號也一律標 `emailVerified(true)`，程式註解寫著「OAuth provider 已驗證 email」。GitHub 路徑確實只接受 verified 信箱，Google 路徑卻沒有檢查。若 Google 回報的 email 未經驗證，持有該 Google 帳號的人就能登入同 email 的任何既有帳號。

紅燈實測：模擬 Google 回 `verified_email: false`、email 與既有帳號相同 → 修復前自動連結並登入成功。

**限制（如實揭露）**：沒有真實 Google 憑證，**沒有對真實 Google 驗證過**。欄位名 `verified_email` 來自 Google 對 `https://www.googleapis.com/oauth2/v2/userinfo`（程式實際呼叫的端點）的文件（OIDC 的 userinfo 端點才叫 `email_verified`）；「現實中能否拿到一個 email 未驗證、卻能完成 OAuth 的 Google 帳號」也是推論。修復採保守做法：欄位缺漏視同未驗證。若實際接上 Google 後發現正常帳號被擋（`E-9903 Google account email is not verified`），先檢查回應的欄位名。另外，GitHub 路徑直接採用 `/user` 回傳的公開 email，前提是「GitHub 只允許已驗證的信箱設為公開 email」，這一點本輪沒有查證，維持原樣。

## 3. 修復

原則：**不要再有第二份簽發 session 的程式碼**（Rule 7：兩份矛盾的實作選一份，選較新、測試較多的 `AuthService`）。

- `AuthService.completeLogin(User)`（新，public）：身分已驗證之後簽發 session 的唯一入口——非 `ACTIVE` 回 `E-1004 Account not active`（比照 `refreshToken()`）、更新 `lastLoginAt`、`resolveTenantForUser`、產生 token 並登記 refresh token。`login()` 驗完密碼後改呼叫它（行為不變：非 `ACTIVE` 帳號早已在 `findByEmailAndStatus` 回 `E-1001`，不透露帳號是否存在）。
- `OAuthService.handleOAuthLogin`：改呼叫 `authService.completeLogin(user)`；刪除自己的 `generateAuthResponse` 複本與租戶解析，`JwtTokenService`／`TenantRepository` 相依一併移除。
- `findOrCreateOAuthUser` 新建使用者不再寫 `tenantId`（比照 DEF-244）。
- `exchangeGoogleCode`：`verified_email` 不是 `true` 即回 `E-9903 Google account email is not verified`（比照 GitHub 找不到 verified 信箱時的錯誤碼）。檢查放在換取使用者資訊那一步，所以 `linkOAuthAccount` 也適用；這與 GitHub 路徑的既有行為一致。
- 停權帳號若先經 email 自動連結才被 `completeLogin` 擋下，自動連結寫入的 `OAuthAccount` 會隨同一交易回滾（`handleOAuthLogin` 是 `@Transactional`，`BusinessException` 是 RuntimeException）。這一點靠 Spring 交易語意推論，測試沒有涵蓋（測試不啟動 Spring）。

## 4. 測試

- `OAuthLoginSessionIntegrationTest`（新，6 案例；真實 `JwtTokenService`、真實 `RefreshTokenService`（連 `make test-db-up` 的 Redis，序列化器與生產相同）、真實 `AuthService`，只 mock 資料庫與對 provider 的 HTTP；不啟動 Spring，因為 `IntegrationTestConfiguration` 會把 Redis 換成 mock）：
  - 對照組：密碼登入後換發成功。
  - OAuth 登入後換發成功。
  - 已連結 Google 的停權帳號 OAuth 登入 → `E-1004`，且 Redis 裡沒有留下任何 refresh token。
  - 以密碼註冊、開店後連結 Google 的店主 → JWT 租戶是自己的店。
  - OAuth 新建的使用者不帶租戶；模擬開店核准後再次 OAuth 登入 → JWT 租戶是自己的店。
  - Google `verified_email: false` 且 email 與既有帳號相同 → `E-9903`，不寫入 `OAuthAccount`、不簽發 token。
- `OAuthServiceTest`（改，13→16 案例）：`JwtTokenService`／`TenantRepository` 的 mock 換成 `AuthService`；Google 的假回應補上 `verified_email`；新增「委派給 `completeLogin` 且其例外原樣往外拋」「`verified_email=false` → `E-9903` 且不碰任何帳號」「缺 `verified_email` 欄位視同未驗證」；新使用者案例加斷言 `tenantId` 為 null。
- `AuthServiceTest`（新增 `CompleteLogin` 3 案例）：非 `ACTIVE` → `E-1004` 且不簽發、不登記、不更新；簽發的 refresh token 有登記且 `lastLoginAt` 有更新；`tenantId` 為 null 時由 `tenant_members` 決定 JWT 租戶。
- **紅燈先行**：新整合測試在未修改的程式碼上跑，6 個案例中 5 個失敗、每個都失敗在該失敗的斷言上（換發回「revoked」、JWT 租戶是 SYSTEM、新使用者 `tenantId` 是 SYSTEM、停權與未驗證 email 都沒被擋），對照組通過。**如實揭露**：`OAuthService` 的建構子在修復中改了簽章，所以紅燈那次跑的測試檔用的是舊建構子（傳 `TenantRepository`、`JwtTokenService`），修復後只改了那一行建構呼叫，斷言完全沒動。
- **突變驗證**（6 種，皆被抓到，已還原並與備份逐位元組比對）：
  - M1 拿掉 `completeLogin` 的狀態檢查：`completeLogin_inactiveAccount_…`、`suspendedUserCannotObtainSessionViaOAuth` 紅。
  - M2 拿掉 `storeRefreshToken`：`completeLogin_registersIssuedRefreshToken`、兩個換發案例（含對照組，因為密碼登入走同一個入口）紅。
  - M3 OAuth 新使用者恢復寫死 SYSTEM 租戶：`handleOAuthLogin_googleNewUser_…`、`newOAuthUserIsNotPinnedToSystemTenant` 紅。
  - M4 拿掉 Google `verified_email` 檢查：兩個 Google 未驗證單元案例、`unverifiedGoogleEmailIsNotTrustedForAccountLinking` 紅。
  - M5 條件放寬成「沒寫 false 就信任」：只有「缺欄位」那個單元案例紅（隔離乾淨）。
  - M6 `completeLogin` 改回 OAuth 舊的租戶解析（沒有 `tenant_members` 回退）：`completeLogin_resolvesTenantFromMembership` 與兩個整合案例紅。**過程細節**：這個單元案例初版用精確 stub（`findById(TENANT_ID)`），M6 讓它紅在 Mockito 的 `PotentialStubbingProblem` 而不是租戶斷言上；已改成對任何 id 回對應租戶，重跑 M6 確認紅在「預期店鋪租戶、實得 SYSTEM」的斷言上。

## 5. 驗證結果

- **第一次全量** `mvn -o clean verify`（`make test-db-up` 已啟動真實 postgres/redis）：單元 **1799**（+6：`OAuthServiceTest` +3、`AuthServiceTest.CompleteLogin` +3）全過；整合 **590**（+6：`OAuthLoginSessionIntegrationTest`）中 **1 個 error**，`BUILD FAILURE`。失敗的是與本輪無關的 `M16ErpInventoryConcurrencyIntegrationTest.concurrentPurchaseReceiptsAllApply`：建第 9 張採購單時單號撞到第 2 張（`duplicate key ... po_number=PO-20260928-288324`）。
  - 查證：`PurchaseOrderService.generatePoNumber` 是「`PO-日期-`6 位隨機數」，`po_number` 是全平台唯一約束，撞號不重試——**設計上允許撞號、撞了就失敗**，登記為 **DEF-297**（只登記不修）。
  - **無法完全解釋的部分（如實揭露）**：當時測試 DB 當天只有 8 張單，隨機撞號機率約十萬分之一；我先用「生日問題」推論，量到只有 8 張之後推翻了自己的推論。單獨重跑該類別 3 次（再建 30 張）全數通過；測試端沒有任何東西動過 `ThreadLocalRandom`；全庫只有這一處用隨機數產生單號。這次為什麼剛好撞到，沒有找到答案。
- **第二次全量** `mvn -o verify`（未改任何程式碼）：單元 **1799**／整合 **590**／0 failures／0 errors／0 skipped；checkstyle（main+test）0 violations；PMD 通過；`BUILD SUCCESS`（9 分 27 秒）。
- 突變時被改動的兩個主程式檔，已與突變前備份逐位元組比對一致；`git status` 只有本輪預期的檔案。
- 未變更 entity／migration，未跑 `make validate-schema`；未跑 E2E／`make validate-release`。前端沒有改動；API 回應形狀不變（`AuthResponse` 相同），`frontend/e2e/at-oauth-callback.spec.ts` 只測錯誤狀態（沒有 code／state、provider 回 error、不支援的 provider），不會打到後端 OAuth 登入。

## 6. 決策與已知限制

- **行為變更（我做的決定，不是你拍板的）**：Google 回報 email 未驗證（或回應沒有該欄位）的帳號，現在無法用 Google 登入或連結。這把程式註解原本就宣稱的「OAuth provider 已驗證 email」落實到 Google 路徑，與 GitHub 既有行為一致。
- **停權經由 OAuth 被擋下時回 `E-1004`（帳號已被鎖定）**：比照 `refreshToken()` 對非 `ACTIVE` 帳號的既有回應；使用者已證明自己擁有該 provider 身分，揭露帳號狀態不構成帳號列舉。密碼登入維持 `E-1001` 不變。
- **既有資料**：若正式環境曾經設定過 OAuth 憑證並有人透過 OAuth 註冊，這些使用者的 `tenantId` 已被寫成 SYSTEM，修復只影響之後新建的帳號。若他們之中有人開店，需要一次性資料修正（把 `tenant_id` 為 SYSTEM 且有 `tenant_members` 的使用者改成 null）。本輪無法查詢正式資料庫，**未執行、也未確認是否存在這樣的資料**。
- **未涵蓋**：沒有對真實 Google／GitHub 走過一次 OAuth（需要憑證，屬人工項目）；沒有 HTTP 層（`POST /v2/auth/oauth/login`）的測試——`IntegrationTestConfiguration` 的 mock Redis 會讓換發在那一層無法驗證；停權帳號經 email 自動連結被擋下時 `OAuthAccount` 的交易回滾只是推論。
- **未改動的既有取捨**：停權不會撤銷已簽發的 access token（15 分鐘內仍可用；FRD BR-M03-003 已載明 access token 無法撤銷）；`AdminService.updateUserStatus` 本身不撤銷 refresh token（換發時會因非 `ACTIVE` 被擋，不影響安全性）。

## 7. 後續

- `myTodoList.md` 三項人工待辦狀態不變，仍在使用者手上。若日後要實際啟用 Google／GitHub 登入，建議把「用真實憑證走一次 OAuth 登入→等 15 分鐘後仍在線上→換發成功」列入手動驗證。
- DEF-292、DEF-294、DEF-295 仍為「不排入排程」，等使用者決定。
