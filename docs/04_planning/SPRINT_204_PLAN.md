# Sprint 204 Plan — 忘記密碼、重設密碼、Email 驗證（DEF-252／253 結案）

**Sprint**: Sprint 204
**日期**: 2026-09-27

## 1. 起點與缺口盤點

使用者在 Sprint 203 開頭要求「依照建議，繼續完成任務！做完後，依序完成ＡＢＣ」；本輪是 **A**（帳號功能：DEF-252／253）。

### 1.1 查證結果（動手前）

| 項目 | 查證 |
|------|------|
| PRD／FRD 有沒有這兩項需求 | **完全沒有**（`忘記密碼`／`重設密碼`／`Email 驗證`／`emailVerified` grep：0 筆）。FRD 只列「密碼修改（已登入會員）」為 Phase 2 P1，資料模型有 `email_verified` 欄位但無任何流程 |
| 後端寄信基礎設施 | **沒有**（無 `JavaMailSender`／SendGrid／SES 等任何寄信依賴） |
| `emailVerified` 的使用 | 只有註冊時寫 `false`、OAuth 註冊寫 `true`、`/me` 回傳；**沒有任何邏輯讀取它** |
| 前端 | `/login` 有指向 `/forgot-password` 的連結，該頁不存在（死連結） |
| 可重用的既有機制 | `RefreshTokenService`（Redis，`blacklistAllRefreshTokens`）、`LoginAttemptService`（`resetAttempts`）、`LoginRateLimitFilter`（每來源 IP 限流）、`app.frontend-base-url` |

### 1.2 依 AISDLC 的順序

**需求 → 設計 → 實作 → 驗證**：先在 PRD／FRD 補需求（使用者已於 Sprint 203 拍板範圍），再寫 SRD／API 文件，最後才實作；實作時每支程式「編譯 → 測試 → 突變驗證」再寫下一支。

## 2. 使用者決策（Sprint 203 的 AskUserQuestion 拍板紀錄）

| # | 問題 | 使用者裁定 |
|---|------|-----------|
| 1 | 寄信怎麼處理（PRD／FRD 完全沒有、也沒有寄信設施） | **先補需求＋寄信介面，先用日誌型 Mock 實作** |
| 2 | 要在哪些操作要求已驗證 Email（既有使用者全是未驗證，強制會鎖死他們） | **只在「開店申請」要求** |

**我自行決定的設計參數**（未逐項詢問，已寫進 PRD／FRD 供使用者事後修正）：token 存 Redis 而非新資料表；重設連結 30 分鐘、驗證連結 24 小時；同一帳號 60 秒冷卻；重設後所有裝置的 Refresh Token 失效；既有會員不回填為已驗證；已登入會員「修改密碼」不在本輪。

### 2.1 一個使用者沒有明說、我必須自己補的設計

兩個決策疊加會產生一個問題：**日誌型 Mock 在正式環境不會真的寄信，而「開店申請要求已驗證 Email」若無條件生效，正式環境所有已登入申請者都無從驗證、全部被擋。** 我的處理（已寫入 PRD §7.4.2）：

- `EmailSender.canDeliver()`：日誌型 Mock 在 `prod` profile 回 `false`。**前置條件只在 `canDeliver()` 為 `true` 時生效**；正式環境接上真實寄信服務前不生效，且啟動時記 WARN；接上後自動生效，不需另外設定。
- `prod` 日誌**不記錄信件內容**：連結等同密碼重設憑證，任何能讀日誌的人都能接管帳號。
- **這兩項功能在接上真實寄信服務之前，不可宣告可在正式環境上線。**

這是「使用者選了 A 又選了 Mock」之後產生的隱含後果，需使用者知悉；若想改成無條件強制，改 `requireVerifiedEmailForStoreApplication` 一行即可，但代價是上述的鎖死。

## 3. 實作內容

**文件（先寫）**：PRD §7.4.2；FRD US-M03-006／007、BR-M03-003、§5.2／§5.7、US-M17-001 邊界條件；SRD §5.5（v1.2）；[API_M03_Auth.md](../02_architecture/api/API_M03_Auth.md) §6～§9；API_Index；[API_Error_Codes.md](../02_architecture/API_Error_Codes.md)（`E-1011`、`E-1012`）；[TC_M03_Auth.md](../03_testing/TC_M03_Auth.md) §3.5。

**後端**（依開發順序，每支程式編譯＋測試後才寫下一支）
1. `ErrorCode` `E_1011`（連結無效或已過期，400）、`E_1012`（請先驗證電子郵件，403）；`GlobalExceptionHandler` 狀態對應；文件對照表。
2. `infrastructure/email`：`EmailSender`（`send`、`canDeliver`）、`EmailDeliveryException`、`LoggingEmailSender`。
3. `infrastructure/security/AccountTokenService`：Redis 一次性 token。
4. `core/auth/AccountSecurityService`：`requestPasswordReset`、`resetPassword`、`sendEmailVerification`、`sendEmailVerificationQuietly`、`verifyEmail`、`requireVerifiedEmailForStoreApplication`。
5. `ForgotPasswordRequest`／`ResetPasswordRequest`／`VerifyEmailRequest`；`AuthController` 四個端點；`SecurityConfig` 放行三個公開端點；`LoginRateLimitFilter` 納入四個路徑。
6. `TenantService.createApplication`：已登入申請者前置條件（訪客不檢查）。
7. `AuthService.register`：交易**提交後**寄驗證信（提交失敗不會對從未存在的帳號寄連結）；寄信失敗不影響註冊。

**前端**：`/forgot-password`、`/reset-password`、`/verify-email` 三個頁面（與 `/login` 同一視覺與語言）；`services/auth.ts` 四個方法與 `getApiErrorInfo`；`TenantApplyForm` 收到 `E-1012` 時提供「重新寄送驗證信」。

**E2E 基礎設施**：`frontend/e2e/helpers/mailbox.ts` 從後端日誌取連結（環境變數 `E2E_BACKEND_LOG`，`scripts/validate-e2e.sh` 與雲端 e2e job 皆已設定）；`at-m17-001`／`at-m17-002` 在申請前先走完 Email 驗證；新增 `at-account-security.spec.ts`。**刻意不做**後端「開發用信箱查詢端點」：那等於「任何人可讀他人的密碼重設連結」，誤部署即帳號接管。

## 4. 測試

| 層級 | 檔案 | 守什麼 |
|------|------|--------|
| 單元 | `LoggingEmailSenderTest`（2） | **prod 日誌不得出現連結、token、收件人**；非 prod 要出現（E2E 靠它取連結） |
| 真 Redis 整合 | `AccountTokenServiceIntegrationTest`（8） | 一次性、**16 執行緒併發恰好 1 個成功**、每（用途,會員）一個有效連結、用途隔離、**Redis 不存原文且皆有 TTL**、冷卻、垃圾輸入 |
| 單元 | `AccountSecurityServiceTest`（18） | 不揭露帳號是否存在、冷卻、寄信失敗不改變回應、重設撤銷全部 Refresh Token、註冊後寄信失敗不影響註冊、前置條件在 `canDeliver()=false` 時不生效 |
| 單元 | `AuthServiceRegisterTest`（+3）、`TenantServiceTest`（+2）、`LoginRateLimitFilterTest`（+4）、`GlobalExceptionHandlerTest`（更新期望表）、`ErrorCodeDocDriftTest`（守住新增兩列） | 註冊後**交易提交才寄**、訪客不檢查前置條件、四個路徑受限流 |
| HTTP 層 | `AuthAccountSecurityControllerE2ETest`（7） | 誰不用登入、弱密碼**不進 Service（連結不被消耗）**、`E-1011` 對應、`verify/send` 用登入者本人 id |
| 整合 | `TenantApplicationReviewE2ETest`（+1：IT-M17-APP-007） | 真實鏈路：未驗證 → 403 `E-1012` 且不留申請 → 驗證後 201 |
| Playwright | `at-account-security.spec.ts`（5） | 重設後**舊密碼立刻失效**、連結**一次性**、未註冊 Email 畫面相同、未驗證不能開店／驗證後可以 |

**突變驗證**（暫時破壞實作，測試必須紅，之後還原並以 `diff` 確認）：`LoggingEmailSender` prod 也記錄內容；`AccountTokenService` 改為非原子消耗／改存原文／不作廢舊 token；`AccountSecurityService` 拿掉 `canDeliver` 守門／重設不撤銷 Refresh Token／寄信失敗不吞例外；`LoginRateLimitFilter` 移除新路徑；`SecurityConfig` 不放行 forgot；`AuthController.resetPassword` 拿掉 `@Valid`；`TenantService` 拿掉前置條件；`AuthService` 註冊時立即寄（不等提交）。**共 12 種，全部被預期的測試抓到**。

**過程中的一次測試自身錯誤**：`AccountSecurityServiceTest` 對已 stub 為丟例外的方法用 `when(...)` 重新設定，會先觸發該例外；改用 `doReturn(...).when(...)`。這是測試寫法的錯，不是服務的缺陷。

## 5. 驗證結果

- **後端** `mvn -o clean verify`（真實 postgres／redis）：**單元 1764**（Sprint 203 收尾 1733，+31）、**整合 561**（Sprint 202 基準 545，+16），**0 failures／0 errors**；checkstyle（main＋test）**0 violations**；`BUILD SUCCESS`。
- **E2E** `make validate-e2e`（乾淨 DB → Flyway 建表 → `ddl-auto=validate` 啟動 → Playwright）：**76 passed／4 skipped／0 failed**（基準 71 passed／4 skipped，+5 即 `at-account-security.spec.ts`；4 個 skipped 與基準相同，非本輪造成）。已確認 5 個新 spec 與修改過的 `at-m17-001`／`at-m17-002` 都實際執行並通過，不是被跳過。
- **前端** `tsc --noEmit` 無錯誤；`eslint` 對新增／修改檔案無錯誤（僅 `services/auth.ts` 既有的 `import/no-anonymous-default-export` 警告，非本輪造成）；`next build` 成功（含在 E2E 腳本內），三個新路由為**動態**（與嚴格 nonce CSP 相容）。
- **本輪沒有 Flyway 遷移**（token 走 Redis）；`make validate-schema-doc` 不適用。
- **未執行**：`make validate-release` 完整守門（屬 push 流程，見 §7）。
- **中途遇到的環境問題**：Sprint 203 收尾時測試用 PostgreSQL 沒開，5 個 Spring 上下文測試類別載入失敗（`localhost:5432` refused）；`make test-db-up` 後全綠，非程式問題。

## 6. 範圍外（延後）

- **真實寄信服務**（SMTP／SendGrid／SES）：需使用者決定服務並提供憑證；新增一個 `EmailSender` 實作並標 `@Primary` 即可，呼叫端不必動。接上時同時應改為**非同步寄送**（見 SRD §5.5 時序側通道說明）。
- 已登入會員「修改密碼」（FRD Phase 2 P1）、既有會員的 Email 驗證回填。
- OAuth-only 帳號設定密碼。
- 稽核日誌：密碼重設是高敏感事件，本輪只寫應用日誌（`AuthService` 現有登入／註冊也未寫稽核，比照；與 DEF-257 同一缺口）。
- `AuthController` 其他既有端點與 `API_M03_Auth.md` §1～§5 的舊格式仍未逐項核對（見 Sprint 203 §6）。

## 7. Push

**尚未 push。** 使用者本輪回覆未涵蓋 push 授權（2026-07-08 的免確認授權只涵蓋金流／租戶隔離 commit；本輪雖動到認證與開店申請，但性質是新功能而非既有缺陷修復，不視為涵蓋）。Sprint 203～206 完成後一併詢問。

## 8. 下一步

依使用者指示接著做 **B → C**：B（M07 真實金流——程式層已於 Sprint 49~56、80 完成，剩人工上線步驟；AI 可做的是核對並更新 [STRIPE_PRODUCTION_CHECKLIST.md](../08_deployment/STRIPE_PRODUCTION_CHECKLIST.md) 的過時敘述）→ C（DEF-283，先真實 Tomcat 實測可行性）。
